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

class MissingPageException : Exception("Página não encontrada")

/** Acesso à API MediaWiki da Stardew Valley Wiki em português. */
object WikiApi {
    const val BASE = "https://pt.stardewvalleywiki.com"
    private const val API = "$BASE/mediawiki/api.php"
    private const val UA = "StardewWikiPT-Android/1.0 (app de leitura pessoal)"

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    fun pageUrl(title: String): String =
        "$BASE/" + enc(title.replace(' ', '_'))
            .replace("%2F", "/")
            .replace("%3A", ":")

    private fun get(params: Map<String, String>): JSONObject {
        val all = params + mapOf("format" to "json", "formatversion" to "2", "utf8" to "1")
        val query = all.entries.joinToString("&") { enc(it.key) + "=" + enc(it.value) }
        val conn = URL("$API?$query").openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15000
            conn.readTimeout = 25000
            conn.setRequestProperty("User-Agent", UA)
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("Erro HTTP $code")
            val text = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            return JSONObject(text)
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
