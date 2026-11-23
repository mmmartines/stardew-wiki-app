import SwiftUI

/// Escolha do idioma da wiki (bandeira + nome). Na primeira abertura é obrigatória.
struct LanguageSheet: View {
    let required: Bool
    @EnvironmentObject var model: AppModel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List(Lang.all) { w in
                Button {
                    let changed = w.code != Lang.code || required
                    dismiss()
                    if changed { model.setLang(w.code) }
                } label: {
                    HStack(spacing: 14) {
                        Text(w.flag).font(.title2)
                        Text(w.name).font(.pixel(18)).foregroundStyle(Color.ink)
                        Spacer()
                        if w.code == Lang.code && !required {
                            Image(systemName: "checkmark").foregroundStyle(Color.wood).fontWeight(.semibold)
                        }
                    }
                    .frame(minHeight: 44)
                }
                .listRowBackground(Color.parchment)
                .accessibilityAddTraits(w.code == Lang.code ? .isSelected : [])
            }
            .scrollContentBackground(.hidden)
            .background(Color.pageBackground)
            .navigationTitle(t("choose_language"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                if !required {
                    ToolbarItem(placement: .cancellationAction) { Button(t("cancel")) { dismiss() } }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }
}

/// "Feito por um fã, sem fins lucrativos" — mesma página do Android, depois da primeira escolha de idioma.
struct FanScreen: View {
    @EnvironmentObject var model: AppModel
    @Environment(\.dismiss) private var dismiss
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        WebView(html: HTML.fan(dark: scheme == .dark), baseURL: URL(string: WikiApi.base + "/"),
                key: "fan|\(model.lang)|\(scheme == .dark)",
                onMessage: { action, _ in
                    switch action {
                    case "done": dismiss()
                    case "credits":
                        dismiss()
                        model.tab = .home
                        model.homePath = [.credits]
                    default: break
                    }
                })
            .background(Color.pageBackground.ignoresSafeArea())
    }
}

/// Sobre e créditos: Eric Barone/ConcernedApe, contribuidores da wiki, licença e o app.
struct CreditsScreen: View {
    @Environment(\.colorScheme) private var scheme
    @Environment(\.openURL) private var openURL
    @EnvironmentObject var model: AppModel

    var body: some View {
        WebView(html: HTML.credits(dark: scheme == .dark), baseURL: nil,
                key: "credits|\(model.lang)|\(scheme == .dark)",
                onLink: { openURL($0) })
            .background(Color.pageBackground.ignoresSafeArea())
            .navigationTitle(t("credits_title"))
            .navigationBarTitleDisplayMode(.inline)
    }
}
