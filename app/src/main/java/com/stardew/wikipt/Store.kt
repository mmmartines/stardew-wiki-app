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

    private companion object {
        const val FAV = "favs_all"
        const val HIST = "hist_all"
    }

    /** Uma página salva: idioma da wiki + título. */
    data class Entry(val lang: String, val title: String)

    init {
        migrate()
    }

    /**
     * Favoritos e histórico valem para todos os idiomas (cada item lembra em que idioma foi salvo).
     * Versões anteriores guardavam uma lista por idioma ("fav_pt", "fav_en"…) ou só em português ("fav").
     */
    private fun migrate() {
        if (prefs.getBoolean("migrated_v2", false)) return
        val favs = mutableListOf<Entry>()
        val hist = mutableListOf<Entry>()
        fun old(key: String): List<String> {
            val raw = prefs.getString(key, null) ?: return emptyList()
            return try { JSONArray(raw).let { a -> List(a.length()) { a.getString(it) } } } catch (_: Exception) { emptyList() }
        }
        old("fav").forEach { favs.add(Entry("pt", it)) }
        old("hist").forEach { hist.add(Entry("pt", it)) }
        val e = prefs.edit().remove("fav").remove("hist")
        Lang.all.forEach { w ->
            old("fav_" + w.code).forEach { t -> if (favs.none { it.lang == w.code && it.title == t }) favs.add(Entry(w.code, t)) }
            old("hist_" + w.code).forEach { t -> if (hist.none { it.lang == w.code && it.title == t }) hist.add(Entry(w.code, t)) }
            e.remove("fav_" + w.code).remove("hist_" + w.code)
        }
        e.putString(FAV, toJson(favs)).putString(HIST, toJson(hist)).putBoolean("migrated_v2", true).apply()
    }

    private fun toJson(l: List<Entry>): String {
        val a = JSONArray()
        l.forEach { a.put(JSONObject().put("l", it.lang).put("t", it.title)) }
        return a.toString()
    }

    private fun entries(key: String): MutableList<Entry> = try {
        val a = JSONArray(prefs.getString(key, "[]"))
        MutableList(a.length()) { a.getJSONObject(it).let { o -> Entry(o.getString("l"), o.getString("t")) } }
    } catch (_: Exception) {
        mutableListOf()
    }

    private fun save(key: String, l: List<Entry>) {
        prefs.edit().putString(key, toJson(l)).apply()
    }

    private fun here(t: String) = Entry(Lang.code, t)

    /** Favoritos de todos os idiomas, do mais recente para o mais antigo. */
    fun favorites(): MutableList<Entry> = entries(FAV)
    fun isFavorite(t: String) = favorites().contains(here(t))

    fun toggleFavorite(t: String): Boolean {
        val l = favorites()
        val e = here(t)
        val nowFav = if (l.remove(e)) false else { l.add(0, e); true }
        save(FAV, l)
        return nowFav
    }

    fun removeFavorite(e: Entry) {
        val l = favorites(); l.remove(e); save(FAV, l)
    }

    /** Histórico de todos os idiomas. */
    fun history(): MutableList<Entry> = entries(HIST)

    /** Histórico só do idioma atual (atalhos da tela inicial). */
    fun historyHere(): List<String> = history().filter { it.lang == Lang.code }.map { it.title }

    fun addHistory(t: String) {
        val l = history()
        val e = here(t)
        l.remove(e)
        l.add(0, e)
        while (l.size > 150) l.removeAt(l.size - 1)
        save(HIST, l)
    }

    fun removeHistory(e: Entry) {
        val l = history(); l.remove(e); save(HIST, l)
    }

    fun clearHistory() = save(HIST, emptyList())

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
