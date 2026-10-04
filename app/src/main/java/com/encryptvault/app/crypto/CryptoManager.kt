package com.encryptvault.app.crypto

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.security.*
import java.security.spec.KeySpec
import javax.crypto.*
import javax.crypto.spec.*

data class FileItem(
    val uri: Uri,
    val name: String,
    val size: String,
    val status: ProcessStatus = ProcessStatus.PENDING
)

enum class ProcessStatus { PENDING, ENCRYPTED, DECRYPTED, DONE, ERROR }

class CryptoManager(private val ctx: Context) {

    // ---- 文件信息 ----
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
            if (doc.isFile) {
                list.add(FileItem(doc.uri, doc.name ?: "unknown",
                    formatSize(doc.length())))
            }
        }
        return list
    }

    // ---- 读取字节 ----
    private fun readBytes(uri: Uri): ByteArray =
        ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)

    private fun writeBytes(uri: Uri, data: ByteArray) {
        ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(data) }
    }

    // ---- 加密文件 ----
    suspend fun encryptFile(uri: Uri, password: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val plain = readBytes(uri)
                val cipher = buildCipher(password, Cipher.ENCRYPT_MODE, null)
                val encrypted = cipher.doFinal(plain)
                // 格式: [16字节 IV][密文]
                val output = ByteArray(16 + encrypted.size)
                System.arraycopy(cipher.iv, 0, output, 0, 16)
                System.arraycopy(encrypted, 0, output, 16, encrypted.size)
                writeBytes(uri, output)
                true
            } catch (e: Exception) {
                e.printStackTrace(); false
            }
        }

    // ---- 解密文件 ----
    suspend fun decryptFile(uri: Uri, password: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val data = readBytes(uri)
                if (data.size < 17) return@withContext false
                val iv = data.copyOfRange(0, 16)
                val ciphertext = data.copyOfRange(16, data.size)
                val cipher = buildCipher(password, Cipher.DECRYPT_MODE, iv)
                val decrypted = cipher.doFinal(ciphertext)
                writeBytes(uri, decrypted)
                true
            } catch (e: Exception) {
                e.printStackTrace(); false
            }
        }

    // ---- 构建 Cipher (AES-256-GCM + PBKDF2) ----
    private fun buildCipher(
        password: String, mode: Int, iv: ByteArray?
    ): Cipher {
        val salt = "EncryptVault-Salt-2026".toByteArray(Charsets.UTF_8)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec: KeySpec = PBEKeySpec(password.toCharArray(), salt, 10000, 256)
        val key = SecretKeySpec(factory.generateSecret(spec).encoded, "AES")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        if (mode == Cipher.ENCRYPT_MODE) {
            val nonce = ByteArray(16)
            SecureRandom().nextBytes(nonce)
            cipher.init(mode, key, GCMParameterSpec(128, nonce))
        } else {
            cipher.init(mode, key, GCMParameterSpec(128, iv ?: ByteArray(16)))
        }
        return cipher
    }

    // ---- Shell 脚本源码保护 ----
    suspend fun protectShellScript(
        inputPath: String, outputPath: String
    ): String = withContext(Dispatchers.IO) {
        try {
            val src = File(inputPath)
            if (!src.exists()) return@withContext "❌ 源文件不存在: $inputPath"

            val script = src.readText()
            // 1. 生成随机密钥
            val keyBytes = ByteArray(32)
            SecureRandom().nextBytes(keyBytes)
            val keyB64 = Base64.encodeToString(keyBytes, Base64.NO_WRAP)

            // 2. AES-256-GCM 加密源码
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val keySpec = SecretKeySpec(keyBytes, "AES")
            val nonce = ByteArray(12)
            SecureRandom().nextBytes(nonce)
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, GCMParameterSpec(128, nonce))
            val ciphertext = cipher.doFinal(script.toByteArray(Charsets.UTF_8))
            val payloadB64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
            val nonceB64 = Base64.encodeToString(nonce, Base64.NO_WRAP)

            // 3. 生成自解密执行脚本
            val protectedScript = """
#!/data/data/com.termux/files/usr/bin/bash
# ============================================================
#  EncryptVault Protected Script
#  源码已 AES-256-GCM 加密，运行时解密到内存
# ============================================================
__EV_KEY="${keyB64}"
__EV_IV="${nonceB64}"
__EV_DATA="${payloadB64}"

__ev_decode() {
    printf '%s' "${'$'}1" | base64 -d
}

__ev_run() {
    local key iv data tmp
    key="${'$'}(__ev_decode "${'$'}__EV_KEY")"
    iv="${'$'}(__ev_decode "${'$'}__EV_IV")"
    data="${'$'}(__ev_decode "${'$'}__EV_DATA")"

    if command -v openssl >/dev/null 2>&1; then
        tmp="${'$'}(mktemp)"
        chmod 600 "${'$'}tmp"
        printf '%s' "${'$'}data" | openssl enc -d -aes-256-gcm \
            -K "${'$'}(printf '%s' "${'$'}key" | xxd -p -c 256)" \
            -iv "${'$'}(printf '%s' "${'$'}iv" | xxd -p -c 256)" 2>/dev/null \
            | bash
        rm -f "${'$'}tmp"
    else
        echo "[EncryptVault] 需要 openssl 支持" >&2
        exit 1
    fi
    unset key iv data
}
__ev_run
unset __EV_KEY __EV_IV __EV_DATA
""".trimIndent()

            File(outputPath).writeText(protectedScript)
            File(outputPath).setExecutable(true)

            // 4. 生成配对的解密脚本
            val decryptScript = """
#!/data/data/com.termux/files/usr/bin/bash
# EncryptVault 解密脚本 — 输入保护脚本路径，输出原始源码
if [ ${'$'}# -lt 1 ]; then
    echo "用法: ${'$'}0 <protected.sh> [output.sh]"
    exit 1
fi
SRC="${'$'}1"
DST="${'$'}{2:-decrypted.sh}"
grep '^__EV_' "${'$'}SRC" > /tmp/__ev_vars.sh
source /tmp/__ev_vars.sh
rm -f /tmp/__ev_vars.sh
KEY="${'$'}(printf '%s' "${'$'}__EV_KEY" | base64 -d)"
IV="${'$'}(printf '%s' "${'$'}__EV_IV" | base64 -d)"
DATA="${'$'}(printf '%s' "${'$'}__EV_DATA" | base64 -d)"
printf '%s' "${'$'}DATA" | openssl enc -d -aes-256-gcm \
    -K "${'$'}(printf '%s' "${'$'}KEY" | xxd -p -c 256)" \
    -iv "${'$'}(printf '%s' "${'$'}IV" | xxd -p -c 256)" > "${'$'}DST"
echo "✅ 已解密到: ${'$'}DST"
""".trimIndent()

            val decryptPath = outputPath.replace(".sh", "-decrypt.sh")
            File(decryptPath).writeText(decryptScript)
            File(decryptPath).setExecutable(true)

            "✅ 保护成功\n📄 保护脚本: $outputPath\n🔓 解密脚本: $decryptPath\n🔒 算法: AES-256-GCM"
        } catch (e: Exception) {
            "❌ 失败: ${e.message}"
        }
    }

    private fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    }
}
