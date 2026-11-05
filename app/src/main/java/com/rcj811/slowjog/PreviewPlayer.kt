package com.rcj811.slowjog

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 設定頁 / 首頁的試聽：不需前景服務，離開畫面即停止。 */
object PreviewPlayer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var metronome: Metronome? = null
    private var stopJob: Job? = null
    private val _playing = MutableStateFlow<BeatSound?>(null)
    val playing: StateFlow<BeatSound?> = _playing.asStateFlow()

    /** 播放 [millis] 毫秒；millis = 0 表示持續播放直到 stop() */
    fun play(s: Settings, sound: BeatSound = s.beatSound, millis: Long = 2200) {
        if (RunRepository.state.value.isActive) return
        stop()
        val m = Metronome().apply {
            bpm = s.cadence; volume = s.beatVol; this.sound = sound; alternate = s.alternateTone
        }
        metronome = m
        _playing.value = sound
        scope.launch(Dispatchers.Default) {
            BeatSynth.get(sound, false); BeatSynth.get(sound, true)
            if (metronome === m) m.start()
        }
        if (millis > 0) {
            stopJob = scope.launch { delay(millis); if (metronome === m) stop() }
        }
    }

    fun update(s: Settings) {
        metronome?.let { it.bpm = s.cadence; it.volume = s.beatVol; it.alternate = s.alternateTone }
    }

    fun stop() {
        stopJob?.cancel(); stopJob = null
        val m = metronome
        metronome = null
        _playing.value = null
        if (m != null) scope.launch(Dispatchers.Default) { m.stop() }
    }
}
