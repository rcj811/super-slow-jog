package com.rcj811.slowjog.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rcj811.slowjog.BeatClock
import com.rcj811.slowjog.Calories
import com.rcj811.slowjog.Format
import com.rcj811.slowjog.Prefs
import com.rcj811.slowjog.PreviewPlayer
import com.rcj811.slowjog.SessionStore
import com.rcj811.slowjog.Settings
import com.rcj811.slowjog.Stats
import com.rcj811.slowjog.Totals
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun SetupScreen(onStart: () -> Unit) {
    val ctx = LocalContext.current
    val s by Prefs.settings.collectAsStateWithLifecycle()
    val sessions by SessionStore.sessions.collectAsStateWithLifecycle()
    val previewing by PreviewPlayer.playing.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    val todayTotals = remember(sessions) { Totals.of(Stats.onDay(sessions, today)) }
    val streak = remember(sessions) { Stats.streak(sessions, today) }

    DisposableEffect(Unit) { onDispose { PreviewPlayer.stop() } }
    LaunchedEffect(s.cadence, s.volume, s.alternateTone) { PreviewPlayer.update(s) }

    val breathe = rememberInfiniteTransition(label = "breathe")
    val scale by breathe.animateFloat(1f, 1.03f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "scale")

    val header: @Composable () -> Unit = {
        val dateText = today.format(DateTimeFormatter.ofPattern("M 月 d 日")) + " " +
            today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.TAIWAN)
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScreenTitle("超慢跑", dateText, Modifier.weight(1f))
            if (streak > 0) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(JogColors.Sun2.copy(alpha = 0.15f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("🔥 連續 $streak 天", color = JogColors.Sun1, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
    val todayCard: @Composable () -> Unit = {
        // 今日小結
        JogCard {
            SectionLabel("今天")
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth()) {
                StatTile("${todayTotals.minutes}", "分鐘", Modifier.weight(1f), JogColors.Sky)
                StatTile(Format.thousands(todayTotals.steps), "步", Modifier.weight(1f), JogColors.Mint)
                StatTile(Format.kcal(todayTotals.kcal), "大卡", Modifier.weight(1f), JogColors.Flame)
            }
        }
    }
    val durationCard: @Composable () -> Unit = {
        // 時間
        JogCard {
            SectionLabel("跑步時間（每格 10 分鐘）")
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                RepeatButton("−", { Prefs.update(ctx) { it.copy(durationMin = it.durationMin - Settings.DURATION_STEP) } },
                    enabled = s.durationMin > Settings.MIN_DURATION)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("${s.durationMin}", style = MaterialTheme.typography.displayLarge, color = JogColors.Text)
                        Text(" 分鐘", style = MaterialTheme.typography.titleMedium, color = JogColors.Muted, modifier = Modifier.padding(bottom = 12.dp))
                    }
                    if (s.durationMin >= 60) Text(Format.minutes(s.durationMin), color = JogColors.Muted, style = MaterialTheme.typography.bodyMedium)
                }
                RepeatButton("+", { Prefs.update(ctx) { it.copy(durationMin = it.durationMin + Settings.DURATION_STEP) } },
                    enabled = s.durationMin < Settings.MAX_DURATION)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(10, 20, 30, 40, 60).forEach { m ->
                    Chip("$m", s.durationMin == m, { Prefs.update(ctx) { it.copy(durationMin = m) } })
                }
            }
        }
    }
    val cadenceCard: @Composable () -> Unit = {
        // 步頻
        JogCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("步頻（每分鐘 160–220 步，每格 5）", Modifier.weight(1f))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (previewing != null) JogColors.Sun2.copy(alpha = 0.2f) else JogColors.SurfaceHigh)
                        .border(1.dp, if (previewing != null) JogColors.Sun2 else JogColors.Line, RoundedCornerShape(50))
                        .clickable { if (previewing != null) PreviewPlayer.stop() else PreviewPlayer.play(s, millis = 0) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GlyphIcon(if (previewing != null) Glyph.STOP else Glyph.SOUND, if (previewing != null) JogColors.Sun1 else JogColors.Muted, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (previewing != null) "停止" else "試聽", color = if (previewing != null) JogColors.Sun1 else JogColors.Muted, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                RepeatButton("−", { Prefs.update(ctx) { it.copy(cadence = it.cadence - Settings.CADENCE_STEP) } }, enabled = s.cadence > Settings.MIN_CADENCE)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BeatDot(previewing != null)
                        Spacer(Modifier.width(10.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("${s.cadence}", style = MaterialTheme.typography.displayLarge, color = JogColors.Text)
                            Text(" 步/分", style = MaterialTheme.typography.titleMedium, color = JogColors.Muted, modifier = Modifier.padding(bottom = 12.dp))
                        }
                    }
                    Text(cadenceHint(s.cadence), color = JogColors.Muted, style = MaterialTheme.typography.bodyMedium)
                }
                RepeatButton("+", { Prefs.update(ctx) { it.copy(cadence = it.cadence + Settings.CADENCE_STEP) } }, enabled = s.cadence < Settings.MAX_CADENCE)
            }
            Slider(
                value = s.cadence.toFloat(),
                onValueChange = { v -> Prefs.update(ctx) { it.copy(cadence = Math.round(v)) } },
                valueRange = Settings.MIN_CADENCE.toFloat()..Settings.MAX_CADENCE.toFloat(),
                steps = (Settings.MAX_CADENCE - Settings.MIN_CADENCE) / Settings.CADENCE_STEP - 1,
                colors = SliderDefaults.colors(
                    thumbColor = JogColors.Sun1, activeTrackColor = JogColors.Sun2, inactiveTrackColor = JogColors.SurfaceHigh,
                ),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                listOf(160 to "入門", 170 to "輕鬆", 180 to "標準", 200 to "進階", 220 to "挑戰").forEach { (c, name) ->
                    Chip("$name $c", s.cadence == c, { Prefs.update(ctx) { it.copy(cadence = c) } })
                }
            }
        }
    }
    val startArea: @Composable () -> Unit = {
        // 預估
        val estSteps = s.durationMin.toLong() * s.cadence
        val estKcal = Calories.kcal(s.cadence, s.weightKg, s.durationMin * 60_000L)
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(JogColors.SurfaceHigh.copy(alpha = 0.6f))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("預估", color = JogColors.Muted, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.width(12.dp))
            Text("${Format.thousands(estSteps)} 步", color = JogColors.Mint, style = MaterialTheme.typography.titleMedium)
            Text("  ·  ", color = JogColors.Faint)
            Text("約 ${Format.kcal(estKcal)} 大卡", color = JogColors.Flame, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            Text("${s.beatSound.label}聲", color = JogColors.Muted, style = MaterialTheme.typography.labelMedium)
        }

        GradientButton(
            "開始超慢跑",
            onClick = { PreviewPlayer.stop(); onStart() },
            modifier = Modifier.fillMaxWidth(),
            breathing = scale,
        )
    }

    if (isLandscape()) {
        Row(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Spacer(Modifier.height(8.dp))
                header()
                MascotHero(s.cadence, previewing != null)
                todayCard()
                durationCard()
                Spacer(Modifier.height(16.dp))
            }
            Column(
                Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Spacer(Modifier.height(8.dp))
                cadenceCard()
                startArea()
                Spacer(Modifier.height(16.dp))
            }
        }
    } else {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            header()
            MascotHero(s.cadence, previewing != null)
            todayCard()
            durationCard()
            cadenceCard()
            startArea()
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun cadenceHint(c: Int) = when {
    c <= 165 -> "輕鬆入門・適合暖身"
    c <= 175 -> "輕鬆節奏"
    c <= 185 -> "標準超慢跑節奏"
    c <= 195 -> "稍快・心肺加強"
    c <= 205 -> "進階・小步快跑"
    else -> "挑戰・快節奏"
}

/** 隨節拍跳動的小點（試聽時） */
@Composable
private fun BeatDot(active: Boolean) {
    var phase by remember { mutableDoubleStateOf(0.0) }
    LaunchedEffect(active) {
        if (!active) { phase = 0.0; return@LaunchedEffect }
        while (true) withFrameNanos { phase = BeatClock.phase() }
    }
    val p = if (active) beatPulse(phase) else 0f
    Canvas(Modifier.size(18.dp)) {
        val r = size.minDimension / 2
        drawCircle(JogColors.Sun2.copy(alpha = 0.25f + 0.25f * p), r * (0.7f + 0.3f * p))
        drawCircle(if (active) JogColors.Sun1 else JogColors.Faint, r * (0.38f + 0.2f * p), Offset(size.width / 2, size.height / 2))
    }
}
