import SwiftUI

/// Lista paginada: membros de uma categoria, todas as páginas, todas as categorias ou páginas salvas.
@MainActor
final class WikiListModel: ObservableObject {
    @Published var hits: [Hit] = []
    @Published var loading = false
    @Published var error: String?
    private(set) var done = false
    private var next: String?
    private var generation = 0

    let mode: ListMode
    let category: String

    init(mode: ListMode, category: String?) {
        self.mode = mode
        self.category = category ?? ""
    }

    /// Filtro no servidor (todas as páginas/categorias) ou local (categoria e páginas salvas).
    var localFilter: Bool { mode == .category || mode == .saved }

    func reset(prefix: String) {
        generation += 1
        hits = []; next = nil; done = false; error = nil
        Task { await more(prefix: prefix) }
    }

    func more(prefix: String) async {
        if loading || done { return }
        loading = true
        let gen = generation
        defer { if gen == generation { loading = false } }
        do {
            let chunk: Chunk
            switch mode {
            case .category: chunk = try await WikiApi.categoryMembers(category, next: next)
            case .allPages: chunk = try await WikiApi.allPages(prefix: prefix, next: next)
            case .allCategories: chunk = try await WikiApi.allCategories(prefix: prefix, next: next)
            case .saved: chunk = Chunk(items: Store.shared.savedTitles().map { Hit(title: $0) }, next: nil)
            }
            if gen != generation { return }
            hits += chunk.items
            next = chunk.next
            done = chunk.next == nil
        } catch {
            if gen != generation { return }
            self.error = error.friendly(page: false)
        }
    }
}

struct WikiListScreen: View {
    @EnvironmentObject var model: AppModel
    @EnvironmentObject var net: Net
    @StateObject private var list: WikiListModel
    @State private var filter = ""

    init(mode: ListMode, category: String?) {
        _list = StateObject(wrappedValue: WikiListModel(mode: mode, category: category))
    }

    private var title: String {
        switch list.mode {
        case .category:
            let c = list.category
            return c.contains(":") ? String(c[c.index(after: c.firstIndex(of: ":")!)...]) : c
        case .allPages: return t("cat_all_pages")
        case .allCategories: return t("cat_categories")
        case .saved: return t("offline_pages")
        }
    }

    private var shown: [Hit] {
        guard list.localFilter, !filter.isEmpty else { return list.hits }
        return list.hits.filter { $0.title.localizedCaseInsensitiveContains(filter) }
    }

    var body: some View {
        List {
            ForEach(shown) { h in
                Button { open(h) } label: { row(h) }
                    .listRowBackground(Color.parchment)
                    .onAppear {
                        if h == list.hits.last && !list.localFilter { Task { await list.more(prefix: filter) } }
                    }
            }
            if list.loading {
                HStack { Spacer(); ProgressView(); Spacer() }.listRowBackground(Color.clear)
            }
        }
        .listStyle(.insetGrouped)
        .scrollContentBackground(.hidden)
        .background(Color.pageBackground)
        .overlay {
            if !list.loading && shown.isEmpty {
                EmptyState(title: list.error ?? t(list.mode == .saved ? "offline_pages_none" : "nothing_here"),
                           systemImage: list.error == nil ? "tray" : "wifi.exclamationmark") {
                    if list.error != nil { Button(t("retry")) { list.reset(prefix: filter) } }
                }
            }
        }
        .offlineBanner()
        .navigationTitle(title)
        .navigationBarTitleDisplayMode(.inline)
        .searchable(text: $filter, placement: .navigationBarDrawer(displayMode: .always),
                    prompt: t(list.localFilter ? "filter_category" : "filter_prefix"))
        .onValueChange(filter) { f in
            if !list.localFilter {
                Task {
                    try? await Task.sleep(nanoseconds: 400_000_000)
                    if f == filter { list.reset(prefix: f) }
                }
            }
        }
        .onValueChange(net.online) { online in
            if online && list.error != nil { list.reset(prefix: filter) }
        }
        .task { if list.hits.isEmpty { await list.more(prefix: "") } }
    }

    private func row(_ h: Hit) -> some View {
        HStack {
            if h.ns == 14 { Image(systemName: "folder").foregroundStyle(Color.accentColor) }
            Text(h.ns == 14 ? (h.title.split(separator: ":", maxSplits: 1).last.map(String.init) ?? h.title) : h.title)
                .foregroundStyle(Color.ink)
        }
    }

    private func open(_ h: Hit) {
        if h.ns == 14 { model.push(.list(.category, category: h.title)) } else { model.push(.article(h.title)) }
    }
}
