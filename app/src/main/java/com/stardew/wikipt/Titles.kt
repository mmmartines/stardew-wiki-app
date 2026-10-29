package com.stardew.wikipt

import android.content.Context
import org.json.JSONObject

/** Títulos das páginas do menu em cada idioma (links entre idiomas da wiki, em assets/titles.json). */
object Titles {
    @Volatile private var map: JSONObject? = null

    private fun map(ctx: Context): JSONObject =
        map ?: JSONObject(Assets.text(ctx, "titles.json").ifBlank { "{}" }).also { map = it }

    /** Título da página [en] (nome em inglês) na wiki do idioma atual. */
    fun of(ctx: Context, en: String): String {
        if (Lang.code == "en") return en
        return map(ctx).optJSONObject(en)?.optString(Lang.code)?.takeIf { it.isNotBlank() } ?: en
    }
}
