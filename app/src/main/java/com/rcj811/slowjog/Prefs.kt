package com.rcj811.slowjog

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class VoiceGender(val label: String) { FEMALE("女聲"), MALE("男聲") }

/** 畫面方向：直向、橫向、依手機轉向自動旋轉 */
enum class ScreenMode(val label: String) {
    PORTRAIT("直向"), LANDSCAPE("橫向"), AUTO("自動旋轉");

    fun next(): ScreenMode = entries[(ordinal + 1) % entries.size]
}

/** 使用者設定：時間（10 分鐘為單位）、步頻 160–220（每格 5）、體重、聲音等。 */
data class Settings(
    val durationMin: Int = 30,
    val cadence: Int = 180,
    val weightKg: Int = 60,
    /** 節拍音量 0–1.5（超過 100% 以柔和限幅放大） */
    val volume: Float = 0.8f,
    /** 語音提醒音量 0–1 */
    val voiceVolume: Float = 1.0f,
    val beatSound: BeatSound = BeatSound.WATER,
    val alternateTone: Boolean = true,
    val voiceOn: Boolean = true,
    val voiceGender: VoiceGender = VoiceGender.FEMALE,
    /** 指定的 TTS 聲音名稱；空字串 = 依男女聲自動挑選 */
    val voiceName: String = "",
    val vibrateOn: Boolean = true,
    val reminderOn: Boolean = true,
    val countdownOn: Boolean = true,
    val keepScreenOn: Boolean = true,
    val screenMode: ScreenMode = ScreenMode.PORTRAIT,
    val mascot: Mascot = Mascot.DUCK,
) {
    companion object {
        const val MIN_DURATION = 10
        const val MAX_DURATION = 180
        const val DURATION_STEP = 10
        const val MIN_CADENCE = 160
        const val MAX_CADENCE = 220
        const val CADENCE_STEP = 5
        const val REMINDER_EVERY_MIN = 5
        const val COUNTDOWN_SEC = 3
        const val MAX_BEAT_VOLUME = 1.5f

        fun clampDuration(v: Int): Int {
            val rounded = Math.round(v / DURATION_STEP.toFloat()) * DURATION_STEP
            return rounded.coerceIn(MIN_DURATION, MAX_DURATION)
        }

        /** 步頻以 5 為一格，160–220 */
        fun clampCadence(v: Int): Int {
            val rounded = Math.round(v / CADENCE_STEP.toFloat()) * CADENCE_STEP
            return rounded.coerceIn(MIN_CADENCE, MAX_CADENCE)
        }
    }
}

object Prefs {
    private const val FILE = "settings"
    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()
    @Volatile private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        val p = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val d = Settings()
        _settings.value = Settings(
            durationMin = Settings.clampDuration(p.getInt("durationMin", d.durationMin)),
            cadence = Settings.clampCadence(p.getInt("cadence", d.cadence)),
            weightKg = p.getInt("weightKg", d.weightKg).coerceIn(30, 200),
            volume = p.getFloat("volume", d.volume).coerceIn(0f, Settings.MAX_BEAT_VOLUME),
            voiceVolume = p.getFloat("voiceVolume", d.voiceVolume).coerceIn(0f, 1f),
            beatSound = BeatSound.fromId(p.getString("beatSound", d.beatSound.name)),
            alternateTone = p.getBoolean("alternateTone", d.alternateTone),
            voiceOn = p.getBoolean("voiceOn", d.voiceOn),
            voiceGender = runCatching {
                VoiceGender.valueOf(p.getString("voiceGender", d.voiceGender.name)!!)
            }.getOrDefault(d.voiceGender),
            voiceName = p.getString("voiceName", d.voiceName) ?: "",
            vibrateOn = p.getBoolean("vibrateOn", d.vibrateOn),
            reminderOn = p.getBoolean("reminderOn", d.reminderOn),
            countdownOn = p.getBoolean("countdownOn", d.countdownOn),
            keepScreenOn = p.getBoolean("keepScreenOn", d.keepScreenOn),
            screenMode = runCatching {
                ScreenMode.valueOf(p.getString("screenMode", d.screenMode.name)!!)
            }.getOrDefault(d.screenMode),
            mascot = Mascot.fromId(p.getString("mascot", d.mascot.name)),
        )
        loaded = true
    }

    fun update(context: Context, transform: (Settings) -> Settings) {
        val s = transform(_settings.value).let {
            it.copy(
                durationMin = Settings.clampDuration(it.durationMin),
                cadence = Settings.clampCadence(it.cadence),
                weightKg = it.weightKg.coerceIn(30, 200),
                volume = it.volume.coerceIn(0f, Settings.MAX_BEAT_VOLUME),
                voiceVolume = it.voiceVolume.coerceIn(0f, 1f),
            )
        }
        _settings.value = s
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putInt("durationMin", s.durationMin)
            .putInt("cadence", s.cadence)
            .putInt("weightKg", s.weightKg)
            .putFloat("volume", s.volume)
            .putFloat("voiceVolume", s.voiceVolume)
            .putString("beatSound", s.beatSound.name)
            .putBoolean("alternateTone", s.alternateTone)
            .putBoolean("voiceOn", s.voiceOn)
            .putString("voiceGender", s.voiceGender.name)
            .putString("voiceName", s.voiceName)
            .putBoolean("vibrateOn", s.vibrateOn)
            .putBoolean("reminderOn", s.reminderOn)
            .putBoolean("countdownOn", s.countdownOn)
            .putBoolean("keepScreenOn", s.keepScreenOn)
            .putString("screenMode", s.screenMode.name)
            .putString("mascot", s.mascot.name)
            .apply()
    }

    /** 所有設定回到初始值 */
    fun reset(context: Context) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().clear().apply()
        _settings.value = Settings()
    }
}
