package com.hernan.laberinto.model

import kotlin.random.Random

/** Metas de vida: 24, agrupadas en 3 etapas de 8 (juventud, adultez, madurez). */
object Goals {
    const val STAGES = 3
    const val PER_STAGE = Board.TREASURES / STAGES

    fun stageOf(goal: Int) = goal / PER_STAGE
    fun ofStage(stage: Int) = (stage * PER_STAGE until (stage + 1) * PER_STAGE).toList()

    /** Precio en monedas de cada meta (0 = gratis). El orden es el de treasure.N en los textos. */
    val COST = intArrayOf(
        0, 1, 2, 1, 0, 1, 0, 0, // diploma, guitarra, moto, videoconsola, mochila, carnet, monopatín, primer sueldo
        0, 3, 0, 2, 4, 1, 1, 0, // trabajo, coche, pareja, boda, casa, bebé, perro, ascenso
        3, 4, 1, 0, 2, 0, 0, 1, // viaje, velero, huerto, gato, cámara, ahorros, libro, pesca
    )

    /** Monedas que se ganan al conseguir la meta (primer sueldo, trabajo, ascenso). */
    val REWARD = IntArray(Board.TREASURES).also {
        it[7] = 3
        it[8] = 3
        it[15] = 4
    }
}

/** Forma de jugar de cada rival controlado por la IA. */
enum class Personality { AMBITIOUS, ADVENTUROUS, CALM }

/** Sucesos de las casillas "?" y las monedas que dan o quitan. */
enum class EventType(val coins: Int, val weight: Int) {
    INHERITANCE(3, 3), LOTTERY(5, 1), FINE(-2, 3), BILL(-1, 2), GIFT(1, 3), SWAP(0, 2)
}

class Player(val index: Int, val isAi: Boolean, val home: Pos, val personality: Personality? = null) {
    var pos = home
    var coins = 0

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

/** Configuración de una partida: la de un nivel o la de la partida libre. */
data class GameSetup(
    val numAi: Int = 1,
    val goalsPerStage: Int = 2,
    /** Personalidad de cada rival; null = al azar. */
    val personalities: List<Personality>? = null,
    val board: BoardConfig = BoardConfig(),
    val startCoins: Int = 0,
    /** Monedas que se suman al precio de cada meta de pago. */
    val costExtra: Int = 0,
    /** Pesos de los sucesos que cambian respecto a los normales (0 = no sale). */
    val eventWeights: Map<EventType, Int> = emptyMap(),
)

/** Lo que ha pasado en un movimiento, para mostrarlo en pantalla. */
data class MoveResult(
    val goal: Int = -1,
    val reward: Int = 0,
    val coinsPicked: Int = 0,
    /** Meta a la que ha llegado sin dinero suficiente, y cuántas monedas le faltan. */
    val blockedGoal: Int = -1,
    val missingCoins: Int = 0,
    val event: EventType? = null,
    val eventCoins: Int = 0,
    val swappedWith: Player? = null,
)

class GameState(val setup: GameSetup, val rng: Random = Random.Default) {
    constructor(numAi: Int, goalsPerStage: Int, rng: Random = Random.Default) :
        this(GameSetup(numAi = numAi, goalsPerStage = goalsPerStage), rng)

    val board = Board.generate(rng, setup.board)
    val players: List<Player>
    var current = 0
        private set
    var lastInsert: Insert? = null
        private set
    var winner: Player? = null
        private set

    /** Ronda actual: empieza en 1 y sube cada vez que vuelve a tocarle al jugador humano. */
    var round = 1
        private set

    init {
        val numAi = setup.numAi
        val goalsPerStage = setup.goalsPerStage
        require(numAi in 1..3)
        val count = numAi + 1
        require(goalsPerStage in 1..maxGoalsPerStage(numAi))
        val personalities = setup.personalities ?: Personality.entries.shuffled(rng)
        require(personalities.size >= numAi) { "Faltan personalidades para los rivales" }
        players = (0 until count).map { i ->
            Player(i, isAi = i != 0, home = HOMES[i], personality = if (i == 0) null else personalities[i - 1])
        }
        players.forEach { it.coins = setup.startCoins }
        for (s in 0 until Goals.STAGES) {
            val pool = Goals.ofStage(s).shuffled(rng)
            for (i in 0 until goalsPerStage) for (p in players) p.stages[s].add(pool[i * count + p.index])
        }
    }

    /** Precio de una meta en esta partida. */
    fun costOf(goal: Int) = Goals.COST[goal].let { if (it > 0) it + setup.costExtra else 0 }

    private fun eventWeight(e: EventType) = setup.eventWeights[e] ?: e.weight

    val currentPlayer get() = players[current]

    /** Todas las entradas menos la que desharía el último empuje. */
    fun allowedInserts() = board.config.inserts.filter { it != lastInsert?.opposite() }

    fun push(ins: Insert) {
        require(ins in allowedInserts()) { "Empuje no permitido: $ins" }
        val positions = players.map { it.pos }.toMutableList()
        board.push(ins, positions)
        players.forEachIndexed { i, p -> p.pos = positions[i] }
        lastInsert = ins
    }

    /** Casillas a las que le interesa ir: sus metas activas en el tablero, o su casa si se jubila. */
    fun goalsOf(p: Player): List<Pos> = goalsOf(p, board)

    /** Mueve al jugador actual por el camino más corto hasta [to]. */
    fun moveCurrent(to: Pos): MoveResult {
        val path = board.path(currentPlayer.pos, to) ?: error("Casilla no alcanzable: $to")
        return moveCurrent(path)
    }

    /**
     * Mueve al jugador actual por [path] (empieza en su casilla). Recoge las monedas del camino,
     * consigue la meta de destino si es de su etapa y puede pagarla, y si termina en un "?"
     * (habiéndose movido) ocurre un suceso.
     */
    fun moveCurrent(path: List<Pos>): MoveResult {
        val p = currentPlayer
        require(path.first() == p.pos) { "El camino debe empezar en la ficha" }
        val to = path.last()
        require(to in board.reachable(p.pos)) { "Casilla no alcanzable: $to" }

        // Monedas del camino
        var picked = 0
        for (cell in path.drop(1)) {
            if (board[cell].item == Item.COIN) {
                board[cell].item = Item.NONE
                picked++
            }
        }
        p.coins += picked
        p.pos = to

        // Meta
        var result = MoveResult(coinsPicked = picked)
        val tile = board[to]
        if (tile.treasure >= 0 && tile.treasure in p.activeGoals) {
            val goal = tile.treasure
            val cost = costOf(goal)
            if (p.coins >= cost) {
                p.coins -= cost
                p.coins += Goals.REWARD[goal]
                p.stages[Goals.stageOf(goal)].remove(goal)
                p.collected++
                tile.treasure = -1 // la meta conseguida desaparece del tablero
                result = result.copy(goal = goal, reward = Goals.REWARD[goal])
            } else {
                result = result.copy(blockedGoal = goal, missingCoins = cost - p.coins)
            }
        }

        // Suceso
        if (tile.item == Item.EVENT && path.size > 1) result = applyEvent(p, result)

        // Las monedas recogidas reaparecen en otras casillas libres
        repeat(picked) { respawnCoin() }

        // Gana quien se jubila (una mudanza podría dejar a otro jugador en su casa)
        winner = if (p.retiring && p.pos == p.home) p else players.firstOrNull { it.retiring && it.pos == it.home }
        return result
    }

    private fun applyEvent(p: Player, result: MoveResult): MoveResult {
        val others = players.filter { it !== p }
        val options = EventType.entries.filter { eventWeight(it) > 0 && (it != EventType.SWAP || others.isNotEmpty()) }
        if (options.isEmpty()) return result
        var roll = rng.nextInt(options.sumOf { eventWeight(it) })
        val event = options.first { e -> (roll < eventWeight(e)).also { roll -= eventWeight(e) } }
        return when (event) {
            EventType.SWAP -> {
                val other = others.random(rng)
                val tmp = p.pos
                p.pos = other.pos
                other.pos = tmp
                result.copy(event = event, swappedWith = other)
            }
            else -> {
                val before = p.coins
                p.coins = maxOf(0, p.coins + event.coins)
                result.copy(event = event, eventCoins = p.coins - before)
            }
        }
    }

    private fun respawnCoin() {
        val occupied = players.map { it.pos }.toSet()
        val free = board.emptyCells().filter { it !in occupied }
        if (free.isNotEmpty()) board[free.random(rng)].item = Item.COIN
    }

    fun nextTurn() {
        current = (current + 1) % players.size
        if (current == 0) round++
    }

    companion object {
        val HOMES = listOf(Pos(6, 0), Pos(0, 6), Pos(0, 0), Pos(6, 6))

        fun maxGoalsPerStage(numAi: Int) = Goals.PER_STAGE / (numAi + 1)

        fun goalsOf(p: Player, board: Board): List<Pos> =
            if (p.retiring) listOf(p.home) else p.activeGoals.mapNotNull { board.find(it) }
    }
}
