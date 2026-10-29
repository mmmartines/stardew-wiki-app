package com.stardew.wikipt

import android.content.Context

sealed class Target {
    /** [en] = título da página na wiki em inglês; o título no idioma atual vem de [Titles]. */
    data class Article(val en: String) : Target()
    object AllPages : Target()
    object AllCategories : Target()
    object Random : Target()
}

/**
 * Item do menu. [icon] = ícone do jogo hospedado na wiki (carregado ao vivo, só quando
 * BuildConfig.GAME_ICONS for true); [emoji] = ícone próprio usado na versão sem arte do jogo.
 * [labelRes] = rótulo fixo da interface; quando 0, o rótulo é o título da página no idioma atual.
 */
data class Cat(val target: Target, val icon: String, val emoji: String, val labelRes: Int = 0) {
    fun label(ctx: Context): String = when {
        labelRes != 0 -> ctx.getString(labelRes)
        target is Target.Article -> Titles.of(ctx, target.en)
        else -> ""
    }
}

data class Group(val nameRes: Int, val items: List<Cat>)

private const val IMG = "https://stardewvalleywiki.com/mediawiki/images/en/"

private fun a(en: String, icon: String, emoji: String) = Cat(Target.Article(en), IMG + icon, emoji)

object Categories {
    val groups = listOf(
        Group(
            R.string.group_community, listOf(
                a("Villagers", "b/b6/Lewis_Icon.png", "🧑‍🌾"),
                a("Friendship", "8/83/Bouquet.png", "💐"),
                a("Marriage", "e/e0/Mermaid%27s_Pendant.png", "💍"),
                a("Bundles", "3/3f/Bundle_Red.png", "🎁"),
                a("Community Center", "a/a1/Bundle_Purple.png", "🏛️"),
                a("Quests", "c/c0/Quest.png", "📜"),
                a("Festivals", "4/46/Golden_Pumpkin.png", "🎉"),
                a("Pelican Town", "1/10/Gold.png", "🏘️"),
            )
        ),
        Group(
            R.string.group_farm, listOf(
                a("Crops", "d/db/Parsnip.png", "🌱"),
                a("Fruit Trees", "7/7d/Apple.png", "🍎"),
                a("Animals", "2/22/White_Chicken.png", "🐔"),
                a("Crafting", "0/08/Sprinkler.png", "🔨"),
                a("Artisan Goods", "6/69/Wine.png", "🍷"),
                a("Carpenter's Shop", "4/48/Coop.png", "🏠"),
                a("Tools", "c/ca/Iridium_Pickaxe.png", "⛏️"),
                a("Farming", "7/75/Scarecrow.png", "🌾"),
            )
        ),
        Group(
            R.string.group_nature, listOf(
                a("Fish", "0/04/Sardine.png", "🐟"),
                a("Fishing", "d/da/Bamboo_Pole.png", "🎣"),
                a("Foraging", "2/2e/Common_Mushroom.png", "🍄"),
                a("Cooking", "1/18/Fried_Egg.png", "🍳"),
                a("Seasons", "9/9c/Spring.png", "🍂"),
            )
        ),
        Group(
            R.string.group_adventure, listOf(
                a("The Mines", "7/78/Copper_Ore.png", "🪨"),
                a("Skull Cavern", "e/e9/Iridium_Ore.png", "💀"),
                a("Monsters", "7/7b/Green_Slime.png", "👾"),
                a("Weapons", "4/44/Galaxy_Sword.png", "⚔️"),
                a("Rings", "d/d3/Ruby_Ring.png", "💠"),
                a("Minerals", "e/ea/Diamond.png", "💎"),
                a("Artifacts", "b/b9/Dwarf_Scroll_I.png", "🏺"),
                a("Ginger Island", "5/54/Golden_Walnut.png", "🏝️"),
            )
        ),
        Group(
            R.string.group_explore, listOf(
                Cat(Target.Article("Stardew Valley Wiki"), IMG + "a/a5/Stardrop.png", "⭐", R.string.cat_home_page),
                a("Skills", "8/82/Farming_Skill_Icon.png", "📈"),
                a("Achievements", "5/56/Prismatic_Shard.png", "🏆"),
                a("World Map", "f/f3/Return_Scepter.png", "🗺️"),
                Cat(Target.AllPages, IMG + "f/f3/Lost_Book.png", "📚", R.string.cat_all_pages),
                Cat(Target.AllCategories, IMG + "b/b3/Chest.png", "🗂️", R.string.cat_categories),
                Cat(Target.Random, IMG + "8/87/Mystery_Box.png", "🎲", R.string.cat_random),
            )
        ),
    )

    val all: List<Cat> = groups.flatMap { it.items }
}
