package com.encryptvault.app.crypto

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.KeySpec
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class FileItem(
    val uri: Uri,
    val name: String,
    val size: String,
    val status: ProcessStatus = ProcessStatus.PENDING
)

enum class ProcessStatus { PENDING, ENCRYPTED, DECRYPTED, DONE, ERROR }

class CryptoManager(private val ctx: Context) {

    companion object {
        private const val VERSION: Byte = 0x06
        private const val SALT_SIZE = 16
        private const val IV_SIZE = 16
        private const val CHECK_SIZE = 8
        // 文件: PBKDF2-HMAC-SHA512 1,200,000 轮
        private const val ITERATIONS = 1_200_000
        private const val FILE_PRF = "PBKDF2WithHmacSHA512"
        private const val KEY_BITS = 256
        private const val GCM_TAG_BITS = 128
        private const val HEADER_SIZE = 1 + SALT_SIZE + IV_SIZE + CHECK_SIZE
        // Shell: PBKDF2-HMAC-SHA512 1,500,000 轮 (openssl -md sha512)
        private const val OPENSSL_ITER = 1_500_000
        private const val OPENSSL_MD = "PBKDF2WithHmacSHA512"
    }

    fun getFileInfo(uri: Uri): FileItem? = try {
        ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val name = c.getString(c.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                val size = c.getLong(c.getColumnIndexOrThrow(OpenableColumns.SIZE))
                FileItem(uri, name, formatSize(size))
            } else null
        }
    } catch (e: Exception) { null }

    fun scanDirectory(treeUri: Uri): List<FileItem> {
        val list = mutableListOf<FileItem>()
        val root = DocumentFile.fromTreeUri(ctx, treeUri) ?: return list
        root.listFiles().forEach { doc ->
            if (doc.isFile) list.add(FileItem(doc.uri, doc.name ?: "unknown", formatSize(doc.length())))
        }
        return list
    }

    private fun readBytes(uri: Uri): ByteArray =
        ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)

    private fun writeBytes(uri: Uri, data: ByteArray) {
        ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(data) }
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance(FILE_PRF)
        val spec: KeySpec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS)
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }

    private fun keyCheck(key: SecretKeySpec): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(key.encoded).copyOf(CHECK_SIZE)
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun sha16(data: ByteArray): String {
        val d = MessageDigest.getInstance("SHA-256").digest(data)
        return d.copyOf(8).toHex()
    }

    suspend fun encryptFile(uri: Uri, password: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val plain = readBytes(uri)
            val rnd = SecureRandom()
            val salt = ByteArray(SALT_SIZE).also { rnd.nextBytes(it) }
            val iv = ByteArray(IV_SIZE).also { rnd.nextBytes(it) }
            val key = deriveKey(password, salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            val ct = cipher.doFinal(plain)
            val out = ByteArray(HEADER_SIZE + ct.size)
            out[0] = VERSION
            System.arraycopy(salt, 0, out, 1, SALT_SIZE)
            System.arraycopy(iv, 0, out, 1 + SALT_SIZE, IV_SIZE)
            System.arraycopy(keyCheck(key), 0, out, 1 + SALT_SIZE + IV_SIZE, CHECK_SIZE)
            System.arraycopy(ct, 0, out, HEADER_SIZE, ct.size)
            writeBytes(uri, out)
            true
        } catch (e: Exception) { e.printStackTrace(); false }
    }

    suspend fun decryptFile(uri: Uri, password: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val data = readBytes(uri)
            if (data.size < HEADER_SIZE + 16) return@withContext false
            val v = data[0]
            // 兼容 v3/v4/v5/v6
            if (v != VERSION && v != 0x05.toByte() && v != 0x04.toByte() && v != 0x03.toByte())
                return@withContext false
            val hdr = 1 + SALT_SIZE + IV_SIZE + CHECK_SIZE
            if (data.size < hdr + 16) return@withContext false
            val salt = data.copyOfRange(1, 1 + SALT_SIZE)
            val iv = data.copyOfRange(1 + SALT_SIZE, 1 + SALT_SIZE + IV_SIZE)
            val check = data.copyOfRange(1 + SALT_SIZE + IV_SIZE, hdr)
            val ct = data.copyOfRange(hdr, data.size)

            // 旧版本用 SHA256, v6 用 SHA512
            val prf = if (v == VERSION) FILE_PRF else "PBKDF2WithHmacSHA256"
            val iters = if (v == VERSION) ITERATIONS else 600_000
            val factory = SecretKeyFactory.getInstance(prf)
            val spec: KeySpec = PBEKeySpec(password.toCharArray(), salt, iters, KEY_BITS)
            val key = SecretKeySpec(factory.generateSecret(spec).encoded, "AES")

            if (!check.contentEquals(keyCheck(key))) return@withContext false
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            writeBytes(uri, cipher.doFinal(ct))
            true
        } catch (e: Exception) { e.printStackTrace(); false }
    }

    // ============================================================
    //  Shell 保护 v6
    // ============================================================
    private fun deriveOpenSSLKey(password: String, salt: ByteArray): Pair<ByteArray, ByteArray> {
        val factory = SecretKeyFactory.getInstance(OPENSSL_MD)
        val spec = PBEKeySpec(password.toCharArray(), salt, OPENSSL_ITER, 48 * 8)
        val derived = factory.generateSecret(spec).encoded
        return derived.copyOfRange(0, 32) to derived.copyOfRange(32, 48)
    }

    suspend fun protectShellScript(
        inputPath: String, outputPathRaw: String, password: String,
        passwordMode: Boolean, maxRuns: Int, failLimit: Int
    ): String = withContext(Dispatchers.IO) {
        try {
            if (passwordMode && password.length < 8) return@withContext "❌ 密码至少 8 位"
            val src = File(inputPath.trim())
            if (!src.exists()) return@withContext "❌ 源文件不存在"
            if (!src.isFile)   return@withContext "❌ 输入路径不是文件"

            val raw = outputPathRaw.trim().ifBlank { src.parent ?: "/sdcard" }
            val baseName = src.nameWithoutExtension + "-protected.sh"
            val outFile = run {
                val f = File(raw)
                if (f.isDirectory || raw.endsWith("/")) File(f, baseName) else f
            }
            outFile.parentFile?.mkdirs()

            val plain = src.readBytes()
            val D = "${'$'}"
            val b64: String
            val keyRevHex: String

            if (passwordMode) {
                val salt = ByteArray(8).also { SecureRandom().nextBytes(it) }
                val (key, iv) = deriveOpenSSLKey(password, salt)
                val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
                val ct = cipher.doFinal(plain)
                val blob = ByteArray(16 + ct.size)
                System.arraycopy("Salted__".toByteArray(Charsets.US_ASCII), 0, blob, 0, 8)
                System.arraycopy(salt, 0, blob, 8, 8)
                System.arraycopy(ct, 0, blob, 16, ct.size)
                b64 = Base64.encodeToString(blob, Base64.NO_WRAP)
                keyRevHex = ""
            } else {
                val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
                val iv = ByteArray(16).also { SecureRandom().nextBytes(it) }
                val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
                val ct = cipher.doFinal(plain)
                b64 = Base64.encodeToString(ct, Base64.NO_WRAP)
                keyRevHex = (key + iv).toHex().reversed()
            }

            val id = sha16(plain)
            val mode = if (passwordMode) "password" else "nopass"
            val iterLabel = if (passwordMode) "PBKDF2-HMAC-SHA512 (1,500,000 轮) + AES-256-CBC"
                            else "AES-256-CBC + 混淆内嵌密钥"

            val tmpl = """
#!/data/data/com.termux/files/usr/bin/bash
# 也可在 MT 管理器中运行:  bash 本文件.sh
# ============================================================
#  EncryptVault Protected Script (v6 · ${mode})
#  $iterLabel
# ============================================================

printf '\n⚠️  本脚本会执行解密后的 Bash 代码, 请确认来源可信\n\n' >&2

umask 077

__EV_TMPDIR=""
for __d in "${D}{TMPDIR:-}" "/data/data/com.termux/files/usr/tmp" "/tmp" "."; do
    if [ -n "${D}__d" ] && [ -d "${D}__d" ] && [ -w "${D}__d" ]; then
        __EV_TMPDIR="${D}__d"; break
    fi
done
[ -z "${D}__EV_TMPDIR" ] && { printf '❌ 无可用临时目录\n' >&2; exit 1; }

command -v openssl >/dev/null 2>&1 || {
    printf '❌ 需要 openssl\n' >&2; exit 1; }

__EV_MODE='${mode}'
__EV_D='$b64'
__EV_K_REV='$keyRevHex'
__EV_MAX=$maxRuns
__EV_FAIL=$failLimit
__EV_ID='$id'

case "${D}__EV_MAX"  in ''|*[!0-9]*) __EV_MAX=0 ;; esac
case "${D}__EV_FAIL" in ''|*[!0-9]*) __EV_FAIL=0 ;; esac

__EV_HOME="${D}{HOME:-}"
[ -z "${D}__EV_HOME" ] && __EV_HOME="$(cd "$(dirname "$0")" 2>/dev/null && pwd)"
[ -z "${D}__EV_HOME" ] && __EV_HOME="."
__EV_STATE_DIR="${D}{__EV_HOME}/.ev_state"
mkdir -p "${D}__EV_STATE_DIR" 2>/dev/null
chmod 700 "${D}__EV_STATE_DIR" 2>/dev/null
__EV_STATE="${D}{__EV_STATE_DIR}/${D}__EV_ID"

# ---- 自毁: shred 覆盖 3 次 ----
__ev_self_destruct() {
    if command -v shred >/dev/null 2>&1; then
        shred -u -n 3 -z "$0" 2>/dev/null
    fi
    rm -f "$0" 2>/dev/null
    if command -v shred >/dev/null 2>&1; then
        shred -u -n 3 -z "${D}__EV_STATE" 2>/dev/null
    fi
    rm -f "${D}__EV_STATE" 2>/dev/null
    printf '💥 脚本已自毁 (shred 覆盖 3 次)\n' >&2
}

if [ "${D}__EV_MAX" -gt 0 ]; then
    __EV_RUNS=0
    [ -f "${D}__EV_STATE" ] && __EV_RUNS=$(cat "${D}__EV_STATE" 2>/dev/null || echo 0)
    case "${D}__EV_RUNS" in ''|*[!0-9]*) __EV_RUNS=0 ;; esac
    if [ "${D}__EV_RUNS" -ge "${D}__EV_MAX" ]; then
        printf '❌ 已达最大执行次数 (%s/%s)\n' "${D}__EV_RUNS" "${D}__EV_MAX" >&2
        __ev_self_destruct
        exit 1
    fi
fi

__EV_TMP="${D}{__EV_TMPDIR}/.ev_${D}${D}_${D}(date +%s)_${D}RANDOM"
trap 'rm -f "${D}{__EV_TMP}.enc" "${D}{__EV_TMP}.sh" "${D}{__EV_TMP}.k" 2>/dev/null' EXIT INT TERM HUP

printf '%s' "${D}__EV_D" | base64 -d > "${D}{__EV_TMP}.enc" 2>/dev/null
unset __EV_D
[ -s "${D}{__EV_TMP}.enc" ] || { printf '❌ 数据损坏\n' >&2; exit 1; }

if [ "${D}__EV_MODE" = "password" ]; then
    __EV_MAX_TRIES=${D}__EV_FAIL
    [ "${D}__EV_MAX_TRIES" -le 0 ] && __EV_MAX_TRIES=1
    __EV_OK=0
    __EV_TRIES=0
    while [ ${D}__EV_TRIES -lt ${D}__EV_MAX_TRIES ]; do
        printf '🔐 输入密码: ' >&2
        IFS= read -r -s __EV_PWD
        printf '\n' >&2
        if [ -z "${D}__EV_PWD" ]; then
            __EV_TRIES=${D}(( __EV_TRIES + 1 ))
            continue
        fi
        printf '%s\n' "${D}__EV_PWD" | \
            openssl enc -d -aes-256-cbc -pbkdf2 -iter 1500000 -md sha512 \
                -pass stdin -in "${D}{__EV_TMP}.enc" -out "${D}{__EV_TMP}.sh" 2>/dev/null
        __EV_RC=${D}?
        unset __EV_PWD
        if [ ${D}__EV_RC -eq 0 ] && [ -s "${D}{__EV_TMP}.sh" ]; then
            __EV_OK=1; break
        fi
        rm -f "${D}{__EV_TMP}.sh"
        __EV_TRIES=${D}(( __EV_TRIES + 1 ))
        printf '❌ 密码错误 (%s/%s)\n' "${D}__EV_TRIES" "${D}__EV_MAX_TRIES" >&2
    done
    rm -f "${D}{__EV_TMP}.enc"
    if [ ${D}__EV_OK -ne 1 ]; then
        if [ "${D}__EV_FAIL" -gt 0 ]; then
            printf '💥 密码错误次数过多, 脚本自毁\n' >&2
            __ev_self_destruct
        fi
        exit 1
    fi
else
    printf '%s' "${D}__EV_K_REV" | rev > "${D}{__EV_TMP}.k" 2>/dev/null
    unset __EV_K_REV
    [ -s "${D}{__EV_TMP}.k" ] || { printf '❌ 密钥损坏\n' >&2; exit 1; }
    __EV_KEY_HEX=$(cut -c1-64  "${D}{__EV_TMP}.k")
    __EV_IV_HEX=$(cut -c65-96 "${D}{__EV_TMP}.k")
    rm -f "${D}{__EV_TMP}.k"
    openssl enc -d -aes-256-cbc -K "${D}__EV_KEY_HEX" -iv "${D}__EV_IV_HEX" \
        -in "${D}{__EV_TMP}.enc" -out "${D}{__EV_TMP}.sh" 2>/dev/null
    __EV_RC=${D}?
    rm -f "${D}{__EV_TMP}.enc"
    unset __EV_KEY_HEX __EV_IV_HEX
    if [ ${D}__EV_RC -ne 0 ] || [ ! -s "${D}{__EV_TMP}.sh" ]; then
        printf '❌ 解密失败\n' >&2
        exit 1
    fi
fi

if [ "${D}__EV_MAX" -gt 0 ]; then
    __EV_RUNS=0
    [ -f "${D}__EV_STATE" ] && __EV_RUNS=$(cat "${D}__EV_STATE" 2>/dev/null || echo 0)
    case "${D}__EV_RUNS" in ''|*[!0-9]*) __EV_RUNS=0 ;; esac
    __EV_RUNS=${D}(( __EV_RUNS + 1 ))
    printf '%s' "${D}__EV_RUNS" > "${D}__EV_STATE" 2>/dev/null
    chmod 600 "${D}__EV_STATE" 2>/dev/null
fi

chmod 700 "${D}{__EV_TMP}.sh" 2>/dev/null
bash "${D}{__EV_TMP}.sh"
__EV_RC=${D}?

rm -f "${D}{__EV_TMP}.sh" 2>/dev/null
trap - EXIT INT TERM HUP
unset __EV_TMP __EV_TMPDIR __EV_STATE __EV_STATE_DIR __EV_HOME
exit ${D}__EV_RC
""".trimIndent()

            outFile.writeText(tmpl)
            outFile.setExecutable(true)

            val modeLabel = if (passwordMode) "密码模式" else "无密码模式"
            val maxLabel = if (maxRuns > 0) "$maxRuns 次" else "无限"
            val failLabel = if (passwordMode) (if (failLimit > 0) "$failLimit 次自毁" else "关闭") else "—"

            """
✅ 加密成功 ($modeLabel · v6)
📄 ${outFile.absolutePath}
🔒 ${if (passwordMode) "PBKDF2-SHA512(1.5M) + AES-256-CBC" else "AES-256-CBC + 混淆内嵌"}
📊 执行上限: $maxLabel
💥 自毁方式: shred 覆盖 3 次
🛡 umask 077 · trap 全覆盖
🔗 兼容: Termux / MT Manager / Linux
▶️ 运行: bash ${outFile.name}
""".trimIndent()
        } catch (e: Exception) {
            "❌ 失败: ${e.message}"
        }
    }

    suspend fun unprotectShellScript(
        inputPath: String, outputPathRaw: String, password: String
    ): String = withContext(Dispatchers.IO) {
        try {
            val src = File(inputPath.trim())
            if (!src.exists()) return@withContext "❌ 保护脚本不存在"
            if (!src.isFile)   return@withContext "❌ 输入路径不是文件"

            val text = src.readText()
            val dRe = Regex("""__EV_D\s*=\s*['"]([A-Za-z0-9+/=]+)['"]""")
            val dM = dRe.find(text) ?: return@withContext "❌ 不是有效的保护脚本"
            val modeRe = Regex("""__EV_MODE\s*=\s*['"](password|nopass)['"]""")
            val modeM = modeRe.find(text)
            val isPwd = modeM?.groupValues?.get(1) == "password"

            val b64 = dM.groupValues[1]
            val ct: ByteArray
            val key: ByteArray
            val iv: ByteArray

            if (isPwd) {
                val blob = Base64.decode(b64, Base64.NO_WRAP)
                if (blob.size < 17) return@withContext "❌ 数据损坏"
                if (String(blob, 0, 8, Charsets.US_ASCII) != "Salted__")
                    return@withContext "❌ 格式错误"
                if (password.isBlank()) return@withContext "❌ 请输入密码"
                val salt = blob.copyOfRange(8, 16)
                ct = blob.copyOfRange(16, blob.size)
                val kp = deriveOpenSSLKey(password, salt)
                key = kp.first; iv = kp.second
            } else {
                val kRe = Regex("""__EV_K_REV\s*=\s*['"]([0-9a-fA-F]+)['"]""")
                val kM = kRe.find(text) ?: return@withContext "❌ 缺少密钥字段"
                val revHex = kM.groupValues[1].reversed()
                if (revHex.length != 96) return@withContext "❌ 密钥长度错误"
                key = revHex.substring(0, 64).chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                iv  = revHex.substring(64, 96).chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                ct = Base64.decode(b64, Base64.NO_WRAP)
            }

            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            val plain = try { cipher.doFinal(ct) }
                        catch (e: Exception) { return@withContext "❌ 密码错误或数据损坏" }

            val raw = outputPathRaw.trim().ifBlank { src.parent ?: "/sdcard" }
            val outName = src.name.removeSuffix("-protected.sh").let {
                if (it.endsWith(".sh")) it else "$it-restored.sh"
            }
            val outFile = run {
                val f = File(raw)
                if (f.isDirectory || raw.endsWith("/")) File(f, outName) else f
            }
            outFile.parentFile?.mkdirs()
            outFile.writeBytes(plain)
            outFile.setExecutable(true)

            """
✅ 已还原 (${if (isPwd) "密码模式" else "无密码模式"})
📄 ${outFile.absolutePath}
📦 ${plain.size} 字节
""".trimIndent()
        } catch (e: Exception) {
            "❌ 解保护失败: ${e.message}"
        }
    }

    private fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    }
}
