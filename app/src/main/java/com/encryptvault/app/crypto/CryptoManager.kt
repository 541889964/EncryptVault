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
        private const val VERSION: Byte = 0x04
        private const val SALT_SIZE = 16
        private const val IV_SIZE = 16
        private const val CHECK_SIZE = 8
        private const val ITERATIONS = 600_000
        private const val KEY_BITS = 256
        private const val GCM_TAG_BITS = 128
        private const val HEADER_SIZE = 1 + SALT_SIZE + IV_SIZE + CHECK_SIZE
        // Shell 保护: openssl 兼容
        private const val OPENSSL_ITER = 600_000
        private const val OPENSSL_MD = "PBKDF2WithHmacSHA256"
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

    // ---- 文件加密 (AES-256-GCM) ----
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
            if (data.size < HEADER_SIZE + 16 || data[0] != VERSION) return@withContext false
            val salt = data.copyOfRange(1, 1 + SALT_SIZE)
            val iv = data.copyOfRange(1 + SALT_SIZE, 1 + SALT_SIZE + IV_SIZE)
            val storedCheck = data.copyOfRange(1 + SALT_SIZE + IV_SIZE, HEADER_SIZE)
            val ct = data.copyOfRange(HEADER_SIZE, data.size)
            val key = deriveKey(password, salt)
            if (!storedCheck.contentEquals(keyCheck(key))) return@withContext false
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            writeBytes(uri, cipher.doFinal(ct))
            true
        } catch (e: Exception) { e.printStackTrace(); false }
    }

    // ============================================================
    //  Shell 保护 — 密码模式 (世界顶级)
    //  PBKDF2-SHA256(600K) → 48 字节 → 32 AES key + 16 IV
    //  格式: openssl "Salted__" 兼容
    //  脚本内无密钥
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
            if (!src.exists()) return@withContext "❌ 源文件不存在: $inputPath"
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

            // openssl 兼容格式: "Salted__" (8B) + salt (8B) + ct
            val blob = ByteArray(16 + ct.size)
            System.arraycopy("Salted__".toByteArray(Charsets.US_ASCII), 0, blob, 0, 8)
            System.arraycopy(salt, 0, blob, 8, 8)
            System.arraycopy(ct, 0, blob, 16, ct.size)

            val b64 = Base64.encodeToString(blob, Base64.NO_WRAP)

            val tmpl = """
#!/data/data/com.termux/files/usr/bin/bash
# ============================================================
#  EncryptVault Protected Script (v4 · Password Mode)
#  PBKDF2-HMAC-SHA256 (600,000 轮) + AES-256-CBC
#  密钥由密码派生 · 脚本内无密钥信息
# ============================================================
__EV_D='$b64'

printf '\n🔐 输入密码: ' >&2
read -s __EV_PWD
echo >&2

__EV_TMP="${'$'}(mktemp /tmp/.ev.XXXXXX 2>/dev/null || mktemp)"
__EV_TMP_SH="${'$'}__EV_TMP.sh"
trap 'rm -f "${'$'}__EV_TMP" "${'$'}__EV_TMP_SH"' EXIT

printf '%s' "${'$'}__EV_D" | base64 -d | \
    openssl enc -d -aes-256-cbc -pbkdf2 -iter 600000 -md sha256 \
        -pass pass:"${'$'}__EV_PWD" -out "${'$'}__EV_TMP_SH" 2>/dev/null

if [ ${'$'}? -ne 0 ] || [ ! -s "${'$'}__EV_TMP_SH" ]; then
    echo "❌ 密码错误或脚本损坏" >&2
    exit 1
fi

chmod 700 "${'$'}__EV_TMP_SH"
bash "${'$'}__EV_TMP_SH"
unset __EV_PWD __EV_D
""".trimIndent()

            outFile.writeText(tmpl)
            outFile.setExecutable(true)

            """
✅ 加密成功 (密码模式)
📄 ${outFile.absolutePath}
🔒 PBKDF2-SHA256(600K) + AES-256-CBC
⚠️ 密码不在脚本内, 请务必牢记
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
            if (!src.exists()) return@withContext "❌ 保护脚本不存在: $inputPath"
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
