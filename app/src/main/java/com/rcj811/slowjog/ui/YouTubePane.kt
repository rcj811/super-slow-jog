package com.rcj811.slowjog.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.graphics.Bitmap
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.rcj811.slowjog.YtItem
import kotlin.math.roundToInt

/**
 * YouTube 官方嵌入式播放器（IFrame Player API）包在 WebView 裡。
 * 以 App 套件名稱作為嵌入來源（origin / referrer），避免 YouTube 的「播放器設定錯誤」。
 */
class YtController(context: Context, items: List<YtItem>, shuffle: Boolean) {
    private val main = Handler(Looper.getMainLooper())
    val order: List<YtItem> = if (shuffle) items.shuffled() else items
    var index by mutableIntStateOf(0)
        private set
    var ready by mutableStateOf(false)
        private set
    var errorText by mutableStateOf<String?>(null)
        private set
    private var wantPlay = false
    private var volume = 60
    private var consecutiveErrors = 0
    private var lastConsoleError: String? = null

    /** 播放器狀態文字 */
    var stateText by mutableStateOf("載入中…")
        private set

    @SuppressLint("SetJavaScriptEnabled")
    val web: WebView = WebView(context).apply {
        setBackgroundColor(android.graphics.Color.BLACK)
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        webChromeClient = object : WebChromeClient() {
            // 沒有封面時用黑色小圖，避免出現灰色播放圖示
            override fun getDefaultVideoPoster(): Bitmap? =
                super.getDefaultVideoPoster() ?: Bitmap.createBitmap(1, 1, Bitmap.Config.RGB_565)

            override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                if (m.messageLevel() == ConsoleMessage.MessageLevel.ERROR) lastConsoleError = m.message()
                return true
            }
        }
        webViewClient = WebViewClient()
        addJavascriptInterface(Bridge(), "Android")
        val firstId = order.firstOrNull()?.id ?: ""
        loadDataWithBaseURL(ORIGIN, HTML.replace("__VIDEO_ID__", firstId), "text/html", "utf-8", null)
        // 15 秒內播放器沒準備好就顯示原因
        main.postDelayed({
            if (!ready) {
                errorText = "YouTube 播放器載入失敗，請確認網路連線" +
                    (lastConsoleError?.let { "（$it）" } ?: "")
            }
        }, 15_000)
    }



    val current: YtItem? get() = order.getOrNull(index)

    private inner class Bridge {
        @JavascriptInterface fun onReady() = main.post {
            ready = true
            errorText = null
            stateText = "已就緒"
            js("vol($volume)")
            if (index == 0) {
                if (wantPlay) js("play()")
            } else {
                loadCurrent()
            }
            Unit
        }

        @JavascriptInterface fun onState(state: Int) = main.post {
            stateText = when (state) {
                -1 -> "準備中…"
                0 -> "播放完畢"
                1 -> "播放中"
                2 -> "已暫停"
                3 -> "緩衝中…"
                5 -> "已載入"
                else -> stateText
            }
            if (state == 0) next() // 播完 → 下一首
            if (state == 1) {
                consecutiveErrors = 0
                errorText = null
            }
            Unit
        }

        @JavascriptInterface fun onError(code: Int) = main.post {
            consecutiveErrors++
            errorText = when (code) {
                101, 150 -> "這部影片不允許在其他 App 播放，已跳到下一首"
                100 -> "找不到這部影片，已跳到下一首"
                else -> "影片無法播放（$code），已跳到下一首"
            }
            if (consecutiveErrors >= order.size) {
                errorText = "清單中的影片都無法播放，請確認網址或網路連線"
            } else {
                next()
            }
            Unit
        }
    }

    private fun js(code: String) = main.post { runCatching { web.evaluateJavascript(code, null) } }

    private fun loadCurrent() {
        val id = current?.id ?: return
        js(if (wantPlay) "loadV('$id',true)" else "loadV('$id',false)")
    }

    fun setPlaying(play: Boolean) {
        wantPlay = play
        if (ready) js(if (play) "play()" else "pause()")
    }

    fun setVolume(v: Int) {
        volume = v.coerceIn(0, 100)
        if (ready) js("vol($volume)")
    }

    fun next() {
        if (order.isEmpty()) return
        index = (index + 1) % order.size
        if (ready) loadCurrent()
    }

    fun previous() {
        if (order.isEmpty()) return
        index = (index - 1 + order.size) % order.size
        if (ready) loadCurrent()
    }

    /** 播放器所在的最上層容器（整段跑步期間只掛上一次，旋轉螢幕不會拆下來） */
    var host: FrameLayout? = null
        private set

    fun attachTo(root: FrameLayout) {
        if (web.parent === root) { host = root; return }
        (web.parent as? ViewGroup)?.removeView(web)
        // 保留已算好的位置（Box 可能比這裡先完成定位）
        val lp = (web.layoutParams as? FrameLayout.LayoutParams)
            ?: FrameLayout.LayoutParams(1, 1, Gravity.TOP or Gravity.START)
        root.addView(web, lp)
        host = root
    }

    private var target = intArrayOf(0, 0, 1, 1)

    /**
     * 移動、縮放播放器到指定位置（相對於 host）。
     * 在版面計算當中直接改 LayoutParams 有時不會生效（旋轉後會保留舊尺寸），
     * 所以排到下一個時機套用，並在套用後確認實際大小，不對就再套一次。
     */
    fun place(left: Int, top: Int, w: Int, h: Int) {
        target = intArrayOf(left, top, w, h)
        web.post { applyPlacement(0) }
    }

    private fun applyPlacement(attempt: Int) {
        val (left, top, w, h) = target.toList()
        val lp = (web.layoutParams as? FrameLayout.LayoutParams)
            ?: FrameLayout.LayoutParams(w, h, Gravity.TOP or Gravity.START)
        if (lp.width != w || lp.height != h || lp.leftMargin != left || lp.topMargin != top) {
            lp.width = w
            lp.height = h
            lp.leftMargin = left
            lp.topMargin = top
            lp.gravity = Gravity.TOP or Gravity.START
            web.layoutParams = lp
        }
        web.requestLayout()
        (web.parent as? View)?.requestLayout()
        if (attempt < 5) {
            web.postDelayed({
                if (web.width != target[2] || web.height != target[3] ||
                    web.left != target[0] || web.top != target[1]
                ) applyPlacement(attempt + 1)
            }, 120)
        }
    }

    fun destroy() {
        host = null
        runCatching {
            (web.parent as? ViewGroup)?.removeView(web)
            web.stopLoading()
            web.loadUrl("about:blank")
            web.destroy()
        }
    }

    companion object {
        const val ORIGIN = "https://com.rcj811.slowjog"

        private val HTML = """
<!DOCTYPE html><html><head>
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="referrer" content="strict-origin-when-cross-origin">
<style>html,body{margin:0;padding:0;background:#000;height:100%;overflow:hidden}#p{position:absolute;top:0;left:0;width:100%;height:100%}</style>
</head><body><div id="p"></div>
<script src="https://www.youtube.com/iframe_api" defer></script>
<script>
var player=null, ready=false;
function onYouTubeIframeAPIReady(){
  var cfg={width:'100%',height:'100%',
    playerVars:{playsinline:1,controls:1,rel:0,fs:0,enablejsapi:1,iv_load_policy:3,origin:'$ORIGIN'},
    events:{
      onReady:function(){ready=true;Android.onReady();},
      onStateChange:function(e){Android.onState(e.data);},
      onError:function(e){Android.onError(e.data);}
    }};
  if('__VIDEO_ID__'){cfg.videoId='__VIDEO_ID__';}
  player=new YT.Player('p',cfg);
}
function loadV(id,auto){ if(!ready)return; if(auto){player.loadVideoById(id);}else{player.cueVideoById(id);} }
function play(){ if(ready)player.playVideo(); }
function pause(){ if(ready)player.pauseVideo(); }
function vol(v){ if(!ready)return; player.setVolume(v); if(v>0){player.unMute();}else{player.mute();} }
</script></body></html>
""".trimIndent()
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * 跑步畫面裡的 YouTube 播放器。
 *
 * WebView 不放在 Compose 畫面裡繪製（在 Compose 裡 WebView 影片會變成黑畫面、甚至蓋住旁邊的文字），
 * 而是在跑步開始時加到 Activity 的最上層（只加一次），這個 Box 只負責告訴它位置與大小。
 * 旋轉螢幕時直向／橫向版面會換一個 Box，但 WebView 不會被拆下，只會移動、縮放，所以畫面不會消失。
 * [hidden] 為 true（例如倒數中）時暫時隱藏，讓 Compose 的內容顯示出來。
 */
@Composable
fun YouTubePlayerBox(
    controller: YtController,
    playing: Boolean,
    volume: Int,
    modifier: Modifier = Modifier,
    hidden: Boolean = false,
) {
    LaunchedEffect(playing, controller.ready) { controller.setPlaying(playing) }
    LaunchedEffect(volume, controller.ready) { controller.setVolume(volume) }

    val web = controller.web
    val context = LocalContext.current
    val root = remember(context) { context.findActivity()?.findViewById<FrameLayout>(android.R.id.content) }
    LaunchedEffect(hidden) { web.visibility = if (hidden) View.INVISIBLE else View.VISIBLE }

    Box(
        modifier
            .background(androidx.compose.ui.graphics.Color.Black)
            .onGloballyPositioned { coords ->
                if (root == null) return@onGloballyPositioned
                val pos = coords.positionInWindow()
                val loc = IntArray(2)
                root.getLocationInWindow(loc)
                val w = coords.size.width.coerceAtLeast(1)
                val h = coords.size.height.coerceAtLeast(1)
                val left = pos.x.roundToInt() - loc[0]
                val top = pos.y.roundToInt() - loc[1]
                controller.place(left, top, w, h)
            },
    ) {
        if (root == null) {
            // 找不到 Activity 時退回在 Compose 內顯示
            AndroidView(
                factory = {
                    (web.parent as? ViewGroup)?.removeView(web)
                    web
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** 曲目資訊與上一首／下一首（放在播放器下方，不會被影片蓋住） */
@Composable
fun YouTubeTrackBar(controller: YtController, modifier: Modifier = Modifier) {
    val cur = controller.current
    Row(
        modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
            .background(JogColors.Surface)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TrackButton(Glyph.LEFT, "上一首", leading = true) { controller.previous() }
        Spacer(Modifier.width(12.dp))
        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            Text(
                if (cur != null) "第 ${controller.index + 1} / ${controller.order.size} 首・${controller.stateText}" else "",
                color = JogColors.Sun1, style = MaterialTheme.typography.labelMedium, maxLines = 1,
            )
            Text(
                controller.errorText ?: cur?.display.orEmpty(),
                color = if (controller.errorText != null) JogColors.Sun3 else JogColors.Text,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        TrackButton(Glyph.RIGHT, "下一首", leading = false) { controller.next() }
    }
}

@Composable
private fun TrackButton(glyph: Glyph, label: String, leading: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .height(52.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
            .background(JogColors.SurfaceHigh)
            .border(1.dp, JogColors.Sun2.copy(alpha = 0.5f), androidx.compose.foundation.shape.RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading) GlyphIcon(glyph, JogColors.Sun1, Modifier.size(22.dp))
        Text(
            label, color = JogColors.Text, style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        if (!leading) GlyphIcon(glyph, JogColors.Sun1, Modifier.size(22.dp))
    }
}

/** 建立並在離開畫面時釋放播放器 */
@Composable
fun rememberYtController(items: List<YtItem>, shuffle: Boolean): YtController {
    val ctx = LocalContext.current
    val c = remember { YtController(ctx, items, shuffle) }
    DisposableEffect(c) {
        ctx.findActivity()?.findViewById<FrameLayout>(android.R.id.content)?.let { c.attachTo(it) }
        onDispose { c.destroy() }
    }
    return c
}
