package com.stardew.wikipt

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/** Idiomas da Stardew Valley Wiki e o idioma escolhido no app. */
object Lang {
    data class Wiki(val code: String, val name: String)

    /** Mesmos idiomas oferecidos pela wiki na web. */
    val all = listOf(
        Wiki("pt", "Português"),
        Wiki("en", "English"),
        Wiki("es", "Español"),
        Wiki("fr", "Français"),
        Wiki("de", "Deutsch"),
        Wiki("it", "Italiano"),
        Wiki("hu", "Magyar"),
        Wiki("ru", "Русский"),
        Wiki("tr", "Türkçe"),
        Wiki("ja", "日本語"),
        Wiki("ko", "한국어"),
        Wiki("zh", "中文"),
    )

    private const val PREFS = "wiki"
    private const val KEY = "lang"

    @Volatile
    var code: String = "pt"
        private set

    fun host(c: String = code) = if (c == "en") "stardewvalleywiki.com" else "$c.stardewvalleywiki.com"
    fun base(c: String = code) = "https://" + host(c)

    /** Carrega a escolha salva; devolve false se o usuário ainda não escolheu. */
    fun load(ctx: Context): Boolean {
        val saved = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        code = saved ?: suggested()
        return saved != null
    }

    /** Idioma do aparelho, se a wiki tiver; senão inglês. */
    fun suggested(): String {
        val dev = Locale.getDefault().language
        return all.firstOrNull { it.code == dev }?.code ?: "en"
    }

    fun set(ctx: Context, c: String) {
        code = c
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, c).apply()
        WebBridge.reset()
        // a interface acompanha o idioma da wiki (textos que não existirem caem no inglês)
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(c))
    }

    fun index(c: String = code) = all.indexOfFirst { it.code == c }.coerceAtLeast(0)
}
