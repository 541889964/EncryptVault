package com.encryptvault.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.encryptvault.app.crypto.CryptoManager
import com.encryptvault.app.crypto.FileItem
import com.encryptvault.app.crypto.ProcessStatus
import kotlinx.coroutines.*

@Composable
fun LiquidBackground(content: @Composable () -> Unit) {
    val bg = remember { Brush.linearGradient(listOf(
        Color(0xFF0A0E27), Color(0xFF1B1F3B), Color(0xFF0F1436), Color(0xFF0A0E27))) }
    val glow = remember { Brush.radialGradient(listOf(
        Color(0xFF6C63FF).copy(alpha = 0.32f), Color.Transparent)) }
    val glow2 = remember { Brush.radialGradient(listOf(
        Color(0xFFFF6584).copy(alpha = 0.20f), Color.Transparent)) }
    val trans = rememberInfiniteTransition(label = "bg")
    val shift by trans.animateFloat(0f, 500f,
        infiniteRepeatable(tween(35000, easing = LinearEasing)), label = "s")
    Box(Modifier.fillMaxSize().background(bg)) {
        Box(Modifier.size(320.dp).graphicsLayer {
            translationX = shift * 1.1f; translationY = 60f
        }.background(glow, CircleShape))
        Box(Modifier.size(260.dp).graphicsLayer {
            translationX = 1000f - shift * 0.85f; translationY = 500f + shift * 0.3f
        }.background(glow2, CircleShape))
        content()
    }
}

@Composable
fun GlassCard(modifier: Modifier = Modifier, cornerRadius: Dp = 22.dp,
    content: @Composable BoxScope.() -> Unit) {
    Box(modifier.clip(RoundedCornerShape(cornerRadius))
        .background(Color.White.copy(alpha = 0.055f))
        .border(0.5.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(cornerRadius))
        .padding(18.dp), content = content)
}

@Composable
fun GlassTextField(value: String, onValueChange: (String) -> Unit, label: String,
    placeholder: String = "", isPassword: Boolean = false,
    showPassword: Boolean = false, onToggleVisibility: (() -> Unit)? = null,
    isNumber: Boolean = false, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, color = Color.White.copy(0.55f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(value = value, onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder, color = Color.White.copy(0.28f), fontSize = 14.sp) },
            visualTransformation = if (isPassword && !showPassword)
                PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = if (isNumber) KeyboardOptions(keyboardType = KeyboardType.Number)
                              else KeyboardOptions.Default,
            trailingIcon = onToggleVisibility?.let { cb -> {
                IconButton(onClick = cb) {
                    Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        null, tint = Color.White.copy(0.5f))
                }
            } },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFF6C63FF),
                unfocusedBorderColor = Color.White.copy(0.15f),
                cursorColor = Color(0xFF6C63FF),
                focusedContainerColor = Color.White.copy(0.03f),
                unfocusedContainerColor = Color.White.copy(0.03f)),
            shape = RoundedCornerShape(14.dp), singleLine = true)
    }
}

@Composable
fun EncryptApp() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val crypto = remember { CryptoManager(ctx) }

    var files by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var isProcessing by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> uris.forEach { crypto.getFileInfo(it)?.let { f -> files = files + f } } }

    val dirPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> uri?.let { scope.launch { files = files + crypto.scanDirectory(it) } } }

    LiquidBackground {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(when (selectedTab) {
                        0 -> "文件加密"; 1 -> "源码保护"; else -> "设置"
                    }, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(when (selectedTab) {
                        0 -> "Local · AES-256-GCM"
                        1 -> "PBKDF2 · 600K · MT Ready"
                        else -> "Version 5.1.0"
                    }, fontSize = 12.sp, color = Color.White.copy(0.45f), letterSpacing = 1.sp)
                }
                if (selectedTab == 0 && files.isNotEmpty()) {
                    IconButton(onClick = { files = emptyList(); statusText = "已清空" }) {
                        Icon(Icons.Outlined.DeleteSweep, null, tint = Color.White.copy(0.7f))
                    }
                }
            }
            Box(Modifier.weight(1f)) {
                when (selectedTab) {
                    0 -> HomeTab(files, isProcessing, statusText, password, showPassword,
                        onPasswordChange = { password = it },
                        onToggleShow = { showPassword = !showPassword },
                        onAddFile = { filePicker.launch(arrayOf("*/*")) },
                        onAddDir = { dirPicker.launch(null) },
                        onEncrypt = {
                            if (password.length < 6) statusText = "密码至少 6 位"
                            else scope.launch {
                                isProcessing = true; var ok = 0
                                files.forEach { f ->
                                    statusText = "加密中: ${f.name}"
                                    if (crypto.encryptFile(f.uri, password)) ok++
                                }
                                statusText = "完成: $ok/${files.size}"; isProcessing = false
                            }
                        },
                        onDecrypt = {
                            if (password.isBlank()) statusText = "请输入密码"
                            else scope.launch {
                                isProcessing = true; var ok = 0
                                files.forEach { f ->
                                    statusText = "解密中: ${f.name}"
                                    if (crypto.decryptFile(f.uri, password)) ok++
                                }
                                statusText = if (ok == 0) "解密失败: 密码错误"
                                             else "完成: $ok/${files.size}"
                                isProcessing = false
                            }
                        })
                    1 -> ShellTab(crypto)
                    2 -> SettingsTab()
                }
            }
            GlassTabBar(selectedTab) { selectedTab = it }
        }
    }
}

@Composable
fun GlassTabBar(selected: Int, onSelect: (Int) -> Unit) {
    val items = listOf(
        Triple("加密", Icons.Outlined.Lock, Icons.Filled.Lock),
        Triple("Shell", Icons.Outlined.Security, Icons.Filled.Security),
        Triple("设置", Icons.Outlined.Settings, Icons.Filled.Settings))
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEachIndexed { i, (label, outIcon, fillIcon) ->
            val active = selected == i
            Box(Modifier.weight(1f).height(58.dp)
                .clip(RoundedCornerShape(29.dp))
                .background(if (active) Color(0xFF6C63FF).copy(alpha = 0.92f)
                            else Color.White.copy(alpha = 0.07f))
                .border(0.5.dp,
                    if (active) Color(0xFF8B84FF).copy(alpha = 0.7f)
                    else Color.White.copy(alpha = 0.12f),
                    RoundedCornerShape(29.dp))
                .clickable { onSelect(i) }, contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(if (active) fillIcon else outIcon, null,
                        tint = if (active) Color.White else Color.White.copy(0.55f),
                        modifier = Modifier.size(20.dp))
                    Spacer(Modifier.height(3.dp))
                    Text(label, color = if (active) Color.White else Color.White.copy(0.55f),
                        fontSize = 10.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
    }
}

@Composable
fun HomeTab(files: List<FileItem>, isProcessing: Boolean, statusText: String,
    password: String, showPassword: Boolean,
    onPasswordChange: (String) -> Unit, onToggleShow: () -> Unit,
    onAddFile: () -> Unit, onAddDir: () -> Unit,
    onEncrypt: () -> Unit, onDecrypt: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        GlassCard(Modifier.fillMaxWidth()) {
            Column {
                GlassTextField(password, onPasswordChange, "加密密码",
                    "输入密码 (建议 12 位以上)", isPassword = true,
                    showPassword = showPassword, onToggleVisibility = onToggleShow)
                if (password.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    val s = when {
                        password.length >= 12 -> 1f to Color(0xFF4CAF50)
                        password.length >= 8  -> 0.66f to Color(0xFFFFC107)
                        else -> 0.33f to Color(0xFFFF5252)
                    }
                    Box(Modifier.fillMaxWidth().height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(0.08f))) {
                        Box(Modifier.fillMaxWidth(s.first).fillMaxHeight()
                            .background(s.second, RoundedCornerShape(2.dp)))
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AddButton("添加文件", Icons.Outlined.InsertDriveFile, Modifier.weight(1f), onAddFile)
            AddButton("添加目录", Icons.Outlined.FolderOpen, Modifier.weight(1f), onAddDir)
        }
        AnimatedVisibility(visible = statusText.isNotEmpty()) {
            Text(statusText, color = when {
                statusText.startsWith("完成") || statusText.startsWith("已清空") -> Color(0xFF4CAF50)
                statusText.contains("失败") -> Color(0xFFFF5252)
                else -> Color(0xFF9C96FF)
            }, fontSize = 13.sp, modifier = Modifier.padding(vertical = 10.dp))
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("已选文件", color = Color.White.copy(0.5f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            Text("${files.size}", color = Color.White.copy(0.5f), fontSize = 12.sp)
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (files.isEmpty()) item {
                Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.FolderOpen, null, tint = Color.White.copy(0.15f),
                            modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("还没有添加文件", color = Color.White.copy(0.3f), fontSize = 13.sp)
                    }
                }
            }
            items(files, key = { it.uri.toString() }) { f -> FileRow(f) }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onEncrypt, enabled = !isProcessing && files.isNotEmpty(),
                modifier = Modifier.weight(1f).height(54.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF6C63FF),
                    disabledContainerColor = Color.White.copy(0.08f)),
                shape = RoundedCornerShape(16.dp)) {
                if (isProcessing) CircularProgressIndicator(Modifier.size(20.dp),
                    color = Color.White, strokeWidth = 2.dp)
                else { Icon(Icons.Filled.Lock, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                    Text("加密", fontWeight = FontWeight.SemiBold, fontSize = 15.sp) }
            }
            Button(onClick = onDecrypt, enabled = !isProcessing && files.isNotEmpty(),
                modifier = Modifier.weight(1f).height(54.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White.copy(0.08f), contentColor = Color.White,
                    disabledContainerColor = Color.White.copy(0.05f)),
                border = BorderStroke(0.5.dp, Color.White.copy(0.2f)),
                shape = RoundedCornerShape(16.dp)) {
                Icon(Icons.Filled.LockOpen, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                Text("解密", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
        }
    }
}

@Composable
fun AddButton(text: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.height(48.dp).clip(RoundedCornerShape(14.dp))
        .background(Color.White.copy(0.07f))
        .border(0.5.dp, Color.White.copy(0.14f), RoundedCornerShape(14.dp))
        .clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Color.White.copy(0.8f), modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(text, color = Color.White.copy(0.9f), fontSize = 13.sp)
        }
    }
}

@Composable
fun FileRow(file: FileItem) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
        .background(Color.White.copy(0.05f))
        .border(0.5.dp, Color.White.copy(0.1f), RoundedCornerShape(14.dp))
        .padding(horizontal = 14.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF6C63FF).copy(0.15f)),
                contentAlignment = Alignment.Center) {
                Icon(when {
                    file.name.endsWith(".sh") -> Icons.Outlined.Terminal
                    file.name.endsWith(".txt") -> Icons.Outlined.Description
                    else -> Icons.Outlined.InsertDriveFile
                }, null, tint = Color(0xFF9C96FF), modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(file.name, color = Color.White, fontSize = 14.sp,
                    fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text(file.size, color = Color.White.copy(0.4f), fontSize = 11.sp)
            }
        }
    }
}

// ============================================================
//  Shell Tab (v5.1 新增模式选择 + 高级选项)
// ============================================================
@Composable
fun ShellTab(crypto: CryptoManager) {
    var inputPath by remember { mutableStateOf("") }
    var outputPath by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var password2 by remember { mutableStateOf("") }
    var showPwd by remember { mutableStateOf(false) }
    var passwordMode by remember { mutableStateOf(true) }
    var maxRunsText by remember { mutableStateOf("0") }
    var failLimitText by remember { mutableStateOf("3") }
    var advanced by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf("") }
    var isWorking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 12.dp)) {

        // ---- 模式选择 ----
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Column {
                    Text("保护模式", color = Color.White.copy(0.55f), fontSize = 12.sp,
                        fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ModeChip("🔐 密码模式", passwordMode, Modifier.weight(1f)) {
                            passwordMode = true
                        }
                        ModeChip("⚡ 无密码", !passwordMode, Modifier.weight(1f)) {
                            passwordMode = false
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        if (passwordMode)
                            "运行时需输入密码，PBKDF2 600K 轮派生密钥。脚本内零密钥，世界顶级安全。"
                        else
                            "运行时直接执行，无需输密码。密钥混淆内嵌脚本 —— 防小白不防高手，适合外挂分发。",
                        color = Color.White.copy(0.6f), fontSize = 11.sp, lineHeight = 16.sp
                    )
                }
            }
        }

        // ---- 路径 ----
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Column {
                    GlassTextField(inputPath, { inputPath = it }, "源文件路径",
                        "/storage/emulated/0/myscript.sh")
                    Spacer(Modifier.height(12.dp))
                    GlassTextField(outputPath, { outputPath = it }, "输出目录",
                        "/storage/emulated/0/Download")
                }
            }
        }

        // ---- 密码 (仅密码模式) ----
        if (passwordMode) {
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Column {
                        GlassTextField(password, { password = it },
                            "保护密码 (至少 8 位)", "输入强密码",
                            isPassword = true, showPassword = showPwd,
                            onToggleVisibility = { showPwd = !showPwd })
                        Spacer(Modifier.height(12.dp))
                        GlassTextField(password2, { password2 = it },
                            "确认密码", "再输一次",
                            isPassword = true, showPassword = showPwd)
                    }
                }
            }
        }

        // ---- 高级选项 ----
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Column {
                    Row(
                        Modifier.fillMaxWidth().clickable { advanced = !advanced },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (advanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            null, tint = Color.White.copy(0.6f), modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("高级安全选项", color = Color.White.copy(0.85f),
                            fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.weight(1f))
                        Text(
                            "次数: ${maxRunsText.ifBlank{"0"}} | 自毁: ${failLimitText.ifBlank{"0"}}",
                            color = Color.White.copy(0.4f), fontSize = 11.sp)
                    }
                    AnimatedVisibility(visible = advanced) {
                        Column {
                            Spacer(Modifier.height(14.dp))
                            GlassTextField(maxRunsText, { maxRunsText = it.filter { c -> c.isDigit() } },
                                "最大执行次数 (0 = 无限)", "0", isNumber = true)
                            Spacer(Modifier.height(4.dp))
                            Text("超过次数后脚本自动 rm -f $0 自毁",
                                color = Color.White.copy(0.4f), fontSize = 11.sp)
                            Spacer(Modifier.height(12.dp))
                            if (passwordMode) {
                                GlassTextField(failLimitText,
                                    { failLimitText = it.filter { c -> c.isDigit() } },
                                    "密码错误自毁阈值 (0 = 关闭)", "3", isNumber = true)
                                Spacer(Modifier.height(4.dp))
                                Text("连续输错密码 N 次后脚本自动自毁",
                                    color = Color.White.copy(0.4f), fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        // ---- 操作按钮 ----
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = {
                    val maxRuns = maxRunsText.toIntOrNull() ?: 0
                    val failLimit = failLimitText.toIntOrNull() ?: 0
                    when {
                        inputPath.isBlank() -> result = "❌ 请输入源文件路径"
                        passwordMode && password.length < 8 -> result = "❌ 密码至少 8 位"
                        passwordMode && password != password2 -> result = "❌ 两次密码不一致"
                        else -> scope.launch {
                            isWorking = true; result = "正在加密..."
                            result = crypto.protectShellScript(
                                inputPath, outputPath, password,
                                passwordMode, maxRuns, failLimit)
                            isWorking = false
                        }
                    }
                }, enabled = !isWorking, modifier = Modifier.weight(1f).height(54.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF6C63FF),
                        disabledContainerColor = Color.White.copy(0.08f)),
                    shape = RoundedCornerShape(16.dp)) {
                    if (isWorking) CircularProgressIndicator(Modifier.size(20.dp),
                        color = Color.White, strokeWidth = 2.dp)
                    else { Icon(Icons.Filled.Lock, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("加密", fontWeight = FontWeight.SemiBold) }
                }
                Button(onClick = {
                    when {
                        inputPath.isBlank() -> result = "❌ 请输入保护脚本路径"
                        else -> scope.launch {
                            isWorking = true; result = "正在解保护..."
                            result = crypto.unprotectShellScript(inputPath, outputPath, password)
                            isWorking = false
                        }
                    }
                }, enabled = !isWorking, modifier = Modifier.weight(1f).height(54.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(0.08f), contentColor = Color.White,
                        disabledContainerColor = Color.White.copy(0.05f)),
                    border = BorderStroke(0.5.dp, Color.White.copy(0.2f)),
                    shape = RoundedCornerShape(16.dp)) {
                    Icon(Icons.Filled.LockOpen, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("解保护", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        if (result.isNotEmpty()) item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text(result, color = Color.White.copy(0.85f), fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace, lineHeight = 18.sp)
            }
        }
    }
}

@Composable
fun ModeChip(text: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier.height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (active) Color(0xFF6C63FF).copy(alpha = 0.85f)
                        else Color.White.copy(alpha = 0.06f))
            .border(0.5.dp,
                if (active) Color(0xFF8B84FF) else Color.White.copy(alpha = 0.12f),
                RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (active) Color.White else Color.White.copy(0.7f),
            fontSize = 13.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
fun SettingsTab() {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 12.dp)) {
        item { GlassCard(Modifier.fillMaxWidth()) {
            Column {
                Text("加密算法", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.height(12.dp))
                InfoRow("对称算法", "AES-256")
                InfoRow("加密模式", "GCM (AEAD)")
                InfoRow("密钥派生", "PBKDF2-HMAC-SHA256")
                InfoRow("迭代次数", "600,000 轮")
                InfoRow("盐值", "16 字节随机")
                InfoRow("认证标签", "128 bit")
                InfoRow("Shell 密码模式", "PBKDF2 + AES-256-CBC")
                InfoRow("Shell 无密码", "AES-256-CBC + 内嵌密钥")
                InfoRow("密码传输", "stdin (不可见)")
            }
        } }
        item { GlassCard(Modifier.fillMaxWidth()) {
            Column {
                Text("安全说明", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.height(10.dp))
                Text("密码模式：密钥由密码派生，脚本内不存密钥。运行时密码走 stdin 传给 openssl，不在进程列表可见。临时文件用 umask 077 创建，权限始终 0600。trap 捕获 EXIT/INT/TERM/HUP，任何退出方式都清理临时文件。",
                    color = Color.White.copy(0.7f), fontSize = 12.sp, lineHeight = 19.sp)
                Spacer(Modifier.height(10.dp))
                Text("无密码模式：密钥以 hex 反转形式内嵌脚本，运行时反混淆后直接解密。适合外挂分发，防普通用户查看源码，但防不住逆向能力强的攻击者。",
                    color = Color.White.copy(0.7f), fontSize = 12.sp, lineHeight = 19.sp)
                Spacer(Modifier.height(10.dp))
                Text("自毁机制：密码错误 N 次或执行次数超限后，脚本执行 rm -f $0 删除自身，同时清理状态文件。状态文件位于 $HOME/.ev_state/，用脚本 SHA-256 前 16 位命名。",
                    color = Color.White.copy(0.7f), fontSize = 12.sp, lineHeight = 19.sp)
            }
        } }
        item { GlassCard(Modifier.fillMaxWidth()) {
            Column {
                Text("关于", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.height(8.dp))
                InfoRow("应用", "EncryptVault")
                InfoRow("版本", "5.1.0")
                InfoRow("网络", "零联网")
                InfoRow("数据", "零收集")
                InfoRow("兼容", "Termux / MT / Linux")
            }
        } }
    }
}

@Composable
fun InfoRow(k: String, v: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(k, color = Color.White.copy(0.5f), fontSize = 12.sp)
        Spacer(Modifier.weight(1f))
        Text(v, color = Color.White.copy(0.9f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}
