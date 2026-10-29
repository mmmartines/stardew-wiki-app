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
    private var requestId = 0
    private var searching = false

    override fun onCreate(savedInstanceState: Bundle?) {
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
        applyInsets(toolbar, bottomNav)
        setupHome()
        bottomNav.setOnItemSelectedListener { showTab(it.itemId); true }
        showTab(R.id.nav_home)

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
        val names = Lang.all.map { it.name }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.choose_language)
            .setCancelable(cancelable)
            .setSingleChoiceItems(names, Lang.index()) { d, i ->
                d.dismiss()
                val chosen = Lang.all[i].code
                if (then != null) getSharedPreferences("wiki", MODE_PRIVATE).edit().putBoolean("pending_welcome", true).apply()
                Lang.set(this, chosen) // pode recriar a tela no novo idioma
                // se a tela não for recriada, atualiza aqui mesmo
                handler.postDelayed({
                    if (isFinishing || isDestroyed) return@postDelayed
                    homeKey = ""
                    if (!searching) showTab(bottomNav.selectedItemId)
                    if (getSharedPreferences("wiki", MODE_PRIVATE).getBoolean("pending_welcome", false)) welcome()
                }, 500)
            }
            .show()
    }

    override fun onPostResume() {
        super.onPostResume()
        val prefs = getSharedPreferences("wiki", MODE_PRIVATE)
        if (prefs.getBoolean("pending_welcome", false) && !isFinishing) {
            prefs.edit().putBoolean("pending_welcome", false).apply()
            welcome()
        }
    }

    private fun welcome() {
        getSharedPreferences("wiki", MODE_PRIVATE).edit().putBoolean("pending_welcome", false).apply()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.welcome_title)
            .setMessage(R.string.welcome_text)
            .setCancelable(false)
            .setPositiveButton(R.string.welcome_continue, null)
            .setNeutralButton(R.string.welcome_credits) { _, _ -> openCredits() }
            .show()
    }

    private fun openCredits() = startActivity(Intent(this, CreditsActivity::class.java))

    // ---------- Abas ----------

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
                supportActionBar?.subtitle = Lang.all[Lang.index()].name
                renderHome()
            }
        }
    }

    private fun showTitles(titles: List<String>, emptyMsg: String, fav: Boolean) {
        list.layoutManager = LinearLayoutManager(this)
        rows.items = titles.map { t ->
            Row(t, null, { ArticleActivity.start(this, t) }, { confirmRemove(t, fav) })
        }
        list.adapter = rows
        if (titles.isEmpty()) showEmpty(emptyMsg)
    }

    private fun confirmRemove(t: String, fav: Boolean) {
        MaterialAlertDialogBuilder(this)
            .setTitle(t)
            .setMessage(if (fav) R.string.remove_fav_q else R.string.remove_hist_q)
            .setPositiveButton(R.string.remove) { _, _ ->
                if (fav) store.removeFavorite(t) else store.removeHistory(t)
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
        val r = store.history().take(6)
        val key = Lang.code + "|" + r.joinToString("|")
        if (key == homeKey && homeWeb.url != null) return
        homeKey = key
        recent = r
        val dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        homeWeb.loadDataWithBaseURL(WikiApi.BASE + "/", Home.html(this, Fonts.css(this), dark, r), "text/html", "utf-8", null)
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
        (store.favorites() + store.history()).distinct().filter { it.contains(q, ignoreCase = true) }

    private fun suggest(q: String) {
        val id = ++requestId
        progress.isVisible = true
        runAsync({ WikiApi.prefix(q) }) { r ->
            if (id != requestId) return@runAsync
            progress.isVisible = false
            empty.isVisible = false
            val out = mutableListOf(Row(getString(R.string.search_all_fmt, q), null, { fullSearch(q) }))
            r.onSuccess { hits ->
                hits.forEach { h -> out.add(Row(h.title, null, { ArticleActivity.start(this, h.title) })) }
            }.onFailure {
                out.add(Row(getString(R.string.offline_title), getString(R.string.offline_sub), {}))
                localMatches(q).forEach { t -> out.add(Row(t, getString(R.string.saved_on_device), { ArticleActivity.start(this, t) })) }
            }
            rows.items = out
        }
    }

    private fun fullSearch(q: String) {
        pending?.let { handler.removeCallbacks(it) }
        val id = ++requestId
        progress.isVisible = true
        empty.isVisible = false
        runAsync({ WikiApi.search(q) }) { r ->
            if (id != requestId) return@runAsync
            progress.isVisible = false
            r.onSuccess { hits ->
                rows.items = hits.map { h -> Row(h.title, h.snippet, { ArticleActivity.start(this, h.title) }) }
                if (hits.isEmpty()) showEmpty(getString(R.string.nothing_found_fmt, q))
            }.onFailure {
                val local = localMatches(q)
                rows.items = local.map { t -> Row(t, getString(R.string.saved_on_device), { ArticleActivity.start(this, t) }) }
                if (local.isEmpty()) showEmpty(getString(R.string.no_connection))
            }
        }
    }

    // ---------- Menu ----------

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_language -> chooseLanguage(cancelable = true)

            R.id.action_clear_history -> MaterialAlertDialogBuilder(this)
                .setMessage(R.string.clear_history_q)
                .setPositiveButton(R.string.delete) { _, _ ->
                    store.clearHistory()
                    if (!searching) showTab(bottomNav.selectedItemId)
                }
                .setNegativeButton(R.string.cancel, null)
                .show()

            R.id.action_clear_cache -> {
                val mb = store.cacheBytes() / 1048576.0
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.offline_pages)
                    .setMessage(getString(R.string.offline_pages_fmt, store.cacheCount(), mb))
                    .setPositiveButton(R.string.delete) { _, _ -> store.clearCache(); toast(getString(R.string.cache_cleared)) }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }

            R.id.action_about -> openCredits()

            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }
}
