import Foundation

/// Um idioma da Stardew Valley Wiki.
struct Wiki: Identifiable, Hashable {
    let code: String
    let name: String
    let flag: String
    var id: String { code }
}

/// Idiomas da wiki (os mesmos do site e do app Android) e o idioma escolhido.
enum Lang {
    static let all: [Wiki] = [
        Wiki(code: "pt", name: "Português", flag: "🇧🇷"),
        Wiki(code: "en", name: "English", flag: "🇺🇸"),
        Wiki(code: "es", name: "Español", flag: "🇪🇸"),
        Wiki(code: "fr", name: "Français", flag: "🇫🇷"),
        Wiki(code: "de", name: "Deutsch", flag: "🇩🇪"),
        Wiki(code: "it", name: "Italiano", flag: "🇮🇹"),
        Wiki(code: "hu", name: "Magyar", flag: "🇭🇺"),
        Wiki(code: "ru", name: "Русский", flag: "🇷🇺"),
        Wiki(code: "tr", name: "Türkçe", flag: "🇹🇷"),
        Wiki(code: "ja", name: "日本語", flag: "🇯🇵"),
        Wiki(code: "ko", name: "한국어", flag: "🇰🇷"),
        Wiki(code: "zh", name: "中文", flag: "🇨🇳"),
    ]

    private static let key = "lang"

    /// Idioma atual da wiki (e da interface).
    static var code: String = UserDefaults.standard.string(forKey: key) ?? suggested()

    /// A pessoa já escolheu um idioma alguma vez?
    static var chosen: Bool { UserDefaults.standard.string(forKey: key) != nil }

    static func set(_ c: String) {
        code = c
        UserDefaults.standard.set(c, forKey: key)
    }

    /// Idioma do aparelho, se a wiki tiver; senão inglês.
    static func suggested() -> String {
        let dev = Locale.preferredLanguages.first.map { String($0.prefix(2)) } ?? "en"
        return all.first { $0.code == dev }?.code ?? "en"
    }

    static var current: Wiki { all.first { $0.code == code } ?? all[1] }

    static func wiki(_ c: String) -> Wiki? { all.first { $0.code == c } }

    static func host(_ c: String = code) -> String { c == "en" ? "stardewvalleywiki.com" : "\(c).stardewvalleywiki.com" }
    static func base(_ c: String = code) -> String { "https://" + host(c) }

    /// Pasta .lproj dos textos da interface para o idioma.
    static func lproj(_ c: String) -> String { c == "zh" ? "zh-Hans" : c }
}
