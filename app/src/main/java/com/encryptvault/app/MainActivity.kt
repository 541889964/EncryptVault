
package com.encryptvault.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.encryptvault.app.ui.theme.EncryptVaultTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val items = SecurityChecker.check(this)
        val hasFatal = SecurityChecker.hasFatal(items)

        // 只有致命项 (Frida/Xposed/调试器/APK 路径) 才阻断
        // Root / 模拟器 / 多开 / 重打包 → 仅警告, 不阻断
        if (hasFatal) {
            Toast.makeText(this, "❌ 检测到高危逆向环境，拒绝运行", Toast.LENGTH_LONG).show()
            finishAndRemoveTask()
            return
        }

        enableEdgeToEdge()
        setContent {
            EncryptVaultTheme {
                EncryptApp(securityReport = items)
            }
        }
    }
}
