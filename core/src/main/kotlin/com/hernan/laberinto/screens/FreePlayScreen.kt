package com.hernan.laberinto.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.ScreenUtils
import com.badlogic.gdx.utils.viewport.ExtendViewport
import com.hernan.laberinto.Buttons
import com.hernan.laberinto.LaberintoGame
import com.hernan.laberinto.Ui
import com.hernan.laberinto.model.GameSetup
import com.hernan.laberinto.model.GameState
import kotlin.math.floor

/** Partida libre: eliges rivales y metas por etapa, con el tablero estándar. */
class FreePlayScreen(private val game: LaberintoGame) : ScreenAdapter() {
    private val a = game.assets
    private val ui = game.ui
    private val batch = game.batch
    private val s = game.strings
    private val viewport = ExtendViewport(MenuScreen.WORLD_W, MenuScreen.WORLD_H)
    private val background = MazeBackground(7)

    private var numAi = 1
    private var goals = 2
    private val maxGoals get() = GameState.maxGoalsPerStage(numAi)

    private val options = listOf(
        OptionRow({ s["menu.opponents"] }, { numAi.toString() }) {
            numAi = (numAi + it).coerceIn(1, 3)
            goals = goals.coerceAtMost(maxGoals)
        },
        OptionRow({ s["menu.treasures"] }, { goals.toString() }) { goals = (goals + it).coerceIn(1, maxGoals) },
    )

    private val buttons = Buttons(ui, game.audio)
    private val playBtn = buttons.add(a.greenButton, 2f, { s["menu.play"] }) { play() }
    private val backBtn = buttons.add(a.slateButton, 1f, { s["howto.back"] }) { game.switchTo(MenuScreen(game)) }
    private val touch = Vector2()

    private fun play() = game.switchTo(GameScreen(game, GameSetup(numAi = numAi, goalsPerStage = goals)))

    private val input = object : InputAdapter() {
        override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
            viewport.unproject(touch.set(screenX.toFloat(), screenY.toFloat()))
            if (!buttons.down(touch.x, touch.y) && options.any { it.touch(touch.x, touch.y) }) {
                game.audio.play(game.audio.click)
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
                Input.Keys.BACK, Input.Keys.ESCAPE -> game.switchTo(MenuScreen(game))
                Input.Keys.ENTER, Input.Keys.SPACE -> play()
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
        ScreenUtils.clear(Ui.BG)
        viewport.apply()
        batch.projectionMatrix = viewport.camera.combined
        val w = viewport.worldWidth
        val h = viewport.worldHeight
        batch.begin()
        background.draw(a, ui, batch, w, h, delta)
        ui.textCentered(s["menu.freePlay"], w / 2, h * 0.8f, Ui.GOLD, 2f)
        var y = floor(h * 0.6f)
        for (o in options) {
            o.draw(a, ui, batch, w, y)
            y -= 56
        }
        playBtn.rect.set(floor(w / 2 - 70), y - 10, 140f, 38f)
        backBtn.rect.set(floor(w / 2 - 70), y - 52, 140f, 28f)
        buttons.draw()
        batch.end()
    }
}
