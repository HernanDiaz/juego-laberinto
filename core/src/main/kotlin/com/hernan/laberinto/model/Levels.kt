package com.hernan.laberinto.model

import com.badlogic.gdx.utils.JsonReader
import com.badlogic.gdx.utils.JsonValue

/**
 * Un nivel del modo historia. Se definen en assets/levels.json; los textos (nombre y
 * descripción) están en los archivos de idioma como level.N.name y level.N.desc.
 */
data class Level(
    val index: Int,
    /** Meta de vida que se usa como icono en el mapa. */
    val icon: Int,
    val setup: GameSetup,
    /** Para 2 estrellas: ganar en este número de rondas o menos. */
    val roundsFor2: Int,
    /** Para 3 estrellas: además, terminar con al menos estas monedas. */
    val coinsFor3: Int,
    /** Muestra pistas (flecha y casilla recomendadas) para aprender a jugar. */
    val hints: Boolean = false,
) {
    /** Estrellas conseguidas al ganar en [rounds] rondas con [coins] monedas. */
    fun stars(rounds: Int, coins: Int) = when {
        rounds <= roundsFor2 && coins >= coinsFor3 -> 3
        rounds <= roundsFor2 -> 2
        else -> 1
    }
}

object Levels {
    fun parse(json: String): List<Level> =
        JsonReader().parse(json).mapIndexed { i, v -> level(i, v) }

    private fun level(index: Int, v: JsonValue): Level {
        val b = v.get("board")
        val def = BoardConfig()
        val board = if (b == null) def else BoardConfig(
            pushLines = b.get("pushLines")?.asIntArray()?.toList() ?: def.pushLines,
            straight = b.getInt("straight", def.straight),
            corner = b.getInt("corner", def.corner),
            tee = b.getInt("tee", def.tee),
            cross = b.getInt("cross", def.cross),
            deadEnd = b.getInt("deadEnd", def.deadEnd),
            coins = b.getInt("coins", def.coins),
            events = b.getInt("events", def.events),
        )
        val rivals = v.get("rivals").asStringArray().map { Personality.valueOf(it) }
        val events = v.get("events")?.associate { EventType.valueOf(it.name) to it.asInt() } ?: emptyMap()
        val setup = GameSetup(
            numAi = rivals.size,
            goalsPerStage = v.getInt("goalsPerStage", 2),
            personalities = rivals,
            board = board,
            startCoins = v.getInt("startCoins", 0),
            costExtra = v.getInt("costExtra", 0),
            eventWeights = events,
            aiSkill = v.getDouble("aiSkill", 1.0),
            noCosts = v.getBoolean("noCosts", false),
        )
        val stars = v.get("stars")
        return Level(
            index, v.getInt("icon"), setup, stars.getInt("rounds"), stars.getInt("coins"),
            hints = v.getBoolean("hints", false),
        )
    }
}
