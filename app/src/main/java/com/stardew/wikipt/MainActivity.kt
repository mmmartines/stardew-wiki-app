package com.stardew.wikipt

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MainActivity : AppCompatActivity() {
    private lateinit var store: Store
    private lateinit var list: RecyclerView
    private lateinit var empty: TextView
    private lateinit var progress: View
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var homeWeb: WebView

    private val rows = RowAdapter()
    private var searchItem: MenuItem? = null
    private var homeKey = ""
    private var recent: List<String> = emptyList()
    private val handler = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null
    @Volatile private var requestId = 0
    private var searching = false

    override fun onCreate(savedInstanceState: Bundle?) {
        edgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        store = Store(this)
        list = findViewById(R.id.list)
        empty = findViewById(R.id.empty)
        progress = findViewById(R.id.progress)
        bottomNav = findViewById(R.id.bottom_nav)
        homeWeb = findViewById(R.id.home_web)
        applyInsets(toolbar, bottomNav, navBar = true)
        setupHome()
        bottomNav.setOnItemSelectedListener { showTab(it.itemId); true }
        showTab(R.id.nav_home)
        onBackPressedDispatcher.addCallback(this, closeSearch)
        rows.attachSwipe(list)
        Offline.attach(this, toolbar, full = true,
            onSeeSaved = { ListActivity.start(this, ListActivity.MODE_SAVED, null) },
            recents = { store.historyHere().filter { store.isSaved(it) }.take(4) },
            onOpenPage = { ArticleActivity.start(this, it) },
            onSeeFavorites = { bottomNav.selectedItemId = R.id.nav_fav },
            savedCount = { store.cacheCount() },
            onOnline = {
                homeKey = ""
                if (!searching) showTab(bottomNav.selectedItemId)
            })

        if (!Lang.load(this)) firstRun()
    }

    override fun onResume() {
        super.onResume()
        if (!searching) showTab(bottomNav.selectedItemId)
    }

    // ---------- Primeira abertura: idioma + créditos ----------

    private fun firstRun() {
        chooseLanguage(cancelable = false) { welcome() }
    }

    private fun chooseLanguage(cancelable: Boolean, then: (() -> Unit)? = null) {
        val selected = Lang.code
        val adapter = object : android.widget.BaseAdapter() {
            override fun getCount() = Lang.all.size
            override fun getItem(i: Int) = Lang.all[i]
            override fun getItemId(i: Int) = i.toLong()
            override fun getView(i: Int, convert: View?, parent: android.view.ViewGroup): View {
                val v = convert ?: layoutInflater.inflate(R.layout.item_lang, parent, false)
                val w = Lang.all[i]
                v.findViewById<TextView>(R.id.flag).text = w.flag
                v.findViewById<TextView>(R.id.name).text = w.name
                v.findViewById<View>(R.id.check).visibility = if (w.code == selected) View.VISIBLE else View.INVISIBLE
                return v
            }
        }
        wikiDialog()
            .setTitle(R.string.choose_language)
            .setCancelable(cancelable)
            .setAdapter(adapter) { d, i ->
                d.dismiss()
                if (Lang.all[i].code == selected && then == null) return@setAdapter
                val chosen = Lang.all[i].code
                if (then != null) getSharedPreferences("wiki", MODE_PRIVATE).edit().putBoolean("pending_welcome", true).apply()
                Lang.set(this, chosen) // pode recriar a tela no novo idioma
                // se a tela não for recriada, atualiza aqui mesmo
                handler.postDelayed({
                    if (isFinishing || isDestroyed) return@postDelayed
                    homeKey = ""
                    invalidateOptionsMenu()
                    if (!searching) showTab(bottomNav.selectedItemId)
                    if (getSharedPreferences("wiki", MODE_PRIVATE).getBoolean("pending_welcome", false)) welcome()
                }, 500)
            }
            .show().framed()
    }

    override fun onPostResume() {
        super.onPostResume()
        val prefs = getSharedPreferences("wiki", MODE_PRIVATE)
        if (prefs.getBoolean("pending_welcome", false) && !isFinishing) {
            prefs.edit().putBoolean("pending_welcome", false).apply()
            welcome()
        }
    }

    /** Tela "feito por um fã" — aparece depois de escolher o idioma pela primeira vez. */
    private fun welcome() {
        getSharedPreferences("wiki", MODE_PRIVATE).edit().putBoolean("pending_welcome", false).apply()
        startActivity(Intent(this, FanActivity::class.java))
    }

    private fun openCredits() = startActivity(Intent(this, CreditsActivity::class.java))

    // ---------- Abas ----------

    /** Voltar fecha a busca (necessário com o "voltar preditivo" do Android). */
    private val closeSearch = object : androidx.activity.OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            searchItem?.collapseActionView()
        }
    }

    private fun showTab(id: Int) {
        progress.isVisible = false
        empty.isVisible = false
        homeWeb.isVisible = id == R.id.nav_home
        list.isVisible = id != R.id.nav_home
        supportActionBar?.subtitle = null
        when (id) {
            R.id.nav_fav -> {
                supportActionBar?.title = getString(R.string.tab_favorites)
                showTitles(store.favorites(), getString(R.string.empty_favorites), fav = true)
            }
            R.id.nav_history -> {
                supportActionBar?.title = getString(R.string.tab_history)
                showTitles(store.history(), getString(R.string.empty_history), fav = false)
            }
            else -> {
                supportActionBar?.title = getString(R.string.app_name)
                supportActionBar?.subtitle = getString(R.string.toolbar_subtitle)
                renderHome()
            }
        }
    }

    private fun showTitles(items: List<Store.Entry>, emptyMsg: String, fav: Boolean) {
        list.layoutManager = LinearLayoutManager(this)
        val out = mutableListOf<Row>()
        if (items.isNotEmpty() && !store.swipeLearned) out.add(Row(getString(R.string.swipe_hint), null, {}, header = true))
        val timeFmt = android.text.format.DateFormat.getTimeFormat(this)
        var group: String? = null
        items.forEachIndexed { index, e ->
            if (!fav) {
                val g = dayLabel(e.time)
                if (g != group) { out.add(Row(g, null, {}, header = true)); group = g }
            }
            val w = Lang.all.firstOrNull { it.code == e.lang }
            val parts = mutableListOf<String>()
            if (!fav && e.time > 0) parts.add(timeFmt.format(java.util.Date(e.time)))
            if (e.lang != Lang.code && w != null) parts.add(w.flag + "  " + w.name)
            out.add(Row(e.title, parts.joinToString("  ·  ").ifEmpty { null }, { openEntry(e) }, { confirmRemove(e, fav) },
                onSwipe = { removeWithUndo(e, index, fav) }))
        }
        rows.items = out
        list.adapter = rows
        if (items.isEmpty()) showEmpty(emptyMsg)
    }

    /** "Hoje", "Ontem" ou a data ("2 de nov."), no fuso do celular; itens antigos sem hora ficam em "Antes". */
    private fun dayLabel(t: Long): String {
        if (t <= 0) return getString(R.string.day_earlier)
        val cal = java.util.Calendar.getInstance()
        fun dayIndex(ms: Long): Long {
            cal.timeInMillis = ms
            return cal.get(java.util.Calendar.YEAR) * 400L + cal.get(java.util.Calendar.DAY_OF_YEAR)
        }
        val now = System.currentTimeMillis()
        val d = dayIndex(t)
        return when (d) {
            dayIndex(now) -> getString(R.string.day_today)
            dayIndex(now - 86_400_000L) -> getString(R.string.day_yesterday)
            else -> {
                val loc = resources.configuration.locales[0]
                val pattern = android.text.format.DateFormat.getBestDateTimePattern(loc, "dMMMyyyy")
                java.text.SimpleDateFormat(pattern, loc).format(java.util.Date(t))
            }
        }
    }

    /** Deslizou: remove na hora e oferece "Desfazer" por alguns segundos. */
    private fun removeWithUndo(e: Store.Entry, index: Int, fav: Boolean) {
        if (fav) store.removeFavorite(e) else store.removeHistory(e)
        store.swipeLearned = true
        showTab(bottomNav.selectedItemId)
        com.google.android.material.snackbar.Snackbar.make(list, getString(R.string.removed_fmt, e.title), 5000)
            .setAnchorView(bottomNav)
            .setAction(R.string.undo) {
                store.restore(e, index, fav)
                showTab(bottomNav.selectedItemId)
            }
            .show()
    }

    /** Abre um favorito/histórico; se for de outro idioma, procura a mesma página no idioma atual. */
    private fun openEntry(e: Store.Entry) {
        if (e.lang == Lang.code) {
            ArticleActivity.start(this, e.title); return
        }
        progress.isVisible = true
        val to = Lang.code
        runAsync({ WikiApi.translate(e.title, e.lang, to) }) { r ->
            progress.isVisible = false
            val t = r.getOrNull()
            if (t != null) {
                ArticleActivity.start(this, t)
            } else {
                val from = Lang.all.first { it.code == e.lang }
                wikiDialog()
                    .setTitle(e.title)
                    .setMessage(getString(R.string.not_in_lang_fmt, Lang.current().name, from.name))
                    .setPositiveButton(getString(R.string.open_in_fmt, from.flag + " " + from.name)) { _, _ ->
                        Lang.set(this, e.lang)
                        ArticleActivity.start(this, e.title)
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        }
    }

    private fun confirmRemove(e: Store.Entry, fav: Boolean) {
        wikiDialog()
            .setTitle(e.title)
            .setMessage(if (fav) R.string.remove_fav_q else R.string.remove_hist_q)
            .setPositiveButton(R.string.remove) { _, _ ->
                if (fav) store.removeFavorite(e) else store.removeHistory(e)
                showTab(bottomNav.selectedItemId)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showEmpty(msg: String) {
        empty.text = msg
        empty.isVisible = true
    }

    // ---------- Tela inicial ----------

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    private fun setupHome() {
        homeWeb.settings.javaScriptEnabled = true
        // ícones da Início vêm do cache quando possível (abre mais rápido e funciona offline)
        homeWeb.settings.cacheMode = android.webkit.WebSettings.LOAD_CACHE_ELSE_NETWORK
        homeWeb.addJavascriptInterface(object {
            @JavascriptInterface
            fun open(i: Int) = runOnUiThread { Categories.all.getOrNull(i)?.let { this@MainActivity.open(it.target) } }

            @JavascriptInterface
            fun recent(i: Int) = runOnUiThread { recent.getOrNull(i)?.let { ArticleActivity.start(this@MainActivity, it) } }

            @JavascriptInterface
            fun search() = runOnUiThread { searchItem?.expandActionView() }

            @JavascriptInterface
            fun credits() = runOnUiThread { openCredits() }
        }, "Android")
    }

    private fun renderHome() {
        val r = store.historyHere().take(6)
        val dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val base = Lang.code + "|" + dark
        val key = base + "|" + r.joinToString("|")
        if (key == homeKey && homeWeb.url != null) return
        recent = r
        if (homeKey.startsWith("$base|") && homeWeb.url != null) {
            // mesma página: troca só os chips, sem recarregar nem perder a rolagem
            homeKey = key
            homeWeb.evaluateJavascript("window.__recent&&__recent(" + org.json.JSONObject.quote(Home.recentHtml(this, r)) + ")", null)
            return
        }
        homeKey = key
        // monta o HTML (com a fonte embutida) fora da thread principal, para não travar a abertura
        runAsync({ Home.html(this, Fonts.css(this), dark, r) }) { res ->
            if (homeKey != key) return@runAsync // já mudou de novo
            res.onSuccess { homeWeb.loadDataWithBaseURL(WikiApi.BASE + "/", it, "text/html", "utf-8", null) }
        }
    }

    // ---------- Navegação ----------

    private fun open(target: Target) {
        when (target) {
            is Target.Article -> ArticleActivity.start(this, Titles.of(this, target.en))
            Target.AllPages -> ListActivity.start(this, ListActivity.MODE_ALL, null)
            Target.AllCategories -> ListActivity.start(this, ListActivity.MODE_CATS, null)
            Target.Random -> {
                progress.isVisible = true
                runAsync({ WikiApi.random() }) { r ->
                    progress.isVisible = false
                    r.onSuccess { ArticleActivity.start(this, it) }
                        .onFailure { toast(getString(R.string.no_connection)) }
                }
            }
        }
    }

    // ---------- Busca ----------

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        menu.findItem(R.id.action_language)?.actionView?.findViewById<TextView>(R.id.flag_button)?.apply {
            text = Lang.current().flag
            // leitor de tela lê "Idioma da wiki: Português", não o emoji da bandeira
            contentDescription = getString(R.string.language) + ": " + Lang.current().name
            minWidth = (48 * resources.displayMetrics.density).toInt()
            minHeight = (48 * resources.displayMetrics.density).toInt()
            contentDescription = getString(R.string.language) + ": " + Lang.current().name
            androidx.appcompat.widget.TooltipCompat.setTooltipText(this, contentDescription)
            setOnClickListener { chooseLanguage(cancelable = true) }
        }
        val item = menu.findItem(R.id.action_search)
        searchItem = item
        val sv = item.actionView as SearchView
        sv.queryHint = getString(R.string.search_hint)
        sv.maxWidth = Int.MAX_VALUE
        sv.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                val q = query.trim()
                if (q.isNotEmpty()) fullSearch(q)
                sv.clearFocus()
                return true
            }

            override fun onQueryTextChange(newText: String): Boolean {
                if (!searching) return true
                val q = newText.trim()
                pending?.let { handler.removeCallbacks(it) }
                if (q.isEmpty()) {
                    requestId++
                    progress.isVisible = false
                    rows.items = emptyList()
                    showEmpty(getString(R.string.type_to_search))
                } else {
                    val r = Runnable { suggest(q) }
                    pending = r
                    handler.postDelayed(r, 300)
                }
                return true
            }
        })
        item.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                closeSearch.isEnabled = true
                searching = true
                bottomNav.isVisible = false
                homeWeb.isVisible = false
                list.isVisible = true
                supportActionBar?.subtitle = null
                list.layoutManager = LinearLayoutManager(this@MainActivity)
                rows.items = emptyList()
                list.adapter = rows
                showEmpty(getString(R.string.type_to_search))
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                closeSearch.isEnabled = false
                searching = false
                requestId++
                pending?.let { handler.removeCallbacks(it) }
                bottomNav.isVisible = true
                showTab(bottomNav.selectedItemId)
                return true
            }
        })
        return true
    }

    private fun localMatches(q: String): List<String> =
        (store.favorites() + store.history()).filter { it.lang == Lang.code }.map { it.title }.distinct()
            .filter { it.contains(q, ignoreCase = true) }

    private fun suggest(q: String) {
        val id = ++requestId
        progress.isVisible = true
        runAsync({ if (id != requestId) throw java.util.concurrent.CancellationException(); WikiApi.prefix(q) }) { r ->
            if (id != requestId) return@runAsync
            progress.isVisible = false
            empty.isVisible = false
            val out = mutableListOf(Row(getString(R.string.search_all_fmt, q), null, { fullSearch(q) }))
            r.onSuccess { hits ->
                hits.forEach { h -> out.add(Row(h.title, null, { ArticleActivity.start(this, h.title) })) }
            }.onFailure { e ->
                if (e is OfflineException || !Net.online()) {
                    out.add(Row(getString(R.string.offline_title), getString(R.string.offline_sub), {}))
                } else {
                    out.add(Row("⚠️  " + getString(R.string.search_failed), null, {}))
                }
                localMatches(q).forEach { t -> out.add(Row(t, getString(R.string.saved_on_device), { ArticleActivity.start(this, t) })) }
            }
            rows.items = out
            val few = (r.getOrNull()?.size ?: 99) < 3
            if (few && Lang.code != "en") crossLanguage(q, id, out)
        }
    }

    /**
     * Poucos resultados no idioma atual: procura o termo na wiki em inglês e sugere a página equivalente
     * ("Parsnip" na wiki PT → Chirívia). Sem equivalente, oferece abrir na wiki em inglês.
     */
    private fun crossLanguage(q: String, id: Int, base: List<Row>) {
        val here = Lang.code
        runAsync({
            if (id != requestId) throw java.util.concurrent.CancellationException()
            val en = WikiApi.prefix(q, "en", 3).map { it.title }
            if (id != requestId) throw java.util.concurrent.CancellationException()
            WikiApi.translateMany(en, "en", here)
        }) { r ->
            if (id != requestId || Lang.code != here) return@runAsync
            val pairs = r.getOrNull() ?: return@runAsync
            val shown = base.map { it.title.lowercase() }.toSet()
            val extra = mutableListOf<Row>()
            pairs.forEach { (en, t) ->
                if (t != null) {
                    if (t.lowercase() !in shown) extra.add(Row(t, getString(R.string.did_you_mean_fmt, en), { ArticleActivity.start(this, t) }))
                } else {
                    extra.add(Row(en, getString(R.string.only_in_english), { offerEnglish(en) }))
                }
            }
            if (extra.isNotEmpty()) rows.items = base + extra.distinctBy { it.title.lowercase() }
        }
    }

    /** Página que só existe na wiki em inglês: pergunta antes de trocar o idioma. */
    private fun offerEnglish(title: String) {
        val en = Lang.all.first { it.code == "en" }
        wikiDialog()
            .setTitle(title)
            .setMessage(getString(R.string.not_in_lang_fmt, Lang.current().name, en.name))
            .setPositiveButton(getString(R.string.open_in_fmt, en.flag + " " + en.name)) { _, _ ->
                searchItem?.collapseActionView()
                Lang.set(this, "en")
                ArticleActivity.start(this, title)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun fullSearch(q: String) {
        pending?.let { handler.removeCallbacks(it) }
        val id = ++requestId
        progress.isVisible = true
        empty.isVisible = false
        runAsync({ if (id != requestId) throw java.util.concurrent.CancellationException(); WikiApi.search(q) }) { r ->
            if (id != requestId) return@runAsync
            progress.isVisible = false
            r.onSuccess { hits ->
                rows.items = hits.map { h -> Row(h.title, h.snippet, { ArticleActivity.start(this, h.title) }) }
                if (hits.isEmpty()) showEmpty(getString(R.string.nothing_found_fmt, q))
            }.onFailure { e ->
                val local = localMatches(q)
                rows.items = local.map { t -> Row(t, getString(R.string.saved_on_device), { ArticleActivity.start(this, t) }) }
                if (local.isEmpty()) showEmpty(friendlyError(e, page = false))
            }
        }
    }

    // ---------- Menu ----------

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_language -> chooseLanguage(cancelable = true)

            R.id.action_clear_history -> wikiDialog()
                .setMessage(R.string.clear_history_q)
                .setPositiveButton(R.string.delete) { _, _ ->
                    store.clearHistory()
                    if (!searching) showTab(bottomNav.selectedItemId)
                }
                .setNegativeButton(R.string.cancel, null)
                .show()

            R.id.action_clear_cache -> {
                val mb = store.cacheBytes() / 1048576.0
                val n = store.cacheCount()
                val dlg = wikiDialog().setTitle(R.string.offline_pages)
                if (n == 0) {
                    dlg.setMessage(R.string.offline_pages_none).setPositiveButton(R.string.ok, null)
                } else {
                    dlg.setMessage(getString(R.string.offline_pages_fmt, n, mb))
                        .setPositiveButton(R.string.view_list) { _, _ -> ListActivity.start(this, ListActivity.MODE_SAVED, null) }
                        .setNeutralButton(R.string.delete) { _, _ -> store.clearCache(); toast(getString(R.string.cache_cleared)) }
                        .setNegativeButton(R.string.cancel, null)
                }
                dlg.show()
            }

            R.id.action_about -> openCredits()
            R.id.action_fan -> startActivity(Intent(this, FanActivity::class.java))

            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }
}
