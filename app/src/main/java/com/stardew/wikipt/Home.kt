package com.stardew.wikipt

import android.text.TextUtils

/** HTML da tela inicial: grade de seções com os ícones do jogo vindos da wiki. */
object Home {
    fun html(dark: Boolean, recent: List<String>): String {
        val sb = StringBuilder()
        sb.append("<!DOCTYPE html><html><head><meta charset='utf-8'>")
        sb.append("<meta name='viewport' content='width=device-width, initial-scale=1, user-scalable=no'>")
        sb.append("<style>").append(CSS).append("</style></head><body class='").append(if (dark) "dark" else "").append("'>")

        sb.append("<div class='hero'><div class='hero-row'>")
        sb.append("<img class='hero-icon' src='https://stardewvalleywiki.com/mediawiki/images/en/a/a5/Stardrop.png'>")
        sb.append("<div><div class='hero-title'>Stardew Wiki</div><div class='hero-sub'>Guia rápido em português</div></div></div>")
        sb.append("<div class='search' onclick='Android.search()'>")
        sb.append("<span class='lupa'>&#128269;</span> Pesquisar item, aldeão, peixe, receita…</div></div>")

        if (recent.isNotEmpty()) {
            sb.append("<h2>Vistos recentemente</h2><div class='chips'>")
            recent.forEachIndexed { i, t ->
                sb.append("<span class='chip' onclick='Android.recent($i)'>").append(TextUtils.htmlEncode(t)).append("</span>")
            }
            sb.append("</div>")
        }

        var idx = 0
        for (g in Categories.groups) {
            sb.append("<h2>").append(TextUtils.htmlEncode(g.name)).append("</h2><div class='grid'>")
            for (c in g.items) {
                sb.append("<div class='card' onclick='Android.open(").append(idx).append(")'>")
                sb.append("<div class='slot'><img src='").append(c.icon)
                    .append("' loading='lazy' onerror=\"this.style.visibility='hidden'\"></div>")
                sb.append("<div class='label'>").append(TextUtils.htmlEncode(c.label)).append("</div></div>")
                idx++
            }
            sb.append("</div>")
        }
        sb.append("<p class='foot'>Conteúdo e imagens: Stardew Valley Wiki (comunidade), CC BY-NC-SA 3.0.<br>")
        sb.append("Stardew Valley © ConcernedApe. App não oficial.</p>")
        sb.append("</body></html>")
        return sb.toString()
    }

    private const val CSS = """
:root{--bg:#fbf5e6;--fg:#3b2a1a;--card:#fff8e8;--edge:#c9a46b;--slot:#f3e2b8;--h:#6b4a26;--muted:#8a7357}
body.dark{--bg:#17130e;--fg:#efe3cc;--card:#2a2117;--edge:#6e5531;--slot:#3a2d1e;--h:#d8b47a;--muted:#a8936f}
*{box-sizing:border-box;-webkit-tap-highlight-color:transparent}
html,body{margin:0;background:var(--bg);color:var(--fg);font-family:sans-serif}
body{padding:0 12px 28px}
img{image-rendering:pixelated}
.hero{margin:0 -12px 6px;padding:18px 16px 16px;background:linear-gradient(160deg,#5aa03a,#2f6b1c);color:#fff;border-bottom:4px solid #24510f}
.hero-row{display:flex;align-items:center;gap:12px}
.hero-icon{width:44px;height:44px}
.hero-title{font-size:22px;font-weight:bold;text-shadow:0 2px 0 rgba(0,0,0,.25)}
.hero-sub{font-size:13px;opacity:.9}
.search{margin-top:14px;background:#fff8e8;color:#7a6446;border:3px solid #8b5a2b;border-radius:10px;padding:11px 12px;font-size:15px;box-shadow:0 3px 0 rgba(0,0,0,.2)}
.lupa{margin-right:4px}
h2{font-size:15px;color:var(--h);margin:20px 2px 10px;text-transform:uppercase;letter-spacing:.5px}
.grid{display:grid;grid-template-columns:repeat(4,1fr);gap:8px}
@media (max-width:330px){.grid{grid-template-columns:repeat(3,1fr)}}
.card{background:var(--card);border:2px solid var(--edge);border-radius:10px;padding:8px 4px 6px;text-align:center;box-shadow:0 3px 0 var(--edge)}
.card:active{transform:translateY(2px);box-shadow:0 1px 0 var(--edge)}
.slot{width:48px;height:48px;margin:0 auto 5px;background:var(--slot);border-radius:8px;display:flex;align-items:center;justify-content:center}
.slot img{width:36px;height:36px;object-fit:contain}
.label{font-size:11.5px;line-height:1.2;min-height:28px;display:flex;align-items:center;justify-content:center}
.chips{display:flex;flex-wrap:wrap;gap:6px}
.chip{background:var(--card);border:2px solid var(--edge);border-radius:16px;padding:5px 11px;font-size:13px}
.foot{font-size:11px;color:var(--muted);text-align:center;margin-top:26px}
"""
}
