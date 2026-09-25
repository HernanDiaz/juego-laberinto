package com.hernan.laberinto

import com.hernan.laberinto.model.Ai
import com.hernan.laberinto.model.Board
import com.hernan.laberinto.model.GameState
import com.hernan.laberinto.model.Levels
import java.io.File
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LevelsTest {
    private val levels = Levels.parse(File("../assets/levels.json").readText())

    @Test
    fun hayOchoNivelesValidos() {
        assertEquals(8, levels.size)
        for (level in levels) {
            val b = Board.generate(Random(level.index), level.setup.board)
            val goals = (0 until Board.TREASURES).count { b.find(it) != null || b.spare.treasure == it }
            assertEquals(Board.TREASURES, goals, "Nivel ${level.index + 1}: faltan metas en el tablero")
            assertTrue(level.icon in 0 until Board.TREASURES)
        }
    }

    @Test
    fun estrellas() {
        val l = levels[0]
        assertEquals(1, l.stars(l.roundsFor2 + 1, 99))
        assertEquals(2, l.stars(l.roundsFor2, l.coinsFor3 - 1))
        assertEquals(3, l.stars(l.roundsFor2, l.coinsFor3))
    }

    /** Juega cada nivel con IAs; imprime rondas y monedas del ganador para ajustar las estrellas. */
    @Test
    fun todosLosNivelesSePuedenTerminar() {
        for (level in levels) {
            val rounds = ArrayList<Int>()
            val coins = ArrayList<Int>()
            for (seed in 1..20) {
                val s = GameState(level.setup, Random(seed * 31 + level.index))
                var turns = 0
                while (s.winner == null && turns < 3000) {
                    val plan = Ai.plan(s)
                    repeat(plan.rotations) { s.board.spare.rotateCw() }
                    s.push(plan.insert)
                    s.moveCurrent(s.board.path(s.currentPlayer.pos, plan.dest)!!)
                    s.nextTurn()
                    turns++
                }
                val w = s.winner
                assertTrue(w != null, "El nivel ${level.index + 1} (semilla $seed) no terminó")
                rounds.add(s.round)
                coins.add(w.coins)
            }
            rounds.sort()
            coins.sort()
            println("NIVEL ${level.index + 1}: rondas mediana=${rounds[rounds.size / 2]} p25=${rounds[rounds.size / 4]} " +
                "min=${rounds.first()} max=${rounds.last()} | monedas ganador mediana=${coins[coins.size / 2]} " +
                "p75=${coins[coins.size * 3 / 4]} | estrellas: rondas<=${level.roundsFor2} monedas>=${level.coinsFor3}")
        }
    }
}
