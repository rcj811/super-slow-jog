package com.rcj811.slowjog

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** 一首 YouTube 影片 */
data class YtItem(val id: String, val title: String = "") {
    val display: String get() = title.ifBlank { "YouTube 影片（$id）" }
}

/** YouTube 播放清單：存在 App 內，沒有數量限制（上限 300 首） */
object YouTubeStore {
    const val MAX_ITEMS = 300
    private val _items = MutableStateFlow<List<YtItem>>(emptyList())
    val items: StateFlow<List<YtItem>> = _items.asStateFlow()
    @Volatile private var loaded = false

    private val idRe = Regex("^[A-Za-z0-9_-]{11}$")
    private val patterns = listOf(
        Regex("""[?&]v=([A-Za-z0-9_-]{11})"""),
        Regex("""youtu\.be/([A-Za-z0-9_-]{11})"""),
        Regex("""/(?:shorts|embed|live|v)/([A-Za-z0-9_-]{11})"""),
    )

    /** 從各種 YouTube 網址取出影片 ID；也接受直接輸入 11 碼 ID */
    fun extractId(text: String): String? {
        val t = text.trim()
        if (idRe.matches(t)) return t
        if (!t.contains("youtu", ignoreCase = true)) return null
        for (p in patterns) p.find(t)?.let { return it.groupValues[1] }
        return null
    }

    /** 一次貼上多個網址（以空白、換行、逗號分隔） */
    fun extractAll(text: String): List<String> =
        text.split(Regex("""[\s,，、]+""")).mapNotNull { extractId(it) }.distinct()

    private fun file(context: Context) = File(context.filesDir, "youtube.json")

    @Synchronized
    fun load(context: Context) {
        if (loaded) return
        loaded = true
        _items.value = runCatching {
            val f = file(context)
            if (!f.exists()) emptyList() else {
                val arr = JSONArray(f.readText())
                (0 until arr.length()).map {
                    val o = arr.getJSONObject(it)
                    YtItem(o.getString("id"), o.optString("title"))
                }
            }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    private fun set(context: Context, list: List<YtItem>) {
        _items.value = list
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("id", it.id).put("title", it.title)) }
        runCatching { file(context).writeText(arr.toString()) }
    }

    /** 加入影片，回傳實際新增的數量 */
    fun add(context: Context, ids: List<String>): Int {
        load(context)
        val cur = _items.value
        val existing = cur.map { it.id }.toHashSet()
        val fresh = ids.filter { it !in existing }.take((MAX_ITEMS - cur.size).coerceAtLeast(0))
        if (fresh.isNotEmpty()) set(context, cur + fresh.map { YtItem(it) })
        return fresh.size
    }

    fun remove(context: Context, index: Int) {
        val cur = _items.value.toMutableList()
        if (index in cur.indices) { cur.removeAt(index); set(context, cur) }
    }

    fun move(context: Context, index: Int, delta: Int) {
        val cur = _items.value.toMutableList()
        val to = index + delta
        if (index in cur.indices && to in cur.indices) {
            val x = cur.removeAt(index); cur.add(to, x); set(context, cur)
        }
    }

    fun clear(context: Context) = set(context, emptyList())

    /** 補上還沒有標題的影片標題（YouTube oEmbed，不需金鑰） */
    suspend fun fillTitles(context: Context) = withContext(Dispatchers.IO) {
        val missing = _items.value.filter { it.title.isBlank() }.map { it.id }
        for (id in missing) {
            val title = runCatching { fetchTitle(id) }.getOrNull() ?: continue
            set(context, _items.value.map { if (it.id == id) it.copy(title = title) else it })
        }
    }

    private fun fetchTitle(id: String): String? {
        val target = URLEncoder.encode("https://www.youtube.com/watch?v=$id", "UTF-8")
        val conn = URL("https://www.youtube.com/oembed?format=json&url=$target").openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        return try {
            if (conn.responseCode != 200) null
            else JSONObject(conn.inputStream.bufferedReader().readText()).optString("title").ifBlank { null }
        } finally {
            conn.disconnect()
        }
    }
}
