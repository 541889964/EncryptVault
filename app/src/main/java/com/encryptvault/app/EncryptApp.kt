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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.encryptvault.app.crypto.CryptoManager
import com.encryptvault.app.crypto.FileItem
import kotlinx.coroutines.*

object AnimeColors {
    val Sakura = Color(0xFFFFB6D9)
    val SakuraDeep = Color(0xFFFF6EC7)
    val Lavender = Color(0xFFA78BFA)
    val LavenderLight = Color(0xFFC4B5FD)
    val Sky = Color(0xFF7DD3FC)
    val Mint = Color(0xFF6EE7B7)
    val PurpleBg = Color(0xFF1A0B2E)
    val PurpleBg2 = Color(0xFF2D1B4E)
    val PurpleBg3 = Color(0xFF4A1F6F)
    val TextPrimary = Color(0xFFFFF5FA)
    val TextSecondary = Color(0xFFE9D5FF)
    val Danger = Color(0xFFFF5252)
}

// 静态 Brush 缓存 (避免每帧重建)
private val BG_BRUSH = Brush.linearGradient(listOf(
    AnimeColors.PurpleBg, AnimeColors.PurpleBg2,
    AnimeColors.PurpleBg3, AnimeColors.PurpleBg))
private val GLOW1 = Brush.radialGradient(listOf(
    AnimeColors.SakuraDeep.copy(0.42f), Color.Transparent))
private val GLOW2 = Brush.radialGradient(listOf(
    AnimeColors.Lavender.copy(0.30f), Color.Transparent))

@Composable
fun AnimeBackground(content: @Composable () -> Unit) {
    // 单个动画 State，两处复用
    val trans = rememberInfiniteTransition(label = "ab")
    val shift by trans.animateFloat(0f, 800f,
        infiniteRepeatable(tween(45000, easing = LinearEasing)), label = "s")
    val breathe by trans.animateFloat(0.9f, 1.12f,
        infiniteRepeatable(tween(3800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b")

    Box(Modifier.fillMaxSize().background(BG_BRUSH)) {
        // 光斑 1 — graphicsLayer 跳过重组, 只触发绘制阶段
        Box(Modifier.size(380.dp).graphicsLayer {
            translationX = shift * 1.1f
            translationY = 60f
            scaleX = breathe; scaleY = breathe
        }.background(GLOW1, CircleShape))

        // 光斑 2
        Box(Modifier.size(320.dp).graphicsLayer {
            translationX = 1100f - shift * 0.85f
            translationY = 540f + shift * 0.3f
            scaleX = breathe; scaleY = breathe
        }.background(GLOW2, CircleShape))

        content()
    }
}

@Composable
fun AnimeCard(modifier: Modifier = Modifier, cornerRadius: Dp = 26.dp,
    accent: Boolean = false, content: @Composable BoxScope.() -> Unit) {
    // Brush 用 remember 缓存, 只在 accent 变化时重建
    val border = remember(accent) {
        if (accent) Brush.linearGradient(listOf(
            AnimeColors.SakuraDeep.copy(0.85f), AnimeColors.Lavender.copy(0.65f),
            AnimeColors.Sky.copy(0.55f), AnimeColors.SakuraDeep.copy(0.85f)))
        else Brush.linearGradient(listOf(
            AnimeColors.Sakura.copy(0.22f), AnimeColors.Lavender.copy(0.15f)))
    }
    val bgB = remember(accent) {
        if (accent) Brush.linearGradient(listOf(
            AnimeColors.SakuraDeep.copy(0.10f), AnimeColors.Lavender.copy(0.06f)))
        else Brush.linearGradient(listOf(
            Color.White.copy(0.055f), AnimeColors.Sakura.copy(0.04f)))
    }
    Box(modifier.clip(RoundedCornerShape(cornerRadius))
        .background(bgB).border(1.dp, border, RoundedCornerShape(cornerRadius))
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
                fontWeight = FontWeight.Bold)
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
                focusedTextColor = AnimeColors.TextPrimary,
                unfocusedTextColor = AnimeColors.TextPrimary,
                focusedBorderColor = AnimeColors.SakuraDeep,
                unfocusedBorderColor = AnimeColors.Lavender.copy(0.25f),
                cursorColor = AnimeColors.SakuraDeep,
                focusedContainerColor = AnimeColors.SakuraDeep.copy(0.05f),
                unfocusedContainerColor = Color.White.copy(0.03f)),
            shape = RoundedCornerShape(16.dp), singleLine = true)
    }
}

data class PasswordStrength(val score: Float, val label: String, val color: Color,
    val hint: String, val isWeak: Boolean = false)

fun evalPassword(p: String): PasswordStrength {
    if (p.isEmpty()) return PasswordStrength(0f, "", Color.Transparent, "", false)
    val common = setOf("password","123456","12345678","qwerty","abc123","monkey","dragon",
        "letmein","iloveyou","admin","welcome","password1","p@ssw0rd","passw0rd","qwerty123",
        "123456789","1234567890","111111","000000","00000000","666666","888888","123123",
        "112233","1q2w3e","1qaz2wsx","asdfgh","zxcvbn","qazwsx","159357","5201314")
    if (p.lowercase() in common) return PasswordStrength(0.1f, "禁用", Color(0xFFD32F2F),
        "常见字典密码", true)
    if (p.length < 8 || p.all { it.isDigit() } || p.all { it.isLetter() } || p.all { it == p[0] })
        return PasswordStrength(0.15f, "极弱", Color(0xFFE53935), "太简单", true)
    var s = 0
    if (p.length >= 8) s++
    if (p.length >= 12) s++
    if (p.length >= 16) s++
    if (p.any { it.isLowerCase() } && p.any { it.isUpperCase() }) s++
    if (p.any { it.isDigit() }) s++
    if (p.any { !it.isLetterOrDigit() }) s++
    return when {
        s <= 2 -> PasswordStrength(0.3f, "弱", Color(0xFFFF6584), "建议 12 位以上", true)
        s <= 3 -> PasswordStrength(0.55f, "中", Color(0xFFFFA726), "加符号更安全", false)
        s <= 4 -> PasswordStrength(0.8f, "强", AnimeColors.Mint, "接近顶级", false)
        else -> PasswordStrength(1f, "极强", AnimeColors.Sakura, "顶级强度 ✓", false)
    }
}

@Composable
fun AnimeStrengthBar(p: String) {
    // 只在 p 变化时重新计算
    val s by remember(p) { mutableStateOf(evalPassword(p)) }
    val score by animateFloatAsState(s.score, tween(400), label = "ps")
    val color by animateColorAsState(s.color, tween(400), label = "pc")
    Column {
        Box(Modifier.fillMaxWidth().height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(AnimeColors.PurpleBg.copy(0.6f))) {
            Box(Modifier.fillMaxWidth(score).fillMaxHeight()
                .background(Brush.horizontalGradient(listOf(
                    AnimeColors.SakuraDeep, color, AnimeColors.Sakura)),
                    RoundedCornerShape(3.dp)))
        }
        if (s.label.isNotEmpty()) {
            Spacer(Modifier.height(7.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("✿", color = color, fontSize = 10.sp)
                Spacer(Modifier.width(5.dp))
                Text(s.label, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text(s.hint, color = AnimeColors.TextSecondary.copy(0.65f), fontSize = 10.sp)
            }
        }
    }
}

@Composable
fun EncryptApp(securityReport: SecurityChecker.Report? = null) {
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

    // derivedStateOf 派生 (避免直接依赖 files.size 的重组)
    val fileCount by remember { derivedStateOf { files.size } }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) files = files + uris.mapNotNull { crypto.getFileInfo(it) }
    }
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
                    Text("✨ v12 · 反逆向 + 反爬虫板",
                        fontSize = 11.sp, color = AnimeColors.Sakura,
                        letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
                }
                if (selectedTab == 0 && fileCount > 0) {
                    IconButton(onClick = { files = emptyList(); statusText = "已清空" }) {
                        Icon(Icons.Outlined.DeleteSweep, null, tint = AnimeColors.SakuraDeep)
                    }
                }
            }
            Box(Modifier.weight(1f)) {
                // AnimatedContent 用 key 分隔 tab
                AnimatedContent(targetState = selectedTab,
                    transitionSpec = {
                        (fadeIn(tween(220)) + scaleIn(initialScale = 0.97f, animationSpec = tween(220)))
                            .togetherWith(fadeOut(tween(150)))
                    }, label = "tab", modifier = Modifier.fillMaxSize()
                ) { tab ->
                    when (tab) {
                        0 -> HomeTab(files, isProcessing, stageText, statusText, password, showPassword,
                            onPwdChange = { password = it },
                            onToggleShow = { showPassword = !showPassword },
                            onAddFile = { filePicker.launch(arrayOf("*/*")) },
                            onAddDir = { dirPicker.launch(null) },
                            onEncrypt = {
                                val st = evalPassword(password)
                                if (st.isWeak) statusText = "密码太弱 · 请更换"
                                else scope.launch {
                                    isProcessing = true; var ok = 0
                                    files.forEachIndexed { i, f ->
                                        stageText = "加密 ${i+1}/$fileCount · ${f.name}"
                                        if (crypto.encryptFile(f.uri, password)) ok++
                                    }
                                    stageText = ""
                                    statusText = "完成 · $ok/$fileCount 个文件已加密"
                                    isProcessing = false
                                }
                            },
                            onDecrypt = {
                                if (password.isBlank()) statusText = "请输入密码"
                                else scope.launch {
                                    isProcessing = true; var ok = 0
                                    files.forEachIndexed { i, f ->
                                        stageText = "解密 ${i+1}/$fileCount · ${f.name}"
                                        if (crypto.decryptFile(f.uri, password)) ok++
                                    }
                                    stageText = ""
                                    statusText = if (ok == 0) "解密失败 · 密码错误或文件损坏"
                                                 else "完成 · $ok/$fileCount 个文件已解密"
                                    isProcessing = false
                                }
                            })
                        1 -> ShellTab(crypto)
                        2 -> SettingsTab(securityReport ?: SecurityChecker.check(ctx))
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
    // items 用 remember 缓存
    val items = remember {
        listOf(
            Triple("加密", Icons.Outlined.Lock, Icons.Filled.Lock),
            Triple("Shell", Icons.Outlined.Security, Icons.Filled.Security),
            Triple("设置", Icons.Outlined.Settings, Icons.Filled.Settings))
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEachIndexed { i, (label, outIcon, fillIcon) ->
            val active = selected == i
            val scale by animateFloatAsState(if (active) 1f else 0.95f,
                tween(200, easing = FastOutSlowInEasing), label = "ts")
            // 渐变色相用 derived 静态, 避免每帧重组
            val bg = if (active) {
                Brush.linearGradient(listOf(
                    AnimeColors.SakuraDeep, AnimeColors.Lavender, AnimeColors.Sky))
            } else {
                Brush.linearGradient(listOf(
                    AnimeColors.Sakura.copy(0.08f), AnimeColors.Lavender.copy(0.06f)))
            }
            Box(Modifier.weight(1f).height(62.dp).scale(scale)
                .clip(RoundedCornerShape(31.dp))
                .background(bg)
                .border(if (active) 1.5.dp else 0.6.dp,
                    if (active) AnimeColors.Sakura.copy(0.9f)
                    else AnimeColors.Lavender.copy(0.18f),
                    RoundedCornerShape(31.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
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
                        fontSize = 10.sp,
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
    onPwdChange: (String) -> Unit, onToggleShow: () -> Unit,
    onAddFile: () -> Unit, onAddDir: () -> Unit,
    onEncrypt: () -> Unit, onDecrypt: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        AnimeCard(Modifier.fillMaxWidth(), accent = password.isNotEmpty()) {
            Column {
                AnimeTextField(password, onPwdChange, "加密密码",
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
            AnimeSmallBtn("添加文件", Icons.Outlined.InsertDriveFile, Modifier.weight(1f), onAddFile)
            AnimeSmallBtn("添加目录", Icons.Outlined.FolderOpen, Modifier.weight(1f), onAddDir)
        }
        AnimatedVisibility(visible = stageText.isNotEmpty(),
            enter = fadeIn(tween(160)) + expandVertically(tween(160)),
            exit = fadeOut(tween(120)) + shrinkVertically(tween(120))) {
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
            Text(statusText, color = when {
                statusText.startsWith("完成") || statusText.startsWith("已清空") -> AnimeColors.Mint
                statusText.contains("失败") || statusText.contains("太弱") -> AnimeColors.SakuraDeep
                else -> AnimeColors.LavenderLight
            }, fontSize = 13.sp, modifier = Modifier.padding(vertical = 10.dp))
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
            if (files.isEmpty()) item(key = "empty", contentType = "empty") {
                Box(Modifier.fillMaxWidth().padding(vertical = 50.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🌸", fontSize = 40.sp)
                        Spacer(Modifier.height(12.dp))
                        Text("还没有添加文件", color = AnimeColors.TextPrimary,
                            fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            itemsIndexed(files, key = { _, f -> f.uri.toString() },
                contentType = { _, _ -> "file" }) { i, f ->
                var visible by remember(f.uri) { mutableStateOf(false) }
                LaunchedEffect(f.uri) {
                    delay((i.coerceAtMost(10)) * 40L)
                    visible = true
                }
                AnimatedVisibility(visible = visible,
                    enter = fadeIn(tween(240)) + slideInVertically(
                        initialOffsetY = { it / 3 }, animationSpec = tween(240))) {
                    FileRowAnime(f)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AnimePrimaryBtn("加密", Icons.Filled.Lock,
                !isProcessing && files.isNotEmpty(), Modifier.weight(1f)) { onEncrypt() }
            AnimeSecondaryBtn("解密", Icons.Filled.LockOpen,
                !isProcessing && files.isNotEmpty(), Modifier.weight(1f)) { onDecrypt() }
        }
    }
}

@Composable
fun AnimePrimaryBtn(text: String, icon: ImageVector, enabled: Boolean,
    modifier: Modifier, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, tween(100), label = "s")
    val bg = if (enabled) Brush.linearGradient(listOf(
        AnimeColors.SakuraDeep, AnimeColors.Lavender, AnimeColors.Sky))
    else Brush.linearGradient(listOf(
        AnimeColors.Lavender.copy(0.15f), AnimeColors.Lavender.copy(0.15f)))
    Box(modifier.height(58.dp).scale(scale)
        .clip(RoundedCornerShape(20.dp))
        .background(bg)
        .border(if (enabled) 1.dp else 0.5.dp,
            if (enabled) AnimeColors.Sakura.copy(0.85f) else AnimeColors.Lavender.copy(0.25f),
            RoundedCornerShape(20.dp))
        .clickable(interactionSource = interaction, indication = null, enabled = enabled) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick()
        }, contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Color.White.copy(if (enabled) 1f else 0.4f),
                modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, color = Color.White.copy(if (enabled) 1f else 0.4f),
                fontWeight = FontWeight.Black, fontSize = 16.sp)
        }
    }
}

@Composable
fun AnimeSecondaryBtn(text: String, icon: ImageVector, enabled: Boolean,
    modifier: Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, tween(100), label = "s")
    val bg = Brush.linearGradient(listOf(
        AnimeColors.Lavender.copy(if (enabled) 0.20f else 0.1f),
        AnimeColors.Sakura.copy(if (enabled) 0.15f else 0.08f)))
    Box(modifier.height(58.dp).scale(scale)
        .clip(RoundedCornerShape(20.dp))
        .background(bg)
        .border(0.8.dp, AnimeColors.Sakura.copy(if (enabled) 0.5f else 0.2f),
            RoundedCornerShape(20.dp))
        .clickable(interactionSource = interaction, indication = null, enabled = enabled) {
            onClick()
        }, contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = AnimeColors.TextPrimary.copy(if (enabled) 0.95f else 0.4f),
                modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, color = AnimeColors.TextPrimary.copy(if (enabled) 0.95f else 0.4f),
                fontWeight = FontWeight.Black, fontSize = 16.sp)
        }
    }
}

@Composable
fun AnimeSmallBtn(text: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, tween(90), label = "s")
    val bg = remember { Brush.linearGradient(listOf(
        AnimeColors.Sakura.copy(0.12f), AnimeColors.Lavender.copy(0.10f))) }
    Box(modifier.height(50.dp).scale(scale)
        .clip(RoundedCornerShape(16.dp))
        .background(bg)
        .border(0.8.dp, AnimeColors.Sakura.copy(0.35f), RoundedCornerShape(16.dp))
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
fun FileRowAnime(file: FileItem) {
    val bg = remember { Brush.linearGradient(listOf(
        AnimeColors.Sakura.copy(0.07f), AnimeColors.Lavender.copy(0.05f))) }
    val icBg = remember { Brush.linearGradient(listOf(
        AnimeColors.SakuraDeep.copy(0.35f), AnimeColors.Lavender.copy(0.25f))) }
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
        .background(bg)
        .border(0.8.dp, AnimeColors.Sakura.copy(0.22f), RoundedCornerShape(16.dp))
        .padding(horizontal = 14.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp))
                .background(icBg), contentAlignment = Alignment.Center) {
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
    var deviceBind by remember { mutableStateOf(true) }
    var validDaysText by remember { mutableStateOf("7") }
    var maxRunsText by remember { mutableStateOf("0") }
    var failLimitText by remember { mutableStateOf("3") }
    var advanced by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf("") }
    var isWorking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 12.dp)) {

        item(key = "hdr", contentType = "hdr") {
            AnimeCard(Modifier.fillMaxWidth(), accent = true) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(15.dp))
                        .background(Brush.linearGradient(listOf(
                            AnimeColors.SakuraDeep.copy(0.55f),
                            AnimeColors.Lavender.copy(0.5f)))),
                        contentAlignment = Alignment.Center) { Text("🛡️", fontSize = 24.sp) }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("v12 世界顶级源码保护", color = AnimeColors.TextPrimary,
                            fontWeight = FontWeight.Black, fontSize = 17.sp)
                        Spacer(Modifier.height(3.dp))
                        Text("✨ 反逆向 12 项 · 反爬虫板 · 自校验",
                            color = AnimeColors.Sakura, fontSize = 11.sp,
                            letterSpacing = 0.8.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item(key = "mode", contentType = "mode") {
            AnimeCard(Modifier.fillMaxWidth()) {
                Column {
                    Text("保护模式", color = AnimeColors.TextSecondary, fontSize = 12.sp,
                        fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AnimeModeChip("🔐 密码模式", passwordMode, Modifier.weight(1f)) { passwordMode = true }
                        AnimeModeChip("⚡ 无密码", !passwordMode, Modifier.weight(1f)) { passwordMode = false }
                    }
                }
            }
        }

        item(key = "paths", contentType = "paths") {
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
            item(key = "pwd", contentType = "pwd") {
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

        item(key = "adv", contentType = "adv") {
            AnimeCard(Modifier.fillMaxWidth()) {
                Column {
                    Row(Modifier.fillMaxWidth().clickable { advanced = !advanced },
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(if (advanced) "▼" else "▶", color = AnimeColors.Sakura, fontSize = 12.sp)
                        Spacer(Modifier.width(8.dp))
                        Text("高级安全选项", color = AnimeColors.TextPrimary,
                            fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    AnimatedVisibility(visible = advanced) {
                        Column {
                            Spacer(Modifier.height(14.dp))
                            if (!passwordMode) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Switch(checked = deviceBind, onCheckedChange = { deviceBind = it },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = AnimeColors.SakuraDeep,
                                            checkedTrackColor = AnimeColors.Sakura.copy(0.3f)))
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text("设备绑定", color = AnimeColors.TextPrimary,
                                            fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Text("换机无法运行", color = AnimeColors.TextSecondary.copy(0.6f),
                                            fontSize = 10.sp)
                                    }
                                }
                                Spacer(Modifier.height(12.dp))
                                AnimeTextField(validDaysText,
                                    { validDaysText = it.filter { c -> c.isDigit() } },
                                    "有效天数 (0 = 永不过期)", "7", isNumber = true)
                                Spacer(Modifier.height(12.dp))
                            }
                            AnimeTextField(maxRunsText,
                                { maxRunsText = it.filter { c -> c.isDigit() } },
                                "最大执行次数 (0 = 无限)", "0", isNumber = true)
                            Spacer(Modifier.height(12.dp))
                            if (passwordMode) {
                                AnimeTextField(failLimitText,
                                    { failLimitText = it.filter { c -> c.isDigit() } },
                                    "密码错误自毁阈值 (0 = 关闭)", "3", isNumber = true)
                            }
                        }
                    }
                }
            }
        }

        item(key = "action", contentType = "action") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AnimePrimaryBtn("加密", Icons.Filled.Lock, !isWorking, Modifier.weight(1f)) {
                    val maxRuns = maxRunsText.toIntOrNull() ?: 0
                    val failLimit = failLimitText.toIntOrNull() ?: 0
                    val validDays = validDaysText.toIntOrNull() ?: 7
                    when {
                        inputPath.isBlank() -> result = "❌ 请输入源文件路径"
                        passwordMode && password.length < 12 -> result = "❌ 密码至少 12 位"
                        passwordMode && evalPassword(password).isWeak -> result = "❌ 密码太弱"
                        passwordMode && password != password2 -> result = "❌ 两次不一致"
                        else -> scope.launch {
                            isWorking = true; result = "✨ 正在加密..."
                            result = crypto.protectShellScript(
                                inputPath, outputPath, password,
                                passwordMode, maxRuns, failLimit,
                                deviceBind, validDays)
                            isWorking = false
                        }
                    }
                }
                AnimeSecondaryBtn("解保护", Icons.Filled.LockOpen, !isWorking, Modifier.weight(1f)) {
                    if (inputPath.isBlank()) result = "❌ 请输入保护脚本路径"
                    else scope.launch {
                        isWorking = true; result = "✨ 正在解保护..."
                        result = crypto.unprotectShellScript(inputPath, outputPath, password)
                        isWorking = false
                    }
                }
            }
        }

        if (result.isNotEmpty()) {
            item(key = "result", contentType = "result") {
                AnimeCard(Modifier.fillMaxWidth()) {
                    Text(result, color = AnimeColors.TextPrimary, fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace, lineHeight = 19.sp)
                }
            }
        }
    }
}

@Composable
fun AnimeModeChip(text: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val scale by animateFloatAsState(if (active) 1f else 0.97f, tween(160), label = "m")
    val bg = if (active)
        Brush.linearGradient(listOf(AnimeColors.SakuraDeep, AnimeColors.Lavender))
    else Brush.linearGradient(listOf(
        AnimeColors.Lavender.copy(0.12f), AnimeColors.Sakura.copy(0.08f)))
    Box(modifier.height(50.dp).scale(scale)
        .clip(RoundedCornerShape(16.dp))
        .background(bg)
        .border(if (active) 1.2.dp else 0.6.dp,
            if (active) AnimeColors.Sakura.copy(0.85f) else AnimeColors.Lavender.copy(0.25f),
            RoundedCornerShape(16.dp))
        .clickable { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick() },
        contentAlignment = Alignment.Center) {
        Text(text, color = if (active) Color.White else AnimeColors.TextSecondary,
            fontSize = 13.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
fun SettingsTab(securityReport: SecurityChecker.Report) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 12.dp)) {

        item(key = "status", contentType = "status") {
            AnimeCard(Modifier.fillMaxWidth(), accent = true) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp))
                        .background(Brush.linearGradient(listOf(
                            AnimeColors.SakuraDeep, AnimeColors.Lavender, AnimeColors.Sky))),
                        contentAlignment = Alignment.Center) { Text("🛡️", fontSize = 26.sp) }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(if (securityReport.clean) "环境安全" else "环境存在风险",
                            color = if (securityReport.clean) AnimeColors.Mint else AnimeColors.Danger,
                            fontWeight = FontWeight.Black, fontSize = 18.sp)
                        Spacer(Modifier.height(3.dp))
                        Text("APK 内嵌反逆向 · 8 项实时检测",
                            color = AnimeColors.Sakura, fontSize = 11.sp,
                            fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item(key = "checks", contentType = "checks") {
            AnimeCard(Modifier.fillMaxWidth()) {
                Column {
                    Text("🛡️ 环境检测", color = AnimeColors.TextPrimary,
                        fontWeight = FontWeight.Black, fontSize = 15.sp)
                    Spacer(Modifier.height(10.dp))
                    SecRow("Root", securityReport.root)
                    SecRow("Frida", securityReport.frida)
                    SecRow("Xposed", securityReport.xposed)
                    SecRow("调试器", securityReport.debugger)
                    SecRow("模拟器", securityReport.emulator)
                    SecRow("虚拟多开", securityReport.virtualApp)
                    SecRow("APK 签名", securityReport.signatureValid)
                    SecRow("反编译重打包", securityReport.repackaged)
                }
            }
        }

        if (!securityReport.clean) {
            item(key = "warn", contentType = "warn") {
                AnimeCard(Modifier.fillMaxWidth()) {
                    Column {
                        Text("⚠️ 检测到风险", color = AnimeColors.Danger,
                            fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                        securityReport.issues.forEach { issue ->
                            Text("· $issue", color = AnimeColors.TextPrimary,
                                fontSize = 12.sp, lineHeight = 20.sp)
                        }
                    }
                }
            }
        }

        item(key = "shell", contentType = "shell") {
            AnimeCard(Modifier.fillMaxWidth()) {
                Column {
                    Text("🔒 Shell 保护 (12 + 3 项)", color = AnimeColors.TextPrimary,
                        fontWeight = FontWeight.Black, fontSize = 15.sp)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "· 反调试 4 项 (x/XTRACEFD/SHELLOPTS/PS4)\n" +
                        "· TracerPid 检测\n" +
                        "· LD_PRELOAD 检测\n" +
                        "· 拒绝 source / 软链\n" +
                        "· 父进程分析工具检测\n" +
                        "· 命令覆盖检测\n" +
                        "· SHA256 自校验\n" +
                        "· 三层密钥变换\n" +
                        "· 设备指纹绑定 (可选)\n" +
                        "· 时间窗口 (可选)\n" +
                        "· shred × 2 自毁\n" +
                        "· 反爬虫板: 预期文件名\n" +
                        "· 反爬虫板: 压缩包/临时目录\n" +
                        "· 反爬虫板: 采集目录黑名单",
                        color = AnimeColors.TextSecondary.copy(0.85f),
                        fontSize = 12.sp, lineHeight = 19.sp)
                }
            }
        }

        item(key = "about", contentType = "about") {
            AnimeCard(Modifier.fillMaxWidth()) {
                Column {
                    Text("关于", color = AnimeColors.TextPrimary,
                        fontWeight = FontWeight.Black, fontSize = 15.sp)
                    Spacer(Modifier.height(8.dp))
                    InfoRow("应用", "EncryptVault")
                    InfoRow("版本", "12.0.0")
                    InfoRow("网络", "零联网")
                    InfoRow("数据", "零收集")
                }
            }
        }
    }
}

@Composable
fun SecRow(label: String, ok: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(if (ok) "✅" else "❌", fontSize = 13.sp)
        Spacer(Modifier.width(8.dp))
        Text(label, color = AnimeColors.TextPrimary, fontSize = 12.sp)
        Spacer(Modifier.weight(1f))
        Text(if (ok) "通过" else "异常",
            color = if (ok) AnimeColors.Mint else AnimeColors.Danger,
            fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun InfoRow(k: String, v: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text("✿", color = AnimeColors.SakuraDeep.copy(0.7f), fontSize = 9.sp)
        Spacer(Modifier.width(6.dp))
        Text(k, color = AnimeColors.TextSecondary.copy(0.75f), fontSize = 12.sp)
        Spacer(Modifier.weight(1f))
        Text(v, color = AnimeColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
