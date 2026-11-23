import Foundation
import CryptoKit

/// Uma página salva: idioma da wiki + título. `time` = quando foi vista (não entra na igualdade).
struct Entry: Codable, Hashable, Identifiable {
    let lang: String
    let title: String
    var time: Double = 0

    var id: String { lang + ":" + title }
    static func == (a: Entry, b: Entry) -> Bool { a.lang == b.lang && a.title == b.title }
    func hash(into h: inout Hasher) { h.combine(lang); h.combine(title) }

    enum CodingKeys: String, CodingKey { case lang = "l", title = "t", time = "d" }
}

/// Favoritos, histórico, preferências e páginas salvas para ler offline.
@MainActor
final class Store: ObservableObject {
    static let shared = Store()

    @Published private(set) var favorites: [Entry] = []
    @Published private(set) var history: [Entry] = []
    @Published var textZoom: Int {
        didSet { UserDefaults.standard.set(textZoom, forKey: "zoom") }
    }

    private let dir: URL
    private let ud = UserDefaults.standard

    private init() {
        let docs = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        dir = docs.appendingPathComponent("pages", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let z = UserDefaults.standard.integer(forKey: "zoom")
        textZoom = z == 0 ? 100 : z
        favorites = load("favs_all")
        history = load("hist_all")
    }

    private func load(_ key: String) -> [Entry] {
        guard let d = ud.data(forKey: key), let l = try? JSONDecoder().decode([Entry].self, from: d) else { return [] }
        return l
    }

    private func save() {
        ud.set(try? JSONEncoder().encode(favorites), forKey: "favs_all")
        ud.set(try? JSONEncoder().encode(history), forKey: "hist_all")
    }

    /// `w` = wiki do item: código do idioma (oficial) ou do mod.
    private func here(_ t: String, _ w: String) -> Entry { Entry(lang: w, title: t, time: Date().timeIntervalSince1970) }

    // MARK: Favoritos e histórico

    func isFavorite(_ t: String, w: String = Lang.code) -> Bool { favorites.contains(here(t, w)) }

    @discardableResult
    func toggleFavorite(_ t: String, w: String = Lang.code) -> Bool {
        let e = here(t, w)
        if let i = favorites.firstIndex(of: e) {
            favorites.remove(at: i); save(); return false
        }
        favorites.insert(e, at: 0); save(); return true
    }

    func remove(_ e: Entry, favorite: Bool) {
        if favorite { favorites.removeAll { $0 == e } } else { history.removeAll { $0 == e } }
        save()
    }

    /// Desfaz uma remoção: devolve o item na posição em que estava.
    func restore(_ e: Entry, at index: Int, favorite: Bool) {
        if favorite {
            if !favorites.contains(e) { favorites.insert(e, at: min(index, favorites.count)) }
        } else if !history.contains(e) {
            history.insert(e, at: min(index, history.count))
        }
        save()
    }

    /// Histórico só do idioma atual (atalhos da tela inicial).
    var historyHere: [String] { history(of: Lang.code) }

    /// Histórico de uma wiki (oficial no idioma ou um mod).
    func history(of w: String) -> [String] { history.filter { $0.lang == w }.map(\.title) }

    func addHistory(_ t: String, w: String = Lang.code) {
        let e = here(t, w)
        history.removeAll { $0 == e }
        history.insert(e, at: 0)
        if history.count > 150 { history.removeLast(history.count - 150) }
        save()
    }

    func clearHistory() { history = []; save() }

    // MARK: Páginas salvas (offline)

    struct Cached: Codable {
        let title: String
        let html: String
        let time: Double
        let sections: [WikiSection]
        var page: Page { Page(title: title, html: html, sections: sections) }
    }

    private func key(_ title: String, lang: String = Lang.code) -> String {
        lang + ":" + title.replacingOccurrences(of: "_", with: " ").trimmingCharacters(in: .whitespaces).lowercased()
    }

    private func file(_ title: String, _ w: String = Lang.code) -> URL {
        let digest = Insecure.MD5.hash(data: Data(key(title, lang: w).utf8)).map { String(format: "%02x", $0) }.joined()
        return dir.appendingPathComponent(digest + ".json")
    }

    func putPage(requested: String, _ p: Page, w: String = Lang.code) {
        let c = Cached(title: p.title, html: p.html, time: Date().timeIntervalSince1970, sections: p.sections)
        guard let data = try? JSONEncoder().encode(c) else { return }
        try? data.write(to: file(requested, w))
        if key(requested, lang: w) != key(p.title, lang: w) { try? data.write(to: file(p.title, w)) }
    }

    func getPage(_ title: String, w: String = Lang.code) -> Cached? {
        guard let d = try? Data(contentsOf: file(title, w)) else { return nil }
        return try? JSONDecoder().decode(Cached.self, from: d)
    }

    func isSaved(_ title: String, w: String = Lang.code) -> Bool { FileManager.default.fileExists(atPath: file(title, w).path) }

    private var files: [URL] {
        (try? FileManager.default.contentsOfDirectory(at: dir, includingPropertiesForKeys: [.fileSizeKey])) ?? []
    }

    var cacheCount: Int { files.count }
    var cacheBytes: Int { files.reduce(0) { $0 + ((try? $1.resourceValues(forKeys: [.fileSizeKey]).fileSize) ?? 0) } }

    func clearCache() { files.forEach { try? FileManager.default.removeItem(at: $0) } }

    /// Títulos das páginas salvas no aparelho para o idioma atual, em ordem alfabética.
    func savedTitles(w: String = Lang.code) -> [String] {
        var out = Set<String>()
        for f in files {
            guard let d = try? Data(contentsOf: f), let c = try? JSONDecoder().decode(Cached.self, from: d) else { continue }
            if file(c.title, w).lastPathComponent == f.lastPathComponent { out.insert(c.title) }
        }
        return out.sorted { $0.localizedCaseInsensitiveCompare($1) == .orderedAscending }
    }
}
