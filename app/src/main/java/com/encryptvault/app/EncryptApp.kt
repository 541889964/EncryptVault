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
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.*
import com.encryptvault.app.crypto.CryptoManager
import com.encryptvault.app.crypto.FileItem
import com.encryptvault.app.crypto.ProcessStatus
import kotlinx.coroutines.*

// ---- 液态玻璃卡片 (内联实现, 不依赖第三方库) ----
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
            .background(Color(0x15111111))
            .padding(16.dp),
        content = content
    )
}

// ---- 毛玻璃背景 ----
@Composable
fun LiquidBackground(content: @Composable () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "bg")
    val shift by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(20000, easing = LinearEasing)),
        label = "shift"
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF0F0C29), Color(0xFF302B63), Color(0xFF24243E),
                        Color(0xFF1A1A2E), Color(0xFF16213E)
                    ),
                    start = Offset(shift * 1000f, 0f),
                    end = Offset(0f, shift * 1000f + 500f)
                )
            )
    ) {
        val bubble = rememberInfiniteTransition(label = "b")
        val offsetX by bubble.animateFloat(
            0f, 60f, infiniteRepeatable(tween(6000), RepeatMode.Reverse), label = "x"
        )
        Box(
            Modifier
                .offset(x = offsetX.dp, y = 120.dp)
                .size(200.dp)
                .blur(80.dp)
                .background(Color(0xFF6C63FF).copy(alpha = 0.3f), CircleShape)
        )
        Box(
            Modifier
                .offset(x = (-offsetX).dp, y = 400.dp)
                .size(160.dp)
                .blur(70.dp)
                .background(Color(0xFFFF6584).copy(alpha = 0.2f), CircleShape)
        )
        content()
    }
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
    ) { uris ->
        uris.forEach { uri ->
            crypto.getFileInfo(uri)?.let { files = files + it }
        }
    }

    val dirPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            scope.launch {
                val list = crypto.scanDirectory(it)
                files = files + list
            }
        }
    }

    LiquidBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text("EncryptVault", fontWeight = FontWeight.Bold, color = Color.White)
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
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
                        files, isProcessing, statusText, password,
                        showPassword, crypto,
                        onAddFile = { filePicker.launch(arrayOf("*/*")) },
                        onAddDir = { dirPicker.launch(null) },
                        onPasswordChange = { password = it },
                        onToggleShow = { showPassword = !showPassword },
                        onEncrypt = {
                            if (password.length < 6) {
                                statusText = "密码至少 6 位"
                            } else {
                                scope.launch {
                                    isProcessing = true
                                    files.forEach { f ->
                                        statusText = "加密中: ${f.name}"
                                        crypto.encryptFile(f.uri, password)
                                    }
                                    statusText = "完成: ${files.size} 个文件"
                                    isProcessing = false
                                }
                            }
                        },
                        onDecrypt = {
                            if (password.isBlank()) {
                                statusText = "请输入密码"
                            } else {
                                scope.launch {
                                    isProcessing = true
                                    files.forEach { f ->
                                        statusText = "解密中: ${f.name}"
                                        crypto.decryptFile(f.uri, password)
                                    }
                                    statusText = "完成"
                                    isProcessing = false
                                }
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
            Text("加密密码", color = Color.White.copy(0.7f), fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("输入密码", color = Color.White.copy(0.3f)) },
                visualTransformation = if (showPassword) VisualTransformation.None
                    else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = onToggleShow) {
                        Icon(
                            if (showPassword) Icons.Default.VisibilityOff
                            else Icons.Default.Visibility,
                            null, tint = Color.White.copy(0.5f)
                        )
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
        GlassActionButton("添加文件", Icons.Outlined.InsertDriveFile,
            Modifier.weight(1f), onAddFile)
        GlassActionButton("添加目录", Icons.Outlined.FolderOpen,
            Modifier.weight(1f), onAddDir)
    }

    AnimatedVisibility(visible = statusText.isNotEmpty()) {
        Text(
            statusText,
            color = if (statusText.startsWith("完成")) Color(0xFF4CAF50)
                else if (statusText.contains("失败")) Color(0xFFFF5252)
                else Color(0xFF6C63FF),
            fontSize = 13.sp,
            modifier = Modifier.padding(vertical = 4.dp)
        )
    }

    LazyColumn(
        Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(files, key = { it.uri.toString() }) { f ->
            GlassFileItem(f)
        }
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
            if (isProcessing) {
                CircularProgressIndicator(
                    Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp
                )
            } else {
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

@Composable
fun ShellProtectPage(crypto: CryptoManager) {
    var inputPath by remember { mutableStateOf("") }
    var outputPath by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LazyColumn(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Column {
                    Text("🛡 Shell 脚本源码保护",
                        color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "对 .sh 文件进行 AES-256 加密 + 自解密封装，防止源码泄露",
                        color = Color.White.copy(0.6f), fontSize = 12.sp
                    )
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
                        placeholder = { Text("/sdcard/script.sh",
                            color = Color.White.copy(0.3f)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF6C63FF),
                            unfocusedBorderColor = Color.White.copy(0.2f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = outputPath, onValueChange = { outputPath = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("输出路径", color = Color.White.copy(0.5f)) },
                        placeholder = { Text("/sdcard/protected.sh",
                            color = Color.White.copy(0.3f)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF6C63FF),
                            unfocusedBorderColor = Color.White.copy(0.2f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                result = "正在保护..."
                                result = crypto.protectShellScript(inputPath, outputPath)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF6C63FF)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Security, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("生成保护脚本", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        if (result.isNotEmpty()) {
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text(result, color = Color.White.copy(0.8f),
                        fontSize = 13.sp, fontFamily =
                            androidx.compose.ui.text.font.FontFamily.Monospace)
                }
            }
        }
    }
}

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
                    Text("加密设置", color = Color.White,
                        fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("算法: AES-256-GCM", color = Color.White.copy(0.7f),
                        fontSize = 14.sp)
                    Text("密钥派生: PBKDF2-HMAC-SHA256 (10000轮)",
                        color = Color.White.copy(0.7f), fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("最低支持: Android 5.0 (API 21)",
                        color = Color(0xFF4CAF50), fontSize = 13.sp)
                }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Column {
                    Text("关于", color = Color.White,
                        fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("EncryptVault v1.0.0",
                        color = Color.White.copy(0.7f), fontSize = 14.sp)
                    Text("基于 Jetpack Compose + 液态玻璃 UI",
                        color = Color.White.copy(0.5f), fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun GlassActionButton(
    text: String, icon: ImageVector,
    modifier: Modifier = Modifier, onClick: () -> Unit
) {
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
                Text(file.name, color = Color.White,
                    fontSize = 14.sp, fontWeight = FontWeight.Medium,
                    maxLines = 1)
                Text(file.size, color = Color.White.copy(0.4f), fontSize = 11.sp)
            }
            Box(
                Modifier
                    .size(8.dp)
                    .background(
                        if (file.status == ProcessStatus.DONE) Color(0xFF4CAF50)
                        else if (file.status == ProcessStatus.ENCRYPTED) Color(0xFF6C63FF)
                        else Color.White.copy(0.3f),
                        CircleShape
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
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
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
                        tint = if (selected == i) Color(0xFF6C63FF)
                            else Color.White.copy(0.4f),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        label,
                        color = if (selected == i) Color(0xFF6C63FF)
                            else Color.White.copy(0.4f),
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}
