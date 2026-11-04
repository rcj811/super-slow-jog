package com.rcj811.slowjog.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

/**
 * 側面跑者動畫。[beatPhase] 以「拍」為單位的連續相位：
 * 每一拍落一隻腳，兩拍為一個完整步態週期。
 * 超慢跑步幅小、身體直立，所以擺幅刻意做得小而輕快。
 */
@Composable
fun Runner(beatPhase: Double, moving: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) { drawRunner(beatPhase, moving) }
}

private fun rad(deg: Double) = deg * PI / 180.0

fun DrawScope.drawRunner(beatPhase: Double, moving: Boolean) {
    val w = size.width
    val h = size.height
    val unit = h / 10f // 身高約 7.5 unit
    val theta = PI * beatPhase
    val stride = if (moving) 1.0 else 0.0

    // 落地瞬間身體最低，騰空時略高
    val bounce = (abs(sin(theta)) * unit * 0.28f * stride).toFloat()
    val groundY = h * 0.9f
    val hipX = w * 0.5f
    val hipY = groundY - unit * 3.55f - bounce
    val lean = rad(6.0) // 微微前傾
    val torsoLen = unit * 2.4f
    val shoulder = Offset(hipX + (sin(lean) * torsoLen).toFloat(), hipY - (cos(lean) * torsoLen).toFloat())
    val headR = unit * 0.62f
    val head = Offset(shoulder.x + unit * 0.22f, shoulder.y - headR * 1.35f)

    // 地面：往後捲動的虛線，速度隨步頻
    val dash = unit * 0.9f
    val gap = unit * 0.7f
    val period = dash + gap
    val scroll = ((beatPhase * unit * 1.1) % period).toFloat()
    var x = -scroll
    while (x < w) {
        val a = 1f - abs((x + dash / 2) / w - 0.5f) * 1.8f
        if (a > 0f) drawLine(
            JogColors.Muted.copy(alpha = 0.45f * a),
            Offset(max(0f, x), groundY), Offset(minOf(w, x + dash), groundY),
            strokeWidth = unit * 0.12f, cap = StrokeCap.Round,
        )
        x += period
    }
    // 影子
    drawOval(
        Color.Black.copy(alpha = 0.35f),
        topLeft = Offset(hipX - unit * 1.3f, groundY - unit * 0.12f),
        size = androidx.compose.ui.geometry.Size(unit * 2.6f, unit * 0.32f),
    )

    val far = JogColors.Sun3.copy(alpha = 0.55f)
    val nearBrush = Brush.verticalGradient(listOf(JogColors.Sun1, JogColors.Sun2), startY = head.y, endY = groundY)
    val limb = unit * 0.5f

    fun leg(phi: Double, color: Color?, brush: Brush?) {
        val thigh = unit * 1.75f
        val shin = unit * 1.8f
        val a = rad(24.0) * sin(phi) * stride // 大腿前後擺
        val knee = rad(12.0) + rad(58.0) * max(0.0, cos(phi)).pow(1.4) * stride // 擺盪期屈膝
        val kx = hipX + (sin(a) * thigh).toFloat()
        val ky = hipY + (cos(a) * thigh).toFloat()
        val sa = a - knee
        val fx = kx + (sin(sa) * shin).toFloat()
        val fy = minOf(groundY - limb * 0.4f, ky + (cos(sa) * shin).toFloat())
        val k = Offset(kx, ky)
        val f = Offset(fx, fy)
        val hip = Offset(hipX, hipY)
        if (brush != null) {
            drawLine(brush, hip, k, limb, StrokeCap.Round); drawLine(brush, k, f, limb, StrokeCap.Round)
            drawLine(brush, f, Offset(f.x + unit * 0.45f, f.y + unit * 0.05f), limb * 0.8f, StrokeCap.Round)
        } else if (color != null) {
            drawLine(color, hip, k, limb, StrokeCap.Round); drawLine(color, k, f, limb, StrokeCap.Round)
            drawLine(color, f, Offset(f.x + unit * 0.45f, f.y + unit * 0.05f), limb * 0.8f, StrokeCap.Round)
        }
    }

    fun arm(phi: Double, color: Color?, brush: Brush?) {
        val upper = unit * 1.25f
        val fore = unit * 1.15f
        val a = -rad(30.0) * sin(phi) * stride - rad(4.0)
        val ex = shoulder.x + (sin(a) * upper).toFloat()
        val ey = shoulder.y + (cos(a) * upper).toFloat()
        val fa = a + rad(85.0) // 手肘彎曲約 90°
        val hx = ex + (sin(fa) * fore).toFloat()
        val hy = ey + (cos(fa) * fore).toFloat() - unit * 0.1f
        val e = Offset(ex, ey)
        val hand = Offset(hx, hy)
        if (brush != null) {
            drawLine(brush, shoulder, e, limb * 0.85f, StrokeCap.Round); drawLine(brush, e, hand, limb * 0.85f, StrokeCap.Round)
        } else if (color != null) {
            drawLine(color, shoulder, e, limb * 0.85f, StrokeCap.Round); drawLine(color, e, hand, limb * 0.85f, StrokeCap.Round)
        }
    }

    // 遠側（較暗）先畫
    leg(theta + PI, far, null)
    arm(theta + PI, far, null)
    // 軀幹與頭
    drawLine(nearBrush, Offset(hipX, hipY), shoulder, limb * 1.25f, StrokeCap.Round)
    drawCircle(nearBrush, headR, head)
    // 微笑（超慢跑的「笑著跑」）
    drawArc(
        Color(0xFF1A0E08), 20f, 120f, false,
        topLeft = Offset(head.x - headR * 0.15f, head.y - headR * 0.05f),
        size = androidx.compose.ui.geometry.Size(headR * 0.75f, headR * 0.55f),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = headR * 0.14f, cap = StrokeCap.Round),
    )
    // 近側
    leg(theta, null, nearBrush)
    arm(theta, null, nearBrush)
}

/** 每拍的脈衝：拍點 = 1，隨後指數衰減 */
fun beatPulse(beatPhase: Double, sharp: Double = 5.0): Float {
    if (beatPhase <= 0.0) return 0f
    val frac = beatPhase - floor(beatPhase)
    return exp(-frac * sharp).toFloat()
}
