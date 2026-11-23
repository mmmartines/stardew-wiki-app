import Foundation

/// Páginas HTML do app, montadas igual às do Android.
enum HTML {
    private static func e(_ s: String) -> String { s.htmlEscaped }

    static var version: String { Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0" }

    // MARK: Tela inicial

    /// `src` = wiki escolhida nos botões do topo; o menu e os recentes são só dela. A busca do topo é global.
    static func home(dark: Bool, recent: [String], src: Source, menu: [(String, [Cat])]) -> String {
        var sb = "<!DOCTYPE html><html><head><meta charset='utf-8'>"
        sb += "<meta name='viewport' content='width=device-width, initial-scale=1, user-scalable=no'>"
        sb += "<style>" + SharedAssets.fontCSS + PageCSS.home + "</style></head><body class='\(dark ? "dark" : "")'>"

        sb += "<div class='scene'>"
        sb += "<div class='sun'></div><div class='cloud c1'></div><div class='cloud c2'></div><div class='cloud c3'></div>"
        sb += "<div class='star s1'></div><div class='star s2'></div><div class='star s3'></div><div class='star s4'></div>"
        sb += "<div class='sign'><div class='post p1'></div><div class='post p2'></div>"
        sb += "<div class='board'><div class='t1'>" + e(t("home_title")) + "</div>"
        sb += "<div class='t2'>" + e(src.mod ? src.emoji + " " + src.name : t("home_tagline")) + "</div></div></div>"
        sb += "<div class='grass'></div></div>"

        sb += "<div class='search' role='button' tabindex='0' onclick='Android.search()'><span class='lupa' aria-hidden='true'></span>"
        sb += e(t("home_search_all")) + "</div>"

        // um botão por wiki: filtra o menu da tela inicial
        sb += "<div class='srcs' role='tablist' aria-label='" + e(t("home_sources")) + "'>"
        let all: [Source?] = [nil] + Sources.mods.map { Optional($0) }
        for (i, w) in all.enumerated() {
            let on = w == nil ? !src.mod : w!.key == src.key
            let label = w == nil ? "⭐ " + t("src_base") : w!.emoji + " " + w!.short
            let a11y = w == nil ? t("src_base") : t("src_mod_fmt", w!.name)
            sb += "<span class='src" + (on ? " on" : "") + "' role='tab' tabindex='0' aria-selected='\(on)'"
            sb += " aria-label='" + e(a11y) + "' onclick='Android.source(\(i))'>" + e(label) + "</span>"
        }
        sb += "</div>"
        if src.mod {
            sb += "<div class='modinfo'>" + e(t("src_mod_fmt", src.name))
            if Lang.code != "en" { sb += " · " + e(t("mod_english_only")) }
            sb += "</div>"
        }

        sb += "<div id='recent'>" + recentHTML(recent) + "</div>"

        var idx = 0
        for (name, items) in menu {
            sb += "<div class='plank' role='heading' aria-level='2'>" + e(name) + "</div><div class='box'><div class='grid'>"
            for c in items {
                sb += "<div class='cell' role='button' tabindex='0' aria-label='" + e(c.label) + "'"
                sb += " onclick='Android.open(\(idx))'>"
                sb += "<div class='slot' aria-hidden='true'><img alt='' src='" + c.icon + "'"
                sb += " loading='lazy' onerror=\"this.style.visibility='hidden'\"></div>"
                sb += "<div class='label' aria-hidden='true'>" + e(c.label) + "</div></div>"
                idx += 1
            }
            sb += "</div></div>"
        }
        let foot = src.mod ? t("home_footer_mod_fmt", src.name, src.license) : t("home_footer")
        sb += "<p class='foot' role='link' tabindex='0' onclick='Android.credits()'>" + e(foot)
        sb += "<br><u>" + e(t("about")) + "</u></p>"
        sb += "<script>window.__recent=function(h){document.getElementById('recent').innerHTML=h}</script>"
        sb += "</body></html>"
        return sb
    }

    /// Bloco "Vistos recentemente" (atualizado sem recarregar a tela).
    static func recentHTML(_ recent: [String]) -> String {
        if recent.isEmpty { return "" }
        var sb = "<div class='plank' role='heading' aria-level='2'>" + e(t("home_recent")) + "</div><div class='chips'>"
        for (i, title) in recent.enumerated() {
            sb += "<span class='chip' role='button' tabindex='0' onclick='Android.recent(\(i))'>" + e(title) + "</span>"
        }
        return sb + "</div>"
    }

    // MARK: Artigo

    static func article(_ p: Page, dark: Bool, src: Source = Sources.base()) -> String {
        var s = "<!DOCTYPE html><html><head><meta charset=\"utf-8\">"
        s += "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
        s += "<style>" + SharedAssets.fontCSS
        s += SharedAssets.text("article", "css")
        // o iOS ajusta o texto sozinho em paisagem; o tamanho escolhido no app é que vale
        s += "html{-webkit-text-size-adjust:100%}</style></head>"
        s += "<body class=\"" + (dark ? "dark" : "") + "\">"
        if src.mod {
            // faixa no topo: qual mod é e, fora do inglês, que a wiki do mod só existe em inglês
            var txt = t("src_mod_fmt", src.name)
            if Lang.code != "en" { txt += " · " + t("mod_english_only") + " " + t("mod_terms_translated") }
            s += "<div class='mod-badge'>" + src.emoji + " " + e(txt) + "</div>"
        }
        s += "<h1 class=\"page-title\">" + e(p.title) + "</h1>"
        s += p.html
        s += footer(p, src)
        s += "<script>window.__t=" + jsStrings() + ";" + Glossary.initScript(src, html: p.html) + "</script>"
        s += "<script>" + SharedAssets.text("article", "js") + "</script></body></html>"
        return s
    }

    /// Crédito em toda página: contribuidores, licença, link para o original e para o histórico de autores.
    private static func footer(_ p: Page, _ src: Source) -> String {
        let orig = e(WikiApi.pageURL(p.title, src: src).absoluteString + "?app_ext=1")
        let hist = e(WikiApi.historyURL(p.title, src: src).absoluteString)
        var s = "<div class='credit'><p>"
        if src.mod {
            s += e(t("article_footer_mod_fmt", src.name + " Wiki", src.license)) + "</p>"
            s += "<p>" + e(t("article_footer_mod_art")) + "</p><p>"
        } else {
            s += e(t("article_footer")) + "</p>"
            s += "<p>" + e(t("article_footer_art")) + "</p><p>"
        }
        s += "<a class='ext' href='" + orig + "'>" + e(t("view_original")) + " ›</a> · "
        s += "<a class='ext' href='" + hist + "'>" + e(t("page_authors")) + " ›</a> · "
        s += "<a class='ext' href='" + src.licenseURL + "'>" + src.license + " ›</a></p></div>"
        return s
    }

    private static func jsStrings() -> String {
        let m: [String: String] = [
            "more": t("js_more_details"), "showAll": t("js_show_all"), "filter": t("js_filter"),
            "onPage": t("js_on_this_page"), "readIntro": t("js_read_intro"), "seeAlso": t("js_see_also"),
            "showTable": t("js_show_table"), "show": t("js_show"), "zoomHint": t("js_zoom_hint"), "close": t("close"),
        ]
        let d = (try? JSONSerialization.data(withJSONObject: m)) ?? Data("{}".utf8)
        return String(data: d, encoding: .utf8) ?? "{}"
    }

    // MARK: Sobre e créditos

    static func credits(dark: Bool) -> String {
        let wiki = Lang.base()
        func link(_ url: String, _ label: String) -> String { "<p><a href='" + url + "'>" + e(t(label)) + " ›</a></p>" }
        func sec(_ h: String, _ body: String) -> String { "<div class='box'><h2>" + e(t(h)) + "</h2>" + body + "</div>" }
        var s = "<!DOCTYPE html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width, initial-scale=1'>"
        s += "<style>" + SharedAssets.fontCSS + PageCSS.credits + "</style></head>"
        s += "<body class='" + (dark ? "dark" : "") + "'>"
        s += "<div class='head'><div class='name'>" + e(t("app_name")) + "</div>"
        s += "<div class='tag'>" + e(t("unofficial_tag")) + "</div>"
        s += "<div class='ver'>" + e(t("credits_version_fmt", version)) + "</div></div>"
        var game = "<p>" + e(t("credits_game")) + "</p>"
        game += link("https://www.stardewvalley.net/", "credits_game_h")
        s += sec("credits_game_h", game)
        var w = "<p>" + e(t("credits_wiki")) + "</p>"
        w += link(wiki + "/", "credits_wiki_link")
        w += link(wiki + "/Special:ActiveUsers", "credits_contrib_link")
        s += sec("credits_wiki_h", w)
        var lic = "<p>" + e(t("credits_license")) + "</p>"
        lic += link("https://creativecommons.org/licenses/by-nc-sa/3.0/", "credits_license_link")
        s += sec("credits_license_h", lic)
        var mods = "<p>" + e(t("credits_mods")) + "</p>"
        for m in Sources.mods { mods += "<p><a href='" + m.base + "/'>" + m.emoji + " " + e(m.name) + " Wiki ›</a></p>" }
        mods += "<p><a href='https://creativecommons.org/licenses/by-sa/4.0/'>CC BY-SA 4.0 ›</a></p>"
        s += sec("credits_mods_h", mods)
        var app = "<p>" + e(t("credits_app")) + "</p>"
        app += link("https://github.com/mmmartines/stardew-wiki-app", "credits_source_link")
        app += "<p class='small'>" + e(t("credits_font")) + "</p>"
        s += sec("credits_app_h", app)
        s += "</body></html>"
        return s
    }

    // MARK: Feito por um fã

    static func fan(dark: Bool) -> String {
        let img = "https://stardewvalleywiki.com/mediawiki/images/en/"
        let names = ["d/db/Parsnip.png", "a/a5/Stardrop.png", "8/83/Bouquet.png", "0/04/Sardine.png", "7/78/Copper_Ore.png"]
        var icons = ""
        for n in names { icons += "<img src='" + img + n + "' alt='' onerror=\"this.style.display='none'\">" }
        var s = "<!DOCTYPE html><html><head><meta charset='utf-8'>"
        s += "<meta name='viewport' content='width=device-width, initial-scale=1, user-scalable=no'>"
        s += "<style>" + SharedAssets.fontCSS + PageCSS.fan + "</style></head>"
        s += "<body class='" + (dark ? "dark" : "") + "'>"
        s += "<div class='board'><div class='name'>" + e(t("app_name")) + "</div>"
        s += "<div class='title'>" + e(t("fan_title")) + "</div></div>"
        s += "<div class='icons' aria-hidden='true'>" + icons + "</div>"
        s += "<div class='box'><div class='inner'>"
        s += "<p>" + e(t("fan_p1")) + "</p>"
        s += "<p class='strong'>" + e(t("fan_p2")) + "</p>"
        s += "<p>" + e(t("fan_p3")) + "</p>"
        s += "<p class='small'>" + e(t("fan_p4")) + "</p>"
        s += "<p class='sign'>" + e(t("fan_signoff")) + "</p>"
        s += "</div></div><div class='actions'>"
        s += "<button class='go' onclick='Android.done()'>" + e(t("welcome_continue")) + "</button>"
        s += "<button class='link' onclick='Android.credits()'>" + e(t("welcome_credits")) + "</button>"
        s += "</div></body></html>"
        return s
    }
}
