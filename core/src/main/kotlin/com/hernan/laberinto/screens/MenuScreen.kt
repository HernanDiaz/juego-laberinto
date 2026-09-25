package com.hernan.laberinto.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.ScreenUtils
import com.badlogic.gdx.utils.viewport.ExtendViewport
import com.hernan.laberinto.Assets
import com.hernan.laberinto.Buttons
import com.hernan.laberinto.LaberintoGame
import com.hernan.laberinto.Ui
import com.hernan.laberinto.model.Board
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

class MenuScreen(private val game: LaberintoGame) : ScreenAdapter() {
    private val a = game.assets
    private val ui = game.ui
    private val batch = game.batch
    private val s = game.strings
    private val viewport = ExtendViewport(WORLD_W, WORLD_H)

    private var numAi = 1
    private var cards = 6
    private val maxCards get() = Board.TREASURES / (numAi + 1)

    private val bgRandom = Random(42)
    private val bgTiles = IntArray(200) { bgRandom.nextInt(16) }
    private var time = 0f

    /** Filas de opciones con flechas < > */
    private class Option(val label: () -> String, val value: () -> String, val change: (Int) -> Unit) {
        val minus = Rectangle()
        val plus = Rectangle()
    }

    private val options = listOf(
        Option({ s["menu.opponents"] }, { numAi.toString() }) { numAi = (numAi + it).coerceIn(1, 3) },
        Option({ s["menu.treasures"] }, { cards.toString() }) { cards = (cards + it).coerceIn(1, maxCards) },
        Option({ s["menu.language"] }, { s.languageName }) { s.cycle(it) },
    )

    private val buttons = Buttons(ui, game.audio)
    private val playBtn = buttons.add(a.greenButton, 2f, { s["menu.play"] }) { startGame() }
    private val howtoBtn = buttons.add(a.slateButton, 1f, { s["menu.howto"] }) {
        game.overlay(InstructionsScreen(game, this))
    }
    private val exitBtn = buttons.add(a.slateButton, 1f, { s["menu.exit"] }) { Gdx.app.exit() }

    private val soundBtn = Rectangle()
    private val touch = Vector2()

    private fun startGame() {
        game.switchTo(GameScreen(game, numAi, cards))
    }

    private val input = object : InputAdapter() {
        override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
            viewport.unproject(touch.set(screenX.toFloat(), screenY.toFloat()))
            if (buttons.down(touch.x, touch.y)) return true
            if (soundBtn.contains(touch)) {
                game.audio.toggle()
                return true
            }
            for (o in options) {
                val step = when {
                    o.minus.contains(touch) -> -1
                    o.plus.contains(touch) -> 1
                    else -> 0
                }
                if (step != 0) {
                    o.change(step)
                    cards = cards.coerceAtMost(maxCards)
                    game.audio.play(game.audio.click)
                }
            }
            return true
        }

        override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
            viewport.unproject(touch.set(screenX.toFloat(), screenY.toFloat()))
            buttons.drag(touch.x, touch.y)
            return true
        }

        override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
            viewport.unproject(touch.set(screenX.toFloat(), screenY.toFloat()))
            buttons.up(touch.x, touch.y)
            return true
        }

        override fun keyDown(keycode: Int): Boolean {
            when (keycode) {
                Input.Keys.BACK, Input.Keys.ESCAPE -> Gdx.app.exit()
                Input.Keys.ENTER, Input.Keys.SPACE -> startGame()
            }
            return true
        }
    }

    override fun show() {
        Gdx.input.inputProcessor = input
    }

    override fun resize(width: Int, height: Int) {
        viewport.update(width, height, true)
    }

    override fun render(delta: Float) {
        time += delta
        ScreenUtils.clear(Ui.BG)
        viewport.apply()
        batch.projectionMatrix = viewport.camera.combined
        val w = viewport.worldWidth
        val h = viewport.worldHeight

        batch.begin()
        // Fondo: laberinto que se desplaza lentamente
        val scroll = (time * 6f) % Assets.TILE
        var i = 0
        var y = -Assets.TILE.toFloat() + scroll
        while (y < h) {
            var x = 0f
            while (x < w) {
                batch.draw(a.tiles[bgTiles[i++ % bgTiles.size]], x, y)
                x += Assets.TILE
            }
            y += Assets.TILE
        }
        ui.rect(0f, 0f, w, h, Color(0.05f, 0.06f, 0.09f, 0.72f))

        // Título
        val titleY = h * 0.82f
        ui.textCentered("LABERINTO", w / 2, titleY - 6, Ui.GOLD, 3f)
        val bob = (sin(time * 3f) * 2f)
        for (k in 0 until 5) {
            batch.draw(a.treasures[(k * 5 + (time / 2).toInt()) % a.treasures.size], w / 2 - 56 + k * 24f, titleY - 36 + if (k % 2 == 0) bob else -bob)
        }

        // Opciones
        var oy = floor(h * 0.63f)
        for (o in options) {
            option(o, oy)
            oy -= 44
        }

        // Botones
        val bx = floor(w / 2 - 70)
        playBtn.rect.set(bx, oy - 14, 140f, 36f)
        howtoBtn.rect.set(bx, oy - 52, 140f, 28f)
        exitBtn.rect.set(bx, oy - 86, 140f, 28f)
        buttons.draw()

        // Sonido
        soundBtn.set(w - 30, h - 30, 26f, 26f)
        batch.draw(if (game.audio.enabled) a.soundOn else a.soundOff, soundBtn.x + 5, soundBtn.y + 5)
        batch.end()
    }

    private fun option(o: Option, y: Float) {
        val w = viewport.worldWidth
        ui.textCentered(o.label(), w / 2, y + 26, Color.WHITE)
        ui.panel(w / 2 - 44, y - 4, 88f, 20f)
        ui.textCentered(o.value(), w / 2, y + 6, Ui.GOLD)
        o.minus.set(w / 2 - 76, y - 8, 28f, 28f)
        o.plus.set(w / 2 + 48, y - 8, 28f, 28f)
        batch.draw(a.arrow, o.minus.x + 6, o.minus.y + 6, 8f, 8f, 16f, 16f, 1f, 1f, 180f)
        batch.draw(a.arrow, o.plus.x + 6, o.plus.y + 6)
    }

    companion object {
        const val WORLD_W = 270f
        const val WORLD_H = 480f
    }
}
