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
    DRUM("輕鼓", "低沉的咚咚聲，像踏在地上的腳步");

    companion object {
        fun fromId(id: String?): BeatSound = entries.firstOrNull { it.name == id } ?: METRONOME
    }
}

object BeatSynth {
    const val SAMPLE_RATE = 44100

    private val cache = HashMap<Pair<BeatSound, Boolean>, FloatArray>()

    /** 取得音色樣本（-1..1）。[alt] = 另一隻腳的音高。 */
    @Synchronized
    fun get(sound: BeatSound, alt: Boolean): FloatArray =
        cache.getOrPut(sound to alt) { render(sound, alt) }

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
        }
        fadeOut(y, 0.02)
        normalize(y, 0.9f)
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
