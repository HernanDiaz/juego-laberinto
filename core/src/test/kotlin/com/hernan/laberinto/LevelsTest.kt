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
    fun losTresPrimerosNivelesSonGratis() {
        assertEquals(listOf(false, false, false, true, true, true, true, true), levels.map { it.premium })
    }

    @Test
    fun estrellas() {
        val l = levels[0]
        assertEquals(1, l.stars(l.roundsFor2 + 1, 99))
        assertEquals(2, l.stars(l.roundsFor2, l.coinsFor3 - 1))
        assertEquals(3, l.stars(l.roundsFor2, l.coinsFor3))
    }

    /**
     * Dificultad: una IA que juega "como una persona media" (habilidad 0,5; 0,8 en los niveles con
     * pistas, porque las pistas le dicen la mejor jugada) contra los rivales de cada nivel.
     * Imprime el porcentaje de victorias para ajustar aiSkill en levels.json.
     */
    @Test
    fun dificultadDeCadaNivel() {
        val rates = levels.map { level ->
            val human = if (level.hints) 0.8 else 0.5
            var wins = 0
            var totalRounds = 0
            val winRounds = ArrayList<Int>()
            val winCoins = ArrayList<Int>()
            val games = 40
            for (seed in 1..games) {
                val rng = Random(seed * 97 + level.index)
                val s = GameState(level.setup, rng)
                var turns = 0
                while (s.winner == null && turns < 3000) {
                    val skill = if (s.current == 0) human else level.setup.aiSkill
                    val plan = Ai.plan(s, rng, skill)
                    repeat(plan.rotations) { s.board.spare.rotateCw() }
                    s.push(plan.insert)
                    s.moveCurrent(s.board.path(s.currentPlayer.pos, plan.dest)!!)
                    s.nextTurn()
                    turns++
                }
                if (s.winner === s.players[0]) {
                    wins++
                    winRounds.add(s.round)
                    winCoins.add(s.players[0].coins)
                }
                totalRounds += s.round
            }
            val rate = wins * 100 / games
            println("DIFICULTAD nivel ${level.index + 1}: el jugador gana el $rate% (rivales=${level.setup.numAi}, " +
                "habilidad IA=${level.setup.aiSkill}, rondas media=${totalRounds / games}) | " +
                "cuando gana: rondas mediana=${winRounds.sorted().getOrNull(winRounds.size / 2)} " +
                "monedas mediana=${winCoins.sorted().getOrNull(winCoins.size / 2)} | " +
                "estrellas: rondas<=${level.roundsFor2} monedas>=${level.coinsFor3}")
            rate
        }
        assertTrue(rates[0] >= 70, "El primer nivel debe ser fácil de ganar (${rates[0]}%)")
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
