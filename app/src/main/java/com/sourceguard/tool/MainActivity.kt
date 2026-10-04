package com.sourceguard.tool

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.sourceguard.tool.ui.theme.SourceGuardTheme

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
            Toast.makeText(
                this,
                "⚠️ 环境警告: ${warns.joinToString("/") { it.name }}",
                Toast.LENGTH_SHORT
            ).show()
        }

        val notice = getString(R.string.announcement)
        enableEdgeToEdge()
        setContent {
            SourceGuardTheme {
                var showNotice by remember { mutableStateOf(true) }
                EncryptApp(securityReport = items)
                if (showNotice) {
                    AnnouncementDialog(text = notice) { showNotice = false }
                }
            }
        }
    }
}
