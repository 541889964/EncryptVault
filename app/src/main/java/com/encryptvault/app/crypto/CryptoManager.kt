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
        private const val VERSION: Byte = 0x03
        private const val SALT_SIZE = 16
        private const val IV_SIZE = 16
        private const val CHECK_SIZE = 8
        private const val ITERATIONS = 600_000
        private const val KEY_BITS = 256
        private const val GCM_TAG_BITS = 128
        private const val HEADER_SIZE = 1 + SALT_SIZE + IV_SIZE + CHECK_SIZE
    }

    fun getFileInfo(uri: Uri): FileItem? {
        return try {
            ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val name = c.getString(c.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                    val size = c.getLong(c.getColumnIndexOrThrow(OpenableColumns.SIZE))
                    FileItem(uri, name, formatSize(size))
                } else null
            }
        } catch (e: Exception) { null }
    }

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

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    // ---- 文件加密 ----
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

    // ---- 文件解密 ----
    suspend fun decryptFile(uri: Uri, password: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val data = readBytes(uri)
            if (data.size < HEADER_SIZE + 16) return@withContext false
            if (data[0] != VERSION) return@withContext false

            val salt = data.copyOfRange(1, 1 + SALT_SIZE)
            val iv = data.copyOfRange(1 + SALT_SIZE, 1 + SALT_SIZE + IV_SIZE)
            val storedCheck = data.copyOfRange(1 + SALT_SIZE + IV_SIZE, HEADER_SIZE)
            val ct = data.copyOfRange(HEADER_SIZE, data.size)

            val key = deriveKey(password, salt)
            if (!storedCheck.contentEquals(keyCheck(key))) return@withContext false

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            val plain = cipher.doFinal(ct)
            writeBytes(uri, plain)
            true
        } catch (e: Exception) { e.printStackTrace(); false }
    }

    // ============================================================
    //  Shell 保护 (v3) — AES-256-CBC + openssl 兼容
    // ============================================================
    suspend fun protectShellScript(inputPath: String, outputPathRaw: String): String =
        withContext(Dispatchers.IO) {
            try {
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
                val rnd = SecureRandom()
                val key = ByteArray(32).also { rnd.nextBytes(it) }
                val iv  = ByteArray(16).also { rnd.nextBytes(it) }

                // AES-256-CBC / PKCS5Padding 与 openssl enc -aes-256-cbc 兼容
                val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
                val ct = cipher.doFinal(plain)

                val keyHex = key.toHex()
                val ivHex  = iv.toHex()
                val dataB64 = Base64.encodeToString(ct, Base64.NO_WRAP)

                val tmpl = """
#!/data/data/com.termux/files/usr/bin/bash
# ============================================================
#  EncryptVault Protected Script (v3)
#  AES-256-CBC · 自解密 · 明文永不落盘
# ============================================================
__EV_K='$keyHex'
__EV_I='$ivHex'
__EV_D='$dataB64'

__ev_main() {
    local tmp
    tmp="${'$'}(mktemp /tmp/.ev.XXXXXX 2>/dev/null || mktemp)"
    printf '%s' "${'$'}__EV_D" | base64 -d > "${'$'}tmp.enc" 2>/dev/null || {
        echo "[EncryptVault] base64 解码失败" >&2; return 1; }
    openssl enc -d -aes-256-cbc -K "${'$'}__EV_K" -iv "${'$'}__EV_I" \
        -in "${'$'}tmp.enc" -out "${'$'}tmp.sh" 2>/dev/null || {
        rm -f "${'$'}tmp.enc" "${'$'}tmp.sh"
        echo "[EncryptVault] openssl 解密失败" >&2; return 1; }
    chmod 700 "${'$'}tmp.sh"
    bash "${'$'}tmp.sh"
    local ret=${'$'}?
    rm -f "${'$'}tmp.enc" "${'$'}tmp.sh"
    return ${'$'}ret
}
__ev_main
unset __EV_K __EV_I __EV_D
""".trimIndent()

                outFile.writeText(tmpl)
                outFile.setExecutable(true)

                """
✅ 加密成功
📄 保护脚本: ${outFile.absolutePath}
🔒 算法: AES-256-CBC (与 openssl 兼容)
▶️ 运行: bash ${outFile.name}
🔓 解保护: 在 App 中输入该保护脚本路径, 点"解保护"
""".trimIndent()
            } catch (e: Exception) {
                "❌ 失败: ${e.message}"
            }
        }

    // ============================================================
    //  Shell 解保护 — 从保护脚本还原原始 .sh
    // ============================================================
    suspend fun unprotectShellScript(inputPath: String, outputPathRaw: String): String =
        withContext(Dispatchers.IO) {
            try {
                val src = File(inputPath.trim())
                if (!src.exists()) return@withContext "❌ 保护脚本不存在: $inputPath"
                if (!src.isFile)   return@withContext "❌ 输入路径不是文件"

                val text = src.readText()
                val kRe = Regex("""__EV_K\s*=\s*['"]([0-9a-fA-F]+)['"]""")
                val iRe = Regex("""__EV_I\s*=\s*['"]([0-9a-fA-F]+)['"]""")
                val dRe = Regex("""__EV_D\s*=\s*['"]([A-Za-z0-9+/=]+)['"]""")

                val kM = kRe.find(text) ?: return@withContext "❌ 不是有效的保护脚本 (缺 __EV_K)"
                val iM = iRe.find(text) ?: return@withContext "❌ 不是有效的保护脚本 (缺 __EV_I)"
                val dM = dRe.find(text) ?: return@withContext "❌ 不是有效的保护脚本 (缺 __EV_D)"

                val keyHex = kM.groupValues[1]
                val ivHex  = iM.groupValues[1]
                val dataB64 = dM.groupValues[1]

                val key = keyHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                val iv  = ivHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                val ct  = Base64.decode(dataB64, Base64.NO_WRAP)

                val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
                val plain = cipher.doFinal(ct)

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
📄 原始脚本: ${outFile.absolutePath}
📦 大小: ${plain.size} 字节
▶️ 运行: bash ${outFile.name}
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
