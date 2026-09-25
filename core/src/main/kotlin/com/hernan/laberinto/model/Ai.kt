package com.hernan.laberinto.model

import kotlin.random.Random

data class AiPlan(val rotations: Int, val insert: Insert, val dest: Pos)

/**
 * IA sencilla: prueba las 4 rotaciones x 11 entradas posibles y elige la jugada que
 * la deja sobre su objetivo o lo más cerca posible, intentando no dejarle el camino
 * libre al siguiente jugador.
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
                val goal = me.target?.let { b.find(it) } ?: if (me.target == null) me.home else null

                val dest: Pos
                var score: Double
                if (goal != null && goal in reach) {
                    dest = goal
                    score = 1000.0
                } else if (goal != null) {
                    dest = reach.minBy { it.dist(goal) }
                    score = 100.0 - dest.dist(goal) * 10
                } else {
                    // Su tesoro está en la pieza suelta: se coloca en el centro para tener opciones.
                    dest = reach.minBy { it.dist(Pos(3, 3)) }
                    score = 0.0
                }
                score += reach.size * 0.2

                if (next !== me) {
                    val nextGoal = next.target?.let { b.find(it) } ?: if (next.target == null) next.home else null
                    if (nextGoal != null && nextGoal in b.reachable(positions[next.index])) score -= 15
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
