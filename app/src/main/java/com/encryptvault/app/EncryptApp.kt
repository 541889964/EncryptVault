package com.encryptvault.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.*
import com.encryptvault.app.crypto.CryptoManager
import com.encryptvault.app.crypto.FileItem
import com.encryptvault.app.crypto.ProcessStatus
import kotlinx.coroutines.*

// ---- 静态毛玻璃背景 (无 blur, 用 radialGradient 模拟, 性能提升 100x) ----
@Composable
fun LiquidBackground(content: @Composable () -> Unit) {
    val trans = rememberInfiniteTransition(label = "bg")
    val shift by trans.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(20000, easing = LinearEasing)), label = "s"
    )
    val glow by trans.animateFloat(
        0.18f, 0.32f,
        infiniteRepeatable(tween(4000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "g"
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF0F0C29), Color(0xFF302B63),
                        Color(0xFF24243E), Color(0xFF1A1A2E)
                    ),
                    start = Offset(shift * 800f, 0f),
                    end = Offset(0f, shift * 800f + 400f)
                )
            )
    ) {
        // 两个径向光斑 (radialGradient 模拟模糊, 不耗 GPU)
        Box(
            Modifier
                .offset(x = 30.dp, y = 100.dp)
                .size(260.dp)
                .background(
                    Brush.radialGradient(listOf(Color(0xFF6C63FF).copy(alpha = glow), Color.Transparent)),
                    CircleShape
                )
        )
        Box(
            Modifier
                .offset(x = 150.dp, y = 420.dp)
                .size(220.dp)
                .background(
                    Brush.radialGradient(listOf(Color(0xFFFF6584).copy(alpha = glow * 0.8f), Color.Transparent)),
                    CircleShape
                )
        )
        content()
    }
}

// ---- 玻璃卡片 (无 blur) ----
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(Color.White.copy(alpha = 0.06f))
            .border(0.5.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(cornerRadius))
            .padding(16.dp),
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
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
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("EncryptVault", fontWeight = FontWeight.Bold, color = Color.White) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            },
            bottomBar = { GlassBottomBar(selectedTab) { selectedTab = it } },
            floatingActionButton = {
                if (selectedTab == 0) {
                    FloatingActionButton(
                        onClick = { filePicker.launch(arrayOf("*/*")) },
                        containerColor = Color(0xFF6C63FF),
                        contentColor = Color.White,
                        shape = CircleShape
                    ) { Icon(Icons.Default.Add, "添加") }
                }
            }
        ) { pad ->
            // ---- 用 AnimatedContent 做页面过渡, 避免重建背景 ----
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    (fadeIn(tween(220)) + slideInHorizontally { it / 12 })
                        .togetherWith(fadeOut(tween(180)))
                },
                label = "tab",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(pad)
                    .padding(horizontal = 16.dp)
            ) { tab ->
                when (tab) {
                    0 -> EncryptPage(
                        files, isProcessing, statusText, password, showPassword, crypto,
                        onAddFile = { filePicker.launch(arrayOf("*/*")) },
                        onAddDir = { dirPicker.launch(null) },
                        onPasswordChange = { password = it },
                        onToggleShow = { showPassword = !showPassword },
                        onEncrypt = {
                            if (password.length < 6) statusText = "密码至少 6 位"
                            else scope.launch {
                                isProcessing = true
                                var ok = 0
                                files.forEach { f ->
                                    statusText = "加密中: ${f.name}"
                                    if (crypto.encryptFile(f.uri, password)) ok++
                                }
                                statusText = "完成: $ok/${files.size} 个文件"
                                isProcessing = false
                            }
                        },
                        onDecrypt = {
                            if (password.isBlank()) statusText = "请输入密码"
                            else scope.launch {
                                isProcessing = true
                                var ok = 0
                                files.forEach { f ->
                                    statusText = "解密中: ${f.name}"
                                    if (crypto.decryptFile(f.uri, password)) ok++
                                }
                                statusText = if (ok == 0) "解密失败: 密码错误或文件损坏"
                                             else "完成: $ok/${files.size} 个文件"
                                isProcessing = false
                            }
                        },
                        onClear = { files = emptyList(); statusText = "" }
                    )
                    1 -> ShellProtectPage(crypto)
                    2 -> SettingsPage()
                }
            }
        }
    }
}

// ---- 加密页 ----
@Composable
fun EncryptPage(
    files: List<FileItem>, isProcessing: Boolean, statusText: String,
    password: String, showPassword: Boolean, crypto: CryptoManager,
    onAddFile: () -> Unit, onAddDir: () -> Unit,
    onPasswordChange: (String) -> Unit, onToggleShow: () -> Unit,
    onEncrypt: () -> Unit, onDecrypt: () -> Unit, onClear: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        GlassCard(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("加密密码", color = Color.White.copy(0.7f), fontSize = 13.sp)
                    Spacer(Modifier.weight(1f))
                    val strength = when {
                        password.length >= 12 -> "强" to Color(0xFF4CAF50)
                        password.length >= 8  -> "中" to Color(0xFFFFC107)
                        password.isNotEmpty() -> "弱" to Color(0xFFFF5252)
                        else -> "" to Color.Transparent
                    }
                    if (strength.first.isNotEmpty()) {
                        Text(strength.first, color = strength.second, fontSize = 12.sp,
                            fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("输入密码 (建议 12 位以上)", color = Color.White.copy(0.3f)) },
                    visualTransformation = if (showPassword) VisualTransformation.None
                        else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = onToggleShow) {
                            Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                null, tint = Color.White.copy(0.5f))
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF6C63FF),
                        unfocusedBorderColor = Color.White.copy(0.2f),
                        cursorColor = Color(0xFF6C63FF)
                    ),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true
                )
            }
        }

        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassActionButton("添加文件", Icons.Outlined.InsertDriveFile, Modifier.weight(1f), onAddFile)
            GlassActionButton("添加目录", Icons.Outlined.FolderOpen, Modifier.weight(1f), onAddDir)
        }

        AnimatedVisibility(visible = statusText.isNotEmpty()) {
            Text(
                statusText,
                color = when {
                    statusText.startsWith("完成") -> Color(0xFF4CAF50)
                    statusText.contains("失败") -> Color(0xFFFF5252)
                    else -> Color(0xFF6C63FF)
                },
                fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        LazyColumn(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(files, key = { it.uri.toString() }) { f -> GlassFileItem(f) }
        }

        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onEncrypt,
                enabled = !isProcessing && files.isNotEmpty(),
                modifier = Modifier.weight(1f).height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C63FF)),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (isProcessing) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                else {
                    Icon(Icons.Default.Lock, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("加密", fontWeight = FontWeight.SemiBold)
                }
            }
            OutlinedButton(
                onClick = onDecrypt,
                enabled = !isProcessing && files.isNotEmpty(),
                modifier = Modifier.weight(1f).height(50.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                border = BorderStroke(1.dp, Color.White.copy(0.3f)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.LockOpen, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("解密")
            }
        }
    }
}

// ---- Shell 保护页 ----
@Composable
fun ShellProtectPage(crypto: CryptoManager) {
    var inputPath by remember { mutableStateOf("") }
    var outputPath by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    var isWorking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LazyColumn(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Column {
                    Text("🛡 Shell 脚本源码保护", color = Color.White,
                        fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("双层保护: AES-256-GCM + 分片混淆 · 运行时仅内存解密 · 明文永不落盘",
                        color = Color.White.copy(0.6f), fontSize = 12.sp)
                }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Column {
                    OutlinedTextField(
                        value = inputPath, onValueChange = { inputPath = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("源文件路径", color = Color.White.copy(0.5f)) },
                        placeholder = { Text("/storage/emulated/0/myscript.sh", color = Color.White.copy(0.3f)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF6C63FF),
                            unfocusedBorderColor = Color.White.copy(0.2f)
                        ),
                        shape = RoundedCornerShape(12.dp), singleLine = true
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = outputPath, onValueChange = { outputPath = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("输出路径 (可填目录)", color = Color.White.copy(0.5f)) },
                        placeholder = { Text("/storage/emulated/0/Download", color = Color.White.copy(0.3f)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF6C63FF),
                            unfocusedBorderColor = Color.White.copy(0.2f)
                        ),
                        shape = RoundedCornerShape(12.dp), singleLine = true
                    )
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                isWorking = true
                                result = "正在保护..."
                                result = crypto.protectShellScript(inputPath, outputPath)
                                isWorking = false
                            }
                        },
                        enabled = !isWorking,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C63FF)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isWorking) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        else {
                            Icon(Icons.Default.Security, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("生成保护脚本", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
        if (result.isNotEmpty()) {
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text(result, color = Color.White.copy(0.85f),
                        fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

// ---- 设置页 (含 900 字公告) ----
@Composable
fun SettingsPage() {
    LazyColumn(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Column {
                    Text("加密设置", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("算法: AES-256-GCM", color = Color.White.copy(0.75f), fontSize = 14.sp)
                    Text("密钥派生: PBKDF2-HMAC-SHA256 · 200,000 轮", color = Color.White.copy(0.75f), fontSize = 14.sp)
                    Text("盐值: 每文件随机 16 字节", color = Color.White.copy(0.75f), fontSize = 14.sp)
                    Text("IV: 每文件随机 16 字节", color = Color.White.copy(0.75f), fontSize = 14.sp)
                    Text("认证: GCM 128-bit Tag", color = Color.White.copy(0.75f), fontSize = 14.sp)
                    Spacer(Modifier.height(10.dp))
                    Text("最低支持: Android 5.0 (API 21)", color = Color(0xFF4CAF50), fontSize = 13.sp)
                }
            }
        }

        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Column {
                    Text("📢 公告与说明", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(10.dp))
                    Text(ANNOUNCEMENT, color = Color.White.copy(0.78f), fontSize = 13.sp,
                        lineHeight = 20.sp)
                }
            }
        }

        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Column {
                    Text("关于", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("EncryptVault v2.0.0", color = Color.White.copy(0.75f), fontSize = 14.sp)
                    Text("Jetpack Compose + 液态玻璃 UI", color = Color.White.copy(0.5f), fontSize = 12.sp)
                    Text("开源 · 无网络 · 无遥测 · 无广告", color = Color.White.copy(0.5f), fontSize = 12.sp)
                }
            }
        }
    }
}

// ---- 通用组件 ----
@Composable
fun GlassActionButton(text: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(0.08f))
            .border(0.5.dp, Color.White.copy(0.15f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Color.White.copy(0.8f), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(text, color = Color.White.copy(0.9f), fontSize = 14.sp)
        }
    }
}

@Composable
fun GlassFileItem(file: FileItem) {
    GlassCard(Modifier.fillMaxWidth(), cornerRadius = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val icon = when {
                file.name.endsWith(".sh") -> Icons.Outlined.Terminal
                file.name.endsWith(".txt") -> Icons.Outlined.Description
                else -> Icons.Outlined.InsertDriveFile
            }
            Icon(icon, null, tint = Color(0xFF6C63FF), modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(file.name, color = Color.White, fontSize = 14.sp,
                    fontWeight = FontWeight.Medium, maxLines = 1)
                Text(file.size, color = Color.White.copy(0.4f), fontSize = 11.sp)
            }
            Box(
                Modifier
                    .size(8.dp)
                    .background(
                        when (file.status) {
                            ProcessStatus.DONE -> Color(0xFF4CAF50)
                            ProcessStatus.ENCRYPTED -> Color(0xFF6C63FF)
                            else -> Color.White.copy(0.3f)
                        }, CircleShape
                    )
            )
        }
    }
}

@Composable
fun GlassBottomBar(selected: Int, onSelect: (Int) -> Unit) {
    GlassCard(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        cornerRadius = 24.dp
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            val items = listOf(
                Triple("加密", Icons.Outlined.Lock, Icons.Filled.Lock),
                Triple("Shell", Icons.Outlined.Security, Icons.Filled.Security),
                Triple("设置", Icons.Outlined.Settings, Icons.Filled.Settings)
            )
            items.forEachIndexed { i, (label, outIcon, fillIcon) ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelect(i) }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(
                        if (selected == i) fillIcon else outIcon, null,
                        tint = if (selected == i) Color(0xFF6C63FF) else Color.White.copy(0.4f),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(label,
                        color = if (selected == i) Color(0xFF6C63FF) else Color.White.copy(0.4f),
                        fontSize = 11.sp)
                }
            }
        }
    }
}

// ---- 900 字公告 ----
val ANNOUNCEMENT = """
关于 EncryptVault

EncryptVault 是一款面向 Android 平台的本地文件加密工具，基于 Jetpack Compose 构建的现代化界面，融合了 iOS 风格的液态玻璃视觉语言与 Material 3 设计规范。应用完全离线运行，所有加密操作均在设备本地完成，不进行任何网络请求，不上传任何数据，从设计上杜绝了云端泄露的可能性。

【加密架构】
应用采用 AES-256-GCM 作为核心对称加密算法。该算法是当前国际公认安全等级最高的对称加密标准之一，被广泛应用于政府、金融、军事等对数据安全要求极高的领域。GCM 模式在提供加密的同时还提供了完整性认证，任何对密文的篡改都会被解密时立刻检测到并导致解密失败，从而防止了中间人攻击与数据篡改。

密钥派生使用 PBKDF2-HMAC-SHA256 算法，迭代次数高达 200,000 次。这意味着攻击者若要暴力破解密码，每次尝试都需要执行二十万次哈希运算，大幅提升了破解成本。即使使用高性能 GPU 集群，一个 8 位复杂密码的穷举时间也以数百年计。

每个文件在加密时都会生成独立的 16 字节随机盐值与 16 字节随机初始向量，通过 SecureRandom 硬件熵源生成，确保相同密码加密相同文件时产生的密文也完全不同，有效抵抗已知明文攻击与重放攻击。密文文件格式为：版本号（1 字节）+ 盐值（16 字节）+ 初始向量（16 字节）+ 密文与认证标签。

【Shell 脚本源码保护】
针对 Shell 脚本等需要保护源码的场景，应用提供了专门的源码保护功能。该功能会对 .sh 文件进行双层处理：第一层使用 AES-256-GCM 加密原始源码；第二层对加密后的字节流进行分片与位运算混淆，进一步提升逆向难度。生成的保护脚本在运行时会在内存中完成反向解密，明文永远不会写入磁盘，从物理层面杜绝了源码泄露。

【使用建议】
建议使用 12 位以上包含大小写字母、数字与特殊符号的强密码。密码是解密文件的唯一凭证，一旦丢失将无法恢复文件，请务必妥善保管。对于重要文件，建议在加密后同时备份原始文件与加密文件，以防密码遗忘导致数据永久丢失。

【隐私承诺】
本应用不收集任何用户数据，不包含任何遥测、统计或广告 SDK，不申请除文件读写之外的任何敏感权限。所有代码完全开源，接受社区审计。

感谢您选择 EncryptVault，愿您的数据永远安全。
""".trimIndent()
