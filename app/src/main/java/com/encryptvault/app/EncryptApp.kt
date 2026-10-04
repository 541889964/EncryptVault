package com.encryptvault.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.encryptvault.app.crypto.CryptoManager
import com.encryptvault.app.crypto.FileItem
import kotlinx.coroutines.*

// ============================================================
//  液态背景
// ============================================================
@Composable
fun LiquidBackground(content: @Composable () -> Unit) {
    val bg = remember { Brush.linearGradient(listOf(
        Color(0xFF0A0E27), Color(0xFF1B1F3B), Color(0xFF0F1436), Color(0xFF0A0E27))) }
    val glow = remember { Brush.radialGradient(listOf(
        Color(0xFF6C63FF).copy(alpha = 0.35f), Color.Transparent)) }
    val glow2 = remember { Brush.radialGradient(listOf(
        Color(0xFFFF6584).copy(alpha = 0.22f), Color.Transparent)) }
    val glow3 = remember { Brush.radialGradient(listOf(
        Color(0xFF5EEAD4).copy(alpha = 0.15f), Color.Transparent)) }
    val trans = rememberInfiniteTransition(label = "bg")
    val shift by trans.animateFloat(0f, 600f,
        infiniteRepeatable(tween(40000, easing = LinearEasing)), label = "s")
    Box(Modifier.fillMaxSize().background(bg)) {
        Box(Modifier.size(340.dp).graphicsLayer {
            translationX = shift * 1.1f; translationY = 60f
        }.background(glow, CircleShape))
        Box(Modifier.size(280.dp).graphicsLayer {
            translationX = 1000f - shift * 0.85f; translationY = 500f + shift * 0.3f
        }.background(glow2, CircleShape))
        Box(Modifier.size(200.dp).graphicsLayer {
            translationX = 200f + shift * 0.5f; translationY = 800f - shift * 0.4f
        }.background(glow3, CircleShape))
        content()
    }
}

// ============================================================
//  玻璃卡片 — 渐变描边 + 微光
// ============================================================
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 22.dp,
    accent: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val borderBrush = remember(accent) {
        if (accent) Brush.linearGradient(listOf(
            Color(0xFF6C63FF).copy(alpha = 0.6f),
            Color(0xFFFF6584).copy(alpha = 0.4f),
            Color(0xFF6C63FF).copy(alpha = 0.6f)
        ))
        else Brush.linearGradient(listOf(
            Color.White.copy(alpha = 0.18f),
            Color.White.copy(alpha = 0.06f)
        ))
    }
    Box(
        modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(Color.White.copy(alpha = if (accent) 0.07f else 0.05f))
            .border(0.7.dp, borderBrush, RoundedCornerShape(cornerRadius))
            .padding(18.dp),
        content = content
    )
}

// ============================================================
//  输入框
// ============================================================
@Composable
fun GlassTextField(
    value: String, onValueChange: (String) -> Unit, label: String,
    placeholder: String = "", isPassword: Boolean = false,
    showPassword: Boolean = false, onToggleVisibility: (() -> Unit)? = null,
    isNumber: Boolean = false, modifier: Modifier = Modifier
) {
    Column(modifier) {
        Text(label, color = Color.White.copy(0.6f), fontSize = 12.sp,
            fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp)
        Spacer(Modifier.height(7.dp))
        OutlinedTextField(
            value = value, onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder, color = Color.White.copy(0.25f), fontSize = 14.sp) },
            visualTransformation = if (isPassword && !showPassword)
                PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = if (isNumber) KeyboardOptions(keyboardType = KeyboardType.Number)
                              else KeyboardOptions.Default,
            trailingIcon = onToggleVisibility?.let { cb -> {
                IconButton(onClick = cb) {
                    Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        null, tint = Color.White.copy(0.55f))
                }
            } },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFF6C63FF),
                unfocusedBorderColor = Color.White.copy(0.12f),
                cursorColor = Color(0xFF6C63FF),
                focusedContainerColor = Color.White.copy(0.04f),
                unfocusedContainerColor = Color.White.copy(0.03f)),
            shape = RoundedCornerShape(14.dp), singleLine = true
        )
    }
}

// ============================================================
//  密码强度计 — 渐变动画条
// ============================================================
data class PasswordStrength(val score: Float, val label: String, val color: Color, val hint: String)

fun evalPassword(p: String): PasswordStrength {
    if (p.isEmpty()) return PasswordStrength(0f, "", Color.Transparent, "")
    var s = 0
    if (p.length >= 6) s++
    if (p.length >= 10) s++
    if (p.length >= 14) s++
    if (p.any { it.isLowerCase() } && p.any { it.isUpperCase() }) s++
    if (p.any { it.isDigit() }) s++
    if (p.any { !it.isLetterOrDigit() }) s++
    return when {
        s <= 2 -> PasswordStrength(0.25f, "弱", Color(0xFFFF5252), "建议 12 位以上含大小写+数字+符号")
        s <= 3 -> PasswordStrength(0.5f, "中", Color(0xFFFFA726), "再加符号会更安全")
        s <= 4 -> PasswordStrength(0.75f, "强", Color(0xFF66BB6A), "不错，但可以更强")
        else -> PasswordStrength(1f, "极强", Color(0xFF4CAF50), "顶级强度")
    }
}

@Composable
fun PasswordStrengthBar(p: String) {
    val s = remember(p) { evalPassword(p) }
    val animatedScore by animateFloatAsState(s.score,
        animationSpec = tween(400, easing = FastOutSlowInEasing), label = "ps")
    val animatedColor by animateColorAsState(s.color,
        animationSpec = tween(400), label = "pc")
    Column {
        Box(
            Modifier.fillMaxWidth().height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White.copy(0.08f))
        ) {
            Box(
                Modifier.fillMaxWidth(animatedScore).fillMaxHeight()
                    .background(
                        Brush.horizontalGradient(listOf(
                            animatedColor.copy(0.6f), animatedColor)),
                        RoundedCornerShape(3.dp)
                    )
            )
        }
        if (s.label.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).background(animatedColor, CircleShape))
                Spacer(Modifier.width(6.dp))
                Text(s.label, color = animatedColor, fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                Text(s.hint, color = Color.White.copy(0.35f), fontSize = 10.sp)
            }
        }
    }
}

// ============================================================
//  App 主入口
// ============================================================
@Composable
fun EncryptApp() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val crypto = remember { CryptoManager(ctx) }

    var files by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var isProcessing by remember { mutableStateOf(false) }
    var stageText by remember { mutableStateOf("") }
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
            // ---- 顶部标题 ----
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(when (selectedTab) {
                        0 -> "文件加密"; 1 -> "源码保护"; else -> "设置"
                    }, fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Color.White,
                        letterSpacing = (-0.5).sp)
                    Spacer(Modifier.height(3.dp))
                    Text(when (selectedTab) {
                        0 -> "PBKDF2-SHA512 · 2,000,000"
                        1 -> "顶级源码保护"
                        else -> "Version 7.0.0"
                    }, fontSize = 11.sp, color = Color(0xFF9C96FF).copy(0.75f),
                        letterSpacing = 1.2.sp, fontWeight = FontWeight.Medium)
                }
                if (selectedTab == 0 && files.isNotEmpty()) {
                    IconButton(onClick = { files = emptyList(); statusText = "已清空" }) {
                        Icon(Icons.Outlined.DeleteSweep, null, tint = Color.White.copy(0.75f))
                    }
                }
            }
            Box(Modifier.weight(1f)) {
                when (selectedTab) {
                    0 -> HomeTab(files, isProcessing, stageText, statusText, password, showPassword,
                        onPasswordChange = { password = it },
                        onToggleShow = { showPassword = !showPassword },
                        onAddFile = { filePicker.launch(arrayOf("*/*")) },
                        onAddDir = { dirPicker.launch(null) },
                        onEncrypt = {
                            if (password.length < 6) statusText = "密码至少 6 位"
                            else scope.launch {
                                isProcessing = true; var ok = 0
                                files.forEachIndexed { i, f ->
                                    stageText = "派生密钥 ${i+1}/${files.size} · ${f.name}"
                                    if (crypto.encryptFile(f.uri, password)) ok++
                                }
                                stageText = ""
                                statusText = "完成 · $ok/${files.size} 个文件已加密"
                                isProcessing = false
                            }
                        },
                        onDecrypt = {
                            if (password.isBlank()) statusText = "请输入密码"
                            else scope.launch {
                                isProcessing = true; var ok = 0
                                files.forEachIndexed { i, f ->
                                    stageText = "解密中 ${i+1}/${files.size} · ${f.name}"
                                    if (crypto.decryptFile(f.uri, password)) ok++
                                }
                                stageText = ""
                                statusText = if (ok == 0) "解密失败 · 密码错误或文件损坏"
                                             else "完成 · $ok/${files.size} 个文件已解密"
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

// ============================================================
//  胶囊 Tab Bar
// ============================================================
@Composable
fun GlassTabBar(selected: Int, onSelect: (Int) -> Unit) {
    val haptic = LocalHapticFeedback.current
    val items = listOf(
        Triple("加密", Icons.Outlined.Lock, Icons.Filled.Lock),
        Triple("Shell", Icons.Outlined.Security, Icons.Filled.Security),
        Triple("设置", Icons.Outlined.Settings, Icons.Filled.Settings))
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEachIndexed { i, (label, outIcon, fillIcon) ->
            val active = selected == i
            val scale by animateFloatAsState(if (active) 1f else 0.96f,
                tween(220), label = "tabScale")
            Box(Modifier.weight(1f).height(58.dp).scale(scale)
                .clip(RoundedCornerShape(29.dp))
                .background(
                    if (active)
                        Brush.linearGradient(listOf(Color(0xFF6C63FF), Color(0xFF8B7FFF)))
                    else Brush.linearGradient(listOf(
                        Color.White.copy(alpha = 0.07f), Color.White.copy(alpha = 0.07f)))
                )
                .border(0.6.dp,
                    if (active) Color(0xFF9D96FF) else Color.White.copy(alpha = 0.1f),
                    RoundedCornerShape(29.dp))
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSelect(i)
                }, contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(if (active) fillIcon else outIcon, null,
                        tint = if (active) Color.White else Color.White.copy(0.5f),
                        modifier = Modifier.size(20.dp))
                    Spacer(Modifier.height(3.dp))
                    Text(label, color = if (active) Color.White else Color.White.copy(0.5f),
                        fontSize = 10.sp, letterSpacing = 0.3.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
    }
}

// ============================================================
//  主页
// ============================================================
@Composable
fun HomeTab(
    files: List<FileItem>, isProcessing: Boolean, stageText: String, statusText: String,
    password: String, showPassword: Boolean,
    onPasswordChange: (String) -> Unit, onToggleShow: () -> Unit,
    onAddFile: () -> Unit, onAddDir: () -> Unit,
    onEncrypt: () -> Unit, onDecrypt: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        // 密码卡片
        GlassCard(Modifier.fillMaxWidth(), accent = password.isNotEmpty()) {
            Column {
                GlassTextField(password, onPasswordChange, "加密密码",
                    "输入密码 · 建议 12 位以上", isPassword = true,
                    showPassword = showPassword, onToggleVisibility = onToggleShow)
                if (password.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    PasswordStrengthBar(password)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AddButton("添加文件", Icons.Outlined.InsertDriveFile, Modifier.weight(1f), onAddFile)
            AddButton("添加目录", Icons.Outlined.FolderOpen, Modifier.weight(1f), onAddDir)
        }

        // 状态
        AnimatedVisibility(visible = stageText.isNotEmpty()) {
            GlassCard(Modifier.fillMaxWidth().padding(top = 12.dp), cornerRadius = 16.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        Modifier.size(16.dp),
                        color = Color(0xFF9C96FF), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(stageText, color = Color.White.copy(0.85f), fontSize = 12.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        AnimatedVisibility(visible = statusText.isNotEmpty() && stageText.isEmpty()) {
            Text(statusText, color = when {
                statusText.startsWith("完成") || statusText.startsWith("已清空") -> Color(0xFF4CAF50)
                statusText.contains("失败") -> Color(0xFFFF5252)
                else -> Color(0xFF9C96FF)
            }, fontSize = 13.sp, modifier = Modifier.padding(vertical = 10.dp))
        }

        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("已选文件", color = Color.White.copy(0.5f), fontSize = 12.sp,
                fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp)
            Spacer(Modifier.weight(1f))
            Text("${files.size}", color = Color.White.copy(0.6f), fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (files.isEmpty()) item {
                EmptyFilesHint()
            }
            items(files, key = { it.uri.toString() }) { f -> FileRow(f) }
        }

        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton("加密", Icons.Filled.Lock, !isProcessing && files.isNotEmpty(),
                Modifier.weight(1f)) { onEncrypt() }
            SecondaryButton("解密", Icons.Filled.LockOpen, !isProcessing && files.isNotEmpty(),
                Modifier.weight(1f)) { onDecrypt() }
        }
    }
}

// ============================================================
//  按钮组件 (带按压缩放 + 光晕)
// ============================================================
@Composable
fun PrimaryButton(
    text: String, icon: ImageVector, enabled: Boolean,
    modifier: Modifier, onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, tween(120), label = "s")
    Box(
        modifier = modifier.height(54.dp).scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (enabled)
                    Brush.linearGradient(listOf(Color(0xFF6C63FF), Color(0xFF8B7FFF)))
                else Brush.linearGradient(listOf(
                    Color.White.copy(0.08f), Color.White.copy(0.08f)))
            )
            .clickable(interactionSource = interaction, indication = null,
                enabled = enabled) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Color.White.copy(if (enabled) 1f else 0.4f),
                modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, color = Color.White.copy(if (enabled) 1f else 0.4f),
                fontWeight = FontWeight.SemiBold, fontSize = 15.sp, letterSpacing = 0.3.sp)
        }
    }
}

@Composable
fun SecondaryButton(
    text: String, icon: ImageVector, enabled: Boolean,
    modifier: Modifier, onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, tween(120), label = "s")
    Box(
        modifier = modifier.height(54.dp).scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = if (enabled) 0.08f else 0.05f))
            .border(0.6.dp,
                Color.White.copy(alpha = if (enabled) 0.22f else 0.1f),
                RoundedCornerShape(16.dp))
            .clickable(interactionSource = interaction, indication = null,
                enabled = enabled) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Color.White.copy(if (enabled) 0.9f else 0.4f),
                modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, color = Color.White.copy(if (enabled) 0.9f else 0.4f),
                fontWeight = FontWeight.SemiBold, fontSize = 15.sp, letterSpacing = 0.3.sp)
        }
    }
}

@Composable
fun AddButton(text: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, tween(100), label = "s")
    Box(
        modifier = modifier.height(48.dp).scale(scale)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(0.07f))
            .border(0.5.dp, Color.White.copy(0.14f), RoundedCornerShape(14.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Color.White.copy(0.8f), modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(text, color = Color.White.copy(0.9f), fontSize = 13.sp)
        }
    }
}

// ============================================================
//  空状态
// ============================================================
@Composable
fun EmptyFilesHint() {
    Box(Modifier.fillMaxWidth().padding(vertical = 50.dp),
        contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(72.dp).clip(CircleShape)
                    .background(Brush.radialGradient(listOf(
                        Color(0xFF6C63FF).copy(alpha = 0.25f),
                        Color(0xFF6C63FF).copy(alpha = 0.05f)))),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Lock, null, tint = Color(0xFF9C96FF).copy(0.7f),
                    modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text("还没有添加文件", color = Color.White.copy(0.5f), fontSize = 15.sp,
                fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            Text("点击上方按钮或右下角 + 添加", color = Color.White.copy(0.3f),
                fontSize = 12.sp, textAlign = TextAlign.Center)
        }
    }
}

// ============================================================
//  文件行
// ============================================================
@Composable
fun FileRow(file: FileItem) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
        .background(Color.White.copy(0.045f))
        .border(0.5.dp, Color.White.copy(0.09f), RoundedCornerShape(14.dp))
        .padding(horizontal = 14.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp))
                .background(Brush.linearGradient(listOf(
                    Color(0xFF6C63FF).copy(0.25f), Color(0xFF8B7FFF).copy(0.15f)))),
                contentAlignment = Alignment.Center) {
                Icon(when {
                    file.name.endsWith(".sh") -> Icons.Outlined.Terminal
                    file.name.endsWith(".txt") -> Icons.Outlined.Description
                    else -> Icons.Outlined.InsertDriveFile
                }, null, tint = Color(0xFF9C96FF), modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(file.name, color = Color.White, fontSize = 14.sp,
                    fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                Text(file.size, color = Color.White.copy(0.4f), fontSize = 11.sp)
            }
        }
    }
}

// ============================================================
//  Shell Tab
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

        item {
            GlassCard(Modifier.fillMaxWidth(), accent = true) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp))
                            .background(Brush.linearGradient(listOf(
                                Color(0xFF6C63FF).copy(0.4f), Color(0xFFFF6584).copy(0.3f)))),
                            contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Security, null, tint = Color.White,
                                modifier = Modifier.size(22.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("顶级源码保护", color = Color.White,
                                fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Spacer(Modifier.height(2.dp))
                            Text("PBKDF2-SHA512 · 2,000,000 轮",
                                color = Color(0xFF9C96FF), fontSize = 11.sp,
                                letterSpacing = 0.8.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }

        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Column {
                    Text("保护模式", color = Color.White.copy(0.6f), fontSize = 12.sp,
                        fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp)
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
                            "运行时需输密码。PBKDF2-SHA512 2M 轮派生密钥，脚本内零密钥。"
                        else
                            "运行时直接执行，无需密码。密钥混淆内嵌 —— 防普通查看，不防逆向。",
                        color = Color.White.copy(0.55f), fontSize = 11.sp, lineHeight = 17.sp
                    )
                }
            }
        }

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

        if (passwordMode) {
            item {
                GlassCard(Modifier.fillMaxWidth(), accent = password.isNotEmpty()) {
                    Column {
                        GlassTextField(password, { password = it },
                            "保护密码 (至少 8 位)", "输入强密码",
                            isPassword = true, showPassword = showPwd,
                            onToggleVisibility = { showPwd = !showPwd })
                        if (password.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            PasswordStrengthBar(password)
                        }
                        Spacer(Modifier.height(12.dp))
                        GlassTextField(password2, { password2 = it },
                            "确认密码", "再输一次",
                            isPassword = true, showPassword = showPwd)
                    }
                }
            }
        }

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
                            "次数: ${maxRunsText.ifBlank{"0"}} · 自毁: ${failLimitText.ifBlank{"0"}}",
                            color = Color.White.copy(0.4f), fontSize = 11.sp)
                    }
                    AnimatedVisibility(visible = advanced) {
                        Column {
                            Spacer(Modifier.height(14.dp))
                            GlassTextField(maxRunsText, { maxRunsText = it.filter { c -> c.isDigit() } },
                                "最大执行次数 (0 = 无限)", "0", isNumber = true)
                            Spacer(Modifier.height(4.dp))
                            Text("超限后自动 shred 覆盖 3 遍销毁自身",
                                color = Color.White.copy(0.4f), fontSize = 11.sp)
                            Spacer(Modifier.height(12.dp))
                            if (passwordMode) {
                                GlassTextField(failLimitText,
                                    { failLimitText = it.filter { c -> c.isDigit() } },
                                    "密码错误自毁阈值 (0 = 关闭)", "3", isNumber = true)
                                Spacer(Modifier.height(4.dp))
                                Text("连续输错 N 次后自动销毁自身",
                                    color = Color.White.copy(0.4f), fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton("加密", Icons.Filled.Lock, !isWorking, Modifier.weight(1f)) {
                    val maxRuns = maxRunsText.toIntOrNull() ?: 0
                    val failLimit = failLimitText.toIntOrNull() ?: 0
                    when {
                        inputPath.isBlank() -> result = "❌ 请输入源文件路径"
                        passwordMode && password.length < 8 -> result = "❌ 密码至少 8 位"
                        passwordMode && password != password2 -> result = "❌ 两次密码不一致"
                        else -> scope.launch {
                            isWorking = true; result = "正在派生密钥 (约 3 秒)..."
                            result = crypto.protectShellScript(
                                inputPath, outputPath, password,
                                passwordMode, maxRuns, failLimit)
                            isWorking = false
                        }
                    }
                }
                SecondaryButton("解保护", Icons.Filled.LockOpen, !isWorking, Modifier.weight(1f)) {
                    when {
                        inputPath.isBlank() -> result = "❌ 请输入保护脚本路径"
                        else -> scope.launch {
                            isWorking = true; result = "正在解保护..."
                            result = crypto.unprotectShellScript(inputPath, outputPath, password)
                            isWorking = false
                        }
                    }
                }
            }
        }

        if (result.isNotEmpty()) item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text(result, color = Color.White.copy(0.85f), fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace, lineHeight = 19.sp)
            }
        }
    }
}

@Composable
fun ModeChip(text: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val scale by animateFloatAsState(if (active) 1f else 0.98f, tween(180), label = "m")
    Box(
        modifier = modifier.height(48.dp).scale(scale)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (active)
                    Brush.linearGradient(listOf(Color(0xFF6C63FF), Color(0xFF8B7FFF)))
                else Brush.linearGradient(listOf(
                    Color.White.copy(alpha = 0.06f), Color.White.copy(alpha = 0.06f)))
            )
            .border(0.6.dp,
                if (active) Color(0xFF9D96FF) else Color.White.copy(alpha = 0.12f),
                RoundedCornerShape(14.dp))
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (active) Color.White else Color.White.copy(0.7f),
            fontSize = 13.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
    }
}

// ============================================================
//  设置页
// ============================================================
@Composable
fun SettingsTab() {
    val dollarZero = "${'$'}0"
    val dollarHome = "${'$'}HOME"
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 12.dp)) {

        item {
            GlassCard(Modifier.fillMaxWidth(), accent = true) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp))
                        .background(Brush.linearGradient(listOf(
                            Color(0xFF6C63FF), Color(0xFFFF6584)))),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.VerifiedUser, null, tint = Color.White,
                            modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("世界顶级加密", color = Color.White,
                            fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Spacer(Modifier.height(3.dp))
                        Text("PBKDF2-SHA512 · 2,000,000 轮",
                            color = Color(0xFF9C96FF), fontSize = 11.sp,
                            letterSpacing = 0.8.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        item { GlassCard(Modifier.fillMaxWidth()) {
            Column {
                Text("加密参数", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.height(12.dp))
                InfoRow("文件算法", "AES-256-GCM")
                InfoRow("文件 PRF", "PBKDF2-HMAC-SHA512")
                InfoRow("文件轮数", "2,000,000")
                InfoRow("Shell 算法", "AES-256-CBC")
                InfoRow("Shell PRF", "PBKDF2-HMAC-SHA512")
                InfoRow("Shell 轮数", "2,000,000")
                InfoRow("盐值", "8B(Shell) / 16B(文件)")
                InfoRow("认证标签", "128 bit (GCM)")
                InfoRow("自毁方式", "shred × 3")
            }
        } }

        item { GlassCard(Modifier.fillMaxWidth()) {
            Column {
                Text("安全说明", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.height(10.dp))
                Text("v7 采用 PBKDF2-HMAC-SHA512 派生密钥，轮数提升到 2,000,000。SHA-512 输出 512 位摘要，配合高迭代次数，即使使用 GPU 集群或 ASIC 矿机，爆破 12 位复杂密码也需数千年。",
                    color = Color.White.copy(0.7f), fontSize = 12.sp, lineHeight = 19.sp)
                Spacer(Modifier.height(10.dp))
                Text("密码模式运行时密码通过 stdin 传给 openssl，不出现在进程参数列表。临时文件用 umask 077 创建，权限始终 0600。trap 捕获 EXIT/INT/TERM/HUP 信号，任何非 SIGKILL 退出都会清理临时文件。",
                    color = Color.White.copy(0.7f), fontSize = 12.sp, lineHeight = 19.sp)
                Spacer(Modifier.height(10.dp))
                Text("自毁机制：密码错误 N 次或执行次数超限后，脚本调用 shred -u -n 3 -z 覆盖 3 遍磁盘扇区 + 零填充，再删除。文件系统级取证恢复几乎不可能。状态文件位于 $dollarHome/.ev_state/，用脚本 SHA-256 前 16 位命名。",
                    color = Color.White.copy(0.7f), fontSize = 12.sp, lineHeight = 19.sp)
            }
        } }

        item { GlassCard(Modifier.fillMaxWidth()) {
            Column {
                Text("关于", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.height(8.dp))
                InfoRow("应用", "EncryptVault")
                InfoRow("版本", "7.0.0")
                InfoRow("网络", "零联网")
                InfoRow("数据", "零收集")
                InfoRow("兼容", "Termux / MT / Linux")
                InfoRow("许可", "开源 · MIT")
            }
        } }
    }
}

@Composable
fun InfoRow(k: String, v: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(k, color = Color.White.copy(0.5f), fontSize = 12.sp)
        Spacer(Modifier.weight(1f))
        Text(v, color = Color.White.copy(0.9f), fontSize = 12.sp,
            fontWeight = FontWeight.Medium)
    }
}
