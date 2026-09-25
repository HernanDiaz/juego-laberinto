package com.hernan.laberinto.model

import kotlin.random.Random

class Player(val index: Int, val isAi: Boolean, val home: Pos) {
    var pos = home
    val cards = ArrayDeque<Int>()
    var collected = 0

    /** Tesoro buscado ahora, o null si ya los tiene todos (entonces debe volver a casa). */
    val target: Int? get() = cards.firstOrNull()
    val total get() = collected + cards.size
}

class GameState(numAi: Int, cardsEach: Int, val rng: Random = Random.Default) {
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
        require(cardsEach in 1..Board.TREASURES / count)
        players = (0 until count).map { i -> Player(i, isAi = i != 0, home = HOMES[i]) }
        val deck = (0 until Board.TREASURES).shuffled(rng)
        for (i in 0 until cardsEach) for (p in players) p.cards.addLast(deck[i * count + p.index])
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

    /** Dónde tiene que ir el jugador: su tesoro, su casa, o null si el tesoro está en la pieza suelta. */
    fun goalOf(p: Player): Pos? {
        val t = p.target ?: return p.home
        return board.find(t)
    }

    /** Mueve al jugador actual. Devuelve el tesoro recogido o -1. */
    fun moveCurrent(to: Pos): Int {
        val p = currentPlayer
        require(to in board.reachable(p.pos)) { "Casilla no alcanzable: $to" }
        p.pos = to
        var found = -1
        val t = p.target
        if (t != null && board[to].treasure == t) {
            p.cards.removeFirst()
            p.collected++
            found = t
        }
        if (p.cards.isEmpty() && p.pos == p.home) winner = p
        return found
    }

    fun nextTurn() {
        current = (current + 1) % players.size
    }

    companion object {
        val HOMES = listOf(Pos(6, 0), Pos(0, 6), Pos(0, 0), Pos(6, 6))
    }
}
