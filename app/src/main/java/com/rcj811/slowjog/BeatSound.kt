package com.rcj811.slowjog

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** 節拍音色。全部即時合成（模態合成 + 小型殘響），不需要音檔。 */
enum class BeatSound(val label: String, val desc: String) {
    WATER("水滴", "清脆的滴水聲，帶一點山洞回音"),
    WOODFISH("木魚", "溫潤低沉的木頭敲擊"),
    METRONOME("節拍器", "經典俐落的滴答聲"),
    MARIMBA("木琴", "柔和的木琴，左右腳 C / G 交替"),
    KALIMBA("拇指琴", "空靈清亮的拇指琴音"),
    BAMBOO("竹筒", "輕敲竹筒的空心「叩叩」聲"),
    BOWL("頌缽", "悠長的鈴缽，適合低步頻放鬆跑"),
    DRUM("輕鼓", "低沉的咚咚聲，像踏在地上的腳步"),
    CLAP("拍手", "清脆的拍手聲，像有人幫你打拍子"),
    HOOF("馬蹄", "「喀搭喀搭」的馬蹄聲"),
    COWBELL("牛鈴", "鄉間牛鈴，明亮有穿透力"),
    TRIANGLE("三角鐵", "叮—清亮悠長的三角鐵"),
    BUBBLE("泡泡", "可愛的「啵」泡泡聲"),
    CHIRP("啾啾", "小鳥「啾啾」兩聲，輕快可愛"),
    BOING("彈簧", "卡通彈簧「啵～嚶」的下滑音"),
    MARBLE("玻璃彈珠", "玻璃珠互碰的清脆「叮」");

    companion object {
        fun fromId(id: String?): BeatSound = entries.firstOrNull { it.name == id } ?: METRONOME
    }
}

object BeatSynth {
    const val SAMPLE_RATE = 44100

    private data class Key(val sound: BeatSound, val alt: Boolean, val tone: Int)
    private val cache = HashMap<Key, FloatArray>()

    /**
     * 取得音色樣本（-1..1）。[alt] = 另一隻腳的音高。
     * [tone]：音色 -1（柔和）… 0（原音）… +1（尖銳），以 0.1 為一格。
     */
    @Synchronized
    fun get(sound: BeatSound, alt: Boolean, tone: Float = 0f): FloatArray {
        val q = Math.round(tone.coerceIn(-1f, 1f) * 10f)
        return cache.getOrPut(Key(sound, alt, q)) {
            val base = if (q == 0) render(sound, alt) else get(sound, alt, 0f)
            if (q == 0) base else shapeTone(base, q / 10f)
        }
    }

    /**
     * 音色調整：
     * - 柔和：音高降低（最多約半個八度）＋ 低通濾波（越柔和越悶、越圓潤）
     * - 尖銳：音高升高（最多約半個八度）＋ 高頻強調（越尖銳越清脆）
     */
    fun shapeTone(x: FloatArray, t: Float): FloatArray {
        val pitch = Math.pow(2.0, t * 0.6)
        val n = (x.size / pitch).toInt().coerceAtLeast(32)
        val y = FloatArray(n) { i ->
            val pos = i * pitch
            val j = pos.toInt()
            val f = (pos - j).toFloat()
            when {
                j + 1 < x.size -> x[j] * (1 - f) + x[j + 1] * f
                j < x.size -> x[j]
                else -> 0f
            }
        }
        if (t < 0f) {
            // 兩級一階低通：截止頻率由 9 kHz（原音）降到約 600 Hz（最柔和）
            val fc = 9000.0 * Math.pow(600.0 / 9000.0, (-t).toDouble())
            val a = exp(-2 * PI * fc / SAMPLE_RATE).toFloat()
            repeat(2) {
                var prev = 0f
                for (i in y.indices) { prev = (1 - a) * y[i] + a * prev; y[i] = prev }
            }
            // 起音稍微變圓（最多 4 ms 淡入）
            val att = (SAMPLE_RATE * 0.004 * -t).toInt()
            for (i in 0 until min(att, y.size)) y[i] *= i.toFloat() / att
        } else {
            // 高頻強調（一階差分），越尖銳越清脆
            val k = t * 1.8f
            var prev = 0f
            for (i in y.indices) { val v = y[i]; y[i] = v + k * (v - prev); prev = v }
        }
        fadeOut(y, 0.015)
        normalize(y, 0.9f)
        return y
    }

    fun render(sound: BeatSound, alt: Boolean): FloatArray {
        val k = if (alt) 0.84 else 1.0 // 另一隻腳：約低小三度
        val rnd = Random(7)
        val y: FloatArray = when (sound) {
            BeatSound.METRONOME -> {
                val f = 1900.0 * (if (alt) 0.75 else 1.0)
                val x = modal(f, doubleArrayOf(1.0, 0.5), doubleArrayOf(1.0, 0.6), doubleArrayOf(0.006, 0.012), 0.06, attack = 0.0003)
                mixInto(x, noiseBurst(rnd, 0.06, 0.0015, 0.8), 0.25)
                reverb(x, 0.08, 0.05)
            }
            BeatSound.WATER -> {
                val f0 = 780.0 * k
                val x = modal(f0, doubleArrayOf(1.0), doubleArrayOf(1.0), doubleArrayOf(0.028), 0.12, attack = 0.001, glide = -0.55, glideTau = 0.012)
                mixInto(x, modal(f0 * 2.02, doubleArrayOf(1.0), doubleArrayOf(1.0), doubleArrayOf(0.012), 0.12, attack = 0.001, glide = -0.5, glideTau = 0.01), 0.15)
                reverb(x, 0.32, 0.25)
            }
            BeatSound.WOODFISH -> {
                val f0 = 430.0 * k
                val x = modal(f0, doubleArrayOf(1.0, 2.42, 4.4), doubleArrayOf(1.0, 0.35, 0.15), doubleArrayOf(0.085, 0.03, 0.012), 0.3, attack = 0.0008, glide = 0.04, glideTau = 0.02)
                mixInto(x, noiseBurst(rnd, 0.3, 0.003, 0.6), 0.18)
                reverb(x, 0.2, 0.15)
            }
            BeatSound.MARIMBA -> {
                val f0 = if (alt) 392.0 else 523.25
                val x = modal(f0, doubleArrayOf(1.0, 3.93, 9.24), doubleArrayOf(1.0, 0.22, 0.08), doubleArrayOf(0.30, 0.06, 0.015), 0.7, attack = 0.002)
                reverb(x, 0.2, 0.2)
            }
            BeatSound.KALIMBA -> {
                val f0 = if (alt) 493.88 else 659.25
                val x = modal(f0, doubleArrayOf(1.0, 5.93, 2.0), doubleArrayOf(1.0, 0.28, 0.06), doubleArrayOf(0.45, 0.05, 0.12), 0.9, attack = 0.0015)
                reverb(x, 0.22, 0.2)
            }
            BeatSound.BOWL -> {
                val f0 = 880.0 * k
                val x = modal(f0, doubleArrayOf(1.0, 1.004, 2.71, 5.15), doubleArrayOf(0.6, 0.6, 0.35, 0.12), doubleArrayOf(0.55, 0.55, 0.3, 0.12), 0.9, attack = 0.004)
                reverb(x, 0.25, 0.2)
            }
            BeatSound.BAMBOO -> {
                val f0 = 820.0 * k
                val x = modal(f0, doubleArrayOf(1.0, 1.9, 3.1), doubleArrayOf(1.0, 0.3, 0.12), doubleArrayOf(0.045, 0.02, 0.01), 0.15, attack = 0.0006, glide = 0.06, glideTau = 0.01)
                mixInto(x, noiseBurst(rnd, 0.15, 0.002, 0.7), 0.12)
                reverb(x, 0.22, 0.15)
            }
            BeatSound.DRUM -> {
                val f0 = 120.0 * (if (alt) 0.9 else 1.0)
                val x = modal(f0, doubleArrayOf(1.0, 1.6), doubleArrayOf(1.0, 0.25), doubleArrayOf(0.14, 0.06), 0.35, attack = 0.001, glide = 0.9, glideTau = 0.025)
                mixInto(x, noiseBurst(rnd, 0.35, 0.004, 0.5), 0.1)
                reverb(x, 0.12, 0.1)
            }
            BeatSound.CLAP -> {
                val n = (SAMPLE_RATE * 0.25).toInt()
                val x = FloatArray(n)
                doubleArrayOf(0.0, 0.009, 0.019).forEachIndexed { j, d ->
                    val s0 = (d * SAMPLE_RATE).toInt()
                    val b = band(noise(rnd, n - s0), 1300.0 * k, 1.4)
                    applyEnv(b, 0.0005, if (j < 2) 0.006 else 0.03)
                    for (i in b.indices) x[s0 + i] += b[i]
                }
                reverb(x, 0.25, 0.15)
            }
            BeatSound.HOOF -> {
                val click = modal(2200.0 * k, doubleArrayOf(1.0, 1.5), doubleArrayOf(1.0, 0.5), doubleArrayOf(0.006, 0.004), 0.25, attack = 0.0003)
                val x = modal(420.0 * k, doubleArrayOf(1.0, 2.3), doubleArrayOf(1.0, 0.3), doubleArrayOf(0.05, 0.02), 0.25, attack = 0.001)
                mixInto(x, click, 0.6)
                val n2 = band(noise(rnd, x.size), 1800.0, 1.0); applyEnv(n2, 0.0005, 0.01)
                mixInto(x, n2, 0.15)
                reverb(x, 0.15, 0.1)
            }
            BeatSound.COWBELL -> {
                val f = 560.0 * k
                val raw = modal(f, doubleArrayOf(1.0, 1.504, 2.03, 2.78), doubleArrayOf(1.0, 0.8, 0.3, 0.2), doubleArrayOf(0.18, 0.12, 0.05, 0.03), 0.5, attack = 0.0008)
                val x = band(raw, f * 1.3, 0.8)
                reverb(x, 0.18, 0.15)
            }
            BeatSound.TRIANGLE -> {
                val x = modal(2100.0 * k, doubleArrayOf(1.0, 1.35, 2.11, 2.67, 3.4), doubleArrayOf(1.0, 0.5, 0.45, 0.3, 0.2), doubleArrayOf(0.7, 0.5, 0.35, 0.25, 0.15), 0.9, attack = 0.0005)
                reverb(x, 0.2, 0.2)
            }
            BeatSound.BUBBLE -> {
                val n = (SAMPLE_RATE * 0.12).toInt()
                val x = FloatArray(n)
                var ph = 0.0
                for (i in 0 until n) {
                    val t = i.toDouble() / SAMPLE_RATE
                    ph += 2 * PI * 900.0 * k * (1 + 1.2 * t / 0.12) / SAMPLE_RATE
                    x[i] = (sin(ph) * min(1.0, t / 0.003) * exp(-t / 0.03)).toFloat()
                }
                reverb(x, 0.3, 0.2)
            }
            BeatSound.CHIRP -> {
                val x = FloatArray((SAMPLE_RATE * 0.2).toInt())
                for ((j, d) in doubleArrayOf(0.0, 0.065).withIndex()) {
                    val c = sweep(0.05, 0.002, 0.016) { t -> 2600.0 * k * (1 + (0.8 + 0.1 * j) * (1 - exp(-t / 0.02))) }
                    val s0 = (d * SAMPLE_RATE).toInt()
                    val g = if (j == 0) 1f else 0.8f
                    for (i in c.indices) if (s0 + i < x.size) x[s0 + i] += c[i] * g
                }
                reverb(x, 0.25, 0.15)
            }
            BeatSound.BOING -> {
                val f = { t: Double -> 330.0 * k * (1 + 0.8 * exp(-t / 0.03)) * (1 + 0.04 * sin(2 * PI * 25 * t) * exp(-t / 0.15)) }
                val x = sweep(0.35, 0.003, 0.12, f)
                mixInto(x, sweep(0.35, 0.003, 0.06) { t -> 2 * f(t) }, 0.3)
                reverb(x, 0.15, 0.1)
            }
            BeatSound.MARBLE -> {
                val x = modal(1900.0 * k, doubleArrayOf(1.0, 2.76, 5.4), doubleArrayOf(1.0, 0.4, 0.15), doubleArrayOf(0.09, 0.04, 0.02), 0.3, attack = 0.0004)
                reverb(x, 0.22, 0.15)
            }
        }
        fadeOut(y, 0.02)
        normalize(y, 0.9f)
        return y
    }

    // ---- 新音色用的工具 ----

    /** 頻率隨時間變化的正弦波（滑音），附起音與指數衰減 */
    private fun sweep(dur: Double, attack: Double, tau: Double, freq: (Double) -> Double): FloatArray {
        val n = (SAMPLE_RATE * dur).toInt()
        val x = FloatArray(n)
        var ph = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / SAMPLE_RATE
            ph += 2 * PI * freq(t) / SAMPLE_RATE
            x[i] = (sin(ph) * min(1.0, t / attack) * exp(-t / tau)).toFloat()
        }
        return x
    }

    private fun noise(rnd: Random, n: Int) = FloatArray(n.coerceAtLeast(0)) { gaussian(rnd).toFloat() }

    private fun applyEnv(x: FloatArray, attack: Double, tau: Double) {
        for (i in x.indices) {
            val t = i.toDouble() / SAMPLE_RATE
            val a = if (attack > 0) min(1.0, t / attack) else 1.0
            x[i] = (x[i] * a * exp(-t / tau)).toFloat()
        }
    }

    /** 帶通濾波（RBJ biquad，0 dB 峰值） */
    private fun band(x: FloatArray, f0: Double, q: Double): FloatArray {
        val w = 2 * PI * f0 / SAMPLE_RATE
        val al = sin(w) / (2 * q)
        val c = kotlin.math.cos(w)
        val a0 = 1 + al
        val y = FloatArray(x.size)
        var x1 = 0.0; var x2 = 0.0; var y1 = 0.0; var y2 = 0.0
        for (i in x.indices) {
            val v = x[i].toDouble()
            val o = (al * v - al * x2 + 2 * c * y1 - (1 - al) * y2) / a0
            x2 = x1; x1 = v; y2 = y1; y1 = o
            y[i] = o.toFloat()
        }
        return y
    }

    // ---- 合成工具 ----

    /**
     * 模態合成：每個泛音 = sin × 指數衰減。
     * glide：起始音高偏移比例（負值 = 由低往上滑，像水滴；正值 = 由高往下）。
     */
    private fun modal(
        f0: Double, ratios: DoubleArray, amps: DoubleArray, taus: DoubleArray, dur: Double,
        attack: Double = 0.002, glide: Double = 0.0, glideTau: Double = 0.03,
    ): FloatArray {
        val n = (SAMPLE_RATE * dur).toInt()
        val out = FloatArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / SAMPLE_RATE
            val warped = t + glide * glideTau * (1 - exp(-t / glideTau))
            var v = 0.0
            for (m in ratios.indices) {
                v += amps[m] * sin(2 * PI * f0 * ratios[m] * warped) * exp(-t / taus[m])
            }
            val env = if (attack > 0) min(1.0, t / attack) else 1.0
            out[i] = (v * env).toFloat()
        }
        return out
    }

    private fun noiseBurst(rnd: Random, dur: Double, tau: Double, bright: Double): FloatArray {
        val n = (SAMPLE_RATE * dur).toInt()
        val out = FloatArray(n)
        var prev = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / SAMPLE_RATE
            val g = gaussian(rnd)
            val v = (g - prev) * bright + g * (1 - bright)
            prev = g
            out[i] = (v * exp(-t / tau)).toFloat()
        }
        return out
    }

    private fun gaussian(rnd: Random): Double {
        // Box–Muller
        val u1 = rnd.nextDouble().coerceAtLeast(1e-12)
        val u2 = rnd.nextDouble()
        return kotlin.math.sqrt(-2 * kotlin.math.ln(u1)) * kotlin.math.cos(2 * PI * u2)
    }

    private fun mixInto(dst: FloatArray, src: FloatArray, gain: Double) {
        val n = min(dst.size, src.size)
        for (i in 0 until n) dst[i] += (src[i] * gain).toFloat()
    }

    /** 小型 Schroeder 殘響：4 個並聯梳狀濾波 + 1 個全通。 */
    private fun reverb(input: FloatArray, wet: Double, tail: Double): FloatArray {
        val n = input.size + (SAMPLE_RATE * tail).toInt()
        val x = DoubleArray(n)
        for (i in input.indices) x[i] = input[i].toDouble()
        val acc = DoubleArray(n)
        val combs = arrayOf(29.7 to 0.72, 37.1 to 0.70, 41.1 to 0.68, 43.7 to 0.66)
        for ((ms, g) in combs) {
            val d = (SAMPLE_RATE * ms / 1000).toInt()
            val y = DoubleArray(n)
            for (i in 0 until n) {
                y[i] = x[i] + (if (i >= d) g * y[i - d] else 0.0)
                acc[i] += y[i] / combs.size
            }
        }
        val d = (SAMPLE_RATE * 0.005).toInt()
        val g = 0.7
        val ap = DoubleArray(n)
        for (i in 0 until n) {
            val xd = if (i >= d) acc[i - d] else 0.0
            val yd = if (i >= d) ap[i - d] else 0.0
            ap[i] = -g * acc[i] + xd + g * yd
        }
        val out = FloatArray(n)
        for (i in 0 until n) out[i] = (x[i] * (1 - wet) + ap[i] * wet).toFloat()
        return out
    }

    private fun fadeOut(x: FloatArray, sec: Double) {
        val n = min(x.size, (SAMPLE_RATE * sec).toInt())
        for (j in 0 until n) {
            val i = x.size - n + j
            x[i] *= 1f - j.toFloat() / n
        }
    }

    private fun normalize(x: FloatArray, peak: Float) {
        var m = 0f
        for (v in x) m = maxOf(m, abs(v))
        if (m <= 0f) return
        val g = peak / m
        for (i in x.indices) x[i] *= g
    }
}
