package com.rcj811.slowjog.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rcj811.slowjog.BodyShape
import com.rcj811.slowjog.DailyReminder
import com.rcj811.slowjog.Format
import com.rcj811.slowjog.Prefs
import com.rcj811.slowjog.Settings
import java.util.Locale

@Composable
private fun stepperSlider() = SliderDefaults.colors(
    thumbColor = JogColors.Sun1, activeTrackColor = JogColors.Sun2, inactiveTrackColor = JogColors.SurfaceHigh,
)

/** 一列：標題 + −／數值／+ */
@Composable
fun ValueStepper(
    title: String,
    value: String,
    unit: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    minusEnabled: Boolean = true,
    plusEnabled: Boolean = true,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = JogColors.Text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        RepeatButton("−", onMinus, size = 42.dp, enabled = minusEnabled)
        Row(Modifier.width(118.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
            Text(value, color = JogColors.Text, style = MaterialTheme.typography.headlineSmall)
            Text(" $unit", color = JogColors.Muted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 3.dp))
        }
        RepeatButton("+", onPlus, size = 42.dp, enabled = plusEnabled)
    }
}

/** 身高、體重；下方說明角色會依此變化 */
@Composable
fun BodyEditor(showShapeNote: Boolean = true) {
    val ctx = LocalContext.current
    val s by Prefs.settings.collectAsStateWithLifecycle()
    Column {
        ValueStepper(
            "身高", "${s.heightCm}", "公分",
            { Prefs.update(ctx) { it.copy(heightCm = it.heightCm - 1) } },
            { Prefs.update(ctx) { it.copy(heightCm = it.heightCm + 1) } },
            s.heightCm > Settings.MIN_HEIGHT, s.heightCm < Settings.MAX_HEIGHT,
        )
        Slider(
            value = s.heightCm.toFloat().coerceIn(120f, 210f),
            onValueChange = { v -> Prefs.update(ctx) { it.copy(heightCm = Math.round(v)) } },
            valueRange = 120f..210f,
            colors = stepperSlider(),
        )
        ValueStepper(
            "體重", "${s.weightKg}", "公斤",
            { Prefs.update(ctx) { it.copy(weightKg = it.weightKg - 1) } },
            { Prefs.update(ctx) { it.copy(weightKg = it.weightKg + 1) } },
            s.weightKg > Settings.MIN_WEIGHT, s.weightKg < Settings.MAX_WEIGHT,
        )
        Slider(
            value = s.weightKg.toFloat().coerceIn(30f, 150f),
            onValueChange = { v -> Prefs.update(ctx) { it.copy(weightKg = Math.round(v)) } },
            valueRange = 30f..150f,
            colors = stepperSlider(),
        )
        Text(
            BodyShape.describe(s.heightCm, s.weightKg),
            color = JogColors.Sun1, style = MaterialTheme.typography.labelLarge,
        )
        if (showShapeNote) {
            Spacer(Modifier.height(6.dp))
            Text(
                "陪跑角色會依你的身高、體重呈現高矮胖瘦：越高腿越長、體重越重身形越圓潤。體重也用來計算卡路里。",
                color = JogColors.Muted, style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** 每次運動多久、步頻 */
@Composable
fun GoalEditor() {
    val ctx = LocalContext.current
    val s by Prefs.settings.collectAsStateWithLifecycle()
    Column {
        ValueStepper(
            "每次運動", "${s.durationMin}", "分鐘",
            { Prefs.update(ctx) { it.copy(durationMin = it.durationMin - Settings.DURATION_STEP) } },
            { Prefs.update(ctx) { it.copy(durationMin = it.durationMin + Settings.DURATION_STEP) } },
            s.durationMin > Settings.MIN_DURATION, s.durationMin < Settings.MAX_DURATION,
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            listOf(10, 20, 30, 40, 50, 60, 90, 120).forEach { m ->
                Chip("$m", s.durationMin == m, { Prefs.update(ctx) { it.copy(durationMin = m) } })
            }
        }
        Spacer(Modifier.height(14.dp))
        ValueStepper(
            "步頻", "${s.cadence}", "步/分",
            { Prefs.update(ctx) { it.copy(cadence = it.cadence - Settings.CADENCE_STEP) } },
            { Prefs.update(ctx) { it.copy(cadence = it.cadence + Settings.CADENCE_STEP) } },
            s.cadence > Settings.MIN_CADENCE, s.cadence < Settings.MAX_CADENCE,
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            listOf(160 to "入門", 170 to "輕鬆", 180 to "標準", 200 to "進階", 220 to "挑戰").forEach { (c, name) ->
                Chip("$name $c", s.cadence == c, { Prefs.update(ctx) { it.copy(cadence = c) } })
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "預估每次約 ${Format.thousands(s.durationMin.toLong() * s.cadence)} 步",
            color = JogColors.Muted, style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** 需要時請求通知權限（Android 13 以上），回傳一個呼叫用的函式 */
@Composable
fun rememberNotificationPermissionRequest(): () -> Unit {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    return {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

/** 每日運動提醒：開關、時間 */
@Composable
fun DailyReminderEditor() {
    val ctx = LocalContext.current
    val s by Prefs.settings.collectAsStateWithLifecycle()
    val requestPermission = rememberNotificationPermissionRequest()
    Column {
        SwitchRow(
            "每天提醒我運動",
            if (s.dailyReminderOn) "每天 ${DailyReminder.timeText(s.dailyHour, s.dailyMinute)} 提醒，目標 ${s.durationMin} 分鐘"
            else "到了設定的時間會跳出通知",
            s.dailyReminderOn,
        ) { on ->
            Prefs.update(ctx) { it.copy(dailyReminderOn = on) }
            if (on) requestPermission()
        }
        if (s.dailyReminderOn) {
            Spacer(Modifier.height(6.dp))
            Text(
                DailyReminder.timeText(s.dailyHour, s.dailyMinute),
                color = JogColors.Sun1, style = MaterialTheme.typography.displayMedium,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
            ValueStepper(
                "時", String.format(Locale.US, "%02d", s.dailyHour), "",
                { Prefs.update(ctx) { it.copy(dailyHour = it.dailyHour - 1) } },
                { Prefs.update(ctx) { it.copy(dailyHour = it.dailyHour + 1) } },
            )
            Spacer(Modifier.height(6.dp))
            ValueStepper(
                "分", String.format(Locale.US, "%02d", s.dailyMinute), "",
                { Prefs.update(ctx) { it.copy(dailyMinute = (it.dailyMinute / 5) * 5 - 5) } },
                { Prefs.update(ctx) { it.copy(dailyMinute = (it.dailyMinute / 5) * 5 + 5) } },
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                listOf(6 to 30, 7 to 0, 12 to 30, 17 to 30, 19 to 0, 20 to 30).forEach { (h, m) ->
                    Chip(
                        String.format(Locale.US, "%d:%02d", h, m),
                        s.dailyHour == h && s.dailyMinute == m,
                        { Prefs.update(ctx) { it.copy(dailyHour = h, dailyMinute = m) } },
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "當天已跑滿目標時間就不會再提醒。提醒可能會晚幾分鐘。",
                color = JogColors.Faint, style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
