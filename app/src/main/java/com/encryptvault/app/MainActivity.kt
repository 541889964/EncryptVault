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

        val report = SecurityChecker.check(this)

        // 签名篡改 / 反编译重打包 → 直接退出
        if (!report.signatureValid || report.repackaged) {
            Toast.makeText(this, "❌ 签名或结构被篡改，拒绝运行", Toast.LENGTH_LONG).show()
            finishAndRemoveTask()
            return
        }
        // 调试/Frida/Xposed → 直接退出
        if (report.debugger || report.frida || report.xposed) {
            Toast.makeText(this, "❌ 检测到逆向环境，拒绝运行", Toast.LENGTH_LONG).show()
            finishAndRemoveTask()
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
