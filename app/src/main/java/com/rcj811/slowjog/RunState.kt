package com.rcj811.slowjog

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class Phase { IDLE, COUNTDOWN, RUNNING, PAUSED, FINISHED }

data class RunState(
    val phase: Phase = Phase.IDLE,
    val countdown: Int = 0,
    val targetMin: Int = 30,
    val elapsedMs: Long = 0,
    val cadence: Int = 180,
    val steps: Long = 0,
    val kcal: Double = 0.0,
    /** 每過 5 分鐘 +1，畫面據此跳出提示 */
    val milestone: Int = 0,
    val result: Session? = null,
) {
    val targetMs: Long get() = targetMin * 60_000L
    val remainingMs: Long get() = (targetMs - elapsedMs).coerceAtLeast(0)
    val progress: Float get() = if (targetMs == 0L) 0f else (elapsedMs.toFloat() / targetMs).coerceIn(0f, 1f)
    val isActive: Boolean get() = phase == Phase.COUNTDOWN || phase == Phase.RUNNING || phase == Phase.PAUSED
}

/** 服務與畫面共用的跑步狀態 */
object RunRepository {
    private val _state = MutableStateFlow(RunState())
    val state: StateFlow<RunState> = _state.asStateFlow()

    fun set(s: RunState) { _state.value = s }
    fun update(f: (RunState) -> RunState) { _state.value = f(_state.value) }
    fun dismissResult() {
        if (_state.value.phase == Phase.FINISHED) _state.value = RunState()
    }
}
