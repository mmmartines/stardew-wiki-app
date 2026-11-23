#!/usr/bin/env python3
"""Gera android/app/src/main/assets/terms.json: termos da wiki (títulos de seção, rótulos dos quadros de
informação e cabeçalhos de tabela) em inglês -> no idioma de cada wiki oficial, alinhando a mesma página nos
dois idiomas (a estrutura é a mesma). Usado nas páginas dos mods antes da tradução automática.
Rodar de vez em quando:  python3 tools/gen_terms.py"""
import json, re, html, time, urllib.request, urllib.parse, collections, os

UA = "Mozilla/5.0 (Linux; Android) GuiaDoVale/terms (unofficial read-only reader; https://github.com/mmmartines/stardew-wiki-app)"
LANGS = ["pt", "es", "fr", "de", "it", "hu", "ru", "tr", "ja", "ko", "zh"]
PAGES = ["Abigail", "Sebastian", "Lewis", "Parsnip", "Sardine", "Fried Egg", "Green Slime", "Galaxy Sword",
         "Egg Festival", "Diamond", "Dwarf Scroll I", "Sprinkler", "Chicken", "Wine", "Coop", "Iridium Pickaxe",
         "Ruby Ring", "Apple", "Common Mushroom", "Pierre's General Store", "Fish Pond", "Furnace", "Bundles", "Quests"]

def host(l): return "stardewvalleywiki.com" if l == "en" else f"{l}.stardewvalleywiki.com"

def api(l, **p):
    p.update(format="json", formatversion="2")
    url = f"https://{host(l)}/mediawiki/api.php?" + urllib.parse.urlencode(p)
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    for tent in range(4):
        time.sleep(1.0 + tent * 20)  # devagar; se a wiki recusar, espera e tenta de novo
        try:
            with urllib.request.urlopen(req, timeout=30) as r:
                return json.load(r)
        except Exception as e:
            err = e
    raise err

def clean(x):
    x = html.unescape(re.sub(r"<[^>]+>", "", x))
    return re.sub(r"\s+", " ", x).strip().rstrip(":").strip()

def parts(t):
    heads = [clean(h) for _, h in re.findall(r"<h([234])[^>]*>(.*?)</h\1>", t, re.S)]
    heads = heads[1:] if heads and len(heads[0]) < 20 and heads[0] in ("Contents",) else heads
    labels = [clean(x) for x in re.findall(r'<td id="infoboxsection"[^>]*>(.*?)</td>', t, re.S)]
    tables = []
    for tb in re.findall(r"<table[^>]*>(.*?)</table>", t, re.S):
        tables.append([clean(x) for x in re.findall(r"<th[^>]*>(.*?)</th>", tb, re.S)])
    return heads, labels, tables

votes = {l: collections.defaultdict(collections.Counter) for l in LANGS}
for page in PAGES:
    try:
        ll = api("en", action="query", titles=page, redirects=1, prop="langlinks", lllimit="max")
        links = {x["lang"]: x["title"] for x in ll["query"]["pages"][0].get("langlinks", [])}
        en = parts(api("en", action="parse", page=page, redirects=1, prop="text")["parse"]["text"])
    except Exception as e:
        print("pulei", page, e); continue
    for l in LANGS:
        if l not in links: continue
        try:
            tr = parts(api(l, action="parse", page=links[l], redirects=1, prop="text")["parse"]["text"])
        except Exception as e:
            print("pulei", page, l, e); continue
        pairs = []
        # só alinha listas do mesmo tamanho (mesma estrutura nos dois idiomas)
        if len(en[0]) == len(tr[0]): pairs += zip(en[0], tr[0])
        if len(en[1]) == len(tr[1]): pairs += zip(en[1], tr[1])
        if len(en[2]) == len(tr[2]):
            for a, b in zip(en[2], tr[2]):
                if len(a) == len(b): pairs += zip(a, b)
        for a, b in pairs:
            if a and b and len(a) <= 40 and len(b) <= 60 and not re.search(r"\d", a):
                votes[l][a][b] += 1
    print("ok", page)

out = {}
for l in LANGS:
    m = {}
    for a, c in votes[l].items():
        b, n = c.most_common(1)[0]
        if n >= 1 and b: m[a] = b
    out[l] = dict(sorted(m.items()))
dst = os.path.join(os.path.dirname(__file__), "..", "android", "app", "src", "main", "assets", "terms.json")
json.dump(out, open(dst, "w", encoding="utf-8"), ensure_ascii=False, indent=0, sort_keys=True)
print({l: len(v) for l, v in out.items()})
