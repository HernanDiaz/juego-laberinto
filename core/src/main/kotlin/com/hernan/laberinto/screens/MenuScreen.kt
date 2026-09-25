package com.hernan.laberinto.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.ScreenUtils
import com.badlogic.gdx.utils.viewport.ExtendViewport
import com.hernan.laberinto.Buttons
import com.hernan.laberinto.LaberintoGame
import com.hernan.laberinto.Ui
import kotlin.math.floor
import kotlin.math.sin

class MenuScreen(private val game: LaberintoGame) : ScreenAdapter() {
    private val a = game.assets
    private val ui = game.ui
    private val batch = game.batch
    private val s = game.strings
    private val viewport = ExtendViewport(WORLD_W, WORLD_H)
    private val background = MazeBackground()
    private var time = 0f

    private val language = OptionRow({ s["menu.language"] }, { s.languageName }) { s.cycle(it) }

    private val buttons = Buttons(ui, game.audio)
    private val levelsBtn = buttons.add(a.greenButton, 2f, { s["menu.levels"] }) { game.switchTo(LevelMapScreen(game)) }
    private val freeBtn = buttons.add(a.slateButton, 1f, { s["menu.freePlay"] }) { game.switchTo(FreePlayScreen(game)) }
    private val howtoBtn = buttons.add(a.slateButton, 1f, { s["menu.howto"] }) {
        game.overlay(InstructionsScreen(game, this))
    }
    private val exitBtn = buttons.add(a.slateButton, 1f, { s["menu.exit"] }) { Gdx.app.exit() }

    private val soundBtn = Rectangle()
    private val touch = Vector2()

    private val input = object : InputAdapter() {
        override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
            viewport.unproject(touch.set(screenX.toFloat(), screenY.toFloat()))
            when {
                buttons.down(touch.x, touch.y) -> {}
                soundBtn.contains(touch) -> game.audio.toggle()
                language.touch(touch.x, touch.y) -> game.audio.play(game.audio.click)
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
                Input.Keys.ENTER, Input.Keys.SPACE -> game.switchTo(LevelMapScreen(game))
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
        background.draw(a, ui, batch, w, h, delta)

        // Título (puede tener varias líneas); las fuentes altas (hindi) van a tamaño doble
        val titleY = h * 0.845f
        val lines = s.titleLines
        val titleScale = if (ui.lineHeight > 15) 2f else 3f
        val spacing = minOf(ui.lineHeight, 15f) * titleScale * 0.8f
        lines.forEachIndexed { i, line ->
            ui.textCentered(line, w / 2, titleY + (lines.size - 1) * spacing / 2 - i * spacing, Ui.GOLD, titleScale)
        }
        // Iconos bajo la última línea del título
        val lastLine = titleY - (lines.size - 1) * spacing / 2
        val iconsY = minOf(titleY - 50, lastLine - 10 * titleScale - 18)
        val bob = sin(time * 3f) * 2f
        for (k in 0 until 5) {
            batch.draw(a.treasures[(k * 5 + (time / 2).toInt()) % a.treasures.size], w / 2 - 56 + k * 24f, iconsY + if (k % 2 == 0) bob else -bob)
        }

        // Botones y opción de idioma
        val bx = floor(w / 2 - 70)
        var y = floor(h * 0.56f)
        levelsBtn.rect.set(bx, y, 140f, 38f)
        y -= 40
        freeBtn.rect.set(bx, y, 140f, 28f)
        y -= 34
        howtoBtn.rect.set(bx, y, 140f, 28f)
        y -= 34
        exitBtn.rect.set(bx, y, 140f, 28f)
        buttons.draw()
        language.draw(a, ui, batch, w, y - 64)

        soundBtn.set(w - 30, h - 30, 26f, 26f)
        batch.draw(if (game.audio.enabled) a.soundOn else a.soundOff, soundBtn.x + 5, soundBtn.y + 5)
        batch.end()
    }

    companion object {
        const val WORLD_W = 270f
        const val WORLD_H = 480f
    }
}
