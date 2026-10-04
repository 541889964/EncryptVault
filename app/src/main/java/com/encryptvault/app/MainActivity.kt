
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

        // 任一 fatal 项未通过 -> 立即秒退
        val fatal = items.firstOrNull { !it.passed && it.fatal }
        if (fatal != null) {
            Toast.makeText(this,
                "❌ ${fatal.name}: ${fatal.detail}",
                Toast.LENGTH_LONG).show()
            finishAndRemoveTask()
            return
        }

        // 警告项 -> 只 Toast 提示
        val warns = items.filter { !it.passed && !it.fatal }
        if (warns.isNotEmpty()) {
            val summary = warns.joinToString(" / ") { it.name }
            Toast.makeText(this,
                "⚠️ 环境警告: $summary (不影响使用)",
                Toast.LENGTH_SHORT).show()
        }

        enableEdgeToEdge()
        setContent {
            EncryptVaultTheme {
                EncryptApp(securityReport = items)
            }
        }
    }
}
