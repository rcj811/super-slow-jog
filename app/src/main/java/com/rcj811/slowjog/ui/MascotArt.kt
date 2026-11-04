package com.rcj811.slowjog.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rcj811.slowjog.AvatarStore
import com.rcj811.slowjog.Mascot
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 原地超慢跑角色（正面視角）。
 * 所有角色畫在 100×100 的虛擬座標裡，地面在 y = 93。
 * 每一拍落一隻腳；兩拍為一個完整步態週期。
 */

/** 每拍的脈衝：拍點 = 1，隨後指數衰減 */
fun beatPulse(beatPhase: Double, sharp: Double = 5.0): Float {
    if (beatPhase <= 0.0) return 0f
    val frac = beatPhase - floor(beatPhase)
    return exp(-frac * sharp).toFloat()
}

class JogPose(
    val bounce: Float,  // 身體離地高度（0..1，歡呼時可到 2）
    val liftL: Float,   // 左腳抬起 0..1
    val liftR: Float,   // 右腳抬起 0..1
    val squash: Float,  // 落地壓縮 0..1
    val swing: Float,   // 擺臂 -1..1（正 = 左手往前）
    val wobble: Float,  // 次要擺動（耳朵、尾巴、辮子），比身體慢半拍
    val armsUp: Float,  // 歡呼舉手 0..1
    val wave: Float,    // 歡呼揮手
)

fun jogPose(beatPhase: Double, moving: Boolean, t: Float, celebrate: Boolean): JogPose {
    if (celebrate) {
        val c = t * 1.4
        val f = c - floor(c)
        val j = sin(PI * f).toFloat()
        return JogPose(j * 2f, j * 0.5f, j * 0.5f, (exp(-f * 8) * 0.8).toFloat(), 0f,
            sin(2 * PI * c - 1.2).toFloat(), 1f, sin(t * 9.0).toFloat())
    }
    if (!moving) {
        val b = 0.5f + 0.5f * sin(t * 2.4f)
        return JogPose(0.10f * b, 0f, 0f, 0f, 0.06f * sin(t * 1.2f), 0.35f * sin(t * 2.4f - 1f), 0f, 0f)
    }
    val th = PI * beatPhase
    val s = sin(th)
    val frac = beatPhase - floor(beatPhase)
    return JogPose(
        bounce = abs(s).toFloat(),
        liftL = max(0.0, -s).pow(0.85).toFloat(),
        liftR = max(0.0, s).pow(0.85).toFloat(),
        squash = exp(-frac * 9).toFloat(),
        swing = s.toFloat(),
        wobble = sin(2 * th - 1.3).toFloat(),
        armsUp = 0f,
        wave = 0f,
    )
}

/**
 * 會原地跑步的角色。
 * [phaseSource] 回傳目前節拍相位（例如跑步中的實際節拍）；回傳 null 時改用 [cadence] 自行計時。
 */
@Composable
fun MascotView(
    mascot: Mascot,
    modifier: Modifier = Modifier,
    moving: Boolean = true,
    cadence: Int = 180,
    celebrate: Boolean = false,
    phaseSource: (() -> Double?)? = null,
) {
    val avatar by AvatarStore.image.collectAsStateWithLifecycle()
    var t by remember { mutableFloatStateOf(0f) }
    var localPhase by remember { mutableDoubleStateOf(0.0) }
    val cad by rememberUpdatedState(cadence)
    LaunchedEffect(Unit) {
        var last = withFrameNanos { it }
        val start = last
        while (true) {
            withFrameNanos { now ->
                val dt = (now - last) / 1e9
                last = now
                t = ((now - start) / 1e9).toFloat()
                localPhase += dt * cad / 60.0
            }
        }
    }
    Canvas(modifier) {
        val time = t
        val ph = phaseSource?.invoke() ?: localPhase
        drawMascot(mascot, jogPose(ph, moving, time, celebrate), time, avatar)
    }
}

// ---------------- 繪圖工具 ----------------

private const val GROUND = 93f

private val DARK = Color(0xFF2B2320)
private val WHITE = Color.White
private val BLUSH = Color(0xFFFF7A8A).copy(alpha = 0.45f)
private val MOUTH = Color(0xFF8A2E3B)
private val TONGUE = Color(0xFFFF8FA3)

private class Pen(val ds: DrawScope, val u: Float, val ox: Float, val oy: Float) {
    fun o(x: Float, y: Float) = Offset(ox + x * u, oy + y * u)

    fun circle(col: Color, x: Float, y: Float, r: Float) = ds.drawCircle(col, r * u, o(x, y))

    fun oval(col: Color, cx: Float, cy: Float, rx: Float, ry: Float) =
        ds.drawOval(col, o(cx - rx, cy - ry), Size(2 * rx * u, 2 * ry * u))

    fun line(col: Color, x1: Float, y1: Float, x2: Float, y2: Float, w: Float) =
        ds.drawLine(col, o(x1, y1), o(x2, y2), strokeWidth = w * u, cap = StrokeCap.Round)

    fun rrect(col: Color, x: Float, y: Float, w: Float, h: Float, r: Float) =
        ds.drawRoundRect(col, o(x, y), Size(w * u, h * u), CornerRadius(r * u))

    inner class PB(val path: Path) {
        fun m(x: Float, y: Float) { val p = o(x, y); path.moveTo(p.x, p.y) }
        fun l(x: Float, y: Float) { val p = o(x, y); path.lineTo(p.x, p.y) }
        fun cu(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
            val a = o(x1, y1); val b = o(x2, y2); val c = o(x3, y3)
            path.cubicTo(a.x, a.y, b.x, b.y, c.x, c.y)
        }
        fun z() = path.close()
    }

    fun shape(col: Color, build: PB.() -> Unit) {
        val p = Path(); PB(p).build(); ds.drawPath(p, col)
    }

    fun stroke(col: Color, w: Float, build: PB.() -> Unit) {
        val p = Path(); PB(p).build()
        ds.drawPath(p, col, style = Stroke(w * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }

    /** 與 Compose drawArc 相同：角度、0° = 三點鐘方向、順時針 */
    fun arc(col: Color, cx: Float, cy: Float, rx: Float, ry: Float, start: Float, sweep: Float, w: Float) =
        ds.drawArc(col, start, sweep, false, o(cx - rx, cy - ry), Size(2 * rx * u, 2 * ry * u),
            style = Stroke(w * u, cap = StrokeCap.Round))

    fun rotated(deg: Float, px: Float, py: Float, block: () -> Unit) {
        ds.rotate(deg, o(px, py)) { block() }
    }
}

private fun blinkAt(t: Float, offset: Float) = ((t + offset) % 3.4f) < 0.13f

private fun talking(ps: JogPose) = ps.bounce > 0.2f || ps.armsUp > 0f

private fun Pen.shadow(ps: JogPose, w: Float = 20f) {
    val k = 1f - 0.18f * min(ps.bounce, 2f)
    oval(Color.Black.copy(alpha = 0.30f * k), 50f, 94.6f, w * k, 2.8f * k)
}

private fun Pen.legs(
    ps: JogPose, dy: Float, hipY: Float, xl: Float, xr: Float, col: Color, width: Float,
    foot: (x: Float, y: Float, k: Float, side: Float) -> Unit,
) {
    val base = 91.2f + dy * 0.3f
    for (i in 0..1) {
        val x = if (i == 0) xl else xr
        val lift = if (i == 0) ps.liftL else ps.liftR
        val side = if (i == 0) -1f else 1f
        val fy = base - lift * 8.5f
        val fx = x - side * lift * 0.8f
        val hy = hipY + dy
        val ky = hy + (fy - hy) * (0.52f - 0.22f * lift)
        val kx = (x + fx) / 2f + side * lift * 1.6f
        line(col, x, hy, kx, ky, width)
        line(col, kx, ky, fx, fy, width)
        foot(fx, fy, 1f + 0.16f * lift, side)
    }
}

private fun Pen.arm(
    ps: JogPose, sx: Float, sy: Float, side: Float, col: Color, width: Float,
    hand: (x: Float, y: Float) -> Unit, reach: Float = 1f,
) {
    val fwd = ps.swing * (-side)
    val up = ps.armsUp
    var ex = sx + side * 4.5f * reach
    var ey = sy + 6.5f * reach - fwd * 1.2f
    var hx = sx + side * (2.4f - fwd * 1.4f) * reach
    var hy = sy + 10f * reach - fwd * 5.5f
    val ex2 = sx + side * 5.5f * reach
    val ey2 = sy - 4.5f * reach
    val hx2 = sx + side * (8f + 1.2f * ps.wave) * reach
    val hy2 = sy - 11.5f * reach
    ex += (ex2 - ex) * up; ey += (ey2 - ey) * up
    hx += (hx2 - hx) * up; hy += (hy2 - hy) * up
    line(col, sx, sy, ex, ey, width)
    line(col, ex, ey, hx, hy, width)
    hand(hx, hy)
}

private fun Pen.eyes(lx: Float, rx: Float, y: Float, erx: Float, ery: Float, blink: Boolean, col: Color = DARK) {
    for (x in floatArrayOf(lx, rx)) {
        if (blink) {
            arc(col, x, y + ery * 0.2f, erx * 1.15f, ery * 0.6f, 200f, 140f, 1f)
        } else {
            oval(col, x, y, erx, ery)
            circle(WHITE, x + erx * 0.35f, y - ery * 0.38f, erx * 0.42f)
        }
    }
}

private fun Pen.blush(lx: Float, rx: Float, y: Float, rxx: Float = 3f, ryy: Float = 1.8f) {
    oval(BLUSH, lx, y, rxx, ryy); oval(BLUSH, rx, y, rxx, ryy)
}

private fun Pen.mouth(x: Float, y: Float, w: Float, open: Float) {
    if (open > 0.05f) {
        val h = w * (0.7f + 0.6f * min(open, 1f))
        shape(MOUTH) { m(x - w, y); cu(x - w, y + h * 1.3f, x + w, y + h * 1.3f, x + w, y); z() }
        oval(TONGUE, x, y + h * 0.75f, w * 0.55f, h * 0.32f)
    } else {
        arc(MOUTH, x, y - w * 0.35f, w, w * 0.75f, 25f, 130f, 1.1f)
    }
}

private fun Pen.sneaker(col: Color, accent: Color): (Float, Float, Float, Float) -> Unit = { x, y, k, side ->
    oval(col, x + side * 0.6f, y + 0.6f, 4.6f * k, 2.9f * k)
    line(accent, x - 3.4f * k + side * 0.6f, y + 2.1f * k, x + 3.4f * k + side * 0.6f, y + 2.1f * k, 1.1f * k)
}

private fun Pen.paw(col: Color): (Float, Float, Float, Float) -> Unit = { x, y, k, _ ->
    oval(col, x, y + 0.8f, 4f * k, 2.8f * k)
}

private fun Pen.ball(col: Color, r: Float): (Float, Float) -> Unit = { x, y -> circle(col, x, y, r) }

// ---------------- 入口 ----------------

fun DrawScope.drawMascot(mascot: Mascot, ps: JogPose, t: Float, avatar: ImageBitmap?) {
    val u = size.minDimension / 100f
    val p = Pen(this, u, (size.width - 100f * u) / 2f, (size.height - 100f * u) / 2f)
    val sq = ps.squash
    withTransform({ scale(1f + 0.05f * sq, 1f - 0.06f * sq, pivot = p.o(50f, GROUND)) }) {
        when (mascot) {
            Mascot.BOY -> p.kid(ps, t, girl = false)
            Mascot.GIRL -> p.kid(ps, t, girl = true)
            Mascot.DUCK -> p.duck(ps, t)
            Mascot.DOG -> p.dog(ps, t)
            Mascot.CAT -> p.cat(ps, t)
            Mascot.CAPYBARA -> p.capybara(ps, t)
            Mascot.RABBIT -> p.rabbit(ps, t)
            Mascot.PANDA -> p.panda(ps, t)
            Mascot.PHOTO -> p.photo(ps, avatar)
        }
    }
}

// ---------------- 角色 ----------------

private fun Pen.kid(ps: JogPose, t: Float, girl: Boolean) {
    val dy = -ps.bounce * 4f
    val skin = Color(0xFFFFD9B8)
    val hair = if (girl) Color(0xFF6B3E26) else Color(0xFF3E2A20)
    val shirt = if (girl) Color(0xFFFF8FC0) else Color(0xFFFF8A5B)
    val bottom = if (girl) Color(0xFF7C5CFF) else Color(0xFF3D5A80)
    val accent = if (girl) Color(0xFFFF5E9A) else Color(0xFFFF5E62)
    shadow(ps)
    legs(ps, dy, 72f, 44.5f, 55.5f, skin, 4.6f, sneaker(WHITE, accent))
    if (girl) {
        shape(bottom) {
            m(39f, 64f + dy); l(61f, 64f + dy); l(65f, 76f + dy)
            cu(58f, 78.5f + dy, 42f, 78.5f + dy, 35f, 76f + dy); z()
        }
    } else {
        rrect(bottom, 38.5f, 65f + dy, 23f, 10f, 4f)
    }
    rrect(shirt, 37f, 50f + dy, 26f, 18f, 9f)
    if (girl) circle(WHITE.copy(alpha = 0.7f), 50f, 57f + dy, 2.2f)
    else line(WHITE.copy(alpha = 0.55f), 38.5f, 59f + dy, 61.5f, 59f + dy, 2f)
    for (side in floatArrayOf(-1f, 1f)) {
        val sx = 50f + side * 10.5f
        arm(ps, sx, 54f + dy, side, skin, 3.9f, ball(skin, 2.6f))
        circle(shirt, sx, 53.5f + dy, 3.6f)
    }
    val hy = 31f + dy + ps.wobble * 0.5f
    if (girl) {
        for (side in floatArrayOf(-1f, 1f)) {
            rotated(side * (12f + ps.wobble * 14f), 50f + side * 17f, hy - 6f) {
                oval(hair, 50f + side * 22f, hy + 1f, 5.2f, 7.5f)
            }
            circle(Color(0xFFFF5E9A), 50f + side * 18.5f, hy - 5f, 2.2f)
        }
    }
    circle(hair, 50f, hy - 1.5f, 19.5f)
    oval(skin, 50f, hy + 3f, 17f, 15.5f)
    circle(skin, 31.5f, hy + 3f, 3.4f); circle(skin, 68.5f, hy + 3f, 3.4f)
    shape(hair) {
        m(32f, hy - 2f); cu(34f, hy - 16f, 66f, hy - 16f, 68f, hy - 2f)
        cu(63f, hy - 6f, 58f, hy - 9f, 54f, hy - 6f)
        cu(50f, hy - 9f, 45f, hy - 8f, 41f, hy - 4f)
        cu(38f, hy - 6f, 35f, hy - 5f, 32f, hy - 2f); z()
    }
    if (!girl) stroke(hair, 1.6f) { m(50f, hy - 20f); cu(48f, hy - 25f, 53f, hy - 27f, 55f, hy - 24f) }
    eyes(43.5f, 56.5f, hy + 3f, 2f, 2.6f, blinkAt(t, if (girl) 1.1f else 0f))
    blush(38.5f, 61.5f, hy + 8f)
    mouth(50f, hy + 9.5f, 2.6f, if (talking(ps)) 0.6f + 0.4f * ps.bounce else 0f)
}

private fun Pen.duck(ps: JogPose, t: Float) {
    val dy = -ps.bounce * 4f
    val yellow = Color(0xFFFFD54A); val light = Color(0xFFFFE88A)
    val orange = Color(0xFFFF9F1C); val dOrange = Color(0xFFE5830F)
    shadow(ps, 21f)
    legs(ps, dy, 79f, 44f, 56f, orange, 2.4f) { x, y, k, _ ->
        shape(orange) {
            m(x - 5f * k, y + 2f * k)
            cu(x - 4f * k, y - 2.5f * k, x + 4f * k, y - 2.5f * k, x + 5f * k, y + 2f * k)
            cu(x + 2f * k, y + 1f * k, x - 2f * k, y + 1f * k, x - 5f * k, y + 2f * k); z()
        }
    }
    for (side in floatArrayOf(-1f, 1f)) {
        val fwd = ps.swing * (-side)
        val ang = side * (-18f - fwd * 22f) - side * ps.armsUp * 95f
        rotated(ang, 50f + side * 16f, 58f + dy) {
            oval(yellow, 50f + side * 20f, 64f + dy, 5f, 9f)
            oval(light, 50f + side * 20f, 66f + dy, 3f, 5.5f)
        }
    }
    oval(yellow, 50f, 65f + dy, 19f, 16f)
    oval(light, 50f, 69f + dy, 12f, 9.5f)
    val hy = 37f + dy + ps.wobble * 0.6f
    circle(yellow, 50f, hy, 16.5f)
    stroke(yellow, 2.2f) { m(49f, hy - 15f); cu(46f, hy - 22f, 52f, hy - 24f, 53f, hy - 19f) }
    stroke(yellow, 1.8f) { m(51f, hy - 15.5f); cu(54f, hy - 21f, 58f, hy - 20f, 57f, hy - 17f) }
    eyes(43.5f, 56.5f, hy - 2f, 1.9f, 2.5f, blinkAt(t, 0.7f))
    blush(38f, 62f, hy + 4f, 2.8f, 1.7f)
    val op = if (talking(ps)) 0.5f + 0.5f * min(ps.bounce, 1f) else 0.1f
    oval(dOrange, 50f, hy + 6.5f + op * 1.6f, 5.2f, 2.2f + op * 0.8f)
    oval(orange, 50f, hy + 4.6f, 7f, 2.9f)
    circle(WHITE.copy(alpha = 0.5f), 47.5f, hy + 3.8f, 0.9f)
}

private fun Pen.dog(ps: JogPose, t: Float) {
    val dy = -ps.bounce * 4f
    val fur = Color(0xFFD9A066); val light = Color(0xFFFFF0DA); val ear = Color(0xFF9C6638)
    shadow(ps, 19f)
    rotated(ps.wobble * 28f - 10f, 62f, 70f + dy) {
        stroke(fur, 4f) { m(62f, 70f + dy); cu(68f, 68f + dy, 71f, 63f + dy, 70f, 57f + dy) }
        circle(light, 70f, 57f + dy, 2.2f)
    }
    legs(ps, dy, 78f, 44f, 56f, fur, 5.6f, paw(light))
    oval(fur, 50f, 66f + dy, 14.5f, 13f)
    oval(light, 50f, 69f + dy, 8.5f, 8.5f)
    for (side in floatArrayOf(-1f, 1f)) arm(ps, 50f + side * 11f, 60f + dy, side, fur, 4.6f, ball(light, 2.7f), 0.85f)
    val hy = 35f + dy + ps.wobble * 0.6f
    circle(fur, 50f, hy, 18f)
    oval(light, 50f, hy + 7f, 10f, 7.5f)
    oval(light, 50f, hy - 9f, 3.5f, 5f)
    for (side in floatArrayOf(-1f, 1f)) {
        rotated(side * (8f + ps.wobble * 13f), 50f + side * 13f, hy - 12f) {
            oval(ear, 50f + side * 17.5f, hy - 1f, 5.5f, 11f)
        }
    }
    eyes(42.5f, 57.5f, hy - 1f, 2f, 2.6f, blinkAt(t, 1.4f))
    blush(37.5f, 62.5f, hy + 5f)
    oval(DARK, 50f, hy + 4.2f, 3.2f, 2.3f)
    circle(WHITE.copy(alpha = 0.6f), 49f, hy + 3.5f, 0.8f)
    stroke(DARK, 0.9f) { m(50f, hy + 6.3f); l(50f, hy + 8f); cu(49f, hy + 10f, 46.5f, hy + 10f, 46f, hy + 8.5f) }
    stroke(DARK, 0.9f) { m(50f, hy + 8f); cu(51f, hy + 10f, 53.5f, hy + 10f, 54f, hy + 8.5f) }
    if (talking(ps)) {
        val b = min(ps.bounce, 1f)
        oval(TONGUE, 50f, hy + 10.8f + b * 0.8f, 2.4f, 2.8f + b * 0.6f)
        line(Color(0xFFE0637C), 50f, hy + 9.6f, 50f, hy + 12f, 0.6f)
    }
}

private fun Pen.cat(ps: JogPose, t: Float) {
    val dy = -ps.bounce * 4f
    val fur = Color(0xFFF4A259); val light = Color(0xFFFFEBD3)
    val stripe = Color(0xFFD27D2C); val pink = Color(0xFFFFB3C1)
    shadow(ps, 19f)
    val sw = ps.wobble * 6f
    stroke(fur, 3.6f) { m(62f, 74f + dy); cu(72f, 72f + dy, 70f + sw, 62f + dy, 74f + sw * 1.4f, 52f + dy) }
    circle(stripe, 74f + sw * 1.4f, 52f + dy, 2f)
    legs(ps, dy, 78f, 44f, 56f, fur, 5.4f, paw(light))
    oval(fur, 50f, 66f + dy, 14f, 13f)
    oval(light, 50f, 69f + dy, 8f, 8.5f)
    for (side in floatArrayOf(-1f, 1f)) arm(ps, 50f + side * 11f, 60f + dy, side, fur, 4.4f, ball(light, 2.6f), 0.85f)
    val hy = 36f + dy + ps.wobble * 0.6f
    for (side in floatArrayOf(-1f, 1f)) {
        rotated(side * ps.wobble * 4f, 50f + side * 11f, hy - 10f) {
            shape(fur) { m(50f + side * 5f, hy - 12f); l(50f + side * 15f, hy - 26f); l(50f + side * 19f, hy - 7f); z() }
            shape(pink) { m(50f + side * 8.5f, hy - 12f); l(50f + side * 14.5f, hy - 21.5f); l(50f + side * 16.5f, hy - 10f); z() }
        }
    }
    oval(fur, 50f, hy, 19.5f, 16.5f)
    line(stripe, 46f, hy - 15f, 46f, hy - 11f, 1.6f)
    line(stripe, 50f, hy - 15f, 50f, hy - 10f, 1.6f)
    line(stripe, 54f, hy - 15f, 54f, hy - 11f, 1.6f)
    oval(light, 50f, hy + 6f, 9f, 6f)
    eyes(42.5f, 57.5f, hy, 2.2f, 2.9f, blinkAt(t, 2.1f))
    blush(37f, 63f, hy + 5f)
    shape(pink) { m(48.2f, hy + 3.4f); l(51.8f, hy + 3.4f); l(50f, hy + 5.4f); z() }
    stroke(DARK, 0.9f) { m(50f, hy + 5.4f); cu(49.5f, hy + 7.6f, 47f, hy + 7.6f, 46.5f, hy + 6f) }
    stroke(DARK, 0.9f) { m(50f, hy + 5.4f); cu(50.5f, hy + 7.6f, 53f, hy + 7.6f, 53.5f, hy + 6f) }
    val whisker = WHITE.copy(alpha = 0.8f)
    for (side in floatArrayOf(-1f, 1f)) {
        for (yy in floatArrayOf(-1.2f, 0.6f, 2.4f)) {
            line(whisker, 50f + side * 9f, hy + 4f + yy * 0.6f, 50f + side * 17f, hy + 3f + yy * 1.3f, 0.55f)
        }
    }
}

private fun Pen.capybara(ps: JogPose, t: Float) {
    val dy = -ps.bounce * 4f
    val fur = Color(0xFFA67B5B); val dark = Color(0xFF7A5438)
    val snout = Color(0xFFB68C6B); val nose = Color(0xFF3B2A20); val nostril = Color(0xFF1E1510)
    shadow(ps, 21f)
    legs(ps, dy, 80f, 42.5f, 57.5f, dark, 6.6f, paw(dark))
    rrect(fur, 32f, 50f + dy, 36f, 32f, 14f)
    oval(Color(0xFFB98E6C), 50f, 69f + dy, 10f, 9f)
    for (side in floatArrayOf(-1f, 1f)) arm(ps, 50f + side * 14f, 59f + dy, side, fur, 5f, ball(dark, 2.6f), 0.8f)
    val hy = 35f + dy + ps.wobble * 0.5f
    oval(dark, 35.5f, hy - 15f, 3.4f, 2.8f); oval(dark, 64.5f, hy - 15f, 3.4f, 2.8f)
    rrect(fur, 31f, hy - 18f, 38f, 34f, 14f)
    rrect(snout, 37f, hy - 1f, 26f, 16f, 8f)
    rrect(nose, 43f, hy + 1f, 14f, 4.2f, 2.1f)
    oval(nostril, 46.2f, hy + 3.1f, 1.3f, 0.9f); oval(nostril, 53.8f, hy + 3.1f, 1.3f, 0.9f)
    line(nose, 50f, hy + 5.2f, 50f, hy + 8.5f, 0.9f)
    stroke(nose, 0.9f) { m(46.5f, hy + 9.5f); cu(48f, hy + 11f, 52f, hy + 11f, 53.5f, hy + 9.5f) }
    val blink = blinkAt(t, 2.8f)
    for (x in floatArrayOf(39.5f, 60.5f)) {
        if (blink) line(DARK, x - 2f, hy - 6f, x + 2f, hy - 6f, 1f)
        else { circle(DARK, x, hy - 6f, 1.8f); circle(WHITE, x + 0.6f, hy - 6.6f, 0.6f) }
    }
    blush(35.5f, 64.5f, hy, 2.6f, 1.5f)
    // 頭上的柚子（比身體慢半拍）
    val yy = hy - 23f - ps.wobble * 1.4f
    circle(Color(0xFFFFB627), 50f, yy, 6f)
    circle(Color(0xFFFFD36B), 48f, yy - 2f, 1.8f)
    shape(Color(0xFF5DBB63)) {
        m(50f, yy - 5.5f); cu(52f, yy - 10f, 57f, yy - 9f, 57f, yy - 8f)
        cu(55f, yy - 6f, 52f, yy - 5.5f, 50f, yy - 5.5f); z()
    }
}

private fun Pen.rabbit(ps: JogPose, t: Float) {
    val dy = -ps.bounce * 4f
    val fur = Color(0xFFF7F3EC); val shade = Color(0xFFE4DCD0)
    val pink = Color(0xFFFFB7C9); val noseC = Color(0xFFFF8FAB)
    shadow(ps, 19f)
    legs(ps, dy, 78f, 44f, 56f, fur, 5.6f) { x, y, k, _ ->
        oval(fur, x, y + 0.6f, 4.8f * k, 3f * k); oval(pink, x, y + 1f, 1.6f * k, 1.1f * k)
    }
    circle(fur, 64f, 73f + dy, 4f)
    oval(fur, 50f, 66f + dy, 14f, 13f)
    oval(shade, 50f, 69f + dy, 8f, 8f)
    for (side in floatArrayOf(-1f, 1f)) arm(ps, 50f + side * 11f, 60f + dy, side, fur, 4.4f, ball(fur, 2.6f), 0.85f)
    val hy = 37f + dy + ps.wobble * 0.6f
    for (side in floatArrayOf(-1f, 1f)) {
        rotated(side * (6f + ps.wobble * 9f), 50f + side * 7f, hy - 12f) {
            oval(fur, 50f + side * 8f, hy - 25f, 5.2f, 13.5f)
            oval(pink, 50f + side * 8f, hy - 24f, 2.6f, 10f)
        }
    }
    circle(fur, 50f, hy, 17f)
    eyes(43f, 57f, hy - 1f, 2f, 2.6f, blinkAt(t, 0.3f))
    blush(38f, 62f, hy + 4.5f)
    oval(noseC, 50f, hy + 3.2f, 1.9f, 1.3f)
    stroke(DARK, 0.9f) { m(50f, hy + 4.4f); cu(49.5f, hy + 6.4f, 47.2f, hy + 6.4f, 46.8f, hy + 5f) }
    stroke(DARK, 0.9f) { m(50f, hy + 4.4f); cu(50.5f, hy + 6.4f, 52.8f, hy + 6.4f, 53.2f, hy + 5f) }
    rrect(WHITE, 48.2f, hy + 5.2f, 3.6f, 3.2f, 0.8f)
    line(shade, 50f, hy + 5.4f, 50f, hy + 8.2f, 0.4f)
}

private fun Pen.panda(ps: JogPose, t: Float) {
    val dy = -ps.bounce * 4f
    val white = Color(0xFFF6F6F1); val black = Color(0xFF2A2A2E)
    shadow(ps, 20f)
    legs(ps, dy, 78f, 43.5f, 56.5f, black, 6.4f, paw(black))
    oval(white, 50f, 66f + dy, 15f, 13.5f)
    shape(black) {
        m(35.5f, 62f + dy); cu(40f, 55f + dy, 60f, 55f + dy, 64.5f, 62f + dy)
        cu(60f, 59f + dy, 40f, 59f + dy, 35.5f, 62f + dy); z()
    }
    for (side in floatArrayOf(-1f, 1f)) arm(ps, 50f + side * 11.5f, 60f + dy, side, black, 5.2f, ball(black, 3f), 0.85f)
    val hy = 35f + dy + ps.wobble * 0.6f
    circle(black, 35f, hy - 13f, 6.3f); circle(black, 65f, hy - 13f, 6.3f)
    circle(white, 50f, hy, 18.5f)
    for (side in floatArrayOf(-1f, 1f)) {
        rotated(side * 28f, 50f + side * 7.5f, hy) { oval(black, 50f + side * 7.5f, hy, 4.6f, 6.2f) }
    }
    val blink = blinkAt(t, 1.9f)
    if (blink) {
        eyes(42.5f, 57.5f, hy - 0.5f, 1.5f, 1.9f, true, WHITE)
    } else {
        for (x in floatArrayOf(42.5f, 57.5f)) {
            circle(WHITE, x, hy - 0.5f, 2f); circle(DARK, x + 0.3f, hy - 0.2f, 1.3f); circle(WHITE, x + 0.8f, hy - 1f, 0.45f)
        }
    }
    blush(37f, 63f, hy + 6f, 2.6f, 1.6f)
    oval(black, 50f, hy + 5f, 2.6f, 1.8f)
    mouth(50f, hy + 8.2f, 2f, if (talking(ps)) 0.7f else 0f)
}

/** 用自己的照片當頭 */
private fun Pen.photo(ps: JogPose, avatar: ImageBitmap?) {
    val dy = -ps.bounce * 4f
    val skin = Color(0xFFFFD9B8); val shirt = Color(0xFFFF7A45); val bottom = Color(0xFF2E4057)
    shadow(ps)
    legs(ps, dy, 74f, 44.5f, 55.5f, skin, 4.6f, sneaker(WHITE, shirt))
    rrect(bottom, 38.5f, 67f + dy, 23f, 10f, 4f)
    rrect(shirt, 37f, 52f + dy, 26f, 18f, 9f)
    circle(WHITE.copy(alpha = 0.75f), 50f, 60f + dy, 2.4f)
    for (side in floatArrayOf(-1f, 1f)) {
        val sx = 50f + side * 10.5f
        arm(ps, sx, 56f + dy, side, skin, 3.9f, ball(skin, 2.6f))
        circle(shirt, sx, 55.5f + dy, 3.6f)
    }
    val hy = 30f + dy + ps.wobble * 0.6f
    circle(WHITE, 50f, hy, 20.5f)
    if (avatar != null) {
        val c = o(50f, hy)
        val r = 19f * u
        val clip = Path().apply { addOval(Rect(c, r)) }
        ds.clipPath(clip) {
            drawImage(
                image = avatar,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(avatar.width, avatar.height),
                dstOffset = IntOffset((c.x - r).roundToInt(), (c.y - r).roundToInt()),
                dstSize = IntSize((2 * r).roundToInt(), (2 * r).roundToInt()),
                filterQuality = FilterQuality.Medium,
            )
        }
    } else {
        circle(Color(0xFF27221E), 50f, hy, 19f)
        val plus = Color(0xFFA59B92)
        line(plus, 50f, hy - 6f, 50f, hy + 6f, 2.2f)
        line(plus, 44f, hy, 56f, hy, 2.2f)
    }
}
