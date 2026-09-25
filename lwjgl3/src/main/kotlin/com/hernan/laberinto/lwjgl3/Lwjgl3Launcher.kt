package com.hernan.laberinto.lwjgl3

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.hernan.laberinto.LaberintoGame

/**
 * Uso normal: gradlew lwjgl3:run
 * Modo tour (pruebas/capturas): gradlew lwjgl3:run --args="tour <carpeta> <guion> [ANCHOxALTO]"  (ver TourGame)
 */
fun main(args: Array<String>) {
    val config = Lwjgl3ApplicationConfiguration().apply {
        setTitle("Las vueltas de la vida")
        // Modo tour: un 4º argumento "ANCHOxALTO" cambia el tamaño de la ventana (p. ej. para capturas)
        val size = args.getOrNull(3)?.split('x')?.map { it.toInt() }
        if (size != null) setWindowedMode(size[0], size[1]) else setWindowedMode(432, 768)
        useVsync(true)
        setForegroundFPS(60)
    }
    val game = if (args.size >= 3 && args[0] == "tour") TourGame(args[1], args[2]) else LaberintoGame()
    Lwjgl3Application(game, config)
}
