package com.rcj811.slowjog.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.rcj811.slowjog.AvatarSource
import com.rcj811.slowjog.CropCircle
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 調整頭像：照片上有一個圓圈，拖曳移動、兩指縮放或用滑桿調大小，圓圈內就是頭像。
 */
@Composable
fun AvatarCropDialog(
    source: AvatarSource,
    onCancel: () -> Unit,
    onRepick: () -> Unit,
    onConfirm: (CropCircle) -> Unit,
) {
    val bmp = source.bitmap
    val img = remember(bmp) { bmp.asImageBitmap() }
    val bw = bmp.width.toFloat()
    val bh = bmp.height.toFloat()
    var crop by remember(bmp) { mutableStateOf(CropCircle.clamp(source.crop, bw, bh)) }
    val minR = CropCircle.minRadius(bw, bh)
    val maxR = min(bw, bh) / 2f

    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Color(0xFF0B0A09))
                .padding(16.dp),
        ) {
            Text("調整頭像", style = MaterialTheme.typography.headlineSmall, color = JogColors.Text)
            Text(
                "拖曳圓圈移動位置，兩指縮放或用下方滑桿調整大小",
                style = MaterialTheme.typography.bodyMedium, color = JogColors.Muted,
            )
            Spacer(Modifier.height(12.dp))

            BoxWithConstraints(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp)),
            ) {
                val viewW = constraints.maxWidth.toFloat()
                val viewH = constraints.maxHeight.toFloat()
                val scale = min(viewW / bw, viewH / bh)
                val dx = (viewW - bw * scale) / 2f
                val dy = (viewH - bh * scale) / 2f
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(bmp, scale) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val c = crop
                                crop = CropCircle.clamp(
                                    CropCircle(c.cx + pan.x / scale, c.cy + pan.y / scale, c.r * zoom), bw, bh,
                                )
                            }
                        }
                ) {
                    drawImage(
                        image = img,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(bmp.width, bmp.height),
                        dstOffset = IntOffset(dx.roundToInt(), dy.roundToInt()),
                        dstSize = IntSize((bw * scale).roundToInt(), (bh * scale).roundToInt()),
                        filterQuality = FilterQuality.Medium,
                    )
                    val center = Offset(dx + crop.cx * scale, dy + crop.cy * scale)
                    val r = crop.r * scale
                    // 圓圈外變暗
                    val scrim = Path().apply {
                        fillType = PathFillType.EvenOdd
                        addRect(Rect(dx, dy, dx + bw * scale, dy + bh * scale))
                        addOval(Rect(center, r))
                    }
                    drawPath(scrim, Color.Black.copy(alpha = 0.62f))
                    drawCircle(Color.White, r, center, style = Stroke(2.5f.dp.toPx()))
                    // 中心十字，方便對準
                    val cross = 7.dp.toPx()
                    drawLine(Color.White.copy(alpha = 0.7f), Offset(center.x - cross, center.y), Offset(center.x + cross, center.y), 1.5f.dp.toPx())
                    drawLine(Color.White.copy(alpha = 0.7f), Offset(center.x, center.y - cross), Offset(center.x, center.y + cross), 1.5f.dp.toPx())
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 即時預覽
                Canvas(
                    Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .border(2.dp, Color.White, CircleShape)
                ) {
                    val clip = Path().apply { addOval(Rect(Offset(size.width / 2, size.height / 2), size.minDimension / 2)) }
                    clipPath(clip) {
                        val side = (2 * crop.r).roundToInt().coerceIn(1, min(bmp.width, bmp.height))
                        val left = (crop.cx - crop.r).roundToInt().coerceIn(0, bmp.width - side)
                        val top = (crop.cy - crop.r).roundToInt().coerceIn(0, bmp.height - side)
                        drawImage(
                            image = img,
                            srcOffset = IntOffset(left, top),
                            srcSize = IntSize(side, side),
                            dstOffset = IntOffset.Zero,
                            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                            filterQuality = FilterQuality.Medium,
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("圓圈大小", color = JogColors.Muted, style = MaterialTheme.typography.labelMedium)
                    if (maxR - minR >= 1f) {
                        Slider(
                            value = crop.r.coerceIn(minR, maxR),
                            onValueChange = { v -> crop = CropCircle.clamp(crop.copy(r = v), bw, bh) },
                            valueRange = minR..maxR,
                            colors = SliderDefaults.colors(
                                thumbColor = JogColors.Sun1, activeTrackColor = JogColors.Sun2,
                                inactiveTrackColor = JogColors.SurfaceHigh,
                            ),
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlineAction("換一張", Modifier.weight(1f), onRepick)
                OutlineAction("取消", Modifier.weight(1f), onCancel)
                Box(
                    Modifier
                        .weight(1.4f)
                        .height(52.dp)
                        .clip(RoundedCornerShape(50))
                        .background(JogColors.sunriseH)
                        .clickable { onConfirm(crop) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("使用這個範圍", color = Color(0xFF1A0E08), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun OutlineAction(text: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(52.dp)
            .clip(RoundedCornerShape(50))
            .border(1.dp, JogColors.Line, RoundedCornerShape(50))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = JogColors.Text, style = MaterialTheme.typography.labelLarge)
    }
}
