import Foundation

/// Uma wiki que o app sabe ler: a oficial (no idioma escolhido) ou a de um mod de expansão (igual ao Android).
/// `key` identifica a wiki em favoritos, histórico e cache: na oficial é o código do idioma ("pt", "en"…),
/// nos mods é um código próprio de 3 letras ("sve", "rsv"…).
struct Source: Hashable {
    let key: String
    let mod: Bool
    let name: String
    let short: String
    let emoji: String
    let host: String
    let apiPath: String
    let pagePath: String
    let indexPath: String
    let license: String
    let licenseURL: String
    let mainPage: String
    var menu: [Cat] = []

    var base: String { "https://" + host }

    /// "Jogo base" ou "Mod: <nome>".
    var label: String { mod ? t("src_mod_fmt", name) : t("src_base") }
}

enum Sources {
    /// Valor salvo para "jogo base" (a wiki oficial no idioma escolhido).
    static let baseChoice = "base"

    private static let img = "https://stardewvalleywiki.com/mediawiki/images/en/"

    /// Ícones do jogo base reaproveitados nos menus dos mods, pela página em inglês.
    private static let icons: [String: String] = [
        "Villagers": "b/b6/Lewis_Icon.png", "Fish": "0/04/Sardine.png", "Crops": "d/db/Parsnip.png",
        "Fruit Trees": "7/7d/Apple.png", "Animals": "2/22/White_Chicken.png", "Crafting": "0/08/Sprinkler.png",
        "Artisan Goods": "6/69/Wine.png", "Cooking": "1/18/Fried_Egg.png", "Foraging": "2/2e/Common_Mushroom.png",
        "Monsters": "7/7b/Green_Slime.png", "Weapons": "4/44/Galaxy_Sword.png", "Minerals": "e/ea/Diamond.png",
        "Artifacts": "b/b9/Dwarf_Scroll_I.png", "Quests": "c/c0/Quest.png", "Festivals": "4/46/Golden_Pumpkin.png",
        "Bundles": "3/3f/Bundle_Red.png", "Buildings": "f/f8/Barn.png", "Shops": "c/cf/Pierre_Icon.png",
        "Furniture": "2/2a/Oak_Chair.png", "Trees": "c/cd/Acorn.png", "Letters": "f/fe/Mail.png",
        "All Letters": "f/fe/Mail.png", "Special Orders": "d/de/Bulletin_Board.png", "Locations": "f/f3/Return_Scepter.png",
        "Events": "1/15/Calendar.png", "Clothing": "9/99/Cowboy_Hat.png", "Compatibility": "f/f4/Solar_Essence.png",
        "Items": "e/ed/Hardwood.png",
    ]

    private static func menu(_ main: String, _ pages: [String]) -> [Cat] {
        pages.map { Cat(target: .article($0), icon: img + (icons[$0] ?? "a/a5/Stardrop.png"), emoji: "📄") } + [
            Cat(target: .article(main), icon: img + "a/a5/Stardrop.png", emoji: "⭐", labelKey: "cat_home_page"),
            Cat(target: .allPages, icon: img + "f/f3/Lost_Book.png", emoji: "📚", labelKey: "cat_all_pages"),
            Cat(target: .allCategories, icon: img + "b/b3/Chest.png", emoji: "🗂️", labelKey: "cat_categories"),
            Cat(target: .random, icon: img + "8/87/Mystery_Box.png", emoji: "🎲", labelKey: "cat_random"),
        ]
    }

    private static func gg(_ key: String, _ sub: String, _ name: String, _ short: String, _ emoji: String, _ pages: [String]) -> Source {
        let main = name + " Wiki"
        return Source(key: key, mod: true, name: name, short: short, emoji: emoji,
                      host: sub + ".wiki.gg", apiPath: "/api.php", pagePath: "/wiki/", indexPath: "/index.php",
                      license: "CC BY-SA 4.0", licenseURL: "https://creativecommons.org/licenses/by-sa/4.0/",
                      mainPage: main, menu: menu(main, pages))
    }

    /// Mods de expansão com wiki própria (os mesmos do Android, em inglês, no wiki.gg).
    static let mods: [Source] = [
        gg("sve", "stardewvalleyexpanded", "Stardew Valley Expanded", "SVE", "🌲",
           ["Villagers", "Locations", "Festivals", "Quests", "Letters", "Crops", "Trees", "Fish", "Crafting",
            "Buildings", "Furniture", "Shops", "Bundles", "Monsters", "Weapons", "Artifacts"]),
        gg("rsv", "ridgesidevillage", "Ridgeside Village", "Ridgeside", "⛰️",
           ["Villagers", "Quests", "Festivals", "All Letters", "Fish", "Foraging", "Fruit Trees", "Cooking",
            "Items", "Monsters", "Weapons", "Compatibility"]),
        gg("sbv", "sunberryvillage", "Sunberry Village", "Sunberry", "🫐",
           ["Villagers", "Locations", "Festivals", "Quests", "Special Orders", "Letters", "Crops", "Fish", "Cooking",
            "Crafting", "Artisan Goods", "Shops", "Monsters", "Weapons", "Artifacts", "Compatibility"]),
        gg("esc", "eastscarp", "East Scarp", "East Scarp", "🌊",
           ["Villagers", "Locations", "Festivals", "Quests", "Special Orders", "Letters", "Animals", "Crops", "Fish",
            "Crafting", "Shops", "Compatibility"]),
        gg("mtv", "visitmountvapius", "Visit Mount Vapius", "Vapius", "🏔️",
           ["Villagers", "Locations", "Events", "Quests", "Animals", "Crops", "Trees", "Foraging", "Fish", "Cooking",
            "Crafting", "Artisan Goods", "Buildings", "Furniture", "Clothing", "Bundles", "Monsters", "Minerals", "Artifacts"]),
    ]

    static func base(_ lang: String = Lang.code) -> Source {
        Source(key: lang, mod: false, name: "Stardew Valley Wiki", short: "Stardew Valley", emoji: "⭐",
               host: Lang.host(lang), apiPath: "/mediawiki/api.php", pagePath: "/", indexPath: "/mediawiki/index.php",
               license: "CC BY-NC-SA 3.0", licenseURL: "https://creativecommons.org/licenses/by-nc-sa/3.0/",
               mainPage: "Stardew Valley Wiki")
    }

    static func isMod(_ key: String?) -> Bool { key != nil && mods.contains { $0.key == key } }

    /// Wiki de um favorito/histórico/link: mod pelo código; senão a oficial no idioma indicado (ou no atual).
    static func of(_ key: String?) -> Source {
        if let m = mods.first(where: { $0.key == key }) { return m }
        if let key, Lang.wiki(key) != nil { return base(key) }
        return base()
    }

    /// Wiki dona de um endereço (oficial em qualquer idioma ou um mod); nil se for outro site.
    static func byHost(_ host: String?) -> Source? {
        guard let h = host?.replacingOccurrences(of: "www.", with: "") else { return nil }
        if let m = mods.first(where: { $0.host == h }) { return m }
        return Lang.all.first { Lang.host($0.code) == h }.map { base($0.code) }
    }

    /// Escolha salva ("base" ou o código do mod) → wiki.
    static func fromChoice(_ choice: String) -> Source { isMod(choice) ? of(choice) : base() }

    /// "Wiki ao abrir o app".
    static var startChoice: String {
        get {
            let c = UserDefaults.standard.string(forKey: "start_src") ?? baseChoice
            return isMod(c) ? c : baseChoice
        }
        set { UserDefaults.standard.set(newValue, forKey: "start_src") }
    }
}
