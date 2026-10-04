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

        // ==== 启动反逆向检查 ====
        val report = SecurityChecker.check(this)

        // 签名篡改 → 直接退出
        if (!report.signatureValid) {
            Toast.makeText(this, "❌ APK 签名被篡改，拒绝运行", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // 调试器 / Frida / Xposed → 直接退出
        if (report.debugger || report.frida || report.xposed) {
            Toast.makeText(this, "❌ 检测到逆向环境，拒绝运行", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        enableEdgeToEdge()
        setContent {
            EncryptVaultTheme {
                EncryptApp(securityReport = report)
            }
        }
    }
}
