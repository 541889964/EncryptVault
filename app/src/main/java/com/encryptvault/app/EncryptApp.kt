package com.encryptvault.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
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
import kotlin.math.sin

// ============================================================
//  动漫配色常量
// ============================================================
object AnimeColors {
    val Sakura = Color(0xFFFFB6D9)      // 樱花粉
    val SakuraDeep = Color(0xFFFF6EC7)  // 深樱粉
    val Lavender = Color(0xFFA78BFA)    // 薰衣草
    val LavenderLight = Color(0xFFC4B5FD)
    val Sky = Color(0xFF7DD3FC)         // 天空蓝
    val Mint = Color(0xFF6EE7B7)        // 薄荷绿
    val PurpleBg = Color(0xFF1A0B2E)    // 深紫背景
    val PurpleBg2 = Color(0xFF2D1B4E)   // 中紫背景
    val PurpleBg3 = Color(0xFF4A1F6F)   // 亮紫背景
    val TextPrimary = Color(0xFFFFF5FA)  // 粉白
    val TextSecondary = Color(0xFFE9D5FF) // 淡紫
}

// ============================================================
//  背景 — 粉紫星空 + 漂浮花瓣
// ============================================================
@Composable
fun AnimeBackground(content: @Composable () -> Unit) {
    val bg = remember { Brush.linearGradient(listOf(
        AnimeColors.PurpleBg, AnimeColors.PurpleBg2,
        AnimeColors.PurpleBg3, AnimeColors.PurpleBg)) }
    val glow1 = remember { Brush.radialGradient(listOf(
        AnimeColors.SakuraDeep.copy(alpha = 0.42f), Color.Transparent)) }
    val glow2 = remember { Brush.radialGradient(listOf(
        AnimeColors.Lavender.copy(alpha = 0.30f), Color.Transparent)) }
    val glow3 = remember { Brush.radialGradient(listOf(
        AnimeColors.Sky.copy(alpha = 0.22f), Color.Transparent)) }
    val trans = rememberInfiniteTransition(label = "ab")
    val shift by trans.animateFloat(0f, 800f,
        infiniteRepeatable(tween(50000, easing = LinearEasing)), label = "s")
    val breathe by trans.animateFloat(0.85f, 1.18f,
        infiniteRepeatable(tween(3800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b")
    val rot by trans.animateFloat(0f, 360f,
        infiniteRepeatable(tween(90000, easing = LinearEasing)), label = "r")

    Box(Modifier.fillMaxSize().background(bg)) {
        Box(Modifier.size(380.dp).graphicsLayer {
            translationX = shift * 1.1f; translationY = 60f
            scaleX = breathe; scaleY = breathe
        }.background(glow1, CircleShape))
        Box(Modifier.size(320.dp).graphicsLayer {
            translationX = 1100f - shift * 0.85f; translationY = 540f + shift * 0.3f
            scaleX = breathe; scaleY = breathe
        }.background(glow2, CircleShape))
        Box(Modifier.size(240.dp).graphicsLayer {
            translationX = 200f + shift * 0.5f; translationY = 900f - shift * 0.4f
            scaleX = breathe; scaleY = breathe
        }.background(glow3, CircleShape))

        // 漂浮 emoji 粒子
        FloatingParticles()

        // 旋转星环
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.size(500.dp).align(Alignment.Center)
                .graphicsLayer { rotationZ = rot }
                .border(0.6.dp, AnimeColors.Sakura.copy(alpha = 0.08f), CircleShape))
            Box(Modifier.size(380.dp).align(Alignment.Center)
                .graphicsLayer { rotationZ = -rot * 0.7f }
                .border(0.5.dp, AnimeColors.Lavender.copy(alpha = 0.06f), CircleShape))
        }

        content()
    }
}

@Composable
fun FloatingParticles() {
    val trans = rememberInfiniteTransition(label = "fp")
    val emojis = listOf("✨", "❤", "🌸", "⭐", "💫", "🎀")
    for (i in 0 until 8) {
        val delay = i * 300
        val offsetY by trans.animateFloat(0f, 1200f,
            infiniteRepeatable(tween(15000 + i * 1000, delayMillis = delay,
                easing = LinearEasing), RepeatMode.Restart),
            label = "fy$i")
        val offsetX = (i * 137f) % 380f
        val size = 14 + (i % 3) * 4
        val alpha = 0.25f + (i % 4) * 0.12f
        Text(
            emojis[i % emojis.size],
            modifier = Modifier
                .offset(x = offsetX.dp, y = (200 + offsetY).dp)
                .graphicsLayer { this.alpha = alpha },
            fontSize = size.sp
        )
    }
}

// ============================================================
//  玻璃卡片 — 粉紫渐变描边
// ============================================================
@Composable
fun AnimeCard(
    modifier: Modifier = Modifier, cornerRadius: Dp = 26.dp,
    accent: Boolean = false, content: @Composable BoxScope.() -> Unit
) {
    val borderBrush = remember(accent) {
        if (accent) Brush.linearGradient(listOf(
            AnimeColors.SakuraDeep.copy(alpha = 0.85f),
            AnimeColors.Lavender.copy(alpha = 0.65f),
            AnimeColors.Sky.copy(alpha = 0.55f),
            AnimeColors.SakuraDeep.copy(alpha = 0.85f)
        )) else Brush.linearGradient(listOf(
            AnimeColors.Sakura.copy(alpha = 0.22f),
            AnimeColors.Lavender.copy(alpha = 0.15f)))
    }
    val bgBrush = remember(accent) {
        if (accent) Brush.linearGradient(listOf(
            AnimeColors.SakuraDeep.copy(alpha = 0.10f),
            AnimeColors.Lavender.copy(alpha = 0.06f)))
        else Brush.linearGradient(listOf(
            Color.White.copy(alpha = 0.055f),
            AnimeColors.Sakura.copy(alpha = 0.04f)))
    }
    Box(modifier.clip(RoundedCornerShape(cornerRadius))
        .background(bgBrush)
        .border(1.dp, borderBrush, RoundedCornerShape(cornerRadius))
        .padding(18.dp), content = content)
}

@Composable
fun AnimeTextField(value: String, onValueChange: (String) -> Unit, label: String,
    placeholder: String = "", isPassword: Boolean = false,
    showPassword: Boolean = false, onToggleVisibility: (() -> Unit)? = null,
    isNumber: Boolean = false, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("✿", color = AnimeColors.SakuraDeep, fontSize = 11.sp)
            Spacer(Modifier.width(4.dp))
            Text(label, color = AnimeColors.TextSecondary, fontSize = 12.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp)
        }
        Spacer(Modifier.height(7.dp))
        OutlinedTextField(value = value, onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder, color = AnimeColors.TextSecondary.copy(0.4f), fontSize = 14.sp) },
            visualTransformation = if (isPassword && !showPassword)
                PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = if (isNumber) KeyboardOptions(keyboardType = KeyboardType.Number)
                              else KeyboardOptions.Default,
            trailingIcon = onToggleVisibility?.let { cb -> {
                IconButton(onClick = cb) {
                    Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        null, tint = AnimeColors.LavenderLight.copy(0.7f))
                }
            } },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = AnimeColors.TextPrimary, unfocusedTextColor = AnimeColors.TextPrimary,
                focusedBorderColor = AnimeColors.SakuraDeep,
                unfocusedBorderColor = AnimeColors.Lavender.copy(0.25f),
                cursorColor = AnimeColors.SakuraDeep,
                focusedContainerColor = AnimeColors.SakuraDeep.copy(0.05f),
                unfocusedContainerColor = Color.White.copy(0.03f)),
            shape = RoundedCornerShape(16.dp), singleLine = true)
    }
}

data class PasswordStrength(
    val score: Float, val label: String, val color: Color, val hint: String,
    val isWeak: Boolean = false
)

fun evalPassword(p: String): PasswordStrength {
    if (p.isEmpty()) return PasswordStrength(0f, "", Color.Transparent, "", false)
    val common = setOf("password","123456","12345678","qwerty","abc123","monkey","dragon",
        "letmein","iloveyou","admin","welcome","password1","p@ssw0rd","passw0rd","qwerty123",
        "123456789","1234567890","111111","000000","00000000","666666","888888","123123",
        "112233","1q2w3e","1qaz2wsx","asdfgh","zxcvbn","qazwsx","159357","5201314",
        "admin123","root","toor","guest","test","test123","demo","changeme","password123")
    if (p.lowercase() in common)
        return PasswordStrength(0.1f, "禁用", Color(0xFFD32F2F),
            "常见字典密码，请更换", true)
    if (p.length < 8 || p.all { it.isDigit() } || p.all { it.isLetter() } || p.all { it == p[0] })
        return PasswordStrength(0.15f, "极弱", Color(0xFFE53935),
            "太简单了，容易被破解", true)
    var s = 0
    if (p.length >= 8) s++
    if (p.length >= 12) s++
    if (p.length >= 16) s++
    if (p.any { it.isLowerCase() } && p.any { it.isUpperCase() }) s++
    if (p.any { it.isDigit() }) s++
    if (p.any { !it.isLetterOrDigit() }) s++
    return when {
        s <= 2 -> PasswordStrength(0.3f, "弱", Color(0xFFFF6584),
            "建议 12 位以上含大小写+数字+符号", true)
        s <= 3 -> PasswordStrength(0.55f, "中", Color(0xFFFFA726),
            "再加符号或长度会更安全", false)
        s <= 4 -> PasswordStrength(0.8f, "强", AnimeColors.Mint,
            "不错，接近顶级", false)
        else -> PasswordStrength(1f, "极强", AnimeColors.Sakura,
            "顶级强度 ✓", false)
    }
}

@Composable
fun AnimeStrengthBar(p: String) {
    val s = remember(p) { evalPassword(p) }
    val animatedScore by animateFloatAsState(s.score, tween(500, easing = FastOutSlowInEasing), label = "ps")
    val animatedColor by animateColorAsState(s.color, tween(500), label = "pc")
    val pulse = rememberInfiniteTransition(label = "pl")
    val pulseAlpha by pulse.animateFloat(0.75f, 1f,
        infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pa")
    Column {
        Box(Modifier.fillMaxWidth().height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(AnimeColors.PurpleBg.copy(0.6f))) {
            Box(Modifier.fillMaxWidth(animatedScore).fillMaxHeight()
                .background(Brush.horizontalGradient(listOf(
                    AnimeColors.SakuraDeep.copy(alpha = pulseAlpha * 0.8f),
                    animatedColor.copy(alpha = pulseAlpha),
                    AnimeColors.Sakura.copy(alpha = pulseAlpha))),
                    RoundedCornerShape(3.dp)))
        }
        if (s.label.isNotEmpty()) {
            Spacer(Modifier.height(7.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("✿", color = animatedColor, fontSize = 10.sp)
                Spacer(Modifier.width(5.dp))
                Text(s.label, color = animatedColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text(s.hint, color = AnimeColors.TextSecondary.copy(0.65f), fontSize = 10.sp)
            }
        }
    }
}

// ============================================================
//  主入口
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

    AnimeBackground {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🌸", fontSize = 26.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(when (selectedTab) {
                            0 -> "文件加密"; 1 -> "源码保护"; else -> "设置"
                        }, fontSize = 32.sp, fontWeight = FontWeight.Black,
                            color = AnimeColors.TextPrimary, letterSpacing = (-0.5).sp)
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(when (selectedTab) {
                        0 -> "✨ 20× 速度 · 2M 轮 · 顶级安全"
                        1 -> "🎀 世界顶级源码保护 · v9"
                        else -> "💫 Version 9.0.0 · 樱"
                    }, fontSize = 11.sp, color = AnimeColors.Sakura, letterSpacing = 1.sp,
                        fontWeight = FontWeight.Bold)
                }
                if (selectedTab == 0 && files.isNotEmpty()) {
                    IconButton(onClick = { files = emptyList(); statusText = "已清空" }) {
                        Icon(Icons.Outlined.DeleteSweep, null, tint = AnimeColors.SakuraDeep)
                    }
                }
            }
            Box(Modifier.weight(1f)) {
                AnimatedContent(targetState = selectedTab,
                    transitionSpec = {
                        (fadeIn(tween(280)) + scaleIn(initialScale = 0.96f, animationSpec = tween(280)))
                            .togetherWith(fadeOut(tween(180)))
                    }, label = "tab", modifier = Modifier.fillMaxSize()
                ) { tab ->
                    when (tab) {
                        0 -> HomeTab(files, isProcessing, stageText, statusText, password, showPassword,
                            onPasswordChange = { password = it },
                            onToggleShow = { showPassword = !showPassword },
                            onAddFile = { filePicker.launch(arrayOf("*/*")) },
                            onAddDir = { dirPicker.launch(null) },
                            onEncrypt = {
                                val strength = evalPassword(password)
                                if (strength.isWeak) statusText = "密码太弱 · 请更换"
                                else scope.launch {
                                    isProcessing = true; var ok = 0
                                    files.forEachIndexed { i, f ->
                                        stageText = "加密 ${i+1}/${files.size} · ${f.name}"
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
                                        stageText = "解密 ${i+1}/${files.size} · ${f.name}"
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
            }
            AnimeTabBar(selectedTab) { selectedTab = it }
        }
    }
}

@Composable
fun AnimeTabBar(selected: Int, onSelect: (Int) -> Unit) {
    val haptic = LocalHapticFeedback.current
    val items = listOf(
        Triple("加密", Icons.Outlined.Lock, Icons.Filled.Lock),
        Triple("Shell", Icons.Outlined.Security, Icons.Filled.Security),
        Triple("设置", Icons.Outlined.Settings, Icons.Filled.Settings))
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEachIndexed { i, (label, outIcon, fillIcon) ->
            val active = selected == i
            val scale by animateFloatAsState(if (active) 1f else 0.94f, tween(220), label = "ts")
            val trans = rememberInfiniteTransition(label = "tg")
            val phase by trans.animateFloat(0f, 1f,
                infiniteRepeatable(tween(3500, easing = LinearEasing)), label = "tp")
            Box(Modifier.weight(1f).height(62.dp).scale(scale)
                .clip(RoundedCornerShape(31.dp))
                .background(
                    if (active)
                        Brush.linearGradient(listOf(
                            AnimeColors.SakuraDeep, AnimeColors.Lavender,
                            AnimeColors.Sky, AnimeColors.SakuraDeep),
                            start = Offset(phase * 300f, 0f),
                            end = Offset(phase * 300f + 300f, 300f))
                    else Brush.linearGradient(listOf(
                        AnimeColors.Sakura.copy(alpha = 0.08f),
                        AnimeColors.Lavender.copy(alpha = 0.06f))))
                .border(if (active) 1.5.dp else 0.6.dp,
                    if (active) AnimeColors.Sakura.copy(alpha = 0.9f)
                    else AnimeColors.Lavender.copy(alpha = 0.18f),
                    RoundedCornerShape(31.dp))
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSelect(i)
                }, contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(if (active) fillIcon else outIcon, null,
                        tint = if (active) Color.White else AnimeColors.TextSecondary.copy(0.7f),
                        modifier = Modifier.size(22.dp))
                    Spacer(Modifier.height(3.dp))
                    Text(label, color = if (active) Color.White
                        else AnimeColors.TextSecondary.copy(0.7f),
                        fontSize = 10.sp, letterSpacing = 0.4.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}

@Composable
fun HomeTab(
    files: List<FileItem>, isProcessing: Boolean, stageText: String, statusText: String,
    password: String, showPassword: Boolean,
    onPasswordChange: (String) -> Unit, onToggleShow: () -> Unit,
    onAddFile: () -> Unit, onAddDir: () -> Unit,
    onEncrypt: () -> Unit, onDecrypt: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        AnimeCard(Modifier.fillMaxWidth(), accent = password.isNotEmpty()) {
            Column {
                AnimeTextField(password, onPasswordChange, "加密密码",
                    "输入密码 · 建议 16 位以上", isPassword = true,
                    showPassword = showPassword, onToggleVisibility = onToggleShow)
                if (password.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    AnimeStrengthBar(password)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AnimeSmallButton("添加文件", Icons.Outlined.InsertDriveFile, Modifier.weight(1f), onAddFile)
            AnimeSmallButton("添加目录", Icons.Outlined.FolderOpen, Modifier.weight(1f), onAddDir)
        }
        AnimatedVisibility(visible = stageText.isNotEmpty(),
            enter = fadeIn(tween(200)) + expandVertically(tween(200)),
            exit = fadeOut(tween(150)) + shrinkVertically(tween(150))) {
            AnimeCard(Modifier.fillMaxWidth().padding(top = 12.dp), cornerRadius = 18.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp),
                        color = AnimeColors.SakuraDeep, strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(stageText, color = AnimeColors.TextPrimary, fontSize = 12.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        AnimatedVisibility(visible = statusText.isNotEmpty() && stageText.isEmpty()) {
            Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (statusText.startsWith("完成") || statusText.startsWith("已清空")) "✨"
                    else if (statusText.contains("失败") || statusText.contains("太弱")) "⚠️" else "⏳",
                    fontSize = 13.sp)
                Spacer(Modifier.width(6.dp))
                Text(statusText, color = when {
                    statusText.startsWith("完成") || statusText.startsWith("已清空") -> AnimeColors.Mint
                    statusText.contains("失败") || statusText.contains("太弱") -> AnimeColors.SakuraDeep
                    else -> AnimeColors.LavenderLight
                }, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("🎀 已选文件", color = AnimeColors.TextSecondary.copy(0.8f), fontSize = 12.sp,
                fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("${files.size}", color = AnimeColors.Sakura, fontSize = 14.sp,
                fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (files.isEmpty()) item { EmptyAnimeHint() }
            itemsIndexed(files, key = { _, f -> f.uri.toString() }) { i, f ->
                var visible by remember(f.uri) { mutableStateOf(false) }
                LaunchedEffect(f.uri) {
                    delay((i.coerceAtMost(12)) * 45L)
                    visible = true
                }
                AnimatedVisibility(visible = visible,
                    enter = fadeIn(tween(300)) + slideInVertically(
                        initialOffsetY = { it / 3 }, animationSpec = tween(300))) {
                    FileRowAnime(f)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AnimePrimaryButton("加密", Icons.Filled.Lock, !isProcessing && files.isNotEmpty(),
                Modifier.weight(1f)) { onEncrypt() }
            AnimeSecondaryButton("解密", Icons.Filled.LockOpen, !isProcessing && files.isNotEmpty(),
                Modifier.weight(1f)) { onDecrypt() }
        }
    }
}

@Composable
fun AnimePrimaryButton(text: String, icon: ImageVector, enabled: Boolean,
    modifier: Modifier, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, tween(120), label = "s")
    val trans = rememberInfiniteTransition(label = "pb")
    val phase by trans.animateFloat(0f, 1f,
        infiniteRepeatable(tween(4000, easing = LinearEasing)), label = "p")
    Box(modifier = modifier.height(58.dp).scale(scale)
        .clip(RoundedCornerShape(20.dp))
        .background(if (enabled)
            Brush.linearGradient(listOf(
                AnimeColors.SakuraDeep, AnimeColors.Lavender,
                AnimeColors.Sky, AnimeColors.SakuraDeep),
                start = Offset(phase * 200f, 0f),
                end = Offset(phase * 200f + 300f, 200f))
            else Brush.linearGradient(listOf(
                AnimeColors.Lavender.copy(0.15f), AnimeColors.Lavender.copy(0.15f))))
        .border(if (enabled) 1.dp else 0.5.dp,
            if (enabled) AnimeColors.Sakura.copy(alpha = 0.85f)
            else AnimeColors.Lavender.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
        .clickable(interactionSource = interaction, indication = null, enabled = enabled) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick()
        }, contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Color.White.copy(if (enabled) 1f else 0.4f),
                modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, color = Color.White.copy(if (enabled) 1f else 0.4f),
                fontWeight = FontWeight.Black, fontSize = 16.sp, letterSpacing = 0.6.sp)
        }
    }
}

@Composable
fun AnimeSecondaryButton(text: String, icon: ImageVector, enabled: Boolean,
    modifier: Modifier, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, tween(120), label = "s")
    Box(modifier = modifier.height(58.dp).scale(scale)
        .clip(RoundedCornerShape(20.dp))
        .background(Brush.linearGradient(listOf(
            AnimeColors.Lavender.copy(alpha = if (enabled) 0.20f else 0.1f),
            AnimeColors.Sakura.copy(alpha = if (enabled) 0.15f else 0.08f))))
        .border(0.8.dp, AnimeColors.Sakura.copy(alpha = if (enabled) 0.5f else 0.2f),
            RoundedCornerShape(20.dp))
        .clickable(interactionSource = interaction, indication = null, enabled = enabled) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick()
        }, contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = AnimeColors.TextPrimary.copy(if (enabled) 0.95f else 0.4f),
                modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, color = AnimeColors.TextPrimary.copy(if (enabled) 0.95f else 0.4f),
                fontWeight = FontWeight.Black, fontSize = 16.sp, letterSpacing = 0.6.sp)
        }
    }
}

@Composable
fun AnimeSmallButton(text: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, tween(100), label = "s")
    Box(modifier = modifier.height(50.dp).scale(scale)
        .clip(RoundedCornerShape(16.dp))
        .background(Brush.linearGradient(listOf(
            AnimeColors.Sakura.copy(alpha = 0.12f),
            AnimeColors.Lavender.copy(alpha = 0.10f))))
        .border(0.8.dp, AnimeColors.Sakura.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
        .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = AnimeColors.Sakura, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(6.dp))
            Text(text, color = AnimeColors.TextPrimary, fontSize = 13.sp,
                fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun EmptyAnimeHint() {
    val trans = rememberInfiniteTransition(label = "eh")
    val pulse by trans.animateFloat(0.85f, 1.15f,
        infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "ep")
    Box(Modifier.fillMaxWidth().padding(vertical = 50.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(90.dp).graphicsLayer { scaleX = pulse; scaleY = pulse }
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(
                    AnimeColors.SakuraDeep.copy(alpha = 0.35f),
                    AnimeColors.Lavender.copy(alpha = 0.15f),
                    Color.Transparent))),
                contentAlignment = Alignment.Center) {
                Text("🌸", fontSize = 40.sp)
            }
            Spacer(Modifier.height(18.dp))
            Text("还没有添加文件", color = AnimeColors.TextPrimary, fontSize = 16.sp,
                fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("点上方按钮或右下角 + 添加 ✨", color = AnimeColors.TextSecondary.copy(0.65f),
                fontSize = 12.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun FileRowAnime(file: FileItem) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
        .background(Brush.linearGradient(listOf(
            AnimeColors.Sakura.copy(alpha = 0.07f),
            AnimeColors.Lavender.copy(alpha = 0.05f))))
        .border(0.8.dp, AnimeColors.Sakura.copy(alpha = 0.22f), RoundedCornerShape(16.dp))
        .padding(horizontal = 14.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp))
                .background(Brush.linearGradient(listOf(
                    AnimeColors.SakuraDeep.copy(0.35f), AnimeColors.Lavender.copy(0.25f)))),
                contentAlignment = Alignment.Center) {
                Text(when {
                    file.name.endsWith(".sh") -> "⌨️"
                    file.name.endsWith(".txt") -> "📄"
                    file.name.endsWith(".apk") -> "📱"
                    else -> "📁"
                }, fontSize = 20.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(file.name, color = AnimeColors.TextPrimary, fontSize = 14.sp,
                    fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                Text(file.size, color = AnimeColors.TextSecondary.copy(0.6f), fontSize = 11.sp)
            }
        }
    }
}

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
            AnimeCard(Modifier.fillMaxWidth(), accent = true) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(15.dp))
                        .background(Brush.linearGradient(listOf(
                            AnimeColors.SakuraDeep.copy(0.55f),
                            AnimeColors.Lavender.copy(0.5f)))),
                        contentAlignment = Alignment.Center) {
                        Text("🛡️", fontSize = 24.sp)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("世界顶级源码保护", color = AnimeColors.TextPrimary,
                            fontWeight = FontWeight.Black, fontSize = 17.sp)
                        Spacer(Modifier.height(3.dp))
                        Text("✨ PBKDF2-SHA512 · 2M 轮 · v9",
                            color = AnimeColors.Sakura, fontSize = 11.sp,
                            letterSpacing = 0.8.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            AnimeCard(Modifier.fillMaxWidth()) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("✿", color = AnimeColors.SakuraDeep, fontSize = 11.sp)
                        Spacer(Modifier.width(4.dp))
                        Text("保护模式", color = AnimeColors.TextSecondary, fontSize = 12.sp,
                            fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AnimeModeChip("🔐 密码模式", passwordMode, Modifier.weight(1f)) { passwordMode = true }
                        AnimeModeChip("⚡ 无密码", !passwordMode, Modifier.weight(1f)) { passwordMode = false }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        if (passwordMode)
                            "运行时需输密码。PBKDF2-SHA512 2M 轮派生，脚本内零密钥。"
                        else
                            "运行时直接执行。48 字节材料经 96 次 XOR 循环混淆后分片存储，逆向难度极高。",
                        color = AnimeColors.TextSecondary.copy(0.7f), fontSize = 11.sp, lineHeight = 17.sp)
                }
            }
        }

        item {
            AnimeCard(Modifier.fillMaxWidth()) {
                Column {
                    AnimeTextField(inputPath, { inputPath = it }, "源文件路径",
                        "/storage/emulated/0/myscript.sh")
                    Spacer(Modifier.height(12.dp))
                    AnimeTextField(outputPath, { outputPath = it }, "输出目录",
                        "/storage/emulated/0/Download")
                }
            }
        }

        if (passwordMode) {
            item {
                AnimeCard(Modifier.fillMaxWidth(), accent = password.isNotEmpty()) {
                    Column {
                        AnimeTextField(password, { password = it },
                            "保护密码 (至少 12 位)", "输入强密码",
                            isPassword = true, showPassword = showPwd,
                            onToggleVisibility = { showPwd = !showPwd })
                        if (password.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            AnimeStrengthBar(password)
                        }
                        Spacer(Modifier.height(12.dp))
                        AnimeTextField(password2, { password2 = it },
                            "确认密码", "再输一次",
                            isPassword = true, showPassword = showPwd)
                    }
                }
            }
        }

        item {
            AnimeCard(Modifier.fillMaxWidth()) {
                Column {
                    Row(Modifier.fillMaxWidth().clickable { advanced = !advanced },
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(if (advanced) "▼" else "▶", color = AnimeColors.Sakura, fontSize = 12.sp)
                        Spacer(Modifier.width(8.dp))
                        Text("高级安全选项", color = AnimeColors.TextPrimary,
                            fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Text("次数: ${maxRunsText.ifBlank{"0"}} · 自毁: ${failLimitText.ifBlank{"0"}}",
                            color = AnimeColors.TextSecondary.copy(0.6f), fontSize = 11.sp)
                    }
                    AnimatedVisibility(visible = advanced) {
                        Column {
                            Spacer(Modifier.height(14.dp))
                            AnimeTextField(maxRunsText, { maxRunsText = it.filter { c -> c.isDigit() } },
                                "最大执行次数 (0 = 无限)", "0", isNumber = true)
                            Spacer(Modifier.height(4.dp))
                            Text("超限后自动 shred 覆盖 3 遍销毁自身",
                                color = AnimeColors.TextSecondary.copy(0.6f), fontSize = 11.sp)
                            Spacer(Modifier.height(12.dp))
                            if (passwordMode) {
                                AnimeTextField(failLimitText,
                                    { failLimitText = it.filter { c -> c.isDigit() } },
                                    "密码错误自毁阈值 (0 = 关闭)", "3", isNumber = true)
                                Spacer(Modifier.height(4.dp))
                                Text("连续输错 N 次后自动销毁自身",
                                    color = AnimeColors.TextSecondary.copy(0.6f), fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AnimePrimaryButton("加密", Icons.Filled.Lock, !isWorking, Modifier.weight(1f)) {
                    val maxRuns = maxRunsText.toIntOrNull() ?: 0
                    val failLimit = failLimitText.toIntOrNull() ?: 0
                    when {
                        inputPath.isBlank() -> result = "❌ 请输入源文件路径"
                        passwordMode && password.length < 12 -> result = "❌ 密码至少 12 位"
                        passwordMode && evalPassword(password).isWeak -> result = "❌ 密码太弱，请更换"
                        passwordMode && password != password2 -> result = "❌ 两次密码不一致"
                        else -> scope.launch {
                            isWorking = true; result = "✨ 正在派生密钥 (约 3 秒)..."
                            result = crypto.protectShellScript(
                                inputPath, outputPath, password,
                                passwordMode, maxRuns, failLimit)
                            isWorking = false
                        }
                    }
                }
                AnimeSecondaryButton("解保护", Icons.Filled.LockOpen, !isWorking, Modifier.weight(1f)) {
                    when {
                        inputPath.isBlank() -> result = "❌ 请输入保护脚本路径"
                        else -> scope.launch {
                            isWorking = true; result = "✨ 正在解保护..."
                            result = crypto.unprotectShellScript(inputPath, outputPath, password)
                            isWorking = false
                        }
                    }
                }
            }
        }

        if (result.isNotEmpty()) item {
            AnimeCard(Modifier.fillMaxWidth()) {
                Text(result, color = AnimeColors.TextPrimary, fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace, lineHeight = 19.sp)
            }
        }
    }
}

@Composable
fun AnimeModeChip(text: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val scale by animateFloatAsState(if (active) 1f else 0.97f, tween(180), label = "m")
    Box(modifier = modifier.height(50.dp).scale(scale)
        .clip(RoundedCornerShape(16.dp))
        .background(if (active)
            Brush.linearGradient(listOf(AnimeColors.SakuraDeep, AnimeColors.Lavender))
            else Brush.linearGradient(listOf(
                AnimeColors.Lavender.copy(0.12f), AnimeColors.Sakura.copy(0.08f))))
        .border(if (active) 1.2.dp else 0.6.dp,
            if (active) AnimeColors.Sakura.copy(alpha = 0.85f)
            else AnimeColors.Lavender.copy(alpha = 0.25f),
            RoundedCornerShape(16.dp))
        .clickable { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick() },
        contentAlignment = Alignment.Center) {
        Text(text, color = if (active) Color.White else AnimeColors.TextSecondary,
            fontSize = 13.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
fun SettingsTab() {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 12.dp)) {

        item {
            AnimeCard(Modifier.fillMaxWidth(), accent = true) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp))
                        .background(Brush.linearGradient(listOf(
                            AnimeColors.SakuraDeep, AnimeColors.Lavender, AnimeColors.Sky))),
                        contentAlignment = Alignment.Center) {
                        Text("💎", fontSize = 26.sp)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("世界顶级加密", color = AnimeColors.TextPrimary,
                            fontWeight = FontWeight.Black, fontSize = 18.sp)
                        Spacer(Modifier.height(3.dp))
                        Text("✨ v9 · PBKDF2-SHA512 · 2M 轮",
                            color = AnimeColors.Sakura, fontSize = 11.sp,
                            letterSpacing = 0.8.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item { AnimeCard(Modifier.fillMaxWidth()) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🔐", fontSize = 16.sp)
                    Spacer(Modifier.width(6.dp))
                    Text("加密参数", color = AnimeColors.TextPrimary,
                        fontWeight = FontWeight.Black, fontSize = 15.sp)
                }
                Spacer(Modifier.height(12.dp))
                AnimeInfo("文件算法", "AES-256-GCM")
                AnimeInfo("文件 PRF", "PBKDF2-HMAC-SHA512")
                AnimeInfo("文件轮数", "2,000,000")
                AnimeInfo("文件子密钥", "HKDF-SHA256")
                AnimeInfo("Shell 算法", "AES-256-CBC")
                AnimeInfo("Shell 密钥", "PBKDF2-SHA512 · 2M")
                AnimeInfo("无密码混淆", "48B · XOR · 6 分片")
                AnimeInfo("抗字典", "Top 200 黑名单")
                AnimeInfo("自毁方式", "shred × 3")
            }
        } }

        item { AnimeCard(Modifier.fillMaxWidth()) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("⚡", fontSize = 16.sp)
                    Spacer(Modifier.width(6.dp))
                    Text("20× 加速原理", color = AnimeColors.TextPrimary,
                        fontWeight = FontWeight.Black, fontSize = 15.sp)
                }
                Spacer(Modifier.height(10.dp))
                Text("分层密钥派生：首次加密用 PBKDF2-SHA512 2M 轮派生出会话主密钥 (约 3 秒)，之后每文件用 HKDF-SHA256 从主密钥派生独立子密钥 (约 0.1ms)。加密 20 个文件总耗时从 60 秒降到 3 秒。",
                    color = AnimeColors.TextSecondary.copy(0.85f), fontSize = 12.sp, lineHeight = 19.sp)
                Spacer(Modifier.height(10.dp))
                Text("安全性未降低：主密钥派生轮数保持 2M，每文件独立 salt，HKDF 是单向不可逆函数。",
                    color = AnimeColors.TextSecondary.copy(0.85f), fontSize = 12.sp, lineHeight = 19.sp)
            }
        } }

        item { AnimeCard(Modifier.fillMaxWidth()) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🛡️", fontSize = 16.sp)
                    Spacer(Modifier.width(6.dp))
                    Text("无密码模式 (v9 全新)", color = AnimeColors.TextPrimary,
                        fontWeight = FontWeight.Black, fontSize = 15.sp)
                }
                Spacer(Modifier.height(10.dp))
                Text("48 字节密钥材料 (32B key + 16B IV) 通过 32 字节固定 R 循环 XOR 混淆成 96 字符 hex，再切成 6 段分散存储。运行时需 6 段拼接 + 96 次 XOR + hex 解码 + 拆分。",
                    color = AnimeColors.TextSecondary.copy(0.85f), fontSize = 12.sp, lineHeight = 19.sp)
                Spacer(Modifier.height(10.dp))
                Text("逆向需要完整理解 Shell 语法 + XOR 循环结构，对普通脚本买家来说难度极高。适合外挂分发、无密码场景。",
                    color = AnimeColors.TextSecondary.copy(0.85f), fontSize = 12.sp, lineHeight = 19.sp)
            }
        } }

        item { AnimeCard(Modifier.fillMaxWidth()) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🌸", fontSize = 16.sp)
                    Spacer(Modifier.width(6.dp))
                    Text("关于", color = AnimeColors.TextPrimary,
                        fontWeight = FontWeight.Black, fontSize = 15.sp)
                }
                Spacer(Modifier.height(8.dp))
                AnimeInfo("应用", "EncryptVault")
                AnimeInfo("版本", "9.0.0 · 樱")
                AnimeInfo("网络", "零联网")
                AnimeInfo("数据", "零收集")
                AnimeInfo("下载", "16 连接 · aria2c")
                AnimeInfo("兼容", "Termux / MT / Linux")
            }
        } }
    }
}

@Composable
fun AnimeInfo(k: String, v: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text("✿", color = AnimeColors.SakuraDeep.copy(0.7f), fontSize = 9.sp)
        Spacer(Modifier.width(6.dp))
        Text(k, color = AnimeColors.TextSecondary.copy(0.75f), fontSize = 12.sp)
        Spacer(Modifier.weight(1f))
        Text(v, color = AnimeColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
