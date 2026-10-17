package com.hernan.laberinto.lwjgl3

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PixmapIO
import com.hernan.laberinto.LaberintoGame
import com.hernan.laberinto.Platform
import com.hernan.laberinto.model.GameSetup
import com.hernan.laberinto.screens.GameScreen
import com.hernan.laberinto.screens.InstructionsScreen
import com.hernan.laberinto.screens.LevelMapScreen
import com.hernan.laberinto.screens.LocalSetupScreen
import com.hernan.laberinto.screens.MenuScreen
import com.hernan.laberinto.screens.MultiplayerScreen
import com.hernan.laberinto.screens.PlayMode
import com.hernan.laberinto.screens.WifiScreen
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
 *   debug:wifi / debug:host / debug:join:IP / debug:start   sala de Wi-Fi
 *   debug:autoplay  los turnos de este móvil se juegan solos;  debug:dump  imprime el estado
 *   debug:hotseat   partida de 2 personas + 1 IA en el mismo móvil;  debug:manual  quita el autoplay
 *   debug:menu / debug:map / debug:multi / debug:local   ir a esa pantalla
 *   debug:select:N  ficha del nivel N en el mapa;  debug:play:N  jugar el nivel N (desde 0)
 *   debug:howto:P   instrucciones en la página P;  debug:stars:3,2,2  estrellas guardadas
 *   shot:NOMBRE   guarda <outDir>/NOMBRE.png
 *   rec:on / rec:off  graba cada fotograma en <outDir>/f_00000.png... con el tiempo fijo a 1/30 s
 *                 (vídeo fluido aunque guardar sea lento; montarlo con ffmpeg a 30 fps)
 */
class TourGame(private val outDir: String, script: String, ios: Boolean = false) :
    LaberintoGame(if (ios) IosLike else Platform.NONE) {
    private object IosLike : Platform {
        override val canQuit = false
    }

    private val steps = ArrayDeque(script.split(';').map { it.trim() }.filter { it.isNotEmpty() })
    private var waitFrames = 30

    private var recording = false
    private var frame = 0

    override fun render() {
        if (recording) {
            screen?.render(1f / 30f)
            save("f_%05d".format(frame++), Deflater.BEST_SPEED)
        } else {
            super.render()
        }
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
                // Multijugador
                "wifi" -> switchTo(WifiScreen(this))
                "host" -> (screen as? WifiScreen)?.debugHost()
                "start" -> (screen as? WifiScreen)?.debugStart()
                "autoplay" -> (screen as? GameScreen)?.debugAutoPlay = true
                "dump" -> println("ESTADO " + ((screen as? GameScreen)?.debugDump() ?: "sin partida"))
                "hotseat" -> switchTo(
                    GameScreen(this, GameSetup(humans = 2, numAi = 1, goalsPerStage = 2, aiSkill = 0.5), mode = PlayMode.HotSeat),
                )
                "manual" -> (screen as? GameScreen)?.debugAutoPlay = false
                // Ir a una pantalla directamente (capturas para las tiendas, con cualquier tamaño de ventana)
                "menu" -> switchTo(MenuScreen(this))
                "map" -> switchTo(LevelMapScreen(this))
                "multi" -> switchTo(MultiplayerScreen(this))
                "local" -> switchTo(LocalSetupScreen(this))
                else -> when {
                    arg.startsWith("join:") -> (screen as? WifiScreen)?.debugJoin(arg.removePrefix("join:"))
                    // select:N  abre la ficha del nivel N (desde 0) en el mapa
                    arg.startsWith("select:") -> (screen as? LevelMapScreen)?.debugSelect(arg.removePrefix("select:").toInt())
                    // play:N  empieza el nivel N (desde 0)
                    arg.startsWith("play:") -> levels[arg.removePrefix("play:").toInt()].let {
                        switchTo(GameScreen(this, it.setup, it))
                    }
                    // howto:P  instrucciones en la página P (desde 0)
                    arg.startsWith("howto:") -> switchTo(
                        InstructionsScreen(this, MenuScreen(this)).apply { debugPage(arg.removePrefix("howto:").toInt()) },
                    )
                    // stars:3,2,2  deja guardadas esas estrellas en los primeros niveles (y borra el resto)
                    arg.startsWith("stars:") -> {
                        progress.reset()
                        arg.removePrefix("stars:").split(',').forEachIndexed { i, n -> progress.record(i, n.toInt()) }
                    }
                    else -> error("Paso desconocido: $step")
                }
            }
            "shot" -> save(arg, Deflater.DEFAULT_COMPRESSION)
            "rec" -> recording = arg == "on"
            else -> error("Paso desconocido: $step")
        }
    }

    private fun save(name: String, compression: Int) {
        val w = Gdx.graphics.backBufferWidth
        val h = Gdx.graphics.backBufferHeight
        val pixmap = Pixmap.createFromFrameBuffer(0, 0, w, h)
        PixmapIO.writePNG(Gdx.files.absolute("$outDir/$name.png"), pixmap, compression, true)
        pixmap.dispose()
    }
}
