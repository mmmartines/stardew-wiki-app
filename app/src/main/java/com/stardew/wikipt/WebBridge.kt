package com.stardew.wikipt

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Faz as chamadas da API por dentro de um WebView invisível.
 * O WebView se comporta como um navegador comum, então passa pela proteção
 * anti-robô da wiki que às vezes bloqueia conexões diretas do app.
 */
object WebBridge {
    private const val HOME = "${WikiApi.BASE}/mediawiki/api.php?action=query&meta=siteinfo&format=json"

    private val main = Handler(Looper.getMainLooper())
    private var web: WebView? = null
    @Volatile private var ready = CountDownLatch(1)
    private val seq = AtomicInteger()
    private val results = ConcurrentHashMap<Int, Array<String?>>()
    private val latches = ConcurrentHashMap<Int, CountDownLatch>()
    private lateinit var app: Context

    fun init(ctx: Context) {
        app = ctx.applicationContext
        main.post { ensure() }
    }

    private class Js {
        @JavascriptInterface
        fun done(id: Int, ok: Boolean, text: String) {
            results[id] = arrayOf(if (ok) "1" else "0", text)
            latches.remove(id)?.countDown()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun ensure() {
        if (web != null) return
        val w = WebView(app)
        w.settings.javaScriptEnabled = true
        w.settings.domStorageEnabled = true
        w.addJavascriptInterface(Js(), "Bridge")
        w.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                // Página de desafio anti-robô redireciona sozinha; só fica pronto quando vier o JSON.
                view.evaluateJavascript("(document.body && document.body.innerText || '').trim().charAt(0)") { c ->
                    if (c == "\"{\"") ready.countDown()
                }
            }
        }
        web = w
        w.loadUrl(HOME)
    }

    private fun reload() {
        ready = CountDownLatch(1)
        main.post { ensure(); web?.loadUrl(HOME) }
    }

    /** Busca [url] (mesma origem da wiki) e devolve o texto. Chamar fora da thread principal. */
    fun fetch(url: String, attempt: Int = 0): String {
        ready.await(25, TimeUnit.SECONDS)
        val id = seq.incrementAndGet()
        val latch = CountDownLatch(1)
        latches[id] = latch
        val js = "fetch(" + JSONObject.quote(url) + ",{credentials:'include'})" +
            ".then(function(r){return r.text().then(function(t){Bridge.done($id, r.ok, r.ok ? t : ('HTTP '+r.status))})})" +
            ".catch(function(e){Bridge.done($id,false,String(e))})"
        main.post {
            ensure()
            web?.evaluateJavascript(js, null)
        }
        if (!latch.await(30, TimeUnit.SECONDS)) {
            latches.remove(id)
            throw IOException("Tempo esgotado (navegador interno)")
        }
        val r = results.remove(id) ?: throw IOException("Sem resposta")
        val text = r[1] ?: ""
        val looksJson = text.trimStart().startsWith("{")
        if (r[0] != "1" || !looksJson) {
            if (attempt == 0) {
                reload()
                return fetch(url, 1)
            }
            throw IOException("Navegador interno: " + text.take(120))
        }
        return text
    }
}
