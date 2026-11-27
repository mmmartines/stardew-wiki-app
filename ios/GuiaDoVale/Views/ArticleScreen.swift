import SwiftUI

/// Carrega a página: do aparelho se estiver salva e recente, senão da wiki (guardando para ler offline).
@MainActor
final class ArticleModel: ObservableObject {
    enum State { case loading, page(Page), error(String, String?) }

    @Published var state: State = .loading
    let requested: String
    /// Wiki da página: oficial (idioma) ou um mod.
    let src: Source
    private(set) var current: String
    private var triedFallback = false

    init(requested: String, src: Source) {
        self.requested = requested
        self.src = src
        current = requested
    }

    var page: Page? { if case .page(let p) = state { return p }; return nil }

    func load(force: Bool = false) async -> Bool {
        let store = Store.shared
        let cached = store.getPage(current, w: src.key)
        let fresh = cached.map { Date().timeIntervalSince1970 - $0.time < 7 * 24 * 3600 } ?? false
        if !force, let cached, fresh { show(cached.page); return false }
        if !Net.isOnline && cached == nil {
            // já sabemos que está offline: avisa na hora em vez de deixar a tela em branco
            state = .error(t("err_offline_page"), nil)
            return false
        }
        state = .loading
        do {
            let p = try await WikiApi.parse(current, src: src)
            store.putPage(requested: requested, p, w: src.key)
            show(p)
            return force
        } catch is MissingPageError where !triedFallback {
            return await fallback()
        } catch {
            if let cached {
                show(cached.page)
                AppToast.show(t("offline_showing_saved"))
            } else if error is MissingPageError {
                state = .error(t("page_not_found_fmt", current), nil)
            } else {
                state = .error(error.friendly(page: true), String(describing: error))
            }
            return false
        }
    }

    /// Título não existe com esse nome: abre o melhor resultado da busca.
    private func fallback() async -> Bool {
        triedFallback = true
        if let first = try? await WikiApi.search(current, src: src).first {
            current = first.title
            return await load()
        }
        state = .error(t("page_not_found_fmt", current), nil)
        return false
    }

    private func show(_ p: Page) {
        current = p.title
        state = .page(p)
        Store.shared.addHistory(p.title, w: src.key)
    }
}

/// Avisos curtos ("Página atualizada") a partir de qualquer lugar.
@MainActor
enum AppToast {
    static weak var model: AppModel?
    static func show(_ text: String) { model?.show(toast: text) }
}

struct ArticleScreen: View {
    let requested: String
    let anchor: String?
    let src: Source

    @EnvironmentObject var model: AppModel
    @EnvironmentObject var store: Store
    @EnvironmentObject var net: Net
    @Environment(\.colorScheme) private var scheme
    @Environment(\.openURL) private var openURL
    @StateObject private var article: ArticleModel
    @StateObject private var web = WebController()
    @State private var showDetails = false
    @State private var mtRequest: MTRequest?

    init(requested: String, anchor: String?, src: String? = nil) {
        let s = Sources.of(src)
        self.requested = requested
        self.anchor = anchor
        self.src = s
        _article = StateObject(wrappedValue: ArticleModel(requested: requested, src: s))
    }

    private var title: String { article.page?.title ?? requested }

    var body: some View {
        ZStack {
            Color.pageBackground.ignoresSafeArea()
            switch article.state {
            case .loading:
                ProgressView().controlSize(.large)
            case .error(let msg, let detail):
                errorView(msg, detail)
            case .page(let p):
                WebView(html: HTML.article(p, dark: scheme == .dark, src: src),
                        baseURL: WikiApi.pageURL(p.title, src: src),
                        key: p.title + "|\(scheme == .dark)|\(p.html.count)",
                        controller: web,
                        onLink: handleLink,
                        onLoaded: {
                            web.setTextZoom(store.textZoom)
                            if let anchor { web.scrollTo(anchor: anchor) }
                            translateTerms(p)
                        })
                    .ignoresSafeArea(edges: .bottom)
            }
        }
        .autoTranslate(mtRequest) { out in
            if let out, let data = try? JSONSerialization.data(withJSONObject: out), let json = String(data: data, encoding: .utf8) {
                web.js("window.__mtApply&&__mtApply(" + json + ")")
            } else {
                web.js("window.__mtFail&&__mtFail()")
            }
        }
        .offlineBanner()
        .navigationTitle(title)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            // deixa claro de onde é a página: jogo base ou mod (e qual)
            ToolbarItem(placement: .principal) {
                VStack(spacing: 0) {
                    Text(title).font(.headline).lineLimit(1)
                    Text(src.label).font(.caption2).foregroundStyle(.secondary).lineLimit(1)
                }
                .accessibilityElement(children: .combine)
            }
        }
        .toolbar { toolbar }
        .task {
            AppToast.model = model
            if article.page == nil { _ = await article.load() }
        }
        .onValueChange(net.online) { online in
            if online, case .error = article.state { Task { _ = await article.load() } }
        }
    }

    /// Página de mod em outro idioma: busca na wiki oficial os nomes do jogo que ainda faltam no glossário.
    private func translateTerms(_ p: Page) {
        guard Glossary.applies(src) else { return }
        let lang = Lang.code
        let titles = Glossary.titles(p.html)
        let missing = Net.isOnline ? Glossary.missing(lang, titles) : []
        let needTerms = Net.isOnline && !Glossary.hasTerms(lang)
        Task {
            if !missing.isEmpty || needTerms {
                // termos da wiki (uma vez por idioma) e nomes do jogo citados na página
                if needTerms { await Glossary.fetchTerms(lang) }
                if !missing.isEmpty { await Glossary.fetch(lang, missing) }
                guard article.page?.title == p.title, Lang.code == lang else { return }
                let base = (try? JSONSerialization.data(withJSONObject: [Lang.base()])).flatMap { String(data: $0, encoding: .utf8) } ?? "[\"\"]"
                web.js("window.__gloss&&__gloss(" + Glossary.knownJSON(lang, titles) + "," + base + "[0]);" +
                       "window.__setTerms&&__setTerms(" + Glossary.termsJSON(lang) + ")")
            }
            // depois dos nomes do jogo (que ficam como na wiki oficial), o resto do texto: tradução automática
            guard AutoTranslateSupport.available, article.page?.title == p.title else { return }
            let raw = await web.evaluate("window.__mtCollect?__mtCollect():[]")
            let texts = (raw as? [Any])?.map { "\($0)" } ?? []
            if !texts.isEmpty { mtRequest = MTRequest(texts: texts) }
        }
    }

    // MARK: Barra superior

    @ToolbarContentBuilder private var toolbar: some ToolbarContent {
        ToolbarItemGroup(placement: .navigationBarTrailing) {
            if let p = article.page {
                let fav = store.isFavorite(p.title, w: src.key)
                Button {
                    let now = store.toggleFavorite(p.title, w: src.key)
                    model.show(toast: t(now ? "fav_added" : "fav_removed"))
                } label: {
                    Image(systemName: fav ? "star.fill" : "star")
                }
                .accessibilityLabel(Text(t(fav ? "fav_remove" : "fav_add")))

                if !p.sections.isEmpty {
                    Menu {
                        ForEach(Array(p.sections.enumerated()), id: \.offset) { _, s in
                            Button(String(repeating: "    ", count: max(0, s.level - 1)) + s.line) { web.scrollTo(anchor: s.anchor) }
                        }
                    } label: {
                        Image(systemName: "list.bullet")
                    }
                    .accessibilityLabel(Text(t("sections")))
                }
            }
            Menu {
                if let p = article.page {
                    Button { web.showFind() } label: { Label(t("find_in_page"), systemImage: "doc.text.magnifyingglass") }
                    Menu {
                        Picker(t("text_size"), selection: Binding(get: { store.textZoom }, set: { z in
                            store.textZoom = z
                            web.setTextZoom(z)
                        })) {
                            Text(t("size_small")).tag(85)
                            Text(t("size_normal")).tag(100)
                            Text(t("size_large")).tag(115)
                            Text(t("size_xlarge")).tag(135)
                        }
                    } label: { Label(t("text_size"), systemImage: "textformat.size") }
                    Button {
                        Task { if await article.load(force: true) { model.show(toast: t("page_updated")) } }
                    } label: { Label(t("refresh"), systemImage: "arrow.clockwise") }
                    ShareLink(item: WikiApi.pageURL(p.title, src: src), subject: Text(p.title)) {
                        Label(t("share"), systemImage: "square.and.arrow.up")
                    }
                    Divider()
                    Button { openURL(WikiApi.pageURL(p.title, src: src)) } label: { Label(t("view_original"), systemImage: "safari") }
                    Button { openURL(WikiApi.historyURL(p.title, src: src)) } label: { Label(t("page_authors"), systemImage: "person.2") }
                }
                Button { model.tab = .home; model.homePath = [] } label: { Label(t("tab_home"), systemImage: "house") }
            } label: {
                Image(systemName: "ellipsis.circle")
            }
        }
    }

    // MARK: Erro

    private func errorView(_ msg: String, _ detail: String?) -> some View {
        VStack(spacing: 16) {
            Image(systemName: net.online ? "exclamationmark.triangle" : "wifi.slash")
                .font(.largeTitle).foregroundStyle(Color.accentColor)
            Text(msg).multilineTextAlignment(.center)
            if let detail {
                if showDetails {
                    Text(t("details_fmt", detail)).font(.footnote).foregroundStyle(.secondary).textSelection(.enabled)
                } else {
                    Button(t("show_details")) { showDetails = true }.font(.footnote)
                }
            }
            Button(t("retry")) { Task { _ = await article.load(force: true) } }
                .buttonStyle(.borderedProminent).tint(Color.wood)
        }
        .padding(28)
        .frame(maxWidth: 520)
    }

    // MARK: Links

    private func handleLink(_ url: URL) {
        guard let scheme = url.scheme, scheme == "http" || scheme == "https" else { return }
        let comps = URLComponents(url: url, resolvingAgainstBaseURL: false)
        func q(_ name: String) -> String? { comps?.queryItems?.first { $0.name == name }?.value }
        let host = (url.host ?? "").replacingOccurrences(of: "www.", with: "")

        if q("app_ext") != nil {
            if var c = comps {
                let items = (c.queryItems ?? []).filter { $0.name != "app_ext" }
                c.queryItems = items.isEmpty ? nil : items
                if let u = c.url { openURL(u) }
            }
            return
        }
        if q("action") == "history" { openURL(url); return }
        if host != src.host {
            // link para outra wiki conhecida (ex.: página de mod que cita a wiki oficial): abre aqui mesmo
            if let other = Sources.byHost(host), q("redlink") == nil, let tt = Self.title(from: url),
               !Self.hasNamespace(tt, Self.nsCategory), !Self.hasNamespace(tt, Self.nsFile), !Self.hasNamespace(tt, Self.nsExternal) {
                model.push(.article(tt, anchor: url.fragment, src: other.key))
            } else {
                openURL(url)
            }
            return
        }
        if q("redlink") != nil { model.show(toast: t("page_missing_on_wiki")); return }
        if q("action") == "edit" || q("action") == "submit" || q("veaction") != nil {
            model.show(toast: t("read_only")) // o app nunca edita a wiki
            return
        }
        guard let target = Self.title(from: url) else {
            if let f = url.fragment { web.scrollTo(anchor: f) }
            return
        }
        if target.caseInsensitiveCompare(title) == .orderedSame {
            if let f = url.fragment { web.scrollTo(anchor: f) }
            return
        }
        if Self.hasNamespace(target, Self.nsCategory) {
            model.push(.list(.category, category: target, src: src.key))
        } else if Self.hasNamespace(target, Self.nsFile) {
            // imagem: a ampliação é feita pela própria página
        } else if Self.hasNamespace(target, Self.nsExternal) {
            openURL(url)
        } else {
            model.push(.article(target, anchor: url.fragment, src: src.key))
        }
    }

    /// Converte um link da wiki no título da página (ou nil se não for página).
    static func title(from url: URL) -> String? {
        let path = url.path
        var raw: String?
        if path.hasPrefix("/mediawiki/index.php") || path == "/index.php" {
            raw = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems?.first { $0.name == "title" }?.value
        } else if path.hasPrefix("/wiki/") {
            raw = String(path.dropFirst(6)) // wikis dos mods (wiki.gg)
        } else {
            raw = String(path.dropFirst())
        }
        guard let r = raw, !r.isEmpty, !r.hasPrefix("mediawiki/") else { return nil }
        return r.replacingOccurrences(of: "_", with: " ")
    }

    private static func hasNamespace(_ t: String, _ ns: [String]) -> Bool {
        ns.contains { t.lowercased().hasPrefix($0.lowercased() + ":") }
    }

    // nomes dos espaços de nomes nas 12 wikis (os mesmos do Android)
    static let nsCategory = ["Category", "Categoria", "Kategorie", "Catégorie", "Categoría", "Kategória", "Kategori",
                             "Категория", "カテゴリ", "분류", "分类"]
    static let nsFile = ["File", "Image", "Arquivo", "Ficheiro", "Imagem", "Datei", "Bild", "Fichier", "Archivo", "Fájl",
                         "Dosya", "Файл", "ファイル", "画像", "파일", "文件"]
    static let nsExternal = ["Special", "Especial", "Spezial", "Spécial", "Speciale", "Speciális", "Özel", "Служебная",
                             "特別", "특수", "特殊", "User", "Usuário", "Utilizador", "Benutzer", "Benutzerin", "Utilisateur",
                             "Usuario", "Utente", "Szerkesztő", "Kullanıcı", "Участник", "利用者", "사용자", "用户", "Template",
                             "Predefinição", "Vorlage", "Modèle", "Plantilla", "Sablon", "Şablon", "Шаблон", "テンプレート", "틀",
                             "模板", "Talk", "Discussão", "Diskussion", "Discussion", "Discusión", "Discussione", "Vita",
                             "Tartışma", "Обсуждение", "ノート", "토론", "讨论", "MediaWiki", "Module", "Módulo", "Modul", "Модуль"]
}
