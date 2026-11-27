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
        .sheet(isPresented: $model.showMyMods) { MyModsSheet() }
        // pergunta uma vez quais mods a pessoa joga (depois da primeira abertura ou ao atualizar o app)
        .onValueChange(model.showFan) { showing in
            if !showing && !Sources.myModsAsked && UserDefaults.standard.string(forKey: "screen") == nil {
                Task { try? await Task.sleep(nanoseconds: 600_000_000); model.showMyMods = true }
            }
        }
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
        .preferredColorScheme(model.tourDark ? .dark : nil)
        .onAppear {
            screenshotMode()
            if Lang.chosen && !model.firstRun && !Sources.myModsAsked && UserDefaults.standard.string(forKey: "screen") == nil {
                Task { try? await Task.sleep(nanoseconds: 800_000_000); model.showMyMods = true }
            }
        }
    }

    /// Só para os prints automáticos do GitHub (argumentos de inicialização). Sem efeito no uso normal.
    private func screenshotMode() {
        guard let screen = UserDefaults.standard.string(forKey: "screen") else { return }
        model.showLanguage = false
        model.firstRun = false
        let page = UserDefaults.standard.string(forKey: "page") ?? "Abigail"
        let src = UserDefaults.standard.string(forKey: "src")
        if let src, Sources.isMod(src) || src == Sources.baseChoice { model.homeChoice = src }
        switch screen {
        case "search": model.searchActive = true
        case "article": model.homePath = [.article(page, src: Sources.isMod(src) ? src : nil)]
        case "favorites": Store.shared.toggleFavorite(page); model.tab = .favorites
        case "history": Store.shared.addHistory(page); model.tab = .history
        case "lang": model.showLanguage = true
        case "fan": model.showFan = true
        case "credits": model.homePath = [.credits]
        case "list": model.homePath = [.list(.allCategories)]
        case "tour": Task { await tour() }
        default: break
        }
    }

    /// Modo "tour" dos testes no Appetize: passa sozinho pelas telas principais, 8 s em cada,
    /// para uma única sessão curta registrar todas (o plano grátis tem sessões de 3 minutos).
    private func tour() async {
        func step(_ change: () -> Void) async {
            try? await Task.sleep(nanoseconds: 8_000_000_000)
            change()
        }
        // 0: Início (já está na tela)
        await step { model.presetQuery = "Parsnip"; model.searchActive = true }          // 1: busca pelo nome em inglês
        await step { model.searchActive = false; model.homePath = [.article("Abigail")] } // 2: artigo
        await step {
            if !Store.shared.isFavorite("Abigail") { Store.shared.toggleFavorite("Abigail") }
            model.tab = .favorites                                                            // 3: favoritos
        }
        await step { model.tab = .home; model.homePath = [.list(.allCategories)] }        // 4: categorias
        await step { model.homePath = []; model.tourOffline = true }                       // 5: sem internet
        await step { model.tourOffline = false; model.tourDark = true }                    // 6: Início no escuro
        await step { model.homePath = [.article("Chirívia")] }                             // 7: artigo no escuro
    }
}

extension View {
    /// Destinos comuns às três abas.
    func routes() -> some View {
        navigationDestination(for: Route.self) { r in
            switch r {
            case .article(let title, let anchor, let src): ArticleScreen(requested: title, anchor: anchor, src: src)
            case .list(let mode, let cat, let src): WikiListScreen(mode: mode, category: cat, src: src)
            case .credits: CreditsScreen()
            }
        }
    }
}
