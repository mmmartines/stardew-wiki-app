import SwiftUI
import UIKit

// Compatibilidade com o iOS 16 (iPhone 8, 8 Plus e X). Onde o iOS 17 tem um recurso pronto,
// usamos o do sistema; no iOS 16, uma versão equivalente feita aqui.

/// Tela de "lista vazia" / aviso (no iOS 17+ é a ContentUnavailableView do sistema).
struct EmptyState<Actions: View>: View {
    let title: String
    let systemImage: String
    var message: String? = nil
    @ViewBuilder var actions: () -> Actions

    var body: some View {
        if #available(iOS 17, *) {
            ContentUnavailableView {
                Label(title, systemImage: systemImage)
            } description: {
                if let message { Text(message) }
            } actions: {
                actions()
            }
        } else {
            VStack(spacing: 12) {
                Image(systemName: systemImage).font(.system(size: 44)).foregroundStyle(.secondary)
                Text(title).font(.title3.weight(.semibold)).multilineTextAlignment(.center)
                if let message { Text(message).font(.callout).foregroundStyle(.secondary).multilineTextAlignment(.center) }
                actions()
            }
            .padding(32)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }
}

extension EmptyState where Actions == EmptyView {
    init(title: String, systemImage: String, message: String? = nil) {
        self.init(title: title, systemImage: systemImage, message: message) { EmptyView() }
    }
}

extension View {
    /// `onChange(of:)` que funciona igual no iOS 16 e no 17+ (a assinatura mudou no 17).
    @ViewBuilder
    func onValueChange<V: Equatable>(_ value: V, perform: @escaping (V) -> Void) -> some View {
        if #available(iOS 17, *) {
            onChange(of: value) { _, new in perform(new) }
        } else {
            onChange(of: value, perform: perform)
        }
    }

    /// Busca que também abre por código (tocar no campo de busca da tela inicial).
    /// iOS 17+: `searchable(isPresented:)`. iOS 16: acompanha o estado pela própria busca do sistema.
    @ViewBuilder
    func searchableCompat(text: Binding<String>, isPresented: Binding<Bool>, prompt: String) -> some View {
        if #available(iOS 17, *) {
            searchable(text: text, isPresented: isPresented, placement: .navigationBarDrawer(displayMode: .automatic), prompt: prompt)
        } else {
            searchable(text: text, placement: .navigationBarDrawer(displayMode: .automatic), prompt: prompt)
        }
    }
}

/// iOS 16: mantém `model.searchActive` em sincronia com a busca do sistema (colocar dentro do conteúdo pesquisável).
struct SearchStateSync: View {
    @Binding var active: Bool
    @Environment(\.isSearching) private var isSearching
    @Environment(\.dismissSearch) private var dismissSearch

    var body: some View {
        if #available(iOS 17, *) {
            EmptyView()
        } else {
            Color.clear.frame(width: 0, height: 0)
                .onValueChange(isSearching) { if active != $0 { active = $0 } }
                .onValueChange(active) { want in
                    if want && !isSearching { SearchFocus.focus() }
                    if !want && isSearching { dismissSearch() }
                }
        }
    }
}

/// iOS 16 não abre a busca por código; aqui o campo de busca do sistema recebe o foco diretamente.
@MainActor
enum SearchFocus {
    static func focus() {
        let windows = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.flatMap(\.windows)
        guard let window = windows.first(where: \.isKeyWindow) ?? windows.first else { return }
        func find(_ v: UIView) -> UISearchBar? {
            if let s = v as? UISearchBar, s.window != nil { return s }
            for c in v.subviews { if let s = find(c) { return s } }
            return nil
        }
        find(window)?.searchTextField.becomeFirstResponder()
    }
}
