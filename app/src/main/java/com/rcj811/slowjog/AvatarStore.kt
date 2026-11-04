package com.rcj811.slowjog

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.Rect
import android.media.ExifInterface
import android.media.FaceDetector
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 「我的照片」角色的頭像：從相簿挑照片 → 轉正 → 自動找臉裁成正方形 → 存在 App 內。
 * 只存在手機本機，不會上傳。
 */
object AvatarStore {
    private const val SIZE = 384
    private val _image = MutableStateFlow<ImageBitmap?>(null)
    val image: StateFlow<ImageBitmap?> = _image.asStateFlow()
    @Volatile private var loaded = false

    private fun file(context: Context) = File(context.filesDir, "avatar.png")

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val f = file(context)
        if (f.exists()) {
            _image.value = runCatching { BitmapFactory.decodeFile(f.path)?.asImageBitmap() }.getOrNull()
        }
    }

    fun clear(context: Context) {
        runCatching { file(context).delete() }
        _image.value = null
    }

    /** 匯入照片；成功回傳 true */
    suspend fun importFrom(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val cr = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching false

            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1200) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            var bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: return@runCatching false

            // 依 EXIF 轉正
            val orientation = runCatching {
                cr.openInputStream(uri)?.use {
                    ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                }
            }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
            val m = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            }
            if (!m.isIdentity) bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)

            val crop = faceCrop(bmp)
            val square = Bitmap.createBitmap(bmp, crop.left, crop.top, crop.width(), crop.height())
            val out = Bitmap.createScaledBitmap(square, SIZE, SIZE, true)
            file(context).outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
            _image.value = out.asImageBitmap()
            true
        }.getOrDefault(false)
    }

    /**
     * 找出要裁切的正方形：先用系統內建的人臉偵測把臉放在中間；
     * 找不到（例如寵物照）就取偏上方的正中央，因為臉通常在照片上半部。
     */
    private fun faceCrop(bmp: Bitmap): Rect {
        val w = bmp.width
        val h = bmp.height
        val full = min(w, h)
        var rect = Rect((w - full) / 2, ((h - full) * 0.3f).roundToInt(), (w - full) / 2 + full, ((h - full) * 0.3f).roundToInt() + full)
        runCatching {
            val scale = 480f / max(w, h)
            val sw = ((w * scale).toInt() / 2 * 2).coerceAtLeast(2)
            val sh = (h * scale).toInt().coerceAtLeast(2)
            val small = Bitmap.createScaledBitmap(bmp, sw, sh, true).copy(Bitmap.Config.RGB_565, false)
            val faces = arrayOfNulls<FaceDetector.Face>(4)
            val n = FaceDetector(sw, sh, faces.size).findFaces(small, faces)
            val face = faces.take(n).filterNotNull().maxByOrNull { it.eyesDistance() } ?: return@runCatching
            val mid = PointF()
            face.getMidPoint(mid)
            val eyes = face.eyesDistance() / scale
            val cx = mid.x / scale
            val cy = mid.y / scale + eyes * 0.35f
            val side = (eyes * 3.6f).coerceIn(min(64f, full.toFloat()), full.toFloat()).roundToInt()
            val left = (cx - side / 2f).roundToInt().coerceIn(0, w - side)
            val top = (cy - side / 2f).roundToInt().coerceIn(0, h - side)
            rect = Rect(left, top, left + side, top + side)
        }
        return rect
    }
}
