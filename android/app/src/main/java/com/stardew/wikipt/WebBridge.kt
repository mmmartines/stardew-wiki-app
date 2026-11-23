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
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Faz as chamadas da API por dentro de um WebView invisível.
 * O WebView se comporta como um navegador comum, então passa pela proteção
 * anti-robô da wiki que às vezes bloqueia conexões diretas do app.
 */
object WebBridge {
    private val HOME: String get() = "${WikiApi.BASE}/mediawiki/api.php?action=query&meta=siteinfo&format=json"

    private val main = Handler(Looper.getMainLooper())
    private var web: WebView? = null
    @Volatile private var ready = CountDownLatch(1)
    private val seq = AtomicInteger()
    private val results = ConcurrentHashMap<Int, Array<String?>>()
    private val latches = ConcurrentHashMap<Int, CountDownLatch>()
    private lateinit var app: Context

    fun init(ctx: Context) {
        app = ctx.applicationContext // o WebView só é criado quando for preciso
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

    /** Idioma trocado: a página-base passa a ser a da nova wiki. */
    fun reset() {
        if (!::app.isInitialized || web == null) return
        reload(force = true)
    }

    @Volatile private var lastReload = 0L

    /**
     * Recarrega a página-base, no máximo uma vez a cada 10 s. Antes, cada falha recarregava a página,
     * o que derrubava os outros pedidos em andamento, que falhavam e recarregavam de novo, até a busca parar.
     */
    private fun reload(force: Boolean = false) {
        val now = android.os.SystemClock.elapsedRealtime()
        synchronized(this) {
            if (!force && now - lastReload < 10_000) return
            lastReload = now
            ready = CountDownLatch(1)
        }
        // pedidos presos na página antiga não vão responder: libera quem está esperando
        latches.keys.toList().forEach { id -> results[id] = arrayOf("0", "reload"); latches.remove(id)?.countDown() }
        main.post { ensure(); web?.loadUrl(HOME) }
    }

    /** No máximo 3 pedidos ao mesmo tempo pelo navegador interno. */
    private val gate = Semaphore(3)

    /** Busca [url] (mesma origem da wiki) e devolve o texto. Chamar fora da thread principal. */
    fun fetch(url: String, attempt: Int = 0): String {
        var error: IOException? = null
        if (!gate.tryAcquire(20, TimeUnit.SECONDS)) throw IOException("Navegador interno ocupado")
        try {
            return fetchOnce(url)
        } catch (e: IOException) {
            error = e
        } finally {
            gate.release()
        }
        if (attempt > 0) throw error ?: IOException("Sem resposta")
        reload()
        return fetch(url, 1)
    }

    private fun fetchOnce(url: String): String {
        main.post { ensure() }
        ready.await(15, TimeUnit.SECONDS)
        val id = seq.incrementAndGet()
        val latch = CountDownLatch(1)
        latches[id] = latch
        // outra wiki (endereço completo): sem cookies, senão o navegador bloqueia (CORS com origin=*)
        val cred = if (url.startsWith("http")) "omit" else "include"
        val js = "fetch(" + JSONObject.quote(url) + ",{credentials:'$cred'})" +
            ".then(function(r){return r.text().then(function(t){Bridge.done($id, r.ok, r.ok ? t : ('HTTP '+r.status))})})" +
            ".catch(function(e){Bridge.done($id,false,String(e))})"
        main.post {
            ensure()
            web?.evaluateJavascript(js, null)
        }
        if (!latch.await(20, TimeUnit.SECONDS)) {
            latches.remove(id)
            results.remove(id)
            throw IOException("Tempo esgotado (navegador interno)")
        }
        val r = results.remove(id) ?: throw IOException("Sem resposta")
        val text = r[1] ?: ""
        if (r[0] != "1" || !text.trimStart().startsWith("{")) throw IOException("Navegador interno: " + text.take(120))
        return text
    }
}
