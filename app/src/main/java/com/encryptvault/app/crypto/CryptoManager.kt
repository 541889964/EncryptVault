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
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class FileItem(val uri: Uri, val name: String, val size: String,
    val status: ProcessStatus = ProcessStatus.PENDING)
enum class ProcessStatus { PENDING, ENCRYPTED, DECRYPTED, DONE, ERROR }

class CryptoManager(private val ctx: Context) {

    companion object {
        private const val VERSION: Byte = 0x0B
        private const val SALT_SIZE = 16
        private const val IV_SIZE = 16
        private const val CHECK_SIZE = 8
        private const val PBKDF2_ITER = 2_000_000
        private const val PBKDF2_PRF = "PBKDF2WithHmacSHA512"
        private const val KEY_BITS = 256
        private const val GCM_TAG_BITS = 128
        private const val OPENSSL_ITER = 2_000_000
        private const val OPENSSL_MD = "PBKDF2WithHmacSHA512"
        private const val HKDF_INFO = "EncryptVault-file-v11"
        private const val HEADER_SIZE = 1 + SALT_SIZE + SALT_SIZE + IV_SIZE + CHECK_SIZE

        private val XOR_R = byteArrayOf(
            0x3A, 0x7F, 0x2C, 0x91.toByte(), 0x4E, 0xB5.toByte(), 0x11, 0x63,
            0xD7.toByte(), 0x8A.toByte(), 0x25, 0xF9.toByte(), 0x06, 0x44, 0xBB.toByte(), 0x72,
            0x9D.toByte(), 0xE3.toByte(), 0x58, 0xAF.toByte(), 0x31, 0x84.toByte(), 0xCD.toByte(), 0x1A,
            0x76, 0xE8.toByte(), 0x50, 0x02, 0x9B.toByte(), 0x35, 0xC1.toByte(), 0x68)
        private val R_HEX = XOR_R.joinToString("") { "%02x".format(it) }

        private const val CONST_HEX =
            "a3f1c8e29b4d60715f2e8a3c9d17b5e4f028c6a9d3b7e15f824a6c0d9e3f7b12"
    }

    private var sessionPwdHash: String? = null
    private var sessionMasterSalt: ByteArray? = null
    private var sessionMasterKey: SecretKeySpec? = null

    private fun hashPwd(p: String): String =
        MessageDigest.getInstance("SHA-256").digest(p.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun ensureMasterKey(password: String): Pair<ByteArray, SecretKeySpec> {
        val h = hashPwd(password)
        if (sessionPwdHash == h && sessionMasterSalt != null && sessionMasterKey != null)
            return sessionMasterSalt!! to sessionMasterKey!!
        val salt = ByteArray(SALT_SIZE).also { SecureRandom().nextBytes(it) }
        val factory = SecretKeyFactory.getInstance(PBKDF2_PRF)
        val spec: KeySpec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITER, KEY_BITS)
        val key = SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
        sessionPwdHash = h; sessionMasterSalt = salt; sessionMasterKey = key
        return salt to key
    }

    private fun deriveMasterKeyWithSalt(password: String, salt: ByteArray): SecretKeySpec {
        if (sessionMasterSalt?.contentEquals(salt) == true && sessionMasterKey != null)
            return sessionMasterKey!!
        val factory = SecretKeyFactory.getInstance(PBKDF2_PRF)
        val spec: KeySpec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITER, KEY_BITS)
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }

    private fun deriveFileKey(masterKey: SecretKeySpec, fileSalt: ByteArray): SecretKeySpec {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(masterKey.encoded, "HmacSHA256"))
        val prk = mac.doFinal(fileSalt)
        val mac2 = Mac.getInstance("HmacSHA256")
        mac2.init(SecretKeySpec(prk, "HmacSHA256"))
        val okm = mac2.doFinal(HKDF_INFO.toByteArray(Charsets.US_ASCII))
        return SecretKeySpec(okm.copyOf(32), "AES")
    }

    private fun keyCheck(key: SecretKeySpec): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key.encoded, "HmacSHA256"))
        return mac.doFinal("EncryptVault-KCV-v11".toByteArray(Charsets.US_ASCII)).copyOf(CHECK_SIZE)
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
    private fun sha16(d: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(d).copyOf(8).toHex()

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

    suspend fun encryptFile(uri: Uri, password: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val plain = readBytes(uri)
            val (masterSalt, masterKey) = ensureMasterKey(password)
            val fileSalt = ByteArray(SALT_SIZE).also { SecureRandom().nextBytes(it) }
            val iv = ByteArray(IV_SIZE).also { SecureRandom().nextBytes(it) }
            val fileKey = deriveFileKey(masterKey, fileSalt)
            val kcv = keyCheck(masterKey)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, fileKey, GCMParameterSpec(GCM_TAG_BITS, iv))
            val ct = cipher.doFinal(plain)
            val out = ByteArray(HEADER_SIZE + ct.size)
            out[0] = VERSION
            System.arraycopy(masterSalt, 0, out, 1, SALT_SIZE)
            System.arraycopy(fileSalt, 0, out, 1 + SALT_SIZE, SALT_SIZE)
            System.arraycopy(iv, 0, out, 1 + SALT_SIZE * 2, IV_SIZE)
            System.arraycopy(kcv, 0, out, 1 + SALT_SIZE * 2 + IV_SIZE, CHECK_SIZE)
            System.arraycopy(ct, 0, out, HEADER_SIZE, ct.size)
            writeBytes(uri, out); true
        } catch (e: Exception) { e.printStackTrace(); false }
    }

    suspend fun decryptFile(uri: Uri, password: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val data = readBytes(uri)
            if (data.size < HEADER_SIZE + 16 || data[0] != VERSION) return@withContext false
            val masterSalt = data.copyOfRange(1, 1 + SALT_SIZE)
            val fileSalt = data.copyOfRange(1 + SALT_SIZE, 1 + SALT_SIZE * 2)
            val iv = data.copyOfRange(1 + SALT_SIZE * 2, 1 + SALT_SIZE * 2 + IV_SIZE)
            val kcv = data.copyOfRange(1 + SALT_SIZE * 2 + IV_SIZE, HEADER_SIZE)
            val ct = data.copyOfRange(HEADER_SIZE, data.size)
            val masterKey = deriveMasterKeyWithSalt(password, masterSalt)
            if (!kcv.contentEquals(keyCheck(masterKey))) return@withContext false
            sessionPwdHash = hashPwd(password); sessionMasterSalt = masterSalt; sessionMasterKey = masterKey
            val fileKey = deriveFileKey(masterKey, fileSalt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, fileKey, GCMParameterSpec(GCM_TAG_BITS, iv))
            writeBytes(uri, cipher.doFinal(ct)); true
        } catch (e: Exception) { e.printStackTrace(); false }
    }

    // ============================================================
    //  Shell 保护 v11 — 含全套反逆向
    // ============================================================
    private fun deriveOpenSSLKey(password: String, salt: ByteArray): Pair<ByteArray, ByteArray> {
        val factory = SecretKeyFactory.getInstance(OPENSSL_MD)
        val spec = PBEKeySpec(password.toCharArray(), salt, OPENSSL_ITER, 48 * 8)
        val derived = factory.generateSecret(spec).encoded
        return derived.copyOfRange(0, 32) to derived.copyOfRange(32, 48)
    }

    private fun transformNoPass(key: ByteArray, iv: ByteArray, dkHex: String): List<String> {
        val km = (key + iv).toHex()  // 96 hex
        // 层1: XOR DK
        val t1 = StringBuilder()
        for (i in 0 until 96 step 2) {
            val b = km.substring(i, i + 2).toInt(16)
            val d = dkHex.substring(i, i + 2).toInt(16)
            t1.append("%02x".format(b xor d))
        }
        // 层2: 按 4-hex 单元反序
        val t2 = StringBuilder()
        var i = 0
        while (i < 96) { t2.insert(0, t1.substring(i, i + 4)); i += 4 }
        // 层3: XOR CONST
        val t3 = StringBuilder()
        for (i in 0 until 96 step 2) {
            val b = t2.substring(i, i + 2).toInt(16)
            val c = CONST_HEX.substring(i, i + 2).toInt(16)
            t3.append("%02x".format(b xor c))
        }
        val s = t3.toString()
        return (0 until 6).map { s.substring(it * 16, (it + 1) * 16) }
    }

    suspend fun protectShellScript(
        inputPath: String, outputPathRaw: String, password: String,
        passwordMode: Boolean, maxRuns: Int, failLimit: Int,
        deviceBind: Boolean, validDays: Int
    ): String = withContext(Dispatchers.IO) {
        try {
            if (passwordMode && password.length < 8) return@withContext "❌ 密码至少 8 位"
            val src = File(inputPath.trim())
            if (!src.exists()) return@withContext "❌ 源文件不存在"
            if (!src.isFile)   return@withContext "❌ 输入路径不是文件"
            if (java.nio.file.Files.isSymbolicLink(src.toPath()))
                return@withContext "❌ 源文件是软链接，拒绝加密"

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
            val segs: List<String>
            val deviceSalt: String
            val deviceFp: String

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
                segs = listOf("","","","","","")
                deviceSalt = ""; deviceFp = ""
            } else {
                val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
                val iv = ByteArray(16).also { SecureRandom().nextBytes(it) }
                val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
                val ct = cipher.doFinal(plain)
                b64 = Base64.encodeToString(ct, Base64.NO_WRAP)

                // 设备绑定: DK = SHA256(salt + FP_HASH)
                val ds = ByteArray(16).also { SecureRandom().nextBytes(it) }
                val fpRaw = "GEN_HOST|GEN_MODEL|GEN_BRAND"
                val fpH = MessageDigest.getInstance("SHA-256")
                    .digest(fpRaw.toByteArray()).joinToString("") { "%02x".format(it) }.substring(0, 32)
                val dk = if (deviceBind) {
                    val md = MessageDigest.getInstance("SHA-256")
                    md.update(ds); md.update(fpH.toByteArray())
                    md.digest().joinToString("") { "%02x".format(it) }
                } else {
                    // 不绑定设备时, DK 用一个固定常量, 运行端用同样方式
                    val md = MessageDigest.getInstance("SHA-256")
                    md.update(ds); md.update("FIXED".toByteArray())
                    md.digest().joinToString("") { "%02x".format(it) }
                }
                segs = transformNoPass(key, iv, dk)
                deviceSalt = ds.joinToString("") { "%02x".format(it) }
                deviceFp = fpH
            }

            val id = sha16(plain)
            val mode = if (passwordMode) "password" else "nopass"
            val expTs = System.currentTimeMillis() / 1000 + validDays.toLong() * 86400
            val bindFlag = if (deviceBind) "1" else "0"

            val nopassVars = if (!passwordMode) """
__EV_P1='${segs[0]}'
__EV_P2='${segs[1]}'
__EV_P3='${segs[2]}'
__EV_P4='${segs[3]}'
__EV_P5='${segs[4]}'
__EV_P6='${segs[5]}'
__EV_SALT='$deviceSalt'
__EV_CONST='$CONST_HEX'
__EV_BIND=$bindFlag
""" else ""

            val expLine = if (validDays > 0) "__EV_EXP=$expTs" else "__EV_EXP=0"

            // ==== 反逆向头部 ====
            val antiRe = """
# ---------- 反逆向 (12 项) ----------
# 1. 反调试 -x
case "${D}-" in *x*) exit 1 ;; esac
# 2. BASH_XTRACEFD
[ -n "${D}{BASH_XTRACEFD:-}" ] && exit 1
# 3. SHELLOPTS 含 xtrace
case ":${D}{SHELLOPTS:-}:" in *:xtrace:*) exit 1 ;; esac
# 4. PS4 非默认
[ "${D}{PS4:-+ }" != "+ " ] && exit 1
# 5. TracerPid
if [ -r /proc/self/status ]; then
    if grep -qE '^TracerPid:\s*[1-9]' /proc/self/status 2>/dev/null; then exit 1; fi
fi
# 6. LD_PRELOAD
[ -n "${D}{LD_PRELOAD:-}" ] && exit 1
# 7. 必须非 source 调用
[ -z "${D}{BASH_SOURCE[0]:-}" ] && exit 1
# 8. 定位自身
__EV_SELF="${D}{BASH_SOURCE[0]}"
[ -f "${D}__EV_SELF" ] || __EV_SELF="${D}0"
[ -f "${D}__EV_SELF" ] || exit 1
# 9. 拒绝软链接
[ -L "${D}__EV_SELF" ] && exit 1
# 10. 父进程检测
if [ -r "/proc/${D}PPID/cmdline" ]; then
    __EV_PP=$(tr '\0' ' ' < "/proc/${D}PPID/cmdline" 2>/dev/null)
    case "${D}__EV_PP" in
        *sed*|*awk*|*grep*|*"cat "*|*tee*|*strace*|*ltrace*|*gdb*|*python*|*perl*|*ruby*|*node*|*hexdump*|*xxd*|*strings*)
            printf '❌ 检测到分析工具\n' >&2; exit 1 ;;
    esac
fi
# 11. 关键命令不得被 shell 函数覆盖
for __c in openssl sha256sum base64 date hostname getprop cut tail head; do
    if declare -F "${D}__c" >/dev/null 2>&1; then
        printf '❌ 检测到命令被覆盖: ${D}__c\n' >&2; exit 1
    fi
done
# 12. 自校验 SHA256 (最后一行 #HASH:xxx)
__EV_LAST=$(tail -n 1 "${D}__EV_SELF")
case "${D}__EV_LAST" in
    \#HASH:*) __EV_EXP_HASH="${D}{__EV_LAST#\#HASH:}" ;;
    *) printf '❌ 脚本结构异常\n' >&2; exit 1 ;;
esac
__EV_ACT_HASH=$(head -n -1 "${D}__EV_SELF" | sha256sum | awk '{print ${D}1}')
if [ "${D}__EV_EXP_HASH" != "${D}__EV_ACT_HASH" ]; then
    printf '❌ 脚本已被修改, 拒绝运行\n' >&2
    command -v shred >/dev/null 2>&1 && shred -u -n 2 -z "${D}__EV_SELF" 2>/dev/null || rm -f "${D}__EV_SELF"
    exit 1
fi
unset __EV_LAST __EV_EXP_HASH __EV_ACT_HASH __EV_PP
"""

            val timeCheck = if (validDays > 0) """
# ---------- 时间窗口 ----------
__EV_NOW=$(date +%s)
if [ "${D}__EV_NOW" -gt ${D}__EV_EXP ]; then
    printf '💥 脚本已过期\n' >&2
    command -v shred >/dev/null 2>&1 && shred -u -n 2 -z "${D}__EV_SELF" 2>/dev/null || rm -f "${D}__EV_SELF"
    exit 1
fi
""" else ""

            val nopassDecrypt = if (!passwordMode) """
# ---------- 无密码模式: 逆三层变换 ----------
if [ "${D}__EV_BIND" = "1" ]; then
    __EV_HOSTN=$(hostname 2>/dev/null || echo "")
    __EV_MODEL=$(getprop ro.product.model 2>/dev/null || echo "")
    __EV_BRAND=$(getprop ro.product.brand 2>/dev/null || echo "")
    __EV_FP_RAW="${D}{__EV_HOSTN}|${D}{__EV_MODEL}|${D}{__EV_BRAND}"
else
    __EV_FP_RAW="FIXED"
fi
__EV_DK=$(printf '%s%s' "${D}__EV_SALT" "${D}__EV_FP_RAW" | sha256sum | awk '{print ${D}1}')
unset __EV_HOSTN __EV_MODEL __EV_BRAND __EV_FP_RAW __EV_SALT

__EV_T3="${D}{__EV_P1}${D}{__EV_P2}${D}{__EV_P3}${D}{__EV_P4}${D}{__EV_P5}${D}{__EV_P6}"
unset __EV_P1 __EV_P2 __EV_P3 __EV_P4 __EV_P5 __EV_P6

# 逆层3
__EV_T2=""
__EV_I=0
while [ ${D}__EV_I -lt 96 ]; do
    __EV_B=$((16#${D}{__EV_T3:${D}__EV_I:2}))
    __EV_C=$((16#${D}{__EV_CONST:${D}__EV_I:2}))
    __EV_T2="${D}{__EV_T2}$(printf '%02x' $(( __EV_B ^ __EV_C )))"
    __EV_I=$((__EV_I+2))
done
# 逆层2 (4-hex 单元反序)
__EV_T1=""
__EV_I=0
while [ ${D}__EV_I -lt 96 ]; do
    __EV_T1="${D}{__EV_T2:${D}__EV_I:4}${D}{__EV_T1}"
    __EV_I=$((__EV_I+4))
done
# 逆层1
__EV_KM=""
__EV_I=0
while [ ${D}__EV_I -lt 96 ]; do
    __EV_B=$((16#${D}{__EV_T1:${D}__EV_I:2}))
    __EV_D=$((16#${D}{__EV_DK:${D}__EV_I:2}))
    __EV_KM="${D}{__EV_KM}$(printf '%02x' $(( __EV_B ^ __EV_D )))"
    __EV_I=$((__EV_I+2))
done
unset __EV_T3 __EV_T2 __EV_T1 __EV_DK __EV_CONST __EV_I __EV_B __EV_C __EV_D

__EV_KEY=$(printf '%s' "${D}__EV_KM" | cut -c1-64)
__EV_IV=$(printf '%s' "${D}__EV_KM" | cut -c65-96)
unset __EV_KM
""" else """
# ---------- 密码模式 ----------
__EV_MAX_TRIES=${D}__EV_FAIL
[ "${D}__EV_MAX_TRIES" -le 0 ] && __EV_MAX_TRIES=1
__EV_OK=0
__EV_TRIES=0
while [ ${D}__EV_TRIES -lt ${D}__EV_MAX_TRIES ]; do
    printf '🔐 输入密码: ' >&2
    IFS= read -r -s __EV_PWD
    printf '\n' >&2
    [ -z "${D}__EV_PWD" ] && { __EV_TRIES=$((__EV_TRIES+1)); continue; }
    printf '⏳ 派生密钥 (约 3 秒)...\n' >&2
    printf '%s\n' "${D}__EV_PWD" | openssl enc -d -aes-256-cbc \\
        -pbkdf2 -iter 2000000 -md sha512 -pass stdin \\
        -in "${D}{__EV_TMP}.enc" -out "${D}{__EV_TMP}.sh" 2>/dev/null
    __EV_RC=${D}?
    unset __EV_PWD
    if [ ${D}__EV_RC -eq 0 ] && [ -s "${D}{__EV_TMP}.sh" ]; then __EV_OK=1; break; fi
    rm -f "${D}{__EV_TMP}.sh"
    __EV_TRIES=$((__EV_TRIES+1))
    printf '❌ 密码错误 (%s/%s)\n' "${D}__EV_TRIES" "${D}__EV_MAX_TRIES" >&2
done
rm -f "${D}{__EV_TMP}.enc"
[ ${D}__EV_OK -ne 1 ] && { printf '💥 密码错误过多, 自毁\n' >&2; shred -u "$0" 2>/dev/null || rm -f "$0"; exit 1; }
"""

            val tmpl = """
#!/data/data/com.termux/files/usr/bin/bash
# EncryptVault Protected Script v11 (${mode})
umask 077
$antiRe
$timeCheck
command -v openssl >/dev/null 2>&1 || exit 1
command -v sha256sum >/dev/null 2>&1 || exit 1

__EV_TMP="${D}{TMPDIR:-/tmp}/.ev_${D}${D}_${D}RANDOM"
trap 'rm -f "${D}{__EV_TMP}.enc" "${D}{__EV_TMP}.sh" 2>/dev/null' EXIT INT TERM HUP

__EV_D='$b64'
printf '%s' "${D}__EV_D" | base64 -d > "${D}{__EV_TMP}.enc" 2>/dev/null
unset __EV_D
[ -s "${D}{__EV_TMP}.enc" ] || exit 1

$nopassVars
$expLine
__EV_FAIL=$failLimit
__EV_MAX=$maxRuns

$nopassDecrypt

if [ "${D}__EV_MODE:-}" = "password" ] || [ -n "${D}{__EV_KEY:-}" ]; then :; fi
""".trimIndent()

            // 组装: 密码模式需要额外处理, 无密码直接解密
            val headTail = """
$nopassDecrypt

if [ "$mode" = "password" ]; then
    :
else
    openssl enc -d -aes-256-cbc -K "${D}__EV_KEY" -iv "${D}__EV_IV" \\
        -in "${D}{__EV_TMP}.enc" -out "${D}{__EV_TMP}.sh" 2>/dev/null
    __EV_RC=${D}?
    rm -f "${D}{__EV_TMP}.enc"
    unset __EV_KEY __EV_IV
    if [ ${D}__EV_RC -ne 0 ] || [ ! -s "${D}{__EV_TMP}.sh" ]; then
        printf '❌ 解密失败 (设备/时间不符)\n' >&2
        command -v shred >/dev/null 2>&1 && shred -u -n 2 -z "${D}__EV_SELF" 2>/dev/null || rm -f "${D}__EV_SELF"
        exit 1
    fi
fi

# 运行次数限制
if [ ${D}{__EV_MAX:-0} -gt 0 ]; then
    __EV_STATE="${D}{HOME}/.ev_state/${D}__EV_ID"
    mkdir -p "${D}{HOME}/.ev_state" 2>/dev/null
    __EV_RUNS=0
    [ -f "${D}__EV_STATE" ] && __EV_RUNS=$(cat "${D}__EV_STATE" 2>/dev/null || echo 0)
    case "${D}__EV_RUNS" in ''|*[!0-9]*) __EV_RUNS=0 ;; esac
    if [ ${D}__EV_RUNS -ge ${D}__EV_MAX ]; then
        printf '❌ 已达最大执行次数\n' >&2
        command -v shred >/dev/null 2>&1 && shred -u -n 2 -z "${D}__EV_SELF" 2>/dev/null || rm -f "${D}__EV_SELF"
        exit 1
    fi
    echo $((__EV_RUNS+1)) > "${D}__EV_STATE"
    chmod 600 "${D}__EV_STATE" 2>/dev/null
fi

chmod 700 "${D}{__EV_TMP}.sh" 2>/dev/null
bash "${D}{__EV_TMP}.sh"
__EV_RC=${D}?
rm -f "${D}{__EV_TMP}.sh"
trap - EXIT INT TERM HUP
exit ${D}__EV_RC
"""

            // 完整脚本: 头 + 反逆向 + 分片 + 解密分支 + 尾部
            val full = buildString {
                append("#!/data/data/com.termux/files/usr/bin/bash\n")
                append("# EncryptVault Protected Script v11 (")
                append(mode); append(")\n")
                append("umask 077\n\n")
                append(antiRe)
                append(timeCheck)
                append("\ncommand -v openssl >/dev/null 2>&1 || exit 1\n")
                append("command -v sha256sum >/dev/null 2>&1 || exit 1\n\n")
                append("__EV_TMP=\"")
                append(D); append("{TMPDIR:-/tmp}/.ev_")
                append(D); append("$"); append(D); append("RANDOM\"\n")
                append("trap 'rm -f \"")
                append(D); append("{__EV_TMP}.enc\" \"")
                append(D); append("{__EV_TMP}.sh\" 2>/dev/null' EXIT INT TERM HUP\n\n")
                append("__EV_MODE='$mode'\n")
                append("__EV_D='$b64'\n")
                if (!passwordMode) {
                    append("__EV_P1='"); append(segs[0]); append("'\n")
                    append("__EV_P2='"); append(segs[1]); append("'\n")
                    append("__EV_P3='"); append(segs[2]); append("'\n")
                    append("__EV_P4='"); append(segs[3]); append("'\n")
                    append("__EV_P5='"); append(segs[4]); append("'\n")
                    append("__EV_P6='"); append(segs[5]); append("'\n")
                    append("__EV_SALT='$deviceSalt'\n")
                    append("__EV_CONST='$CONST_HEX'\n")
                    append("__EV_BIND=$bindFlag\n")
                }
                if (validDays > 0) { append("__EV_EXP=$expTs\n") } else { append("__EV_EXP=0\n") }
                append("__EV_MAX=$maxRuns\n")
                append("__EV_FAIL=$failLimit\n")
                append("__EV_ID='$id'\n\n")
                append("printf '%s' \"")
                append(D); append("__EV_D\" | base64 -d > \"")
                append(D); append("{__EV_TMP}.enc\" 2>/dev/null\n")
                append("unset __EV_D\n")
                append("[ -s \"")
                append(D); append("{__EV_TMP}.enc\" ] || exit 1\n\n")
                if (passwordMode) {
                    append("__EV_MAX_TRIES=$failLimit\n")
                    append("[ \"")
                    append(D); append("__EV_MAX_TRIES\" -le 0 ] && __EV_MAX_TRIES=1\n")
                    append("__EV_OK=0\n__EV_TRIES=0\n")
                    append("while [ ")
                    append(D); append("__EV_TRIES -lt ")
                    append(D); append("__EV_MAX_TRIES ]; do\n")
                    append("    printf '🔐 输入密码: ' >&2\n")
                    append("    IFS= read -r -s __EV_PWD\n")
                    append("    printf '\\n' >&2\n")
                    append("    [ -z \"")
                    append(D); append("__EV_PWD\" ] && { __EV_TRIES=$((__EV_TRIES+1)); continue; }\n")
                    append("    printf '⏳ 派生密钥 (约 3 秒)...\\n' >&2\n")
                    append("    printf '%s\\n' \"")
                    append(D); append("__EV_PWD\" | openssl enc -d -aes-256-cbc \\\n")
                    append("        -pbkdf2 -iter 2000000 -md sha512 -pass stdin \\\n")
                    append("        -in \"")
                    append(D); append("{__EV_TMP}.enc\" -out \"")
                    append(D); append("{__EV_TMP}.sh\" 2>/dev/null\n")
                    append("    __EV_RC=$?\n    unset __EV_PWD\n")
                    append("    if [ ")
                    append(D); append("__EV_RC -eq 0 ] && [ -s \"")
                    append(D); append("{__EV_TMP}.sh\" ]; then __EV_OK=1; break; fi\n")
                    append("    rm -f \"")
                    append(D); append("{__EV_TMP}.sh\"\n")
                    append("    __EV_TRIES=$((__EV_TRIES+1))\n")
                    append("    printf '❌ 密码错误 (%s/%s)\\n' \"")
                    append(D); append("__EV_TRIES\" \"")
                    append(D); append("__EV_MAX_TRIES\" >&2\n")
                    append("done\n")
                    append("rm -f \"")
                    append(D); append("{__EV_TMP}.enc\"\n")
                    append("[ ")
                    append(D); append("__EV_OK -ne 1 ] && { shred -u \"")
                    append(D); append("__EV_SELF\" 2>/dev/null || rm -f \"")
                    append(D); append("__EV_SELF\"; exit 1; }\n")
                } else {
                    // 无密码逆变换
                    append("# 逆三层变换\n")
                    append("if [ \"")
                    append(D); append("__EV_BIND\" = \"1\" ]; then\n")
                    append("    __EV_HN=$(hostname 2>/dev/null || echo \"\")\n")
                    append("    __EV_MD=$(getprop ro.product.model 2>/dev/null || echo \"\")\n")
                    append("    __EV_BR=$(getprop ro.product.brand 2>/dev/null || echo \"\")\n")
                    append("    __EV_FPR=\"")
                    append(D); append("{__EV_HN}|")
                    append(D); append("{__EV_MD}|")
                    append(D); append("{__EV_BR}\"\n")
                    append("else\n    __EV_FPR=\"FIXED\"\nfi\n")
                    append("__EV_DK=$(printf '%s%s' \"")
                    append(D); append("__EV_SALT\" \"")
                    append(D); append("__EV_FPR\" | sha256sum | awk '{print $1}')\n")
                    append("unset __EV_HN __EV_MD __EV_BR __EV_FPR __EV_SALT\n\n")
                    append("__EV_T3=\"")
                    append(D); append("{__EV_P1}")
                    append(D); append("{__EV_P2}")
                    append(D); append("{__EV_P3}")
                    append(D); append("{__EV_P4}")
                    append(D); append("{__EV_P5}")
                    append(D); append("{__EV_P6}\"\n")
                    append("unset __EV_P1 __EV_P2 __EV_P3 __EV_P4 __EV_P5 __EV_P6\n\n")
                    append("__EV_T2=\"\"\n__EV_I=0\n")
                    append("while [ ")
                    append(D); append("__EV_I -lt 96 ]; do\n")
                    append("    __EV_B=$((16#")
                    append(D); append("{__EV_T3:")
                    append(D); append("__EV_I:2}))\n")
                    append("    __EV_C=$((16#")
                    append(D); append("{__EV_CONST:")
                    append(D); append("__EV_I:2}))\n")
                    append("    __EV_T2=\"")
                    append(D); append("{__EV_T2}$(printf '%02x' $(( __EV_B ^ __EV_C )))\"\n")
                    append("    __EV_I=$((__EV_I+2))\n")
                    append("done\n")
                    append("__EV_T1=\"\"\n__EV_I=0\n")
                    append("while [ ")
                    append(D); append("__EV_I -lt 96 ]; do\n")
                    append("    __EV_T1=\"")
                    append(D); append("{__EV_T2:")
                    append(D); append("__EV_I:4}")
                    append(D); append("{__EV_T1}\"\n")
                    append("    __EV_I=$((__EV_I+4))\n")
                    append("done\n")
                    append("__EV_KM=\"\"\n__EV_I=0\n")
                    append("while [ ")
                    append(D); append("__EV_I -lt 96 ]; do\n")
                    append("    __EV_B=$((16#")
                    append(D); append("{__EV_T1:")
                    append(D); append("__EV_I:2}))\n")
                    append("    __EV_DD=$((16#")
                    append(D); append("{__EV_DK:")
                    append(D); append("__EV_I:2}))\n")
                    append("    __EV_KM=\"")
                    append(D); append("{__EV_KM}$(printf '%02x' $(( __EV_B ^ __EV_DD )))\"\n")
                    append("    __EV_I=$((__EV_I+2))\n")
                    append("done\n")
                    append("unset __EV_T3 __EV_T2 __EV_T1 __EV_DK __EV_CONST __EV_I __EV_B __EV_C __EV_DD\n\n")
                    append("__EV_KEY=$(printf '%s' \"")
                    append(D); append("__EV_KM\" | cut -c1-64)\n")
                    append("__EV_IV=$(printf '%s' \"")
                    append(D); append("__EV_KM\" | cut -c65-96)\n")
                    append("unset __EV_KM\n\n")
                    append("openssl enc -d -aes-256-cbc -K \"")
                    append(D); append("__EV_KEY\" -iv \"")
                    append(D); append("__EV_IV\" \\\n")
                    append("    -in \"")
                    append(D); append("{__EV_TMP}.enc\" -out \"")
                    append(D); append("{__EV_TMP}.sh\" 2>/dev/null\n")
                    append("__EV_RC=$?\n")
                    append("rm -f \"")
                    append(D); append("{__EV_TMP}.enc\"\n")
                    append("unset __EV_KEY __EV_IV\n")
                    append("if [ ")
                    append(D); append("__EV_RC -ne 0 ] || [ ! -s \"")
                    append(D); append("{__EV_TMP}.sh\" ]; then\n")
                    append("    printf '❌ 解密失败 (设备/时间不符)\\n' >&2\n")
                    append("    command -v shred >/dev/null 2>&1 && shred -u -n 2 -z \"")
                    append(D); append("__EV_SELF\" 2>/dev/null || rm -f \"")
                    append(D); append("__EV_SELF\"\n")
                    append("    exit 1\n")
                    append("fi\n")
                }
                append("\nif [ \"")
                append(D); append("__EV_MAX\" -gt 0 ]; then\n")
                append("    __EV_STATE=\"")
                append(D); append("{HOME}/.ev_state/")
                append(D); append("__EV_ID\"\n")
                append("    mkdir -p \"")
                append(D); append("{HOME}/.ev_state\" 2>/dev/null\n")
                append("    __EV_RUNS=0\n")
                append("    [ -f \"")
                append(D); append("__EV_STATE\" ] && __EV_RUNS=$(cat \"")
                append(D); append("__EV_STATE\" 2>/dev/null || echo 0)\n")
                append("    case \"")
                append(D); append("__EV_RUNS\" in ''|*[!0-9]*) __EV_RUNS=0 ;; esac\n")
                append("    if [ ")
                append(D); append("__EV_RUNS -ge ")
                append(D); append("__EV_MAX ]; then\n")
                append("        printf '❌ 已达最大执行次数\\n' >&2\n")
                append("        command -v shred >/dev/null 2>&1 && shred -u -n 2 -z \"")
                append(D); append("__EV_SELF\" 2>/dev/null || rm -f \"")
                append(D); append("__EV_SELF\"\n")
                append("        exit 1\n    fi\n")
                append("    echo $((__EV_RUNS+1)) > \"")
                append(D); append("__EV_STATE\"\n")
                append("    chmod 600 \"")
                append(D); append("__EV_STATE\" 2>/dev/null\n")
                append("fi\n\n")
                append("chmod 700 \"")
                append(D); append("{__EV_TMP}.sh\" 2>/dev/null\n")
                append("bash \"")
                append(D); append("{__EV_TMP}.sh\"\n")
                append("__EV_RC=$?\n")
                append("rm -f \"")
                append(D); append("{__EV_TMP}.sh\"\n")
                append("trap - EXIT INT TERM HUP\n")
                append("exit $__EV_RC\n")
            }

            outFile.writeText(full)
            outFile.setExecutable(true)

            // 追加自校验哈希行
            val selfHash = MessageDigest.getInstance("SHA-256")
                .digest(outFile.readBytes())
                .joinToString("") { "%02x".format(it) }
            outFile.appendText("#HASH:$selfHash\n")

            val modeLabel = if (passwordMode) "密码模式" else "无密码模式"
            val bindLabel = if (!passwordMode) (if (deviceBind) " · 设备绑定开" else " · 设备绑定关") else ""
            val timeLabel = if (validDays > 0) " · ${validDays} 天有效" else " · 永不过期"

            """
✅ 加密成功 (v11 · $modeLabel$bindLabel$timeLabel)
📄 ${outFile.absolutePath}
🛡 反逆向 12 项 · 自校验 SHA256
🔒 ${if (passwordMode) "PBKDF2-SHA512(2M) + AES-256-CBC" else "AES-256-CBC + 三层变换"}
📊 执行上限: ${if (maxRuns > 0) "$maxRuns 次" else "无限"}
💥 自毁: shred × 2
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
            val b64 = dM.groupValues[1]
            val isPwd = text.contains("__EV_MODE='password'")
            val ct: ByteArray
            val key: ByteArray
            val iv: ByteArray

            if (isPwd) {
                val blob = Base64.decode(b64, Base64.NO_WRAP)
                if (blob.size < 17) return@withContext "❌ 数据损坏"
                if (password.isBlank()) return@withContext "❌ 请输入密码"
                val salt = blob.copyOfRange(8, 16)
                ct = blob.copyOfRange(16, blob.size)
                val kp = deriveOpenSSLKey(password, salt)
                key = kp.first; iv = kp.second
            } else {
                // 无密码: 无法在此解保护 (需要设备指纹)
                return@withContext "❌ 无密码模式无法在 App 内解保护 (设备绑定)"
            }

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
            "✅ 已还原: ${outFile.absolutePath}\n📦 ${plain.size} 字节"
        } catch (e: Exception) { "❌ 解保护失败: ${e.message}" }
    }

    private fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    }
}
