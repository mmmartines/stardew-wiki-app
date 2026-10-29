package com.stardew.wikipt

import android.content.Context
import android.util.Base64

/** Fonte pixelada (Pixelify Sans, OFL) embutida como data URI para os WebViews. */
object Fonts {
    @Volatile private var css: String? = null

    fun css(ctx: Context): String {
        css?.let { return it }
        val sb = StringBuilder()
        for ((w, f) in listOf(400 to "pixelify-sans-latin-400-normal.woff2", 700 to "pixelify-sans-latin-700-normal.woff2")) {
            try {
                val bytes = ctx.assets.open("fonts/$f").use { it.readBytes() }
                val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                sb.append("@font-face{font-family:'Pixel';font-weight:$w;font-display:block;src:url(data:font/woff2;base64,")
                    .append(b64).append(") format('woff2')}")
            } catch (_: Exception) {
            }
        }
        return sb.toString().also { css = it }
    }
}
