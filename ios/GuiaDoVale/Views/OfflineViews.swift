import SwiftUI

/// Faixa "Sem internet" logo abaixo da barra superior; empurra o conteúdo para baixo em vez de cobri-lo.
struct OfflineBanner: ViewModifier {
    let show: Bool

    func body(content: Content) -> some View {
        content.safeAreaInset(edge: .top, spacing: 0) {
            if show {
                Text(t("offline_banner"))
                    .font(.footnote.weight(.medium))
                    .foregroundStyle(Color(red: 1, green: 0.914, blue: 0.722))
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 6)
                    .background(Color.wood)
                    .accessibilityAddTraits(.isHeader)
            }
        }
    }
}

extension View {
    func offlineBanner(show: Bool) -> some View { modifier(OfflineBanner(show: show)) }

    /// Faixa padrão: aparece sempre que o aparelho estiver sem internet.
    func offlineBanner() -> some View { modifier(AutoOfflineBanner()) }
}

private struct AutoOfflineBanner: ViewModifier {
    @EnvironmentObject var net: Net
    func body(content: Content) -> some View { content.offlineBanner(show: !net.online) }
}

/// Tela cheia de sem internet na Início: some sozinha quando a conexão volta.
struct OfflineScreen: View {
    @EnvironmentObject var model: AppModel
    @EnvironmentObject var store: Store

    private var recents: [String] { store.historyHere.filter { store.isSaved($0) }.prefix(4).map { $0 } }

    var body: some View {
        ScrollView {
            VStack(spacing: 14) {
                Image("Chicken")
                    .interpolation(.none)
                    .resizable()
                    .frame(width: 72, height: 72)
                    .accessibilityHidden(true)
                Text(t("offline_screen_title"))
                    .font(.pixel(22, relativeTo: .title2))
                    .multilineTextAlignment(.center)
                    .accessibilityAddTraits(.isHeader)
                Text(t("offline_screen_text"))
                    .multilineTextAlignment(.center)
                HStack(spacing: 10) {
                    ProgressView()
                    Text(t("offline_waiting")).font(.pixel(15)).foregroundStyle(.secondary)
                }
                .padding(.top, 4)

                if !recents.isEmpty {
                    Text(t("offline_recent"))
                        .font(.pixel(16, relativeTo: .headline))
                        .padding(.top, 8)
                        .accessibilityAddTraits(.isHeader)
                    ForEach(recents, id: \.self) { title in
                        Button {
                            model.offlineDismissed = true
                            model.push(.article(title))
                        } label: {
                            HStack {
                                Text(title).foregroundStyle(Color.ink)
                                Spacer()
                                Image(systemName: "chevron.right").foregroundStyle(.secondary)
                            }
                            .padding(.horizontal, 14).frame(minHeight: 48)
                            .background(RoundedRectangle(cornerRadius: 6).fill(Color.pageBackground))
                            .overlay(RoundedRectangle(cornerRadius: 6).stroke(Color.wood.opacity(0.5), lineWidth: 1))
                        }
                    }
                }

                let hasSaved = !recents.isEmpty || store.cacheCount > 0
                Button {
                    model.offlineDismissed = true
                    if hasSaved { model.push(.list(.saved)) } else { model.tab = .favorites }
                } label: {
                    Text(t(hasSaved ? "offline_see_saved" : "offline_see_favorites"))
                        .font(.pixel(17, relativeTo: .body))
                        .frame(maxWidth: .infinity, minHeight: 44)
                }
                .buttonStyle(.borderedProminent)
                .tint(Color.wood)
                .padding(.top, 6)
            }
            .foregroundStyle(Color.ink)
            .padding(24)
            .background(RoundedRectangle(cornerRadius: 8).fill(Color.parchment))
            .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.wood, lineWidth: 4))
            .frame(maxWidth: 560)
            .padding(20)
            .frame(maxWidth: .infinity, minHeight: 0)
        }
        .background(Color.homeBackground.ignoresSafeArea())
        .onAppear { UIAccessibility.post(notification: .announcement, argument: t("offline_screen_title")) }
    }
}
