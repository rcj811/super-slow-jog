package com.rcj811.slowjog

import java.util.Locale

object Format {
    /** 秒數 → mm:ss 或 h:mm:ss */
    fun clock(totalSec: Long): String {
        val s = totalSec.coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec)
        else String.format(Locale.US, "%02d:%02d", m, sec)
    }

    /** 分鐘數 → 「1 小時 20 分」 */
    fun minutes(min: Int): String {
        val h = min / 60
        val m = min % 60
        return when {
            h == 0 -> "$m 分鐘"
            m == 0 -> "$h 小時"
            else -> "$h 小時 $m 分"
        }
    }

    fun thousands(v: Long): String = String.format(Locale.US, "%,d", v)

    fun kcal(v: Double): String =
        if (v < 100) String.format(Locale.US, "%.1f", v) else String.format(Locale.US, "%,d", v.toLong())
}
