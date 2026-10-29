package com.stardew.wikipt

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var store: Store
    private lateinit var list: RecyclerView
    private lateinit var empty: TextView
    private lateinit var progress: View
    private lateinit var bottomNav: BottomNavigationView

    private val rows = RowAdapter()
    private val cats = CatAdapter { open(it.target) }
    private val handler = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null
    private var requestId = 0
    private var searching = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById<MaterialToolbar>(R.id.toolbar))
        store = Store(this)
        list = findViewById(R.id.list)
        empty = findViewById(R.id.empty)
        progress = findViewById(R.id.progress)
        bottomNav = findViewById(R.id.bottom_nav)

        cats.items = Categories.home
        bottomNav.setOnItemSelectedListener { showTab(it.itemId); true }
        showTab(R.id.nav_home)
    }

    override fun onResume() {
        super.onResume()
        if (!searching && bottomNav.selectedItemId != R.id.nav_home) showTab(bottomNav.selectedItemId)
    }

    // ---------- Abas ----------

    private fun showTab(id: Int) {
        progress.isVisible = false
        empty.isVisible = false
        when (id) {
            R.id.nav_fav -> {
                supportActionBar?.subtitle = getString(R.string.tab_favorites)
                showTitles(store.favorites(), getString(R.string.empty_favorites), fav = true)
            }
            R.id.nav_history -> {
                supportActionBar?.subtitle = getString(R.string.tab_history)
                showTitles(store.history(), getString(R.string.empty_history), fav = false)
            }
            else -> {
                supportActionBar?.subtitle = getString(R.string.home_subtitle)
                list.layoutManager = GridLayoutManager(this, 3)
                list.adapter = cats
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
            .setMessage(if (fav) "Remover dos favoritos?" else "Remover do histórico?")
            .setPositiveButton("Remover") { _, _ ->
                if (fav) store.removeFavorite(t) else store.removeHistory(t)
                showTab(bottomNav.selectedItemId)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showEmpty(msg: String) {
        empty.text = msg
        empty.isVisible = true
    }

    // ---------- Navegação ----------

    private fun open(target: Target) {
        when (target) {
            is Target.Article -> ArticleActivity.start(this, target.title)
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
                    handler.postDelayed(r, 250)
                }
                return true
            }
        })
        item.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                searching = true
                bottomNav.isVisible = false
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
            val out = mutableListOf(Row("🔎  Pesquisar \"$q\" em todo o conteúdo", null, { fullSearch(q) }))
            r.onSuccess { hits ->
                hits.forEach { h -> out.add(Row(h.title, null, { ArticleActivity.start(this, h.title) })) }
            }.onFailure {
                out.add(Row("⚠️  Sem conexão", "Favoritos e páginas já abertas continuam disponíveis offline", {}))
                localMatches(q).forEach { t -> out.add(Row(t, "Salva no aparelho", { ArticleActivity.start(this, t) })) }
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
                if (hits.isEmpty()) showEmpty("Nada encontrado para \"$q\".")
            }.onFailure {
                val local = localMatches(q)
                rows.items = local.map { t -> Row(t, "Salva no aparelho", { ArticleActivity.start(this, t) }) }
                if (local.isEmpty()) showEmpty(getString(R.string.no_connection))
            }
        }
    }

    // ---------- Menu ----------

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_clear_history -> MaterialAlertDialogBuilder(this)
                .setMessage("Apagar todo o histórico?")
                .setPositiveButton("Apagar") { _, _ ->
                    store.clearHistory()
                    if (!searching) showTab(bottomNav.selectedItemId)
                }
                .setNegativeButton("Cancelar", null)
                .show()

            R.id.action_clear_cache -> {
                val mb = store.cacheBytes() / 1048576.0
                MaterialAlertDialogBuilder(this)
                    .setTitle("Páginas salvas offline")
                    .setMessage(
                        String.format(Locale("pt", "BR"), "%d páginas (%.1f MB). Apagar?", store.cacheCount(), mb)
                    )
                    .setPositiveButton("Apagar") { _, _ -> store.clearCache(); toast("Cache apagado") }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }

            R.id.action_about -> MaterialAlertDialogBuilder(this)
                .setTitle(R.string.app_name)
                .setMessage(R.string.about_text)
                .setPositiveButton("OK", null)
                .show()

            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }
}
