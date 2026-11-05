package com.rcj811.slowjog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class CoreTest {
    private val zone: ZoneId = ZoneOffset.ofHours(8)

    private fun sessionAt(date: LocalDate, hour: Int, minutes: Int, cadence: Int = 180) = Session(
        startMs = date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli(),
        durationSec = minutes * 60,
        targetMin = minutes,
        cadence = cadence,
        steps = minutes.toLong() * cadence,
        kcal = Calories.kcal(cadence, 60, minutes * 60_000L),
        completed = true,
    )

    @Test fun durationSnapsToTenMinutes() {
        assertEquals(10, Settings.clampDuration(3))
        assertEquals(30, Settings.clampDuration(26))
        assertEquals(120, Settings.clampDuration(999))
        assertEquals(120, Settings.clampDuration(130))
        assertEquals(50, Settings.clampDuration(50))
        assertEquals(40, Settings.clampDuration(40))
    }

    @Test fun cadenceClampedToStepsOfFive() {
        assertEquals(160, Settings.clampCadence(120))
        assertEquals(220, Settings.clampCadence(300))
        assertEquals(220, Settings.clampCadence(221))
        assertEquals(185, Settings.clampCadence(183))
        assertEquals(180, Settings.clampCadence(182))
        assertEquals(175, Settings.clampCadence(175))
        // 每格 5，共 13 段
        val all = (Settings.MIN_CADENCE..Settings.MAX_CADENCE step Settings.CADENCE_STEP).toList()
        assertEquals(13, all.size)
        assertEquals(160, all.first())
        assertEquals(220, all.last())
    }

    @Test fun caloriesReasonable() {
        // 60kg、180 步/分、30 分鐘 ≈ 4.4 METs × 60 × 0.5 = 132 kcal
        val k = Calories.kcal(180, 60, 30 * 60_000L)
        assertEquals(132.0, k, 0.5)
        assertTrue(Calories.mets(220) > Calories.mets(160))
    }

    @Test fun dailyMonthlyYearlyTotals() {
        val d = LocalDate.of(2026, 11, 4)
        val all = listOf(
            sessionAt(d, 7, 30),
            sessionAt(d, 20, 10),
            sessionAt(d.minusDays(1), 7, 20),
            sessionAt(LocalDate.of(2026, 10, 15), 7, 40),
            sessionAt(LocalDate.of(2025, 12, 31), 7, 10),
        )
        assertEquals(40, Totals.of(Stats.onDay(all, d, zone)).minutes)
        assertEquals(2, Stats.onDay(all, d, zone).size)

        val nov = Stats.daysOfMonth(all, java.time.YearMonth.of(2026, 11), zone)
        assertEquals(30, nov.size)
        assertEquals(40, nov[3].minutes)
        assertEquals(20, nov[2].minutes)

        val year = Stats.monthsOfYear(all, 2026, zone)
        assertEquals(12, year.size)
        assertEquals(60, year[10].minutes)
        assertEquals(40, year[9].minutes)
        assertEquals(0, year[0].minutes)

        assertEquals(2, Stats.streak(all, d, zone))
        assertEquals(2, Stats.streak(all, d.plusDays(1), zone)) // 今天還沒跑，從昨天起算
        assertEquals(0, Stats.streak(all, d.plusDays(2), zone))

        val week = Stats.weekOf(all, d, zone) // 2026-11-04 是週三
        assertEquals(LocalDate.of(2026, 11, 2), week.first().first)
        assertEquals(40, week[2].second.minutes)
    }

    @Test fun jogInPlaceAlternatesFeet() {
        val a = com.rcj811.slowjog.ui.jogPose(0.5, true, 0f, false)
        assertEquals(1f, a.liftR, 0.01f)
        assertEquals(0f, a.liftL, 0.01f)
        val b = com.rcj811.slowjog.ui.jogPose(1.5, true, 0f, false)
        assertEquals(1f, b.liftL, 0.01f)
        assertEquals(0f, b.liftR, 0.01f)
        // 落地瞬間身體最低、壓縮最大
        val land = com.rcj811.slowjog.ui.jogPose(2.0, true, 0f, false)
        assertEquals(0f, land.bounce, 0.01f)
        assertEquals(1f, land.squash, 0.01f)
        // 暫停時雙腳著地
        val idle = com.rcj811.slowjog.ui.jogPose(0.5, false, 1f, false)
        assertEquals(0f, idle.liftL + idle.liftR, 0.001f)
    }

    @Test fun cropCircleStaysInsidePhoto() {
        // 1000×600 的照片：圓最大半徑 300
        val big = CropCircle.clamp(CropCircle(500f, 300f, 900f), 1000f, 600f)
        assertEquals(300f, big.r, 0.01f)
        // 拖到邊緣外會被擋在照片內
        val edge = CropCircle.clamp(CropCircle(-50f, 700f, 100f), 1000f, 600f)
        assertEquals(100f, edge.cx, 0.01f)
        assertEquals(500f, edge.cy, 0.01f)
        // 太小會被放大到最小半徑
        val tiny = CropCircle.clamp(CropCircle(500f, 300f, 1f), 1000f, 600f)
        assertEquals(CropCircle.minRadius(1000f, 600f), tiny.r, 0.01f)
    }

    @Test fun bodyShapeFollowsHeightAndWeight() {
        val normal = BodyShape.of(165, 60)
        assertEquals(1f, normal.legK, 0.01f)
        assertEquals(1f, normal.widthK, 0.03f)
        // 越高腿越長、越矮腿越短
        assertTrue(BodyShape.of(185, 70).legK > normal.legK)
        assertTrue(BodyShape.of(148, 45).legK < normal.legK)
        // BMI 越高越寬、越低越窄，且有上下限
        assertTrue(BodyShape.of(165, 90).widthK > normal.widthK)
        assertTrue(BodyShape.of(165, 45).widthK < normal.widthK)
        assertEquals(1.45f, BodyShape.of(150, 200).widthK, 0.001f)
        assertEquals(0.78f, BodyShape.of(210, 30).widthK, 0.001f)
        assertEquals("標準", BodyShape.buildWord(165, 60))
        assertEquals("纖瘦", BodyShape.buildWord(170, 50))
        assertEquals("圓潤", BodyShape.buildWord(160, 80))
        // 關閉體型變化時一律標準
        assertEquals(BodyShape.NORMAL, BodyShape.from(Settings(heightCm = 190, weightKg = 120, bodyShapeOn = false)))
    }

    @Test fun dailyReminderNextTrigger() {
        val now = java.time.ZonedDateTime.of(2026, 11, 5, 9, 30, 0, 0, zone)
        // 今天還沒到 → 今天
        val a = DailyReminder.nextTrigger(now, 19, 0)
        assertEquals(5, a.dayOfMonth); assertEquals(19, a.hour)
        // 已經過了 → 明天
        val b = DailyReminder.nextTrigger(now, 7, 0)
        assertEquals(6, b.dayOfMonth); assertEquals(7, b.hour)
        // 剛好同一分鐘也排明天，避免重複
        val c = DailyReminder.nextTrigger(now, 9, 30)
        assertEquals(6, c.dayOfMonth)
        assertEquals("晚上 19:00", DailyReminder.timeText(19, 0))
        assertEquals("早上 07:05", DailyReminder.timeText(7, 5))
    }

    @Test fun youtubeLinks() {
        val id = "dQw4w9WgXcQ"
        assertEquals(id, YouTubeStore.extractId("https://www.youtube.com/watch?v=$id"))
        assertEquals(id, YouTubeStore.extractId("https://youtu.be/$id?si=abc123"))
        assertEquals(id, YouTubeStore.extractId("https://m.youtube.com/watch?feature=share&v=$id&t=30"))
        assertEquals(id, YouTubeStore.extractId("https://www.youtube.com/shorts/$id"))
        assertEquals(id, YouTubeStore.extractId("https://music.youtube.com/watch?v=$id&list=RDAMVM"))
        assertEquals(id, YouTubeStore.extractId(id))
        assertNull(YouTubeStore.extractId("https://example.com/watch?v=$id"))
        assertNull(YouTubeStore.extractId("hello"))
        // 一次貼上多個（含重複、雜訊）
        val many = YouTubeStore.extractAll(
            "https://youtu.be/AAAAAAAAAAA\nhttps://www.youtube.com/watch?v=BBBBBBBBBBB , 看這個 https://youtu.be/AAAAAAAAAAA"
        )
        assertEquals(listOf("AAAAAAAAAAA", "BBBBBBBBBBB"), many)
    }

    @Test fun mascotIds() {
        assertEquals(Mascot.CAPYBARA, Mascot.fromId("CAPYBARA"))
        assertEquals(Mascot.DUCK, Mascot.fromId("unknown"))
        assertEquals(Mascot.DUCK, Mascot.fromId(null))
    }

    @Test fun voiceGenderGuess() {
        assertEquals(VoiceGender.FEMALE, VoicePicker.guessGender("zh-TW-female-1"))
        assertEquals(VoiceGender.MALE, VoicePicker.guessGender("zh-tw-x-male"))
        assertEquals(VoiceGender.MALE, VoicePicker.guessGender("zh-TW-SMTm00"))
        assertEquals(VoiceGender.FEMALE, VoicePicker.guessGender("zh-TW-SMTf00"))
        assertNull(VoicePicker.guessGender("cmn-tw-x-ctc-local"))
    }

    @Test fun allBeatSoundsRender() {
        for (snd in BeatSound.entries) for (alt in listOf(false, true)) {
            val x = BeatSynth.render(snd, alt)
            assertTrue("$snd empty", x.size > 1000)
            var peak = 0f
            for (v in x) { assertTrue("$snd NaN", v.isFinite()); peak = maxOf(peak, kotlin.math.abs(v)) }
            assertEquals("$snd peak", 0.9f, peak, 0.01f)
            // 結尾淡出到幾乎無聲，避免爆音
            assertTrue("$snd tail", kotlin.math.abs(x.last()) < 0.01f)
        }
    }
}
