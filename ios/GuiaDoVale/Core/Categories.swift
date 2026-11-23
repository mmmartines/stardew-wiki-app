import Foundation

/// Para onde um bloco da tela inicial leva.
enum Target: Hashable {
    /// Título da página na wiki em inglês; o título no idioma atual vem de `Titles`.
    case article(String)
    case allPages
    case allCategories
    case random
}

/// Bloco do menu. `icon` = ícone do jogo hospedado na wiki (uso autorizado pela ConcernedApe).
struct Cat: Hashable {
    let target: Target
    let icon: String
    let emoji: String
    var labelKey: String? = nil

    var label: String {
        if let labelKey { return t(labelKey) }
        if case .article(let en) = target { return Titles.of(en) }
        return ""
    }
}

struct Group {
    let nameKey: String
    let items: [Cat]
}

private let img = "https://stardewvalleywiki.com/mediawiki/images/en/"
private func a(_ en: String, _ icon: String, _ emoji: String) -> Cat { Cat(target: .article(en), icon: img + icon, emoji: emoji) }

/// Mesmo menu do app Android.
enum Categories {
    static let groups: [Group] = [
        Group(nameKey: "group_community", items: [
            a("Villagers", "b/b6/Lewis_Icon.png", "🧑‍🌾"),
            a("Friendship", "8/83/Bouquet.png", "💐"),
            a("Marriage", "e/e0/Mermaid%27s_Pendant.png", "💍"),
            a("Bundles", "3/3f/Bundle_Red.png", "🎁"),
            a("Community Center", "a/a1/Bundle_Purple.png", "🏛️"),
            a("Quests", "c/c0/Quest.png", "📜"),
            a("Festivals", "4/46/Golden_Pumpkin.png", "🎉"),
            a("Pelican Town", "1/10/Gold.png", "🏘️"),
        ]),
        Group(nameKey: "group_farm", items: [
            a("Crops", "d/db/Parsnip.png", "🌱"),
            a("Fruit Trees", "7/7d/Apple.png", "🍎"),
            a("Animals", "2/22/White_Chicken.png", "🐔"),
            a("Crafting", "0/08/Sprinkler.png", "🔨"),
            a("Artisan Goods", "6/69/Wine.png", "🍷"),
            a("Carpenter's Shop", "4/48/Coop.png", "🏠"),
            a("Tools", "c/ca/Iridium_Pickaxe.png", "⛏️"),
            a("Farming", "7/75/Scarecrow.png", "🌾"),
        ]),
        Group(nameKey: "group_nature", items: [
            a("Fish", "0/04/Sardine.png", "🐟"),
            a("Fishing", "d/da/Bamboo_Pole.png", "🎣"),
            a("Foraging", "2/2e/Common_Mushroom.png", "🍄"),
            a("Cooking", "1/18/Fried_Egg.png", "🍳"),
            a("Seasons", "9/9c/Spring.png", "🍂"),
        ]),
        Group(nameKey: "group_adventure", items: [
            a("The Mines", "7/78/Copper_Ore.png", "🪨"),
            a("Skull Cavern", "e/e9/Iridium_Ore.png", "💀"),
            a("Monsters", "7/7b/Green_Slime.png", "👾"),
            a("Weapons", "4/44/Galaxy_Sword.png", "⚔️"),
            a("Rings", "d/d3/Ruby_Ring.png", "💠"),
            a("Minerals", "e/ea/Diamond.png", "💎"),
            a("Artifacts", "b/b9/Dwarf_Scroll_I.png", "🏺"),
            a("Ginger Island", "5/54/Golden_Walnut.png", "🏝️"),
        ]),
        Group(nameKey: "group_explore", items: [
            Cat(target: .article("Stardew Valley Wiki"), icon: img + "a/a5/Stardrop.png", emoji: "⭐", labelKey: "cat_home_page"),
            a("Skills", "8/82/Farming_Skill_Icon.png", "📈"),
            a("Achievements", "5/56/Prismatic_Shard.png", "🏆"),
            a("World Map", "f/f3/Return_Scepter.png", "🗺️"),
            Cat(target: .allPages, icon: img + "f/f3/Lost_Book.png", emoji: "📚", labelKey: "cat_all_pages"),
            Cat(target: .allCategories, icon: img + "b/b3/Chest.png", emoji: "🗂️", labelKey: "cat_categories"),
            Cat(target: .random, icon: img + "8/87/Mystery_Box.png", emoji: "🎲", labelKey: "cat_random"),
        ]),
    ]

    static let all: [Cat] = groups.flatMap(\.items)
}

/// Títulos das páginas do menu em cada idioma (titles.json, o mesmo arquivo do Android).
enum Titles {
    private static let map: [String: [String: String]] = {
        guard let url = Bundle.main.url(forResource: "titles", withExtension: "json"),
              let d = try? Data(contentsOf: url),
              let m = try? JSONSerialization.jsonObject(with: d) as? [String: [String: String]] else { return [:] }
        return m
    }()

    static func of(_ en: String) -> String {
        if Lang.code == "en" { return en }
        let t = map[en]?[Lang.code] ?? ""
        return t.isEmpty ? en : t
    }
}

/// Arquivos compartilhados com o Android (CSS/JS das páginas, fonte pixelada).
enum SharedAssets {
    private static var cache: [String: String] = [:]

    static func text(_ name: String, _ ext: String) -> String {
        let k = name + "." + ext
        if let c = cache[k] { return c }
        let s = Bundle.main.url(forResource: name, withExtension: ext).flatMap { try? String(contentsOf: $0, encoding: .utf8) } ?? ""
        cache[k] = s
        return s
    }

    /// Fonte pixelada (Pixelify Sans, OFL) embutida como data URI para as páginas.
    static let fontCSS: String = {
        var css = ""
        for (w, f) in [(400, "pixelify-sans-latin-400-normal"), (700, "pixelify-sans-latin-700-normal")] {
            guard let url = Bundle.main.url(forResource: f, withExtension: "woff2"), let d = try? Data(contentsOf: url) else { continue }
            css += "@font-face{font-family:'Pixel';font-weight:\(w);font-display:block;src:url(data:font/woff2;base64,\(d.base64EncodedString())) format('woff2')}"
        }
        return css
    }()
}
