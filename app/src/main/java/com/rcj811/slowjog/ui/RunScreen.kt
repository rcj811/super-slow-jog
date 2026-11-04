package com.rcj811.slowjog.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rcj811.slowjog.BeatClock
import com.rcj811.slowjog.Format
import com.rcj811.slowjog.JogService
import com.rcj811.slowjog.Phase
import com.rcj811.slowjog.Prefs
import com.rcj811.slowjog.RunRepository
import com.rcj811.slowjog.RunState
import com.rcj811.slowjog.Session
import com.rcj811.slowjog.SessionStore
import com.rcj811.slowjog.Settings
import com.rcj811.slowjog.Stats
import com.rcj811.slowjog.Totals
import kotlinx.coroutines.delay
import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun RunScreen(run: RunState) {
    val s by Prefs.settings.collectAsStateWithLifecycle()
    KeepScreenOn(s.keepScreenOn && run.isActive)

    BackHandler(enabled = run.phase == Phase.FINISHED) { RunRepository.dismissResult() }

    AnimatedContent(
        targetState = run.phase == Phase.FINISHED,
        transitionSpec = { (fadeIn(tween(500)) + scaleIn(initialScale = 0.96f)) togetherWith fadeOut(tween(300)) },
        label = "runOrResult",
    ) { finished ->
        if (finished) ResultView(run.result) else ActiveRun(run, s)
    }
}

@Composable
private fun ActiveRun(run: RunState, s: Settings) {
    val ctx = LocalContext.current
    var phase by remember { mutableDoubleStateOf(0.0) }
    val moving = run.phase == Phase.RUNNING
    LaunchedEffect(Unit) {
        while (true) withFrameNanos { if (BeatClock.active) phase = BeatClock.phase() }
    }
    val pulse = if (moving) beatPulse(phase) else 0f
    var confirmStop by remember { mutableStateOf(false) }
    val landscape = isLandscape()

    // 5 分鐘里程碑提示
    var bannerFor by remember { mutableIntStateOf(0) }
    var showBanner by remember { mutableStateOf(false) }
    LaunchedEffect(run.milestone) {
        if (run.milestone > 0) {
            bannerFor = run.milestone
            showBanner = true
            delay(4500)
            showBanner = false
        }
    }
    val done = bannerFor * Settings.REMINDER_EVERY_MIN
    val bannerText = "🎉 已完成 $done 分鐘・還剩 ${run.targetMin - done} 分鐘"

    val pausedBlink = rememberInfiniteTransition(label = "paused")
    val blink by pausedBlink.animateFloat(0.45f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "blink")

    val onStop: () -> Unit = { if (run.phase == Phase.COUNTDOWN) JogService.send(ctx, JogService.ACTION_STOP) else confirmStop = true }

    Box(
        Modifier
            .fillMaxSize()
            .background(JogColors.Bg)
            .drawBehind {
                // 隨節拍呼吸的背景光暈
                val c = if (landscape) Offset(size.height * 0.5f + 16.dp.toPx(), size.height / 2)
                else Offset(size.width / 2, size.height * 0.36f)
                val base = if (landscape) size.height else size.width
                drawCircle(
                    Brush.radialGradient(
                        listOf(JogColors.Sun2.copy(alpha = 0.10f + 0.10f * pulse), Color.Transparent),
                        center = c, radius = base * (0.75f + 0.04f * pulse),
                    ),
                    radius = base, center = c,
                )
            }
    ) {
        if (landscape) {
            Row(
                Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .displayCutoutPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RingArea(
                    run, phase, pulse, moving, blink,
                    Modifier.fillMaxHeight().aspectRatio(1f, matchHeightConstraintsFirst = true),
                )
                Spacer(Modifier.width(20.dp))
                Column(
                    Modifier.weight(1f).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceEvenly,
                ) {
                    RunHeader(run, s)
                    StatsCard(run)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FootRow(phase, pulse, moving)
                        Spacer(Modifier.width(24.dp))
                        CadenceControl(run)
                    }
                    Controls(run, pulse, compact = true, onStop = onStop)
                }
            }
            Box(
                Modifier.fillMaxWidth().statusBarsPadding().padding(top = 6.dp),
                contentAlignment = Alignment.TopCenter,
            ) { MilestoneBanner(showBanner, bannerText) }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(10.dp))
                RunHeader(run, s)
                Box(Modifier.fillMaxWidth().height(52.dp), contentAlignment = Alignment.Center) {
                    MilestoneBanner(showBanner, bannerText)
                }
                RingArea(run, phase, pulse, moving, blink, Modifier.fillMaxWidth(0.86f).aspectRatio(1f))
                Spacer(Modifier.height(10.dp))
                FootRow(phase, pulse, moving)
                Spacer(Modifier.height(12.dp))
                StatsCard(run)
                Spacer(Modifier.height(10.dp))
                CadenceControl(run)
                Spacer(Modifier.weight(1f))
                Controls(run, pulse, compact = false, onStop = onStop)
                Spacer(Modifier.height(18.dp))
            }
        }
    }

    if (confirmStop) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            containerColor = JogColors.SurfaceHigh,
            title = { Text("結束這次超慢跑？") },
            text = { Text(if (run.elapsedMs < 60_000) "未滿 1 分鐘不會列入紀錄。" else "目前進度會存進統計。") },
            confirmButton = {
                TextButton(onClick = { confirmStop = false; JogService.send(ctx, JogService.ACTION_STOP) }) {
                    Text("結束", color = JogColors.Sun3)
                }
            },
            dismissButton = { TextButton(onClick = { confirmStop = false }) { Text("繼續跑", color = JogColors.Text) } },
        )
    }
}

@Composable
private fun RunHeader(run: RunState, s: Settings) {
    val ctx = LocalContext.current
    var showVolume by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("超慢跑", style = MaterialTheme.typography.titleMedium, color = JogColors.Text)
            Text(
                "目標 ${Format.minutes(run.targetMin)}・${s.beatSound.label}聲",
                style = MaterialTheme.typography.bodyMedium, color = JogColors.Muted, maxLines = 1,
            )
        }
        ScreenModeChip()
        Spacer(Modifier.width(8.dp))
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(JogColors.Sun2.copy(alpha = 0.16f))
                .border(1.dp, JogColors.Sun2.copy(alpha = 0.5f), RoundedCornerShape(50))
                .clickable { showVolume = true }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlyphIcon(Glyph.SOUND, JogColors.Sun1, Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("${Math.round(s.volume * 100)}%", color = JogColors.Sun1, style = MaterialTheme.typography.labelMedium)
        }
    }
    if (showVolume) {
        VolumeDialog(
            onDismiss = { showVolume = false },
            onVoiceTest = { JogService.send(ctx, JogService.ACTION_VOICE_TEST) },
        )
    }
}

/** 進度圓環 + 跑者動畫 + 剩餘時間（大小隨可用空間縮放） */
@Composable
private fun RingArea(run: RunState, phase: Double, pulse: Float, moving: Boolean, blink: Float, modifier: Modifier) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val w = maxWidth
        val small = w < 280.dp
        ProgressRing(run, pulse, Modifier.fillMaxSize())
        when (run.phase) {
            Phase.COUNTDOWN -> CountdownCenter(run.countdown, small)
            else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Runner(
                    beatPhase = phase,
                    moving = moving,
                    modifier = Modifier.size(w * 0.46f).scale(1f + 0.015f * pulse),
                )
                Text(
                    Format.clock(run.remainingMs / 1000),
                    fontSize = if (small) 38.sp else 48.sp, fontWeight = FontWeight.Black,
                    color = JogColors.Text,
                )
                Text(
                    if (run.phase == Phase.PAUSED) "已暫停" else "剩餘時間",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (run.phase == Phase.PAUSED) JogColors.Sun1.copy(alpha = blink) else JogColors.Muted,
                )
            }
        }
    }
}

@Composable
private fun FootRow(phase: Double, pulse: Float, moving: Boolean) {
    val idx = floor(phase).toLong()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("左", color = JogColors.Faint, style = MaterialTheme.typography.labelMedium)
        FootprintDot(if (moving && idx % 2 == 0L) pulse else 0f, JogColors.Sun1, Modifier.size(width = 22.dp, height = 30.dp))
        FootprintDot(if (moving && idx % 2 != 0L) pulse else 0f, JogColors.Sun3, Modifier.size(width = 22.dp, height = 30.dp).padding(top = 4.dp))
        Text("右", color = JogColors.Faint, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun StatsCard(run: RunState) {
    JogCard(Modifier.fillMaxWidth(), padding = 14.dp) {
        Row(Modifier.fillMaxWidth()) {
            StatTile(Format.clock(run.elapsedMs / 1000), "已跑", Modifier.weight(1f), JogColors.Sky)
            StatTile(Format.thousands(run.steps), "步數", Modifier.weight(1f), JogColors.Mint)
            StatTile(Format.kcal(run.kcal), "大卡", Modifier.weight(1f), JogColors.Flame)
        }
    }
}

/** 跑步中調整步頻 */
@Composable
private fun CadenceControl(run: RunState) {
    val ctx = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        RepeatButton("−", { JogService.send(ctx, JogService.ACTION_CADENCE, run.cadence - 1) }, size = 44.dp,
            enabled = run.cadence > Settings.MIN_CADENCE)
        Column(Modifier.width(100.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${run.cadence}", style = MaterialTheme.typography.headlineSmall, color = JogColors.Text)
            Text("步/分", style = MaterialTheme.typography.labelMedium, color = JogColors.Muted)
        }
        RepeatButton("+", { JogService.send(ctx, JogService.ACTION_CADENCE, run.cadence + 1) }, size = 44.dp,
            enabled = run.cadence < Settings.MAX_CADENCE)
    }
}

@Composable
private fun Controls(run: RunState, pulse: Float, compact: Boolean, onStop: () -> Unit) {
    val ctx = LocalContext.current
    val moving = run.phase == Phase.RUNNING
    val side = if (compact) 54.dp else 64.dp
    val main = if (compact) 74.dp else 88.dp
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        Box(
            Modifier
                .size(side)
                .clip(CircleShape)
                .background(JogColors.SurfaceHigh)
                .border(1.dp, JogColors.Line, CircleShape)
                .clickable(onClick = onStop),
            contentAlignment = Alignment.Center,
        ) { GlyphIcon(Glyph.STOP, JogColors.Text, Modifier.size(side * 0.4f)) }

        val playScale by animateFloatAsState(if (moving) 1f + 0.04f * pulse else 1f, label = "play")
        Box(
            Modifier
                .size(main)
                .scale(playScale)
                .clip(CircleShape)
                .background(JogColors.sunrise)
                .clickable(enabled = run.phase != Phase.COUNTDOWN) {
                    if (run.phase == Phase.RUNNING) JogService.send(ctx, JogService.ACTION_PAUSE)
                    else JogService.send(ctx, JogService.ACTION_RESUME)
                },
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(if (run.phase == Phase.PAUSED) Glyph.PLAY else Glyph.PAUSE, Color(0xFF1A0E08), Modifier.size(main * 0.41f))
        }
        Spacer(Modifier.size(side)) // 視覺平衡
    }
}

@Composable
private fun MilestoneBanner(visible: Boolean, text: String) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
    ) {
        Box(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(JogColors.sunriseH)
                .padding(horizontal = 18.dp, vertical = 8.dp)
        ) {
            Text(text, color = Color(0xFF1A0E08), fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

@Composable
private fun ProgressRing(run: RunState, pulse: Float, modifier: Modifier) {
    val progress by animateFloatAsState(run.progress, tween(400, easing = LinearEasing), label = "progress")
    Canvas(modifier) {
        val stroke = size.minDimension * 0.045f
        val inset = stroke * 1.6f
        val d = size.minDimension - inset * 2
        val topLeft = Offset((size.width - d) / 2, (size.height - d) / 2)
        val arcSize = Size(d, d)
        val center = Offset(size.width / 2, size.height / 2)
        val r = d / 2

        // 節拍光環
        drawCircle(
            JogColors.Sun2.copy(alpha = 0.22f * pulse), r + stroke * (1.2f + 0.6f * pulse), center,
            style = Stroke(stroke * 0.5f),
        )
        // 底環
        drawArc(JogColors.SurfaceHigh, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
        // 進度
        if (progress > 0f) {
            rotate(-90f, center) {
                drawArc(
                    Brush.sweepGradient(listOf(JogColors.Sun1, JogColors.Sun2, JogColors.Sun3, JogColors.Sun1), center),
                    0f, 360f * progress, false, topLeft, arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
            // 前端亮點
            val ang = -PI / 2 + 2 * PI * progress
            val tip = Offset(center.x + (cos(ang) * r).toFloat(), center.y + (sin(ang) * r).toFloat())
            drawCircle(Color.White.copy(alpha = 0.9f), stroke * 0.32f, tip)
        }
        // 每 5 分鐘刻度
        val marks = run.targetMin / Settings.REMINDER_EVERY_MIN
        for (i in 1 until marks) {
            val f = i.toFloat() / marks
            val ang = -PI / 2 + 2 * PI * f
            val passed = progress >= f
            val inner = r - stroke * 1.25f
            val outer = r - stroke * 0.85f
            drawLine(
                if (passed) JogColors.Sun1 else JogColors.Faint,
                Offset(center.x + (cos(ang) * inner).toFloat(), center.y + (sin(ang) * inner).toFloat()),
                Offset(center.x + (cos(ang) * outer).toFloat(), center.y + (sin(ang) * outer).toFloat()),
                strokeWidth = stroke * 0.18f, cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun CountdownCenter(n: Int, small: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("準備好了嗎？", color = JogColors.Muted, style = MaterialTheme.typography.titleMedium)
        AnimatedContent(
            targetState = n,
            transitionSpec = {
                (scaleIn(initialScale = 1.8f, animationSpec = spring(Spring.DampingRatioMediumBouncy)) + fadeIn()) togetherWith
                    (scaleOut(targetScale = 0.5f) + fadeOut())
            },
            label = "countdown",
        ) { v ->
            Text(
                if (v > 0) "$v" else "GO",
                fontSize = if (small) 92.sp else 120.sp, fontWeight = FontWeight.Black,
                color = JogColors.Sun1,
            )
        }
        Text("跟著節拍，用前腳掌輕輕落地", color = JogColors.Muted, style = MaterialTheme.typography.bodyMedium)
    }
}

// ---------------- 完成畫面 ----------------

@Composable
private fun ResultView(result: Session?) {
    val sessions by SessionStore.sessions.collectAsStateWithLifecycle()
    val today = remember(sessions) { Totals.of(Stats.onDay(sessions, LocalDate.now())) }
    val badge = remember { Animatable(0f) }
    val check = remember { Animatable(0f) }
    val confetti = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        badge.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow))
    }
    LaunchedEffect(Unit) {
        delay(250)
        check.animateTo(1f, tween(550, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(Unit) { confetti.animateTo(1f, tween(2800, easing = LinearEasing)) }
    val particles = remember { makeConfetti() }
    val completed = result?.completed == true

    Box(Modifier.fillMaxSize().background(JogColors.Bg)) {
        Canvas(Modifier.fillMaxSize()) {
            val t = confetti.value
            if (t in 0.001f..0.999f) particles.forEach { p ->
                val x = size.width * (0.5f + p.vx * t)
                val y = size.height * (0.3f + p.vy * t + 0.9f * t * t)
                rotate(p.spin * t * 720f, Offset(x, y)) {
                    drawRect(p.color.copy(alpha = 1f - t), Offset(x, y), Size(p.size, p.size * 0.5f))
                }
            }
        }
        val badgeView: @Composable (Dp) -> Unit = { size ->
            Box(
                Modifier
                    .size(size)
                    .scale(badge.value)
                    .clip(CircleShape)
                    .background(JogColors.sunrise),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(size * 0.48f)) {
                    val path = Path().apply {
                        moveTo(this@Canvas.size.width * 0.12f, this@Canvas.size.height * 0.52f)
                        lineTo(this@Canvas.size.width * 0.42f, this@Canvas.size.height * 0.8f)
                        lineTo(this@Canvas.size.width * 0.9f, this@Canvas.size.height * 0.22f)
                    }
                    val pm = PathMeasure().apply { setPath(path, false) }
                    val seg = Path()
                    pm.getSegment(0f, pm.length * check.value, seg, true)
                    drawPath(seg, Color(0xFF1A0E08), style = Stroke(this@Canvas.size.width * 0.12f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        }
        val titleView: @Composable () -> Unit = {
            Text(if (completed) "完成！" else "辛苦了！", style = MaterialTheme.typography.headlineLarge, color = JogColors.Text)
            Text(
                if (completed) "今天也笑著跑完了 ${result?.targetMin ?: 0} 分鐘" else "提前結束也很棒，持續最重要",
                color = JogColors.Muted, style = MaterialTheme.typography.bodyLarge,
            )
        }
        val statsView: @Composable () -> Unit = {
            if (result != null) {
                JogCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth()) {
                        StatTile(Format.clock(result.durationSec.toLong()), "時間", Modifier.weight(1f), JogColors.Sky)
                        StatTile(Format.thousands(result.steps), "步數", Modifier.weight(1f), JogColors.Mint)
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth()) {
                        StatTile(Format.kcal(result.kcal), "大卡", Modifier.weight(1f), JogColors.Flame)
                        StatTile("${result.cadence}", "平均步頻", Modifier.weight(1f), JogColors.Sun1)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "今天累計 ${today.minutes} 分鐘・${Format.thousands(today.steps)} 步・${Format.kcal(today.kcal)} 大卡",
                    color = JogColors.Muted, style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        if (isLandscape()) {
            Row(
                Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .displayCutoutPadding()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    badgeView(108.dp)
                    Spacer(Modifier.height(16.dp))
                    titleView()
                }
                Spacer(Modifier.width(24.dp))
                Column(Modifier.weight(1.2f), horizontalAlignment = Alignment.CenterHorizontally) {
                    statsView()
                    Spacer(Modifier.height(16.dp))
                    GradientButton("完成", { RunRepository.dismissResult() }, Modifier.fillMaxWidth())
                }
            }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.weight(0.6f))
                badgeView(132.dp)
                Spacer(Modifier.height(22.dp))
                titleView()
                Spacer(Modifier.height(24.dp))
                statsView()
                Spacer(Modifier.weight(1f))
                GradientButton("完成", { RunRepository.dismissResult() }, Modifier.fillMaxWidth())
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

private class Particle(val vx: Float, val vy: Float, val spin: Float, val size: Float, val color: Color)

private fun makeConfetti(): List<Particle> {
    val rnd = Random(42)
    val colors = listOf(JogColors.Sun1, JogColors.Sun2, JogColors.Sun3, JogColors.Mint, JogColors.Sky, Color.White)
    return List(70) {
        val a = rnd.nextFloat() * 2 * PI.toFloat()
        val sp = 0.25f + rnd.nextFloat() * 0.5f
        Particle(
            vx = cos(a) * sp, vy = sin(a) * sp * 0.8f - 0.35f,
            spin = rnd.nextFloat() * 2 - 1, size = 14f + rnd.nextFloat() * 18f,
            color = colors[rnd.nextInt(colors.size)],
        )
    }
}

@Composable
fun KeepScreenOn(on: Boolean) {
    val view = LocalView.current
    DisposableEffect(on) {
        view.keepScreenOn = on
        onDispose { view.keepScreenOn = false }
    }
}
