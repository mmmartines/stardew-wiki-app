import Foundation
import WebKit

/// Faz os pedidos da API por dentro de um WKWebView invisível, que se comporta como um navegador comum
/// e passa pela proteção anti-robô da wiki quando ela bloqueia a conexão direta (mesma ideia do Android).
@MainActor
final class Bridge: NSObject, WKNavigationDelegate {
    static let shared = Bridge()

    private var web: WKWebView?
    private var loadedHost: String?
    private var readyWaiters: [CheckedContinuation<Void, Never>] = []
    private var ready = false

    private func ensure(host: String) {
        if web != nil && loadedHost == host { return }
        let w = web ?? {
            let cfg = WKWebViewConfiguration()
            cfg.websiteDataStore = .default()
            let v = WKWebView(frame: .zero, configuration: cfg)
            v.navigationDelegate = self
            return v
        }()
        web = w
        loadedHost = host
        ready = false
        w.load(URLRequest(url: URL(string: "https://\(host)/mediawiki/api.php?action=query&meta=siteinfo&format=json")!))
    }

    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        Task { @MainActor in
            // a página de desafio redireciona sozinha; só fica pronto quando vier o JSON
            let first = (try? await webView.evaluateJavaScript("(document.body && document.body.innerText || '').trim().charAt(0)")) as? String
            if first == "{" { self.markReady() }
        }
    }

    private func markReady() {
        ready = true
        let w = readyWaiters
        readyWaiters = []
        w.forEach { $0.resume() }
    }

    private func waitReady(timeout: Double) async {
        if ready { return }
        await withCheckedContinuation { (c: CheckedContinuation<Void, Never>) in
            readyWaiters.append(c)
            Task { @MainActor in
                try? await Task.sleep(nanoseconds: UInt64(timeout * 1_000_000_000))
                if !self.ready {
                    let all = self.readyWaiters
                    self.readyWaiters = []
                    all.forEach { $0.resume() }
                }
            }
        }
    }

    /// Busca [url] pelo navegador interno e devolve o texto.
    func fetch(_ url: URL) async throws -> String {
        let host = url.host ?? Lang.host()
        let sameSite = host == Lang.host()
        ensure(host: sameSite ? host : Lang.host())
        await waitReady(timeout: 15)
        guard let web else { throw WikiError(description: "bridge unavailable") }
        // outra wiki: sem cookies, senão o navegador bloqueia (CORS com origin=*)
        let js = """
        const r = await fetch(url, {credentials: cred});
        const t = await r.text();
        return JSON.stringify({ok: r.ok, status: r.status, text: t});
        """
        let result = try await web.callAsyncJavaScript(js, arguments: ["url": url.absoluteString, "cred": sameSite ? "include" : "omit"],
                                                       contentWorld: .page)
        guard let s = result as? String, let data = s.data(using: .utf8),
              let o = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw WikiError(description: "bridge: no response")
        }
        if (o["ok"] as? Bool) != true { throw WikiError(description: "HTTP \(o["status"] as? Int ?? 0)") }
        let text = o["text"] as? String ?? ""
        if !text.trimmingCharacters(in: .whitespaces).hasPrefix("{") { throw WikiError(description: "bridge: not JSON") }
        return text
    }

    /// Idioma trocado: a página-base passa a ser a da nova wiki.
    func reset() {
        loadedHost = nil
        ready = false
    }
}
