package com.pranshuparmar.inkmayhem.game

/** A world: its stages in order, its paper and its theme tune (a file under music/). */
class World(val number: Int, val name: String, val theme: Theme, val music: String, val stages: List<StageDef>)

/**
 * Every stage, world by world, each the same every time. A stage unlocks once the one before it
 * (across worlds too) has been cleared; each world's first stage teaches what is new in it.
 */
object Stages {
    val WORLDS = listOf(
        World(1, "The Notebook", Theme.PAGE, "ink_rush", World1.STAGES),
        World(2, "The Cellar", Theme.CELLAR, "cellar", World2.STAGES),
        World(3, "Up High", Theme.SKY, "sky", World3.STAGES),
        World(4, "The Castle", Theme.CASTLE, "castle", World4.STAGES),
        World(5, "Midnight Ink", Theme.MIDNIGHT, "midnight", World5.STAGES),
    )

    val FIRST_PAGE: StageDef get() = World1.FIRST_PAGE

    /** Every stage in play order. */
    val ALL: List<StageDef> = WORLDS.flatMap { it.stages }

    fun byId(id: String): StageDef? = ALL.firstOrNull { it.id == id }

    fun next(def: StageDef): StageDef? = ALL.getOrNull(ALL.indexOf(def) + 1)

    fun worldOf(def: StageDef): World = WORLDS.first { it.number == def.world }
}
