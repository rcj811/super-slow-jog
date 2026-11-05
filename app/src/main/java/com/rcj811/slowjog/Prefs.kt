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

/** 使用者設定：時間（10 分鐘為單位）、步頻 160–220（每格 5）、身高體重、聲音、每日提醒等。 */
data class Settings(
    val durationMin: Int = 30,
    val cadence: Int = 180,
    val heightCm: Int = 165,
    val weightKg: Int = 60,
    /** 角色依身高體重呈現高矮胖瘦 */
    val bodyShapeOn: Boolean = true,
    /** 是否已完成第一次設定 */
    val onboarded: Boolean = false,
    /** 每日運動提醒 */
    val dailyReminderOn: Boolean = true,
    val dailyHour: Int = 20,
    val dailyMinute: Int = 30,
    /** 跑步時播放 YouTube（取代角色區） */
    val ytOn: Boolean = false,
    val ytVolume: Int = 60,
    /** 統一音量：節拍、語音、YouTube 一樣大，只用裝置音量調整 */
    val unifiedVolume: Boolean = true,
    val ytShuffle: Boolean = false,
    /** App 最小化或關螢幕時 YouTube 繼續播放（自用） */
    val ytBackground: Boolean = true,
    /** 節拍音量 0–1.5（超過 100% 以柔和限幅放大） */
    val volume: Float = 0.8f,
    /** 語音提醒音量 0–1 */
    val voiceVolume: Float = 1.0f,
    val beatSound: BeatSound = BeatSound.METRONOME,
    val alternateTone: Boolean = true,
    val voiceOn: Boolean = true,
    val voiceGender: VoiceGender = VoiceGender.FEMALE,
    /** 指定的 TTS 聲音名稱；空字串 = 依男女聲自動挑選；FIRST_VOICE = 清單第一個（台灣優先） */
    val voiceName: String = FIRST_VOICE,
    val vibrateOn: Boolean = true,
    val reminderOn: Boolean = true,
    val countdownOn: Boolean = true,
    val keepScreenOn: Boolean = true,
    val screenMode: ScreenMode = ScreenMode.AUTO,
    val mascot: Mascot = Mascot.DUCK,
) {
    /** 實際使用的音量（統一音量時全部 100%，只由裝置音量決定） */
    val beatVol: Float get() = if (unifiedVolume) 1f else volume
    val voiceVol: Float get() = if (unifiedVolume) 1f else voiceVolume
    val ytVol: Int get() = if (unifiedVolume) 100 else ytVolume

    companion object {
        const val MIN_DURATION = 10
        const val MAX_DURATION = 120
        const val DURATION_STEP = 10
        const val MIN_CADENCE = 160
        const val MAX_CADENCE = 220
        const val CADENCE_STEP = 5
        const val REMINDER_EVERY_MIN = 5
        const val COUNTDOWN_SEC = 3
        /** 預設語音：聲音 1（清單第一個，台灣中文優先） */
        const val FIRST_VOICE = "@first"
        const val MAX_BEAT_VOLUME = 1.5f
        const val MIN_HEIGHT = 100
        const val MAX_HEIGHT = 220
        const val MIN_WEIGHT = 30
        const val MAX_WEIGHT = 200

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
            heightCm = p.getInt("heightCm", d.heightCm).coerceIn(Settings.MIN_HEIGHT, Settings.MAX_HEIGHT),
            weightKg = p.getInt("weightKg", d.weightKg).coerceIn(Settings.MIN_WEIGHT, Settings.MAX_WEIGHT),
            bodyShapeOn = p.getBoolean("bodyShapeOn", d.bodyShapeOn),
            onboarded = p.getBoolean("onboarded", d.onboarded),
            dailyReminderOn = p.getBoolean("dailyReminderOn", d.dailyReminderOn),
            dailyHour = p.getInt("dailyHour", d.dailyHour).coerceIn(0, 23),
            dailyMinute = p.getInt("dailyMinute", d.dailyMinute).coerceIn(0, 59),
            ytOn = p.getBoolean("ytOn", d.ytOn),
            ytVolume = p.getInt("ytVolume", d.ytVolume).coerceIn(0, 100),
            unifiedVolume = p.getBoolean("unifiedVolume", d.unifiedVolume),
            ytShuffle = p.getBoolean("ytShuffle", d.ytShuffle),
            ytBackground = p.getBoolean("ytBackground", d.ytBackground),
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
                heightCm = it.heightCm.coerceIn(Settings.MIN_HEIGHT, Settings.MAX_HEIGHT),
                weightKg = it.weightKg.coerceIn(Settings.MIN_WEIGHT, Settings.MAX_WEIGHT),
                dailyHour = Math.floorMod(it.dailyHour, 24),
                dailyMinute = Math.floorMod(it.dailyMinute, 60),
                ytVolume = it.ytVolume.coerceIn(0, 100),
                volume = it.volume.coerceIn(0f, Settings.MAX_BEAT_VOLUME),
                voiceVolume = it.voiceVolume.coerceIn(0f, 1f),
            )
        }
        _settings.value = s
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putInt("durationMin", s.durationMin)
            .putInt("cadence", s.cadence)
            .putInt("heightCm", s.heightCm)
            .putInt("weightKg", s.weightKg)
            .putBoolean("bodyShapeOn", s.bodyShapeOn)
            .putBoolean("onboarded", s.onboarded)
            .putBoolean("dailyReminderOn", s.dailyReminderOn)
            .putInt("dailyHour", s.dailyHour)
            .putInt("dailyMinute", s.dailyMinute)
            .putBoolean("ytOn", s.ytOn)
            .putInt("ytVolume", s.ytVolume)
            .putBoolean("unifiedVolume", s.unifiedVolume)
            .putBoolean("ytShuffle", s.ytShuffle)
            .putBoolean("ytBackground", s.ytBackground)
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
