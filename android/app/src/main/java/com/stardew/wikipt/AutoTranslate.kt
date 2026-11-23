package com.stardew.wikipt

import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import java.util.concurrent.TimeUnit

/**
 * Tradução automática (inglês → idioma da wiki) das páginas dos mods, feita no aparelho pelo ML Kit.
 * Na primeira vez baixa o pacote do idioma (cerca de 30 MB); depois funciona sem internet.
 * Os textos já traduzidos ficam em memória para não traduzir de novo ao voltar à página.
 */
object AutoTranslate {
    private val translators = HashMap<String, Translator>()
    private val memo = HashMap<String, String>()

    /** Idioma suportado pelo tradutor (null = não traduz). */
    fun target(lang: String): String? = if (lang == "en") null else TranslateLanguage.fromLanguageTag(lang)

    @Synchronized
    private fun translator(target: String): Translator = translators.getOrPut(target) {
        Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage(target)
                .build()
        )
    }

    /** Traduz os textos (fora da thread principal). Lança exceção se não der (sem pacote e sem internet, etc.). */
    fun translate(texts: List<String>, lang: String): List<String> {
        val target = target(lang) ?: return texts
        val tr = translator(target)
        Tasks.await(tr.downloadModelIfNeeded(DownloadConditions.Builder().build()), 5, TimeUnit.MINUTES)
        return texts.map { raw ->
            val t = raw.trim()
            if (t.isEmpty()) return@map raw
            val key = "$target\u0000$t"
            synchronized(memo) { memo[key] }?.let { return@map it }
            val out = Tasks.await(tr.translate(t), 30, TimeUnit.SECONDS)
            synchronized(memo) { if (memo.size > 5000) memo.clear(); memo[key] = out }
            out
        }
    }
}
