package com.hernan.laberinto.model

import kotlin.random.Random

data class AiPlan(val rotations: Int, val insert: Insert, val dest: Pos)

/**
 * IA: prueba las 4 rotaciones x 11 entradas posibles y, para cada una, todas las casillas
 * alcanzables. Puntúa conseguir una meta que pueda pagar, las monedas del camino, los sucesos
 * y acercarse a sus objetivos; y penaliza dejarle el camino libre al siguiente jugador.
 * Cuánto pesa cada cosa depende de su personalidad.
 */
object Ai {
    private class Weights(val coin: Double, val event: Double, val hinder: Double)

    private fun weights(p: Personality?) = when (p) {
        Personality.AMBITIOUS -> Weights(coin = 40.0, event = 5.0, hinder = 15.0)
        Personality.ADVENTUROUS -> Weights(coin = 15.0, event = 60.0, hinder = 5.0)
        Personality.CALM -> Weights(coin = 20.0, event = -25.0, hinder = 25.0)
        null -> Weights(coin = 25.0, event = 0.0, hinder = 15.0)
    }

    fun plan(state: GameState, rng: Random = state.rng): AiPlan {
        val me = state.currentPlayer
        val next = state.players[(me.index + 1) % state.players.size]
        val w = weights(me.personality)
        var best: AiPlan? = null
        var bestScore = Double.NEGATIVE_INFINITY

        for (rot in 0..3) {
            for (ins in state.allowedInserts()) {
                val b = state.board.copy()
                repeat(rot) { b.spare.rotateCw() }
                val positions = state.players.map { it.pos }.toMutableList()
                b.push(ins, positions)

                val start = positions[me.index]
                val tree = b.paths(start)
                val targets = targetsOf(me, b)

                var hinder = 0.0
                if (next !== me) {
                    val nextReach = b.reachable(positions[next.index])
                    if (GameState.goalsOf(next, b).any { it in nextReach }) hinder = w.hinder
                }

                for (dest in tree.keys) {
                    val path = Board.pathIn(tree, dest)!!
                    val coinsOnPath = path.drop(1).count { b[it].item == Item.COIN }
                    val tile = b[dest]
                    var score = coinsOnPath * w.coin - hinder

                    val goal = tile.treasure
                    if (goal >= 0 && goal in me.activeGoals && me.coins + coinsOnPath >= Goals.COST[goal]) {
                        score += 1000.0 + Goals.REWARD[goal] * 20
                    }
                    if (me.retiring && dest == me.home) score += 1000.0
                    if (tile.item == Item.EVENT && dest != start) score += w.event
                    if (targets.isNotEmpty()) score -= 10.0 * targets.minOf { dest.dist(it) }
                    score += rng.nextDouble() * 3

                    if (score > bestScore) {
                        bestScore = score
                        best = AiPlan(rot, ins, dest)
                    }
                }
            }
        }
        return best!!
    }

    /**
     * Hacia dónde conviene acercarse: su casa si se jubila; si no, las metas activas que ya puede
     * pagar; y si no puede pagar ninguna, las monedas del tablero.
     */
    private fun targetsOf(me: Player, b: Board): List<Pos> {
        if (me.retiring) return listOf(me.home)
        val affordable = me.activeGoals.filter { Goals.COST[it] <= me.coins }.mapNotNull { b.find(it) }
        if (affordable.isNotEmpty()) return affordable
        val coins = b.allCells().filter { b[it].item == Item.COIN }
        return coins.ifEmpty { GameState.goalsOf(me, b) }
    }
}
