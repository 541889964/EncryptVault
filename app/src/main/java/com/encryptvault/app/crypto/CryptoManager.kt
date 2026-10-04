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
        private const val VERSION: Byte = 0x05
        private const val SALT_SIZE = 16
        private const val IV_SIZE = 16
        private const val CHECK_SIZE = 8
        private const val ITERATIONS = 600_000
        private const val KEY_BITS = 256
        private const val GCM_TAG_BITS = 128
        private const val HEADER_SIZE = 1 + SALT_SIZE + IV_SIZE + CHECK_SIZE
        private const val OPENSSL_ITER = 600_000
        private const val OPENSSL_MD = "PBKDF2WithHmacSHA256"
        // 保护脚本模板会用到这个符号, 用占位符避免 Kotlin 插值冲突
        private const val D = "\$"
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
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec: KeySpec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS)
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }

    private fun keyCheck(key: SecretKeySpec): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(key.encoded).copyOf(CHECK_SIZE)
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
            // 兼容 v3/v4/v5 旧格式
            val salt: ByteArray; val iv: ByteArray; val check: ByteArray; val ct: ByteArray
            if (data[0] == VERSION || data[0] == 0x04.toByte() || data[0] == 0x03.toByte()) {
                val chkSize = if (data[0] == 0x03.toByte()) 8 else 8
                val hdr = 1 + SALT_SIZE + IV_SIZE + chkSize
                if (data.size < hdr + 16) return@withContext false
                salt = data.copyOfRange(1, 1 + SALT_SIZE)
                iv = data.copyOfRange(1 + SALT_SIZE, 1 + SALT_SIZE + IV_SIZE)
                check = data.copyOfRange(1 + SALT_SIZE + IV_SIZE, hdr)
                ct = data.copyOfRange(hdr, data.size)
            } else return@withContext false

            val key = deriveKey(password, salt)
            if (!check.contentEquals(keyCheck(key))) return@withContext false
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            writeBytes(uri, cipher.doFinal(ct))
            true
        } catch (e: Exception) { e.printStackTrace(); false }
    }

    // ============================================================
    //  Shell 保护 — 生成 MT + Termux 兼容脚本
    // ============================================================
    private fun deriveOpenSSLKey(password: String, salt: ByteArray): Pair<ByteArray, ByteArray> {
        val factory = SecretKeyFactory.getInstance(OPENSSL_MD)
        val spec = PBEKeySpec(password.toCharArray(), salt, OPENSSL_ITER, 48 * 8)
        val derived = factory.generateSecret(spec).encoded
        return derived.copyOfRange(0, 32) to derived.copyOfRange(32, 48)
    }

    suspend fun protectShellScript(
        inputPath: String, outputPathRaw: String, password: String
    ): String = withContext(Dispatchers.IO) {
        try {
            if (password.length < 8) return@withContext "❌ 密码至少 8 位"
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
            val salt = ByteArray(8).also { SecureRandom().nextBytes(it) }
            val (key, iv) = deriveOpenSSLKey(password, salt)

            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            val ct = cipher.doFinal(plain)

            // openssl 兼容格式: "Salted__" + 8B salt + ciphertext
            val blob = ByteArray(16 + ct.size)
            System.arraycopy("Salted__".toByteArray(Charsets.US_ASCII), 0, blob, 0, 8)
            System.arraycopy(salt, 0, blob, 8, 8)
            System.arraycopy(ct, 0, blob, 16, ct.size)

            val b64 = Base64.encodeToString(blob, Base64.NO_WRAP)

            // 保护脚本模板 — 使用 ${'$'} 转义避免 Kotlin 插值
            val D = "${'$'}"
            val tmpl = """
#!/data/data/com.termux/files/usr/bin/bash
# 也可在 MT 管理器中运行:  sh 本文件.sh
# ============================================================
#  EncryptVault Protected Script (v5 · MT Compatible)
#  PBKDF2-HMAC-SHA256 (600,000 轮) + AES-256-CBC
#  密钥由密码派生 · 脚本内无任何密钥信息
# ============================================================

# ⚠️ 警告
printf '\n⚠️  本脚本会执行解密后的 Bash 代码, 请确认来源可信\n\n' >&2

# 严格 umask — 所有新文件默认为 0600
umask 077

# 自动检测临时目录 (Termux / MT / Linux)
__EV_TMPDIR=""
for __d in "${D}{TMPDIR:-}" "/data/data/com.termux/files/usr/tmp" "/tmp" "."; do
    if [ -n "${D}__d" ] && [ -d "${D}__d" ] && [ -w "${D}__d" ]; then
        __EV_TMPDIR="${D}__d"; break
    fi
done
if [ -z "${D}__EV_TMPDIR" ]; then
    printf '❌ 找不到可写的临时目录\n' >&2
    exit 1
fi

# 检查 openssl
if ! command -v openssl >/dev/null 2>&1; then
    printf '❌ 需要 openssl, 请先安装\n' >&2
    printf '   Termux: pkg install openssl\n' >&2
    printf '   MT:     内置终端应已包含\n' >&2
    exit 1
fi

__EV_D='$b64'

printf '🔐 输入密码: ' >&2
IFS= read -r -s __EV_PWD
printf '\n' >&2

if [ -z "${D}{__EV_PWD:-}" ]; then
    printf '❌ 密码不能为空\n' >&2
    exit 1
fi

# 不可预测的临时文件名
__EV_TMP="${D}{__EV_TMPDIR}/.ev_${D}${D}_${D}(date +%s)"

# 捕获所有退出信号清理临时文件
trap 'rm -f "${D}{__EV_TMP}.enc" "${D}{__EV_TMP}.sh" 2>/dev/null' EXIT INT TERM HUP

# 1. Base64 解码
printf '%s' "${D}__EV_D" | base64 -d > "${D}{__EV_TMP}.enc" 2>/dev/null
unset __EV_D
if [ ! -s "${D}{__EV_TMP}.enc" ]; then
    printf '❌ 数据损坏\n' >&2
    exit 1
fi

# 2. 解密 — 密码走 stdin, 不出现在进程列表
printf '%s\n' "${D}__EV_PWD" | \
    openssl enc -d -aes-256-cbc -pbkdf2 -iter 600000 -md sha256 \
        -pass stdin -in "${D}{__EV_TMP}.enc" -out "${D}{__EV_TMP}.sh" 2>/dev/null
__EV_RC=${D}?
unset __EV_PWD
rm -f "${D}{__EV_TMP}.enc"

if [ ${D}__EV_RC -ne 0 ] || [ ! -s "${D}{__EV_TMP}.sh" ]; then
    printf '❌ 密码错误或文件损坏\n' >&2
    exit 1
fi

chmod 700 "${D}{__EV_TMP}.sh" 2>/dev/null

# 3. 执行
bash "${D}{__EV_TMP}.sh"
__EV_RC=${D}?

# 4. 清理
rm -f "${D}{__EV_TMP}.sh" 2>/dev/null
trap - EXIT INT TERM HUP
unset __EV_TMP __EV_TMPDIR
exit ${D}__EV_RC
""".trimIndent()

            outFile.writeText(tmpl)
            outFile.setExecutable(true)

            """
✅ 加密成功
📄 ${outFile.absolutePath}
🔒 PBKDF2-SHA256(600K) + AES-256-CBC
🛡 脚本内无密钥 · 密码走 stdin · umask 077
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

            val blob = Base64.decode(dM.groupValues[1], Base64.NO_WRAP)
            if (blob.size < 17) return@withContext "❌ 数据损坏"
            if (String(blob, 0, 8, Charsets.US_ASCII) != "Salted__")
                return@withContext "❌ 格式错误"

            val salt = blob.copyOfRange(8, 16)
            val ct = blob.copyOfRange(16, blob.size)
            val (key, iv) = deriveOpenSSLKey(password, salt)

            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            val plain = try { cipher.doFinal(ct) }
                        catch (e: Exception) { return@withContext "❌ 密码错误" }

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
✅ 已还原
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
