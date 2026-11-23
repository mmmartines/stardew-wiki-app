import SwiftUI
import WebKit

/// Acesso ao WKWebView de uma tela (rodar JS, buscar na página, rolar até uma seção).
@MainActor
final class WebController: ObservableObject {
    weak var web: WKWebView?

    func js(_ code: String) {
        web?.evaluateJavaScript(code, completionHandler: nil)
    }

    func scrollTo(anchor: String) {
        let q = (try? JSONSerialization.data(withJSONObject: [anchor])).flatMap { String(data: $0, encoding: .utf8) } ?? "[\"\"]"
        js("window.__goto && window.__goto(\(q)[0])")
    }

    func showFind() {
        guard let web else { return }
        web.isFindInteractionEnabled = true
        web.findInteraction?.presentFindNavigator(showingReplace: false)
    }

    func setTextZoom(_ percent: Int) {
        js("document.documentElement.style.webkitTextSizeAdjust='\(percent)%'")
    }
}

/// Página HTML do app. As páginas usam o mesmo JavaScript do Android, que chama `Android.open(i)`,
/// `Android.search()`, `Zoom.state(true)` etc.; aqui essas chamadas viram mensagens para o Swift.
struct WebView: UIViewRepresentable {
    let html: String
    let baseURL: URL?
    /// Só recarrega quando a chave muda (evita recarregar a cada redesenho da tela).
    let key: String
    var controller: WebController? = nil
    var onMessage: ((String, Int) -> Void)? = nil
    /// Link tocado na página. Devolve true se o app tratou (a navegação é cancelada).
    var onLink: ((URL) -> Void)? = nil
    var onLoaded: (() -> Void)? = nil
    var scrollEnabled = true

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    func makeUIView(context: Context) -> WKWebView {
        let cfg = WKWebViewConfiguration()
        let ucc = WKUserContentController()
        let bridge = """
        (function(){
          function post(a,v){ try { window.webkit.messageHandlers.app.postMessage({a:a, v:v}); } catch(e) {} }
          window.Android = {
            open: function(i){ post('open', i); }, recent: function(i){ post('recent', i); }, source: function(i){ post('source', i); },
            search: function(){ post('search', 0); }, credits: function(){ post('credits', 0); },
            done: function(){ post('done', 0); }
          };
          window.Zoom = { state: function(o){ post('zoom', o ? 1 : 0); } };
        })();
        """
        ucc.addUserScript(WKUserScript(source: bridge, injectionTime: .atDocumentStart, forMainFrameOnly: true))
        ucc.add(WeakHandler(context.coordinator), name: "app")
        cfg.userContentController = ucc
        cfg.dataDetectorTypes = []
        let web = WKWebView(frame: .zero, configuration: cfg)
        web.navigationDelegate = context.coordinator
        web.isOpaque = false
        web.backgroundColor = UIColor(named: "PageBackground") ?? .systemBackground
        web.scrollView.isScrollEnabled = scrollEnabled
        web.allowsLinkPreview = false
        if #available(iOS 16.4, *) { web.isInspectable = true }
        controller?.web = web
        context.coordinator.load(into: web)
        return web
    }

    func updateUIView(_ web: WKWebView, context: Context) {
        context.coordinator.parent = self
        controller?.web = web
        if context.coordinator.loadedKey != key { context.coordinator.load(into: web) }
    }

    static func dismantleUIView(_ web: WKWebView, coordinator: Coordinator) {
        web.configuration.userContentController.removeScriptMessageHandler(forName: "app")
        web.navigationDelegate = nil
    }

    @MainActor
    final class Coordinator: NSObject, WKNavigationDelegate, WKScriptMessageHandler {
        var parent: WebView
        var loadedKey: String?

        init(_ parent: WebView) { self.parent = parent }

        func load(into web: WKWebView) {
            loadedKey = parent.key
            web.loadHTMLString(parent.html, baseURL: parent.baseURL)
        }

        func userContentController(_ ucc: WKUserContentController, didReceive message: WKScriptMessage) {
            guard let body = message.body as? [String: Any], let a = body["a"] as? String else { return }
            let v = (body["v"] as? Int) ?? (body["v"] as? NSNumber)?.intValue ?? 0
            parent.onMessage?(a, v)
        }

        func webView(_ webView: WKWebView, decidePolicyFor action: WKNavigationAction,
                     decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
            // a própria página carregada pelo app passa; qualquer link vai para o app decidir
            if action.navigationType == .other, action.targetFrame?.isMainFrame != false,
               action.request.url == parent.baseURL || action.request.url?.absoluteString == "about:blank" {
                decisionHandler(.allow)
                return
            }
            if action.targetFrame?.isMainFrame == false && action.navigationType == .other {
                decisionHandler(.allow) // iframes internos (raros nas páginas da wiki)
                return
            }
            if let url = action.request.url { parent.onLink?(url) }
            decisionHandler(.cancel)
        }

        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            parent.onLoaded?()
        }
    }
}

/// Evita o ciclo de retenção entre WKUserContentController e o coordenador.
@MainActor
private final class WeakHandler: NSObject, WKScriptMessageHandler {
    weak var target: WKScriptMessageHandler?
    init(_ t: WKScriptMessageHandler) { target = t }
    func userContentController(_ ucc: WKUserContentController, didReceive message: WKScriptMessage) {
        target?.userContentController(ucc, didReceive: message)
    }
}
