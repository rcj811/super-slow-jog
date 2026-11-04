package com.rcj811.slowjog.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rcj811.slowjog.Prefs
import com.rcj811.slowjog.ScreenMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun JogCard(
    modifier: Modifier = Modifier,
    padding: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(24.dp))
            .background(JogColors.Surface)
            .border(1.dp, JogColors.Line.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
            .padding(padding),
        content = content,
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text, modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
        color = JogColors.Muted,
    )
}

/** 圓形 ± 按鈕，按住會連續調整 */
@Composable
fun RepeatButton(
    symbol: String,
    onStep: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    enabled: Boolean = true,
) {
    val step by rememberUpdatedState(onStep)
    val scope = rememberCoroutineScope()
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(if (enabled) JogColors.SurfaceHigh else JogColors.Surface)
            .border(1.dp, JogColors.Line, CircleShape)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown()
                    step()
                    var job: Job? = scope.launch {
                        delay(420)
                        var d = 110L
                        while (true) {
                            step(); delay(d); d = (d * 0.9).toLong().coerceAtLeast(45)
                        }
                    }
                    waitForUpOrCancellation()
                    job?.cancel(); job = null
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            symbol, fontSize = 26.sp, fontWeight = FontWeight.Medium,
            color = if (enabled) JogColors.Text else JogColors.Faint,
        )
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val bg by animateColorAsState(if (selected) JogColors.Sun2.copy(alpha = 0.18f) else JogColors.SurfaceHigh, label = "chipBg")
    val fg by animateColorAsState(if (selected) JogColors.Sun1 else JogColors.Muted, label = "chipFg")
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .border(1.dp, if (selected) JogColors.Sun2.copy(alpha = 0.6f) else JogColors.Line, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = fg, style = MaterialTheme.typography.labelLarge)
    }
}

/** 分段切換（日 / 月 / 年 等） */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(JogColors.SurfaceHigh)
            .padding(4.dp),
    ) {
        options.forEachIndexed { i, label ->
            val sel = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(if (sel) JogColors.sunriseH else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)))
                    .clickable { onSelect(i) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (sel) Color(0xFF1A0E08) else JogColors.Muted,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    breathing: Float = 1f,
) {
    Box(
        modifier
            .scale(breathing)
            .height(64.dp)
            .clip(RoundedCornerShape(50))
            .background(JogColors.sunriseH)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color(0xFF1A0E08), fontSize = 20.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
fun StatTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    color: Color = JogColors.Text,
    unit: String? = null,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = MaterialTheme.typography.headlineSmall, color = color, maxLines = 1)
            if (unit != null) {
                Text(" $unit", style = MaterialTheme.typography.labelMedium, color = JogColors.Muted, modifier = Modifier.padding(bottom = 3.dp))
            }
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = JogColors.Muted, textAlign = TextAlign.Center)
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = JogColors.Text)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = JogColors.Muted)
        }
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF1A0E08),
                checkedTrackColor = JogColors.Sun2,
                uncheckedTrackColor = JogColors.SurfaceHigh,
                uncheckedBorderColor = JogColors.Line,
                uncheckedThumbColor = JogColors.Muted,
            ),
        )
    }
}

@Composable
fun RowScope.Spacer1() = Box(Modifier.weight(1f))

// ---------------- 自繪圖示 ----------------

enum class Glyph { PLAY, PAUSE, STOP, CHART, RUN, GEAR, LEFT, RIGHT, SOUND, PHONE, PHONE_LAND, ROTATE }

@Composable
fun GlyphIcon(glyph: Glyph, color: Color, modifier: Modifier = Modifier.size(24.dp)) {
    Canvas(modifier) { drawGlyph(glyph, color) }
}

fun DrawScope.drawGlyph(glyph: Glyph, color: Color) {
    val w = size.width
    val h = size.height
    val sw = w * 0.1f
    when (glyph) {
        Glyph.PLAY -> {
            val p = Path().apply {
                moveTo(w * 0.3f, h * 0.18f); lineTo(w * 0.84f, h * 0.5f); lineTo(w * 0.3f, h * 0.82f); close()
            }
            drawPath(p, color)
        }
        Glyph.PAUSE -> {
            drawRoundRect(color, Offset(w * 0.24f, h * 0.18f), Size(w * 0.17f, h * 0.64f), CornerRadius(w * 0.05f))
            drawRoundRect(color, Offset(w * 0.59f, h * 0.18f), Size(w * 0.17f, h * 0.64f), CornerRadius(w * 0.05f))
        }
        Glyph.STOP -> drawRoundRect(color, Offset(w * 0.24f, h * 0.24f), Size(w * 0.52f, h * 0.52f), CornerRadius(w * 0.08f))
        Glyph.CHART -> {
            val bw = w * 0.16f
            listOf(0.45f to 0.12f, 0.25f to 0.42f, 0.6f to 0.72f).forEach { (top, x) ->
                drawRoundRect(color, Offset(w * x, h * top), Size(bw, h * (0.88f - top)), CornerRadius(bw / 2))
            }
        }
        Glyph.RUN -> {
            drawFootprint(Offset(w * 0.34f, h * 0.6f), w * 0.36f, color)
            drawFootprint(Offset(w * 0.66f, h * 0.36f), w * 0.36f, color)
        }
        Glyph.GEAR -> {
            val c = Offset(w / 2, h / 2)
            for (i in 0 until 8) {
                rotate(i * 45f, c) {
                    drawRoundRect(color, Offset(w * 0.43f, h * 0.06f), Size(w * 0.14f, h * 0.22f), CornerRadius(w * 0.03f))
                }
            }
            drawCircle(color, w * 0.3f, c, style = Stroke(sw * 1.3f))
        }
        Glyph.LEFT, Glyph.RIGHT -> {
            val dir = if (glyph == Glyph.LEFT) 1f else -1f
            val p = Path().apply {
                moveTo(w * (0.5f + 0.12f * dir), h * 0.22f)
                lineTo(w * (0.5f - 0.16f * dir), h * 0.5f)
                lineTo(w * (0.5f + 0.12f * dir), h * 0.78f)
            }
            drawPath(p, color, style = Stroke(sw * 1.2f, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        }
        Glyph.PHONE, Glyph.PHONE_LAND -> {
            val land = glyph == Glyph.PHONE_LAND
            val pw = if (land) w * 0.84f else w * 0.5f
            val ph = if (land) h * 0.5f else h * 0.84f
            val tl = Offset((w - pw) / 2, (h - ph) / 2)
            drawRoundRect(color, tl, Size(pw, ph), CornerRadius(w * 0.1f), style = Stroke(sw))
            val dot = if (land) Offset(tl.x + pw - w * 0.11f, h / 2) else Offset(w / 2, tl.y + ph - h * 0.11f)
            drawCircle(color, w * 0.045f, dot)
        }
        Glyph.ROTATE -> {
            drawArc(color, 200f, 250f, false, Offset(w * 0.18f, h * 0.18f), Size(w * 0.64f, h * 0.64f),
                style = Stroke(sw, cap = androidx.compose.ui.graphics.StrokeCap.Round))
            // 箭頭
            val p = Path().apply {
                moveTo(w * 0.12f, h * 0.30f); lineTo(w * 0.25f, h * 0.47f); lineTo(w * 0.38f, h * 0.30f)
            }
            drawPath(p, color, style = Stroke(sw, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        }
        Glyph.SOUND -> {
            val p = Path().apply {
                moveTo(w * 0.12f, h * 0.38f); lineTo(w * 0.3f, h * 0.38f); lineTo(w * 0.52f, h * 0.18f)
                lineTo(w * 0.52f, h * 0.82f); lineTo(w * 0.3f, h * 0.62f); lineTo(w * 0.12f, h * 0.62f); close()
            }
            drawPath(p, color)
            drawArc(color, -45f, 90f, false, Offset(w * 0.42f, h * 0.3f), Size(w * 0.4f, h * 0.4f), style = Stroke(sw, cap = androidx.compose.ui.graphics.StrokeCap.Round))
        }
    }
}

/** 鞋印：前掌橢圓 + 腳跟 */
fun DrawScope.drawFootprint(center: Offset, height: Float, color: Color) {
    val fw = height * 0.42f
    drawOval(color, Offset(center.x - fw / 2, center.y - height / 2), Size(fw, height * 0.62f))
    val hw = fw * 0.75f
    drawOval(color, Offset(center.x - hw / 2, center.y + height * 0.18f), Size(hw, height * 0.32f))
}

@Composable
fun FootprintDot(lit: Float, color: Color, modifier: Modifier = Modifier) {
    val a by animateFloatAsState(lit, label = "foot")
    Canvas(modifier) {
        drawFootprint(Offset(size.width / 2, size.height / 2), size.height * 0.95f, color.copy(alpha = 0.18f + 0.82f * a))
    }
}

@Composable
fun ScreenTitle(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.headlineLarge, color = JogColors.Text)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = JogColors.Muted)
    }
}

@Composable
fun Rows(spacing: Dp = 12.dp, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing), content = content)
}

/** 目前畫面是否為橫向（寬 > 高） */
@Composable
fun isLandscape(): Boolean {
    val c = androidx.compose.ui.platform.LocalConfiguration.current
    return c.screenWidthDp > c.screenHeightDp
}

fun ScreenMode.glyph(): Glyph = when (this) {
    ScreenMode.PORTRAIT -> Glyph.PHONE
    ScreenMode.LANDSCAPE -> Glyph.PHONE_LAND
    ScreenMode.AUTO -> Glyph.ROTATE
}

/** 點一下在 直向 → 橫向 → 自動旋轉 之間切換 */
@Composable
fun ScreenModeChip(modifier: Modifier = Modifier) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val s by Prefs.settings.collectAsStateWithLifecycle()
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(JogColors.SurfaceHigh)
            .clickable { Prefs.update(ctx) { it.copy(screenMode = it.screenMode.next()) } }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphIcon(s.screenMode.glyph(), JogColors.Muted, Modifier.size(18.dp))
        androidx.compose.foundation.layout.Spacer(Modifier.size(4.dp))
        Text(s.screenMode.label, color = JogColors.Muted, style = MaterialTheme.typography.labelMedium)
    }
}
