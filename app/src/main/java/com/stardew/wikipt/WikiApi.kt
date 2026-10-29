package com.stardew.wikipt

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class Section(val line: String, val anchor: String, val level: Int)
data class Page(val title: String, val html: String, val sections: List<Section>)
data class Hit(val title: String, val snippet: String = "", val ns: Int = 0)
data class Chunk(val items: List<Hit>, val next: String?)

class MissingPageException : Exception("missing page")

/** Acesso somente leitura à API MediaWiki da Stardew Valley Wiki, no idioma escolhido. */
object WikiApi {
    val BASE: String get() = Lang.base()
    private val API: String get() = "$BASE/mediawiki/api.php"
    private val UA = "Mozilla/5.0 (Linux; Android) GuiaDoVale/" + BuildConfig.VERSION_NAME +
        " (unofficial read-only reader; https://github.com/mmmartines/stardew-wiki-app)"

    /** Intervalo mínimo entre requisições, para não sobrecarregar o servidor da wiki. */
    private const val MIN_GAP_MS = 250L
    private var lastRequest = 0L

    @Synchronized
    private fun throttle() {
        val wait = lastRequest + MIN_GAP_MS - System.currentTimeMillis()
        if (wait > 0) Thread.sleep(wait)
        lastRequest = System.currentTimeMillis()
    }

    fun historyUrl(title: String): String =
        "$BASE/mediawiki/index.php?title=" + enc(title.replace(' ', '_')) + "&action=history"

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    fun pageUrl(title: String): String =
        "$BASE/" + enc(title.replace(' ', '_'))
            .replace("%2F", "/")
            .replace("%3A", ":")

    private fun get(params: Map<String, String>): JSONObject {
        val all = params + mapOf("format" to "json", "formatversion" to "2", "utf8" to "1")
        val query = all.entries.joinToString("&") { enc(it.key) + "=" + enc(it.value) }
        throttle()
        if (!useBridge) {
            try {
                return JSONObject(direct("$API?$query"))
            } catch (e: Exception) {
                lastDirectError = e.toString()
                useBridge = true // a partir daqui usa o navegador interno
            }
        }
        try {
            return JSONObject(WebBridge.fetch("/mediawiki/api.php?$query"))
        } catch (e: Exception) {
            throw IOException((e.message ?: e.toString()) + "\n(direct: " + lastDirectError + ")")
        }
    }

    @Volatile private var useBridge = false
    @Volatile private var lastDirectError = "-"

    private fun direct(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10000
            conn.readTimeout = 20000
            conn.setRequestProperty("User-Agent", UA)
            conn.setRequestProperty("Accept", "application/json")
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            val text = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            if (!text.trimStart().startsWith("{")) throw IOException("not JSON")
            return text
        } finally {
            conn.disconnect()
        }
    }

    private fun queryList(j: JSONObject, key: String): List<JSONObject> {
        val a = j.optJSONObject("query")?.optJSONArray(key) ?: return emptyList()
        return (0 until a.length()).map { a.getJSONObject(it) }
    }

    private fun cont(j: JSONObject, key: String): String? =
        j.optJSONObject("continue")?.optString(key)?.takeIf { it.isNotEmpty() }

    fun parse(title: String): Page {
        val j = get(
            mapOf(
                "action" to "parse",
                "page" to title,
                "redirects" to "1",
                "prop" to "text|sections",
                "disableeditsection" to "1",
                "disabletoc" to "1",
                "disablelimitreport" to "1"
            )
        )
        j.optJSONObject("error")?.let {
            val code = it.optString("code")
            if (code == "missingtitle" || code == "invalidtitle") throw MissingPageException()
            throw IOException(it.optString("info", "Erro da wiki"))
        }
        val p = j.getJSONObject("parse")
        val secs = mutableListOf<Section>()
        p.optJSONArray("sections")?.let { arr ->
            for (i in 0 until arr.length()) {
                val s = arr.getJSONObject(i)
                secs.add(Section(stripHtml(s.optString("line")), s.optString("anchor"), s.optInt("toclevel", 1)))
            }
        }
        return Page(p.getString("title"), p.optString("text"), secs)
    }

    /** Sugestões rápidas pelo começo do título. */
    fun prefix(q: String): List<Hit> {
        val j = get(
            mapOf(
                "action" to "query", "list" to "prefixsearch",
                "pssearch" to q, "pslimit" to "20", "psnamespace" to "0"
            )
        )
        return queryList(j, "prefixsearch").map { Hit(it.getString("title")) }
    }

    /** Busca no texto completo de todas as páginas. */
    fun search(q: String): List<Hit> {
        val j = get(
            mapOf(
                "action" to "query", "list" to "search", "srsearch" to q,
                "srlimit" to "40", "srprop" to "snippet", "srnamespace" to "0"
            )
        )
        return queryList(j, "search").map { Hit(it.getString("title"), stripHtml(it.optString("snippet"))) }
    }

    fun categoryMembers(category: String, next: String?): Chunk {
        val p = mutableMapOf(
            "action" to "query", "list" to "categorymembers", "cmtitle" to category,
            "cmlimit" to "200", "cmtype" to "subcat|page"
        )
        if (next != null) p["cmcontinue"] = next
        val j = get(p)
        val items = queryList(j, "categorymembers").map { Hit(it.getString("title"), "", it.optInt("ns")) }
        return Chunk(items, cont(j, "cmcontinue"))
    }

    fun allPages(prefix: String, next: String?): Chunk {
        val p = mutableMapOf(
            "action" to "query", "list" to "allpages", "apnamespace" to "0",
            "aplimit" to "200", "apfilterredir" to "nonredirects"
        )
        if (prefix.isNotBlank()) p["apprefix"] = prefix
        if (next != null) p["apcontinue"] = next
        val j = get(p)
        val items = queryList(j, "allpages").map { Hit(it.getString("title")) }
        return Chunk(items, cont(j, "apcontinue"))
    }

    fun allCategories(prefix: String, next: String?): Chunk {
        val p = mutableMapOf(
            "action" to "query", "list" to "allcategories", "aclimit" to "200"
        )
        if (prefix.isNotBlank()) p["acprefix"] = prefix
        if (next != null) p["accontinue"] = next
        val j = get(p)
        val items = queryList(j, "allcategories").map {
            val name = it.optString("category", it.optString("*"))
            Hit("Category:$name", "", 14)
        }
        return Chunk(items, cont(j, "accontinue"))
    }

    fun random(): String {
        val j = get(mapOf("action" to "query", "list" to "random", "rnnamespace" to "0", "rnlimit" to "1"))
        return queryList(j, "random").firstOrNull()?.getString("title") ?: throw IOException("Sem resultado")
    }
}
