
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
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class FileItem(
    val uri: Uri, val name: String, val size: String,
    val status: ProcessStatus = ProcessStatus.PENDING
)
enum class ProcessStatus { PENDING, ENCRYPTED, DECRYPTED, DONE, ERROR }

class CryptoManager(private val ctx: Context) {

    companion object {
        private const val MAGIC: Byte = 0x10
        private const val SALT_SZ = 32
        private const val IV_SZ = 12
        private const val TAG_BITS = 128
        private const val HMAC_SZ = 32
        private const val PBKDF2_ITER = 6_000_000
        private const val PBKDF2_PRF = "PBKDF2WithHmacSHA512"
        private const val MASTER_BITS = 512
        private const val OPENSSL_ITER = 2_000_000
        private const val OPENSSL_MD = "PBKDF2WithHmacSHA512"
        private const val HKDF_INFO_AES = "EncryptVault-v16-aes"
        private const val HKDF_INFO_MAC = "EncryptVault-v16-mac"
        private const val CONST_HEX =
            "a3f1c8e29b4d60715f2e8a3c9d17b5e4f028c6a9d3b7e15f824a6c0d9e3f7b12"
        private const val SLEEP_WRONG = 1500L
    }

    private var sPwdHash: String? = null
    private var sMasterSalt: ByteArray? = null
    private var sMasterKey: ByteArray? = null

    private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }
    private fun sha256Hex(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8)).hex()
    private fun id16(d: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(d).copyOf(8).hex()

    private fun deriveMaster(password: String, salt: ByteArray): ByteArray {
        val f = SecretKeyFactory.getInstance(PBKDF2_PRF)
        val spec: KeySpec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITER, MASTER_BITS)
        return f.generateSecret(spec).encoded
    }

    private fun hkdf(ikm: ByteArray, salt: ByteArray, info: String, len: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA512")
        mac.init(SecretKeySpec(salt, "HmacSHA512"))
        val prk = mac.doFinal(ikm)
        val mac2 = Mac.getInstance("HmacSHA512")
        mac2.init(SecretKeySpec(prk, "HmacSHA512"))
        mac2.update(info.toByteArray(Charsets.US_ASCII)); mac2.update(0x01.toByte())
        return mac2.doFinal().copyOf(len)
    }

    private fun ensureMaster(password: String): Pair<ByteArray, ByteArray> {
        val h = sha256Hex(password)
        if (sPwdHash == h && sMasterSalt != null && sMasterKey != null)
            return sMasterSalt!! to sMasterKey!!
        val salt = ByteArray(SALT_SZ).also { SecureRandom().nextBytes(it) }
        val key = deriveMaster(password, salt)
        sPwdHash = h; sMasterSalt = salt; sMasterKey = key
        return salt to key
    }

    private fun deriveMasterFromSalt(password: String, salt: ByteArray): ByteArray {
        if (sMasterSalt?.contentEquals(salt) == true && sMasterKey != null) return sMasterKey!!
        return deriveMaster(password, salt)
    }

    fun getFileInfo(uri: Uri): FileItem? = try {
        ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val n = c.getString(c.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                val s = c.getLong(c.getColumnIndexOrThrow(OpenableColumns.SIZE))
                FileItem(uri, n, fmtSize(s))
            } else null
        }
    } catch (_: Exception) { null }

    fun scanDirectory(treeUri: Uri): List<FileItem> {
        val list = mutableListOf<FileItem>()
        val root = DocumentFile.fromTreeUri(ctx, treeUri) ?: return list
        root.listFiles().forEach { d ->
            if (d.isFile) list.add(FileItem(d.uri, d.name ?: "unknown", fmtSize(d.length())))
        }
        return list
    }

    private fun readBytes(uri: Uri): ByteArray =
        ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
    private fun writeBytes(uri: Uri, data: ByteArray) {
        ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(data) }
    }

    suspend fun encryptFile(uri: Uri, password: String): Boolean = withContext(Dispatchers.IO) {
        var ak: ByteArray? = null; var mk: ByteArray? = null
        try {
            val plain = readBytes(uri)
            val (mSalt, mKey) = ensureMaster(password)
            val fSalt = ByteArray(SALT_SZ).also { SecureRandom().nextBytes(it) }
            val iv = ByteArray(IV_SZ).also { SecureRandom().nextBytes(it) }
            ak = hkdf(mKey, fSalt, HKDF_INFO_AES, 32)
            mk = hkdf(mKey, fSalt, HKDF_INFO_MAC, 32)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(ak, "AES"),
                GCMParameterSpec(TAG_BITS, iv))
            val ct = cipher.doFinal(plain)
            val hmac = Mac.getInstance("HmacSHA256")
            hmac.init(SecretKeySpec(mk, "HmacSHA256"))
            hmac.update(iv); hmac.update(ct)
            val tag = hmac.doFinal()
            val out = ByteArray(1 + SALT_SZ + SALT_SZ + IV_SZ + ct.size + HMAC_SZ)
            var p = 0
            out[p] = MAGIC; p += 1
            System.arraycopy(mSalt, 0, out, p, SALT_SZ); p += SALT_SZ
            System.arraycopy(fSalt, 0, out, p, SALT_SZ); p += SALT_SZ
            System.arraycopy(iv, 0, out, p, IV_SZ); p += IV_SZ
            System.arraycopy(ct, 0, out, p, ct.size); p += ct.size
            System.arraycopy(tag, 0, out, p, HMAC_SZ)
            writeBytes(uri, out); true
        } catch (e: Exception) { e.printStackTrace(); false }
        finally { ak?.let { Arrays.fill(it, 0) }; mk?.let { Arrays.fill(it, 0) } }
    }

    suspend fun decryptFile(uri: Uri, password: String): Boolean = withContext(Dispatchers.IO) {
        var ak: ByteArray? = null; var mk: ByteArray? = null
        try {
            val data = readBytes(uri)
            val need = 1 + SALT_SZ + SALT_SZ + IV_SZ + 16 + HMAC_SZ
            if (data.size < need || data[0] != MAGIC) {
                Thread.sleep(SLEEP_WRONG); return@withContext false
            }
            var p = 1
            val mSalt = data.copyOfRange(p, p + SALT_SZ); p += SALT_SZ
            val fSalt = data.copyOfRange(p, p + SALT_SZ); p += SALT_SZ
            val iv = data.copyOfRange(p, p + IV_SZ); p += IV_SZ
            val ctLen = data.size - p - HMAC_SZ
            val ct = data.copyOfRange(p, p + ctLen); p += ctLen
            val storedTag = data.copyOfRange(p, p + HMAC_SZ)
            val mKey = deriveMasterFromSalt(password, mSalt)
            ak = hkdf(mKey, fSalt, HKDF_INFO_AES, 32)
            mk = hkdf(mKey, fSalt, HKDF_INFO_MAC, 32)
            val hmac = Mac.getInstance("HmacSHA256")
            hmac.init(SecretKeySpec(mk, "HmacSHA256"))
            hmac.update(iv); hmac.update(ct)
            if (!MessageDigest.isEqual(storedTag, hmac.doFinal())) {
                Thread.sleep(SLEEP_WRONG); return@withContext false
            }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(ak, "AES"),
                GCMParameterSpec(TAG_BITS, iv))
            writeBytes(uri, cipher.doFinal(ct))
            sPwdHash = sha256Hex(password); sMasterSalt = mSalt; sMasterKey = mKey
            true
        } catch (e: Exception) { Thread.sleep(SLEEP_WRONG); false }
        finally { ak?.let { Arrays.fill(it, 0) }; mk?.let { Arrays.fill(it, 0) } }
    }

    // ============================================================
    //  Shell 保护 v16 — 三层变换 + 反 Python + 反反编译
    // ============================================================
    private fun deriveOpenSSLKey(pwd: String, salt: ByteArray): Pair<ByteArray, ByteArray> {
        val f = SecretKeyFactory.getInstance(OPENSSL_MD)
        val spec = PBEKeySpec(pwd.toCharArray(), salt, OPENSSL_ITER, 48 * 8)
        val d = f.generateSecret(spec).encoded
        return d.copyOfRange(0, 32) to d.copyOfRange(32, 48)
    }

    private fun transform3(key: ByteArray, iv: ByteArray, tag: String): List<String> {
        val km = (key + iv).hex()
        val dk = MessageDigest.getInstance("SHA-512")
            .digest(tag.toByteArray(Charsets.UTF_8)).hex().take(96)
        val t1 = StringBuilder(96); var i = 0
        while (i < 96) {
            t1.append("%02x".format(km.substring(i, i+2).toInt(16) xor dk.substring(i, i+2).toInt(16)))
            i += 2
        }
        val t2 = StringBuilder(96); i = 0
        while (i < 96) { t2.insert(0, t1.substring(i, i+4)); i += 4 }
        val t3 = StringBuilder(96); i = 0
        while (i < 96) {
            t3.append("%02x".format(t2.substring(i, i+2).toInt(16) xor CONST_HEX.substring(i, i+2).toInt(16)))
            i += 2
        }
        val s = t3.toString()
        return (0 until 6).map { s.substring(it*16, (it+1)*16) }
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
            if (!src.isFile) return@withContext "❌ 路径不是文件"
            if (java.nio.file.Files.isSymbolicLink(src.toPath()))
                return@withContext "❌ 源文件是软链接"

            val raw = outputPathRaw.trim().ifBlank { src.parent ?: "/sdcard" }
            val baseName = src.nameWithoutExtension + "-protected.sh"
            val outFile = run {
                val f = File(raw)
                if (f.isDirectory || raw.endsWith("/")) File(f, baseName) else f
            }
            outFile.parentFile?.mkdirs()
            val plain = src.readBytes()
            val D = "${'$'}"
            val b64: String; val segs: List<String>; val deviceSalt: String

            if (passwordMode) {
                val salt = ByteArray(8).also { SecureRandom().nextBytes(it) }
                val (k, iv) = deriveOpenSSLKey(password, salt)
                val c = Cipher.getInstance("AES/CBC/PKCS5Padding")
                c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(k, "AES"), IvParameterSpec(iv))
                val ct = c.doFinal(plain)
                val blob = ByteArray(16 + ct.size)
                System.arraycopy("Salted__".toByteArray(Charsets.US_ASCII), 0, blob, 0, 8)
                System.arraycopy(salt, 0, blob, 8, 8)
                System.arraycopy(ct, 0, blob, 16, ct.size)
                b64 = Base64.encodeToString(blob, Base64.NO_WRAP)
                segs = List(6) { "" }; deviceSalt = ""
            } else {
                val k = ByteArray(32).also { SecureRandom().nextBytes(it) }
                val iv = ByteArray(16).also { SecureRandom().nextBytes(it) }
                val c = Cipher.getInstance("AES/CBC/PKCS5Padding")
                c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(k, "AES"), IvParameterSpec(iv))
                val ct = c.doFinal(plain)
                b64 = Base64.encodeToString(ct, Base64.NO_WRAP)
                val ds = ByteArray(16).also { SecureRandom().nextBytes(it) }
                val tag = ds.hex() + ":" + (if (deviceBind) "BIND" else "FIXED")
                segs = transform3(k, iv, tag)
                deviceSalt = ds.hex()
                Arrays.fill(k, 0); Arrays.fill(iv, 0)
            }

            val id = id16(plain)
            val mode = if (passwordMode) "password" else "nopass"
            val expTs = if (validDays > 0) System.currentTimeMillis()/1000L + validDays.toLong()*86400L else 0L
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

            val tpl = """
#!/data/data/com.termux/files/usr/bin/bash
# EncryptVault v16 ($mode) - 反 Python / 反反编译
umask 077

# ===== 反调试 =====
case "${D}-" in *x*) exit 1 ;; esac
[ -n "${D}{BASH_XTRACEFD:-}" ] && exit 1
case ":${D}{SHELLOPTS:-}:" in *:xtrace:*) exit 1 ;; esac
[ "${D}{PS4:-+ }" != "+ " ] && exit 1

# ===== TracerPid =====
if [ -r /proc/self/status ]; then
    grep -qE '^TracerPid:\s*[1-9]' /proc/self/status 2>/dev/null && exit 1
fi

# ===== LD_PRELOAD =====
[ -n "${D}{LD_PRELOAD:-}" ] && exit 1

# ===== 反 source =====
[ -z "${D}{BASH_SOURCE[0]:-}" ] && exit 1
__EV_SELF="${D}{BASH_SOURCE[0]}"
[ -f "${D}__EV_SELF" ] || __EV_SELF="${D}0"
[ -f "${D}__EV_SELF" ] || exit 1
[ -L "${D}__EV_SELF" ] && exit 1

# ===== 父进程检测 (含 Python/Perl/Ruby/Node) =====
if [ -r "/proc/${D}PPID/cmdline" ]; then
    __EV_PP=$(tr '\0' ' ' < "/proc/${D}PPID/cmdline" 2>/dev/null)
    case "${D}__EV_PP" in
        *sed*|*awk*|*grep*|*"cat "*|*tee*|*strace*|*ltrace*|*gdb*|*python*|*python3*|*perl*|*ruby*|*node*|*hexdump*|*xxd*|*strings*|*jadx*|*apktool*|*dex2jar*)
            printf '❌ 父进程分析工具\n' >&2; exit 1 ;;
    esac
fi

# ===== 反 Python 环境变量 =====
[ -n "${D}{PYTHONPATH:-}" ] && exit 1
[ -n "${D}{PYTHONHOME:-}" ] && exit 1
[ -n "${D}{PYTHONSTARTUP:-}" ] && exit 1

# ===== 反 Python 进程扫描 (精准) =====
if [ -r /proc/self/task ] && [ -d /proc ]; then
    for __p in /proc/[0-9]*/comm; do
        [ -r "${D}__p" ] || continue
        __n=$(cat "${D}__p" 2>/dev/null)
        case "${D}__n" in
            python|python3|ipython|pypy|jupyter|spyder) 
                printf '❌ 检测到 Python 进程\n' >&2; exit 1 ;;
        esac
    done
fi

# ===== 反反编译工具残留 =====
for __f in \
    /data/local/tmp/jadx /data/local/tmp/jadx-gui \
    /data/local/tmp/apktool* /data/local/tmp/dex2jar* \
    /data/local/tmp/jd-cli* /data/local/tmp/jd-gui* \
    /sdcard/jadx /sdcard/apktool /sdcard/dex2jar \
    /sdcard/MT2 /sdcard/NP管理器 /sdcard/Android/data/bin.mt.plus; do
    [ -e "${D}__f" ] && { printf '❌ 反编译工具残留\n' >&2; exit 1; }
done

# ===== 反常见反编译进程 =====
for __p in /proc/[0-9]*/comm; do
    [ -r "${D}__p" ] || continue
    __n=$(cat "${D}__p" 2>/dev/null)
    case "${D}__n" in
        jadx|jadx-gui|apktool|dex2jar|jd-cli|jd-gui|procyon)
            printf '❌ 检测到反编译进程\n' >&2; exit 1 ;;
    esac
done

# ===== 命令覆盖检测 =====
for __c in openssl sha256sum base64 date hostname getprop cut tail head; do
    declare -F "${D}__c" >/dev/null 2>&1 && {
        printf '❌ 命令被覆盖: ${D}__c\n' >&2; exit 1
    }
done

# ===== 自校验 SHA256 =====
__EV_LAST=$(tail -n 1 "${D}__EV_SELF")
case "${D}__EV_LAST" in
    \#HASH:*) __EV_EXP="${D}{__EV_LAST#\#HASH:}" ;;
    *) printf '❌ 结构异常\n' >&2; exit 1 ;;
esac
__EV_ACT=$(head -n -1 "${D}__EV_SELF" | sha256sum | awk '{print ${D}1}')
if [ "${D}__EV_EXP" != "${D}__EV_ACT" ]; then
    printf '❌ 脚本已被修改\n' >&2
    if command -v shred >/dev/null 2>&1; then shred -u -n 2 -z "${D}__EV_SELF" 2>/dev/null; else rm -f "${D}__EV_SELF"; fi
    exit 1
fi
unset __EV_LAST __EV_EXP __EV_ACT __EV_PP __n __f __c

command -v openssl >/dev/null 2>&1 || exit 1
command -v sha256sum >/dev/null 2>&1 || exit 1

$expLine
if [ "${D}__EV_EXP" -gt 0 ]; then
    [ $(date +%s) -gt "${D}__EV_EXP" ] && {
        printf '💥 已过期\n' >&2
        if command -v shred >/dev/null 2>&1; then shred -u -n 2 -z "${D}__EV_SELF" 2>/dev/null; else rm -f "${D}__EV_SELF"; fi
        exit 1
    }
fi

__EV_TMP="${D}{TMPDIR:-/tmp}/.ev_${D}${D}_${D}RANDOM"
trap 'rm -f "${D}{__EV_TMP}.enc" "${D}{__EV_TMP}.sh" 2>/dev/null' EXIT INT TERM HUP

__EV_MODE='$mode'
__EV_D='$b64'
$nopassVars
__EV_MAX=$maxRuns
__EV_FAIL=$failLimit
__EV_ID='$id'

printf '%s' "${D}__EV_D" | base64 -d > "${D}{__EV_TMP}.enc" 2>/dev/null
unset __EV_D
[ -s "${D}{__EV_TMP}.enc" ] || exit 1

if [ "${D}__EV_MODE" = "password" ]; then
    __EV_MAX_TRIES=${D}__EV_FAIL
    [ "${D}__EV_MAX_TRIES" -le 0 ] && __EV_MAX_TRIES=1
    __EV_OK=0; __EV_TRIES=0
    while [ ${D}__EV_TRIES -lt ${D}__EV_MAX_TRIES ]; do
        printf '🔐 密码: ' >&2
        IFS= read -r -s __EV_PWD
        printf '\n' >&2
        [ -z "${D}__EV_PWD" ] && { __EV_TRIES=$((__EV_TRIES+1)); continue; }
        printf '⏳ 派生密钥...\n' >&2
        printf '%s\n' "${D}__EV_PWD" | openssl enc -d -aes-256-cbc \
            -pbkdf2 -iter 2000000 -md sha512 -pass stdin \
            -in "${D}{__EV_TMP}.enc" -out "${D}{__EV_TMP}.sh" 2>/dev/null
        __EV_RC=${D}?
        unset __EV_PWD
        if [ ${D}__EV_RC -eq 0 ] && [ -s "${D}{__EV_TMP}.sh" ]; then __EV_OK=1; break; fi
        rm -f "${D}{__EV_TMP}.sh"
        __EV_TRIES=$((__EV_TRIES+1))
        printf '❌ 密码错误 (%s/%s)\n' "${D}__EV_TRIES" "${D}__EV_MAX_TRIES" >&2
    done
    rm -f "${D}{__EV_TMP}.enc"
    [ ${D}__EV_OK -ne 1 ] && {
        printf '💥 密码错误过多\n' >&2
        if command -v shred >/dev/null 2>&1; then shred -u -n 2 -z "${D}__EV_SELF" 2>/dev/null; else rm -f "${D}__EV_SELF"; fi
        exit 1
    }
else
    if [ "${D}__EV_BIND" = "1" ]; then
        __EV_HN=$(hostname 2>/dev/null || echo "")
        __EV_MD=$(getprop ro.product.model 2>/dev/null || echo "")
        __EV_BR=$(getprop ro.product.brand 2>/dev/null || echo "")
        __EV_TAG="${D}__EV_SALT:BIND"
    else
        __EV_TAG="${D}__EV_SALT:FIXED"
    fi
    __EV_DK=$(printf '%s' "${D}__EV_TAG" | sha512sum | awk '{print ${D}1}' | cut -c1-96)
    unset __EV_HN __EV_MD __EV_BR __EV_TAG __EV_SALT

    __EV_T3="${D}{__EV_P1}${D}{__EV_P2}${D}{__EV_P3}${D}{__EV_P4}${D}{__EV_P5}${D}{__EV_P6}"
    unset __EV_P1 __EV_P2 __EV_P3 __EV_P4 __EV_P5 __EV_P6

    __EV_T2=""; __EV_I=0
    while [ ${D}__EV_I -lt 96 ]; do
        __EV_B=$((16#${D}{__EV_T3:${D}__EV_I:2}))
        __EV_C=$((16#${D}{__EV_CONST:${D}__EV_I:2}))
        __EV_T2="${D}{__EV_T2}$(printf '%02x' $(( __EV_B ^ __EV_C )))"
        __EV_I=$((__EV_I+2))
    done

    __EV_T1=""; __EV_I=0
    while [ ${D}__EV_I -lt 96 ]; do
        __EV_T1="${D}{__EV_T2:${D}__EV_I:4}${D}{__EV_T1}"
        __EV_I=$((__EV_I+4))
    done

    __EV_KM=""; __EV_I=0
    while [ ${D}__EV_I -lt 96 ]; do
        __EV_B=$((16#${D}{__EV_T1:${D}__EV_I:2}))
        __EV_DD=$((16#${D}{__EV_DK:${D}__EV_I:2}))
        __EV_KM="${D}{__EV_KM}$(printf '%02x' $(( __EV_B ^ __EV_DD )))"
        __EV_I=$((__EV_I+2))
    done
    unset __EV_T3 __EV_T2 __EV_T1 __EV_DK __EV_CONST __EV_I __EV_B __EV_C __EV_DD

    __EV_KEY=$(printf '%s' "${D}__EV_KM" | cut -c1-64)
    __EV_IV=$(printf '%s' "${D}__EV_KM" | cut -c65-96)
    unset __EV_KM

    openssl enc -d -aes-256-cbc -K "${D}__EV_KEY" -iv "${D}__EV_IV" \
        -in "${D}{__EV_TMP}.enc" -out "${D}{__EV_TMP}.sh" 2>/dev/null
    __EV_RC=${D}?
    rm -f "${D}{__EV_TMP}.enc"
    unset __EV_KEY __EV_IV
    if [ ${D}__EV_RC -ne 0 ] || [ ! -s "${D}{__EV_TMP}.sh" ]; then
        printf '❌ 解密失败\n' >&2
        if command -v shred >/dev/null 2>&1; then shred -u -n 2 -z "${D}__EV_SELF" 2>/dev/null; else rm -f "${D}__EV_SELF"; fi
        exit 1
    fi
fi

if [ "${D}__EV_MAX" -gt 0 ]; then
    __EV_STATE="${D}{HOME}/.ev_state/${D}__EV_ID"
    mkdir -p "${D}{HOME}/.ev_state" 2>/dev/null
    __EV_RUNS=0
    [ -f "${D}__EV_STATE" ] && __EV_RUNS=$(cat "${D}__EV_STATE" 2>/dev/null || echo 0)
    case "${D}__EV_RUNS" in ''|*[!0-9]*) __EV_RUNS=0 ;; esac
    if [ ${D}__EV_RUNS -ge ${D}__EV_MAX ]; then
        printf '❌ 已达上限\n' >&2
        if command -v shred >/dev/null 2>&1; then shred -u -n 2 -z "${D}__EV_SELF" 2>/dev/null; else rm -f "${D}__EV_SELF"; fi
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
""".trimIndent()

            outFile.writeText(tpl)
            outFile.setExecutable(true)
            val selfHash = MessageDigest.getInstance("SHA-256")
                .digest(outFile.readBytes()).hex()
            outFile.appendText("#HASH:$selfHash\n")

            "✅ 加密成功 (v16 · 反 Python + 反反编译)\n📄 ${outFile.absolutePath}"
        } catch (e: Exception) { "❌ 失败: ${e.message}" }
    }

    suspend fun unprotectShellScript(
        inputPath: String, outputPathRaw: String, password: String
    ): String = withContext(Dispatchers.IO) {
        try {
            val src = File(inputPath.trim())
            if (!src.exists() || !src.isFile) return@withContext "❌ 文件不存在"
            val text = src.readText()
            val b64 = Regex("""__EV_D\s*=\s*['"]([A-Za-z0-9+/=]+)['"]""")
                .find(text)?.groupValues?.get(1) ?: return@withContext "❌ 非保护脚本"
            if (!text.contains("__EV_MODE='password'"))
                return@withContext "❌ 无密码模式无法在 App 内解保护"
            val blob = Base64.decode(b64, Base64.NO_WRAP)
            if (blob.size < 17) return@withContext "❌ 数据损坏"
            if (password.isBlank()) return@withContext "❌ 需密码"
            val salt = blob.copyOfRange(8, 16)
            val ct = blob.copyOfRange(16, blob.size)
            val (k, iv) = deriveOpenSSLKey(password, salt)
            val c = Cipher.getInstance("AES/CBC/PKCS5Padding")
            c.init(Cipher.DECRYPT_MODE, SecretKeySpec(k, "AES"), IvParameterSpec(iv))
            val plain = try { c.doFinal(ct) } catch (_: Exception) { return@withContext "❌ 密码错" }
            val raw = outputPathRaw.trim().ifBlank { src.parent ?: "/sdcard" }
            val outName = src.name.removeSuffix("-protected.sh").let {
                if (it.endsWith(".sh")) it else "$it-restored.sh" }
            val outFile = run {
                val f = File(raw)
                if (f.isDirectory || raw.endsWith("/")) File(f, outName) else f
            }
            outFile.parentFile?.mkdirs()
            outFile.writeBytes(plain); outFile.setExecutable(true)
            "✅ 已还原: ${outFile.absolutePath}"
        } catch (e: Exception) { "❌ 解保护失败: ${e.message}" }
    }

    private fun fmtSize(b: Long): String = when {
        b < 1024L -> "$b B"
        b < 1024L * 1024L -> "%.1f KB".format(b / 1024.0)
        else -> "%.1f MB".format(b / (1024.0 * 1024.0))
    }
}
