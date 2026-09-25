package com.hernan.laberinto.model

import kotlin.random.Random

data class AiPlan(val rotations: Int, val insert: Insert, val dest: Pos)

/**
 * IA sencilla: prueba las 4 rotaciones x 11 entradas posibles y elige la jugada que
 * la deja sobre alguna de sus metas activas (o lo más cerca posible), intentando no
 * dejarle el camino libre al siguiente jugador.
 */
object Ai {
    fun plan(state: GameState, rng: Random = state.rng): AiPlan {
        val me = state.currentPlayer
        val next = state.players[(me.index + 1) % state.players.size]
        var best: AiPlan? = null
        var bestScore = Double.NEGATIVE_INFINITY

        for (rot in 0..3) {
            for (ins in state.allowedInserts()) {
                val b = state.board.copy()
                repeat(rot) { b.spare.rotateCw() }
                val positions = state.players.map { it.pos }.toMutableList()
                b.push(ins, positions)

                val start = positions[me.index]
                val reach = b.reachable(start)
                val goals = GameState.goalsOf(me, b)

                val dest: Pos
                var score: Double
                val reachable = goals.filter { it in reach }
                if (reachable.isNotEmpty()) {
                    dest = reachable.minBy { it.dist(start) }
                    score = 1000.0
                } else if (goals.isNotEmpty()) {
                    dest = reach.minBy { p -> goals.minOf { p.dist(it) } }
                    score = 100.0 - goals.minOf { dest.dist(it) } * 10
                } else {
                    // Sus metas están en la pieza suelta: se coloca en el centro para tener opciones.
                    dest = reach.minBy { it.dist(Pos(3, 3)) }
                    score = 0.0
                }
                score += reach.size * 0.2

                if (next !== me) {
                    val nextReach = b.reachable(positions[next.index])
                    if (GameState.goalsOf(next, b).any { it in nextReach }) score -= 15
                }
                score += rng.nextDouble() * 3

                if (score > bestScore) {
                    bestScore = score
                    best = AiPlan(rot, ins, dest)
                }
            }
        }
        return best!!
    }
}
