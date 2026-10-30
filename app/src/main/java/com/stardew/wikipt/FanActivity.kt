package com.stardew.wikipt

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.text.TextUtils
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity

/**
 * Tela "feito por um fã, sem fins lucrativos". Aparece depois de escolher o idioma pela primeira vez
 * e pode ser reaberta pelo menu. Traduzida para os 12 idiomas da wiki.
 */
class FanActivity : AppCompatActivity() {

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val web = WebView(this)
        web.setBackgroundColor(androidx.core.content.ContextCompat.getColor(this, R.color.bg))
        setContentView(web)
        applyInsets(web, web)
        web.settings.javaScriptEnabled = true
        web.addJavascriptInterface(object {
            @JavascriptInterface
            fun done() = runOnUiThread { finish() }

            @JavascriptInterface
            fun credits() = runOnUiThread {
                startActivity(Intent(this@FanActivity, CreditsActivity::class.java))
                finish()
            }
        }, "Android")
        web.loadDataWithBaseURL(WikiApi.BASE + "/", html(), "text/html", "utf-8", null)
    }

    private fun s(id: Int) = TextUtils.htmlEncode(getString(id))

    private fun html(): String {
        val dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val img = "https://stardewvalleywiki.com/mediawiki/images/en/"
        val icons = if (BuildConfig.GAME_ICONS) {
            listOf("d/db/Parsnip.png", "a/a5/Stardrop.png", "8/83/Bouquet.png", "0/04/Sardine.png", "7/78/Copper_Ore.png")
                .joinToString("") { "<img src='$img$it' onerror=\"this.style.display='none'\">" }
        } else "🌱 ⭐ 💐 🐟 ⛏️"
        return "<!DOCTYPE html><html><head><meta charset='utf-8'>" +
            "<meta name='viewport' content='width=device-width, initial-scale=1, user-scalable=no'>" +
            "<style>" + Fonts.css(this) + CSS + "</style></head><body class='" + (if (dark) "dark" else "") + "'>" +
            "<div class='board'><div class='name'>" + s(R.string.app_name) + "</div>" +
            "<div class='title'>" + s(R.string.fan_title) + "</div></div>" +
            "<div class='icons'>" + icons + "</div>" +
            "<div class='box'>" +
            "<p>" + s(R.string.fan_p1) + "</p>" +
            "<p class='strong'>" + s(R.string.fan_p2) + "</p>" +
            "<p>" + s(R.string.fan_p3) + "</p>" +
            "<p class='small'>" + s(R.string.fan_p4) + "</p>" +
            "<p class='sign'>" + s(R.string.fan_signoff) + "</p>" +
            "</div>" +
            "<button class='go' onclick='Android.done()'>" + s(R.string.welcome_continue) + "</button>" +
            "<button class='link' onclick='Android.credits()'>" + s(R.string.welcome_credits) + "</button>" +
            "</body></html>"
    }

    private companion object {
        const val CSS = """
:root{--bg:#f7e2b0;--fg:#3b2412;--muted:#7a5a35;--card:#fff1cc;--card2:#f6dca4;--wood:#9a5a24;--woodD:#5a2f0e;--woodL:#c27a3a;--slot:#f0c477;--link:#a8410f}
body.dark{--bg:#1e1812;--fg:#f0e1c2;--muted:#b49a72;--card:#2a2017;--card2:#34281c;--wood:#6b3d17;--woodD:#3a1f0a;--woodL:#8a5428;--slot:#4a3624;--link:#f0a560}
*{box-sizing:border-box}
html,body{margin:0;background:var(--bg);color:var(--fg)}
body{font-family:sans-serif;font-size:16px;line-height:1.55;padding:18px 16px 28px;
 background-image:repeating-linear-gradient(0deg,rgba(0,0,0,.035) 0 2px,transparent 2px 6px)}
img{image-rendering:pixelated}
.board{text-align:center;padding:16px 12px 14px;background:var(--wood);color:#ffe9b8;border:4px solid var(--woodD);border-radius:6px;
 box-shadow:inset 0 0 0 3px var(--woodL),0 4px 0 rgba(0,0,0,.25)}
.name{font-family:'Pixel',sans-serif;font-weight:700;font-size:28px;text-shadow:2px 2px 0 var(--woodD)}
.title{font-family:'Pixel',sans-serif;font-size:17px;margin-top:6px;color:#ffd98a}
.icons{display:flex;justify-content:center;gap:10px;margin:16px 0 4px;font-size:26px}
.icons img{width:36px;height:36px;padding:4px;background:var(--slot);border:2px solid var(--woodL);border-radius:4px}
.box{background:var(--card);border:4px solid var(--woodD);border-radius:6px;padding:6px 16px 10px;margin:12px 0 18px;
 box-shadow:inset 0 0 0 3px var(--woodL),inset 0 0 0 6px var(--card2),0 4px 0 rgba(0,0,0,.2)}
p{margin:12px 0}
.strong{font-weight:700}
.small{font-size:14px;color:var(--muted)}
.sign{font-family:'Pixel',sans-serif;font-size:18px;text-align:center;margin-top:16px}
button{display:block;width:100%;font-family:'Pixel',sans-serif;cursor:pointer}
.go{padding:13px;font-size:20px;font-weight:700;color:#ffe9b8;background:var(--wood);border:4px solid var(--woodD);border-radius:6px;
 box-shadow:inset 0 0 0 2px var(--woodL),0 4px 0 rgba(0,0,0,.25);text-shadow:1px 1px 0 var(--woodD)}
.go:active{transform:translateY(2px);box-shadow:inset 0 0 0 2px var(--woodL),0 2px 0 rgba(0,0,0,.25)}
.link{margin-top:12px;padding:10px;font-size:16px;background:none;border:0;color:var(--link);text-decoration:underline}
"""
    }
}
