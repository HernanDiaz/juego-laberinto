package com.hernan.laberinto.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.ScreenUtils
import com.badlogic.gdx.utils.viewport.ExtendViewport
import com.hernan.laberinto.Assets
import com.hernan.laberinto.Buttons
import com.hernan.laberinto.LaberintoGame
import com.hernan.laberinto.Ui
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Mapa de niveles: un camino vertical (de la juventud, abajo, a la jubilación, arriba) con una
 * parada por nivel. Se desplaza arrastrando. Al tocar un nivel abierto se muestra su ficha.
 */
class LevelMapScreen(private val game: LaberintoGame) : ScreenAdapter() {
    private val a = game.assets
    private val ui = game.ui
    private val batch = game.batch
    private val s = game.strings
    private val levels = game.levels
    private val progress = game.progress
    private val viewport = ExtendViewport(MenuScreen.WORLD_W, MenuScreen.WORLD_H)
    private val background = MazeBackground(3)
    private var time = 0f

    // Desplazamiento vertical del mapa
    private var scroll = 0f
    private var scrollInitialized = false
    private var dragging = false
    private var dragStart = 0f
    private var lastY = 0f

    /** Nivel cuya ficha está abierta, o -1. */
    private var selected = -1
    private var toast = ""
    private var toastTime = 0f

    private val mapButtons = Buttons(ui, game.audio)
    private val backBtn = mapButtons.add(a.slateButton, 1f, { s["howto.back"] }) { game.switchTo(MenuScreen(game)) }

    private val panelButtons = Buttons(ui, game.audio)
    private val playBtn = panelButtons.add(a.greenButton, 1f, { s["menu.play"] }) {
        val level = levels[selected]
        game.switchTo(GameScreen(game, level.setup, level))
    }
    private val closeBtn = panelButtons.add(a.slateButton, 1f, { s["howto.back"] }) { selected = -1 }

    private val touch = Vector2()

    private fun nodeX(i: Int, w: Float) = floor(w / 2 + if (i % 2 == 0) -55f else 55f)
    private fun nodeY(i: Int) = BOTTOM + i * SPACING
    private val contentHeight get() = nodeY(levels.size - 1) + 70f

    private fun visibleBottom() = 44f
    private fun visibleTop(h: Float) = h - 40f
    private fun maxScroll(h: Float) = maxOf(0f, contentHeight - (visibleTop(h) - visibleBottom()))

    private val input = object : InputAdapter() {
        override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
            viewport.unproject(touch.set(screenX.toFloat(), screenY.toFloat()))
            if (selected >= 0) {
                panelButtons.down(touch.x, touch.y)
                return true
            }
            if (mapButtons.down(touch.x, touch.y)) return true
            dragging = false
            dragStart = touch.y
            lastY = touch.y
            return true
        }

        override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
            viewport.unproject(touch.set(screenX.toFloat(), screenY.toFloat()))
            if (selected >= 0) {
                panelButtons.drag(touch.x, touch.y)
                return true
            }
            mapButtons.drag(touch.x, touch.y)
            if (abs(touch.y - dragStart) > 6) dragging = true
            if (dragging) {
                scroll = (scroll - (touch.y - lastY)).coerceIn(0f, maxScroll(viewport.worldHeight))
            }
            lastY = touch.y
            return true
        }

        override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
            viewport.unproject(touch.set(screenX.toFloat(), screenY.toFloat()))
            if (selected >= 0) {
                panelButtons.up(touch.x, touch.y)
                return true
            }
            if (mapButtons.up(touch.x, touch.y) || dragging) return true
            tapNode(touch.x, touch.y)
            return true
        }

        override fun scrolled(amountX: Float, amountY: Float): Boolean {
            scroll = (scroll - amountY * 30).coerceIn(0f, maxScroll(viewport.worldHeight))
            return true
        }

        override fun keyDown(keycode: Int): Boolean {
            when (keycode) {
                Input.Keys.BACK, Input.Keys.ESCAPE -> if (selected >= 0) selected = -1 else game.switchTo(MenuScreen(game))
            }
            return true
        }
    }

    /** (Modo tour) abre la ficha de un nivel. */
    fun debugSelect(level: Int) {
        selected = level
    }

    private fun tapNode(x: Float, y: Float) {
        val w = viewport.worldWidth
        for (i in levels.indices) {
            val nx = nodeX(i, w)
            val ny = nodeY(i) + visibleBottom() - scroll
            if (hypot(x - nx, y - ny) <= 20f) {
                if (progress.unlocked(i)) {
                    game.audio.play(game.audio.click)
                    selected = i
                } else {
                    game.audio.play(game.audio.denied)
                    toast = s["level.locked"]
                    toastTime = 2f
                }
                return
            }
        }
    }

    override fun show() {
        Gdx.input.inputProcessor = input
    }

    override fun resize(width: Int, height: Int) {
        viewport.update(width, height, true)
        if (!scrollInitialized) {
            // Centrar el mapa en el nivel que toca jugar
            val h = viewport.worldHeight
            val target = nodeY(progress.current()) - (visibleTop(h) - visibleBottom()) / 2
            scroll = target.coerceIn(0f, maxScroll(h))
            scrollInitialized = true
        }
    }

    override fun render(delta: Float) {
        time += delta
        if (toastTime > 0) toastTime -= delta
        ScreenUtils.clear(Ui.BG)
        viewport.apply()
        batch.projectionMatrix = viewport.camera.combined
        val w = viewport.worldWidth
        val h = viewport.worldHeight
        val base = visibleBottom() - scroll

        batch.begin()
        background.draw(a, ui, batch, w, h, delta)

        // Camino entre paradas (puntos de arena; más oscuros si aún está cerrado)
        for (i in 0 until levels.size - 1) {
            val x1 = nodeX(i, w)
            val y1 = nodeY(i) + base
            val x2 = nodeX(i + 1, w)
            val y2 = nodeY(i + 1) + base
            val open = progress.unlocked(i + 1)
            val steps = (hypot(x2 - x1, y2 - y1) / 8f).toInt()
            for (k in 2 until steps - 1) {
                val f = k / steps.toFloat()
                val px = floor(MathUtils.lerp(x1, x2, f))
                val py = floor(MathUtils.lerp(y1, y2, f))
                ui.rect(px - 3, py - 3, 6f, 6f, Ui.SHADOW)
                ui.rect(px - 2, py - 2, 4f, 4f, if (open) SAND else Color(0.35f, 0.33f, 0.3f, 1f))
            }
        }

        // Paradas
        val current = progress.current()
        for (i in levels.indices) {
            val x = nodeX(i, w)
            val y = nodeY(i) + base
            if (y < -40 || y > h + 40) continue
            val open = progress.unlocked(i)
            batch.draw(if (open) a.nodeOpen else a.nodeLocked, x - 14, y - 14)
            if (open) batch.draw(a.treasures[levels[i].icon], x - 8, y - 8) else batch.draw(a.lock, x - 8, y - 8)
            // Estrellas conseguidas
            val stars = progress.stars(i)
            for (k in 0 until 3) {
                batch.draw(if (k < stars) a.smallStar else a.smallStarEmpty, x - 14 + k * 10f, y - 25)
            }
            ui.textCentered("${i + 1}. ${s["level.$i.name"]}", x, y - 36, if (open) Color.WHITE else Ui.MUTED)
            // La ficha del jugador espera en el nivel que toca
            if (i == current) {
                val hop = abs(sin(time * 4f)) * 4f
                batch.draw(a.pawns[0], x - 8, y + 14 + hop)
            }
        }

        // Barra superior con el título y botón de volver abajo
        ui.rect(0f, h - 34, w, 34f, Color(0.08f, 0.09f, 0.13f, 0.92f))
        ui.textCentered(s["map.title"], w / 2, h - 17, Ui.GOLD, 2f)
        ui.rect(0f, 0f, w, 38f, Color(0.08f, 0.09f, 0.13f, 0.92f))
        backBtn.rect.set(floor(w / 2 - 60), 5f, 120f, 28f)
        mapButtons.draw()

        if (toastTime > 0) {
            val tw = ui.textWidth(toast) + 16
            ui.panel(floor(w / 2 - tw / 2), h / 2 - 11, floor(tw), 22f, Color(0.1f, 0.12f, 0.18f, 0.95f), Ui.GOLD)
            ui.textCentered(toast, w / 2, h / 2, Color.WHITE)
        }
        if (selected >= 0) drawPanel(w, h)
        batch.end()
    }

    /** Ficha del nivel: descripción, rivales, estrellas y botón para jugar. */
    private fun drawPanel(w: Float, h: Float) {
        val level = levels[selected]
        ui.rect(0f, 0f, w, h, Color(0f, 0f, 0f, 0.7f))
        val pw = 244f
        val ph = 300f
        val px = floor(w / 2 - pw / 2)
        val py = floor(h / 2 - ph / 2)
        ui.panel(px, py, pw, ph)
        var y = py + ph - 18
        ui.textCentered("${selected + 1}. ${s["level.$selected.name"]}", w / 2, y, Ui.GOLD)
        y -= 16
        y -= ui.textWrapped(s["level.$selected.desc"], px + 10, y, pw - 20, Color.WHITE, Align.center) + 12

        // Rivales
        val rivals = level.setup.numAi
        ui.textCentered(s["level.rivals"], w / 2 - rivals * 9f, y, Ui.MUTED)
        val labelW = ui.textWidth(s["level.rivals"])
        for (k in 0 until rivals) batch.draw(a.pawns[k + 1], w / 2 - rivals * 9f + labelW / 2 + 4 + k * 18f, y - 9)
        y -= 26

        // Estrellas: condición y si ya se consiguió
        val got = progress.stars(selected)
        val lines = listOf(
            s["level.star1"],
            s.format("level.star2", level.roundsFor2),
            s.format("level.star3", level.coinsFor3),
        )
        for ((k, line) in lines.withIndex()) {
            batch.draw(if (k < got) a.star else a.starEmpty, px + 12, y - 8)
            ui.text(line, px + 34, y + 6, Color.WHITE)
            y -= 22
        }

        playBtn.rect.set(px + 14, py + 12, 104f, 30f)
        closeBtn.rect.set(px + pw - 118, py + 12, 104f, 30f)
        panelButtons.draw()
    }

    companion object {
        private const val BOTTOM = 36f
        private const val SPACING = 74f
        private val SAND = Color.valueOf("d6b274")
    }
}
