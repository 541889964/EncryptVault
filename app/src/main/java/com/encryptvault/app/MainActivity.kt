
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
        val fatal = items.firstOrNull { !it.passed && it.fatal }
        if (fatal != null) {
            Toast.makeText(this, "❌ ${fatal.name}: ${fatal.detail}", Toast.LENGTH_LONG).show()
            finishAndRemoveTask()
            return
        }

        val warns = items.filter { !it.passed && !it.fatal }
        if (warns.isNotEmpty()) {
            Toast.makeText(this,
                "⚠️ 环境警告: ${warns.joinToString(" / ") { it.name }} (不影响使用)",
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
