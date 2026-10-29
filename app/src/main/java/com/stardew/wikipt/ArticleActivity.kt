package com.stardew.wikipt

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import org.json.JSONObject

class ArticleActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_ANCHOR = "anchor"
        private const val WEEK = 7L * 24 * 3600 * 1000

        fun start(ctx: Context, title: String, anchor: String? = null) {
            ctx.startActivity(
                Intent(ctx, ArticleActivity::class.java)
                    .putExtra(EXTRA_TITLE, title)
                    .putExtra(EXTRA_ANCHOR, anchor)
            )
        }

        /** Converte um link da wiki no título da página (ou null se não for página). */
        fun titleFromUri(uri: Uri?): String? {
            if (uri == null) return null
            val path = uri.path ?: return null
            val raw = if (path.startsWith("/mediawiki/index.php") || path == "/index.php") {
                uri.getQueryParameter("title")
            } else {
                path.removePrefix("/")
            }
            if (raw.isNullOrBlank() || raw.startsWith("mediawiki/")) return null
            return raw.replace('_', ' ')
        }

        private fun hasNs(t: String, ns: List<String>) =
            ns.any { t.startsWith("$it:", ignoreCase = true) }

        // nomes dos espaços de nomes nas 12 wikis
        private val NS_CATEGORY = listOf("Category", "Categoria", "Kategorie", "Catégorie", "Categoría",
            "Kategória", "Kategori", "Категория", "カテゴリ", "분류", "分类")
        private val NS_FILE = listOf("File", "Image", "Arquivo", "Ficheiro", "Imagem", "Datei", "Bild", "Fichier",
            "Archivo", "Fájl", "Dosya", "Файл", "ファイル", "画像", "파일", "文件")
        private val NS_EXTERNAL = listOf("Special", "Especial", "Spezial", "Spécial", "Speciale", "Speciális", "Özel",
            "Служебная", "特別", "특수", "特殊", "User", "Usuário", "Utilizador", "Benutzer", "Benutzerin", "Utilisateur",
            "Usuario", "Utente", "Szerkesztő", "Kullanıcı", "Участник", "利用者", "사용자", "用户", "Template",
            "Predefinição", "Vorlage", "Modèle", "Plantilla", "Sablon", "Şablon", "Шаблон", "テンプレート", "틀", "模板",
            "Talk", "Discussão", "Diskussion", "Discussion", "Discusión", "Discussione", "Vita", "Tartışma", "Обсуждение",
            "ノート", "토론", "讨论", "MediaWiki", "Module", "Módulo", "Modul", "Модуль")
    }

    private lateinit var store: Store
    private lateinit var web: WebView
    private lateinit var progress: View
    private lateinit var errorBox: View
    private lateinit var errorText: TextView

    private lateinit var requested: String
    private var current = ""
    private var page: Page? = null
    private var triedFallback = false
    private var pendingAnchor: String? = null
    private var findSnack: Snackbar? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_article)
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        applyInsets(toolbar, findViewById(R.id.content))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_home)

        store = Store(this)
        WebBridge.init(this)
        web = findViewById(R.id.web)
        progress = findViewById(R.id.progress)
        errorBox = findViewById(R.id.error_box)
        errorText = findViewById(R.id.error_text)
        findViewById<View>(R.id.retry).setOnClickListener { load(force = true) }

        requested = intent.getStringExtra(EXTRA_TITLE) ?: "Stardew Valley Wiki"
        current = requested
        pendingAnchor = intent.getStringExtra(EXTRA_ANCHOR)
        supportActionBar?.title = current

        setupWeb()
        load(force = false)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWeb() {
        web.settings.apply {
            javaScriptEnabled = true
            builtInZoomControls = true
            displayZoomControls = false
            textZoom = store.textZoom
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                handleLink(request.url)

            override fun onPageFinished(view: WebView, url: String?) {
                pendingAnchor?.let { scrollToAnchor(it) }
                pendingAnchor = null
            }
        }
        web.setFindListener { active, count, done ->
            if (done) findSnack?.setText(if (count == 0) getString(R.string.nothing_found) else getString(R.string.find_count_fmt, active + 1, count))
        }
    }

    // ---------- Carregamento ----------

    private fun load(force: Boolean) {
        val cached = store.getPage(current)
        val fresh = cached != null && System.currentTimeMillis() - cached.time < WEEK
        if (!force && cached != null && fresh) {
            show(cached.page)
            return
        }
        showLoading()
        val title = current
        runAsync({ WikiApi.parse(title) }) { r ->
            r.onSuccess { p ->
                store.putPage(requested, p)
                show(p)
                if (force) toast(getString(R.string.page_updated))
            }.onFailure { e ->
                when {
                    e is MissingPageException && !triedFallback -> fallback()
                    cached != null -> {
                        show(cached.page)
                        toast(getString(R.string.offline_showing_saved))
                    }
                    e is MissingPageException -> showError(getString(R.string.page_not_found_fmt, title))
                    else -> showError(getString(R.string.load_error) + "\n\n" + getString(R.string.details_fmt, e.message ?: e.toString()))
                }
            }
        }
    }

    /** Título não existe com esse nome: abre o melhor resultado da busca. */
    private fun fallback() {
        triedFallback = true
        val q = current
        runAsync({ WikiApi.search(q) }) { r ->
            val first = r.getOrNull()?.firstOrNull()
            if (first == null) {
                showError(getString(R.string.page_not_found_fmt, q))
            } else {
                current = first.title
                supportActionBar?.title = current
                load(force = false)
            }
        }
    }

    private fun showLoading() {
        progress.isVisible = true
        errorBox.isVisible = false
    }

    private fun showError(msg: String) {
        progress.isVisible = false
        web.isVisible = false
        errorText.text = msg
        errorBox.isVisible = true
    }

    private fun show(p: Page) {
        page = p
        current = p.title
        supportActionBar?.title = p.title
        progress.isVisible = false
        errorBox.isVisible = false
        web.isVisible = true
        store.addHistory(p.title)
        web.loadDataWithBaseURL(WikiApi.pageUrl(p.title), buildHtml(p), "text/html", "utf-8", null)
        invalidateOptionsMenu()
    }

    private fun isDark(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    private fun buildHtml(p: Page): String {
        val bodyClass = if (isDark()) "dark" else ""
        return "<!DOCTYPE html><html><head><meta charset=\"utf-8\">" +
            "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" +
            "<style>" + Fonts.css(this) + Assets.text(this, "article.css") + "</style></head><body class=\"" + bodyClass + "\">" +
            "<h1 class=\"page-title\">" + TextUtils.htmlEncode(p.title) + "</h1>" +
            p.html + footer(p) +
            "<script>window.__t=" + jsStrings() + ";</script>" +
            "<script>" + Assets.text(this, "article.js") + "</script></body></html>"
    }

    /** Crédito em toda página: contribuidores, licença, link para o original e para o histórico de autores. */
    private fun footer(p: Page): String {
        fun e(id: Int) = TextUtils.htmlEncode(getString(id))
        val orig = TextUtils.htmlEncode(WikiApi.pageUrl(p.title) + "?app_ext=1")
        val hist = TextUtils.htmlEncode(WikiApi.historyUrl(p.title))
        return "<div class='credit'><p>" + e(R.string.article_footer) + "</p><p>" + e(R.string.article_footer_art) +
            "</p><p><a class='ext' href='" + orig + "'>" + e(R.string.view_original) + " ›</a> · " +
            "<a class='ext' href='" + hist + "'>" + e(R.string.page_authors) + " ›</a> · " +
            "<a class='ext' href='https://creativecommons.org/licenses/by-nc-sa/3.0/'>CC BY-NC-SA 3.0 ›</a></p></div>"
    }

    private fun jsStrings(): String = JSONObject()
        .put("more", getString(R.string.js_more_details))
        .put("showAll", getString(R.string.js_show_all))
        .put("filter", getString(R.string.js_filter))
        .put("onPage", getString(R.string.js_on_this_page))
        .put("readIntro", getString(R.string.js_read_intro))
        .put("seeAlso", getString(R.string.js_see_also))
        .put("showTable", getString(R.string.js_show_table))
        .put("show", getString(R.string.js_show))
        .toString()

    // ---------- Links ----------

    private fun handleLink(uri: Uri): Boolean {
        val scheme = uri.scheme ?: return true
        if (scheme != "http" && scheme != "https") return true
        val host = uri.host?.removePrefix("www.")
        if (uri.getQueryParameter("app_ext") != null) {
            openExternal(Uri.parse(uri.toString().replace("?app_ext=1", ""))); return true
        }
        if (host != Lang.host() || uri.getQueryParameter("action") == "history") {
            openExternal(uri); return true
        }
        if (uri.getQueryParameter("redlink") != null) {
            toast(getString(R.string.page_missing_on_wiki)); return true
        }
        val action = uri.getQueryParameter("action")
        if (action == "edit" || action == "submit" || uri.getQueryParameter("veaction") != null) {
            toast(getString(R.string.read_only)); return true // o app nunca edita a wiki
        }
        val t = titleFromUri(uri)
        val frag = uri.fragment
        if (t == null || t.equals(current, ignoreCase = true)) {
            if (frag != null) scrollToAnchor(frag)
            return true
        }
        when {
            hasNs(t, NS_CATEGORY) -> ListActivity.start(this, ListActivity.MODE_CATEGORY, t)
            hasNs(t, NS_FILE) -> { /* imagem: ignora */ }
            hasNs(t, NS_EXTERNAL) -> openExternal(uri)
            else -> start(this, t, frag)
        }
        return true
    }

    private fun scrollToAnchor(anchor: String) {
        val js = "window.__goto && window.__goto(" + JSONObject.quote(anchor) + ")"
        web.evaluateJavascript(js, null)
    }

    private fun openExternal(uri: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: ActivityNotFoundException) {
            toast(getString(R.string.no_browser))
        }
    }

    // ---------- Menu ----------

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.article_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val loaded = page != null
        menu.findItem(R.id.action_fav)?.apply {
            isEnabled = loaded
            val fav = loaded && store.isFavorite(current)
            setIcon(if (fav) R.drawable.ic_star else R.drawable.ic_star_border)
            title = getString(if (fav) R.string.fav_remove else R.string.fav_add)
        }
        menu.findItem(R.id.action_sections)?.isEnabled = !page?.sections.isNullOrEmpty()
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onSupportNavigateUp(): Boolean {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val p = page
        when (item.itemId) {
            R.id.action_fav -> if (p != null) {
                val now = store.toggleFavorite(p.title)
                toast(getString(if (now) R.string.fav_added else R.string.fav_removed))
                invalidateOptionsMenu()
            }
            R.id.action_sections -> showSections()
            R.id.action_find -> askFind()
            R.id.action_refresh -> load(force = true)
            R.id.action_text -> chooseTextSize()
            R.id.action_share -> if (p != null) {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, p.title + " — " + WikiApi.pageUrl(p.title))
                startActivity(Intent.createChooser(send, getString(R.string.share)))
            }
            R.id.action_browser -> openExternal(Uri.parse(WikiApi.pageUrl(current)))
            R.id.action_authors -> openExternal(Uri.parse(WikiApi.historyUrl(current)))
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun showSections() {
        val secs = page?.sections.orEmpty()
        if (secs.isEmpty()) return
        val labels = secs.map { "    ".repeat((it.level - 1).coerceAtLeast(0)) + it.line }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.sections)
            .setItems(labels) { _, i -> scrollToAnchor(secs[i].anchor) }
            .show()
    }

    private fun askFind() {
        val input = EditText(this).apply {
            hint = getString(R.string.find_hint)
            isSingleLine = true
        }
        val pad = (20 * resources.displayMetrics.density).toInt()
        val box = FrameLayout(this).apply {
            setPadding(pad, pad / 2, pad, 0)
            addView(input)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.find_in_page)
            .setView(box)
            .setPositiveButton(R.string.find_go) { _, _ ->
                val q = input.text.toString().trim()
                if (q.isNotEmpty()) {
                    web.findAllAsync(q)
                    showFindSnack(getString(R.string.searching))
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
        input.requestFocus()
    }

    private fun showFindSnack(text: String) {
        val s = Snackbar.make(web, text, Snackbar.LENGTH_INDEFINITE)
            .setAction(getString(R.string.next)) {
                web.findNext(true)
                showFindSnack("…")
            }
        findSnack = s
        s.show()
    }

    private fun chooseTextSize() {
        val values = intArrayOf(85, 100, 115, 135)
        val labels = arrayOf(getString(R.string.size_small), getString(R.string.size_normal),
            getString(R.string.size_large), getString(R.string.size_xlarge))
        val cur = values.indexOf(store.textZoom).coerceAtLeast(0)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.text_size)
            .setSingleChoiceItems(labels, cur) { d, i ->
                store.textZoom = values[i]
                web.settings.textZoom = values[i]
                d.dismiss()
            }
            .show()
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }
}
