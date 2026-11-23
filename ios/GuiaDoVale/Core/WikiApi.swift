import Foundation

struct Section: Codable, Hashable {
    let line: String
    let anchor: String
    let level: Int
}

struct Page: Codable {
    let title: String
    let html: String
    let sections: [Section]
}

struct Hit: Hashable, Identifiable {
    let title: String
    var snippet: String = ""
    var ns: Int = 0
    var id: String { title }
}

struct Chunk {
    let items: [Hit]
    let next: String?
}

/// Espaçamento mínimo entre pedidos, para não sobrecarregar o servidor da wiki.
private actor Throttle {
    private var last = Date.distantPast
    func wait() async {
        let gap = 0.25 - Date().timeIntervalSince(last)
        if gap > 0 { try? await Task.sleep(nanoseconds: UInt64(gap * 1_000_000_000)) }
        last = Date()
    }
}

/// Acesso somente leitura à API MediaWiki da Stardew Valley Wiki, no idioma escolhido.
enum WikiApi {
    static var base: String { Lang.base() }

    private static let throttle = Throttle()
    private static let ua: String = {
        let v = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1"
        return "Mozilla/5.0 (iPhone; CPU iPhone OS like Mac OS X) GuiaDoVale/\(v) (unofficial read-only reader; https://github.com/mmmartines/stardew-wiki-app)"
    }()

    private static let session: URLSession = {
        let c = URLSessionConfiguration.default
        c.timeoutIntervalForRequest = 20
        c.httpAdditionalHeaders = ["User-Agent": ua, "Accept": "application/json"]
        return URLSession(configuration: c)
    }()

    // de tempos em tempos tenta de novo a conexão direta (o bloqueio anti-robô costuma ser passageiro)
    nonisolated(unsafe) private static var bridgeSince: Date?

    static func pageURL(_ title: String, lang: String = Lang.code) -> URL {
        let path = title.replacingOccurrences(of: " ", with: "_")
            .addingPercentEncoding(withAllowedCharacters: .urlPathAllowed.subtracting(CharacterSet(charactersIn: "?#"))) ?? title
        return URL(string: Lang.base(lang) + "/" + path)!
    }

    static func historyURL(_ title: String) -> URL {
        var c = URLComponents(string: base + "/mediawiki/index.php")!
        c.queryItems = [URLQueryItem(name: "title", value: title.replacingOccurrences(of: " ", with: "_")),
                        URLQueryItem(name: "action", value: "history")]
        return c.url!
    }

    // MARK: - Pedido base

    private static func get(_ params: [String: String], lang: String = Lang.code) async throws -> [String: Any] {
        let wikiBase = Lang.base(lang)
        var all = params
        all["format"] = "json"; all["formatversion"] = "2"; all["utf8"] = "1"
        if lang != Lang.code { all["origin"] = "*" }
        var c = URLComponents(string: wikiBase + "/mediawiki/api.php")!
        c.queryItems = all.sorted { $0.key < $1.key }.map { URLQueryItem(name: $0.key, value: $0.value) }
        // "+" precisa ir codificado, senão o servidor lê como espaço
        c.percentEncodedQuery = c.percentEncodedQuery?.replacingOccurrences(of: "+", with: "%2B")
        let url = c.url!

        if !Net.isOnline { throw OfflineError() }
        await throttle.wait()

        if let since = bridgeSince, Date().timeIntervalSince(since) > 180 { bridgeSince = nil }
        var directError = "-"
        if bridgeSince == nil {
            do {
                let (data, resp) = try await session.data(from: url)
                let code = (resp as? HTTPURLResponse)?.statusCode ?? 0
                if (200..<300).contains(code), let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
                    return obj
                }
                directError = "HTTP \(code)"
            } catch {
                if !Net.isOnline { throw OfflineError() }
                directError = String(describing: error)
            }
            bridgeSince = Date() // a partir daqui usa o navegador interno
        }
        do {
            let text = try await Bridge.shared.fetch(url)
            guard let data = text.data(using: .utf8),
                  let obj = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
                throw WikiError(description: "not JSON")
            }
            return obj
        } catch {
            if !Net.isOnline { throw OfflineError() }
            let detail = "\(error)\n(direct: \(directError))"
            if let m = detail.range(of: #"HTTP (\d{3})"#, options: .regularExpression),
               let code = Int(detail[m].dropFirst(5)) {
                throw ServerError(code: code, detail: detail)
            }
            throw WikiError(description: detail)
        }
    }

    private static func list(_ j: [String: Any], _ key: String) -> [[String: Any]] {
        ((j["query"] as? [String: Any])?[key] as? [[String: Any]]) ?? []
    }

    private static func cont(_ j: [String: Any], _ key: String) -> String? {
        guard let s = (j["continue"] as? [String: Any])?[key] as? String, !s.isEmpty else { return nil }
        return s
    }

    /// Categorias internas da wiki, sem interesse para quem joga.
    private static let maintenance = try! NSRegularExpression(
        pattern: "archived|user pages|maintenance|candidates for|pages with|pages using|hidden categor|tracking|templates?$",
        options: .caseInsensitive)

    // MARK: - Consultas

    static func parse(_ title: String) async throws -> Page {
        let j = try await get(["action": "parse", "page": title, "redirects": "1", "prop": "text|sections",
                               "disableeditsection": "1", "disabletoc": "1", "disablelimitreport": "1"])
        if let e = j["error"] as? [String: Any] {
            let code = e["code"] as? String ?? ""
            if code == "missingtitle" || code == "invalidtitle" { throw MissingPageError() }
            throw WikiError(description: e["info"] as? String ?? "wiki error")
        }
        guard let p = j["parse"] as? [String: Any] else { throw WikiError(description: "no parse") }
        let secs = (p["sections"] as? [[String: Any]] ?? []).map {
            Section(line: ($0["line"] as? String ?? "").strippingHTML,
                    anchor: $0["anchor"] as? String ?? "",
                    level: $0["toclevel"] as? Int ?? 1)
        }
        return Page(title: p["title"] as? String ?? title, html: p["text"] as? String ?? "", sections: secs)
    }

    /// Sugestões rápidas pelo começo do título.
    static func prefix(_ q: String, lang: String = Lang.code, limit: Int = 20) async throws -> [Hit] {
        let j = try await get(["action": "query", "list": "prefixsearch", "pssearch": q,
                               "pslimit": String(limit), "psnamespace": "0"], lang: lang)
        return list(j, "prefixsearch").compactMap { $0["title"] as? String }.map { Hit(title: $0) }.uniqueByTitle
    }

    /// Busca no texto completo de todas as páginas.
    static func search(_ q: String) async throws -> [Hit] {
        let j = try await get(["action": "query", "list": "search", "srsearch": q, "srlimit": "40",
                               "srprop": "snippet", "srnamespace": "0"])
        return list(j, "search").compactMap { o -> Hit? in
            guard let title = o["title"] as? String else { return nil }
            return Hit(title: title, snippet: (o["snippet"] as? String ?? "").strippingHTML)
        }.uniqueByTitle
    }

    static func categoryMembers(_ category: String, next: String?) async throws -> Chunk {
        var p = ["action": "query", "list": "categorymembers", "cmtitle": category, "cmlimit": "200", "cmtype": "subcat|page"]
        if let next { p["cmcontinue"] = next }
        let j = try await get(p)
        let items = list(j, "categorymembers").compactMap { o -> Hit? in
            guard let title = o["title"] as? String else { return nil }
            return Hit(title: title, ns: o["ns"] as? Int ?? 0)
        }
        return Chunk(items: items, next: cont(j, "cmcontinue"))
    }

    static func allPages(prefix: String, next: String?) async throws -> Chunk {
        var p = ["action": "query", "list": "allpages", "apnamespace": "0", "aplimit": "200", "apfilterredir": "nonredirects"]
        if !prefix.isEmpty { p["apprefix"] = prefix }
        if let next { p["apcontinue"] = next }
        let j = try await get(p)
        return Chunk(items: list(j, "allpages").compactMap { $0["title"] as? String }.map { Hit(title: $0) },
                     next: cont(j, "apcontinue"))
    }

    static func allCategories(prefix: String, next: String?) async throws -> Chunk {
        var p = ["action": "query", "list": "allcategories", "aclimit": "200"]
        if !prefix.isEmpty { p["acprefix"] = prefix }
        if let next { p["accontinue"] = next }
        let j = try await get(p)
        let items = list(j, "allcategories").compactMap { o -> Hit? in
            guard let name = (o["category"] as? String) ?? (o["*"] as? String) else { return nil }
            let r = NSRange(name.startIndex..., in: name)
            if maintenance.firstMatch(in: name, range: r) != nil { return nil }
            return Hit(title: "Category:" + name, ns: 14)
        }
        return Chunk(items: items, next: cont(j, "accontinue"))
    }

    /// Título equivalente na wiki de outro idioma, pelos links entre idiomas.
    static func translate(_ title: String, from: String, to: String) async throws -> String? {
        if from == to { return title }
        return try await translateMany([title], from: from, to: to).first?.1
    }

    /// Para cada título da wiki em [from], o equivalente na wiki em [to] (nil quando não há), numa chamada só.
    static func translateMany(_ titles: [String], from: String, to: String) async throws -> [(String, String?)] {
        if titles.isEmpty { return [] }
        let j = try await get(["action": "query", "titles": titles.joined(separator: "|"), "redirects": "1",
                               "prop": "langlinks", "lllang": to, "lllimit": "max"], lang: from)
        var alias: [String: String] = [:]
        for k in ["normalized", "redirects"] {
            for o in list(j, k) {
                if let f = o["from"] as? String, let t = o["to"] as? String { alias[f] = t }
            }
        }
        var found: [String: String?] = [:]
        for p in list(j, "pages") {
            guard let title = p["title"] as? String else { continue }
            let ll = (p["langlinks"] as? [[String: Any]])?.first?["title"] as? String
            found[title] = (ll?.isEmpty == false) ? ll : nil
        }
        return titles.map { t in
            var k = t
            for _ in 0..<3 { if let a = alias[k] { k = a } }
            return (t, found[k] ?? nil)
        }
    }

    /// Páginas da wiki atual que apontam para [enTitle] na wiki em inglês ("Parsnip" → "Chirívia").
    static func fromEnglish(_ enTitle: String) async throws -> [String] {
        let j = try await get(["action": "query", "list": "langbacklinks", "lbllang": "en",
                               "lbltitle": enTitle, "lbllimit": "5"])
        return list(j, "langbacklinks").filter { ($0["ns"] as? Int ?? 0) == 0 }.compactMap { $0["title"] as? String }
    }

    static func random() async throws -> String {
        let j = try await get(["action": "query", "list": "random", "rnnamespace": "0", "rnlimit": "1"])
        guard let t = list(j, "random").first?["title"] as? String else { throw WikiError(description: "no result") }
        return t
    }
}

extension Array where Element == Hit {
    /// Remove redirecionamentos que só mudam maiúsculas/minúsculas.
    var uniqueByTitle: [Hit] {
        var seen = Set<String>()
        return filter { seen.insert($0.title.lowercased()).inserted }
    }
}

extension String {
    /// Texto sem as tags HTML (trechos de busca e títulos de seção).
    var strippingHTML: String {
        var s = replacingOccurrences(of: "<[^>]+>", with: "", options: .regularExpression)
        for (k, v) in ["&amp;": "&", "&lt;": "<", "&gt;": ">", "&quot;": "\"", "&#39;": "'", "&nbsp;": " "] {
            s = s.replacingOccurrences(of: k, with: v)
        }
        return s.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    var htmlEscaped: String {
        replacingOccurrences(of: "&", with: "&amp;").replacingOccurrences(of: "<", with: "&lt;")
            .replacingOccurrences(of: ">", with: "&gt;").replacingOccurrences(of: "\"", with: "&quot;")
            .replacingOccurrences(of: "'", with: "&#39;")
    }
}
