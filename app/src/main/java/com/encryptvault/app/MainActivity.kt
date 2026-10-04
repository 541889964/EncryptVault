
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
        val failed = items.firstOrNull { !it.passed }

        if (failed != null) {
            Toast.makeText(this,
                "❌ ${failed.name}: ${failed.detail}",
                Toast.LENGTH_LONG).show()
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
