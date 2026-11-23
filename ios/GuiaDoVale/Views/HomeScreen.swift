import SwiftUI

/// Tela inicial: a mesma cena de fazenda do Android (HTML), com a busca nativa do iOS.
struct HomeScreen: View {
    @EnvironmentObject var model: AppModel
    @EnvironmentObject var store: Store
    @EnvironmentObject var net: Net
    @Environment(\.colorScheme) private var scheme
    @StateObject private var web = WebController()
    @StateObject private var search = SearchModel()
    @State private var query = UserDefaults.standard.string(forKey: "q") ?? ""
    @State private var confirmClear = false
    @State private var cacheDialog = false

    private var recent: [String] { Array(store.historyHere.prefix(6)) }

    var body: some View {
        ZStack {
            Color.homeBackground.ignoresSafeArea()
            WebView(html: HTML.home(dark: scheme == .dark, recent: recent),
                    baseURL: URL(string: WikiApi.base + "/"),
                    key: "\(model.lang)|\(scheme == .dark)",
                    controller: web,
                    onMessage: handle)
                .opacity(model.searchActive ? 0 : 1)
            if model.searchActive {
                SearchResultsView(search: search, query: query)
            }
            if !net.online && !model.offlineDismissed && !model.searchActive {
                OfflineScreen()
            }
            SearchStateSync(active: $model.searchActive) // iOS 16
        }
        .offlineBanner(show: !net.online && (model.offlineDismissed || model.searchActive))
        .searchableCompat(text: $query, isPresented: $model.searchActive, prompt: t("search_hint"))
        .autocorrectionDisabled()
        .textInputAutocapitalization(.never)
        .onSubmit(of: .search) { search.full(query) }
        .onValueChange(query) { q in search.suggest(q) }
        .onValueChange(model.searchActive) { active in if !active { search.cancel() } }
        .onValueChange(store.history) { _ in updateRecent() }
        .onAppear { if !query.isEmpty { search.suggest(query) } }
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .principal) {
                VStack(spacing: 0) {
                    Text(t("app_name")).font(.pixel(19, relativeTo: .headline))
                    Text(t("toolbar_subtitle")).font(.caption2).foregroundStyle(.secondary)
                }
                .accessibilityElement(children: .combine)
            }
            ToolbarItemGroup(placement: .navigationBarTrailing) {
                LanguageButton()
                Menu {
                    Button(role: .destructive) { confirmClear = true } label: {
                        Label(t("clear_history"), systemImage: "trash")
                    }
                    Button { cacheDialog = true } label: { Label(t("offline_pages"), systemImage: "arrow.down.circle") }
                    Divider()
                    Button { model.showFan = true } label: { Label(t("fan_menu"), systemImage: "heart") }
                    Button { model.push(.credits) } label: { Label(t("about"), systemImage: "info.circle") }
                } label: {
                    Image(systemName: "ellipsis.circle").accessibilityLabel(Text(t("about")))
                }
            }
        }
        .confirmationDialog(t("clear_history_q"), isPresented: $confirmClear, titleVisibility: .visible) {
            Button(t("delete"), role: .destructive) { store.clearHistory() }
            Button(t("cancel"), role: .cancel) {}
        }
        .alert(t("offline_pages"), isPresented: $cacheDialog) {
            if store.cacheCount > 0 {
                Button(t("view_list")) { model.push(.list(.saved)) }
                Button(t("delete"), role: .destructive) {
                    store.clearCache()
                    model.show(toast: t("cache_cleared"))
                }
                Button(t("cancel"), role: .cancel) {}
            } else {
                Button(t("ok"), role: .cancel) {}
            }
        } message: {
            if store.cacheCount > 0 {
                Text(t("offline_pages_fmt", store.cacheCount, Double(store.cacheBytes) / 1_048_576))
            } else {
                Text(t("offline_pages_none"))
            }
        }
    }

    /// Chamadas vindas da página (os mesmos nomes usados no Android).
    private func handle(_ action: String, _ i: Int) {
        switch action {
        case "open": if Categories.all.indices.contains(i) { model.open(Categories.all[i].target) }
        case "recent": if recent.indices.contains(i) { model.push(.article(recent[i])) }
        case "search": model.searchActive = true
        case "credits": model.push(.credits)
        default: break
        }
    }

    /// Atualiza só os "Vistos recentemente", sem recarregar a página nem perder a rolagem.
    private func updateRecent() {
        let h = HTML.recentHTML(recent)
        let q = (try? JSONSerialization.data(withJSONObject: [h])).flatMap { String(data: $0, encoding: .utf8) } ?? "[\"\"]"
        web.js("window.__recent && __recent(\(q)[0])")
    }
}

/// Bandeira do idioma da wiki na barra superior; abre a lista de idiomas.
struct LanguageButton: View {
    @EnvironmentObject var model: AppModel

    var body: some View {
        Button { model.showLanguage = true } label: {
            Text(Lang.current.flag).font(.title3)
                .frame(minWidth: 32, minHeight: 32)
        }
        .accessibilityLabel(Text(t("language") + ": " + Lang.current.name))
    }
}
