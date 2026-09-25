package com.hernan.laberinto.model

import kotlin.random.Random

data class AiPlan(val rotations: Int, val insert: Insert, val dest: Pos)

/**
 * IA: prueba las 4 rotaciones x todas las entradas y, para cada una, todas las casillas
 * alcanzables. Puntúa conseguir una meta que pueda pagar, las monedas del camino, los sucesos
 * y acercarse a sus objetivos; y penaliza dejarle el camino libre al siguiente jugador.
 * Cuánto pesa cada cosa depende de su personalidad.
 *
 * La habilidad ([skill], de 0 a 1) la hace más o menos fuerte: con poca habilidad a veces
 * juega al azar, valora peor las jugadas (más "ruido"), apenas intenta bloquear y se fija
 * menos en acercarse a sus metas.
 */
object Ai {
    private class Weights(val coin: Double, val event: Double, val hinder: Double)

    private fun weights(p: Personality?) = when (p) {
        Personality.AMBITIOUS -> Weights(coin = 40.0, event = 5.0, hinder = 15.0)
        Personality.ADVENTUROUS -> Weights(coin = 15.0, event = 60.0, hinder = 5.0)
        Personality.CALM -> Weights(coin = 20.0, event = -25.0, hinder = 25.0)
        null -> Weights(coin = 25.0, event = 0.0, hinder = 15.0)
    }

    fun plan(state: GameState, rng: Random = state.rng, skill: Double = state.setup.aiSkill): AiPlan {
        val me = state.currentPlayer
        val next = state.players[(me.index + 1) % state.players.size]
        val w = weights(me.personality)
        val inserts = state.allowedInserts()

        // Con poca habilidad, a veces hace una jugada cualquiera
        if (rng.nextDouble() < (1 - skill) * 0.5) {
            val rot = rng.nextInt(4)
            val ins = inserts.random(rng)
            val b = state.board.copy()
            repeat(rot) { b.spare.rotateCw() }
            val positions = state.players.map { it.pos }.toMutableList()
            b.push(ins, positions)
            return AiPlan(rot, ins, b.reachable(positions[me.index]).random(rng))
        }

        var best: AiPlan? = null
        var bestScore = Double.NEGATIVE_INFINITY
        val noise = 3 + (1 - skill) * 600

        for (rot in 0..3) {
            for (ins in inserts) {
                val b = state.board.copy()
                repeat(rot) { b.spare.rotateCw() }
                val positions = state.players.map { it.pos }.toMutableList()
                b.push(ins, positions)

                var hinder = 0.0
                if (next !== me) {
                    val nextReach = b.reachable(positions[next.index])
                    if (GameState.goalsOf(next, b).any { it in nextReach }) hinder = w.hinder * skill
                }

                for ((dest, value) in destinations(state, me, b, positions[me.index], w, skill)) {
                    val score = value - hinder + rng.nextDouble() * noise
                    if (score > bestScore) {
                        bestScore = score
                        best = AiPlan(rot, ins, dest)
                    }
                }
            }
        }
        return best!!
    }

    /** Mejor casilla para [player] en el tablero actual, sin empujar (para las pistas). */
    fun bestDestination(state: GameState, player: Player = state.currentPlayer): Pos =
        destinations(state, player, state.board, player.pos, weights(null), 1.0).maxBy { it.second }.first

    /** Valor de cada casilla alcanzable desde [start] en el tablero [b]. */
    private fun destinations(
        state: GameState,
        me: Player,
        b: Board,
        start: Pos,
        w: Weights,
        skill: Double,
    ): List<Pair<Pos, Double>> {
        val tree = b.paths(start)
        val targets = targetsOf(state, me, b)
        return tree.keys.map { dest ->
            val path = Board.pathIn(tree, dest)!!
            val coinsOnPath = path.drop(1).count { b[it].item == Item.COIN }
            val tile = b[dest]
            var score = coinsOnPath * w.coin * (0.5 + skill / 2)
            val goal = tile.treasure
            if (goal >= 0 && goal in me.activeGoals && me.coins + coinsOnPath >= state.costOf(goal)) {
                score += 1000.0 + Goals.REWARD[goal] * 20
            }
            if (me.retiring && dest == me.home) score += 1000.0
            if (tile.item == Item.EVENT && dest != start) score += w.event
            if (targets.isNotEmpty()) score -= 10.0 * skill * targets.minOf { dest.dist(it) }
            dest to score
        }
    }

    /**
     * Hacia dónde conviene acercarse: su casa si se jubila; si no, las metas activas que ya puede
     * pagar; y si no puede pagar ninguna, las monedas del tablero.
     */
    private fun targetsOf(state: GameState, me: Player, b: Board): List<Pos> {
        if (me.retiring) return listOf(me.home)
        val affordable = me.activeGoals.filter { state.costOf(it) <= me.coins }.mapNotNull { b.find(it) }
        if (affordable.isNotEmpty()) return affordable
        val coins = b.allCells().filter { b[it].item == Item.COIN }
        return coins.ifEmpty { GameState.goalsOf(me, b) }
    }
}
