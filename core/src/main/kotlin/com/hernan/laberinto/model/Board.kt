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

    companion object {
        val ALL: List<Insert> = Side.entries.flatMap { s -> listOf(1, 3, 5).map { Insert(s, it) } }
    }
}

class Board(private val grid: Array<Array<Tile>>, var spare: Tile) {

    operator fun get(p: Pos) = grid[p.r][p.c]
    operator fun get(r: Int, c: Int) = grid[r][c]

    fun copy() = Board(Array(SIZE) { r -> Array(SIZE) { c -> grid[r][c].copy() } }, spare.copy())

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
    fun path(from: Pos, to: Pos): List<Pos>? = pathIn(bfs(from), to)

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

        /** Orientación de las 16 piezas fijas (filas y columnas pares), como en el juego de mesa. */
        private fun fixedMask(r: Int, c: Int): Int {
            val n = Dirs.N; val e = Dirs.E; val s = Dirs.S; val w = Dirs.W
            val last = SIZE - 1
            return when {
                r == 0 && c == 0 -> e or s
                r == 0 && c == last -> w or s
                r == last && c == 0 -> n or e
                r == last && c == last -> n or w
                r == 0 -> e or s or w
                r == last -> n or e or w
                c == 0 -> n or e or s
                c == last -> n or s or w
                r == 2 && c == 2 -> e or s or w
                r == 2 && c == 4 -> n or s or w
                r == 4 && c == 4 -> n or e or w
                else -> n or e or s
            }
        }

        fun isCorner(r: Int, c: Int) = (r == 0 || r == SIZE - 1) && (c == 0 || c == SIZE - 1)

        const val COINS = 6
        const val EVENTS = 4

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
         * Tablero aleatorio con la composición original:
         * 16 fijas (12 con tesoro) + 34 móviles: 12 rectas, 16 curvas (6 con tesoro), 6 en T (con tesoro).
         */
        fun generate(rng: Random): Board {
            val treasures = (0 until TREASURES).shuffled(rng).iterator()
            val movable = ArrayList<Tile>()
            repeat(12) { movable.add(Tile(STRAIGHT)) }
            repeat(10) { movable.add(Tile(CORNER)) }
            repeat(6) { movable.add(Tile(CORNER, treasures.next())) }
            repeat(6) { movable.add(Tile(TEE, treasures.next())) }
            movable.shuffle(rng)
            movable.forEach { t -> repeat(rng.nextInt(4)) { t.rotateCw() } }
            val it = movable.iterator()

            val grid = Array(SIZE) { r ->
                Array(SIZE) { c ->
                    if (r % 2 == 0 && c % 2 == 0) {
                        Tile(fixedMask(r, c), if (isCorner(r, c)) -1 else treasures.next(), fixed = true)
                    } else {
                        it.next()
                    }
                }
            }
            val board = Board(grid, it.next())
            // Sucesos y monedas en casillas sin meta
            val free = board.emptyCells().shuffled(rng)
            free.take(EVENTS).forEach { board[it].item = Item.EVENT }
            free.drop(EVENTS).take(COINS).forEach { board[it].item = Item.COIN }
            return board
        }
    }
}
