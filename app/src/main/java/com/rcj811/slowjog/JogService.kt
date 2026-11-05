package com.rcj811.slowjog

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** 跑步中的前景服務：節拍、計時、5 分鐘提醒、步數與卡路里、存檔。 */
class JogService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var metronome: Metronome? = null
    private var announcer: Announcer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var runJob: Job? = null
    private var stopJob: Job? = null
    private var settingsJob: Job? = null

    private var accumulatedMs = 0L
    private var segmentStart = 0L
    private var lastTick = 0L
    private var kcal = 0.0
    private var startWallMs = 0L
    private var nextMilestoneMin = Settings.REMINDER_EVERY_MIN
    private var lastMinuteSaid = false
    private var lastNotifyAt = 0L
    private var encourageIndex = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Prefs.load(this)
        SessionStore.load(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> start()
            ACTION_PAUSE -> pause()
            ACTION_RESUME -> resume()
            ACTION_STOP -> stopRun()
            ACTION_CADENCE -> setCadence(intent.getIntExtra(EXTRA_CADENCE, RunRepository.state.value.cadence))
            ACTION_VOICE_TEST -> if (RunRepository.state.value.isActive) {
                if (Prefs.settings.value.voiceOn) announcer?.speak("語音音量測試", flush = true) else announcer?.chime()
            }
            else -> if (!RunRepository.state.value.isActive) stopSelf()
        }
        return START_NOT_STICKY
    }

    // ---------------- 流程 ----------------

    private fun start() {
        stopJob?.cancel()
        val s = Prefs.settings.value
        goForeground(buildNotification(RunState(phase = Phase.COUNTDOWN, targetMin = s.durationMin, cadence = s.cadence)))
        acquireWakeLock()
        if (announcer == null) announcer = Announcer(this)
        announcer?.applyVoice(s)

        accumulatedMs = 0; kcal = 0.0
        nextMilestoneMin = Settings.REMINDER_EVERY_MIN
        lastMinuteSaid = false
        encourageIndex = 0
        startWallMs = System.currentTimeMillis()

        val m = Metronome().apply {
            bpm = s.cadence; volume = s.beatVol; sound = s.beatSound; alternate = s.alternateTone
        }
        metronome?.stop()
        metronome = m
        watchSettings()

        runJob?.cancel()
        runJob = scope.launch {
            // 預先合成音色（背景執行緒）
            launch(Dispatchers.Default) { BeatSynth.get(s.beatSound, false); BeatSynth.get(s.beatSound, true) }
            if (s.countdownOn) {
                for (n in Settings.COUNTDOWN_SEC downTo 1) {
                    RunRepository.set(RunState(phase = Phase.COUNTDOWN, countdown = n, targetMin = s.durationMin, cadence = s.cadence))
                    if (s.voiceOn) announcer?.speak(numberWord(n), flush = true) else announcer?.chime()
                    if (s.vibrateOn) announcer?.vibrate(Announcer.TICK)
                    delay(1000)
                }
            }
            if (s.voiceOn) announcer?.speak("開始！保持微笑的速度", flush = true)
            beginRunning(s)
            tickLoop()
        }
    }

    private fun beginRunning(s: Settings) {
        val now = SystemClock.elapsedRealtime()
        segmentStart = now
        lastTick = now
        metronome?.start()
        RunRepository.set(RunState(phase = Phase.RUNNING, targetMin = s.durationMin, cadence = s.cadence))
    }

    private suspend fun tickLoop() {
        while (scope.isActive) {
            val st = RunRepository.state.value
            if (st.phase == Phase.RUNNING) tick(st)
            if (RunRepository.state.value.phase == Phase.FINISHED || !RunRepository.state.value.isActive) break
            delay(200)
        }
    }

    private fun tick(st: RunState) {
        val s = Prefs.settings.value
        val now = SystemClock.elapsedRealtime()
        val dt = now - lastTick
        lastTick = now
        kcal += Calories.kcal(st.cadence, s.weightKg, dt)
        val elapsed = accumulatedMs + (now - segmentStart)
        val steps = metronome?.beats?.get() ?: 0
        var milestone = st.milestone

        if (elapsed >= st.targetMs) {
            RunRepository.set(st.copy(elapsedMs = st.targetMs, steps = steps, kcal = kcal))
            finish(completed = true)
            return
        }

        if (s.reminderOn && elapsed >= nextMilestoneMin * 60_000L) {
            milestone++
            val remainMin = st.targetMin - nextMilestoneMin
            remind(s, nextMilestoneMin, remainMin)
            nextMilestoneMin += Settings.REMINDER_EVERY_MIN
        }
        if (!lastMinuteSaid && st.targetMin >= 10 && st.targetMs - elapsed <= 60_000L) {
            lastMinuteSaid = true
            if (s.voiceOn) announcer?.speak("最後一分鐘，加油！") else announcer?.chime()
        }

        val ns = st.copy(elapsedMs = elapsed, steps = steps, kcal = kcal, milestone = milestone)
        RunRepository.set(ns)
        if (now - lastNotifyAt >= 1000) {
            lastNotifyAt = now
            notifyUpdate(ns)
        }
    }

    private fun remind(s: Settings, doneMin: Int, remainMin: Int) {
        if (s.vibrateOn) announcer?.vibrate(Announcer.MILESTONE)
        if (s.voiceOn) {
            val tip = ENCOURAGE[encourageIndex++ % ENCOURAGE.size]
            announcer?.speak("已經跑了 $doneMin 分鐘，還剩 $remainMin 分鐘。$tip")
        } else {
            announcer?.chime()
        }
    }

    private fun pause() {
        val st = RunRepository.state.value
        if (st.phase != Phase.RUNNING) return
        val now = SystemClock.elapsedRealtime()
        kcal += Calories.kcal(st.cadence, Prefs.settings.value.weightKg, now - lastTick)
        accumulatedMs += now - segmentStart
        metronome?.paused = true
        val ns = st.copy(phase = Phase.PAUSED, elapsedMs = accumulatedMs, kcal = kcal)
        RunRepository.set(ns)
        notifyUpdate(ns)
        if (Prefs.settings.value.voiceOn) announcer?.speak("已暫停", flush = true)
    }

    private fun resume() {
        val st = RunRepository.state.value
        if (st.phase != Phase.PAUSED) return
        val now = SystemClock.elapsedRealtime()
        segmentStart = now
        lastTick = now
        metronome?.paused = false
        val ns = st.copy(phase = Phase.RUNNING)
        RunRepository.set(ns)
        notifyUpdate(ns)
        if (Prefs.settings.value.voiceOn) announcer?.speak("繼續", flush = true)
    }

    private fun setCadence(c: Int) {
        val cad = Settings.clampCadence(c)
        val st = RunRepository.state.value
        if (st.phase == Phase.RUNNING) {
            // 先把舊步頻的熱量結算
            val now = SystemClock.elapsedRealtime()
            kcal += Calories.kcal(st.cadence, Prefs.settings.value.weightKg, now - lastTick)
            lastTick = now
        }
        metronome?.bpm = cad
        Prefs.update(this) { it.copy(cadence = cad) }
        RunRepository.update { it.copy(cadence = cad, kcal = kcal) }
    }

    private fun stopRun() {
        val st = RunRepository.state.value
        when (st.phase) {
            Phase.COUNTDOWN -> cancelToIdle()
            Phase.RUNNING, Phase.PAUSED -> {
                if (st.phase == Phase.RUNNING) {
                    val now = SystemClock.elapsedRealtime()
                    kcal += Calories.kcal(st.cadence, Prefs.settings.value.weightKg, now - lastTick)
                    accumulatedMs += now - segmentStart
                    RunRepository.set(st.copy(elapsedMs = accumulatedMs, kcal = kcal, steps = metronome?.beats?.get() ?: st.steps))
                }
                finish(completed = false)
            }
            else -> cancelToIdle()
        }
    }

    private fun cancelToIdle() {
        runJob?.cancel()
        metronome?.stop(); metronome = null
        announcer?.stopSpeaking()
        RunRepository.set(RunState())
        shutdownSoon(0)
    }

    private fun finish(completed: Boolean) {
        val st = RunRepository.state.value
        runJob?.cancel()
        metronome?.stop(); metronome = null
        val s = Prefs.settings.value
        val elapsed = st.elapsedMs
        if (!completed && elapsed < 60_000L) {
            // 未滿 1 分鐘不列入紀錄
            announcer?.stopSpeaking()
            RunRepository.set(RunState())
            shutdownSoon(0)
            return
        }
        val steps = st.steps
        val avgCadence = if (elapsed > 0) (steps * 60_000.0 / elapsed).roundToInt().coerceIn(Settings.MIN_CADENCE, Settings.MAX_CADENCE) else st.cadence
        val session = Session(
            startMs = startWallMs,
            durationSec = (elapsed / 1000).toInt(),
            targetMin = st.targetMin,
            cadence = avgCadence,
            steps = steps,
            kcal = kcal,
            completed = completed,
        )
        SessionStore.add(this, session)
        RunRepository.set(st.copy(phase = Phase.FINISHED, result = session, kcal = kcal))

        if (s.vibrateOn) announcer?.vibrate(Announcer.FINISH)
        if (s.voiceOn) {
            val mins = (elapsed / 60_000L).toInt()
            val text = if (completed) "恭喜完成 ${st.targetMin} 分鐘超慢跑！共 $steps 步，消耗約 ${kcal.roundToInt()} 大卡。"
            else "這次跑了 $mins 分鐘，共 $steps 步，消耗約 ${kcal.roundToInt()} 大卡。辛苦了！"
            announcer?.speak(text, flush = true)
        } else {
            announcer?.chime(long = true)
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        shutdownSoon(12_000)
    }

    /** 等語音說完再結束服務 */
    private fun shutdownSoon(delayMs: Long) {
        settingsJob?.cancel()
        releaseWakeLock()
        stopJob?.cancel()
        stopJob = scope.launch {
            delay(delayMs)
            ServiceCompat.stopForeground(this@JogService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun watchSettings() {
        settingsJob?.cancel()
        settingsJob = scope.launch {
            Prefs.settings.collect { s ->
                metronome?.let {
                    it.volume = s.beatVol
                    it.alternate = s.alternateTone
                    if (it.sound != s.beatSound) {
                        launch(Dispatchers.Default) {
                            BeatSynth.get(s.beatSound, false); BeatSynth.get(s.beatSound, true)
                            it.sound = s.beatSound
                        }
                    }
                }
                announcer?.applyVoice(s)
            }
        }
    }

    override fun onDestroy() {
        runJob?.cancel()
        metronome?.stop(); metronome = null
        announcer?.shutdown(); announcer = null
        releaseWakeLock()
        if (RunRepository.state.value.isActive) RunRepository.set(RunState())
        scope.cancel()
        super.onDestroy()
    }

    // ---------------- 通知 ----------------

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        val ch = NotificationChannel(CHANNEL, "超慢跑進行中", NotificationManager.IMPORTANCE_LOW).apply {
            description = "顯示剩餘時間與控制按鈕"
            setShowBadge(false)
        }
        nm.createNotificationChannel(ch)
    }

    private fun servicePending(action: String, code: Int): PendingIntent =
        PendingIntent.getService(
            this, code, Intent(this, JogService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun buildNotification(st: RunState): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = when (st.phase) {
            Phase.COUNTDOWN -> "準備開始…"
            Phase.PAUSED -> "已暫停 · 剩 ${Format.clock(st.remainingMs / 1000)}"
            else -> "超慢跑中 · 剩 ${Format.clock(st.remainingMs / 1000)}"
        }
        val text = "${st.cadence} 步/分 · ${Format.thousands(st.steps)} 步 · ${Format.kcal(st.kcal)} 大卡"
        val b = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_run)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setColor(0xFFFF7A45.toInt())
            .setProgress(1000, (st.progress * 1000).toInt(), false)
        when (st.phase) {
            Phase.RUNNING -> b.addAction(0, "暫停", servicePending(ACTION_PAUSE, 1))
            Phase.PAUSED -> b.addAction(0, "繼續", servicePending(ACTION_RESUME, 2))
            else -> {}
        }
        b.addAction(0, "結束", servicePending(ACTION_STOP, 3))
        return b.build()
    }

    private fun notifyUpdate(st: RunState) {
        if (!st.isActive) return
        runCatching {
            getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(st))
        }
    }

    private fun goForeground(n: Notification) {
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        ServiceCompat.startForeground(this, NOTIF_ID, n, type)
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SlowJog:run").apply {
            setReferenceCounted(false)
            acquire(4 * 60 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
    }

    companion object {
        const val ACTION_START = "slowjog.START"
        const val ACTION_PAUSE = "slowjog.PAUSE"
        const val ACTION_RESUME = "slowjog.RESUME"
        const val ACTION_STOP = "slowjog.STOP"
        const val ACTION_CADENCE = "slowjog.CADENCE"
        const val ACTION_VOICE_TEST = "slowjog.VOICE_TEST"
        const val EXTRA_CADENCE = "cadence"
        private const val CHANNEL = "jog"
        private const val NOTIF_ID = 42

        private val ENCOURAGE = listOf(
            "保持微笑，用能聊天的速度跑。",
            "放鬆肩膀，用前腳掌輕輕落地。",
            "步伐小一點，節奏穩穩的。",
            "抬頭挺胸，自然擺臂。",
            "呼吸放慢，你做得很好！",
            "跟著節拍，一步一步來。",
        )

        private fun numberWord(n: Int) = when (n) { 1 -> "一"; 2 -> "二"; 3 -> "三"; else -> n.toString() }

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, JogService::class.java).setAction(ACTION_START))
        }

        fun send(context: Context, action: String, cadence: Int? = null) {
            val i = Intent(context, JogService::class.java).setAction(action)
            if (cadence != null) i.putExtra(EXTRA_CADENCE, cadence)
            runCatching { context.startService(i) }
        }
    }
}
