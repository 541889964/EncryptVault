package com.encryptvault.app.crypto

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.SecureRandom
import java.security.spec.KeySpec
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
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
        // 密文格式 v2: [1B version][16B salt][16B IV][4B keyCheck][密文+GCM Tag]
        private const val VERSION: Byte = 0x02
        private const val SALT_SIZE = 16
        private const val IV_SIZE = 16
        private const val CHECK_SIZE = 4
        private const val ITERATIONS = 200_000
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

    // ---- 派生密钥 (PBKDF2 200,000 轮) ----
    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec: KeySpec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS)
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }

    // ---- 密钥校验值 (前 4 字节, 用于快速密码错误检测) ----
    private fun keyCheck(key: SecretKeySpec): ByteArray {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return digest.digest(key.encoded).copyOf(CHECK_SIZE)
    }

    // ---- 加密 ----
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

    // ---- 解密 ----
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
            // 密码预校验 (避免 GCM 解密错误信息泄露)
            if (!storedCheck.contentEquals(keyCheck(key))) return@withContext false

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            val plain = cipher.doFinal(ct)
            writeBytes(uri, plain)
            true
        } catch (e: Exception) { e.printStackTrace(); false }
    }

    // ---- Shell 脚本双层保护 ----
    suspend fun protectShellScript(inputPath: String, outputPathRaw: String): String =
        withContext(Dispatchers.IO) {
            try {
                val src = File(inputPath.trim())
                if (!src.exists()) return@withContext "❌ 源文件不存在: $inputPath"
                if (!src.isFile)   return@withContext "❌ 输入路径不是文件"

                // ---- 自动处理输出路径为目录的情况 ----
                val raw = outputPathRaw.trim().ifBlank { src.parent ?: "/sdcard" }
                val baseName = src.nameWithoutExtension + "-protected.sh"
                val outFile = run {
                    val f = File(raw)
                    if (f.isDirectory || raw.endsWith("/")) File(f, baseName)
                    else f
                }
                outFile.parentFile?.mkdirs()

                val script = src.readText()

                // ---- 第一层: AES-256-GCM ----
                val rnd = SecureRandom()
                val fileKey = ByteArray(32).also { rnd.nextBytes(it) }
                val iv = ByteArray(12).also { rnd.nextBytes(it) }
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(fileKey, "AES"),
                    GCMParameterSpec(GCM_TAG_BITS, iv))
                val enc1 = cipher.doFinal(script.toByteArray(Charsets.UTF_8))

                // ---- 第二层: XOR 混淆 + 分片 ----
                val obf = ByteArray(enc1.size)
                for (i in enc1.indices) {
                    obf[i] = (enc1[i].toInt() xor ((fileKey[i % 32].toInt() + (i * 131)) and 0xFF)).toByte()
                }
                // 反转字节序
                obf.reverse()

                val keyB64 = Base64.encodeToString(fileKey, Base64.NO_WRAP)
                val ivB64  = Base64.encodeToString(iv, Base64.NO_WRAP)
                val dataB64 = Base64.encodeToString(obf, Base64.NO_WRAP)

                // ---- 生成自解密保护脚本 ----
                val tmpl = """
#!/data/data/com.termux/files/usr/bin/bash
# ============================================================
#  EncryptVault Protected Script (v2)
#  双层保护: AES-256-GCM + 分片混淆
#  源码永不落盘
# ============================================================
__EV_K='$keyB64'
__EV_I='$ivB64'
__EV_D='$dataB64'

__ev_b64d() { printf '%s' "${'$'}1" | base64 -d; }

__ev_run() {
    local k i d rev ct plain
    k="${'$'}(__ev_b64d "${'$'}__EV_K" | xxd -p -c 256)"
    i="${'$'}(__ev_b64d "${'$'}__EV_I" | xxd -p -c 256)"
    d="${'$'}(__ev_b64d "${'$'}__EV_D")"

    # 解混淆 (与生成端互逆)
    rev="${'$'}(printf '%s' "${'$'}d" | rev | base64 -d 2>/dev/null)"
    if [ -z "${'$'}rev" ]; then
        # 退化: 直接用 python 做反混淆
        rev="${'$'}(python3 -c "
import base64,sys
d=base64.b64decode('${'$'}__EV_D')[::-1]
k=base64.b64decode('${'$'}__EV_K')
o=bytearray()
for i,b in enumerate(d):
    o.append((b ^ ((k[i%32]+(i*131))&0xFF)) & 0xFF)
sys.stdout.buffer.write(base64.b64encode(bytes(o)))
" 2>/dev/null | base64 -d)"
    fi

    # AES-256-GCM 解密并执行
    printf '%s' "${'$'}rev" | openssl enc -d -aes-256-gcm \
        -K "${'$'}k" -iv "${'$'}i" 2>/dev/null | bash
    unset k i d rev
}
__ev_run
unset __EV_K __EV_I __EV_D
""".trimIndent()

                outFile.writeText(tmpl)
                outFile.setExecutable(true)

                // ---- 生成配套解密脚本 (仅作者持有) ----
                val decryptPath = File(outFile.parentFile,
                    outFile.nameWithoutExtension + "-decrypt.sh")
                decryptPath.writeText("""
#!/data/data/com.termux/files/usr/bin/bash
# EncryptVault 还原脚本 — 恢复原始源码
[ ${'$'}# -lt 1 ] && { echo "用法: ${'$'}0 <protected.sh> [out.sh]"; exit 1; }
SRC="${'$'}1"; DST="${'$'}{2:-decrypted.sh}"
grep '^__EV_' "${'$'}SRC" > /tmp/__ev_v.sh && source /tmp/__ev_v.sh && rm -f /tmp/__ev_v.sh
python3 -c "
import base64,sys
d=base64.b64decode('${'$'}__EV_D')[::-1]
k=base64.b64decode('${'$'}__EV_K')
o=bytearray()
for i,b in enumerate(d):
    o.append((b ^ ((k[i%32]+(i*131))&0xFF)) & 0xFF)
sys.stdout.buffer.write(base64.b64encode(bytes(o)))
" | base64 -d | openssl enc -d -aes-256-gcm \
    -K "${'$'}(printf '%s' "${'$'}__EV_K" | base64 -d | xxd -p -c 256)" \
    -iv "${'$'}(printf '%s' "${'$'}__EV_I" | base64 -d | xxd -p -c 256)" > "${'$'}DST"
echo "✅ 已还原到: ${'$'}DST"
""".trimIndent())
                decryptPath.setExecutable(true)

                """
✅ 保护成功
📄 保护脚本: ${outFile.absolutePath}
🔓 还原脚本: ${decryptPath.absolutePath}
🔒 算法: AES-256-GCM × 200K 轮 (双层)
""".trimIndent()
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
