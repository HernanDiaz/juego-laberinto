package com.hernan.laberinto

import com.hernan.laberinto.model.Ai
import com.hernan.laberinto.model.Board
import com.hernan.laberinto.model.Dirs
import com.hernan.laberinto.model.GameState
import com.hernan.laberinto.model.Goals
import com.hernan.laberinto.model.Insert
import com.hernan.laberinto.model.Item
import com.hernan.laberinto.model.Pos
import com.hernan.laberinto.model.Side
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BoardTest {
    @Test
    fun rotacion() {
        assertEquals(Dirs.E or Dirs.W, Dirs.rotateCw(Dirs.N or Dirs.S))
        assertEquals(Dirs.N or Dirs.E, Dirs.rotateCw(Dirs.N or Dirs.W))
    }

    @Test
    fun tableroTiene24TesorosUnicos() {
        val b = Board.generate(Random(1))
        val found = (0 until Board.TREASURES).mapNotNull { b.find(it) }
        val onSpare = if (b.spare.treasure >= 0) 1 else 0
        assertEquals(Board.TREASURES, found.toSet().size + onSpare)
    }

    @Test
    fun empujarDesplazaFichasYDaLaVuelta() {
        val b = Board.generate(Random(2))
        val spare = b.spare
        val lastTile = b[1, 6]
        val pawns = mutableListOf(Pos(1, 6), Pos(1, 2), Pos(0, 0))
        b.push(Insert(Side.LEFT, 1), pawns)
        assertTrue(b[1, 0] === spare)
        assertTrue(b.spare === lastTile)
        assertEquals(listOf(Pos(1, 0), Pos(1, 3), Pos(0, 0)), pawns)
    }

    @Test
    fun noSePuedeDeshacerElEmpuje() {
        val s = GameState(1, 3, Random(3))
        s.push(Insert(Side.TOP, 2))
        assertFalse(Insert(Side.BOTTOM, 2) in s.allowedInserts())
        assertEquals(s.board.config.inserts.size - 1, s.allowedInserts().size)
    }

    @Test
    fun lasPiezasFijasNoSeMueven() {
        val b = Board.generate(Random(8))
        val fixed = b.allCells().filter { b.config.isFixed(it.r, it.c) }
        assertEquals(9, fixed.size)
        val before = fixed.associateWith { b[it] }
        for (ins in b.config.inserts) b.push(ins, mutableListOf())
        for ((pos, tile) in before) assertTrue(b[pos] === tile, "La pieza fija de $pos se ha movido")
        assertEquals(16, b.config.inserts.size)
    }

    @Test
    fun lasMetasSeRepartenPorEtapas() {
        val s = GameState(1, 4, Random(4))
        val all = s.players.flatMap { p -> p.stages.flatten() }
        assertEquals(all.size, all.toSet().size, "Ninguna meta se repite entre jugadores")
        for (p in s.players) {
            p.stages.forEachIndexed { stage, goals ->
                assertEquals(4, goals.size)
                assertTrue(goals.all { Goals.stageOf(it) == stage })
            }
            // Al principio solo cuentan las metas de juventud
            assertEquals(p.stages[0], p.activeGoals)
        }
    }

    @Test
    fun soloSeConsigueUnaMetaDeLaEtapaActual() {
        val s = GameState(1, 1, Random(5))
        val p = s.currentPlayer
        val adultGoal = p.stages[1].first()
        // Colocamos la meta de adultez justo donde está el jugador: no debe contar aún
        p.coins = 10 // para que el precio no influya
        s.board[p.pos].treasure = adultGoal
        assertEquals(-1, s.moveCurrent(p.pos).goal)
        assertEquals(0, p.collected)
        // La de juventud sí
        val youthGoal = p.stages[0].first()
        s.board[p.pos].treasure = youthGoal
        assertEquals(youthGoal, s.moveCurrent(p.pos).goal)
        assertEquals(1, p.stage)
        assertEquals(-1, s.board[p.pos].treasure, "La meta conseguida desaparece del tablero")
    }

    /** Partida con el jugador actual quieto sobre una casilla y la meta indicada como única pendiente. */
    private fun stateWithGoal(goal: Int, seed: Int = 6): GameState {
        val s = GameState(1, 1, Random(seed))
        val p = s.currentPlayer
        p.stages.forEach { it.clear() }
        p.stages[Goals.stageOf(goal)].add(goal)
        s.board[p.pos].treasure = goal
        return s
    }

    @Test
    fun lasMetasDePagoNecesitanMonedas() {
        val moto = 2
        val s = stateWithGoal(moto)
        val p = s.currentPlayer
        val r = s.moveCurrent(p.pos)
        assertEquals(moto, r.blockedGoal)
        assertEquals(Goals.COST[moto], r.missingCoins)
        assertEquals(0, p.collected)

        p.coins = Goals.COST[moto] + 1
        assertEquals(moto, s.moveCurrent(p.pos).goal)
        assertEquals(1, p.coins, "Se descuenta el precio")
    }

    @Test
    fun elPrimerSueldoDaMonedas() {
        val sueldo = 7
        val s = stateWithGoal(sueldo)
        val r = s.moveCurrent(s.currentPlayer.pos)
        assertEquals(sueldo, r.goal)
        assertEquals(Goals.REWARD[sueldo], s.currentPlayer.coins)
    }

    @Test
    fun lasMonedasSeRecogenPorElCaminoYReaparecen() {
        // Busca una partida en la que el jugador pueda moverse al menos una casilla
        val s = (1..50).map { GameState(1, 1, Random(it)) }
            .first { g -> g.board.reachable(g.currentPlayer.pos).size > 1 }
        val p = s.currentPlayer
        val dest = s.board.reachable(p.pos).first { it != p.pos }
        val path = s.board.path(p.pos, dest)!!
        path.drop(1).forEach { s.board[it].treasure = -1; s.board[it].item = Item.COIN }
        val coinsBefore = s.board.allCells().count { s.board[it].item == Item.COIN }

        val r = s.moveCurrent(path)
        assertEquals(path.size - 1, r.coinsPicked)
        assertEquals(path.size - 1, p.coins)
        assertEquals(coinsBefore, s.board.allCells().count { s.board[it].item == Item.COIN }, "Reaparecen en otro sitio")
    }

    @Test
    fun losSucesosSoloOcurrenSiTeMueves() {
        val s = (1..50).map { GameState(1, 1, Random(it)) }
            .first { g -> g.board.reachable(g.currentPlayer.pos).size > 1 }
        val p = s.currentPlayer
        val dest = s.board.reachable(p.pos).first { it != p.pos }
        s.board[dest].treasure = -1
        s.board[dest].item = Item.EVENT
        assertNotNull(s.moveCurrent(dest).event)
        // Quedarse quieto sobre un suceso no lo repite
        assertEquals(null, s.moveCurrent(p.pos).event)
    }

    @Test
    fun lasPartidasTienenUnaDuracionRazonable() {
        // 1 rival y 2 metas por etapa (la opción por defecto): medimos rondas por partida
        val rounds = (1..30).map { seed ->
            val s = GameState(1, 2, Random(seed))
            var turns = 0
            while (s.winner == null && turns < 2000) {
                val plan = Ai.plan(s)
                repeat(plan.rotations) { s.board.spare.rotateCw() }
                s.push(plan.insert)
                s.moveCurrent(s.board.path(s.currentPlayer.pos, plan.dest)!!)
                s.nextTurn()
                turns++
            }
            turns / 2.0
        }
        println("Rondas por partida: media=${rounds.average()} min=${rounds.min()} max=${rounds.max()}")
        assertTrue(rounds.average() < 40, "Las partidas duran demasiado: ${rounds.average()} rondas")
    }

    @Test
    fun partidaEntreIAsTermina() {
        repeat(5) { seed ->
            val s = GameState(3, 2, Random(seed))
            var turns = 0
            while (s.winner == null && turns < 3000) {
                val plan = Ai.plan(s)
                repeat(plan.rotations) { s.board.spare.rotateCw() }
                s.push(plan.insert)
                assertNotNull(s.board.path(s.currentPlayer.pos, plan.dest))
                s.moveCurrent(s.board.path(s.currentPlayer.pos, plan.dest)!!)
                s.nextTurn()
                turns++
            }
            assertNotNull(s.winner, "La partida $seed no terminó")
        }
    }
}
