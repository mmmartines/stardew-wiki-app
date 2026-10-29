package com.stardew.wikipt

sealed class Target {
    data class Article(val title: String) : Target()
    object AllPages : Target()
    object AllCategories : Target()
    object Random : Target()
}

/** [icon] é o caminho do ícone hospedado na própria wiki (carregado ao vivo). */
data class Cat(val label: String, val icon: String, val target: Target)
data class Group(val name: String, val items: List<Cat>)

private const val IMG = "https://stardewvalleywiki.com/mediawiki/images/en/"

private fun a(label: String, icon: String, title: String = label) = Cat(label, IMG + icon, Target.Article(title))

object Categories {
    val groups = listOf(
        Group(
            "Comunidade", listOf(
                a("Aldeões", "b/b6/Lewis_Icon.png"),
                a("Amizade", "8/83/Bouquet.png"),
                a("Casamento", "e/e0/Mermaid%27s_Pendant.png"),
                a("Pacotes", "3/3f/Bundle_Red.png", "Conjuntos"),
                a("Centro Comunitário", "a/a1/Bundle_Purple.png"),
                a("Missões", "c/c0/Quest.png"),
                a("Festivais", "4/46/Golden_Pumpkin.png"),
                a("Vila Pelicanos", "1/10/Gold.png"),
            )
        ),
        Group(
            "Fazenda", listOf(
                a("Lavouras", "d/db/Parsnip.png"),
                a("Árvores frutíferas", "7/7d/Apple.png"),
                a("Animais", "2/22/White_Chicken.png"),
                a("Artesanato", "0/08/Sprinkler.png"),
                a("Mercadorias Artesanais", "6/69/Wine.png"),
                a("Construções", "4/48/Coop.png", "Construções na Fazenda"),
                a("Ferramentas", "c/ca/Iridium_Pickaxe.png"),
                a("Cultivo", "7/75/Scarecrow.png"),
            )
        ),
        Group(
            "Natureza e comida", listOf(
                a("Peixes", "0/04/Sardine.png"),
                a("Pesca", "d/da/Bamboo_Pole.png"),
                a("Coleta", "2/2e/Common_Mushroom.png"),
                a("Culinária", "1/18/Fried_Egg.png"),
                a("Estações", "9/9c/Spring.png"),
            )
        ),
        Group(
            "Aventura", listOf(
                a("As Minas", "7/78/Copper_Ore.png"),
                a("Caverna da Caveira", "e/e9/Iridium_Ore.png"),
                a("Monstros", "7/7b/Green_Slime.png"),
                a("Armas", "4/44/Galaxy_Sword.png"),
                a("Anéis", "d/d3/Ruby_Ring.png"),
                a("Minerais", "e/ea/Diamond.png"),
                a("Artefatos", "b/b9/Dwarf_Scroll_I.png"),
                a("Ilha Gengibre", "5/54/Golden_Walnut.png"),
            )
        ),
        Group(
            "Explorar a wiki", listOf(
                a("Página inicial", "a/a5/Stardrop.png", "Stardew Valley Wiki"),
                a("Habilidades", "8/82/Farming_Skill_Icon.png"),
                a("Conquistas", "5/56/Prismatic_Shard.png"),
                a("Mapa", "f/f3/Return_Scepter.png", "Mapa Múndi"),
                Cat("Todas as páginas", IMG + "f/f3/Lost_Book.png", Target.AllPages),
                Cat("Categorias", IMG + "b/b3/Chest.png", Target.AllCategories),
                Cat("Página aleatória", IMG + "8/87/Mystery_Box.png", Target.Random),
            )
        ),
    )

    val all: List<Cat> = groups.flatMap { it.items }
}
