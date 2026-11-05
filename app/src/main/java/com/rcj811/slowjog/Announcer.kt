package com.rcj811.slowjog

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import java.util.Locale

/** TTS 可選聲音 */
data class VoiceOption(val name: String, val label: String, val gender: VoiceGender?)

object VoicePicker {
    private val femaleRe = Regex("female|女")
    private val maleRe = Regex("(^|[^e])male|男")
    private val samsungRe = Regex("smt([fm])\\d")
    private val suffixRe = Regex("[-_#]([fm])\\d*($|[-_#])")

    /** 從聲音名稱猜性別（各家引擎命名不同，猜不到回傳 null） */
    fun guessGender(name: String): VoiceGender? {
        val n = name.lowercase(Locale.ROOT)
        if (femaleRe.containsMatchIn(n)) return VoiceGender.FEMALE
        if (maleRe.containsMatchIn(n)) return VoiceGender.MALE
        samsungRe.find(n)?.let { return if (it.groupValues[1] == "f") VoiceGender.FEMALE else VoiceGender.MALE }
        suffixRe.find(n)?.let { return if (it.groupValues[1] == "f") VoiceGender.FEMALE else VoiceGender.MALE }
        return null
    }

    private fun regionRank(v: Voice): Int = when (v.locale.country.uppercase(Locale.ROOT)) {
        "TW" -> 0
        "CN", "" -> 1
        "SG" -> 2
        else -> 3 // HK 等多為粵語
    }

    fun chineseVoices(tts: TextToSpeech): List<Voice> = runCatching {
        tts.voices.orEmpty()
            .filter { it.locale.language in setOf("zh", "cmn") }
            .filterNot { it.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) == true }
            .sortedWith(compareBy<Voice>({ regionRank(it) }, { it.isNetworkConnectionRequired }, { it.name }))
    }.getOrDefault(emptyList())

    fun options(tts: TextToSpeech): List<VoiceOption> =
        chineseVoices(tts).mapIndexed { i, v ->
            val g = guessGender(v.name)
            val region = when (v.locale.country.uppercase(Locale.ROOT)) {
                "TW" -> "台灣"; "CN" -> "中國"; "HK" -> "香港"; "SG" -> "新加坡"; else -> v.locale.country
            }
            val extra = listOfNotNull(region.ifBlank { null }, g?.label, if (v.isNetworkConnectionRequired) "需網路" else null)
            VoiceOption(v.name, "聲音 ${i + 1}" + if (extra.isNotEmpty()) "（${extra.joinToString("・")}）" else "", g)
        }
}

/**
 * 語音提醒 + 震動。沒有中文語音時改用提示音。
 */
class Announcer(context: Context, private val onReady: (() -> Unit)? = null) : TextToSpeech.OnInitListener {
    private val ctx = context.applicationContext
    private var tts: TextToSpeech? = TextToSpeech(ctx, this)
    @Volatile var ready = false; private set
    @Volatile var chineseAvailable = false; private set
    private val pending = ArrayList<String>()
    private var tone: ToneGenerator? = null
    private var toneVolume = -1

    override fun onInit(status: Int) {
        val t = tts ?: return
        if (status != TextToSpeech.SUCCESS) {
            ready = true; chineseAvailable = false
            onReady?.invoke(); return
        }
        t.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        var r = t.setLanguage(Locale.TAIWAN)
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) r = t.setLanguage(Locale.CHINA)
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) r = t.setLanguage(Locale.CHINESE)
        chineseAvailable = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
        applyVoice(Prefs.settings.value)
        ready = true
        synchronized(pending) {
            pending.forEach { speakNow(it, false) }
            pending.clear()
        }
        onReady?.invoke()
    }

    fun voiceOptions(): List<VoiceOption> = tts?.let { VoicePicker.options(it) }.orEmpty()

    /** 依設定挑選聲音：指定名稱 > 名稱可辨識性別的聲音 > 預設聲音 + 音高調整 */
    fun applyVoice(s: Settings) {
        val t = tts ?: return
        if (!chineseAvailable) return
        val voices = VoicePicker.chineseVoices(t)
        val chosen = if (s.voiceName == Settings.FIRST_VOICE) voices.firstOrNull()
        else voices.firstOrNull { s.voiceName.isNotEmpty() && it.name == s.voiceName }
        if (chosen != null) {
            runCatching { t.setVoice(chosen) }
            t.setPitch(1.0f)
        } else {
            val gendered = voices.firstOrNull { VoicePicker.guessGender(it.name) == s.voiceGender }
            if (gendered != null) {
                runCatching { t.setVoice(gendered) }
                t.setPitch(1.0f)
            } else {
                voices.firstOrNull()?.let { v -> runCatching { t.setVoice(v) } }
                // 引擎沒標性別時，用音高模擬男 / 女聲
                t.setPitch(if (s.voiceGender == VoiceGender.MALE) 0.72f else 1.12f)
            }
        }
        t.setSpeechRate(1.0f)
    }

    fun speak(text: String, flush: Boolean = false) {
        if (!ready) { synchronized(pending) { pending.add(text) }; return }
        speakNow(text, flush)
    }

    private fun speakNow(text: String, flush: Boolean) {
        val t = tts
        if (t != null && chineseAvailable) {
            val params = Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, Prefs.settings.value.voiceVol.coerceIn(0f, 1f))
            }
            t.speak(text, if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, params, "jog-${System.nanoTime()}")
        } else {
            chime()
        }
    }

    fun chime(long: Boolean = false) {
        runCatching {
            val vol = (Prefs.settings.value.voiceVol * 100).toInt().coerceIn(0, 100)
            if (tone == null || toneVolume != vol) {
                tone?.release()
                tone = ToneGenerator(AudioManager.STREAM_MUSIC, vol)
                toneVolume = vol
            }
            val tg = tone ?: return@runCatching
            tg.startTone(if (long) ToneGenerator.TONE_PROP_PROMPT else ToneGenerator.TONE_PROP_BEEP2, if (long) 600 else 300)
        }
    }

    fun vibrate(pattern: LongArray) {
        runCatching {
            val v: Vibrator = if (Build.VERSION.SDK_INT >= 31) {
                (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            v.vibrate(VibrationEffect.createWaveform(pattern, -1))
        }
    }

    fun stopSpeaking() { runCatching { tts?.stop() } }

    fun shutdown() {
        runCatching { tts?.stop(); tts?.shutdown() }
        tts = null
        runCatching { tone?.release() }
        tone = null
    }

    companion object {
        val TICK = longArrayOf(0, 40)
        val MILESTONE = longArrayOf(0, 180, 120, 180)
        val FINISH = longArrayOf(0, 400, 150, 400, 150, 600)
    }
}
