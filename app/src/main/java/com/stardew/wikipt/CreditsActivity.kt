package com.stardew.wikipt

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar

/** Tela "Sobre e créditos": Eric Barone/ConcernedApe, contribuidores da wiki, licença e o app. */
class CreditsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_credits)
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.credits_title)
        val web = findViewById<WebView>(R.id.web)
        applyInsets(toolbar, web)

        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, request.url))
                } catch (_: ActivityNotFoundException) {
                    toast(getString(R.string.no_browser))
                }
                return true
            }
        }
        web.loadDataWithBaseURL(null, html(), "text/html", "utf-8", null)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish(); return true
    }

    private fun s(id: Int) = TextUtils.htmlEncode(getString(id))

    private fun html(): String {
        val dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val wiki = WikiApi.BASE
        fun link(url: String, label: Int) = "<p><a href='$url'>" + s(label) + " ›</a></p>"
        fun sec(h: Int, body: String) = "<div class='box'><h2>" + s(h) + "</h2>" + body + "</div>"
        return "<!DOCTYPE html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width, initial-scale=1'>" +
            "<style>" + Fonts.css(this) + CSS + "</style></head><body class='" + (if (dark) "dark" else "") + "'>" +
            "<div class='head'><div class='name'>" + s(R.string.app_name) + "</div><div class='tag'>" +
            s(R.string.unofficial_tag) + "</div><div class='ver'>" +
            TextUtils.htmlEncode(getString(R.string.credits_version_fmt, BuildConfig.VERSION_NAME)) + "</div></div>" +
            sec(R.string.credits_game_h, "<p>" + s(R.string.credits_game) + "</p>" +
                link("https://www.stardewvalley.net/", R.string.credits_game_h)) +
            sec(R.string.credits_wiki_h, "<p>" + s(R.string.credits_wiki) + "</p>" +
                link("$wiki/", R.string.credits_wiki_link) +
                link("$wiki/Special:ActiveUsers", R.string.credits_contrib_link)) +
            sec(R.string.credits_license_h, "<p>" + s(R.string.credits_license) + "</p>" +
                link("https://creativecommons.org/licenses/by-nc-sa/3.0/", R.string.credits_license_link)) +
            sec(R.string.credits_app_h, "<p>" + s(R.string.credits_app) + "</p>" +
                link("https://github.com/mmmartines/stardew-wiki-app", R.string.credits_source_link) +
                "<p class='small'>" + s(R.string.credits_font) + "</p>") +
            "</body></html>"
    }

    private companion object {
        const val CSS = """
:root{--bg:#f7e2b0;--fg:#3b2412;--muted:#7a5a35;--link:#a8410f;--card:#fff1cc;--wood:#9a5a24;--woodD:#5a2f0e;--woodL:#c27a3a}
body.dark{--bg:#1e1812;--fg:#f0e1c2;--muted:#b49a72;--link:#f0a560;--card:#2a2017;--wood:#6b3d17;--woodD:#3a1f0a;--woodL:#8a5428}
html,body{background:var(--bg);color:var(--fg);margin:0}
body{font-family:sans-serif;font-size:15px;line-height:1.55;padding:14px 14px 40px}
.head{text-align:center;padding:16px 10px;margin-bottom:14px;background:var(--wood);color:#ffe9b8;border:4px solid var(--woodD);border-radius:6px;box-shadow:inset 0 0 0 3px var(--woodL)}
.name{font-family:'Pixel',sans-serif;font-weight:700;font-size:26px;text-shadow:2px 2px 0 var(--woodD)}
.tag{font-size:14px;margin-top:2px}.ver{font-size:12px;opacity:.85;margin-top:6px}
.box{background:var(--card);border:3px solid var(--woodD);border-radius:6px;padding:4px 14px 8px;margin:12px 0;box-shadow:0 3px 0 rgba(0,0,0,.15)}
h2{font-family:'Pixel',sans-serif;font-size:19px;color:var(--wood);margin:10px 0 4px}
body.dark h2{color:#e7b77a}
a{color:var(--link);font-weight:700;text-decoration:none}
.small{font-size:12.5px;color:var(--muted)}
"""
    }
}
