package com.hernan.laberinto.model

import kotlin.random.Random

/** Direcciones como bits de una máscara de conexiones. */
object Dirs {
    const val N = 1
    const val E = 2
    const val S = 4
    const val W = 8
    val ALL = intArrayOf(N, E, S, W)

    fun dr(d: Int) = when (d) { N -> -1; S -> 1; else -> 0 }
    fun dc(d: Int) = when (d) { E -> 1; W -> -1; else -> 0 }
    fun opposite(d: Int) = when (d) { N -> S; S -> N; E -> W; else -> E }

    /** Gira una máscara 90° en sentido horario (N->E->S->W). */
    fun rotateCw(mask: Int) = ((mask shl 1) or (mask shr 3)) and 15
}

/** Objetos que puede haber en una casilla sin meta. */
object Item {
    const val NONE = 0
    const val COIN = 1
    const val EVENT = 2
}

class Tile(var mask: Int, var treasure: Int = -1, val fixed: Boolean = false, var item: Int = Item.NONE) {
    fun rotateCw() {
        mask = Dirs.rotateCw(mask)
    }

    fun opens(d: Int) = mask and d != 0
    fun copy() = Tile(mask, treasure, fixed, item)
}

data class Pos(val r: Int, val c: Int) {
    fun inBoard() = r in 0 until Board.SIZE && c in 0 until Board.SIZE
    fun dist(o: Pos) = kotlin.math.abs(r - o.r) + kotlin.math.abs(c - o.c)
}

/** Lado del tablero por el que se introduce la pieza suelta. */
enum class Side { LEFT, RIGHT, TOP, BOTTOM }

data class Insert(val side: Side, val index: Int) {
    fun opposite() = Insert(
        when (side) {
            Side.LEFT -> Side.RIGHT
            Side.RIGHT -> Side.LEFT
            Side.TOP -> Side.BOTTOM
            Side.BOTTOM -> Side.TOP
        },
        index,
    )

    /** Casillas de la línea en el orden del empuje: la primera recibe la pieza nueva. */
    fun cells(): List<Pos> = when (side) {
        Side.LEFT -> (0 until Board.SIZE).map { Pos(index, it) }
        Side.RIGHT -> (Board.SIZE - 1 downTo 0).map { Pos(index, it) }
        Side.TOP -> (0 until Board.SIZE).map { Pos(it, index) }
        Side.BOTTOM -> (Board.SIZE - 1 downTo 0).map { Pos(it, index) }
    }

}

/**
 * Cómo se construye un tablero (cada nivel puede tener el suyo).
 * - [pushLines]: filas y columnas que se pueden empujar. Las casillas cuya fila y columna
 *   no se empujan nunca se mueven (piezas fijas).
 * - Pesos de cada tipo de pieza móvil; se reparten en proporción entre las piezas necesarias.
 */
data class BoardConfig(
    val pushLines: List<Int> = listOf(1, 2, 4, 5),
    val straight: Int = 10,
    val corner: Int = 14,
    val tee: Int = 10,
    val cross: Int = 3,
    val deadEnd: Int = 4,
    val coins: Int = 6,
    val events: Int = 4,
) {
    /** Entradas por las que se puede meter la pieza suelta. */
    val inserts: List<Insert> = Side.entries.flatMap { s -> pushLines.map { Insert(s, it) } }

    fun isFixed(r: Int, c: Int) = r !in pushLines && c !in pushLines
}

class Board(private val grid: Array<Array<Tile>>, var spare: Tile, val config: BoardConfig = BoardConfig()) {

    operator fun get(p: Pos) = grid[p.r][p.c]
    operator fun get(r: Int, c: Int) = grid[r][c]

    fun copy() = Board(Array(SIZE) { r -> Array(SIZE) { c -> grid[r][c].copy() } }, spare.copy(), config)

    /**
     * Introduce la pieza suelta por [ins]. La pieza expulsada pasa a ser la nueva pieza suelta.
     * Las fichas de la línea se desplazan con sus casillas; la que sale del tablero
     * reaparece en la pieza recién introducida.
     */
    fun push(ins: Insert, pawns: MutableList<Pos>) {
        val cells = ins.cells()
        val ejected = this[cells.last()]
        for (k in cells.size - 1 downTo 1) {
            val to = cells[k]
            val from = cells[k - 1]
            grid[to.r][to.c] = grid[from.r][from.c]
        }
        grid[cells[0].r][cells[0].c] = spare
        spare = ejected
        for (i in pawns.indices) {
            val k = cells.indexOf(pawns[i])
            if (k >= 0) pawns[i] = cells[(k + 1) % cells.size]
        }
    }

    fun connected(p: Pos, d: Int): Boolean {
        val q = Pos(p.r + Dirs.dr(d), p.c + Dirs.dc(d))
        return q.inBoard() && this[p].opens(d) && this[q].opens(Dirs.opposite(d))
    }

    /** Árbol de caminos más cortos desde [from]: casilla alcanzable -> casilla anterior. */
    fun paths(from: Pos): Map<Pos, Pos?> = bfs(from)

    private fun bfs(from: Pos): Map<Pos, Pos?> {
        val prev = LinkedHashMap<Pos, Pos?>()
        prev[from] = null
        val queue = ArrayDeque<Pos>()
        queue.add(from)
        while (queue.isNotEmpty()) {
            val p = queue.removeFirst()
            for (d in Dirs.ALL) {
                if (!connected(p, d)) continue
                val n = Pos(p.r + Dirs.dr(d), p.c + Dirs.dc(d))
                if (n !in prev) {
                    prev[n] = p
                    queue.add(n)
                }
            }
        }
        return prev
    }

    fun reachable(from: Pos): Set<Pos> = bfs(from).keys

    /** Camino más corto de [from] a [to], ambos incluidos, o null si no están conectados. */
    /**
     * Camino más corto de [from] a [to] (ambos incluidos), o null si no están conectados.
     * Si hay varios igual de cortos, elige el que pasa por más monedas: es lo que espera
     * el jugador al ver monedas por el camino.
     */
    fun path(from: Pos, to: Pos): List<Pos>? {
        val tree = bfs(from)
        if (to !in tree) return null
        // Distancias desde el origen (el orden del BFS ya es por distancia creciente)
        val dist = HashMap<Pos, Int>()
        for ((p, prev) in tree) dist[p] = if (prev == null) 0 else dist.getValue(prev) + 1
        val coins = HashMap<Pos, Int>()
        val parent = HashMap<Pos, Pos?>()
        for (p in tree.keys) {
            val own = if (p != from && this[p].item == Item.COIN) 1 else 0
            if (p == from) {
                coins[p] = 0
                parent[p] = null
                continue
            }
            var best: Pos? = null
            for (d in Dirs.ALL) {
                if (!connected(p, d)) continue
                val q = Pos(p.r + Dirs.dr(d), p.c + Dirs.dc(d))
                if (dist[q] != dist.getValue(p) - 1) continue
                if (best == null || coins.getValue(q) > coins.getValue(best)) best = q
            }
            parent[p] = best
            coins[p] = coins.getValue(best!!) + own
        }
        return pathIn(parent, to)
    }

    /** Casillas vacías (sin meta ni objeto) donde puede aparecer una moneda nueva. */
    fun emptyCells(): List<Pos> = allCells().filter { p ->
        val t = this[p]
        t.treasure < 0 && t.item == Item.NONE && !isCorner(p.r, p.c)
    }

    fun allCells(): List<Pos> = (0 until SIZE).flatMap { r -> (0 until SIZE).map { c -> Pos(r, c) } }

    /** Posición del tesoro en el tablero, o null si está en la pieza suelta. */
    fun find(treasure: Int): Pos? {
        for (r in 0 until SIZE) for (c in 0 until SIZE) {
            if (grid[r][c].treasure == treasure) return Pos(r, c)
        }
        return null
    }

    companion object {
        const val SIZE = 7
        const val TREASURES = 24

        private const val STRAIGHT = Dirs.N or Dirs.S
        private const val CORNER = Dirs.N or Dirs.E
        private const val TEE = Dirs.N or Dirs.E or Dirs.S
        private const val CROSS = Dirs.N or Dirs.E or Dirs.S or Dirs.W
        private const val DEAD_END = Dirs.N

        /**
         * Piezas fijas: esquinas (casas) hacia dentro, bordes en T hacia dentro, plaza central en cruz
         * y el resto de fijas interiores al azar entre cruces y T.
         */
        private fun fixedTile(r: Int, c: Int, rng: Random): Tile {
            val n = Dirs.N; val e = Dirs.E; val s = Dirs.S; val w = Dirs.W
            val last = SIZE - 1
            val mask = when {
                r == 0 && c == 0 -> e or s
                r == 0 && c == last -> w or s
                r == last && c == 0 -> n or e
                r == last && c == last -> n or w
                r == 0 -> e or s or w
                r == last -> n or e or w
                c == 0 -> n or e or s
                c == last -> n or s or w
                r == SIZE / 2 && c == SIZE / 2 -> CROSS
                rng.nextInt(3) == 0 -> CROSS
                else -> (0 until rng.nextInt(4)).fold(TEE) { m, _ -> Dirs.rotateCw(m) }
            }
            return Tile(mask, fixed = true)
        }

        /** Reparte [total] piezas en proporción a los pesos (método del mayor resto). */
        private fun distribute(total: Int, weights: List<Int>): List<Int> {
            val sum = weights.sum()
            val exact = weights.map { it.toDouble() * total / sum }
            val counts = exact.map { it.toInt() }.toMutableList()
            val order = exact.indices.sortedByDescending { exact[it] - counts[it] }
            var missing = total - counts.sum()
            for (k in order) {
                if (missing == 0) break
                counts[k]++
                missing--
            }
            return counts
        }

        fun isCorner(r: Int, c: Int) = (r == 0 || r == SIZE - 1) && (c == 0 || c == SIZE - 1)


        /** Camino hasta [to] dentro de un árbol de [paths], ambos extremos incluidos. */
        fun pathIn(prev: Map<Pos, Pos?>, to: Pos): List<Pos>? {
            if (to !in prev) return null
            val out = ArrayList<Pos>()
            var cur: Pos? = to
            while (cur != null) {
                out.add(cur)
                cur = prev[cur]
            }
            out.reverse()
            return out
        }

        /**
         * Tablero aleatorio según [config]: piezas fijas donde ni la fila ni la columna se empujan,
         * piezas móviles repartidas según los pesos, 24 metas (primero en los callejones sin
         * salida, luego en piezas que no sean rectas) y las monedas y sucesos en casillas libres.
         */
        fun generate(rng: Random, config: BoardConfig = BoardConfig()): Board {
            val movableCells = (0 until SIZE).sumOf { r -> (0 until SIZE).count { c -> !config.isFixed(r, c) } }
            val counts = distribute(
                movableCells + 1, // + la pieza suelta
                listOf(config.straight, config.corner, config.tee, config.cross, config.deadEnd),
            )
            val masks = listOf(STRAIGHT, CORNER, TEE, CROSS, DEAD_END)
            val movable = ArrayList<Tile>()
            counts.forEachIndexed { k, n -> repeat(n) { movable.add(Tile(masks[k])) } }
            movable.shuffle(rng)
            movable.forEach { t -> repeat(rng.nextInt(4)) { t.rotateCw() } }
            val it = movable.iterator()

            val grid = Array(SIZE) { r ->
                Array(SIZE) { c -> if (config.isFixed(r, c)) fixedTile(r, c, rng) else it.next() }
            }
            val board = Board(grid, it.next(), config)

            // Metas: callejones sin salida primero, luego el resto de piezas que no son rectas
            val center = Pos(SIZE / 2, SIZE / 2)
            val candidates = board.allCells().filter { !isCorner(it.r, it.c) && it != center }.map { board[it] } + board.spare
            val deadEnds = candidates.filter { Integer.bitCount(it.mask) == 1 }.shuffled(rng)
            val others = candidates.filter { Integer.bitCount(it.mask) > 1 }.shuffled(rng)
                .sortedBy { if (it.mask == STRAIGHT || it.mask == Dirs.rotateCw(STRAIGHT)) 1 else 0 }
            val goals = (0 until TREASURES).shuffled(rng)
            (deadEnds + others).take(TREASURES).forEachIndexed { k, tile -> tile.treasure = goals[k] }

            // Sucesos y monedas en casillas sin meta
            val free = board.emptyCells().shuffled(rng)
            free.take(config.events).forEach { board[it].item = Item.EVENT }
            free.drop(config.events).take(config.coins).forEach { board[it].item = Item.COIN }
            return board
        }
    }
}
