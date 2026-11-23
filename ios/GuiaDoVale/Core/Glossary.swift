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
        return "window.__glossInit={map:" + knownJSON(Lang.code, titles(html)) + ",base:" + base + "[0]};"
    }
}
