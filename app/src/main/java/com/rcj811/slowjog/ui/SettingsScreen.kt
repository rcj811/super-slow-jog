package com.rcj811.slowjog.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rcj811.slowjog.Announcer
import com.rcj811.slowjog.BeatSound
import com.rcj811.slowjog.Calories
import com.rcj811.slowjog.Prefs
import com.rcj811.slowjog.PreviewPlayer
import com.rcj811.slowjog.ScreenMode
import com.rcj811.slowjog.VoiceGender
import com.rcj811.slowjog.VoiceOption
import java.util.Locale

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val s by Prefs.settings.collectAsStateWithLifecycle()
    val playing by PreviewPlayer.playing.collectAsStateWithLifecycle()

    // 語音引擎（試聽與列出可用聲音）
    var ttsReady by remember { mutableIntStateOf(0) }
    val announcer = remember { Announcer(ctx) { ttsReady++ } }
    DisposableEffect(Unit) {
        onDispose { announcer.shutdown(); PreviewPlayer.stop() }
    }
    val voices: List<VoiceOption> = remember(ttsReady) { announcer.voiceOptions() }
    var showVoiceList by remember { mutableStateOf(false) }

    fun sayDemo() {
        announcer.applyVoice(Prefs.settings.value)
        announcer.speak("已經跑了 5 分鐘，還剩 25 分鐘。保持微笑，繼續加油！", flush = true)
    }

    Column(
        Modifier
            .fillMaxSize()
            .wrapContentWidth(Alignment.CenterHorizontally)
            .widthIn(max = 720.dp)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        ScreenTitle("設定")

        // ---- 音量 ----
        JogCard {
            SectionLabel("音量")
            Spacer(Modifier.height(10.dp))
            VolumeControls(
                onBeatTest = { PreviewPlayer.play(Prefs.settings.value, millis = 1500) },
                onVoiceTest = { sayDemo() },
            )
        }

        // ---- 節拍聲音 ----
        JogCard {
            SectionLabel("節拍聲音（點一下試聽）")
            Spacer(Modifier.height(10.dp))
            BeatSound.entries.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                    row.forEach { snd ->
                        SoundCard(
                            snd, selected = s.beatSound == snd, playing = playing == snd,
                            modifier = Modifier.weight(1f),
                        ) {
                            Prefs.update(ctx) { it.copy(beatSound = snd) }
                            PreviewPlayer.play(Prefs.settings.value, snd)
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            SwitchRow("左右腳不同音高", "幫助分辨左右腳節奏", s.alternateTone) { v -> Prefs.update(ctx) { it.copy(alternateTone = v) } }
        }

        // ---- 語音提醒 ----
        JogCard {
            SectionLabel("語音提醒")
            SwitchRow("語音播報", "倒數、每 5 分鐘進度、完成時播報", s.voiceOn) { v -> Prefs.update(ctx) { it.copy(voiceOn = v) } }
            if (s.voiceOn) {
                Spacer(Modifier.height(6.dp))
                Text("聲音", color = JogColors.Text, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                Segmented(
                    VoiceGender.entries.map { it.label },
                    VoiceGender.entries.indexOf(s.voiceGender),
                    { i -> Prefs.update(ctx) { it.copy(voiceGender = VoiceGender.entries[i], voiceName = "") }; sayDemo() },
                    Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val current = voices.firstOrNull { it.name == s.voiceName }?.label ?: "自動（依男女聲挑選）"
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(JogColors.SurfaceHigh)
                            .clickable { showVoiceList = !showVoiceList }
                            .padding(12.dp)
                    ) {
                        Text("引擎聲音", color = JogColors.Muted, style = MaterialTheme.typography.labelMedium)
                        Text(current, color = JogColors.Text, style = MaterialTheme.typography.bodyLarge)
                    }
                    Spacer(Modifier.width(10.dp))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(JogColors.Sun2.copy(alpha = 0.18f))
                            .border(1.dp, JogColors.Sun2.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                            .clickable { sayDemo() }
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) { Text("試聽", color = JogColors.Sun1, style = MaterialTheme.typography.labelLarge) }
                }
                if (showVoiceList) {
                    Spacer(Modifier.height(8.dp))
                    VoiceItem("自動（依男女聲挑選）", s.voiceName.isEmpty()) {
                        Prefs.update(ctx) { it.copy(voiceName = "") }; showVoiceList = false; sayDemo()
                    }
                    voices.forEach { v ->
                        VoiceItem(v.label, s.voiceName == v.name) {
                            Prefs.update(ctx) { it.copy(voiceName = v.name, voiceGender = v.gender ?: it.voiceGender) }
                            showVoiceList = false; sayDemo()
                        }
                    }
                }
                if (ttsReady > 0 && !announcer.chineseAvailable) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "這支手機尚未安裝中文語音，會改用提示音。可到系統「文字轉語音」下載中文（台灣）語音。",
                        color = JogColors.Sun1, style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "開啟文字轉語音設定 ›", color = JogColors.Sky, style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.clickable {
                            runCatching {
                                ctx.startActivity(Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                        },
                    )
                } else if (voices.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "若男女聲差異不明顯，可在「引擎聲音」挑選其他聲音；手機語音引擎沒有男聲時會用較低音調模擬。",
                        color = JogColors.Faint, style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = JogColors.Line)
            SwitchRow("每 5 分鐘提醒", "播報已跑時間與剩餘時間", s.reminderOn) { v -> Prefs.update(ctx) { it.copy(reminderOn = v) } }
            SwitchRow("開始前倒數 3 秒", null, s.countdownOn) { v -> Prefs.update(ctx) { it.copy(countdownOn = v) } }
            SwitchRow("震動提醒", "倒數、里程碑、完成時震動", s.vibrateOn) { v -> Prefs.update(ctx) { it.copy(vibrateOn = v) } }
        }

        // ---- 個人 ----
        JogCard {
            SectionLabel("個人資料（計算卡路里）")
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("體重", color = JogColors.Text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                RepeatButton("−", { Prefs.update(ctx) { it.copy(weightKg = it.weightKg - 1) } }, size = 40.dp)
                Text("${s.weightKg} kg", color = JogColors.Text, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 14.dp))
                RepeatButton("+", { Prefs.update(ctx) { it.copy(weightKg = it.weightKg + 1) } }, size = 40.dp)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                String.format(
                    Locale.US,
                    "以 METs 估算：180 步/分 ≈ %.1f METs，%d kg 跑 30 分鐘約 %.0f 大卡。實際消耗依個人體能而異。",
                    Calories.mets(180), s.weightKg, Calories.kcal(180, s.weightKg, 30 * 60_000L),
                ),
                color = JogColors.Faint, style = MaterialTheme.typography.bodyMedium,
            )
        }

        // ---- 螢幕 ----
        JogCard {
            SectionLabel("畫面方向")
            Spacer(Modifier.height(10.dp))
            Segmented(
                ScreenMode.entries.map { it.label },
                ScreenMode.entries.indexOf(s.screenMode),
                { i -> Prefs.update(ctx) { it.copy(screenMode = ScreenMode.entries[i]) } },
                Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "橫向適合放在跑步機或桌上；跑步畫面右上角也能快速切換。",
                color = JogColors.Faint, style = MaterialTheme.typography.bodyMedium,
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = JogColors.Line)
            SwitchRow("跑步時螢幕保持開啟", "關閉後鎖螢幕也會繼續計時與播放節拍", s.keepScreenOn) { v -> Prefs.update(ctx) { it.copy(keepScreenOn = v) } }
        }

        Text(
            "節拍聲皆由 App 即時合成，可與音樂同時播放。",
            color = JogColors.Faint, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
        )
    }
}

@Composable
private fun sliderColors() = SliderDefaults.colors(
    thumbColor = JogColors.Sun1, activeTrackColor = JogColors.Sun2, inactiveTrackColor = JogColors.SurfaceHigh,
)

@Composable
private fun SoundCard(snd: BeatSound, selected: Boolean, playing: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) JogColors.Sun2.copy(alpha = 0.14f) else JogColors.SurfaceHigh)
            .border(1.dp, if (selected) JogColors.Sun2 else JogColors.Line, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(snd.label, color = if (selected) JogColors.Sun1 else JogColors.Text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (playing) GlyphIcon(Glyph.SOUND, JogColors.Sun1, Modifier.size(18.dp))
        }
        Spacer(Modifier.height(2.dp))
        Text(snd.desc, color = JogColors.Muted, style = MaterialTheme.typography.bodyMedium, minLines = 2, maxLines = 2)
    }
}

@Composable
private fun VoiceItem(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) JogColors.Sun2.copy(alpha = 0.14f) else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = if (selected) JogColors.Sun1 else JogColors.Text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (selected) Text("✓", color = JogColors.Sun1)
    }
}
