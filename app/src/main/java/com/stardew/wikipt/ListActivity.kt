package com.stardew.wikipt

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar

/** Lista paginada: membros de categoria, todas as páginas ou todas as categorias. */
class ListActivity : AppCompatActivity() {

    companion object {
        const val MODE_CATEGORY = 0
        const val MODE_ALL = 1
        const val MODE_CATS = 2
        private const val EXTRA_MODE = "mode"
        private const val EXTRA_CAT = "cat"

        fun start(ctx: Context, mode: Int, category: String?) {
            ctx.startActivity(
                Intent(ctx, ListActivity::class.java)
                    .putExtra(EXTRA_MODE, mode)
                    .putExtra(EXTRA_CAT, category)
            )
        }
    }

    private var mode = MODE_ALL
    private var category = ""
    private lateinit var list: RecyclerView
    private lateinit var progress: View
    private lateinit var empty: TextView

    private val adapter = RowAdapter()
    private val hits = mutableListOf<Hit>()
    private var next: String? = null
    private var loading = false
    private var done = false
    private var filter = ""
    private var generation = 0
    private val handler = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)
        setSupportActionBar(findViewById<MaterialToolbar>(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        WebBridge.init(this)
        mode = intent.getIntExtra(EXTRA_MODE, MODE_ALL)
        category = intent.getStringExtra(EXTRA_CAT) ?: ""
        supportActionBar?.title = when (mode) {
            MODE_CATEGORY -> category.substringAfter(':')
            MODE_CATS -> "Categorias"
            else -> "Todas as páginas"
        }
        if (mode == MODE_CATEGORY) supportActionBar?.subtitle = "Categoria"

        list = findViewById(R.id.list)
        progress = findViewById(R.id.progress)
        empty = findViewById(R.id.empty)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter
        list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (!rv.canScrollVertically(1)) loadMore()
            }
        })

        val input = findViewById<EditText>(R.id.filter)
        input.hint = if (mode == MODE_CATEGORY) "Filtrar nesta categoria…" else "Começa com…"
        input.doAfterTextChanged { e ->
            filter = e?.toString()?.trim() ?: ""
            if (mode == MODE_CATEGORY) {
                render()
            } else {
                pending?.let { handler.removeCallbacks(it) }
                val r = Runnable { reset() }
                pending = r
                handler.postDelayed(r, 350)
            }
        }
        reset()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish(); return true
    }

    private fun reset() {
        generation++
        next = null
        done = false
        loading = false
        hits.clear()
        adapter.items = emptyList()
        empty.isVisible = false
        loadMore()
    }

    private fun loadMore() {
        if (loading || done) return
        loading = true
        progress.isVisible = true
        empty.isVisible = false
        val gen = generation
        val cont = next
        val pre = filter
        runAsync({
            when (mode) {
                MODE_CATEGORY -> WikiApi.categoryMembers(category, cont)
                MODE_CATS -> WikiApi.allCategories(pre, cont)
                else -> WikiApi.allPages(pre, cont)
            }
        }) { r ->
            if (gen != generation) return@runAsync
            loading = false
            progress.isVisible = false
            r.onSuccess { ch ->
                hits.addAll(ch.items)
                next = ch.next
                done = ch.next == null
                render()
                // categorias carregam inteiras para o filtro local funcionar
                if (mode == MODE_CATEGORY && !done) loadMore()
            }.onFailure {
                empty.text = getString(R.string.no_connection_retry)
                empty.isVisible = true
                empty.setOnClickListener { empty.isVisible = false; loadMore() }
            }
        }
    }

    private fun render() {
        val shown = if (mode == MODE_CATEGORY && filter.isNotEmpty()) {
            hits.filter { it.title.contains(filter, ignoreCase = true) }
        } else hits
        adapter.items = shown.map { h ->
            if (h.ns == 14) {
                Row("📁  " + h.title.substringAfter(':'), "Categoria", { start(this, MODE_CATEGORY, h.title) })
            } else {
                Row(h.title, null, { ArticleActivity.start(this, h.title) })
            }
        }
        if (shown.isEmpty() && done) {
            empty.text = "Nada por aqui."
            empty.setOnClickListener(null)
            empty.isVisible = true
        }
    }
}
