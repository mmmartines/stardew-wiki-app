package com.stardew.wikipt

sealed class Target {
    data class Article(val title: String) : Target()
    object AllPages : Target()
    object AllCategories : Target()
    object Random : Target()
}

data class Cat(val emoji: String, val label: String, val target: Target)

/**
 * Menu principal. Se algum título não existir exatamente com esse nome na wiki,
 * o app abre automaticamente o melhor resultado da busca.
 */
object Categories {
    val home = listOf(
        Cat("🏡", "Página inicial", Target.Article("Stardew Valley Wiki")),
        Cat("👥", "Aldeões", Target.Article("Aldeões")),
        Cat("🎁", "Amizade e presentes", Target.Article("Amizade")),
        Cat("💍", "Casamento", Target.Article("Casamento")),
        Cat("🌱", "Culturas", Target.Article("Culturas")),
        Cat("🌳", "Árvores frutíferas", Target.Article("Árvores Frutíferas")),
        Cat("🍄", "Coleta", Target.Article("Forrageamento")),
        Cat("🐟", "Peixes", Target.Article("Peixes")),
        Cat("🎣", "Pesca", Target.Article("Pesca")),
        Cat("💎", "Minerais", Target.Article("Minerais")),
        Cat("🏺", "Artefatos", Target.Article("Artefatos")),
        Cat("🐄", "Animais", Target.Article("Animais")),
        Cat("🔨", "Fabricação", Target.Article("Fabricação")),
        Cat("🍳", "Culinária", Target.Article("Culinária")),
        Cat("🧺", "Produtos artesanais", Target.Article("Produtos Artesanais")),
        Cat("⛏️", "Ferramentas", Target.Article("Ferramentas")),
        Cat("⚔️", "Armas", Target.Article("Armas")),
        Cat("💠", "Anéis", Target.Article("Anéis")),
        Cat("🪨", "As Minas", Target.Article("As Minas")),
        Cat("💀", "Caverna da Caveira", Target.Article("Caverna da Caveira")),
        Cat("👾", "Monstros", Target.Article("Monstros")),
        Cat("📦", "Pacotes", Target.Article("Pacotes")),
        Cat("📜", "Missões", Target.Article("Missões")),
        Cat("⭐", "Habilidades", Target.Article("Habilidades")),
        Cat("🏠", "Construções", Target.Article("Construções")),
        Cat("🛒", "Lojas", Target.Article("Lojas")),
        Cat("🎉", "Festivais", Target.Article("Festivais")),
        Cat("📅", "Calendário", Target.Article("Calendário")),
        Cat("🍂", "Estações", Target.Article("Estações")),
        Cat("🗺️", "Mapa", Target.Article("Mapa")),
        Cat("🏝️", "Ilha Gengibre", Target.Article("Ilha Gengibre")),
        Cat("🏆", "Conquistas", Target.Article("Conquistas")),
        Cat("📚", "Todas as páginas", Target.AllPages),
        Cat("🗂️", "Categorias", Target.AllCategories),
        Cat("🎲", "Página aleatória", Target.Random),
    )
}
