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

    /** Wiki escolhida nos botões da tela inicial: "base" (jogo base) ou o código do mod. */
    private var homeChoice = Wikis.BASE
    private fun homeWiki(): Wiki = Wikis.fromChoice(homeChoice)
    /** Itens do menu mostrado na tela inicial (o índice vem da página). */
    private var menuItems: List<Cat> = Categories.all

    override fun onCreate(savedInstanceState: Bundle?) {
        edgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        store = Store(this)
        // o app abre na wiki marcada em "Wiki ao abrir o app" (jogo base ou um mod)
        homeChoice = savedInstanceState?.getString("home_src") ?: Wikis.startChoice(this)
        list = findViewById(R.id.list)
        list.capWidth()
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
            onSeeSaved = { ListActivity.start(this, ListActivity.MODE_SAVED, null, homeWiki().key) },
            recents = { homeWiki().key.let { k -> store.historyHere(k).filter { store.isSaved(it, k) }.take(4) } },
            onOpenPage = { ArticleActivity.start(this, it, src = homeWiki().key) },
            onSeeFavorites = { bottomNav.selectedItemId = R.id.nav_fav },
            savedCount = { store.cacheCount() },
            onOnline = {
                homeKey = ""
                if (!searching) showTab(bottomNav.selectedItemId)
            })

        if (!Lang.load(this)) firstRun()
        shotSetup()
    }

    /** Só no APK de teste: atalhos para os prints automáticos do GitHub (sem efeito na versão da loja). */
    private fun shotSetup() {
        if (!BuildConfig.DEBUG) return
        intent.getStringExtra("shot_src")?.let { homeChoice = it; homeKey = ""; showTab(R.id.nav_home) }
        intent.getStringExtra("shot_page")?.let { ArticleActivity.start(this, it, src = intent.getStringExtra("shot_page_src")) }
    }

    override fun onSaveInstanceState(out: Bundle) {
        super.onSaveInstanceState(out)
        out.putString("home_src", homeChoice)
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
        // com algum item de mod na lista, cada linha diz de onde é (jogo base ou qual mod)
        val mixed = items.any { Wikis.isMod(it.lang) }
        items.forEachIndexed { index, e ->
            if (!fav) {
                val g = dayLabel(e.time)
                if (g != group) { out.add(Row(g, null, {}, header = true)); group = g }
            }
            val w = Lang.all.firstOrNull { it.code == e.lang }
            val parts = mutableListOf<String>()
            if (!fav && e.time > 0) parts.add(timeFmt.format(java.util.Date(e.time)))
            if (Wikis.isMod(e.lang)) {
                val m = Wikis.of(e.lang)
                parts.add(m.emoji + "  " + getString(R.string.src_mod_fmt, m.short))
            } else if (e.lang != Lang.code && w != null) {
                parts.add((if (mixed) getString(R.string.src_base) + "  ·  " else "") + w.flag + "  " + w.name)
            } else if (mixed) parts.add("⭐  " + getString(R.string.src_base))
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
        if (Wikis.isMod(e.lang)) {
            ArticleActivity.start(this, e.title, src = e.lang); return
        }
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
            fun open(i: Int) = runOnUiThread { menuItems.getOrNull(i)?.let { this@MainActivity.open(it.target, homeWiki()) } }

            @JavascriptInterface
            fun recent(i: Int) = runOnUiThread { recent.getOrNull(i)?.let { ArticleActivity.start(this@MainActivity, it, src = homeWiki().key) } }

            /** Botões de wiki no topo: 0 = jogo base, 1… = mods. */
            @JavascriptInterface
            fun source(i: Int) = runOnUiThread {
                val c = if (i <= 0) Wikis.BASE else Wikis.mods.getOrNull(i - 1)?.key ?: Wikis.BASE
                if (c != homeChoice) {
                    homeChoice = c
                    homeKey = ""
                    if (!searching) showTab(R.id.nav_home)
                }
            }

            @JavascriptInterface
            fun search() = runOnUiThread { searchItem?.expandActionView() }

            @JavascriptInterface
            fun credits() = runOnUiThread { openCredits() }
        }, "Android")
    }

    private fun renderHome() {
        val wiki = homeWiki()
        val r = store.historyHere(wiki.key).take(6)
        val dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val base = Lang.code + "|" + homeChoice + "|" + dark
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
        val menu = if (wiki.mod) listOf(wiki.name to wiki.menu)
            else Categories.groups.map { getString(it.nameRes) to it.items }
        menuItems = menu.flatMap { it.second }
        runAsync({ Home.html(this, Fonts.css(this), dark, r, wiki, menu) }) { res ->
            if (homeKey != key) return@runAsync // já mudou de novo
            res.onSuccess { homeWeb.loadDataWithBaseURL(WikiApi.BASE + "/", it, "text/html", "utf-8", null) }
        }
    }

    // ---------- Navegação ----------

    private fun open(target: Target, w: Wiki) {
        val src = if (w.mod) w.key else null
        when (target) {
            // nos mods o título é o da wiki do mod (em inglês); na oficial, o do idioma atual
            is Target.Article -> ArticleActivity.start(this, if (w.mod) target.en else Titles.of(this, target.en), src = src)
            Target.AllPages -> ListActivity.start(this, ListActivity.MODE_ALL, null, src)
            Target.AllCategories -> ListActivity.start(this, ListActivity.MODE_CATS, null, src)
            Target.Random -> {
                progress.isVisible = true
                runAsync({ WikiApi.random(w) }) { r ->
                    progress.isVisible = false
                    r.onSuccess { ArticleActivity.start(this, it, src = src) }
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
        if (BuildConfig.DEBUG) intent.getStringExtra("shot_q")?.let { q ->
            intent.removeExtra("shot_q")
            handler.post { item.expandActionView(); sv.setQuery(q, true) }
        }
        sv.queryHint = getString(R.string.search_hint_all)
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
                    parts = emptyList()
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

    // ---------- Busca global (jogo base + todos os mods) ----------

    /** Resultado de uma wiki na busca global. */
    private class Part(val wiki: Wiki, var rows: List<Row>, val failed: Boolean)

    /** Seções da busca em andamento e a linha "Pesquisar … em todas as páginas" do topo. */
    private var parts: List<Part> = emptyList()
    private var searchTop: List<Row> = emptyList()

    /** Ordem das seções: a wiki aberta na tela inicial primeiro, depois as outras. */
    private fun searchOrder(): List<Wiki> {
        val cur = homeWiki()
        return listOf(cur) + (listOf(Wikis.base()) + Wikis.mods).filter { it.key != cur.key }
    }

    private fun sectionTitle(w: Wiki): String =
        if (w.mod) w.emoji + "  " + getString(R.string.src_mod_fmt, w.name)
        else "⭐  " + getString(R.string.src_base) + " · " + Lang.current().flag + " " + Lang.current().name

    private fun openHit(title: String, w: Wiki) =
        ArticleActivity.start(this, title, src = if (w.mod) w.key else null)

    /** Monta a lista: cada wiki com resultados ganha um cabeçalho com o nome (jogo base ou qual mod). */
    private fun renderSearch(q: String, full: Boolean) {
        val out = searchTop.toMutableList()
        parts.forEach { p ->
            if (p.rows.isNotEmpty()) {
                out.add(Row(sectionTitle(p.wiki), null, {}, header = true))
                out.addAll(p.rows)
            }
        }
        if (parts.isNotEmpty() && parts.all { it.failed }) {
            if (!Net.online()) out.add(Row(getString(R.string.offline_title), getString(R.string.offline_sub), {}))
            else out.add(Row("⚠️  " + getString(R.string.search_failed), null, {}))
            localMatches(q).forEach { out.add(it) }
        }
        empty.isVisible = false
        rows.items = out
        if (full && out.isEmpty()) showEmpty(getString(R.string.nothing_found_fmt, q))
    }

    /** Páginas já vistas ou favoritas (jogo base no idioma atual e mods) que contêm o texto — usado sem internet. */
    private fun localMatches(q: String): List<Row> =
        (store.favorites() + store.history())
            .filter { it.lang == Lang.code || Wikis.isMod(it.lang) }
            .distinctBy { it.lang + "\u0000" + it.title }
            .filter { it.title.contains(q, ignoreCase = true) }
            .map { e ->
                val w = Wikis.of(e.lang)
                val where = if (w.mod) w.emoji + "  " + getString(R.string.src_mod_fmt, w.short) else "⭐  " + getString(R.string.src_base)
                Row(e.title, getString(R.string.saved_on_device) + "  ·  " + where, { openHit(e.title, w) })
            }

    private fun searchEverywhere(q: String, id: Int, full: Boolean, done: (List<Part>) -> Unit) {
        val order = searchOrder()
        runAsync({
            if (id != requestId) throw java.util.concurrent.CancellationException()
            inParallel(order.map { w ->
                {
                    // pesquisa velha (a pessoa já digitou outra coisa): nem chega a consultar a wiki
                    if (id != requestId) throw java.util.concurrent.CancellationException()
                    if (full) WikiApi.search(q, w, if (w.mod) 15 else 40) else WikiApi.prefix(q, w, if (w.mod) 6 else 12)
                }
            })
        }) { r ->
            if (id != requestId) return@runAsync
            val res = r.getOrNull() ?: order.map { Result.failure(r.exceptionOrNull() ?: Exception()) }
            done(order.zip(res).map { (w, x) ->
                val hits = x.getOrNull().orEmpty()
                Part(w, hits.map { h -> Row(h.title, h.snippet.ifBlank { null }, { openHit(h.title, w) }) }, x.isFailure)
            })
        }
    }

    private fun suggest(q: String) {
        val id = ++requestId
        progress.isVisible = true
        searchEverywhere(q, id, full = false) { p ->
            progress.isVisible = false
            parts = p
            searchTop = listOf(Row(getString(R.string.search_all_fmt, q), null, { fullSearch(q) }))
            renderSearch(q, full = false)
            val base = p.first { !it.wiki.mod }
            if (!base.failed && base.rows.size < 3 && Lang.code != "en") crossLanguage(q, id, full = false)
        }
    }

    /**
     * Poucos resultados no jogo base: procura o termo na wiki em inglês e sugere a página equivalente
     * ("Parsnip" na wiki PT → Chirívia). Sem equivalente, oferece abrir na wiki em inglês.
     */
    private fun crossLanguage(q: String, id: Int, full: Boolean, onDone: (() -> Unit)? = null) {
        val here = Lang.code
        progress.isVisible = true
        runAsync({
            if (id != requestId) throw java.util.concurrent.CancellationException()
            crossLookup(q, here, id)
        }) { r ->
            if (id != requestId || Lang.code != here) return@runAsync
            progress.isVisible = false
            val base = parts.firstOrNull { !it.wiki.mod }
            val pairs = r.getOrNull().orEmpty()
            if (base != null && pairs.isNotEmpty()) {
                val shown = base.rows.map { it.title.lowercase() }.toMutableSet()
                val extra = mutableListOf<Row>()
                pairs.forEach { (en, t) ->
                    val row = if (t != null) Row(t, getString(R.string.did_you_mean_fmt, en), { ArticleActivity.start(this, t) })
                    else Row(en, getString(R.string.only_in_english), { offerEnglish(en) })
                    if (shown.add(row.title.lowercase())) extra.add(row)
                }
                base.rows = base.rows + extra
            }
            renderSearch(q, full)
            onDone?.invoke()
        }
    }

    /**
     * Nome em inglês → página no idioma atual. Primeiro pergunta à própria wiki do idioma (funciona sempre);
     * se não achar, busca o termo na wiki em inglês e traduz pelos links entre idiomas.
     */
    private fun crossLookup(q: String, here: String, id: Int): List<Pair<String, String?>> {
        val words = q.trim().split(Regex("\\s+"))
        val candidates = listOf(
            q.trim().replaceFirstChar { it.uppercase() },
            words.joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } },
        ).distinct()
        for (c in candidates) {
            if (id != requestId) throw java.util.concurrent.CancellationException()
            val local = try { WikiApi.fromEnglish(c) } catch (e: java.io.IOException) { emptyList() }
            if (local.isNotEmpty()) return local.map { c to it }
        }
        if (id != requestId) throw java.util.concurrent.CancellationException()
        return try {
            val en = WikiApi.prefix(q, Wikis.base("en"), 3).map { it.title }
            WikiApi.translateMany(en, "en", here)
        } catch (e: java.io.IOException) {
            emptyList()
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
        searchEverywhere(q, id, full = true) { p ->
            progress.isVisible = false
            parts = p
            searchTop = emptyList()
            val base = p.first { !it.wiki.mod }
            if (!base.failed && base.rows.size < 3 && Lang.code != "en") {
                // poucos resultados no jogo base: tenta o nome em inglês antes de dizer "nada encontrado"
                renderSearch(q, full = false)
                crossLanguage(q, id, full = true)
            } else {
                renderSearch(q, full = true)
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

            R.id.action_start_wiki -> chooseStartWiki()
            R.id.action_about -> openCredits()
            R.id.action_fan -> startActivity(Intent(this, FanActivity::class.java))

            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    /** "Wiki ao abrir o app": jogo base ou um mod. A busca do topo continua procurando em todas. */
    private fun chooseStartWiki() {
        val choices = listOf(Wikis.BASE) + Wikis.mods.map { it.key }
        val names = choices.map { c -> if (c == Wikis.BASE) getString(R.string.src_base) else Wikis.of(c).name }
        val labels = choices.mapIndexed { i, c -> (if (c == Wikis.BASE) "⭐" else Wikis.of(c).emoji) + "  " + names[i] }.toTypedArray()
        val cur = choices.indexOf(Wikis.startChoice(this)).coerceAtLeast(0)
        wikiDialog()
            .setTitle(R.string.start_wiki)
            .setSingleChoiceItems(labels, cur) { d, i ->
                val c = choices[i]
                Wikis.setStartChoice(this, c)
                d.dismiss()
                toast(getString(R.string.start_wiki_set_fmt, names[i]))
                // já mostra a wiki escolhida
                if (c != homeChoice) {
                    homeChoice = c
                    homeKey = ""
                    if (!searching) showTab(bottomNav.selectedItemId)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show().framed()
    }
}
