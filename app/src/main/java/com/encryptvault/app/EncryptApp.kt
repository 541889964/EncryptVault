package com.encryptvault.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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

// ============================================================
//  液态背景 — graphicsLayer 跳过重组, 静态 Brush, 零卡顿
// ============================================================
@Composable
fun LiquidBackground(content: @Composable () -> Unit) {
    val staticBrush = remember {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFF0F0C29), Color(0xFF302B63),
                Color(0xFF24243E), Color(0xFF1A1A2E)
            )
        )
    }
    val glow = remember {
        Brush.radialGradient(
            listOf(Color(0xFF6C63FF).copy(alpha = 0.28f), Color.Transparent)
        )
    }
    val glow2 = remember {
        Brush.radialGradient(
            listOf(Color(0xFFFF6584).copy(alpha = 0.22f), Color.Transparent)
        )
    }
    val trans = rememberInfiniteTransition(label = "bg")
    val shift by trans.animateFloat(
        0f, 400f,
        infiniteRepeatable(tween(30000, easing = LinearEasing)),
        label = "s"
    )

    Box(Modifier.fillMaxSize().background(staticBrush)) {
        // graphicsLayer 的 lambda 只在绘制阶段执行, 读 State 不触发重组
        Box(
            Modifier
                .size(280.dp)
                .graphicsLayer {
                    translationX = shift * 1.2f
                    translationY = 90f
                }
                .background(glow, CircleShape)
        )
        Box(
            Modifier
                .size(240.dp)
                .graphicsLayer {
                    translationX = 900f - shift * 0.9f
                    translationY = 420f + shift * 0.4f
                }
                .background(glow2, CircleShape)
        )
        content()
    }
}

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

@OptIn(ExperimentalMaterial3Api::class)
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
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    actions = {
                        if (selectedTab == 0 && files.isNotEmpty()) {
                            IconButton(onClick = {
                                files = emptyList()
                                statusText = "已清空列表"
                            }) {
                                Icon(Icons.Default.DeleteSweep, "清空列表", tint = Color.White)
                            }
                        }
                    }
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
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(pad)
                    .padding(horizontal = 16.dp)
            ) {
                when (selectedTab) {
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
                        onClear = { files = emptyList(); statusText = "已清空列表" }
                    )
                    1 -> ShellProtectPage(crypto)
                    2 -> SettingsPage()
                }
            }
        }
    }
}

// ============================================================
//  加密页
// ============================================================
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
                    val (label, color) = when {
                        password.length >= 12 -> "强" to Color(0xFF4CAF50)
                        password.length >= 8  -> "中" to Color(0xFFFFC107)
                        password.isNotEmpty() -> "弱" to Color(0xFFFF5252)
                        else -> "" to Color.Transparent
                    }
                    if (label.isNotEmpty()) {
                        Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
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
                    statusText.startsWith("完成") || statusText.startsWith("已清空") -> Color(0xFF4CAF50)
                    statusText.contains("失败") -> Color(0xFFFF5252)
                    else -> Color(0xFF6C63FF)
                },
                fontSize = 13.sp, modifier = Modifier.padding(vertical = 4.dp)
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

        if (files.isNotEmpty()) {
            TextButton(
                onClick = onClear,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) {
                Icon(Icons.Default.DeleteSweep, null, tint = Color.White.copy(0.6f), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("清空列表", color = Color.White.copy(0.6f), fontSize = 13.sp)
            }
        }
    }
}

// ============================================================
//  Shell 保护页 (含解保护)
// ============================================================
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
                    Text("AES-256-CBC 加密 · 运行时仅内存解密 · 明文永不落盘 · 保护脚本可直接执行",
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
                    Row(Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
                            modifier = Modifier.weight(1f).height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C63FF)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isWorking) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                            else {
                                Icon(Icons.Default.Security, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("加密", fontWeight = FontWeight.SemiBold)
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    isWorking = true
                                    result = "正在还原..."
                                    result = crypto.unprotectShellScript(inputPath, outputPath)
                                    isWorking = false
                                }
                            },
                            enabled = !isWorking,
                            modifier = Modifier.weight(1f).height(48.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = BorderStroke(1.dp, Color.White.copy(0.3f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.LockOpen, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("解保护")
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

// ============================================================
//  设置页 (含 900 字公告)
// ============================================================
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
                    Text("密钥派生: PBKDF2-HMAC-SHA256 · 600,000 轮", color = Color.White.copy(0.75f), fontSize = 14.sp)
                    Text("盐值: 每文件随机 16 字节", color = Color.White.copy(0.75f), fontSize = 14.sp)
                    Text("IV: 每文件随机 16 字节", color = Color.White.copy(0.75f), fontSize = 14.sp)
                    Text("认证: GCM 128-bit Tag", color = Color.White.copy(0.75f), fontSize = 14.sp)
                    Text("密钥校验: 8 字节指纹预检", color = Color.White.copy(0.75f), fontSize = 14.sp)
                    Spacer(Modifier.height(10.dp))
                    Text("Shell 保护: AES-256-CBC + 128 位随机密钥", color = Color.White.copy(0.75f), fontSize = 14.sp)
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
                    Text("EncryptVault v3.0.0", color = Color.White.copy(0.75f), fontSize = 14.sp)
                    Text("Jetpack Compose + 液态玻璃 UI", color = Color.White.copy(0.5f), fontSize = 12.sp)
                    Text("开源 · 无网络 · 无遥测 · 无广告", color = Color.White.copy(0.5f), fontSize = 12.sp)
                }
            }
        }
    }
}

// ============================================================
//  通用组件
// ============================================================
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

// ============================================================
//  900 字公告
// ============================================================
val ANNOUNCEMENT = """
关于 EncryptVault

EncryptVault 是一款面向 Android 平台的本地文件加密工具，基于 Jetpack Compose 构建的现代化界面，融合了 iOS 风格的液态玻璃视觉语言与 Material 3 设计规范。应用完全离线运行，所有加密操作均在设备本地完成，不进行任何网络请求，不上传任何数据，从设计上杜绝了云端泄露的可能性。

【加密架构】
应用采用 AES-256-GCM 作为核心对称加密算法。该算法是当前国际公认安全等级最高的对称加密标准之一，被广泛应用于政府、金融、军事等对数据安全要求极高的领域。GCM 模式在提供加密的同时还提供了完整性认证，任何对密文的篡改都会被解密时立刻检测到并导致解密失败，从而防止了中间人攻击与数据篡改。

密钥派生使用 PBKDF2-HMAC-SHA256 算法，迭代次数高达 600,000 次。这意味着攻击者若要暴力破解密码，每次尝试都需要执行六十万次哈希运算，大幅提升了破解成本。即使使用高性能 GPU 集群，一个 12 位复杂密码的穷举时间也以数千年计。

每个文件在加密时都会生成独立的 16 字节随机盐值与 16 字节随机初始向量，通过 SecureRandom 硬件熵源生成，确保相同密码加密相同文件时产生的密文也完全不同，有效抵抗已知明文攻击与重放攻击。密文文件格式为：版本号（1 字节）+ 盐值（16 字节）+ 初始向量（16 字节）+ 密钥指纹（8 字节）+ 密文与认证标签。密钥指纹用于在真正解密前快速判断密码是否正确，避免因 GCM 解密失败信息泄露明文特征。

【Shell 脚本源码保护】
针对 Shell 脚本等需要保护源码的场景，应用提供了专门的源码保护功能。该功能使用 256 位随机密钥对 .sh 文件进行 AES-256-CBC 加密，生成的保护脚本采用自解密架构：运行时读取内嵌的 Base64 密文，在内存中完成解密，明文仅在内存中短暂存在并被立即送入 bash 解释器执行，从不写入磁盘。这从物理层面杜绝了源码泄露。同时，应用提供"解保护"功能，可根据保护脚本中内嵌的密钥信息随时还原原始源码。

保护脚本兼容 Termux、Linux、macOS 等所有具备 bash、base64、openssl 三种工具的 POSIX 系统。建议在 Termux 中安装 openssl（pkg install openssl）以获得最佳兼容性。

【使用建议】
建议使用 12 位以上包含大小写字母、数字与特殊符号的强密码。密码是解密文件的唯一凭证，一旦丢失将无法恢复文件，请务必妥善保管。对于重要文件，建议在加密后同时备份原始文件与加密文件，以防密码遗忘导致数据永久丢失。Shell 脚本的保护脚本本身包含密钥信息，任何人拿到保护脚本即可执行，但无法直接看到源码。

【隐私承诺】
本应用不收集任何用户数据，不包含任何遥测、统计或广告 SDK，不申请除文件读写之外的任何敏感权限。所有代码完全开源，接受社区审计。应用不进行任何联网请求，无后台服务，无推送，无唤醒锁，对设备电池与流量零消耗。

感谢您选择 EncryptVault，愿您的数据永远安全。
""".trimIndent()
