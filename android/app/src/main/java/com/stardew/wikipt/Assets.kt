package com.stardew.wikipt

import android.content.Context
import java.util.concurrent.ConcurrentHashMap

/** Lê arquivos de texto de assets/ (CSS/JS das páginas) com cache. */
object Assets {
    private val cache = ConcurrentHashMap<String, String>()

    fun text(ctx: Context, name: String): String =
        cache.getOrPut(name) {
            try {
                ctx.assets.open(name).bufferedReader(Charsets.UTF_8).use { it.readText() }
            } catch (_: Exception) {
                ""
            }
        }
}
