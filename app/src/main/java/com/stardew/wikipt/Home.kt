package com.stardew.wikipt

import android.content.Context
import android.text.TextUtils

/** HTML da tela inicial, com tema de fazenda: céu, grama, madeira e pergaminho. */
object Home {
    private fun e(s: String) = TextUtils.htmlEncode(s)

    fun html(ctx: Context, fontCss: String, dark: Boolean, recent: List<String>): String {
        val sb = StringBuilder()
        sb.append("<!DOCTYPE html><html><head><meta charset='utf-8'>")
        sb.append("<meta name='viewport' content='width=device-width, initial-scale=1, user-scalable=no'>")
        sb.append("<style>").append(fontCss).append(CSS).append("</style></head><body class='")
            .append(if (dark) "dark" else "").append("'>")

        // Cena do topo
        sb.append("<div class='scene'>")
        sb.append("<div class='sun'></div><div class='cloud c1'></div><div class='cloud c2'></div><div class='cloud c3'></div>")
        sb.append("<div class='star s1'></div><div class='star s2'></div><div class='star s3'></div><div class='star s4'></div>")
        sb.append("<div class='sign'><div class='post p1'></div><div class='post p2'></div>")
        sb.append("<div class='board'><div class='t1'>").append(e(ctx.getString(R.string.home_title)))
            .append("</div><div class='t2'>").append(e(ctx.getString(R.string.home_tagline))).append("</div></div></div>")
        sb.append("<div class='grass'></div></div>")

        sb.append("<div class='search' onclick='Android.search()'><span class='lupa'></span>")
        sb.append(e(ctx.getString(R.string.home_search))).append("</div>")

        if (recent.isNotEmpty()) {
            sb.append("<div class='plank'>").append(e(ctx.getString(R.string.home_recent))).append("</div><div class='chips'>")
            recent.forEachIndexed { i, t ->
                sb.append("<span class='chip' onclick='Android.recent($i)'>").append(e(t)).append("</span>")
            }
            sb.append("</div>")
        }

        var idx = 0
        for (g in Categories.groups) {
            sb.append("<div class='plank'>").append(e(ctx.getString(g.nameRes))).append("</div><div class='box'><div class='grid'>")
            for (c in g.items) {
                sb.append("<div class='cell' onclick='Android.open(").append(idx).append(")'>")
                if (BuildConfig.GAME_ICONS) {
                    sb.append("<div class='slot'><img src='").append(c.icon)
                        .append("' loading='lazy' onerror=\"this.style.visibility='hidden'\"></div>")
                } else {
                    sb.append("<div class='slot'><span class='emo'>").append(c.emoji).append("</span></div>")
                }
                sb.append("<div class='label'>").append(e(c.label(ctx))).append("</div></div>")
                idx++
            }
            sb.append("</div></div>")
        }
        sb.append("<p class='foot' onclick='Android.credits()'>").append(e(ctx.getString(R.string.home_footer)))
            .append("<br><u>").append(e(ctx.getString(R.string.about))).append("</u></p>")
        sb.append("</body></html>")
        return sb.toString()
    }

    private const val CSS = """
:root{
 --sky1:#62b6f0;--sky2:#bfe7ff;--grass:#5fa834;--grass2:#4a8a26;--grassTop:#86c94e;
 --wood:#9a5a24;--woodL:#c27a3a;--woodD:#5a2f0e;--ink:#3b2412;
 --paper:#ffe9b8;--paper2:#f4cf86;--slot:#f0c477;--slotD:#c98f45;--bg:#e9c88a;--muted:#7a5a35}
body.dark{
 --sky1:#0e1a3a;--sky2:#2b3f73;--grass:#2f5c1f;--grass2:#234816;--grassTop:#3f7429;
 --wood:#6b3d17;--woodL:#8a5428;--woodD:#3a1f0a;--ink:#f3e3c3;
 --paper:#3a2c1e;--paper2:#2d2116;--slot:#4a3624;--slotD:#2a1d12;--bg:#1e1812;--muted:#b49a72}
*{box-sizing:border-box;-webkit-tap-highlight-color:transparent}
html,body{margin:0;background:var(--bg);color:var(--ink)}
body{font-family:'Pixel',sans-serif;padding:0 10px 30px;
 background-image:repeating-linear-gradient(0deg,rgba(0,0,0,.035) 0 2px,transparent 2px 6px)}
img{image-rendering:pixelated}

.scene{position:relative;height:178px;margin:0 -10px;overflow:hidden;
 background:linear-gradient(var(--sky1),var(--sky2))}
.sun{position:absolute;right:26px;top:18px;width:30px;height:30px;background:#ffd34d;
 box-shadow:0 0 0 4px #ffe68a}
body.dark .sun{background:#f2f0d8;box-shadow:0 0 0 4px #c9c7ad;width:24px;height:24px}
.cloud{position:absolute;width:56px;height:14px;background:#fff;
 box-shadow:8px -8px 0 #fff,24px -10px 0 #fff,40px -6px 0 #fff, 0 4px 0 #dbeefa}
.c1{left:18px;top:40px}.c2{left:150px;top:24px;transform:scale(.8)}.c3{right:90px;top:70px;transform:scale(.6)}
body.dark .cloud{opacity:.12}
.star{position:absolute;width:3px;height:3px;background:#fff;display:none}
body.dark .star{display:block}
.s1{left:30px;top:30px}.s2{left:120px;top:60px}.s3{left:220px;top:22px}.s4{right:70px;top:90px}
.grass{position:absolute;left:0;right:0;bottom:0;height:34px;background:var(--grass);
 border-top:6px solid var(--grassTop);
 box-shadow:inset 0 -12px 0 var(--grass2)}
.grass:before{content:'';position:absolute;left:0;right:0;top:-12px;height:8px;
 background:repeating-linear-gradient(90deg,var(--grassTop) 0 6px,transparent 6px 14px)}
.sign{position:absolute;left:50%;transform:translateX(-50%);bottom:22px;width:230px;height:110px}
.post{position:absolute;bottom:0;width:10px;height:60px;background:var(--woodD)}
.p1{left:40px}.p2{right:40px}
.board{position:absolute;left:0;right:0;top:0;padding:12px 8px 10px;text-align:center;
 background:var(--wood);border:4px solid var(--woodD);border-radius:4px;
 box-shadow:inset 0 0 0 3px var(--woodL), 0 4px 0 rgba(0,0,0,.25);
 background-image:repeating-linear-gradient(0deg,rgba(0,0,0,.08) 0 2px,transparent 2px 14px)}
.t1{font-size:28px;font-weight:700;color:#ffe9b8;text-shadow:2px 2px 0 var(--woodD),-1px -1px 0 #3a1a05}
.t2{font-size:14px;color:#ffd98a;margin-top:2px;text-shadow:1px 1px 0 var(--woodD)}

.search{margin:14px 2px 6px;padding:11px 12px;font-size:16px;color:var(--muted);
 background:var(--paper);border:3px solid var(--woodD);border-radius:4px;
 box-shadow:inset 0 0 0 2px var(--paper2),0 3px 0 var(--woodD)}
.lupa{display:inline-block;width:12px;height:12px;border:3px solid var(--wood);border-radius:50%;
 margin-right:10px;position:relative;top:1px}
.lupa:after{content:'';position:absolute;width:3px;height:7px;background:var(--wood);right:-6px;bottom:-7px;transform:rotate(-45deg)}

.plank{display:inline-block;margin:20px 0 8px;padding:5px 14px 6px;font-size:16px;font-weight:700;
 color:#ffe9b8;background:var(--wood);border:3px solid var(--woodD);border-radius:3px;
 box-shadow:inset 0 0 0 2px var(--woodL),0 3px 0 rgba(0,0,0,.2);text-shadow:1px 1px 0 var(--woodD)}
.box{background:var(--paper);border:4px solid var(--woodD);border-radius:6px;padding:8px;
 box-shadow:inset 0 0 0 3px var(--woodL),inset 0 0 0 6px var(--paper2),0 4px 0 rgba(0,0,0,.2)}
.grid{display:flex;flex-wrap:wrap;display:grid;grid-template-columns:repeat(4,1fr);gap:6px;padding:4px}
@supports not (display:grid){.cell{width:25%}}
@media (max-width:330px){.grid{grid-template-columns:repeat(3,1fr)}}
.cell{text-align:center;padding:4px 2px 2px;border-radius:4px}
.cell:active{background:rgba(0,0,0,.08)}
.slot{width:54px;height:54px;margin:0 auto 4px;background:var(--slot);
 border:3px solid var(--slotD);border-radius:3px;display:flex;align-items:center;justify-content:center;
 box-shadow:inset 3px 3px 0 rgba(0,0,0,.12),inset -2px -2px 0 rgba(255,255,255,.25)}
.cell:active .slot{border-color:#e05a2a}
.slot img{width:36px;height:36px;object-fit:contain}
.emo{font-size:28px;line-height:1}
.label{font-size:13px;line-height:1.15;min-height:30px;display:flex;align-items:flex-start;justify-content:center}
.chips{display:flex;flex-wrap:wrap;gap:6px}
.chip{background:var(--paper);border:3px solid var(--woodD);border-radius:3px;padding:4px 10px;font-size:14px;
 box-shadow:0 2px 0 var(--woodD)}
.foot{font-family:sans-serif;font-size:11px;color:var(--muted);text-align:center;margin-top:26px}
"""
}
