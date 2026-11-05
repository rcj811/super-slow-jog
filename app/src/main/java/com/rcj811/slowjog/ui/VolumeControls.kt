package com.rcj811.slowjog.ui

import android.content.Context
import android.media.AudioManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rcj811.slowjog.Prefs
import com.rcj811.slowjog.PreviewPlayer
import com.rcj811.slowjog.Settings
import kotlinx.coroutines.delay

private fun pct(v: Float) = "${Math.round(v * 100)}%"

@Composable
private fun volumeSliderColors(boost: Boolean = false) = SliderDefaults.colors(
    thumbColor = if (boost) JogColors.Sun3 else JogColors.Sun1,
    activeTrackColor = if (boost) JogColors.Sun3 else JogColors.Sun2,
    inactiveTrackColor = JogColors.SurfaceHigh,
)

/**
 * 音量控制：統一音量開關、節拍音量（0–150%）、語音音量（0–100%）、裝置音量。
 * 統一音量開啟時，所有聲音一樣大，只由裝置音量控制，其他滑桿變灰停用。
 */
@Composable
fun VolumeControls(onBeatTest: (() -> Unit)?, onVoiceTest: (() -> Unit)?) {
    val ctx = LocalContext.current
    val s by Prefs.settings.collectAsStateWithLifecycle()

    val locked = s.unifiedVolume
    Column {
        SwitchRow(
            "統一音量",
            "節拍、語音、YouTube 一樣大聲，只用下方「裝置音量」（或手機音量鍵）調整",
            locked,
        ) { v -> Prefs.update(ctx) { it.copy(unifiedVolume = v) }; PreviewPlayer.update(Prefs.settings.value) }
        Spacer(Modifier.height(8.dp))
        // 節拍、語音（統一音量時反白停用）
        Column(Modifier.alpha(if (locked) 0.35f else 1f)) {
        // 節拍
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlyphIcon(Glyph.SOUND, JogColors.Sun1, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("節拍音量", color = JogColors.Text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(
                pct(s.volume) + if (s.volume > 1f) " 加強" else "",
                color = if (s.volume > 1f) JogColors.Sun3 else JogColors.Sun1,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Slider(
            value = s.volume,
            enabled = !locked,
            onValueChange = { v ->
                val snapped = Math.round(v * 20) / 20f // 5% 一格
                Prefs.update(ctx) { it.copy(volume = snapped) }
                PreviewPlayer.update(Prefs.settings.value)
            },
            onValueChangeFinished = { onBeatTest?.invoke() },
            valueRange = 0f..Settings.MAX_BEAT_VOLUME,
            colors = volumeSliderColors(s.volume > 1f),
        )
        Row {
            listOf(0.5f, 0.8f, 1.0f, 1.3f, 1.5f).forEach { v ->
                QuickPill(pct(v), selected = !locked && kotlin.math.abs(s.volume - v) < 0.01f, enabled = !locked) {
                    Prefs.update(ctx) { it.copy(volume = v) }
                    PreviewPlayer.update(Prefs.settings.value)
                    onBeatTest?.invoke()
                }
                Spacer(Modifier.width(6.dp))
            }
        }

        Spacer(Modifier.height(14.dp))
        // 語音
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🗣", modifier = Modifier.width(28.dp))
            Text("語音音量", color = JogColors.Text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(pct(s.voiceVolume), color = JogColors.Sun1, style = MaterialTheme.typography.labelLarge)
        }
        Slider(
            value = s.voiceVolume,
            enabled = !locked,
            onValueChange = { v -> Prefs.update(ctx) { it.copy(voiceVolume = Math.round(v * 20) / 20f) } },
            onValueChangeFinished = { onVoiceTest?.invoke() },
            valueRange = 0f..1f,
            colors = volumeSliderColors(),
        )

        Spacer(Modifier.height(14.dp))
        YouTubeVolumeSlider()
        }

        Spacer(Modifier.height(14.dp))
        PhoneMediaVolume()
        Text(
            (if (locked) "統一音量開啟中：本 App 的聲音都由「裝置音量」控制，上方的個別音量已停用。"
            else "個別音量是「裝置音量」的比例。節拍超過 100% 會加強但不破音。") +
                "\n裝置音量是整支手機共用的媒體音量，在這裡調整後，瀏覽器、YouTube App、音樂等其他 App 也會一起變大或變小；統一音量開關本身只影響本 App。",
            color = JogColors.Faint, style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** 直接調整裝置（媒體）音量，並跟著實體音量鍵同步 */
@Composable
fun PhoneMediaVolume() {
    val ctx = LocalContext.current
    val am = remember { ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val max = remember { am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var cur by remember { mutableIntStateOf(am.getStreamVolume(AudioManager.STREAM_MUSIC)) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(400)
            cur = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        }
    }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlyphIcon(Glyph.PHONE, JogColors.Muted, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("裝置音量", color = JogColors.Text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text("$cur / $max", color = JogColors.Muted, style = MaterialTheme.typography.labelLarge)
        }
        Slider(
            value = cur.toFloat(),
            onValueChange = { v ->
                val n = Math.round(v)
                if (n != cur) {
                    runCatching { am.setStreamVolume(AudioManager.STREAM_MUSIC, n, 0) }
                    cur = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                }
            },
            valueRange = 0f..max.toFloat(),
            steps = (max - 1).coerceAtLeast(0),
            colors = SliderDefaults.colors(
                thumbColor = JogColors.Text, activeTrackColor = JogColors.Muted, inactiveTrackColor = JogColors.SurfaceHigh,
                activeTickColor = androidx.compose.ui.graphics.Color.Transparent,
                inactiveTickColor = androidx.compose.ui.graphics.Color.Transparent,
            ),
        )
    }
}

@Composable
private fun QuickPill(text: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Text(
        text,
        color = if (selected) JogColors.Sun1 else JogColors.Muted,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) JogColors.Sun2.copy(alpha = 0.18f) else JogColors.SurfaceHigh)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

/** 跑步中的音量面板 */
@Composable
fun VolumeDialog(onDismiss: () -> Unit, onVoiceTest: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = JogColors.SurfaceHigh,
        title = { Text("音量") },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                VolumeControls(onBeatTest = null, onVoiceTest = onVoiceTest)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("好", color = JogColors.Sun1) } },
    )
}
