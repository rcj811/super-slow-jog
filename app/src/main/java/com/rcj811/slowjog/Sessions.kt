package com.rcj811.slowjog

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** 一次超慢跑紀錄 */
data class Session(
    val startMs: Long,
    val durationSec: Int,
    val targetMin: Int,
    val cadence: Int,
    val steps: Long,
    val kcal: Double,
    val completed: Boolean,
) {
    fun date(zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(startMs).atZone(zone).toLocalDate()

    fun toJson(): JSONObject = JSONObject()
        .put("startMs", startMs)
        .put("durationSec", durationSec)
        .put("targetMin", targetMin)
        .put("cadence", cadence)
        .put("steps", steps)
        .put("kcal", kcal)
        .put("completed", completed)

    companion object {
        fun fromJson(o: JSONObject) = Session(
            startMs = o.getLong("startMs"),
            durationSec = o.optInt("durationSec"),
            targetMin = o.optInt("targetMin"),
            cadence = o.optInt("cadence", 180),
            steps = o.optLong("steps"),
            kcal = o.optDouble("kcal", 0.0),
            completed = o.optBoolean("completed", false),
        )
    }
}

/** 本機 JSON 檔保存所有紀錄 */
object SessionStore {
    private val _sessions = MutableStateFlow<List<Session>>(emptyList())
    val sessions: StateFlow<List<Session>> = _sessions.asStateFlow()
    @Volatile private var loaded = false

    private fun file(context: Context) = File(context.filesDir, "sessions.json")

    @Synchronized
    fun load(context: Context) {
        if (loaded) return
        val f = file(context)
        _sessions.value = runCatching {
            if (!f.exists()) emptyList() else {
                val arr = JSONArray(f.readText())
                (0 until arr.length()).map { Session.fromJson(arr.getJSONObject(it)) }
            }
        }.getOrDefault(emptyList()).sortedBy { it.startMs }
        loaded = true
    }

    @Synchronized
    fun add(context: Context, s: Session) {
        load(context)
        val list = (_sessions.value + s).sortedBy { it.startMs }
        _sessions.value = list
        save(context, list)
    }

    @Synchronized
    fun delete(context: Context, s: Session) {
        val list = _sessions.value.filterNot { it.startMs == s.startMs }
        _sessions.value = list
        save(context, list)
    }

    private fun save(context: Context, list: List<Session>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        val f = file(context)
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(arr.toString())
        if (!tmp.renameTo(f)) { f.writeText(arr.toString()); tmp.delete() }
    }
}

/** 統計彙總（純 Kotlin，可單元測試） */
data class Totals(val count: Int, val seconds: Long, val steps: Long, val kcal: Double) {
    val minutes: Int get() = (seconds / 60).toInt()

    companion object {
        val ZERO = Totals(0, 0, 0, 0.0)
        fun of(list: List<Session>) = Totals(
            count = list.size,
            seconds = list.sumOf { it.durationSec.toLong() },
            steps = list.sumOf { it.steps },
            kcal = list.sumOf { it.kcal },
        )
    }
}

enum class Metric(val label: String, val unit: String) {
    MINUTES("時間", "分"), STEPS("步數", "步"), KCAL("卡路里", "大卡");

    fun of(t: Totals): Double = when (this) {
        MINUTES -> t.seconds / 60.0
        STEPS -> t.steps.toDouble()
        KCAL -> t.kcal
    }
}

object Stats {
    fun onDay(all: List<Session>, day: LocalDate, zone: ZoneId = ZoneId.systemDefault()) =
        all.filter { it.date(zone) == day }

    fun inMonth(all: List<Session>, ym: YearMonth, zone: ZoneId = ZoneId.systemDefault()) =
        all.filter { YearMonth.from(it.date(zone)) == ym }

    fun inYear(all: List<Session>, year: Int, zone: ZoneId = ZoneId.systemDefault()) =
        all.filter { it.date(zone).year == year }

    /** 該日所在一週（週一～週日）每天的彙總 */
    fun weekOf(all: List<Session>, day: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<Pair<LocalDate, Totals>> {
        val monday = day.minusDays((day.dayOfWeek.value - 1).toLong())
        return (0 until 7).map { i ->
            val d = monday.plusDays(i.toLong())
            d to Totals.of(onDay(all, d, zone))
        }
    }

    /** 該月每天的彙總 */
    fun daysOfMonth(all: List<Session>, ym: YearMonth, zone: ZoneId = ZoneId.systemDefault()): List<Totals> {
        val byDay = inMonth(all, ym, zone).groupBy { it.date(zone).dayOfMonth }
        return (1..ym.lengthOfMonth()).map { Totals.of(byDay[it].orEmpty()) }
    }

    /** 該年每月的彙總 */
    fun monthsOfYear(all: List<Session>, year: Int, zone: ZoneId = ZoneId.systemDefault()): List<Totals> {
        val byMonth = inYear(all, year, zone).groupBy { it.date(zone).monthValue }
        return (1..12).map { Totals.of(byMonth[it].orEmpty()) }
    }

    /** 到 [today] 為止連續有跑的天數（今天還沒跑則從昨天算起） */
    fun streak(all: List<Session>, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Int {
        val days = all.map { it.date(zone) }.toHashSet()
        var d = if (today in days) today else today.minusDays(1)
        var n = 0
        while (d in days) { n++; d = d.minusDays(1) }
        return n
    }

    fun activeDays(list: List<Session>, zone: ZoneId = ZoneId.systemDefault()): Int =
        list.map { it.date(zone) }.distinct().size
}
