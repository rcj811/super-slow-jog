package com.rcj811.slowjog.ui

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rcj811.slowjog.Prefs
import com.rcj811.slowjog.YouTubeStore
import kotlinx.coroutines.launch

/** YouTube 音量滑桿（設定頁與跑步中的音量面板共用） */
@Composable
fun YouTubeVolumeSlider() {
    val ctx = LocalContext.current
    val s by Prefs.settings.collectAsStateWithLifecycle()
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("▶", color = Color(0xFFFF4E45), modifier = Modifier.width(28.dp))
            Text("YouTube 音量", color = JogColors.Text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(if (s.unifiedVolume) "統一" else "${s.ytVolume}%", color = if (s.unifiedVolume) JogColors.Faint else JogColors.Sun1, style = MaterialTheme.typography.labelLarge)
        }
        Slider(
            value = s.ytVolume.toFloat(),
            enabled = !s.unifiedVolume,
            onValueChange = { v -> Prefs.update(ctx) { it.copy(ytVolume = (Math.round(v / 5f) * 5)) } },
            valueRange = 0f..100f,
            colors = SliderDefaults.colors(
                thumbColor = JogColors.Sun1, activeTrackColor = JogColors.Sun2, inactiveTrackColor = JogColors.SurfaceHigh,
            ),
        )
    }
}

/** 設定頁：YouTube 播放清單 */
@Composable
fun YouTubeSettingsCard() {
    val ctx = LocalContext.current
    val s by Prefs.settings.collectAsStateWithLifecycle()
    val items by YouTubeStore.items.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }

    LaunchedEffect(items.size) { YouTubeStore.fillTitles(ctx) }

    fun addFrom(text: String) {
        val ids = YouTubeStore.extractAll(text)
        if (ids.isEmpty()) {
            Toast.makeText(ctx, "找不到 YouTube 網址，請貼上影片連結", Toast.LENGTH_SHORT).show()
            return
        }
        val n = YouTubeStore.add(ctx, ids)
        input = ""
        if (n > 0 && !Prefs.settings.value.ytOn) Prefs.update(ctx) { it.copy(ytOn = true) }
        Toast.makeText(ctx, if (n > 0) "已加入 $n 首" else "這些影片已經在清單裡", Toast.LENGTH_SHORT).show()
        scope.launch { YouTubeStore.fillTitles(ctx) }
    }

    JogCard {
        SectionLabel("YouTube 播放")
        SwitchRow(
            "跑步時播放 YouTube",
            if (items.isEmpty()) "先在下方加入影片網址" else "跑步畫面的角色區會換成 YouTube，上方顯示剩餘時間",
            s.ytOn && items.isNotEmpty(),
        ) { v ->
            if (v && items.isEmpty()) Toast.makeText(ctx, "請先加入 YouTube 影片", Toast.LENGTH_SHORT).show()
            else Prefs.update(ctx) { it.copy(ytOn = v) }
        }
        SwitchRow("隨機播放", "每次開始跑步時打亂順序", s.ytShuffle) { v -> Prefs.update(ctx) { it.copy(ytShuffle = v) } }
        SwitchRow("最小化或關螢幕時繼續播放", "切到其他 App 或關掉螢幕，YouTube 聲音不中斷（跑步中有效）", s.ytBackground) { v ->
            Prefs.update(ctx) { it.copy(ytBackground = v) }
        }
        Spacer(Modifier.height(4.dp))
        YouTubeVolumeSlider()
        Text(
            "YouTube 音量與節拍、語音分開調整；跑步中點右上角的喇叭也能調。",
            color = JogColors.Faint, style = MaterialTheme.typography.bodyMedium,
        )

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("貼上 YouTube 網址（可一次貼多個，一行一個）") },
            minLines = 2,
            maxLines = 6,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = JogColors.Sun2, unfocusedBorderColor = JogColors.Line,
                focusedLabelColor = JogColors.Sun1, unfocusedLabelColor = JogColors.Muted,
                cursorColor = JogColors.Sun1,
                focusedTextColor = JogColors.Text, unfocusedTextColor = JogColors.Text,
            ),
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionPill("從剪貼簿貼上", Modifier.weight(1f)) {
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val text = cm.primaryClip?.let { clip ->
                    (0 until clip.itemCount).joinToString("\n") { clip.getItemAt(it).coerceToText(ctx).toString() }
                }.orEmpty()
                if (text.isBlank()) Toast.makeText(ctx, "剪貼簿是空的", Toast.LENGTH_SHORT).show() else addFrom(text)
            }
            ActionPill("加入", Modifier.weight(1f), primary = true) { addFrom(input) }
        }

        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "播放清單（${items.size} 首）",
                color = JogColors.Text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f),
            )
            if (items.isNotEmpty()) {
                Text(
                    "清空", color = JogColors.Sun3, style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { confirmClear = true }.padding(8.dp),
                )
            }
        }
        if (items.isEmpty()) {
            Text(
                "還沒有影片。在 YouTube App 按「分享 → 複製連結」，回來按「從剪貼簿貼上」即可。",
                color = JogColors.Faint, style = MaterialTheme.typography.bodyMedium,
            )
        }
        items.forEachIndexed { i, item ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(JogColors.SurfaceHigh)
                    .padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${i + 1}", color = JogColors.Faint, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(30.dp))
                Text(
                    item.display, color = JogColors.Text, style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                SmallIconButton("↑", enabled = i > 0) { YouTubeStore.move(ctx, i, -1) }
                SmallIconButton("↓", enabled = i < items.size - 1) { YouTubeStore.move(ctx, i, 1) }
                SmallIconButton("✕") { YouTubeStore.remove(ctx, i) }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "使用 YouTube 官方嵌入播放器，需要網路；有些影片不允許在其他 App 播放，會自動跳到下一首。",
            color = JogColors.Faint, style = MaterialTheme.typography.bodyMedium,
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = JogColors.SurfaceHigh,
            title = { Text("清空播放清單？") },
            text = { Text("清單中的 ${items.size} 首影片都會移除。") },
            confirmButton = {
                TextButton(onClick = {
                    YouTubeStore.clear(ctx)
                    Prefs.update(ctx) { it.copy(ytOn = false) }
                    confirmClear = false
                }) { Text("清空", color = JogColors.Sun3) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消", color = JogColors.Text) } },
        )
    }
}

@Composable
private fun ActionPill(text: String, modifier: Modifier, primary: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier
            .height(46.dp)
            .clip(RoundedCornerShape(50))
            .then(if (primary) Modifier.background(JogColors.sunriseH) else Modifier.border(1.dp, JogColors.Line, RoundedCornerShape(50)))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (primary) Color(0xFF1A0E08) else JogColors.Text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun SmallIconButton(symbol: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, color = if (enabled) JogColors.Muted else JogColors.Line, style = MaterialTheme.typography.titleMedium)
    }
}
