package com.rcj811.slowjog

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import java.util.concurrent.atomic.AtomicLong

/**
 * 給動畫對拍用的節拍時鐘：記錄最近一拍「實際播出」的時間點。
 * 畫面以 phase = index + (now - timeNanos) / intervalNanos 計算連續相位。
 */
object BeatClock {
    @Volatile var index: Long = 0
    @Volatile var timeNanos: Long = 0
    @Volatile var intervalNanos: Long = 333_333_333
    @Volatile var active: Boolean = false

    fun phase(now: Long = System.nanoTime()): Double {
        if (!active || timeNanos == 0L) return 0.0
        return index + (now - timeNanos).toDouble() / intervalNanos
    }
}

/**
 * 取樣精準的節拍器：以 AudioTrack 串流，把每一拍的音色直接混進 PCM。
 * 不會搶音訊焦點，所以可以邊聽音樂邊跑。
 */
class Metronome {
    @Volatile var bpm: Int = 180
    @Volatile var volume: Float = 0.8f
    @Volatile var sound: BeatSound = BeatSound.WATER
    @Volatile var alternate: Boolean = true
    @Volatile var paused: Boolean = false
        set(value) {
            field = value
            if (!value) resyncRequested = true
        }

    /** 已播出的拍數（= 步數） */
    val beats = AtomicLong(0)

    @Volatile private var running = false
    @Volatile private var resyncRequested = false
    private var thread: Thread? = null

    private class Voice(val data: FloatArray, var pos: Int)

    fun start() {
        if (running) return
        running = true
        resyncRequested = true
        thread = Thread({ loop() }, "metronome").apply { start() }
    }

    fun stop() {
        running = false
        thread?.join(800)
        thread = null
        BeatClock.active = false
    }

    private fun loop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val sr = BeatSynth.SAMPLE_RATE
        val minBuf = AudioTrack.getMinBufferSize(sr, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sr)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minBuf, sr / 8 * 2))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        // 預先合成音色，避免第一拍延遲
        BeatSynth.get(sound, false); BeatSynth.get(sound, true)

        track.play()
        val chunk = ShortArray(sr / 100) // 10 ms
        val voices = ArrayList<Voice>(6)
        var framesWritten = 0L
        var nextBeatAt = 0.0
        var beatIndex = 0L

        try {
            while (running) {
                val isPaused = paused
                if (resyncRequested) {
                    resyncRequested = false
                    nextBeatAt = framesWritten.toDouble()
                }
                val vol = volume
                for (i in chunk.indices) {
                    val frame = framesWritten + i
                    if (!isPaused && frame >= nextBeatAt) {
                        val alt = alternate && beatIndex % 2 == 1L
                        voices.add(Voice(BeatSynth.get(sound, alt), 0))
                        if (voices.size > 6) voices.removeAt(0)
                        // 估計這一拍實際從喇叭播出的時間
                        val head = track.playbackHeadPosition.toLong() and 0xFFFFFFFFL
                        val ahead = (frame - head).coerceAtLeast(0)
                        BeatClock.timeNanos = System.nanoTime() + ahead * 1_000_000_000L / sr
                        BeatClock.index = beatIndex
                        BeatClock.intervalNanos = 60_000_000_000L / bpm.coerceAtLeast(1)
                        BeatClock.active = true
                        beatIndex++
                        beats.incrementAndGet()
                        nextBeatAt += 60.0 * sr / bpm.coerceAtLeast(1)
                    }
                    var s = 0f
                    if (voices.isNotEmpty()) {
                        val it = voices.iterator()
                        while (it.hasNext()) {
                            val v = it.next()
                            if (v.pos >= v.data.size) { it.remove(); continue }
                            s += v.data[v.pos++]
                        }
                    }
                    val out = (s * vol * 0.85f).coerceIn(-1f, 1f)
                    chunk[i] = (out * 32767).toInt().toShort()
                }
                if (isPaused) BeatClock.active = false
                track.write(chunk, 0, chunk.size) // 阻塞寫入，自然以播放速度推進
                framesWritten += chunk.size
            }
        } finally {
            runCatching { track.pause(); track.flush(); track.stop() }
            track.release()
            BeatClock.active = false
        }
    }
}
