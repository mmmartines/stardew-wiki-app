import SwiftUI

/// Destinos de navegação dentro de cada aba.
/// `src` = wiki da página (código do mod, ou idioma da oficial); nil = oficial no idioma atual.
enum Route: Hashable {
    case article(String, anchor: String? = nil, src: String? = nil)
    case list(ListMode, category: String? = nil, src: String? = nil)
    case credits
}

enum ListMode: Hashable {
    case category, allPages, allCategories, saved
}

enum AppTab: Hashable {
    case home, favorites, history
}

/// Estado geral do app: idioma, aba, pilhas de navegação, avisos e primeira abertura.
@MainActor
final class AppModel: ObservableObject {
    /// Idioma atual (a interface toda é redesenhada quando muda).
    @Published private(set) var lang = Lang.code
    @Published var tab: AppTab = .home
    @Published var homePath: [Route] = []
    @Published var favPath: [Route] = []
    @Published var histPath: [Route] = []

    @Published var showLanguage = false
    @Published var firstRun = !Lang.chosen
    @Published var showFan = false
    @Published var searchActive = false
    /// A pessoa escolheu "Ver páginas salvas" e fechou a tela de sem internet (volta quando a conexão cair de novo).
    @Published var offlineDismissed = false
    @Published var toast: String?
    /// Wiki escolhida nos botões da tela inicial: "base" (jogo base) ou o código do mod.
    /// O app abre na wiki marcada em "Wiki ao abrir o app".
    @Published var homeChoice = Sources.startChoice
    /// Muda quando "Meus mods" muda (para redesenhar a tela inicial).
    @Published var myModsVersion = 0
    @Published var showMyMods = false
    var homeSource: Source {
        // mod tirado de "Meus mods": mostra o jogo base
        if Sources.isMod(homeChoice) && !Sources.myMods.contains(where: { $0.key == homeChoice }) { return Sources.base() }
        return Sources.fromChoice(homeChoice)
    }

    // Só para os testes automáticos (modo "tour"): busca pronta, sem internet simulado e modo escuro.
    @Published var presetQuery: String?
    @Published var tourOffline = false
    @Published var tourDark = false

    init() {
        if firstRun { showLanguage = true }
    }

    func setLang(_ c: String) {
        Lang.set(c)
        Bridge.shared.reset()
        // páginas abertas eram do idioma anterior: volta cada aba para o começo
        homePath = []; favPath = []; histPath = []
        lang = c
        if firstRun {
            firstRun = false
            // tela "feito por um fã" depois da primeira escolha, quando a lista de idiomas já fechou
            Task {
                try? await Task.sleep(nanoseconds: 700_000_000)
                showFan = true
            }
        }
    }

    // MARK: Navegação

    func push(_ r: Route) {
        switch tab {
        case .home: homePath.append(r)
        case .favorites: favPath.append(r)
        case .history: histPath.append(r)
        }
    }

    func open(_ target: Target, in s: Source) {
        let src = s.mod ? s.key : nil
        switch target {
        // nos mods o título é o da wiki do mod (em inglês); na oficial, o do idioma atual
        case .article(let en): push(.article(s.mod ? en : Titles.of(en), src: src))
        case .allPages: push(.list(.allPages, src: src))
        case .allCategories: push(.list(.allCategories, src: src))
        case .random:
            Task {
                do { push(.article(try await WikiApi.random(src: s), src: src)) } catch { show(toast: t("no_connection")) }
            }
        }
    }

    /// Abre um favorito/histórico; se for de outro idioma, procura a mesma página no idioma atual.
    func open(_ e: Entry, askSwitch: @escaping (Entry) -> Void) {
        if Sources.isMod(e.lang) { push(.article(e.title, src: e.lang)); return }
        if e.lang == Lang.code { push(.article(e.title)); return }
        let to = Lang.code
        Task {
            if let tr = try? await WikiApi.translate(e.title, from: e.lang, to: to) {
                push(.article(tr))
            } else {
                askSwitch(e)
            }
        }
    }

    func show(toast text: String) {
        toast = text
        Task {
            try? await Task.sleep(nanoseconds: 2_200_000_000)
            if toast == text { toast = nil }
        }
    }
}

extension Font {
    /// Fonte pixelada do jogo (Pixelify Sans), acompanha o tamanho de texto do sistema.
    static func pixel(_ size: CGFloat, relativeTo style: Font.TextStyle = .body) -> Font {
        .custom("PixelifySans-Bold", size: size, relativeTo: style)
    }
}

extension Color {
    static let wood = Color("Wood")
    static let parchment = Color("Parchment")
    static let ink = Color("Ink")
    static let pageBackground = Color("PageBackground")
    static let homeBackground = Color("HomeBackground")
}
