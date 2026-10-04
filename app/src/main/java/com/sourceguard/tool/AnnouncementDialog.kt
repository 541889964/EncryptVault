package com.sourceguard.tool

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

@Composable
fun AnnouncementDialog(text: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .background(Color(0xFF1A0B2E), RoundedCornerShape(24.dp))
                .border(
                    1.dp,
                    Brush.linearGradient(listOf(
                        Color(0xFFFF6EC7), Color(0xFFA78BFA), Color(0xFF7DD3FC))),
                    RoundedCornerShape(24.dp))
        ) {
            Column(Modifier.fillMaxSize().padding(22.dp)) {
                Text("📢 源码防破解工具 · 使用须知",
                    fontSize = 18.sp, fontWeight = FontWeight.Black,
                    color = Color(0xFFFFB6D9))
                Spacer(Modifier.height(14.dp))
                Box(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    Text(text, fontSize = 13.sp, color = Color(0xFFFFF5FA),
                        lineHeight = 21.sp)
                }
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF6EC7),
                        contentColor = Color.White),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("我已知晓", fontWeight = FontWeight.Black, fontSize = 15.sp)
                }
            }
        }
    }
}
