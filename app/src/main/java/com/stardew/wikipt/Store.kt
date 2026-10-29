package com.stardew.wikipt

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Favoritos, histórico, preferências e cache offline das páginas. */
class Store(ctx: Context) {
    private val prefs = ctx.applicationContext.getSharedPreferences("wiki", Context.MODE_PRIVATE)
    private val dir = File(ctx.applicationContext.filesDir, "pages").apply { mkdirs() }

    data class Cached(val page: Page, val time: Long)

    init {
        // versões antigas guardavam favoritos/histórico sem idioma (eram só em português)
        if (prefs.contains("fav") || prefs.contains("hist")) {
            val e = prefs.edit()
            prefs.getString("fav", null)?.let { e.putString("fav_pt", it) }
            prefs.getString("hist", null)?.let { e.putString("hist_pt", it) }
            e.remove("fav").remove("hist").apply()
        }
    }

    private fun list(key: String): MutableList<String> {
        val a = JSONArray(prefs.getString(key, "[]"))
        return MutableList(a.length()) { a.getString(it) }
    }

    private fun save(key: String, l: List<String>) {
        prefs.edit().putString(key, JSONArray(l).toString()).apply()
    }

    fun favorites(): MutableList<String> = list("fav_" + Lang.code)
    fun isFavorite(t: String) = favorites().contains(t)

    fun toggleFavorite(t: String): Boolean {
        val l = favorites()
        val nowFav = if (l.remove(t)) false else { l.add(0, t); true }
        save("fav_" + Lang.code, l)
        return nowFav
    }

    fun removeFavorite(t: String) {
        val l = favorites(); l.remove(t); save("fav_" + Lang.code, l)
    }

    fun history(): MutableList<String> = list("hist_" + Lang.code)

    fun addHistory(t: String) {
        val l = history()
        l.remove(t)
        l.add(0, t)
        while (l.size > 100) l.removeAt(l.size - 1)
        save("hist_" + Lang.code, l)
    }

    fun removeHistory(t: String) {
        val l = history(); l.remove(t); save("hist_" + Lang.code, l)
    }

    fun clearHistory() = save("hist_" + Lang.code, emptyList())

    var textZoom: Int
        get() = prefs.getInt("zoom", 100)
        set(v) = prefs.edit().putInt("zoom", v).apply()

    private fun key(title: String) = Lang.code + ":" + title.replace('_', ' ').trim().lowercase()

    private fun file(title: String): File {
        val md = MessageDigest.getInstance("MD5").digest(key(title).toByteArray())
        return File(dir, md.joinToString("") { "%02x".format(it) } + ".json")
    }

    fun putPage(requested: String, page: Page) {
        val secs = JSONArray()
        page.sections.forEach {
            secs.put(JSONObject().put("line", it.line).put("anchor", it.anchor).put("level", it.level))
        }
        val s = JSONObject()
            .put("title", page.title)
            .put("html", page.html)
            .put("time", System.currentTimeMillis())
            .put("sections", secs)
            .toString()
        try {
            file(requested).writeText(s)
            if (key(requested) != key(page.title)) file(page.title).writeText(s)
        } catch (_: Exception) {
        }
    }

    fun getPage(title: String): Cached? = try {
        val f = file(title)
        if (!f.exists()) null else {
            val o = JSONObject(f.readText())
            val a = o.getJSONArray("sections")
            val secs = (0 until a.length()).map {
                val s = a.getJSONObject(it)
                Section(s.getString("line"), s.getString("anchor"), s.getInt("level"))
            }
            Cached(Page(o.getString("title"), o.getString("html"), secs), o.getLong("time"))
        }
    } catch (_: Exception) {
        null
    }

    fun cacheBytes(): Long = dir.listFiles()?.sumOf { it.length() } ?: 0L
    fun cacheCount(): Int = dir.listFiles()?.size ?: 0
    fun clearCache() {
        dir.listFiles()?.forEach { it.delete() }
    }
}
