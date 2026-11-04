package com.rcj811.slowjog

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.PointF
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
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** 頭像裁切圓（照片像素座標） */
data class CropCircle(val cx: Float, val cy: Float, val r: Float) {
    companion object {
        fun minRadius(w: Float, h: Float): Float {
            val maxR = min(w, h) / 2f
            return min(maxR, max(24f, min(w, h) * 0.06f))
        }

        /** 讓圓圈保持在照片範圍內 */
        fun clamp(c: CropCircle, w: Float, h: Float): CropCircle {
            val maxR = min(w, h) / 2f
            val r = c.r.coerceIn(minRadius(w, h), maxR)
            return CropCircle(c.cx.coerceIn(r, w - r), c.cy.coerceIn(r, h - r), r)
        }
    }
}

/** 等待使用者調整的原始照片 */
class AvatarSource(val bitmap: Bitmap, val crop: CropCircle)

/**
 * 「我的照片」角色的頭像。
 * 流程：從相簿挑照片 → 轉正、縮小後保存原圖 → 使用者用圓圈選位置 → 裁切存成頭像。
 * 原圖保留在 App 內，之後可以再「調整位置」。全部只存在手機本機，不會上傳。
 */
object AvatarStore {
    private const val SIZE = 384
    private const val SOURCE_MAX = 1600
    private val _image = MutableStateFlow<ImageBitmap?>(null)
    val image: StateFlow<ImageBitmap?> = _image.asStateFlow()
    @Volatile private var loaded = false

    private fun file(context: Context) = File(context.filesDir, "avatar.png")
    private fun srcFile(context: Context) = File(context.filesDir, "avatar_src.jpg")
    private fun cropFile(context: Context) = File(context.filesDir, "avatar_crop.txt")

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val f = file(context)
        if (f.exists()) {
            _image.value = runCatching { BitmapFactory.decodeFile(f.path)?.asImageBitmap() }.getOrNull()
        }
    }

    fun hasSource(context: Context) = srcFile(context).exists()

    fun clear(context: Context) {
        runCatching { file(context).delete() }
        runCatching { srcFile(context).delete() }
        runCatching { cropFile(context).delete() }
        _image.value = null
    }

    /** 讀取剛選的照片（轉正、縮小）；按下「使用」後才會取代目前的頭像 */
    suspend fun loadFromUri(context: Context, uri: Uri): AvatarSource? = withContext(Dispatchers.IO) {
        runCatching {
            val cr = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= SOURCE_MAX) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            var bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: return@runCatching null

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
            // 再縮到長邊不超過 SOURCE_MAX
            val big = max(bmp.width, bmp.height)
            if (big > SOURCE_MAX) {
                val k = SOURCE_MAX.toFloat() / big
                m.postScale(k, k)
            }
            if (!m.isIdentity) bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)

            AvatarSource(bmp, suggest(bmp))
        }.getOrNull()
    }

    /** 讀取之前保存的原圖與上次的圓圈位置 */
    suspend fun loadSaved(context: Context): AvatarSource? = withContext(Dispatchers.IO) {
        runCatching {
            val f = srcFile(context)
            if (!f.exists()) return@runCatching null
            val bmp = BitmapFactory.decodeFile(f.path) ?: return@runCatching null
            val saved = runCatching {
                val p = cropFile(context).readText().trim().split(",").map { it.toFloat() }
                CropCircle(p[0], p[1], p[2])
            }.getOrNull()
            AvatarSource(bmp, saved ?: suggest(bmp))
        }.getOrNull()
    }

    /** 依使用者選的圓圈裁切並存成頭像 */
    suspend fun applyCrop(context: Context, src: AvatarSource, crop: CropCircle): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val bmp = src.bitmap
            val c = CropCircle.clamp(crop, bmp.width.toFloat(), bmp.height.toFloat())
            val side = (2 * c.r).roundToInt().coerceIn(1, min(bmp.width, bmp.height))
            val left = (c.cx - c.r).roundToInt().coerceIn(0, bmp.width - side)
            val top = (c.cy - c.r).roundToInt().coerceIn(0, bmp.height - side)
            val square = Bitmap.createBitmap(bmp, left, top, side, side)
            val out = Bitmap.createScaledBitmap(square, SIZE, SIZE, true)
            file(context).outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
            // 保存原圖，之後可以再「調整位置」
            val tmp = File(context.filesDir, "avatar_src.tmp")
            tmp.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            if (!tmp.renameTo(srcFile(context))) { tmp.copyTo(srcFile(context), overwrite = true); tmp.delete() }
            cropFile(context).writeText(String.format(Locale.US, "%.1f,%.1f,%.1f", c.cx, c.cy, c.r))
            _image.value = out.asImageBitmap()
            true
        }.getOrDefault(false)
    }

    /**
     * 圓圈的起始位置：用系統內建的人臉偵測把臉放在中間；
     * 找不到（例如寵物照）就放在偏上方的正中央。使用者之後可以自己移動。
     */
    private fun suggest(bmp: Bitmap): CropCircle {
        val w = bmp.width.toFloat()
        val h = bmp.height.toFloat()
        val full = min(w, h)
        var circle = CropCircle(w / 2f, full / 2f + (h - full) * 0.3f, full / 2f * 0.85f)
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
            circle = CropCircle(mid.x / scale, mid.y / scale + eyes * 0.35f, eyes * 1.8f)
        }
        return CropCircle.clamp(circle, w, h)
    }
}
