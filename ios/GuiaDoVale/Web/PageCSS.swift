import Foundation

// CSS copiado do app Android (Home.kt, CreditsActivity.kt, FanActivity.kt) para as telas ficarem idênticas.
enum PageCSS {
    static let home = #"""
:root{
 --sky1:#62b6f0;--sky2:#bfe7ff;--grass:#5fa834;--grass2:#4a8a26;--grassTop:#86c94e;
 --wood:#9a5a24;--woodL:#c27a3a;--woodD:#5a2f0e;--ink:#3b2412;
 --paper:#ffe9b8;--paper2:#f4cf86;--slot:#f0c477;--slotD:#c98f45;--bg:#e9c88a;--muted:#7a5a35}
body.dark{
 --sky1:#0e1a3a;--sky2:#2b3f73;--grass:#2f5c1f;--grass2:#234816;--grassTop:#3f7429;
 --wood:#6b3d17;--woodL:#8a5428;--woodD:#3a1f0a;--ink:#f3e3c3;
 --paper:#3a2c1e;--paper2:#2d2116;--slot:#4a3624;--slotD:#2a1d12;--bg:#1e1812;--muted:#b49a72}
*{box-sizing:border-box;-webkit-tap-highlight-color:transparent}
html,body{margin:0;background:var(--bg);color:var(--ink);overflow-x:hidden;max-width:100%}
body{font-family:'Pixel',sans-serif;padding:0 10px 30px;max-width:900px;margin:0 auto;
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
.sign{position:absolute;left:50%;transform:translateX(-50%);bottom:22px;width:230px;max-width:calc(100% - 24px);height:110px}
.post{position:absolute;bottom:0;width:10px;height:60px;background:var(--woodD)}
.p1{left:40px}.p2{right:40px}
.board{position:absolute;left:0;right:0;top:0;padding:12px 8px 10px;text-align:center;
 background:var(--wood);border:4px solid var(--woodD);border-radius:4px;
 box-shadow:inset 0 0 0 3px var(--woodL), 0 4px 0 rgba(0,0,0,.25);
 background-image:repeating-linear-gradient(0deg,rgba(0,0,0,.08) 0 2px,transparent 2px 14px)}
.t1{font-size:28px;white-space:nowrap;font-weight:700;color:#ffe9b8;text-shadow:2px 2px 0 var(--woodD),-1px -1px 0 #3a1a05}
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
/* colunas pela largura do texto: com fonte do sistema grande, cabem menos por linha em vez de quebrar palavras */
.grid{display:flex;flex-wrap:wrap;display:grid;grid-template-columns:repeat(auto-fill,minmax(6.1em,1fr));gap:4px;padding:2px;font-size:12.5px}
@supports not (display:grid){.cell{width:25%}}
.cell{text-align:center;padding:4px 1px 2px;border-radius:4px;min-width:0}
.cell:active{background:rgba(0,0,0,.08)}
.slot{width:54px;height:54px;max-width:100%;margin:0 auto 4px;background:var(--slot);
 border:3px solid var(--slotD);border-radius:3px;display:flex;align-items:center;justify-content:center;
 box-shadow:inset 3px 3px 0 rgba(0,0,0,.12),inset -2px -2px 0 rgba(255,255,255,.25)}
.cell:active .slot{border-color:#e05a2a}
.slot img{width:36px;height:36px;object-fit:contain}
.emo{font-size:28px;line-height:1}
.label{font-size:1em;line-height:1.15;min-height:30px;display:flex;align-items:flex-start;justify-content:center;
 overflow-wrap:break-word;hyphens:manual;-webkit-hyphens:manual}
/* telas estreitas ou fonte do sistema grande */
@media (max-width:380px){.box{padding:6px}.slot{width:48px;height:48px}.slot img{width:32px;height:32px}.t1{font-size:24px}.grid{font-size:12px}}
.chips{display:flex;flex-wrap:wrap;gap:6px}
.chip{max-width:100%;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.chip{background:var(--paper);border:3px solid var(--woodD);border-radius:3px;padding:4px 10px;font-size:14px;
 box-shadow:0 2px 0 var(--woodD)}
.srcs{display:flex;flex-wrap:wrap;gap:6px;margin:10px 2px 2px}
.src{flex:0 1 auto;max-width:100%;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:14px;padding:6px 10px;
 background:var(--paper);border:3px solid var(--woodD);border-radius:3px;box-shadow:0 2px 0 var(--woodD);min-height:36px;
 display:flex;align-items:center}
.src.on{background:var(--wood);color:#ffe9b8;box-shadow:inset 0 0 0 2px var(--woodL),0 2px 0 var(--woodD);text-shadow:1px 1px 0 var(--woodD)}
.src:active{transform:translateY(1px)}
.modinfo{margin:10px 2px 0;padding:8px 10px;font-family:sans-serif;font-size:13px;line-height:1.35;color:var(--ink);
 background:var(--paper2);border:2px dashed var(--woodD);border-radius:4px}
.foot{font-family:sans-serif;font-size:11px;color:var(--muted);text-align:center;margin-top:26px}
"""#

    static let credits = #"""
:root{--bg:#f7e2b0;--fg:#3b2412;--muted:#7a5a35;--link:#a8410f;--card:#fff1cc;--wood:#9a5a24;--woodD:#5a2f0e;--woodL:#c27a3a}
body.dark{--bg:#1e1812;--fg:#f0e1c2;--muted:#b49a72;--link:#f0a560;--card:#2a2017;--wood:#6b3d17;--woodD:#3a1f0a;--woodL:#8a5428}
html,body{background:var(--bg);color:var(--fg);margin:0;overflow-x:hidden}
body{font-family:sans-serif;font-size:15px;line-height:1.55;padding:14px 14px 40px;overflow-wrap:anywhere;max-width:760px;margin:0 auto}
@media (max-width:340px){body{padding:10px 10px 30px}.name{font-size:22px}.box{padding:4px 10px 8px}}
.head{text-align:center;padding:16px 10px;margin-bottom:14px;background:var(--wood);color:#ffe9b8;border:4px solid var(--woodD);border-radius:6px;box-shadow:inset 0 0 0 3px var(--woodL)}
.name{font-family:'Pixel',sans-serif;font-weight:700;font-size:26px;text-shadow:2px 2px 0 var(--woodD)}
.tag{font-size:14px;margin-top:2px}.ver{font-size:12px;opacity:.85;margin-top:6px}
.box{background:var(--card);border:3px solid var(--woodD);border-radius:6px;padding:4px 14px 8px;margin:12px 0;box-shadow:0 3px 0 rgba(0,0,0,.15)}
h2{font-family:'Pixel',sans-serif;font-size:19px;color:var(--wood);margin:10px 0 4px}
body.dark h2{color:#e7b77a}
a{color:var(--link);font-weight:700;text-decoration:none}
.small{font-size:12.5px;color:var(--muted)}
"""#

    static let fan = #"""
:root{--bg:#f7e2b0;--fg:#3b2412;--muted:#7a5a35;--card:#fff1cc;--card2:#f6dca4;--wood:#9a5a24;--woodD:#5a2f0e;--woodL:#c27a3a;--slot:#f0c477;--link:#a8410f}
body.dark{--bg:#1e1812;--fg:#f0e1c2;--muted:#b49a72;--card:#2a2017;--card2:#34281c;--wood:#6b3d17;--woodD:#3a1f0a;--woodL:#8a5428;--slot:#4a3624;--link:#f0a560}
*{box-sizing:border-box}
html,body{margin:0;background:var(--bg);color:var(--fg);height:100%}
body{font-family:sans-serif;font-size:16px;line-height:1.55;padding:18px 16px 16px;max-width:640px;margin:0 auto;
 height:100vh;display:flex;flex-direction:column;overflow:hidden;
 background-image:repeating-linear-gradient(0deg,rgba(0,0,0,.035) 0 2px,transparent 2px 6px)}
img{image-rendering:pixelated}
.board{text-align:center;padding:16px 12px 14px;background:var(--wood);color:#ffe9b8;border:4px solid var(--woodD);border-radius:6px;
 box-shadow:inset 0 0 0 3px var(--woodL),0 4px 0 rgba(0,0,0,.25)}
.name{font-family:'Pixel',sans-serif;font-weight:700;font-size:28px;text-shadow:2px 2px 0 var(--woodD)}
.title{font-family:'Pixel',sans-serif;font-size:17px;margin-top:6px;color:#ffd98a}
.icons{display:flex;flex-wrap:wrap;justify-content:center;gap:8px;margin:16px 0 4px;font-size:26px}
.icons img{width:36px;height:36px;padding:4px;background:var(--slot);border:2px solid var(--woodL);border-radius:4px}
.box{flex:1 1 auto;min-height:0;overflow-y:auto;-webkit-overflow-scrolling:touch;display:flex;flex-direction:column;
 background:var(--card);border:4px solid var(--woodD);border-radius:6px;padding:6px 16px 10px;margin:12px 0 16px;
 box-shadow:inset 0 0 0 3px var(--woodL),inset 0 0 0 6px var(--card2),0 4px 0 rgba(0,0,0,.2)}
p{margin:12px 0;overflow-wrap:anywhere}
.strong{font-weight:700}
.small{font-size:14px;color:var(--muted)}
.sign{font-family:'Pixel',sans-serif;font-size:18px;text-align:center;margin-top:16px}
button{display:block;width:100%;font-family:'Pixel',sans-serif;cursor:pointer}
.go{padding:13px;font-size:20px;font-weight:700;color:#ffe9b8;background:var(--wood);border:4px solid var(--woodD);border-radius:6px;
 box-shadow:inset 0 0 0 2px var(--woodL),0 4px 0 rgba(0,0,0,.25);text-shadow:1px 1px 0 var(--woodD)}
.go:active{transform:translateY(2px);box-shadow:inset 0 0 0 2px var(--woodL),0 2px 0 rgba(0,0,0,.25)}
.inner{margin:auto 0}
.board,.icons,.actions{flex:0 0 auto}
@media (max-width:340px){.name{font-size:22px}.title{font-size:15px}.icons img{width:30px;height:30px}body{padding:12px 10px}}
@media (max-height:700px){.icons{margin:10px 0 0}.board{padding:10px}.name{font-size:24px}p{margin:9px 0}}
.link{margin-top:12px;padding:10px;font-size:16px;background:none;border:0;color:var(--link);text-decoration:underline}
"""#
}
