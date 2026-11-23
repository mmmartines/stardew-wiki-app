import Foundation

/// Textos da interface no idioma da wiki (como no Android, a interface acompanha o idioma escolhido).
/// Os arquivos vêm dos strings.xml do Android (ios/scripts/gen_strings.py). Texto que faltar cai no inglês.
enum L10n {
    private static var bundles: [String: Bundle] = [:]
    private static let missing = "\u{1}__missing__"

    private static func bundle(_ lang: String) -> Bundle? {
        if let b = bundles[lang] { return b }
        guard let path = Bundle.main.path(forResource: Lang.lproj(lang), ofType: "lproj"),
              let b = Bundle(path: path) else { return nil }
        bundles[lang] = b
        return b
    }

    static func string(_ key: String) -> String {
        for lang in [Lang.code, "en"] {
            if let b = bundle(lang) {
                let s = b.localizedString(forKey: key, value: missing, table: nil)
                if s != missing { return s }
            }
        }
        return key
    }

    static func format(_ key: String, _ args: CVarArg...) -> String {
        String(format: string(key), locale: Locale(identifier: Lang.code), arguments: args)
    }
}

/// Atalho: `t("tab_home")`, `t("search_all_fmt", q)`.
func t(_ key: String) -> String { L10n.string(key) }
func t(_ key: String, _ args: CVarArg...) -> String {
    String(format: L10n.string(key), locale: Locale(identifier: Lang.code), arguments: args)
}
