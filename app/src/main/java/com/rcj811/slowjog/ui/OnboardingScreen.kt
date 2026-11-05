package com.rcj811.slowjog.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rcj811.slowjog.BodyShape
import com.rcj811.slowjog.DailyReminder
import com.rcj811.slowjog.Format
import com.rcj811.slowjog.Prefs

private class Step(val title: String, val subtitle: String)

private val STEPS = listOf(
    Step("你的身高體重", "陪跑角色會依你的身高、體重變得高矮胖瘦，也會用來計算卡路里"),
    Step("選擇陪跑角色", "跑步時它會跟著節拍原地超慢跑，之後隨時可以換"),
    Step("運動目標", "每次要運動多久、用什麼步頻"),
    Step("每日提醒", "每天固定時間提醒你運動，養成習慣"),
    Step("都設定好了！", "以下設定之後都可以在「設定」裡修改或重設"),
)

/** 第一次開啟 App 的設定流程 */
@Composable
fun OnboardingScreen() {
    val ctx = LocalContext.current
    val s by Prefs.settings.collectAsStateWithLifecycle()
    var step by rememberSaveable { mutableIntStateOf(0) }
    val last = STEPS.size - 1
    val landscape = isLandscape()

    BackHandler(enabled = step > 0) { step-- }

    val finish = {
        Prefs.update(ctx) { it.copy(onboarded = true) }
        DailyReminder.schedule(ctx)
    }

    val preview: @Composable (Modifier) -> Unit = { m ->
        Box(
            m
                .clip(RoundedCornerShape(28.dp))
                .background(JogColors.Surface),
            contentAlignment = Alignment.Center,
        ) {
            MascotView(
                s.mascot,
                Modifier.fillMaxSize().padding(8.dp),
                moving = true,
                cadence = s.cadence,
                celebrate = step == last,
            )
        }
    }

    val content: @Composable () -> Unit = {
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally(tween(260)) { it / 6 * dir } + fadeIn(tween(260))) togetherWith
                    (slideOutHorizontally(tween(200)) { -it / 6 * dir } + fadeOut(tween(200)))
            },
            label = "onboarding",
        ) { st ->
            Column {
                when (st) {
                    0 -> BodyEditor()
                    1 -> MascotPicker()
                    2 -> GoalEditor()
                    3 -> DailyReminderEditor()
                    else -> Summary()
                }
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(JogColors.Bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .displayCutoutPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        // 標題與進度
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (step == 0) "歡迎使用超慢跑" else "第 ${step + 1} / ${STEPS.size} 步",
                    color = JogColors.Sun1, style = MaterialTheme.typography.labelLarge,
                )
                Text(STEPS[step].title, color = JogColors.Text, style = MaterialTheme.typography.headlineLarge)
                Text(STEPS[step].subtitle, color = JogColors.Muted, style = MaterialTheme.typography.bodyMedium)
            }
            if (step < last) {
                Text(
                    "跳過", color = JogColors.Faint, style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { finish() }.padding(10.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            STEPS.indices.forEach { i ->
                Box(
                    Modifier
                        .height(6.dp)
                        .width(if (i == step) 28.dp else 12.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (i <= step) JogColors.Sun2 else JogColors.SurfaceHigh)
                )
            }
        }
        Spacer(Modifier.height(14.dp))

        if (landscape) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                if (step != 1) preview(Modifier.fillMaxHeight().width(220.dp))
                Column(
                    Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                ) { content() }
            }
        } else {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (step != 1) {
                    preview(Modifier.size(190.dp))
                    Spacer(Modifier.height(14.dp))
                }
                Box(Modifier.fillMaxWidth().widthIn(max = 640.dp)) { content() }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (step > 0) {
                Box(
                    Modifier
                        .height(56.dp)
                        .weight(1f)
                        .clip(RoundedCornerShape(50))
                        .border(1.dp, JogColors.Line, RoundedCornerShape(50))
                        .clickable { step-- },
                    contentAlignment = Alignment.Center,
                ) { Text("上一步", color = JogColors.Text, style = MaterialTheme.typography.labelLarge) }
            }
            Box(
                Modifier
                    .height(56.dp)
                    .weight(2f)
                    .clip(RoundedCornerShape(50))
                    .background(JogColors.sunriseH)
                    .clickable { if (step < last) step++ else finish() },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (step < last) "下一步" else "開始使用",
                    color = Color(0xFF1A0E08), style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@Composable
private fun Summary() {
    val s by Prefs.settings.collectAsStateWithLifecycle()
    JogCard(Modifier.fillMaxWidth()) {
        SummaryRow("身高體重", "${s.heightCm} 公分・${s.weightKg} 公斤")
        SummaryRow("角色體型", BodyShape.describe(s.heightCm, s.weightKg))
        SummaryRow("陪跑角色", s.mascot.label)
        SummaryRow("每次運動", "${s.durationMin} 分鐘・${s.cadence} 步/分（約 ${Format.thousands(s.durationMin.toLong() * s.cadence)} 步）")
        SummaryRow(
            "每日提醒",
            if (s.dailyReminderOn) "每天 ${DailyReminder.timeText(s.dailyHour, s.dailyMinute)}" else "不提醒",
        )
    }
    Spacer(Modifier.height(10.dp))
    Text(
        "這些都可以到「設定 → 個人資料與目標」修改，也能在「清除資料」重新執行這個設定流程。",
        color = JogColors.Muted, style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(8.dp).padding(top = 0.dp).clip(CircleShape).background(JogColors.Sun2).align(Alignment.CenterVertically))
        Spacer(Modifier.width(10.dp))
        Text(label, color = JogColors.Muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(76.dp))
        Text(value, color = JogColors.Text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}
