package com.rcj811.slowjog.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rcj811.slowjog.AvatarStore
import com.rcj811.slowjog.Mascot
import com.rcj811.slowjog.Prefs
import kotlinx.coroutines.launch

/** 角色選擇格（3 欄）。選中的角色會跟著步頻原地跑。 */
@Composable
fun MascotPicker(modifier: Modifier = Modifier, columns: Int = 3) {
    val ctx = LocalContext.current
    val s by Prefs.settings.collectAsStateWithLifecycle()
    val avatar by AvatarStore.image.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var importing by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            importing = true
            scope.launch {
                val ok = AvatarStore.importFrom(ctx, uri)
                importing = false
                if (ok) Prefs.update(ctx) { it.copy(mascot = Mascot.PHOTO) }
                else Toast.makeText(ctx, "這張照片無法讀取，請換一張試試", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val pickPhoto = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    Column(modifier) {
        Mascot.entries.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                row.forEach { m ->
                    val selected = s.mascot == m
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (selected) JogColors.Sun2.copy(alpha = 0.14f) else JogColors.SurfaceHigh)
                            .border(if (selected) 2.dp else 1.dp, if (selected) JogColors.Sun2 else JogColors.Line, RoundedCornerShape(18.dp))
                            .clickable {
                                if (m == Mascot.PHOTO && avatar == null) pickPhoto()
                                else Prefs.update(ctx) { it.copy(mascot = m) }
                            }
                            .padding(top = 6.dp, bottom = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(Modifier.fillMaxWidth(0.9f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                            MascotView(m, Modifier.fillMaxWidth().aspectRatio(1f), moving = selected, cadence = s.cadence)
                            if (m == Mascot.PHOTO && importing) CircularProgressIndicator(color = JogColors.Sun1)
                        }
                        Text(
                            m.label,
                            color = if (selected) JogColors.Sun1 else JogColors.Text,
                            style = MaterialTheme.typography.labelLarge,
                        )
                        if (m == Mascot.PHOTO && avatar == null) {
                            Text("點這裡選照片", color = JogColors.Faint, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        if (avatar != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "我的照片：",
                    color = JogColors.Muted, style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "更換照片", color = JogColors.Sky, style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { pickPhoto() }.padding(8.dp),
                )
                Text(
                    "移除", color = JogColors.Muted, style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                        AvatarStore.clear(ctx)
                        if (Prefs.settings.value.mascot == Mascot.PHOTO) Prefs.update(ctx) { it.copy(mascot = Mascot.DUCK) }
                    }.padding(8.dp),
                )
            }
        }
        Text(
            "「我的照片」會自動找到臉並裁成圓形頭像（寵物照也可以），照片只存在這支手機裡。",
            color = JogColors.Faint, style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** 首頁的角色卡：角色跟著步頻原地跑，點一下換角色 */
@Composable
fun MascotHero(cadence: Int, previewing: Boolean, modifier: Modifier = Modifier) {
    val s by Prefs.settings.collectAsStateWithLifecycle()
    var showPicker by remember { mutableStateOf(false) }
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(JogColors.Surface)
            .border(1.dp, JogColors.Line.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
            .clickable { showPicker = true }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MascotView(
            s.mascot,
            Modifier.width(120.dp).height(120.dp),
            moving = true,
            cadence = cadence,
            phaseSource = { if (previewing && com.rcj811.slowjog.BeatClock.active) com.rcj811.slowjog.BeatClock.phase() else null },
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("${s.mascot.label}陪你跑", color = JogColors.Text, style = MaterialTheme.typography.titleMedium)
            Text("原地小步跑・$cadence 步/分", color = JogColors.Muted, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "換角色 ›", color = JogColors.Sun1, style = MaterialTheme.typography.labelLarge,
            )
        }
    }
    if (showPicker) {
        AlertDialog(
            onDismissRequest = { showPicker = false },
            containerColor = JogColors.Surface,
            title = { Text("選擇陪跑角色") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) { MascotPicker() }
            },
            confirmButton = { TextButton(onClick = { showPicker = false }) { Text("好", color = JogColors.Sun1) } },
        )
    }
}
