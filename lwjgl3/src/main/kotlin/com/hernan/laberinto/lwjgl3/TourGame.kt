package com.hernan.laberinto.lwjgl3

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PixmapIO
import com.hernan.laberinto.LaberintoGame
import com.hernan.laberinto.screens.GameScreen
import java.util.zip.Deflater

/**
 * Herramienta de desarrollo: ejecuta un guion de pasos (esperar, tocar, tecla, captura)
 * inyectando eventos directamente en el juego, sin mover el ratón del sistema.
 *
 * Guion: pasos separados por ';'
 *   wait:N        espera N fotogramas
 *   tap:X,Y       toque en coordenadas de ventana (origen arriba a la izquierda)
 *   key:NOMBRE    tecla (p. ej. BACK, ESCAPE, R)
 *   lang:xx       cambia el idioma
 *   debug:swap    fuerza una mudanza (en la pantalla de partida)
 *   debug:win     el jugador gana al instante
 *   debug:resetprogress  borra las estrellas guardadas
 *   shot:NOMBRE   guarda <outDir>/NOMBRE.png
 */
class TourGame(private val outDir: String, script: String) : LaberintoGame() {
    private val steps = ArrayDeque(script.split(';').map { it.trim() }.filter { it.isNotEmpty() })
    private var waitFrames = 30

    override fun render() {
        super.render()
        if (waitFrames > 0) {
            waitFrames--
            return
        }
        val step = steps.removeFirstOrNull()
        if (step == null) {
            Gdx.app.exit()
            return
        }
        val (cmd, arg) = step.split(':', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        val input = Gdx.input.inputProcessor
        when (cmd) {
            "wait" -> waitFrames = arg.toInt()
            "tap" -> {
                val (x, y) = arg.split(',').map { it.trim().toInt() }
                input.touchDown(x, y, 0, Input.Buttons.LEFT)
                input.touchUp(x, y, 0, Input.Buttons.LEFT)
                waitFrames = 2
            }
            "key" -> {
                val code = Input.Keys::class.java.getField(arg).getInt(null)
                input.keyDown(code)
                input.keyUp(code)
                waitFrames = 2
            }
            "lang" -> while (strings.language != arg) strings.cycle(1)
            "debug" -> when (arg) {
                "swap" -> (screen as? GameScreen)?.debugSwap()
                "win" -> (screen as? GameScreen)?.debugWin()
                "resetprogress" -> progress.reset()
            }
            "shot" -> {
                val w = Gdx.graphics.backBufferWidth
                val h = Gdx.graphics.backBufferHeight
                val pixmap = Pixmap.createFromFrameBuffer(0, 0, w, h)
                PixmapIO.writePNG(Gdx.files.absolute("$outDir/$arg.png"), pixmap, Deflater.DEFAULT_COMPRESSION, true)
                pixmap.dispose()
            }
            else -> error("Paso desconocido: $step")
        }
    }
}
