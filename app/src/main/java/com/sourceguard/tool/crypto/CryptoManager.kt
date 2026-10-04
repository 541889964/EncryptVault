
package com.sourceguard.tool.crypto

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
    val uri: Uri,
    val name: String,
    val size: String,
    val status: ProcessStatus = ProcessStatus.PENDING
)

enum class ProcessStatus { PENDING, ENCRYPTED, DECRYPTED, DONE, ERROR }

class CryptoManager(private val ctx: Context) {

    companion object {
        private const val MAGIC: Byte = 0x20
        private const val SS = 32
        private const val IS = 12
        private const val TAG_BITS = 128
        private const val HS = 32
        private const val PITER = 6_000_000
        private const val PRF = "PBKDF2WithHmacSHA512"
        private const val MBITS = 512
        private const val OITER = 2_000_000
        private const val OMD = "PBKDF2WithHmacSHA512"
        private const val HIA = "SourceGuard-v21-aes"
        private const val HIM = "SourceGuard-v21-mac"
        private const val CH =
            "a3f1c8e29b4d60715f2e8a3c9d17b5e4f028c6a9d3b7e15f824a6c0d9e3f7b12"
        private const val SW = 1500L
    }

    private var sPh: String? = null
    private var sMs: ByteArray? = null
    private var sMk: ByteArray? = null

    // ============================================================
    //  关键修复: Byte 是有符号的 (-128..127)
    //  必须用 it.toInt() and 0xFF 转成 0..255 才能保证 2 位 hex
    //  否则负数字节会输出 8 位 hex (ffffff80)，导致 hex 长度爆炸
    // ============================================================
    private fun ByteArray.hx(): String =
        joinToString("") { "%02x".format(it.toInt() and 0xFF) }

    private fun s256(s: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(s.toByteArray(Charsets.UTF_8))
            .hx()

    private fun id16(d: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(d)
            .copyOf(8)
            .hx()

    private fun dM(p: String, s: ByteArray): ByteArray {
        val f = SecretKeyFactory.getInstance(PRF)
        val sp: KeySpec = PBEKeySpec(p.toCharArray(), s, PITER, MBITS)
        return f.generateSecret(sp).encoded
    }

    private fun hk(ikm: ByteArray, salt: ByteArray, info: String, len: Int): ByteArray {
        val m1 = Mac.getInstance("HmacSHA512")
        m1.init(SecretKeySpec(salt, "HmacSHA512"))
        val prk = m1.doFinal(ikm)
        val m2 = Mac.getInstance("HmacSHA512")
        m2.init(SecretKeySpec(prk, "HmacSHA512"))
        m2.update(info.toByteArray(Charsets.US_ASCII))
        m2.update(0x01.toByte())
        return m2.doFinal().copyOf(len)
    }

    private fun eM(p: String): Pair<ByteArray, ByteArray> {
        val h = s256(p)
        if (sPh == h && sMs != null && sMk != null) return sMs!! to sMk!!
        val s = ByteArray(SS).also { SecureRandom().nextBytes(it) }
        val k = dM(p, s)
        sPh = h; sMs = s; sMk = k
        return s to k
    }

    private fun dMS(p: String, s: ByteArray): ByteArray {
        if (sMs?.contentEquals(s) == true && sMk != null) return sMk!!
        return dM(p, s)
    }

    fun getFileInfo(uri: Uri): FileItem? = try {
        ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val n = c.getString(c.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                val s = c.getLong(c.getColumnIndexOrThrow(OpenableColumns.SIZE))
                FileItem(uri, n, fS(s))
            } else null
        }
    } catch (_: Exception) { null }

    fun scanDirectory(t: Uri): List<FileItem> {
        val l = mutableListOf<FileItem>()
        val r = DocumentFile.fromTreeUri(ctx, t) ?: return l
        r.listFiles().forEach { d ->
            if (d.isFile) l.add(FileItem(d.uri, d.name ?: "unknown", fS(d.length())))
        }
        return l
    }

    private fun rB(uri: Uri): ByteArray =
        ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)

    private fun wB(uri: Uri, d: ByteArray) {
        ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(d) }
    }

    // ============================================================
    //  文件加密
    // ============================================================
    suspend fun encryptFile(uri: Uri, p: String): Boolean = withContext(Dispatchers.IO) {
        var ak: ByteArray? = null
        var mk: ByteArray? = null
        try {
            val pl = rB(uri)
            val pair = eM(p)
            val ms = pair.first
            val mkk = pair.second
            val fs = ByteArray(SS).also { SecureRandom().nextBytes(it) }
            val iv = ByteArray(IS).also { SecureRandom().nextBytes(it) }
            ak = hk(mkk, fs, HIA, 32)
            mk = hk(mkk, fs, HIM, 32)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(ak, "AES"), GCMParameterSpec(TAG_BITS, iv))
            val ct = c.doFinal(pl)
            val hm = Mac.getInstance("HmacSHA256")
            hm.init(SecretKeySpec(mk, "HmacSHA256"))
            hm.update(iv)
            hm.update(ct)
            val tg = hm.doFinal()
            val out = ByteArray(1 + SS + SS + IS + ct.size + HS)
            var i = 0
            out[i] = MAGIC; i++
            System.arraycopy(ms, 0, out, i, SS); i += SS
            System.arraycopy(fs, 0, out, i, SS); i += SS
            System.arraycopy(iv, 0, out, i, IS); i += IS
            System.arraycopy(ct, 0, out, i, ct.size); i += ct.size
            System.arraycopy(tg, 0, out, i, HS)
            wB(uri, out)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            ak?.let { Arrays.fill(it, 0) }
            mk?.let { Arrays.fill(it, 0) }
        }
    }

    // ============================================================
    //  文件解密
    // ============================================================
    suspend fun decryptFile(uri: Uri, p: String): Boolean = withContext(Dispatchers.IO) {
        var ak: ByteArray? = null
        var mk: ByteArray? = null
        try {
            val d = rB(uri)
            val need = 1 + SS + SS + IS + 16 + HS
            if (d.size < need || d[0] != MAGIC) {
                Thread.sleep(SW)
                return@withContext false
            }
            var i = 1
            val ms = d.copyOfRange(i, i + SS); i += SS
            val fs = d.copyOfRange(i, i + SS); i += SS
            val iv = d.copyOfRange(i, i + IS); i += IS
            val cl = d.size - i - HS
            val ct = d.copyOfRange(i, i + cl); i += cl
            val tg = d.copyOfRange(i, i + HS)
            val mk2 = dMS(p, ms)
            ak = hk(mk2, fs, HIA, 32)
            mk = hk(mk2, fs, HIM, 32)
            val hm = Mac.getInstance("HmacSHA256")
            hm.init(SecretKeySpec(mk, "HmacSHA256"))
            hm.update(iv)
            hm.update(ct)
            if (!MessageDigest.isEqual(tg, hm.doFinal())) {
                Thread.sleep(SW)
                return@withContext false
            }
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, SecretKeySpec(ak, "AES"), GCMParameterSpec(TAG_BITS, iv))
            wB(uri, c.doFinal(ct))
            sPh = s256(p); sMs = ms; sMk = mk2
            true
        } catch (e: Exception) {
            Thread.sleep(SW)
            false
        } finally {
            ak?.let { Arrays.fill(it, 0) }
            mk?.let { Arrays.fill(it, 0) }
        }
    }

    // ============================================================
    //  Shell 保护 — 三层变换
    // ============================================================
    private fun dO(p: String, s: ByteArray): Pair<ByteArray, ByteArray> {
        val f = SecretKeyFactory.getInstance(OMD)
        val sp = PBEKeySpec(p.toCharArray(), s, OITER, 48 * 8)
        val d = f.generateSecret(sp).encoded
        return d.copyOfRange(0, 32) to d.copyOfRange(32, 48)
    }

    // 关键: km 和 dk 都必须是 96 hex 字符
    // km = (32B key + 16B iv) = 48B → hx() = 96 字符
    // dk = SHA-512(任意长度) = 64B → hx() = 128 字符 → take(96) = 96 字符
    private fun t3(k: ByteArray, iv: ByteArray, tag: String): List<String> {
        val km = (k + iv).hx()
        require(km.length == 96) { "km 长度错误: ${km.length}" }

        val dkFull = MessageDigest.getInstance("SHA-512")
            .digest(tag.toByteArray(Charsets.UTF_8))
            .hx()
        require(dkFull.length == 128) { "SHA-512 输出长度错误: ${dkFull.length}" }
        val dk = dkFull.take(96)

        // 层1: XOR
        val t1 = StringBuilder(96)
        var i = 0
        while (i < 96) {
            val a = km.substring(i, i + 2).toInt(16)
            val b = dk.substring(i, i + 2).toInt(16)
            t1.append("%02x".format(a xor b))
            i += 2
        }

        // 层2: 4-hex 单元反序
        val t2 = StringBuilder(96)
        i = 0
        while (i < 96) {
            t2.insert(0, t1.substring(i, i + 4))
            i += 4
        }

        // 层3: XOR CONST
        val t3s = StringBuilder(96)
        i = 0
        while (i < 96) {
            val a = t2.substring(i, i + 2).toInt(16)
            val b = CH.substring(i, i + 2).toInt(16)
            t3s.append("%02x".format(a xor b))
            i += 2
        }

        val s = t3s.toString()
        require(s.length == 96) { "t3 长度错误: ${s.length}" }
        return (0 until 6).map { s.substring(it * 16, (it + 1) * 16) }
    }

    suspend fun protectShellScript(
        ip: String, op: String, p: String,
        pm: Boolean, mr: Int, fl: Int, db: Boolean, vd: Int
    ): String = withContext(Dispatchers.IO) {
        try {
            if (pm && p.length < 8) return@withContext "❌ 密码至少 8 位"
            val s = File(ip.trim())
            if (!s.exists()) return@withContext "❌ 源文件不存在"
            if (!s.isFile) return@withContext "❌ 路径不是文件"
            if (java.nio.file.Files.isSymbolicLink(s.toPath()))
                return@withContext "❌ 源文件是软链接"

            val raw = op.trim().ifBlank { s.parent ?: "/sdcard" }
            val bn = s.nameWithoutExtension + "-protected.sh"
            val of = run {
                val f = File(raw)
                if (f.isDirectory || raw.endsWith("/")) File(f, bn) else f
            }
            of.parentFile?.mkdirs()
            val pl = s.readBytes()
            val D = "${'$'}"

            val b64: String
            val segs: List<String>
            val dSalt: String

            if (pm) {
                val salt = ByteArray(8).also { SecureRandom().nextBytes(it) }
                val pair = dO(p, salt)
                val c = Cipher.getInstance("AES/CBC/PKCS5Padding")
                c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(pair.first, "AES"),
                    IvParameterSpec(pair.second))
                val ct = c.doFinal(pl)
                val blob = ByteArray(16 + ct.size)
                System.arraycopy("Salted__".toByteArray(Charsets.US_ASCII), 0, blob, 0, 8)
                System.arraycopy(salt, 0, blob, 8, 8)
                System.arraycopy(ct, 0, blob, 16, ct.size)
                b64 = Base64.encodeToString(blob, Base64.NO_WRAP)
                segs = List(6) { "" }
                dSalt = ""
            } else {
                val k = ByteArray(32).also { SecureRandom().nextBytes(it) }
                val iv = ByteArray(16).also { SecureRandom().nextBytes(it) }
                val c = Cipher.getInstance("AES/CBC/PKCS5Padding")
                c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(k, "AES"), IvParameterSpec(iv))
                val ct = c.doFinal(pl)
                b64 = Base64.encodeToString(ct, Base64.NO_WRAP)
                val ds = ByteArray(16).also { SecureRandom().nextBytes(it) }
                val tg = ds.hx() + ":" + (if (db) "BIND" else "FIXED")
                segs = t3(k, iv, tg)
                dSalt = ds.hx()
                Arrays.fill(k, 0)
                Arrays.fill(iv, 0)
            }

            val id = id16(pl)
            val mode = if (pm) "password" else "nopass"
            val eT = if (vd > 0) System.currentTimeMillis() / 1000L + vd.toLong() * 86400L else 0L
            val bF = if (db) "1" else "0"

            val nV = if (!pm) """
__EV_P1='${segs[0]}'
__EV_P2='${segs[1]}'
__EV_P3='${segs[2]}'
__EV_P4='${segs[3]}'
__EV_P5='${segs[4]}'
__EV_P6='${segs[5]}'
__EV_SALT='$dSalt'
__EV_CONST='$CH'
__EV_BIND=$bF
""" else ""

            val eL = if (vd > 0) "__EV_EXP=$eT" else "__EV_EXP=0"

            val tpl = """
#!/data/data/com.termux/files/usr/bin/bash
# SourceGuard v21 ($mode)
umask 077

case "${D}-" in *x*) exit 1 ;; esac
[ -n "${D}{BASH_XTRACEFD:-}" ] && exit 1
case ":${D}{SHELLOPTS:-}:" in *:xtrace:*) exit 1 ;; esac
[ "${D}{PS4:-+ }" != "+ " ] && exit 1

if [ -r /proc/self/status ]; then
    grep -qE '^TracerPid:\s*[1-9]' /proc/self/status 2>/dev/null && exit 1
fi
[ -n "${D}{LD_PRELOAD:-}" ] && exit 1

[ -z "${D}{BASH_SOURCE[0]:-}" ] && exit 1
__EV_SELF="${D}{BASH_SOURCE[0]}"
[ -f "${D}__EV_SELF" ] || __EV_SELF="${D}0"
[ -f "${D}__EV_SELF" ] || exit 1
[ -L "${D}__EV_SELF" ] && exit 1

if [ -r "/proc/${D}PPID/cmdline" ]; then
    __EV_PP=$(tr '\0' ' ' < "/proc/${D}PPID/cmdline" 2>/dev/null)
    case "${D}__EV_PP" in
        *sed*|*awk*|*grep*|*"cat "*|*tee*|*strace*|*ltrace*|*gdb*|*python*|*python3*|*perl*|*ruby*|*node*|*hexdump*|*xxd*|*strings*)
            printf '❌ 父进程分析工具\n' >&2; exit 1 ;;
    esac
fi

for __c in openssl sha256sum base64 date hostname getprop cut tail head; do
    declare -F "${D}__c" >/dev/null 2>&1 && {
        printf '❌ 命令被覆盖: ${D}__c\n' >&2; exit 1
    }
done

__EV_LAST=$(tail -n 1 "${D}__EV_SELF")
case "${D}__EV_LAST" in
    \#HASH:*) __EV_EXP="${D}{__EV_LAST#\#HASH:}" ;;
    *) printf '❌ 结构异常\n' >&2; exit 1 ;;
esac
__EV_ACT=$(head -n -1 "${D}__EV_SELF" | sha256sum | awk '{print ${D}1}')
if [ "${D}__EV_EXP" != "${D}__EV_ACT" ]; then
    printf '❌ 脚本被修改\n' >&2
    if command -v shred >/dev/null 2>&1; then
        shred -u -n 2 -z "${D}__EV_SELF" 2>/dev/null
    else
        rm -f "${D}__EV_SELF"
    fi
    exit 1
fi
unset __EV_LAST __EV_EXP __EV_ACT __EV_PP __c

command -v openssl >/dev/null 2>&1 || exit 1
command -v sha256sum >/dev/null 2>&1 || exit 1

$eL
if [ "${D}__EV_EXP" -gt 0 ]; then
    if [ $(date +%s) -gt "${D}__EV_EXP" ]; then
        printf '💥 已过期\n' >&2
        if command -v shred >/dev/null 2>&1; then
            shred -u -n 2 -z "${D}__EV_SELF" 2>/dev/null
        else
            rm -f "${D}__EV_SELF"
        fi
        exit 1
    fi
fi

__EV_TMP="${D}{TMPDIR:-/tmp}/.ev_${D}${D}_${D}RANDOM"
trap 'rm -f "${D}{__EV_TMP}.enc" "${D}{__EV_TMP}.sh" 2>/dev/null' EXIT INT TERM HUP

__EV_MODE='$mode'
__EV_D='$b64'
$nV
__EV_MAX=$mr
__EV_FAIL=$fl
__EV_ID='$id'

printf '%s' "${D}__EV_D" | base64 -d > "${D}{__EV_TMP}.enc" 2>/dev/null
unset __EV_D
[ -s "${D}{__EV_TMP}.enc" ] || exit 1

if [ "${D}__EV_MODE" = "password" ]; then
    __EV_MAX_TRIES=${D}__EV_FAIL
    [ "${D}__EV_MAX_TRIES" -le 0 ] && __EV_MAX_TRIES=1
    __EV_OK=0
    __EV_TRIES=0
    while [ ${D}__EV_TRIES -lt ${D}__EV_MAX_TRIES ]; do
        printf '\n🔐 密码: ' >&2
        if command -v stty >/dev/null 2>&1 && [ -t 0 ]; then
            stty -echo 2>/dev/null
            IFS= read -r __EV_PWD
            stty echo 2>/dev/null
            printf '\n' >&2
        else
            IFS= read -r __EV_PWD
        fi
        [ -z "${D}__EV_PWD" ] && { __EV_TRIES=$((__EV_TRIES+1)); continue; }
        printf '⏳ 派生密钥...\n' >&2
        printf '%s\n' "${D}__EV_PWD" | openssl enc -d -aes-256-cbc -pbkdf2 -iter 2000000 -md sha512 -pass stdin -in "${D}{__EV_TMP}.enc" -out "${D}{__EV_TMP}.sh" 2>/dev/null
        __EV_RC=${D}?
        unset __EV_PWD
        if [ ${D}__EV_RC -eq 0 ] && [ -s "${D}{__EV_TMP}.sh" ]; then
            __EV_OK=1
            break
        fi
        rm -f "${D}{__EV_TMP}.sh"
        __EV_TRIES=$((__EV_TRIES+1))
        printf '❌ 密码错 (%s/%s)\n' "${D}__EV_TRIES" "${D}__EV_MAX_TRIES" >&2
    done
    rm -f "${D}{__EV_TMP}.enc"
    if [ ${D}__EV_OK -ne 1 ]; then
        printf '💥 密码错误过多\n' >&2
        if command -v shred >/dev/null 2>&1; then
            shred -u -n 2 -z "${D}__EV_SELF" 2>/dev/null
        else
            rm -f "${D}__EV_SELF"
        fi
        exit 1
    fi
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

    __EV_T2=""
    __EV_I=0
    while [ ${D}__EV_I -lt 96 ]; do
        __EV_B=$((16#${D}{__EV_T3:${D}__EV_I:2}))
        __EV_C=$((16#${D}{__EV_CONST:${D}__EV_I:2}))
        __EV_T2="${D}{__EV_T2}$(printf '%02x' $(( __EV_B ^ __EV_C )))"
        __EV_I=$((__EV_I+2))
    done

    __EV_T1=""
    __EV_I=0
    while [ ${D}__EV_I -lt 96 ]; do
        __EV_T1="${D}{__EV_T2:${D}__EV_I:4}${D}{__EV_T1}"
        __EV_I=$((__EV_I+4))
    done

    __EV_KM=""
    __EV_I=0
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

    openssl enc -d -aes-256-cbc -K "${D}__EV_KEY" -iv "${D}__EV_IV" -in "${D}{__EV_TMP}.enc" -out "${D}{__EV_TMP}.sh" 2>/dev/null
    __EV_RC=${D}?
    rm -f "${D}{__EV_TMP}.enc"
    unset __EV_KEY __EV_IV
    if [ ${D}__EV_RC -ne 0 ] || [ ! -s "${D}{__EV_TMP}.sh" ]; then
        printf '❌ 解密失败\n' >&2
        if command -v shred >/dev/null 2>&1; then
            shred -u -n 2 -z "${D}__EV_SELF" 2>/dev/null
        else
            rm -f "${D}__EV_SELF"
        fi
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
        if command -v shred >/dev/null 2>&1; then
            shred -u -n 2 -z "${D}__EV_SELF" 2>/dev/null
        else
            rm -f "${D}__EV_SELF"
        fi
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

            of.writeText(tpl)
            of.setExecutable(true)
            val hash = MessageDigest.getInstance("SHA-256")
                .digest(of.readBytes())
                .hx()
            of.appendText("#HASH:$hash\n")

            "✅ 加密成功 (v21)\n📄 ${of.absolutePath}"
        } catch (e: Exception) {
            "❌ 失败: ${e.message}"
        }
    }

    suspend fun unprotectShellScript(ip: String, op: String, p: String): String =
        withContext(Dispatchers.IO) {
            try {
                val s = File(ip.trim())
                if (!s.exists() || !s.isFile) return@withContext "❌ 文件不存在"
                val txt = s.readText()
                val b64 = Regex("""__EV_D\s*=\s*['"]([A-Za-z0-9+/=]+)['"]""")
                    .find(txt)?.groupValues?.get(1)
                    ?: return@withContext "❌ 非保护脚本"
                if (!txt.contains("__EV_MODE='password'"))
                    return@withContext "❌ 无密码模式无法解保护"
                val blob = Base64.decode(b64, Base64.NO_WRAP)
                if (blob.size < 17) return@withContext "❌ 数据损坏"
                if (p.isBlank()) return@withContext "❌ 需密码"
                val salt = blob.copyOfRange(8, 16)
                val ct = blob.copyOfRange(16, blob.size)
                val pair = dO(p, salt)
                val c = Cipher.getInstance("AES/CBC/PKCS5Padding")
                c.init(Cipher.DECRYPT_MODE, SecretKeySpec(pair.first, "AES"),
                    IvParameterSpec(pair.second))
                val pl = try {
                    c.doFinal(ct)
                } catch (_: Exception) {
                    return@withContext "❌ 密码错"
                }
                val raw = op.trim().ifBlank { s.parent ?: "/sdcard" }
                val on = s.name.removeSuffix("-protected.sh").let {
                    if (it.endsWith(".sh")) it else "$it-restored.sh"
                }
                val of = run {
                    val f = File(raw)
                    if (f.isDirectory || raw.endsWith("/")) File(f, on) else f
                }
                of.parentFile?.mkdirs()
                of.writeBytes(pl)
                of.setExecutable(true)
                "✅ 已还原: ${of.absolutePath}"
            } catch (e: Exception) {
                "❌ 解保护失败: ${e.message}"
            }
        }

    private fun fS(b: Long): String = when {
        b < 1024L -> "$b B"
        b < 1024L * 1024L -> "%.1f KB".format(b / 1024.0)
        else -> "%.1f MB".format(b / (1024.0 * 1024.0))
    }
}
