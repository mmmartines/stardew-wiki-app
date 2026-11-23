import SwiftUI
import Translation

/// Pedido de tradução automática de uma página de mod (textos em inglês, na ordem da página).
struct MTRequest: Equatable {
    let id = UUID()
    let texts: [String]
}

extension View {
    /// Tradução automática no aparelho (framework Translation da Apple, iOS 18+). Antes disso não faz nada.
    @ViewBuilder
    func autoTranslate(_ request: MTRequest?, done: @escaping ([String]?) -> Void) -> some View {
        if #available(iOS 18.0, *) {
            modifier(AutoTranslateModifier(request: request, done: done))
        } else {
            self
        }
    }
}

enum AutoTranslateSupport {
    /// O iPhone tem a tradução automática para apps?
    static var available: Bool {
        if #available(iOS 18.0, *) { return Lang.code != "en" }
        return false
    }

    static var target: String { Lang.code == "zh" ? "zh-Hans" : Lang.code }
}

@available(iOS 18.0, *)
private struct AutoTranslateModifier: ViewModifier {
    let request: MTRequest?
    let done: ([String]?) -> Void
    @State private var config: TranslationSession.Configuration?

    func body(content: Content) -> some View {
        content
            .translationTask(config) { session in
                guard let r = request else { return }
                do {
                    // o sistema pede para baixar o idioma na primeira vez, se precisar
                    let reqs = r.texts.enumerated().map {
                        TranslationSession.Request(sourceText: $0.element.trimmingCharacters(in: .whitespacesAndNewlines),
                                                   clientIdentifier: String($0.offset))
                    }
                    let resp = try await session.translations(from: reqs)
                    var out = r.texts
                    for x in resp { if let id = x.clientIdentifier, let i = Int(id), out.indices.contains(i) { out[i] = x.targetText } }
                    done(out)
                } catch {
                    done(nil) // idioma não suportado, download recusado, sem internet na primeira vez…
                }
            }
            .onChange(of: request?.id) { _, id in
                guard id != nil else { return }
                if config == nil {
                    config = TranslationSession.Configuration(source: Locale.Language(identifier: "en"),
                                                              target: Locale.Language(identifier: AutoTranslateSupport.target))
                } else {
                    config?.invalidate()
                }
            }
    }
}
