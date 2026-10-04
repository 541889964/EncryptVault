
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

        if (hasFatal) {
            val firstFatal = items.first { !it.passed && it.fatal }
            Toast.makeText(this,
                "❌ ${firstFatal.name}: ${firstFatal.detail}",
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
