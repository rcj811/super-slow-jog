package com.rcj811.slowjog

import java.util.Locale

/**
 * 角色體型：依身高決定腿長（高矮），依 BMI 決定身形寬窄（胖瘦）。
 * widthK：身體寬度倍率（0.78–1.45）；legK：腿長倍率（0.72–1.35）。
 */
data class BodyShape(val widthK: Float, val legK: Float) {
    /** 頭部只跟著變寬一點點（臉會圓一點） */
    val headK: Float get() = 1f + (widthK - 1f) * 0.35f
    /** 手腳粗細 */
    val limbK: Float get() = 1f + (widthK - 1f) * 0.8f
    /** 雙腳間距 */
    val legSpread: Float get() = 1f + (widthK - 1f) * 0.6f

    companion object {
        val NORMAL = BodyShape(1f, 1f)

        fun bmi(heightCm: Int, weightKg: Int): Float {
            val m = heightCm.coerceAtLeast(50) / 100f
            return weightKg / (m * m)
        }

        fun of(heightCm: Int, weightKg: Int): BodyShape {
            val w = (1f + (bmi(heightCm, weightKg) - 22f) * 0.045f).coerceIn(0.78f, 1.45f)
            val l = (1f + (heightCm - 165) * 0.012f).coerceIn(0.72f, 1.35f)
            return BodyShape(w, l)
        }

        fun from(s: Settings): BodyShape = if (s.bodyShapeOn) of(s.heightCm, s.weightKg) else NORMAL

        fun heightWord(heightCm: Int) = when {
            heightCm < 155 -> "嬌小"
            heightCm < 172 -> "中等身高"
            else -> "高挑"
        }

        /** 依衛福部成人 BMI 分級，用較溫和的字眼描述身形 */
        fun buildWord(heightCm: Int, weightKg: Int): String {
            val b = bmi(heightCm, weightKg)
            return when {
                b < 18.5f -> "纖瘦"
                b < 24f -> "標準"
                b < 27f -> "稍微圓潤"
                else -> "圓潤"
            }
        }

        fun describe(heightCm: Int, weightKg: Int): String =
            "${heightWord(heightCm)}・${buildWord(heightCm, weightKg)}身形（BMI " +
                String.format(Locale.US, "%.1f", bmi(heightCm, weightKg)) + "）"
    }
}
