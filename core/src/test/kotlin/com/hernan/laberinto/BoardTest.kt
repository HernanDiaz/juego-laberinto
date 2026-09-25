package com.hernan.laberinto

import com.hernan.laberinto.model.Ai
import com.hernan.laberinto.model.Board
import com.hernan.laberinto.model.Dirs
import com.hernan.laberinto.model.GameState
import com.hernan.laberinto.model.Insert
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
        s.push(Insert(Side.TOP, 3))
        assertFalse(Insert(Side.BOTTOM, 3) in s.allowedInserts())
        assertEquals(11, s.allowedInserts().size)
    }

    @Test
    fun partidaEntreIAsTermina() {
        repeat(5) { seed ->
            val s = GameState(3, 6, Random(seed))
            var turns = 0
            while (s.winner == null && turns < 3000) {
                val plan = Ai.plan(s)
                repeat(plan.rotations) { s.board.spare.rotateCw() }
                s.push(plan.insert)
                assertNotNull(s.board.path(s.currentPlayer.pos, plan.dest))
                s.moveCurrent(plan.dest)
                s.nextTurn()
                turns++
            }
            assertNotNull(s.winner, "La partida $seed no terminó")
        }
    }
}
