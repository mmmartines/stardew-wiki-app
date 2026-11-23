package com.stardew.wikipt

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.net.URLDecoder

/**
 * Glossário das páginas dos mods: nome em inglês de algo do jogo base → nome na wiki oficial do idioma.
 * Vem dos links entre idiomas da wiki oficial em inglês e fica salvo no aparelho (um arquivo por idioma),
 * então vale para todas as páginas e funciona sem internet. "" = a wiki do idioma não tem essa página.
 */
object Glossary {
    private val SVW = Regex("href=\"https?://(?:www\\.)?stardewvalleywiki\\.com/([^\"?#]+)")
    private val SEASON = Regex("href=\"/wiki/(Spring|Summer|Fall|Winter)\"")
    private val maps = HashMap<String, MutableMap<String, String>>()

    /** O glossário só vale para páginas de mod lidas em outro idioma que não o inglês. */
    fun applies(w: Wiki) = w.mod && Lang.code != "en"

    /** Títulos do jogo base citados na página (links para a wiki oficial e estações). */
    fun titles(html: String): Set<String> {
        val out = LinkedHashSet<String>()
        SVW.findAll(html).forEach { m ->
            try {
                val t = URLDecoder.decode(m.groupValues[1].replace("+", "%2B"), "UTF-8").replace('_', ' ').trim()
                if (t.isNotEmpty() && !t.contains(':')) out.add(t) // sem Arquivo:, Categoria: etc.
            } catch (_: Exception) {
            }
        }
        SEASON.findAll(html).forEach { out.add(it.groupValues[1]) }
        return out
    }

    private fun file(ctx: Context, lang: String) = File(ctx.applicationContext.filesDir, "gloss_$lang.json")

    @Synchronized
    private fun map(ctx: Context, lang: String): MutableMap<String, String> = maps.getOrPut(lang) {
        val m = HashMap<String, String>()
        try {
            val f = file(ctx, lang)
            if (f.exists()) {
                val o = JSONObject(f.readText())
                o.keys().forEach { k -> m[k] = o.optString(k) }
            }
        } catch (_: Exception) {
        }
        m
    }

    /** O que já se sabe (só os que têm tradução), como JSON para a página. */
    @Synchronized
    fun knownJson(ctx: Context, lang: String, titles: Set<String>): String {
        val m = map(ctx, lang)
        val o = JSONObject()
        titles.forEach { t -> m[t]?.takeIf { it.isNotEmpty() }?.let { o.put(t, it) } }
        return o.toString()
    }

    @Synchronized
    fun missing(ctx: Context, lang: String, titles: Set<String>): List<String> {
        val m = map(ctx, lang)
        return titles.filter { it !in m }
    }

    /** Busca na wiki oficial os títulos que faltam (fora da thread principal) e salva. */
    fun fetch(ctx: Context, lang: String, titles: List<String>) {
        if (titles.isEmpty()) return
        val found = HashMap<String, String>()
        titles.chunked(50).forEach { part ->
            WikiApi.translateMany(part, "en", lang).forEach { (en, tr) -> found[en] = tr ?: "" }
        }
        synchronized(this) {
            val m = map(ctx, lang)
            m.putAll(found)
            try {
                file(ctx, lang).writeText(JSONObject(m as Map<*, *>).toString())
            } catch (_: Exception) {
            }
        }
    }

    private var terms: JSONObject? = null

    /** Termos da wiki oficial (títulos de seção, rótulos), gerados por tools/gen_terms.py, para o idioma. */
    @Synchronized
    fun termsJson(ctx: Context, lang: String): String {
        val all = terms ?: try { JSONObject(Assets.text(ctx, "terms.json").ifBlank { "{}" }) } catch (_: Exception) { JSONObject() }
        terms = all
        return all.optJSONObject(lang)?.toString() ?: "{}"
    }
}
