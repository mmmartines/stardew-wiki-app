import Foundation

/// Glossário das páginas dos mods (igual ao Android): nome em inglês de algo do jogo base → nome na wiki
/// oficial do idioma, pelos links entre idiomas. Fica salvo no aparelho, um arquivo por idioma.
/// "" = a wiki do idioma não tem essa página.
enum Glossary {
    // usado tanto pela tela quanto pela busca em segundo plano: acesso protegido por trava
    nonisolated(unsafe) private static var maps: [String: [String: String]] = [:]
    private static let lock = NSLock()
    private static let svw = try! NSRegularExpression(pattern: #"href="https?://(?:www\.)?stardewvalleywiki\.com/([^"?#]+)"#)
    private static let season = try! NSRegularExpression(pattern: #"href="/wiki/(Spring|Summer|Fall|Winter)""#)

    /// Só para páginas de mod lidas em outro idioma que não o inglês.
    static func applies(_ s: Source) -> Bool { s.mod && Lang.code != "en" }

    /// Títulos do jogo base citados na página (links para a wiki oficial e estações).
    static func titles(_ html: String) -> [String] {
        var out: [String] = []
        var seen = Set<String>()
        let r = NSRange(html.startIndex..., in: html)
        for m in svw.matches(in: html, range: r) {
            guard let g = Range(m.range(at: 1), in: html) else { continue }
            let raw = String(html[g])
            let t = (raw.removingPercentEncoding ?? raw).replacingOccurrences(of: "_", with: " ").trimmingCharacters(in: .whitespaces)
            if !t.isEmpty, !t.contains(":"), seen.insert(t).inserted { out.append(t) }
        }
        for m in season.matches(in: html, range: r) {
            if let g = Range(m.range(at: 1), in: html), seen.insert(String(html[g])).inserted { out.append(String(html[g])) }
        }
        return out
    }

    private static func file(_ lang: String) -> URL {
        let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir.appendingPathComponent("gloss_\(lang).json")
    }

    private static func map(_ lang: String) -> [String: String] {
        lock.lock(); defer { lock.unlock() }
        if let m = maps[lang] { return m }
        let m = (try? Data(contentsOf: file(lang))).flatMap { try? JSONDecoder().decode([String: String].self, from: $0) } ?? [:]
        maps[lang] = m
        return m
    }

    /// O que já se sabe (só os que têm tradução), como JSON para a página.
    static func knownJSON(_ lang: String, _ titles: [String]) -> String {
        let m = map(lang)
        var known: [String: String] = [:]
        for t in titles { if let v = m[t], !v.isEmpty { known[t] = v } }
        let d = (try? JSONSerialization.data(withJSONObject: known)) ?? Data("{}".utf8)
        return String(data: d, encoding: .utf8) ?? "{}"
    }

    static func missing(_ lang: String, _ titles: [String]) -> [String] {
        let m = map(lang)
        return titles.filter { m[$0] == nil }
    }

    /// Busca na wiki oficial os títulos que faltam e salva.
    static func fetch(_ lang: String, _ titles: [String]) async {
        var found: [String: String] = [:]
        var i = 0
        while i < titles.count {
            let part = Array(titles[i..<min(i + 50, titles.count)])
            i += 50
            guard let tr = try? await WikiApi.translateMany(part, from: "en", to: lang) else { return }
            for (en, t) in tr { found[en] = t ?? "" }
        }
        var m = map(lang)
        m.merge(found) { _, new in new }
        lock.lock(); maps[lang] = m; lock.unlock()
        if let d = try? JSONEncoder().encode(m) { try? d.write(to: file(lang)) }
    }

    /// Script inicial da página: aplica o que já está no glossário assim que ela abre.
    static func initScript(_ s: Source, html: String) -> String {
        guard applies(s) else { return "" }
        let base = (try? JSONSerialization.data(withJSONObject: [Lang.base()])).flatMap { String(data: $0, encoding: .utf8) } ?? "[\"\"]"
        return "window.__glossInit={map:" + knownJSON(Lang.code, titles(html)) + ",base:" + base + "[0]};" +
            "window.__terms=" + termsJSON(Lang.code) + ";"
    }

    // MARK: Termos da wiki (títulos de seção, rótulos dos quadros, cabeçalhos de tabela) — igual ao Android

    /// Páginas do jogo base comparadas em inglês e no idioma: a estrutura é a mesma, então os termos se alinham.
    private static let sample = ["Abigail", "Parsnip", "Sardine", "Fried Egg", "Green Slime", "Galaxy Sword", "Egg Festival"]
    private static let label = try! NSRegularExpression(pattern: #"<td id="infoboxsection"[^>]*>(.*?)</td>"#, options: .dotMatchesLineSeparators)
    private static let table = try! NSRegularExpression(pattern: #"<table[^>]*>(.*?)</table>"#, options: .dotMatchesLineSeparators)
    private static let th = try! NSRegularExpression(pattern: #"<th[^>]*>(.*?)</th>"#, options: .dotMatchesLineSeparators)
    nonisolated(unsafe) private static var terms: [String: String] = [:]

    private static func termsFile(_ lang: String) -> URL {
        file(lang).deletingLastPathComponent().appendingPathComponent("terms_\(lang).json")
    }

    static func hasTerms(_ lang: String) -> Bool { FileManager.default.fileExists(atPath: termsFile(lang).path) }

    /// Termos já salvos para o idioma, como JSON para a página ("{}" se ainda não buscou).
    static func termsJSON(_ lang: String) -> String {
        lock.lock(); defer { lock.unlock() }
        if let t = terms[lang] { return t }
        let t = (try? String(contentsOf: termsFile(lang), encoding: .utf8)) ?? "{}"
        terms[lang] = t
        return t
    }

    private static func groups(_ re: NSRegularExpression, _ s: String) -> [String] {
        re.matches(in: s, range: NSRange(s.startIndex..., in: s)).compactMap { m in
            Range(m.range(at: 1), in: s).map { String(s[$0]) }
        }
    }

    private static func clean(_ h: String) -> String {
        var t = h.strippingHTML.replacingOccurrences(of: #"\s+"#, with: " ", options: .regularExpression)
            .trimmingCharacters(in: .whitespaces)
        while t.hasSuffix(":") { t.removeLast() }
        return t.trimmingCharacters(in: .whitespaces)
    }

    private static func parts(_ p: Page) -> ([String], [String], [[String]]) {
        (p.sections.map { $0.line.trimmingCharacters(in: .whitespaces) },
         groups(label, p.html).map(clean),
         groups(table, p.html).map { groups(th, $0).map(clean) })
    }

    /// Busca e salva os termos do idioma (uma vez por idioma).
    static func fetchTerms(_ lang: String) async {
        guard let pairs = try? await WikiApi.translateMany(sample, from: "en", to: lang) else { return }
        var out: [String: String] = [:]
        func add(_ a: [String], _ b: [String]) {
            guard a.count == b.count else { return } // só alinha listas com a mesma estrutura
            for (x, y) in zip(a, b) where !x.isEmpty && !y.isEmpty && x.count <= 40 && y.count <= 60
                && !x.contains(where: \.isNumber) && out[x] == nil {
                out[x] = y
            }
        }
        for (en, tr) in pairs {
            guard let tr,
                  let a = try? await WikiApi.parse(en, src: Sources.base("en")),
                  let b = try? await WikiApi.parse(tr, src: Sources.base(lang)) else { continue }
            let pa = parts(a), pb = parts(b)
            add(pa.0, pb.0)
            add(pa.1, pb.1)
            if pa.2.count == pb.2.count { for (x, y) in zip(pa.2, pb.2) { add(x, y) } }
        }
        let json = (try? JSONSerialization.data(withJSONObject: out)).flatMap { String(data: $0, encoding: .utf8) } ?? "{}"
        lock.lock(); terms[lang] = json; lock.unlock()
        try? json.write(to: termsFile(lang), atomically: true, encoding: .utf8)
    }
}
