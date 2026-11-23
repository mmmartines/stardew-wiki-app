import SwiftUI

/// Três abas como no Android (Início, Favoritos, Histórico), cada uma com sua pilha de navegação.
struct RootView: View {
    @EnvironmentObject var model: AppModel
    @EnvironmentObject var net: Net

    var body: some View {
        TabView(selection: $model.tab) {
            NavigationStack(path: $model.homePath) {
                HomeScreen().routes()
            }
            .tabItem { Label(t("tab_home"), systemImage: "house.fill") }
            .tag(AppTab.home)

            NavigationStack(path: $model.favPath) {
                EntriesScreen(favorites: true).routes()
            }
            .tabItem { Label(t("tab_favorites"), systemImage: "star.fill") }
            .tag(AppTab.favorites)

            NavigationStack(path: $model.histPath) {
                EntriesScreen(favorites: false).routes()
            }
            .tabItem { Label(t("tab_history"), systemImage: "clock.arrow.circlepath") }
            .tag(AppTab.history)
        }
        .tint(Color.accentColor) // marrom no claro, dourado no escuro (legível nos dois)
        .sheet(isPresented: $model.showLanguage) {
            LanguageSheet(required: model.firstRun)
                .interactiveDismissDisabled(model.firstRun) // na primeira abertura a escolha é obrigatória
        }
        .fullScreenCover(isPresented: $model.showFan) { FanScreen() }
        .overlay(alignment: .bottom) {
            if let text = model.toast {
                Text(text)
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(.white)
                    .padding(.horizontal, 16).padding(.vertical, 10)
                    .background(Capsule().fill(Color.black.opacity(0.82)))
                    .padding(.bottom, 64)
                    .transition(.opacity.combined(with: .move(edge: .bottom)))
                    .accessibilityAddTraits(.updatesFrequently)
            }
        }
        .animation(.easeInOut(duration: 0.2), value: model.toast)
        .onValueChange(net.online) { online in
            if online { model.offlineDismissed = false }
        }
        .onAppear(perform: screenshotMode)
    }

    /// Só para os prints automáticos do GitHub (argumentos de inicialização). Sem efeito no uso normal.
    private func screenshotMode() {
        guard let screen = UserDefaults.standard.string(forKey: "screen") else { return }
        model.showLanguage = false
        model.firstRun = false
        let page = UserDefaults.standard.string(forKey: "page") ?? "Abigail"
        switch screen {
        case "search": model.searchActive = true
        case "article": model.homePath = [.article(page)]
        case "favorites": Store.shared.toggleFavorite(page); model.tab = .favorites
        case "history": Store.shared.addHistory(page); model.tab = .history
        case "lang": model.showLanguage = true
        case "fan": model.showFan = true
        case "credits": model.homePath = [.credits]
        case "list": model.homePath = [.list(.allCategories)]
        default: break
        }
    }
}

extension View {
    /// Destinos comuns às três abas.
    func routes() -> some View {
        navigationDestination(for: Route.self) { r in
            switch r {
            case .article(let title, let anchor): ArticleScreen(requested: title, anchor: anchor)
            case .list(let mode, let cat): WikiListScreen(mode: mode, category: cat)
            case .credits: CreditsScreen()
            }
        }
    }
}
