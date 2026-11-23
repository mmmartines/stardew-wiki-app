import Foundation
import Network

/// Estado da conexão do aparelho, observado pelas telas (aviso de sem internet).
@MainActor
final class Net: ObservableObject {
    static let shared = Net()

    @Published private(set) var online = true
    private let monitor = NWPathMonitor()

    /// Leitura segura fora da thread principal (para a camada de rede).
    nonisolated(unsafe) static var isOnline = true

    private init() {
        monitor.pathUpdateHandler = { path in
            let ok = path.status == .satisfied
            Net.isOnline = ok
            Task { @MainActor in
                // espera um instante antes de mudar, para não piscar ao trocar de Wi-Fi para dados móveis
                try? await Task.sleep(nanoseconds: 800_000_000)
                if Net.isOnline == ok { Net.shared.online = ok }
            }
        }
        monitor.start(queue: DispatchQueue(label: "net"))
    }
}

/// Aparelho sem internet: nem tenta a rede.
struct OfflineError: Error {}

/// A wiki respondeu com erro (ex.: 403 do firewall para certos termos, 5xx fora do ar).
struct ServerError: Error { let code: Int; let detail: String }

/// Página não existe com esse nome.
struct MissingPageError: Error {}

/// Erro genérico com detalhe técnico.
struct WikiError: Error, CustomStringConvertible { let description: String }

extension Error {
    var isOffline: Bool { self is OfflineError || !Net.isOnline }

    /// Mensagem curta para o usuário; o detalhe técnico fica escondido.
    func friendly(page: Bool) -> String {
        if isOffline { return t(page ? "err_offline_page" : "no_connection") }
        if self is ServerError { return t(page ? "err_server_page" : "search_failed") }
        return t(page ? "load_error" : "search_failed")
    }
}
