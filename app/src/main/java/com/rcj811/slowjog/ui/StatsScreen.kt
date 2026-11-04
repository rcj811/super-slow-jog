package com.rcj811.slowjog.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rcj811.slowjog.Format
import com.rcj811.slowjog.Metric
import com.rcj811.slowjog.Session
import com.rcj811.slowjog.SessionStore
import com.rcj811.slowjog.Stats
import com.rcj811.slowjog.Totals
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.log10
import kotlin.math.pow

private enum class Range(val label: String) { DAY("日"), MONTH("月"), YEAR("年") }

@Composable
fun StatsScreen() {
    val ctx = LocalContext.current
    val all by SessionStore.sessions.collectAsStateWithLifecycle()
    var rangeIdx by rememberSaveable { mutableIntStateOf(0) }
    var metricIdx by rememberSaveable { mutableIntStateOf(0) }
    var dayEpoch by rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }
    var toDelete by remember { mutableStateOf<Session?>(null) }

    val range = Range.entries[rangeIdx]
    val metric = Metric.entries[metricIdx]
    val day = LocalDate.ofEpochDay(dayEpoch)
    val today = LocalDate.now()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        val streak = remember(all) { Stats.streak(all, today) }
        ScreenTitle("統計", if (streak > 0) "已連續 $streak 天 🔥" else "開始累積你的超慢跑紀錄")

        Segmented(Range.entries.map { it.label }, rangeIdx, { rangeIdx = it }, Modifier.fillMaxWidth())

        // 期間切換
        val title = when (range) {
            Range.DAY -> day.format(DateTimeFormatter.ofPattern("yyyy 年 M 月 d 日")) + "（" +
                day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.TAIWAN) + "）"
            Range.MONTH -> day.format(DateTimeFormatter.ofPattern("yyyy 年 M 月"))
            Range.YEAR -> "${day.year} 年"
        }
        val canNext = when (range) {
            Range.DAY -> day.isBefore(today)
            Range.MONTH -> YearMonth.from(day).isBefore(YearMonth.from(today))
            Range.YEAR -> day.year < today.year
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            NavArrow(Glyph.LEFT, true) {
                dayEpoch = when (range) {
                    Range.DAY -> day.minusDays(1); Range.MONTH -> day.minusMonths(1); Range.YEAR -> day.minusYears(1)
                }.toEpochDay()
            }
            Text(
                title, Modifier.weight(1f).clickable { dayEpoch = today.toEpochDay() },
                textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium, color = JogColors.Text,
            )
            NavArrow(Glyph.RIGHT, canNext) {
                val next = when (range) {
                    Range.DAY -> day.plusDays(1); Range.MONTH -> day.plusMonths(1); Range.YEAR -> day.plusYears(1)
                }
                dayEpoch = minOf(next.toEpochDay(), today.toEpochDay())
            }
        }

        val periodSessions = remember(all, range, dayEpoch) {
            when (range) {
                Range.DAY -> Stats.onDay(all, day)
                Range.MONTH -> Stats.inMonth(all, YearMonth.from(day))
                Range.YEAR -> Stats.inYear(all, day.year)
            }
        }
        val totals = remember(periodSessions) { Totals.of(periodSessions) }

        // 彙總
        JogCard {
            Row(Modifier.fillMaxWidth()) {
                if (totals.minutes >= 60) {
                    StatTile(String.format(Locale.US, "%.1f", totals.minutes / 60.0), "小時", Modifier.weight(1f), JogColors.Sky)
                } else {
                    StatTile("${totals.minutes}", "分鐘", Modifier.weight(1f), JogColors.Sky)
                }
                StatTile(Format.thousands(totals.steps), "步數", Modifier.weight(1f), JogColors.Mint)
                StatTile(Format.kcal(totals.kcal), "大卡", Modifier.weight(1f), JogColors.Flame)
            }
            Spacer(Modifier.height(10.dp))
            val extra = when (range) {
                Range.DAY -> "跑了 ${totals.count} 次"
                else -> "跑了 ${totals.count} 次・${Stats.activeDays(periodSessions)} 天有運動"
            }
            Text(extra, color = JogColors.Muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }

        // 長條圖
        JogCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel(
                    when (range) { Range.DAY -> "本週每日"; Range.MONTH -> "每日"; Range.YEAR -> "每月" } + "・" + metric.label,
                    Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Metric.entries.forEachIndexed { i, m -> Chip(m.label, i == metricIdx, { metricIdx = i }) }
            }
            Spacer(Modifier.height(12.dp))

            val bars: List<Bar> = remember(all, range, dayEpoch, metric) {
                when (range) {
                    Range.DAY -> Stats.weekOf(all, day).map { (d, t) ->
                        Bar(d.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.TAIWAN), metric.of(t), d == day, d.toEpochDay())
                    }
                    Range.MONTH -> {
                        val ym = YearMonth.from(day)
                        Stats.daysOfMonth(all, ym).mapIndexed { i, t ->
                            val dn = i + 1
                            Bar(if (dn == 1 || dn % 5 == 0) "$dn" else "", metric.of(t), dn == day.dayOfMonth, ym.atDay(dn).toEpochDay())
                        }
                    }
                    Range.YEAR -> Stats.monthsOfYear(all, day.year).mapIndexed { i, t ->
                        Bar("${i + 1}", metric.of(t), i + 1 == day.monthValue, LocalDate.of(day.year, i + 1, 1).toEpochDay())
                    }
                }
            }
            BarChart(
                bars, metric,
                onTap = { b ->
                    val d = LocalDate.ofEpochDay(b.epochDay)
                    if (!d.isAfter(today)) {
                        when (range) {
                            Range.DAY -> dayEpoch = b.epochDay
                            Range.MONTH -> { dayEpoch = b.epochDay; rangeIdx = Range.DAY.ordinal }
                            Range.YEAR -> { dayEpoch = b.epochDay; rangeIdx = Range.MONTH.ordinal }
                        }
                    }
                },
            )
            Text(
                when (range) { Range.DAY -> "點一下切換日期"; Range.MONTH -> "點一下查看當天"; Range.YEAR -> "點一下查看該月" },
                color = JogColors.Faint, style = MaterialTheme.typography.labelMedium,
            )
        }

        // 紀錄列表
        if (range == Range.DAY) {
            SectionLabel("紀錄")
            if (periodSessions.isEmpty()) {
                Text("這天還沒有紀錄", color = JogColors.Faint, style = MaterialTheme.typography.bodyMedium)
            } else {
                periodSessions.sortedByDescending { it.startMs }.forEach { s -> SessionRow(s) { toDelete = s } }
            }
        } else if (periodSessions.isNotEmpty()) {
            val best = periodSessions.maxBy { it.durationSec }
            JogCard {
                SectionLabel("最長的一次")
                Spacer(Modifier.height(6.dp))
                SessionRow(best, null)
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    toDelete?.let { s ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            containerColor = JogColors.SurfaceHigh,
            title = { Text("刪除這筆紀錄？") },
            text = { Text("${timeOf(s)}・${Format.clock(s.durationSec.toLong())}・${Format.thousands(s.steps)} 步") },
            confirmButton = { TextButton(onClick = { SessionStore.delete(ctx, s); toDelete = null }) { Text("刪除", color = JogColors.Sun3) } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("取消", color = JogColors.Text) } },
        )
    }
}

@Composable
private fun NavArrow(glyph: Glyph, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(JogColors.SurfaceHigh)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { GlyphIcon(glyph, if (enabled) JogColors.Text else JogColors.Faint, Modifier.size(22.dp)) }
}

private fun timeOf(s: Session): String =
    Instant.ofEpochMilli(s.startMs).atZone(ZoneId.systemDefault()).toLocalTime()
        .format(DateTimeFormatter.ofPattern("HH:mm"))

@Composable
private fun SessionRow(s: Session, onLongPress: (() -> Unit)?) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(JogColors.Surface)
            .pointerInput(onLongPress) { detectTapGestures(onLongPress = { onLongPress?.invoke() }) }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(42.dp).clip(CircleShape)
                .background(if (s.completed) JogColors.Sun2.copy(alpha = 0.18f) else JogColors.SurfaceHigh),
            contentAlignment = Alignment.Center,
        ) { GlyphIcon(Glyph.RUN, if (s.completed) JogColors.Sun1 else JogColors.Muted, Modifier.size(22.dp)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "${s.date().format(DateTimeFormatter.ofPattern("M/d"))} ${timeOf(s)}・${Format.clock(s.durationSec.toLong())}",
                color = JogColors.Text, style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "${Format.thousands(s.steps)} 步・${Format.kcal(s.kcal)} 大卡・${s.cadence} 步/分" + if (s.completed) "" else "・提前結束",
                color = JogColors.Muted, style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private data class Bar(val label: String, val value: Double, val highlight: Boolean, val epochDay: Long)

private fun niceCeil(v: Double): Double {
    if (v <= 0) return 1.0
    val mag = 10.0.pow(kotlin.math.floor(log10(v)))
    val n = v / mag
    val step = when { n <= 1 -> 1.0; n <= 2 -> 2.0; n <= 5 -> 5.0; else -> 10.0 }
    return ceil(step * mag)
}

private fun fmtMetric(v: Double, m: Metric): String = when (m) {
    Metric.MINUTES -> "${v.toInt()}"
    Metric.STEPS -> if (v >= 10000) String.format(Locale.US, "%.1f萬", v / 10000) else Format.thousands(v.toLong())
    Metric.KCAL -> Format.kcal(v)
}

@Composable
private fun BarChart(bars: List<Bar>, metric: Metric, onTap: (Bar) -> Unit) {
    val grow = remember { Animatable(0f) }
    LaunchedEffect(bars) {
        grow.snapTo(0f)
        grow.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
    }
    val max = niceCeil(bars.maxOfOrNull { it.value } ?: 0.0)
    val hl = bars.firstOrNull { it.highlight }

    Column {
        Row {
            Text("最高 ${fmtMetric(max, metric)} ${metric.unit}", color = JogColors.Faint, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.weight(1f))
            if (hl != null) Text("${fmtMetric(hl.value, metric)} ${metric.unit}", color = JogColors.Sun1, style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(6.dp))
        val measurer = rememberTextMeasurer()
        val labelStyle = MaterialTheme.typography.labelMedium
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(184.dp)
                .pointerInput(bars) {
                    detectTapGestures { pos ->
                        if (bars.isEmpty()) return@detectTapGestures
                        val i = (pos.x / size.width * bars.size).toInt().coerceIn(0, bars.size - 1)
                        onTap(bars[i])
                    }
                }
        ) {
            val n = bars.size
            if (n == 0) return@Canvas
            val slot = size.width / n
            val bw = (slot * if (n > 12) 0.62f else 0.5f).coerceAtMost(28.dp.toPx())
            val labelH = 22.dp.toPx()
            val chartH = size.height - labelH
            // 參考線
            listOf(0.5f, 1f).forEach { f ->
                val y = chartH * (1 - f)
                drawLine(
                    JogColors.Line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f)),
                )
            }
            bars.forEachIndexed { i, b ->
                val x = slot * i + (slot - bw) / 2
                val frac = (b.value / max).toFloat().coerceIn(0f, 1f) * grow.value
                val h = (chartH * frac).coerceAtLeast(if (b.value > 0) 4.dp.toPx() else 2.dp.toPx())
                val brush = if (b.highlight) JogColors.sunrise
                else if (b.value > 0) Brush.verticalGradient(listOf(JogColors.Sun2.copy(alpha = 0.55f), JogColors.Sun3.copy(alpha = 0.35f)))
                else Brush.verticalGradient(listOf(JogColors.SurfaceHigh, JogColors.SurfaceHigh))
                drawRoundRect(brush, Offset(x, chartH - h), Size(bw, h), CornerRadius(bw / 2.2f))
                if (b.label.isNotEmpty()) {
                    val layout = measurer.measure(
                        b.label,
                        labelStyle.copy(color = if (b.highlight) JogColors.Sun1 else JogColors.Faint),
                    )
                    drawText(
                        layout,
                        topLeft = Offset(slot * i + slot / 2 - layout.size.width / 2f, chartH + 4.dp.toPx()),
                    )
                }
            }
        }
    }
}

