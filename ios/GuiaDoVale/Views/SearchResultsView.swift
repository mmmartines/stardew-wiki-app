import SwiftUI

/// Uma linha do resultado da busca.
struct SearchRow: Identifiable, Hashable {
    enum Kind: Hashable { case searchAll, page, info, english }
    let kind: Kind
    let title: String
    var subtitle: String? = nil
    var id: String { "\(kind)|\(title)|\(subtitle ?? "")" }
}

/// Busca: sugestões enquanto digita, busca completa ao confirmar e o nome em inglês quando há poucos resultados.
@MainActor
final class SearchModel: ObservableObject {
    @Published var rows: [SearchRow] = []
    @Published var loading = false
    @Published var emptyText: String?

    private var task: Task<Void, Never>?
    private var generation = 0

    func cancel() {
        task?.cancel()
        generation += 1
        loading = false
    }

    func suggest(_ raw: String) {
        let q = raw.trimmingCharacters(in: .whitespaces)
        cancel()
        let gen = generation
        if q.isEmpty {
            rows = []
            emptyText = t("type_to_search")
            return
        }
        task = Task {
            try? await Task.sleep(nanoseconds: 300_000_000) // espera a pessoa parar de digitar
            if Task.isCancelled || gen != generation { return }
            loading = true
            var out = [SearchRow(kind: .searchAll, title: q)]
            var count = 99
            do {
                let hits = try await WikiApi.prefix(q)
                count = hits.count
                out += hits.map { SearchRow(kind: .page, title: $0.title) }
            } catch {
                if gen != generation { return }
                if error.isOffline {
                    out.append(SearchRow(kind: .info, title: t("offline_title"), subtitle: t("offline_sub")))
                } else {
                    out.append(SearchRow(kind: .info, title: "⚠️  " + t("search_failed")))
                }
                out += localMatches(q).map { SearchRow(kind: .page, title: $0, subtitle: t("saved_on_device")) }
            }
            if gen != generation { return }
            loading = false
            emptyText = nil
            rows = out
            if count < 3 && Lang.code != "en" { await crossLanguage(q, gen: gen, base: out) }
        }
    }

    func full(_ raw: String) {
        let q = raw.trimmingCharacters(in: .whitespaces)
        if q.isEmpty { return }
        cancel()
        let gen = generation
        loading = true
        emptyText = nil
        task = Task {
            do {
                let hits = try await WikiApi.search(q)
                if gen != generation { return }
                let base = hits.map { SearchRow(kind: .page, title: $0.title, subtitle: $0.snippet) }
                rows = base
                if hits.count < 3 && Lang.code != "en" {
                    // poucos resultados: tenta o nome em inglês antes de dizer "nada encontrado"
                    await crossLanguage(q, gen: gen, base: base)
                }
                loading = false
                if rows.isEmpty { emptyText = t("nothing_found_fmt", q) }
            } catch {
                if gen != generation { return }
                loading = false
                rows = localMatches(q).map { SearchRow(kind: .page, title: $0, subtitle: t("saved_on_device")) }
                if rows.isEmpty { emptyText = error.friendly(page: false) }
            }
        }
    }

    private func localMatches(_ q: String) -> [String] {
        let s = Store.shared
        var seen = Set<String>()
        return (s.favorites + s.history).filter { $0.lang == Lang.code }.map(\.title)
            .filter { $0.localizedCaseInsensitiveContains(q) && seen.insert($0).inserted }
    }

    /// Nome em inglês → página no idioma atual ("Parsnip" → "Chirívia").
    private func crossLanguage(_ q: String, gen: Int, base: [SearchRow]) async {
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
        if pairs.isEmpty, let en = try? await WikiApi.prefix(q, lang: "en", limit: 3).map(\.title),
           let tr = try? await WikiApi.translateMany(en, from: "en", to: here) {
            pairs = tr
        }
        if gen != generation || Lang.code != here { return }
        let shown = Set(base.map { $0.title.lowercased() })
        var extra: [SearchRow] = []
        var seen = Set<String>()
        for (en, tr) in pairs {
            if let tr {
                if !shown.contains(tr.lowercased()) && seen.insert(tr.lowercased()).inserted {
                    extra.append(SearchRow(kind: .page, title: tr, subtitle: t("did_you_mean_fmt", en)))
                }
            } else if seen.insert(en.lowercased()).inserted {
                extra.append(SearchRow(kind: .english, title: en, subtitle: t("only_in_english")))
            }
        }
        if !extra.isEmpty {
            emptyText = nil
            rows = base + extra
        }
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
                ContentUnavailableView {
                    Label(text, systemImage: "magnifyingglass")
                }
            } else {
                List(search.rows) { r in
                    Button { tap(r) } label: { row(r) }
                        .disabled(r.kind == .info)
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
        case .searchAll: search.full(r.title)
        case .page: model.push(.article(r.title))
        case .english: english = r.title
        case .info: break
        }
    }
}
