package com.stardew.wikipt

import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

object Bg {
    val pool: ExecutorService = Executors.newCachedThreadPool()
}

/**
 * Exibição de ponta a ponta do jeito recomendado pelo Google (igual em todas as versões do Android).
 * Barra de status transparente com ícones claros sobre a madeira; a de navegação se ajusta ao tema.
 */
fun androidx.activity.ComponentActivity.edgeToEdge() {
    enableEdgeToEdge(statusBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
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
fun AppCompatActivity.applyInsets(top: android.view.View, bottom: android.view.View?, navBar: Boolean = false) {
    val t0 = top.paddingTop
    val b0 = bottom?.paddingBottom ?: 0
    val gap = (6 * resources.displayMetrics.density).toInt()
    androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(top.rootView) { _, insets ->
        val T = androidx.core.view.WindowInsetsCompat.Type
        val bars = insets.getInsets(T.systemBars() or T.displayCutout())
        val ime = insets.getInsets(T.ime())
        top.setPadding(top.paddingLeft, t0 + bars.top, top.paddingRight, top.paddingBottom)
        val nav = if (navBar) {
            // Navegação por gestos: a barra do sistema é só o traço de arrastar (não tem botões),
            // então a barra de abas desce e fica só um respiro; com 3 botões, reserva a altura inteira.
            val tappable = insets.getInsets(T.tappableElement()).bottom
            if (tappable > 0) bars.bottom else minOf(bars.bottom, gap)
        } else bars.bottom
        val imeOpen = ime.bottom > bars.bottom
        bottom?.setPadding(bottom.paddingLeft, bottom.paddingTop, bottom.paddingRight,
            b0 + if (imeOpen) ime.bottom else nav)
        insets
    }
}

/** Janela de diálogo no tema do app (pergaminho com moldura de madeira). */
fun android.content.Context.wikiDialog(): com.google.android.material.dialog.MaterialAlertDialogBuilder =
    com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
        .setBackground(androidx.core.content.ContextCompat.getDrawable(this, R.drawable.dialog_bg))

/** Listas em diálogos não encostam na moldura de madeira (telas baixas cortavam o último item). */
fun androidx.appcompat.app.AlertDialog.framed(): androidx.appcompat.app.AlertDialog {
    val pad = (10 * context.resources.displayMetrics.density).toInt()
    listView?.apply {
        setPadding(paddingLeft, paddingTop, paddingRight, pad)
        clipToPadding = true
        isVerticalScrollBarEnabled = true
        isScrollbarFadingEnabled = false
    }
    return this
}
