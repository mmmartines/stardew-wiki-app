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
    private val UA = "Mozilla/5.0 (Linux; Android) GuiaDoVale/" + BuildConfig.VERSION_NAME +
        " (unofficial read-only reader; https://github.com/mmmartines/stardew-wiki-app)"

    /** Intervalo mínimo entre requisições ao mesmo site, para não sobrecarregar o servidor de cada wiki. */
    private const val MIN_GAP_MS = 250L
    private val lastRequest = HashMap<String, Long>()

    private fun throttle(host: String) {
        val wait: Long
        synchronized(lastRequest) {
            val now = System.currentTimeMillis()
            val slot = maxOf(now, (lastRequest[host] ?: 0L) + MIN_GAP_MS)
            lastRequest[host] = slot
            wait = slot - now
        }
        if (wait > 0) Thread.sleep(wait)
    }

    fun historyUrl(title: String, w: Wiki = Wikis.base()): String =
        w.base + w.indexPath + "?title=" + enc(title.replace(' ', '_')) + "&action=history"

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    fun pageUrl(title: String, w: Wiki = Wikis.base()): String =
        w.base + w.pagePath + enc(title.replace(' ', '_'))
            .replace("%2F", "/")
            .replace("%3A", ":")

    private fun get(params: Map<String, String>, w: Wiki = Wikis.base()): JSONObject {
        val other = w.base != BASE
        val all = params + mapOf("format" to "json", "formatversion" to "2", "utf8" to "1") +
            (if (other) mapOf("origin" to "*") else emptyMap())
        val api = w.base + w.apiPath
        val query = all.entries.joinToString("&") { enc(it.key) + "=" + enc(it.value) }
        if (!Net.online()) throw OfflineException()
        throttle(w.host)
        // de tempos em tempos tenta de novo a conexão direta (o bloqueio anti-robô costuma ser passageiro)
        // (cada wiki tem o seu controle: o bloqueio de um site não afeta os outros)
        val since = bridgeSince[w.host]
        if (since != null && android.os.SystemClock.elapsedRealtime() - since > 180_000) bridgeSince.remove(w.host)
        if (!bridgeSince.containsKey(w.host)) {
            try {
                return JSONObject(direct("$api?$query"))
            } catch (e: Exception) {
                lastDirectError = e.toString()
                // a partir daqui usa o navegador interno para este site
                bridgeSince[w.host] = android.os.SystemClock.elapsedRealtime()
            }
        }
        try {
            return JSONObject(WebBridge.fetch(if (other) "$api?$query" else "/mediawiki/api.php?$query"))
        } catch (e: Exception) {
            if (!Net.online()) throw OfflineException()
            val detail = (e.message ?: e.toString()) + "\n(direct: " + lastDirectError + ")"
            val code = Regex("HTTP (\\d{3})").find(detail)?.groupValues?.get(1)?.toIntOrNull()
            throw if (code != null) ServerException(code, detail) else IOException(detail)
        }
    }

    /** Categorias internas da wiki, sem interesse para quem joga. */
    private val MAINTENANCE = Regex("(?i)archived|user pages|maintenance|candidates for|pages with|pages using|hidden categor|tracking|templates?$")

    private val bridgeSince = java.util.concurrent.ConcurrentHashMap<String, Long>()
    @Volatile private var lastDirectError = "-"

    private fun direct(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10000
            conn.readTimeout = 20000
            conn.setRequestProperty("User-Agent", UA)
            conn.setRequestProperty("Accept", "application/json")
            val code = conn.responseCode
            if (code !in 200..299) {
                // lê e fecha a resposta de erro, para a conexão não ficar aberta até o sistema recolher
                try { conn.errorStream?.use { it.readBytes() } } catch (_: Exception) {}
                throw IOException("HTTP $code")
            }
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

    fun parse(title: String, w: Wiki = Wikis.base()): Page {
        val j = get(
            mapOf(
                "action" to "parse",
                "page" to title,
                "redirects" to "1",
                "prop" to "text|sections",
                "disableeditsection" to "1",
                "disabletoc" to "1",
                "disablelimitreport" to "1"
            ), w
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
    fun prefix(q: String, w: Wiki = Wikis.base(), limit: Int = 20): List<Hit> {
        val j = get(
            mapOf(
                "action" to "query", "list" to "prefixsearch",
                "pssearch" to q, "pslimit" to limit.toString(), "psnamespace" to "0"
            ),
            w
        )
        return queryList(j, "prefixsearch").map { Hit(it.getString("title")) }
            .distinctBy { it.title.lowercase() } // redirecionamentos que só mudam maiúsculas
    }

    /** Busca no texto completo de todas as páginas. */
    fun search(q: String, w: Wiki = Wikis.base(), limit: Int = 40): List<Hit> {
        val j = get(
            mapOf(
                "action" to "query", "list" to "search", "srsearch" to q,
                "srlimit" to limit.toString(), "srprop" to "snippet", "srnamespace" to "0"
            ), w
        )
        return queryList(j, "search").map { Hit(it.getString("title"), stripHtml(it.optString("snippet"))) }
            .distinctBy { it.title.lowercase() }
    }

    fun categoryMembers(category: String, next: String?, w: Wiki = Wikis.base()): Chunk {
        val p = mutableMapOf(
            "action" to "query", "list" to "categorymembers", "cmtitle" to category,
            "cmlimit" to "200", "cmtype" to "subcat|page"
        )
        if (next != null) p["cmcontinue"] = next
        val j = get(p, w)
        val items = queryList(j, "categorymembers").map { Hit(it.getString("title"), "", it.optInt("ns")) }
        return Chunk(items, cont(j, "cmcontinue"))
    }

    fun allPages(prefix: String, next: String?, w: Wiki = Wikis.base()): Chunk {
        val p = mutableMapOf(
            "action" to "query", "list" to "allpages", "apnamespace" to "0",
            "aplimit" to "200", "apfilterredir" to "nonredirects"
        )
        if (prefix.isNotBlank()) p["apprefix"] = prefix
        if (next != null) p["apcontinue"] = next
        val j = get(p, w)
        val items = queryList(j, "allpages").map { Hit(it.getString("title")) }
        return Chunk(items, cont(j, "apcontinue"))
    }

    fun allCategories(prefix: String, next: String?, w: Wiki = Wikis.base()): Chunk {
        val p = mutableMapOf(
            "action" to "query", "list" to "allcategories", "aclimit" to "200"
        )
        if (prefix.isNotBlank()) p["acprefix"] = prefix
        if (next != null) p["accontinue"] = next
        val j = get(p, w)
        val items = queryList(j, "allcategories").map {
            val name = it.optString("category", it.optString("*"))
            Hit("Category:$name", "", 14)
        }.filterNot { MAINTENANCE.containsMatchIn(it.title) }
        return Chunk(items, cont(j, "accontinue"))
    }

    /** Título equivalente de [title] (da wiki em [from]) na wiki em [to], via links entre idiomas; null se não houver. */
    fun translate(title: String, from: String, to: String): String? {
        if (from == to) return title
        val j = get(
            mapOf("action" to "query", "titles" to title, "redirects" to "1", "prop" to "langlinks", "lllang" to to),
            Wikis.base(from)
        )
        val page = queryList(j, "pages").firstOrNull() ?: return null
        val ll = page.optJSONArray("langlinks") ?: return null
        return if (ll.length() > 0) ll.getJSONObject(0).optString("title").takeIf { it.isNotBlank() } else null
    }

    /**
     * Para cada título da wiki em [from], o equivalente na wiki em [to] (null quando não há), numa chamada só.
     * Usado para "Você quis dizer Chirívia (Parsnip)?".
     */
    fun translateMany(titles: List<String>, from: String, to: String): List<Pair<String, String?>> {
        if (titles.isEmpty()) return emptyList()
        val j = get(
            mapOf("action" to "query", "titles" to titles.joinToString("|"), "redirects" to "1",
                "prop" to "langlinks", "lllang" to to, "lllimit" to "max"),
            Wikis.base(from)
        )
        // título pedido -> título final (normalizado/redirecionado)
        val alias = mutableMapOf<String, String>()
        for (k in listOf("normalized", "redirects")) {
            j.optJSONObject("query")?.optJSONArray(k)?.let { a ->
                for (i in 0 until a.length()) a.getJSONObject(i).let { alias[it.optString("from")] = it.optString("to") }
            }
        }
        val found = mutableMapOf<String, String?>()
        queryList(j, "pages").forEach { p ->
            val ll = p.optJSONArray("langlinks")
            found[p.optString("title")] = if (ll != null && ll.length() > 0) ll.getJSONObject(0).optString("title").takeIf { it.isNotBlank() } else null
        }
        return titles.map { t ->
            var k = t
            repeat(3) { alias[k]?.let { k = it } }
            t to found[k]
        }
    }

    /**
     * Páginas da wiki atual que apontam para [enTitle] na wiki em inglês (ex.: "Parsnip" → "Chirívia").
     * Consulta a própria wiki do idioma atual, então funciona mesmo pelo navegador interno.
     */
    fun fromEnglish(enTitle: String): List<String> {
        val j = get(mapOf("action" to "query", "list" to "langbacklinks", "lbllang" to "en",
            "lbltitle" to enTitle, "lbllimit" to "5"))
        return queryList(j, "langbacklinks").filter { it.optInt("ns") == 0 }.map { it.getString("title") }
    }

    fun random(w: Wiki = Wikis.base()): String {
        val j = get(mapOf("action" to "query", "list" to "random", "rnnamespace" to "0", "rnlimit" to "1"), w)
        return queryList(j, "random").firstOrNull()?.getString("title") ?: throw IOException("Sem resultado")
    }
}
