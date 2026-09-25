package com.hernan.laberinto.model

import kotlin.random.Random

/** Metas de vida: 24, agrupadas en 3 etapas de 8 (juventud, adultez, madurez). */
object Goals {
    const val STAGES = 3
    const val PER_STAGE = Board.TREASURES / STAGES

    fun stageOf(goal: Int) = goal / PER_STAGE
    fun ofStage(stage: Int) = (stage * PER_STAGE until (stage + 1) * PER_STAGE).toList()
}

class Player(val index: Int, val isAi: Boolean, val home: Pos) {
    var pos = home

    /** Metas pendientes de cada etapa. Dentro de una etapa se pueden conseguir en cualquier orden. */
    val stages: List<MutableList<Int>> = List(Goals.STAGES) { mutableListOf() }
    var collected = 0

    /** Etapa actual (0..2), o STAGES si ya tiene todas las metas y solo le falta jubilarse. */
    val stage: Int get() = stages.indexOfFirst { it.isNotEmpty() }.let { if (it < 0) Goals.STAGES else it }

    /** Metas que puede conseguir ahora: las pendientes de su etapa actual. */
    val activeGoals: List<Int> get() = stages.firstOrNull { it.isNotEmpty() } ?: emptyList()

    /** Ya tiene todas las metas: debe volver a casa (jubilarse). */
    val retiring get() = stages.all { it.isEmpty() }
    val total get() = collected + stages.sumOf { it.size }
}

class GameState(numAi: Int, goalsPerStage: Int, val rng: Random = Random.Default) {
    val board = Board.generate(rng)
    val players: List<Player>
    var current = 0
        private set
    var lastInsert: Insert? = null
        private set
    var winner: Player? = null
        private set

    init {
        require(numAi in 1..3)
        val count = numAi + 1
        require(goalsPerStage in 1..maxGoalsPerStage(numAi))
        players = (0 until count).map { i -> Player(i, isAi = i != 0, home = HOMES[i]) }
        for (s in 0 until Goals.STAGES) {
            val pool = Goals.ofStage(s).shuffled(rng)
            for (i in 0 until goalsPerStage) for (p in players) p.stages[s].add(pool[i * count + p.index])
        }
    }

    val currentPlayer get() = players[current]

    /** Todas las entradas menos la que desharía el último empuje. */
    fun allowedInserts() = Insert.ALL.filter { it != lastInsert?.opposite() }

    fun push(ins: Insert) {
        require(ins in allowedInserts()) { "Empuje no permitido: $ins" }
        val positions = players.map { it.pos }.toMutableList()
        board.push(ins, positions)
        players.forEachIndexed { i, p -> p.pos = positions[i] }
        lastInsert = ins
    }

    /** Casillas a las que le interesa ir: sus metas activas en el tablero, o su casa si se jubila. */
    fun goalsOf(p: Player): List<Pos> = goalsOf(p, board)

    /** Mueve al jugador actual. Devuelve la meta conseguida o -1. */
    fun moveCurrent(to: Pos): Int {
        val p = currentPlayer
        require(to in board.reachable(p.pos)) { "Casilla no alcanzable: $to" }
        p.pos = to
        var found = -1
        val tile = board[to]
        if (tile.treasure >= 0 && tile.treasure in p.activeGoals) {
            found = tile.treasure
            p.stages[Goals.stageOf(found)].remove(found)
            p.collected++
            tile.treasure = -1 // la meta conseguida desaparece del tablero
        }
        if (p.retiring && p.pos == p.home) winner = p
        return found
    }

    fun nextTurn() {
        current = (current + 1) % players.size
    }

    companion object {
        val HOMES = listOf(Pos(6, 0), Pos(0, 6), Pos(0, 0), Pos(6, 6))

        fun maxGoalsPerStage(numAi: Int) = Goals.PER_STAGE / (numAi + 1)

        fun goalsOf(p: Player, board: Board): List<Pos> =
            if (p.retiring) listOf(p.home) else p.activeGoals.mapNotNull { board.find(it) }
    }
}
