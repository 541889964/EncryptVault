package com.sourceguard.tool

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sourceguard.tool.crypto.CryptoManager
import com.sourceguard.tool.crypto.FileItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object AColor {
    val P = Color(0xFFFFB6D9)
    val PD = Color(0xFFFF6EC7)
    val L = Color(0xFFA78BFA)
    val LL = Color(0xFFC4B5FD)
    val S = Color(0xFF7DD3FC)
    val M = Color(0xFF6EE7B7)
    val B1 = Color(0xFF1A0B2E)
    val B2 = Color(0xFF2D1B4E)
    val B3 = Color(0xFF4A1F6F)
    val T1 = Color(0xFFFFF5FA)
    val T2 = Color(0xFFE9D5FF)
    val D = Color(0xFFFF5252)
}

private val BGB = Brush.linearGradient(listOf(AColor.B1, AColor.B2, AColor.B3, AColor.B1))
private val G1 = Brush.radialGradient(listOf(AColor.PD.copy(alpha = 0.42f), Color.Transparent))
private val G2 = Brush.radialGradient(listOf(AColor.L.copy(alpha = 0.30f), Color.Transparent))

@Composable
fun ABg(content: @Composable () -> Unit) {
    val t = rememberInfiniteTransition(label = "bg")
    val sh by t.animateFloat(
        initialValue = 0f, targetValue = 800f,
        animationSpec = infiniteRepeatable(tween(45000, easing = LinearEasing)),
        label = "sh"
    )
    val br by t.animateFloat(
        initialValue = 0.9f, targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            tween(3800, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "br"
    )
    Box(Modifier.fillMaxSize().background(BGB)) {
        Box(
            Modifier
                .size(380.dp)
                .graphicsLayer {
                    translationX = sh * 1.1f
                    translationY = 60f
                    scaleX = br
                    scaleY = br
                }
                .background(G1, CircleShape)
        )
        Box(
            Modifier
                .size(320.dp)
                .graphicsLayer {
                    translationX = 1100f - sh * 0.85f
                    translationY = 540f + sh * 0.3f
                    scaleX = br
                    scaleY = br
                }
                .background(G2, CircleShape)
        )
        content()
    }
}

@Composable
fun ACard(
    m: Modifier = Modifier,
    cr: Dp = 26.dp,
    ac: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val bd = remember(ac) {
        if (ac) Brush.linearGradient(
            listOf(AColor.PD.copy(0.85f), AColor.L.copy(0.65f), AColor.S.copy(0.55f), AColor.PD.copy(0.85f))
        ) else Brush.linearGradient(
            listOf(AColor.P.copy(0.22f), AColor.L.copy(0.15f))
        )
    }
    val bg = remember(ac) {
        if (ac) Brush.linearGradient(
            listOf(AColor.PD.copy(0.10f), AColor.L.copy(0.06f))
        ) else Brush.linearGradient(
            listOf(Color.White.copy(0.055f), AColor.P.copy(0.04f))
        )
    }
    Box(
        m.clip(RoundedCornerShape(cr))
            .background(bg)
            .border(1.dp, bd, RoundedCornerShape(cr))
            .padding(18.dp),
        content = content
    )
}

@Composable
fun ATF(
    v: String, oc: (String) -> Unit, lb: String,
    ph: String = "", ip: Boolean = false, sp: Boolean = false,
    ot: (() -> Unit)? = null, nm: Boolean = false, m: Modifier = Modifier
) {
    Column(m) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("✿", color = AColor.PD, fontSize = 11.sp)
            Spacer(Modifier.width(4.dp))
            Text(lb, color = AColor.T2, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(7.dp))
        OutlinedTextField(
            value = v, onValueChange = oc,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(ph, color = AColor.T2.copy(alpha = 0.4f), fontSize = 14.sp) },
            visualTransformation = if (ip && !sp) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = if (nm) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
            trailingIcon = ot?.let { cb ->
                {
                    IconButton(onClick = cb) {
                        Icon(
                            imageVector = if (sp) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = null,
                            tint = AColor.LL.copy(alpha = 0.7f)
                        )
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = AColor.T1,
                unfocusedTextColor = AColor.T1,
                focusedBorderColor = AColor.PD,
                unfocusedBorderColor = AColor.L.copy(alpha = 0.25f),
                cursorColor = AColor.PD,
                focusedContainerColor = AColor.PD.copy(alpha = 0.05f),
                unfocusedContainerColor = Color.White.copy(alpha = 0.03f)
            ),
            shape = RoundedCornerShape(16.dp),
            singleLine = true
        )
    }
}

data class PW(val score: Float, val label: String, val color: Color, val hint: String, val weak: Boolean = false)

fun evalPW(p: String): PW {
    if (p.isEmpty()) return PW(0f, "", Color.Transparent, "")
    val c = setOf("password", "123456", "12345678", "qwerty", "abc123", "admin", "welcome", "password1")
    if (p.lowercase() in c) return PW(0.1f, "禁用", AColor.D, "常见字典密码", true)
    if (p.length < 8 || p.all { it.isDigit() } || p.all { it.isLetter() })
        return PW(0.15f, "极弱", Color(0xFFE53935), "太简单", true)
    var s = 0
    if (p.length >= 8) s++
    if (p.length >= 12) s++
    if (p.length >= 16) s++
    if (p.any { it.isLowerCase() } && p.any { it.isUpperCase() }) s++
    if (p.any { it.isDigit() }) s++
    if (p.any { !it.isLetterOrDigit() }) s++
    return when {
        s <= 2 -> PW(0.3f, "弱", Color(0xFFFF6584), "建议 12 位以上", true)
        s <= 3 -> PW(0.55f, "中", Color(0xFFFFA726), "加符号更安全")
        s <= 4 -> PW(0.8f, "强", AColor.M, "接近顶级")
        else -> PW(1f, "极强", AColor.P, "顶级强度 ✓")
    }
}

@Composable
fun AStrength(p: String) {
    val s = remember(p) { evalPW(p) }
    val sc by animateFloatAsState(targetValue = s.score, animationSpec = tween(400), label = "sc")
    val cc by animateColorAsState(targetValue = s.color, animationSpec = tween(400), label = "cc")
    Column {
        Box(
            Modifier.fillMaxWidth().height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(AColor.B1.copy(alpha = 0.6f))
        ) {
            Box(
                Modifier.fillMaxWidth(sc).height(6.dp)
                    .background(
                        Brush.horizontalGradient(listOf(AColor.PD, cc, AColor.P)),
                        RoundedCornerShape(3.dp)
                    )
            )
        }
        if (s.label.isNotEmpty()) {
            Spacer(Modifier.height(7.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("✿", color = cc, fontSize = 10.sp)
                Spacer(Modifier.width(5.dp))
                Text(s.label, color = cc, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text(s.hint, color = AColor.T2.copy(alpha = 0.65f), fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun animateColorAsState(
    targetValue: Color,
    animationSpec: androidx.compose.animation.core.AnimationSpec<Color>,
    label: String
): androidx.compose.runtime.State<Color> {
    return androidx.compose.animation.animateColorAsState(
        targetValue = targetValue,
        animationSpec = animationSpec,
        label = label
    )
}

@Composable
fun EncryptApp(securityReport: List<CheckItem>? = null) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val crypto = remember { CryptoManager(ctx) }

    var files by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var work by remember { mutableStateOf(false) }
    var stage by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var pwd by remember { mutableStateOf("") }
    var showPw by remember { mutableStateOf(false) }
    var tab by remember { mutableIntStateOf(0) }
    val cnt by remember { derivedStateOf { files.size } }

    val fp = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { u ->
        if (u.isNotEmpty()) files = files + u.mapNotNull { crypto.getFileInfo(it) }
    }
    val dp = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { u ->
        u?.let { scope.launch { files = files + crypto.scanDirectory(it) } }
    }

    ABg {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🛡️", fontSize = 26.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            when (tab) {
                                0 -> "文件加密"
                                1 -> "源码保护"
                                else -> "设置"
                            },
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Black,
                            color = AColor.T1
                        )
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "✨ v19 · 30项反逆向 · 世界顶级",
                        fontSize = 11.sp,
                        color = AColor.P,
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (tab == 0 && cnt > 0) {
                    IconButton(onClick = { files = emptyList(); status = "已清空" }) {
                        Icon(Icons.Outlined.DeleteSweep, null, tint = AColor.PD)
                    }
                }
            }

            Box(Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = tab,
                    transitionSpec = {
                        (
                            fadeIn(animationSpec = tween(300)) +
                            slideInVertically(
                                animationSpec = tween(300, easing = FastOutSlowInEasing),
                                initialOffsetY = { it / 8 }
                            ) +
                            scaleIn(
                                animationSpec = tween(300),
                                initialScale = 0.96f
                            )
                        ).togetherWith(
                            fadeOut(animationSpec = tween(200)) +
                            slideOutVertically(
                                animationSpec = tween(200),
                                targetOffsetY = { -it / 12 }
                            )
                        )
                    },
                    label = "tab"
                ) { t ->
                    when (t) {
                        0 -> HomeTab(
                            files = files,
                            work = work,
                            stage = stage,
                            status = status,
                            pwd = pwd,
                            showPw = showPw,
                            onPw = { pwd = it },
                            onTog = { showPw = !showPw },
                            onAF = { fp.launch(arrayOf("*/*")) },
                            onAD = { dp.launch(null) },
                            onE = {
                                if (evalPW(pwd).weak) status = "密码太弱"
                                else scope.launch {
                                    work = true
                                    var ok = 0
                                    files.forEachIndexed { i, f ->
                                        stage = "加密 ${i+1}/$cnt · ${f.name}"
                                        if (crypto.encryptFile(f.uri, pwd)) ok++
                                    }
                                    stage = ""
                                    status = "完成 · $ok/$cnt"
                                    work = false
                                }
                            },
                            onD = {
                                if (pwd.isBlank()) status = "请输入密码"
                                else scope.launch {
                                    work = true
                                    var ok = 0
                                    files.forEachIndexed { i, f ->
                                        stage = "解密 ${i+1}/$cnt · ${f.name}"
                                        if (crypto.decryptFile(f.uri, pwd)) ok++
                                    }
                                    stage = ""
                                    status = if (ok == 0) "解密失败 · 密码错误" else "完成 · $ok/$cnt"
                                    work = false
                                }
                            }
                        )
                        1 -> ShellTab(crypto)
                        2 -> SettingsTab(securityReport ?: SecurityChecker.check(ctx))
                    }
                }
            }

            ATabBar(tab) { tab = it }
        }
    }
}

@Composable
fun ATabBar(sel: Int, onSel: (Int) -> Unit) {
    val h = LocalHapticFeedback.current
    val items = remember {
        listOf(
            Triple("加密", Icons.Outlined.Lock, Icons.Filled.Lock),
            Triple("Shell", Icons.Outlined.Security, Icons.Filled.Security),
            Triple("设置", Icons.Outlined.Settings, Icons.Filled.Settings)
        )
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items.forEachIndexed { i, item ->
            val label = item.first
            val oi = item.second
            val fi = item.third
            val a = sel == i
            val s by animateFloatAsState(
                targetValue = if (a) 1f else 0.94f,
                animationSpec = tween(250, easing = FastOutSlowInEasing),
                label = "s"
            )
            val isc by animateFloatAsState(
                targetValue = if (a) 1.1f else 1f,
                animationSpec = tween(250),
                label = "isc"
            )
            val bg = if (a)
                Brush.linearGradient(listOf(AColor.PD, AColor.L, AColor.S))
            else
                Brush.linearGradient(listOf(AColor.P.copy(0.08f), AColor.L.copy(0.06f)))
            Box(
                Modifier.weight(1f).height(62.dp).scale(s)
                    .clip(RoundedCornerShape(31.dp))
                    .background(bg)
                    .border(
                        width = if (a) 1.5.dp else 0.6.dp,
                        color = if (a) AColor.P.copy(0.9f) else AColor.L.copy(0.18f),
                        shape = RoundedCornerShape(31.dp)
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        h.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSel(i)
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (a) fi else oi,
                        contentDescription = null,
                        tint = if (a) Color.White else AColor.T2.copy(0.7f),
                        modifier = Modifier.size(22.dp).scale(isc)
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        label,
                        color = if (a) Color.White else AColor.T2.copy(0.7f),
                        fontSize = 10.sp,
                        fontWeight = if (a) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
fun HomeTab(
    files: List<FileItem>, work: Boolean, stage: String, status: String,
    pwd: String, showPw: Boolean,
    onPw: (String) -> Unit, onTog: () -> Unit,
    onAF: () -> Unit, onAD: () -> Unit,
    onE: () -> Unit, onD: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        ACard(Modifier.fillMaxWidth(), ac = pwd.isNotEmpty()) {
            Column {
                ATF(pwd, onPw, "加密密码", "输入密码 · 建议 16 位以上", ip = true, sp = showPw, ot = onTog)
                if (pwd.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    AStrength(pwd)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ABtnS("添加文件", Icons.Outlined.InsertDriveFile, Modifier.weight(1f), onAF)
            ABtnS("添加目录", Icons.Outlined.FolderOpen, Modifier.weight(1f), onAD)
        }
        AnimatedVisibility(
            visible = stage.isNotEmpty(),
            enter = fadeIn(animationSpec = tween(200)) + expandVertically(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(150)) + shrinkVertically(animationSpec = tween(150))
        ) {
            ACard(Modifier.fillMaxWidth().padding(top = 12.dp), cr = 18.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), color = AColor.PD, strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(stage, color = AColor.T1, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        AnimatedVisibility(
            visible = status.isNotEmpty() && stage.isEmpty(),
            enter = fadeIn(animationSpec = tween(250)) +
                slideInVertically(animationSpec = tween(250), initialOffsetY = { -it / 2 }),
            exit = fadeOut(animationSpec = tween(150))
        ) {
            Text(
                status,
                color = when {
                    status.startsWith("完成") || status.startsWith("已清空") -> AColor.M
                    status.contains("失败") || status.contains("弱") -> AColor.PD
                    else -> AColor.LL
                },
                fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 10.dp)
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("🎀 已选文件", color = AColor.T2.copy(0.8f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("${files.size}", color = AColor.P, fontSize = 14.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (files.isEmpty()) {
                item(key = "empty", contentType = "empty") {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 50.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🌸", fontSize = 40.sp)
                            Spacer(Modifier.height(12.dp))
                            Text("还没有添加文件", color = AColor.T1, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            itemsIndexed(
                items = files,
                key = { _, f -> f.uri.toString() },
                contentType = { _, _ -> "file" }
            ) { i, f ->
                var v by remember(f.uri) { mutableStateOf(false) }
                LaunchedEffect(f.uri) {
                    delay((i.coerceAtMost(10)) * 40L)
                    v = true
                }
                AnimatedVisibility(
                    visible = v,
                    enter = fadeIn(animationSpec = tween(280)) +
                        slideInVertically(animationSpec = tween(280), initialOffsetY = { it / 3 })
                ) {
                    ARow(f)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ABtnP("加密", Icons.Filled.Lock, !work && files.isNotEmpty(), Modifier.weight(1f), onE)
            ABtnO("解密", Icons.Filled.LockOpen, !work && files.isNotEmpty(), Modifier.weight(1f), onD)
        }
    }
}

@Composable
fun ABtnP(t: String, ic: ImageVector, en: Boolean, m: Modifier, oc: () -> Unit) {
    val h = LocalHapticFeedback.current
    val ir = remember { MutableInteractionSource() }
    val p by ir.collectIsPressedAsState()
    val s by animateFloatAsState(targetValue = if (p) 0.95f else 1f, animationSpec = tween(100), label = "s")
    val bg = if (en) Brush.linearGradient(listOf(AColor.PD, AColor.L, AColor.S))
    else Brush.linearGradient(listOf(AColor.L.copy(0.15f), AColor.L.copy(0.15f)))
    Box(
        m.height(58.dp).scale(s)
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .border(
                width = if (en) 1.dp else 0.5.dp,
                color = if (en) AColor.P.copy(0.85f) else AColor.L.copy(0.25f),
                shape = RoundedCornerShape(20.dp)
            )
            .clickable(
                interactionSource = ir, indication = null, enabled = en
            ) {
                h.performHapticFeedback(HapticFeedbackType.LongPress)
                oc()
            },
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(ic, null, tint = Color.White.copy(if (en) 1f else 0.4f), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(t, color = Color.White.copy(if (en) 1f else 0.4f), fontWeight = FontWeight.Black, fontSize = 16.sp)
        }
    }
}

@Composable
fun ABtnO(t: String, ic: ImageVector, en: Boolean, m: Modifier, oc: () -> Unit) {
    val ir = remember { MutableInteractionSource() }
    val p by ir.collectIsPressedAsState()
    val s by animateFloatAsState(targetValue = if (p) 0.95f else 1f, animationSpec = tween(100), label = "s")
    val bg = Brush.linearGradient(
        listOf(
            AColor.L.copy(if (en) 0.20f else 0.1f),
            AColor.P.copy(if (en) 0.15f else 0.08f)
        )
    )
    Box(
        m.height(58.dp).scale(s)
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .border(0.8.dp, AColor.P.copy(if (en) 0.5f else 0.2f), RoundedCornerShape(20.dp))
            .clickable(interactionSource = ir, indication = null, enabled = en) { oc() },
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(ic, null, tint = AColor.T1.copy(if (en) 0.95f else 0.4f), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(t, color = AColor.T1.copy(if (en) 0.95f else 0.4f), fontWeight = FontWeight.Black, fontSize = 16.sp)
        }
    }
}

@Composable
fun ABtnS(t: String, ic: ImageVector, m: Modifier, oc: () -> Unit) {
    val ir = remember { MutableInteractionSource() }
    val p by ir.collectIsPressedAsState()
    val s by animateFloatAsState(targetValue = if (p) 0.96f else 1f, animationSpec = tween(90), label = "s")
    val bg = remember { Brush.linearGradient(listOf(AColor.P.copy(0.12f), AColor.L.copy(0.10f))) }
    Box(
        m.height(50.dp).scale(s)
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .border(0.8.dp, AColor.P.copy(0.35f), RoundedCornerShape(16.dp))
            .clickable(interactionSource = ir, indication = null, onClick = oc),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(ic, null, tint = AColor.P, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(6.dp))
            Text(t, color = AColor.T1, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ARow(f: FileItem) {
    val bg = remember { Brush.linearGradient(listOf(AColor.P.copy(0.07f), AColor.L.copy(0.05f))) }
    val ib = remember { Brush.linearGradient(listOf(AColor.PD.copy(0.35f), AColor.L.copy(0.25f))) }
    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .border(0.8.dp, AColor.P.copy(0.22f), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(ib),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    when {
                        f.name.endsWith(".sh") -> "⌨️"
                        f.name.endsWith(".txt") -> "📄"
                        f.name.endsWith(".apk") -> "📱"
                        else -> "📁"
                    },
                    fontSize = 20.sp
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    f.name,
                    color = AColor.T1, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Text(f.size, color = AColor.T2.copy(0.6f), fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun ShellTab(c: CryptoManager) {
    var ip by remember { mutableStateOf("") }
    var op by remember { mutableStateOf("") }
    var p1 by remember { mutableStateOf("") }
    var p2 by remember { mutableStateOf("") }
    var shw by remember { mutableStateOf(false) }
    var pm by remember { mutableStateOf(true) }
    var db by remember { mutableStateOf(true) }
    var vd by remember { mutableStateOf("7") }
    var mr by remember { mutableStateOf("0") }
    var fl by remember { mutableStateOf("3") }
    var adv by remember { mutableStateOf(false) }
    var res by remember { mutableStateOf("") }
    var wk by remember { mutableStateOf(false) }
    val sc = rememberCoroutineScope()

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 12.dp)
    ) {
        item(key = "h", contentType = "h") {
            ACard(Modifier.fillMaxWidth(), ac = true) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(48.dp).clip(RoundedCornerShape(15.dp))
                            .background(Brush.linearGradient(listOf(AColor.PD.copy(0.55f), AColor.L.copy(0.5f)))),
                        contentAlignment = Alignment.Center
                    ) { Text("🛡️", fontSize = 24.sp) }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("v19 世界顶级源码保护", color = AColor.T1, fontWeight = FontWeight.Black, fontSize = 17.sp)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "✨ 30 项反逆向 · 反 Python · 反反编译",
                            color = AColor.P, fontSize = 11.sp, fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        item(key = "m", contentType = "m") {
            ACard(Modifier.fillMaxWidth()) {
                Column {
                    Text("保护模式", color = AColor.T2, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AModeChip("🔐 密码模式", pm, Modifier.weight(1f)) { pm = true }
                        AModeChip("⚡ 无密码", !pm, Modifier.weight(1f)) { pm = false }
                    }
                }
            }
        }

        item(key = "p", contentType = "p") {
            ACard(Modifier.fillMaxWidth()) {
                Column {
                    ATF(ip, { ip = it }, "源文件路径", "/storage/emulated/0/myscript.sh")
                    Spacer(Modifier.height(12.dp))
                    ATF(op, { op = it }, "输出目录", "/storage/emulated/0/Download")
                }
            }
        }

        if (pm) {
            item(key = "pw", contentType = "pw") {
                ACard(Modifier.fillMaxWidth(), ac = p1.isNotEmpty()) {
                    Column {
                        ATF(p1, { p1 = it }, "保护密码 (至少 12 位)", "输入强密码",
                            ip = true, sp = shw, ot = { shw = !shw })
                        if (p1.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            AStrength(p1)
                        }
                        Spacer(Modifier.height(12.dp))
                        ATF(p2, { p2 = it }, "确认密码", "再输一次", ip = true, sp = shw)
                    }
                }
            }
        }

        item(key = "adv", contentType = "adv") {
            ACard(Modifier.fillMaxWidth()) {
                Column {
                    Row(
                        Modifier.fillMaxWidth().clickable { adv = !adv },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (adv) "▼" else "▶", color = AColor.P, fontSize = 12.sp)
                        Spacer(Modifier.width(8.dp))
                        Text("高级安全选项", color = AColor.T1, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    AnimatedVisibility(
                        visible = adv,
                        enter = fadeIn(animationSpec = tween(200)) +
                            expandVertically(animationSpec = tween(200)),
                        exit = fadeOut(animationSpec = tween(150)) +
                            shrinkVertically(animationSpec = tween(150))
                    ) {
                        Column {
                            Spacer(Modifier.height(14.dp))
                            if (!pm) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Switch(
                                        checked = db,
                                        onCheckedChange = { db = it },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = AColor.PD,
                                            checkedTrackColor = AColor.P.copy(0.3f)
                                        )
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text("设备绑定", color = AColor.T1, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Text("换机无法运行", color = AColor.T2.copy(0.6f), fontSize = 10.sp)
                                    }
                                }
                                Spacer(Modifier.height(12.dp))
                                ATF(vd, { vd = it.filter { ch -> ch.isDigit() } },
                                    "有效天数 (0=永不过期)", "7", nm = true)
                                Spacer(Modifier.height(12.dp))
                            }
                            ATF(mr, { mr = it.filter { ch -> ch.isDigit() } },
                                "最大执行次数 (0=无限)", "0", nm = true)
                            Spacer(Modifier.height(12.dp))
                            if (pm) {
                                ATF(fl, { fl = it.filter { ch -> ch.isDigit() } },
                                    "密码错误自毁阈值 (0=关闭)", "3", nm = true)
                            }
                        }
                    }
                }
            }
        }

        item(key = "a", contentType = "a") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ABtnP("加密", Icons.Filled.Lock, !wk, Modifier.weight(1f)) {
                    val m = mr.toIntOrNull() ?: 0
                    val f = fl.toIntOrNull() ?: 0
                    val v = vd.toIntOrNull() ?: 7
                    when {
                        ip.isBlank() -> res = "❌ 请输入源文件路径"
                        pm && p1.length < 12 -> res = "❌ 密码至少 12 位"
                        pm && evalPW(p1).weak -> res = "❌ 密码太弱"
                        pm && p1 != p2 -> res = "❌ 两次不一致"
                        else -> sc.launch {
                            wk = true
                            res = "✨ 正在加密..."
                            res = c.protectShellScript(ip, op, p1, pm, m, f, db, v)
                            wk = false
                        }
                    }
                }
                ABtnO("解保护", Icons.Filled.LockOpen, !wk, Modifier.weight(1f)) {
                    if (ip.isBlank()) res = "❌ 请输入保护脚本路径"
                    else sc.launch {
                        wk = true
                        res = "✨ 正在解保护..."
                        res = c.unprotectShellScript(ip, op, p1)
                        wk = false
                    }
                }
            }
        }

        if (res.isNotEmpty()) {
            item(key = "r", contentType = "r") {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(animationSpec = tween(250)) +
                        slideInVertically(animationSpec = tween(250), initialOffsetY = { it / 4 })
                ) {
                    ACard(Modifier.fillMaxWidth()) {
                        Text(
                            res, color = AColor.T1, fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace, lineHeight = 19.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AModeChip(t: String, a: Boolean, m: Modifier, oc: () -> Unit) {
    val h = LocalHapticFeedback.current
    val s by animateFloatAsState(targetValue = if (a) 1f else 0.97f, animationSpec = tween(160), label = "s")
    val bg = if (a) Brush.linearGradient(listOf(AColor.PD, AColor.L))
    else Brush.linearGradient(listOf(AColor.L.copy(0.12f), AColor.P.copy(0.08f)))
    Box(
        m.height(50.dp).scale(s)
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .border(
                width = if (a) 1.2.dp else 0.6.dp,
                color = if (a) AColor.P.copy(0.85f) else AColor.L.copy(0.25f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable {
                h.performHapticFeedback(HapticFeedbackType.LongPress)
                oc()
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            t,
            color = if (a) Color.White else AColor.T2,
            fontSize = 13.sp,
            fontWeight = if (a) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun SettingsTab(items: List<CheckItem>) {
    val anyIssue = items.any { !it.passed }
    val fatal = items.any { !it.passed && it.fatal }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 12.dp)
    ) {
        item(key = "st", contentType = "st") {
            ACard(Modifier.fillMaxWidth(), ac = true) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(52.dp).clip(RoundedCornerShape(16.dp))
                            .background(Brush.linearGradient(listOf(AColor.PD, AColor.L, AColor.S))),
                        contentAlignment = Alignment.Center
                    ) { Text(if (!anyIssue) "🛡️" else "⚠️", fontSize = 26.sp) }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            when {
                                !anyIssue -> "环境安全"
                                fatal -> "存在致命风险"
                                else -> "存在告警"
                            },
                            color = when {
                                !anyIssue -> AColor.M
                                fatal -> AColor.D
                                else -> Color(0xFFFFA726)
                            },
                            fontWeight = FontWeight.Black, fontSize = 18.sp
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "${items.count { it.passed }}/${items.size} 项通过",
                            color = AColor.P, fontSize = 11.sp, fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        item(key = "ck", contentType = "ck") {
            ACard(Modifier.fillMaxWidth()) {
                Column {
                    Text("🛡️ 环境检测", color = AColor.T1, fontWeight = FontWeight.Black, fontSize = 15.sp)
                    Spacer(Modifier.height(10.dp))
                    items.forEach { it2 ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                if (it2.passed) "✅" else if (it2.fatal) "❌" else "⚠️",
                                fontSize = 13.sp
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(it2.name, color = AColor.T1, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text(it2.detail, color = AColor.T2.copy(0.7f), fontSize = 11.sp)
                            }
                            Text(
                                if (it2.passed) "通过" else if (it2.fatal) "阻断" else "警告",
                                color = if (it2.passed) AColor.M else if (it2.fatal) AColor.D else Color(0xFFFFA726),
                                fontSize = 12.sp, fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        item(key = "cr", contentType = "cr") {
            ACard(Modifier.fillMaxWidth()) {
                Column {
                    Text("🔐 v19 加密参数", color = AColor.T1, fontWeight = FontWeight.Black, fontSize = 15.sp)
                    Spacer(Modifier.height(10.dp))
                    IR("文件算法", "AES-256-GCM")
                    IR("文件 PRF", "PBKDF2-SHA512 6M")
                    IR("子密钥", "HKDF-SHA512")
                    IR("完整性", "GCM + HMAC-SHA256")
                    IR("Shell 密钥", "PBKDF2-SHA512 2M")
                    IR("Shell 反逆向", "30 项")
                    IR("反 Python", "5 层")
                    IR("反反编译", "jadx/apktool/dex2jar")
                }
            }
        }

        item(key = "ab", contentType = "ab") {
            ACard(Modifier.fillMaxWidth()) {
                Column {
                    Text("关于", color = AColor.T1, fontWeight = FontWeight.Black, fontSize = 15.sp)
                    Spacer(Modifier.height(8.dp))
                    IR("应用", "源码防破解工具")
                    IR("版本", "19.0.0")
                    IR("网络", "零联网")
                    IR("数据", "零收集")
                }
            }
        }
    }
}

@Composable
fun IR(k: String, v: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("✿", color = AColor.PD.copy(0.7f), fontSize = 9.sp)
        Spacer(Modifier.width(6.dp))
        Text(k, color = AColor.T2.copy(0.75f), fontSize = 12.sp)
        Spacer(Modifier.weight(1f))
        Text(v, color = AColor.T1, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
