package com.stardew.wikipt

import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

object Bg {
    val pool: ExecutorService = Executors.newCachedThreadPool()
}

/** Executa [work] em segundo plano e entrega o resultado na thread principal. */
fun <T> AppCompatActivity.runAsync(work: () -> T, done: (Result<T>) -> Unit) {
    Bg.pool.execute {
        val r: Result<T> = try {
            Result.success(work())
        } catch (e: Throwable) {
            Result.failure(e)
        }
        runOnUiThread {
            if (!isFinishing && !isDestroyed) done(r)
        }
    }
}

fun AppCompatActivity.toast(msg: String) {
    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}

fun stripHtml(s: String): String =
    androidx.core.text.HtmlCompat.fromHtml(s, androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim()

/**
 * Android 15+ desenha o app atrás das barras do sistema (obrigatório a partir do Android 16).
 * Dá espaço no topo para a barra de status e embaixo para a barra de navegação/teclado.
 */
fun AppCompatActivity.applyInsets(top: android.view.View, bottom: android.view.View?) {
    val t0 = top.paddingTop
    val b0 = bottom?.paddingBottom ?: 0
    androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(top.rootView) { _, insets ->
        val bars = insets.getInsets(
            androidx.core.view.WindowInsetsCompat.Type.systemBars() or
                androidx.core.view.WindowInsetsCompat.Type.displayCutout()
        )
        val ime = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime())
        top.setPadding(top.paddingLeft, t0 + bars.top, top.paddingRight, top.paddingBottom)
        bottom?.setPadding(bottom.paddingLeft, bottom.paddingTop, bottom.paddingRight, b0 + maxOf(bars.bottom, ime.bottom))
        insets
    }
}
