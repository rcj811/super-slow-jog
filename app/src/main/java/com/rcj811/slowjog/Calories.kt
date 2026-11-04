package com.rcj811.slowjog

/**
 * 卡路里估算：kcal = METs × 體重(kg) × 時間(小時)。
 *
 * 超慢跑約在 4–6 METs 之間（與慢走～慢速慢跑相當），步頻越快強度越高。
 * 這裡以步頻線性內插：150 步/分 ≈ 3.5 METs、180 ≈ 4.4、250 ≈ 6.5。
 * 只是估算，實際消耗依個人體能、步幅而異。
 */
object Calories {
    fun mets(cadence: Int): Double {
        val c = cadence.coerceIn(Settings.MIN_CADENCE, Settings.MAX_CADENCE)
        return 3.5 + (c - 150) * 0.03
    }

    /** 指定步頻、體重下，跑 [millis] 毫秒消耗的大卡。 */
    fun kcal(cadence: Int, weightKg: Int, millis: Long): Double =
        mets(cadence) * weightKg * (millis / 3_600_000.0)
}
