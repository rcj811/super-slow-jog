package com.rcj811.slowjog.ui

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.rcj811.slowjog.YtItem

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

    @SuppressLint("SetJavaScriptEnabled")
    val web: WebView = WebView(context).apply {
        setBackgroundColor(android.graphics.Color.BLACK)
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        webChromeClient = WebChromeClient()
        webViewClient = WebViewClient()
        addJavascriptInterface(Bridge(), "Android")
        loadDataWithBaseURL(ORIGIN + "/", HTML, "text/html", "utf-8", null)
    }

    val current: YtItem? get() = order.getOrNull(index)

    private inner class Bridge {
        @JavascriptInterface fun onReady() = main.post {
            ready = true
            js("vol($volume)")
            loadCurrent()
            Unit
        }

        @JavascriptInterface fun onState(state: Int) = main.post {
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

    fun destroy() {
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
<script>
var player=null, ready=false;
var tag=document.createElement('script'); tag.src='https://www.youtube.com/iframe_api';
document.head.appendChild(tag);
function onYouTubeIframeAPIReady(){
  player=new YT.Player('p',{width:'100%',height:'100%',
    playerVars:{playsinline:1,controls:1,rel:0,fs:0,origin:'$ORIGIN',widget_referrer:'$ORIGIN'},
    events:{
      onReady:function(){ready=true;Android.onReady();},
      onStateChange:function(e){Android.onState(e.data);},
      onError:function(e){Android.onError(e.data);}
    }});
}
function loadV(id,auto){ if(!ready)return; if(auto){player.loadVideoById(id);}else{player.cueVideoById(id);} }
function play(){ if(ready)player.playVideo(); }
function pause(){ if(ready)player.pauseVideo(); }
function vol(v){ if(!ready)return; player.setVolume(v); if(v>0){player.unMute();}else{player.mute();} }
</script></body></html>
""".trimIndent()
    }
}

/** 跑步畫面裡的 YouTube 播放器（WebView 由外部保存，旋轉螢幕不會重新載入） */
@Composable
fun YouTubePlayerBox(controller: YtController, playing: Boolean, volume: Int, modifier: Modifier = Modifier) {
    LaunchedEffect(playing, controller.ready) { controller.setPlaying(playing) }
    LaunchedEffect(volume, controller.ready) { controller.setVolume(volume) }
    Box(modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp)).background(androidx.compose.ui.graphics.Color.Black)) {
        AndroidView(
            factory = {
                (controller.web.parent as? ViewGroup)?.removeView(controller.web)
                controller.web
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** 上一首／曲名／下一首 */
@Composable
fun YouTubeTrackBar(controller: YtController, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TrackButton(Glyph.LEFT) { controller.previous() }
        Spacer(Modifier.width(10.dp))
        Text(
            controller.errorText ?: (controller.current?.let { "${controller.index + 1}/${controller.order.size}　${it.display}" } ?: ""),
            color = if (controller.errorText != null) JogColors.Sun1 else JogColors.Muted,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(10.dp))
        TrackButton(Glyph.RIGHT) { controller.next() }
    }
}

@Composable
private fun TrackButton(glyph: Glyph, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(JogColors.SurfaceHigh).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { GlyphIcon(glyph, JogColors.Text, Modifier.size(20.dp)) }
}

/** 建立並在離開畫面時釋放播放器 */
@Composable
fun rememberYtController(items: List<YtItem>, shuffle: Boolean): YtController {
    val ctx = LocalContext.current
    val c = remember { YtController(ctx, items, shuffle) }
    DisposableEffect(c) { onDispose { c.destroy() } }
    return c
}
