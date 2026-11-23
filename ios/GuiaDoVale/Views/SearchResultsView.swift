import SwiftUI

/// Uma linha do resultado da busca. `src` = wiki da página (nil = jogo base no idioma atual).
struct SearchRow: Identifiable, Hashable {
    enum Kind: Hashable { case searchAll, page, info, english, header }
    let kind: Kind
    let title: String
    var subtitle: String? = nil
    var src: String? = nil
    var id: String { "\(kind)|\(src ?? "")|\(title)|\(subtitle ?? "")" }
}

/// Busca global: procura ao mesmo tempo no jogo base e em todos os mods, com uma seção por wiki
/// (a wiki aberta na tela inicial primeiro). No jogo base, tenta também o nome em inglês quando há poucos resultados.
@MainActor
final class SearchModel: ObservableObject {
    @Published var rows: [SearchRow] = []
    @Published var loading = false
    @Published var emptyText: String?

    /// Resultado de uma wiki.
    private struct Part {
        let src: Source
        var rows: [SearchRow]
        let failed: Bool
    }

    private var parts: [Part] = []
    private var top: [SearchRow] = []
    private var task: Task<Void, Never>?
    private var generation = 0

    func cancel() {
        task?.cancel()
        generation += 1
        loading = false
    }

    private func order(_ first: Source) -> [Source] {
        [first] + ([Sources.base()] + Sources.mods).filter { $0.key != first.key }
    }

    private func sectionTitle(_ s: Source) -> String {
        s.mod ? s.emoji + "  " + t("src_mod_fmt", s.name)
              : "⭐  " + t("src_base") + " · " + Lang.current.flag + " " + Lang.current.name
    }

    /// Consulta todas as wikis em paralelo; cada uma devolve seus resultados ou falha sozinha.
    private func everywhere(_ q: String, first: Source, full: Bool) async -> [Part] {
        let list = order(first)
        return await withTaskGroup(of: (Int, Part).self) { g in
            for (i, w) in list.enumerated() {
                g.addTask {
                    do {
                        let hits = full ? try await WikiApi.search(q, src: w, limit: w.mod ? 15 : 40)
                                        : try await WikiApi.prefix(q, src: w, limit: w.mod ? 6 : 12)
                        let k = w.mod ? w.key : nil
                        return (i, Part(src: w, rows: hits.map {
                            SearchRow(kind: .page, title: $0.title, subtitle: $0.snippet.isEmpty ? nil : $0.snippet, src: k)
                        }, failed: false))
                    } catch {
                        return (i, Part(src: w, rows: [], failed: true))
                    }
                }
            }
            var out = [(Int, Part)]()
            for await r in g { out.append(r) }
            return out.sorted { $0.0 < $1.0 }.map(\.1)
        }
    }

    /// Monta a lista: cada wiki com resultados ganha um cabeçalho (jogo base ou qual mod).
    private func render(_ q: String, full: Bool) {
        var out = top
        for p in parts where !p.rows.isEmpty {
            out.append(SearchRow(kind: .header, title: sectionTitle(p.src), src: p.src.key))
            out += p.rows
        }
        if !parts.isEmpty && parts.allSatisfy(\.failed) {
            if !Net.isOnline {
                out.append(SearchRow(kind: .info, title: t("offline_title"), subtitle: t("offline_sub")))
            } else {
                out.append(SearchRow(kind: .info, title: "⚠️  " + t("search_failed")))
            }
            out += localMatches(q)
        }
        rows = out
        emptyText = full && out.isEmpty ? t("nothing_found_fmt", q) : nil
    }

    func suggest(_ raw: String, first: Source) {
        let q = raw.trimmingCharacters(in: .whitespaces)
        cancel()
        let gen = generation
        if q.isEmpty {
            rows = []; parts = []
            emptyText = t("type_to_search")
            return
        }
        task = Task {
            try? await Task.sleep(nanoseconds: 300_000_000) // espera a pessoa parar de digitar
            if Task.isCancelled || gen != generation { return }
            loading = true
            let p = await everywhere(q, first: first, full: false)
            if gen != generation { return }
            parts = p
            top = [SearchRow(kind: .searchAll, title: q)]
            loading = false
            render(q, full: false)
            if let base = parts.first(where: { !$0.src.mod }), !base.failed, base.rows.count < 3, Lang.code != "en" {
                await crossLanguage(q, gen: gen, full: false)
            }
        }
    }

    func full(_ raw: String, first: Source) {
        let q = raw.trimmingCharacters(in: .whitespaces)
        if q.isEmpty { return }
        cancel()
        let gen = generation
        loading = true
        emptyText = nil
        task = Task {
            let p = await everywhere(q, first: first, full: true)
            if gen != generation { return }
            parts = p
            top = []
            if let base = parts.first(where: { !$0.src.mod }), !base.failed, base.rows.count < 3, Lang.code != "en" {
                // poucos resultados no jogo base: tenta o nome em inglês antes de dizer "nada encontrado"
                render(q, full: false)
                await crossLanguage(q, gen: gen, full: true)
            } else {
                render(q, full: true)
            }
            if gen == generation { loading = false }
        }
    }

    /// Páginas já vistas ou favoritas (jogo base no idioma atual e mods) — usado sem internet.
    private func localMatches(_ q: String) -> [SearchRow] {
        let s = Store.shared
        var seen = Set<String>()
        return (s.favorites + s.history)
            .filter { ($0.lang == Lang.code || Sources.isMod($0.lang)) && $0.title.localizedCaseInsensitiveContains(q) }
            .filter { seen.insert($0.lang + ":" + $0.title).inserted }
            .map { e in
                let w = Sources.of(e.lang)
                let whereLabel = w.mod ? w.emoji + "  " + t("src_mod_fmt", w.short) : "⭐  " + t("src_base")
                return SearchRow(kind: .page, title: e.title, subtitle: t("saved_on_device") + "  ·  " + whereLabel,
                                 src: w.mod ? w.key : nil)
            }
    }

    /// Nome em inglês → página no idioma atual ("Parsnip" → "Chirívia"), acrescentado à seção do jogo base.
    private func crossLanguage(_ q: String, gen: Int, full: Bool) async {
        let here = Lang.code
        var pairs: [(String, String?)] = []
        func cap(_ w: Substring) -> String { w.prefix(1).uppercased() + String(w.dropFirst()) }
        let first = cap(Substring(q))
        let titled = q.split(separator: " ").map(cap).joined(separator: " ")
        let candidates = first == titled ? [first] : [first, titled]
        for c in candidates {
            if gen != generation { return }
            if let local = try? await WikiApi.fromEnglish(c), !local.isEmpty {
                pairs = local.map { (c, $0) }
                break
            }
        }
        if pairs.isEmpty, let en = try? await WikiApi.prefix(q, src: Sources.base("en"), limit: 3).map(\.title),
           let tr = try? await WikiApi.translateMany(en, from: "en", to: here) {
            pairs = tr
        }
        if gen != generation || Lang.code != here { return }
        if let bi = parts.firstIndex(where: { !$0.src.mod }) {
            var seen = Set(parts[bi].rows.map { $0.title.lowercased() })
            for (en, tr) in pairs {
                if let tr {
                    if seen.insert(tr.lowercased()).inserted {
                        parts[bi].rows.append(SearchRow(kind: .page, title: tr, subtitle: t("did_you_mean_fmt", en)))
                    }
                } else if seen.insert(en.lowercased()).inserted {
                    parts[bi].rows.append(SearchRow(kind: .english, title: en, subtitle: t("only_in_english")))
                }
            }
        }
        render(q, full: full)
    }
}

struct SearchResultsView: View {
    @ObservedObject var search: SearchModel
    let query: String
    @EnvironmentObject var model: AppModel
    @State private var english: String?

    var body: some View {
        Group {
            if let text = search.emptyText, search.rows.isEmpty, !search.loading {
                EmptyState(title: text, systemImage: "magnifyingglass")
            } else {
                List(search.rows) { r in
                    if r.kind == .header {
                        // cabeçalho da seção: de qual wiki são os resultados abaixo
                        Text(r.title).font(.pixel(15, relativeTo: .subheadline)).foregroundStyle(Color.accentColor)
                            .accessibilityAddTraits(.isHeader)
                            .listRowBackground(Color.clear)
                    } else {
                        Button { tap(r) } label: { row(r) }
                            .disabled(r.kind == .info)
                    }
                }
                .listStyle(.plain)
                .overlay(alignment: .top) {
                    if search.loading { ProgressView().padding(.top, 12) }
                }
            }
        }
        .background(Color.pageBackground)
        .alert(english ?? "", isPresented: Binding(get: { english != nil }, set: { if !$0 { english = nil } })) {
            if let en = Lang.wiki("en"), let title = english {
                Button(t("open_in_fmt", en.flag + " " + en.name)) {
                    model.searchActive = false
                    model.setLang("en")
                    model.push(.article(title))
                }
            }
            Button(t("cancel"), role: .cancel) {}
        } message: {
            Text(t("not_in_lang_fmt", Lang.current.name, "English"))
        }
    }

    @ViewBuilder private func row(_ r: SearchRow) -> some View {
        switch r.kind {
        case .searchAll:
            Label(t("search_all_fmt", r.title).replacingOccurrences(of: "🔎  ", with: ""), systemImage: "magnifyingglass")
                .foregroundStyle(Color.accentColor)
        default:
            VStack(alignment: .leading, spacing: 2) {
                Text(r.title).foregroundStyle(r.kind == .info ? .secondary : .primary)
                if let s = r.subtitle, !s.isEmpty {
                    Text(s).font(.footnote).foregroundStyle(.secondary).lineLimit(2)
                }
            }
        }
    }

    private func tap(_ r: SearchRow) {
        switch r.kind {
        case .searchAll: search.full(r.title, first: model.homeSource)
        case .page: model.push(.article(r.title, src: r.src))
        case .english: english = r.title
        case .info, .header: break
        }
    }
}
