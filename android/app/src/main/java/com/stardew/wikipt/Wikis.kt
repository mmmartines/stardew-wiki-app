package com.stardew.wikipt

import android.content.Context

/**
 * Uma wiki que o app sabe ler: a wiki oficial (no idioma escolhido) ou a wiki de um mod de expansão.
 * [key] identifica a wiki em favoritos, histórico e cache: na oficial é o código do idioma ("pt", "en"…),
 * nos mods é um código próprio de 3 letras ("sve", "rsv"…), então não se confundem.
 */
data class Wiki(
    val key: String,
    val mod: Boolean,
    /** Nome do conteúdo ("Stardew Valley Expanded"); na oficial vem das strings. */
    val name: String,
    /** Nome curto para botões e listas ("SVE"). */
    val short: String,
    val emoji: String,
    val host: String,
    /** Caminho da API ("/mediawiki/api.php" na oficial, "/api.php" no wiki.gg). */
    val apiPath: String,
    /** Prefixo das páginas ("/" na oficial, "/wiki/" no wiki.gg). */
    val pagePath: String,
    val indexPath: String,
    val license: String,
    val licenseUrl: String,
    /** Página inicial da wiki (aberta pelo item "Página inicial" do menu). */
    val mainPage: String,
    val menu: List<Cat> = emptyList(),
) {
    val base: String get() = "https://$host"

    fun label(ctx: Context): String = if (mod) ctx.getString(R.string.src_mod_fmt, name) else ctx.getString(R.string.src_base)
}

object Wikis {
    /** Valor salvo para "jogo base" (a wiki oficial no idioma escolhido). */
    const val BASE = "base"

    private const val IMG = "https://stardewvalleywiki.com/mediawiki/images/en/"

    /** Ícones do jogo base (hospedados na wiki oficial) reaproveitados nos menus dos mods, pela página em inglês. */
    private val ICONS = mapOf(
        "Villagers" to "b/b6/Lewis_Icon.png", "Fish" to "0/04/Sardine.png", "Crops" to "d/db/Parsnip.png",
        "Fruit Trees" to "7/7d/Apple.png", "Animals" to "2/22/White_Chicken.png", "Crafting" to "0/08/Sprinkler.png",
        "Artisan Goods" to "6/69/Wine.png", "Cooking" to "1/18/Fried_Egg.png", "Foraging" to "2/2e/Common_Mushroom.png",
        "Monsters" to "7/7b/Green_Slime.png", "Weapons" to "4/44/Galaxy_Sword.png", "Minerals" to "e/ea/Diamond.png",
        "Artifacts" to "b/b9/Dwarf_Scroll_I.png", "Quests" to "c/c0/Quest.png", "Festivals" to "4/46/Golden_Pumpkin.png",
        "Bundles" to "3/3f/Bundle_Red.png", "Buildings" to "f/f8/Barn.png", "Shops" to "c/cf/Pierre_Icon.png",
        "Furniture" to "2/2a/Oak_Chair.png", "Trees" to "c/cd/Acorn.png", "Letters" to "f/fe/Mail.png",
        "All Letters" to "f/fe/Mail.png", "Special Orders" to "d/de/Bulletin_Board.png", "Locations" to "f/f3/Return_Scepter.png",
        "Events" to "1/15/Calendar.png", "Clothing" to "9/99/Cowboy_Hat.png", "Compatibility" to "f/f4/Solar_Essence.png",
        "Items" to "e/ed/Hardwood.png",
    )
    private val EMOJI = mapOf(
        "Villagers" to "🧑‍🌾", "Fish" to "🐟", "Crops" to "🌱", "Fruit Trees" to "🍎", "Animals" to "🐔", "Crafting" to "🔨",
        "Artisan Goods" to "🍷", "Cooking" to "🍳", "Foraging" to "🍄", "Monsters" to "👾", "Weapons" to "⚔️",
        "Minerals" to "💎", "Artifacts" to "🏺", "Quests" to "📜", "Festivals" to "🎉", "Bundles" to "🎁",
        "Buildings" to "🏠", "Shops" to "🛒", "Furniture" to "🪑", "Trees" to "🌳", "Letters" to "✉️", "All Letters" to "✉️",
        "Special Orders" to "📋", "Locations" to "🗺️", "Events" to "📅", "Clothing" to "👒", "Compatibility" to "🧩",
        "Items" to "🎒",
    )

    /** Menu de um mod: as páginas do mod (rótulo traduzido quando a wiki oficial tem o mesmo tema) + atalhos fixos. */
    private fun menu(main: String, vararg pages: String): List<Cat> =
        pages.map { Cat(Target.Article(it), IMG + (ICONS[it] ?: "a/a5/Stardrop.png"), EMOJI[it] ?: "📄") } + listOf(
            Cat(Target.Article(main), IMG + "a/a5/Stardrop.png", "⭐", R.string.cat_home_page),
            Cat(Target.AllPages, IMG + "f/f3/Lost_Book.png", "📚", R.string.cat_all_pages),
            Cat(Target.AllCategories, IMG + "b/b3/Chest.png", "🗂️", R.string.cat_categories),
            Cat(Target.Random, IMG + "8/87/Mystery_Box.png", "🎲", R.string.cat_random),
        )

    private fun gg(key: String, sub: String, name: String, short: String, emoji: String, vararg pages: String): Wiki {
        val main = "$name Wiki"
        return Wiki(
            key = key, mod = true, name = name, short = short, emoji = emoji,
            host = "$sub.wiki.gg", apiPath = "/api.php", pagePath = "/wiki/", indexPath = "/index.php",
            license = "CC BY-SA 4.0", licenseUrl = "https://creativecommons.org/licenses/by-sa/4.0/",
            mainPage = main, menu = menu(main, *pages),
        )
    }

    /** Mods de expansão com wiki própria (todas em inglês, no wiki.gg, licença CC BY-SA 4.0). */
    val mods: List<Wiki> = listOf(
        gg("sve", "stardewvalleyexpanded", "Stardew Valley Expanded", "SVE", "🌲",
            "Villagers", "Locations", "Festivals", "Quests", "Letters", "Crops", "Trees", "Fish", "Crafting",
            "Buildings", "Furniture", "Shops", "Bundles", "Monsters", "Weapons", "Artifacts"),
        gg("rsv", "ridgesidevillage", "Ridgeside Village", "Ridgeside", "⛰️",
            "Villagers", "Quests", "Festivals", "All Letters", "Fish", "Foraging", "Fruit Trees", "Cooking",
            "Items", "Monsters", "Weapons", "Compatibility"),
        gg("sbv", "sunberryvillage", "Sunberry Village", "Sunberry", "🫐",
            "Villagers", "Locations", "Festivals", "Quests", "Special Orders", "Letters", "Crops", "Fish", "Cooking",
            "Crafting", "Artisan Goods", "Shops", "Monsters", "Weapons", "Artifacts", "Compatibility"),
        gg("esc", "eastscarp", "East Scarp", "East Scarp", "🌊",
            "Villagers", "Locations", "Festivals", "Quests", "Special Orders", "Letters", "Animals", "Crops", "Fish",
            "Crafting", "Shops", "Compatibility"),
        gg("mtv", "visitmountvapius", "Visit Mount Vapius", "Vapius", "🏔️",
            "Villagers", "Locations", "Events", "Quests", "Animals", "Crops", "Trees", "Foraging", "Fish", "Cooking",
            "Crafting", "Artisan Goods", "Buildings", "Furniture", "Clothing", "Bundles", "Monsters", "Minerals", "Artifacts"),
    )

    fun base(lang: String = Lang.code) = Wiki(
        key = lang, mod = false, name = "Stardew Valley Wiki", short = "Stardew Valley", emoji = "⭐",
        host = Lang.host(lang), apiPath = "/mediawiki/api.php", pagePath = "/", indexPath = "/mediawiki/index.php",
        license = "CC BY-NC-SA 3.0", licenseUrl = "https://creativecommons.org/licenses/by-nc-sa/3.0/",
        mainPage = "Stardew Valley Wiki",
    )

    fun isMod(key: String?) = key != null && mods.any { it.key == key }

    /** Wiki de um favorito/histórico/link: mod pelo código; senão a oficial no idioma indicado (ou no atual). */
    fun of(key: String?): Wiki = mods.firstOrNull { it.key == key }
        ?: base(if (key != null && Lang.all.any { it.code == key }) key else Lang.code)

    /** Wiki dona de um endereço (oficial em qualquer idioma ou um mod); null se for outro site. */
    fun byHost(host: String?): Wiki? {
        val h = host?.removePrefix("www.") ?: return null
        mods.firstOrNull { it.host == h }?.let { return it }
        return Lang.all.firstOrNull { Lang.host(it.code) == h }?.let { base(it.code) }
    }

    /** Escolha salva ("base" ou o código do mod) → wiki. */
    fun fromChoice(choice: String?): Wiki = if (isMod(choice)) of(choice) else base()

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("wiki", Context.MODE_PRIVATE)

    /** Wiki que o app mostra ao abrir ("base" ou código do mod). */
    fun startChoice(ctx: Context): String = prefs(ctx).getString("start_src", BASE)?.takeIf { it == BASE || isMod(it) } ?: BASE

    fun setStartChoice(ctx: Context, choice: String) = prefs(ctx).edit().putString("start_src", choice).apply()
}
