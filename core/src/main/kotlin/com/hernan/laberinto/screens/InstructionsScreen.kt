package com.hernan.laberinto.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.Screen
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.ScreenUtils
import com.badlogic.gdx.utils.viewport.ExtendViewport
import com.hernan.laberinto.Assets.Companion.TILE
import com.hernan.laberinto.Buttons
import com.hernan.laberinto.LaberintoGame
import com.hernan.laberinto.Ui
import com.hernan.laberinto.model.Dirs
import kotlin.math.floor
import kotlin.math.sin

/** Instrucciones en 4 páginas con ilustraciones animadas. Al salir vuelve a [previous]. */
class InstructionsScreen(private val game: LaberintoGame, private val previous: Screen) : ScreenAdapter() {
    private val a = game.assets
    private val ui = game.ui
    private val batch = game.batch
    private val s = game.strings
    private val viewport = ExtendViewport(MenuScreen.WORLD_W, MenuScreen.WORLD_H)

    private var page = 0
    private var time = 0f
    private val touch = Vector2()

    private val buttons = Buttons(ui, game.audio)
    private val prevBtn = buttons.add(a.slateButton, 2f, { "<" }) { turn(-1) }
    private val nextBtn = buttons.add(a.slateButton, 2f, { ">" }) { turn(1) }
    private val backBtn = buttons.add(a.greenButton, 1f, { s["howto.back"] }) { close() }

    private fun turn(step: Int) {
        page = (page + step + PAGES) % PAGES
        time = 0f
    }

    private fun close() = game.returnTo(previous)

    private val input = object : InputAdapter() {
        override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
            viewport.unproject(touch.set(screenX.toFloat(), screenY.toFloat()))
            buttons.down(touch.x, touch.y)
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
                Input.Keys.BACK, Input.Keys.ESCAPE -> close()
                Input.Keys.LEFT -> turn(-1)
                Input.Keys.RIGHT -> turn(1)
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
        ui.textCentered(s["howto.title"], w / 2, h - 28, Ui.GOLD, 2f)

        val n = page + 1
        val key = PAGE_TEXTS[page] // la página de dinero (p5) va antes que la de jubilación (p4)
        ui.textCentered(s["howto.p$key.title"], w / 2, h - 60, Color.WHITE)

        // Ilustración centrada en un recuadro
        val boxH = 112f
        val boxY = floor(h - 72 - boxH)
        ui.panel(12f, boxY, w - 24, boxH, Ui.SHADOW, Ui.BORDER)
        val cx = floor(w / 2)
        val cy = floor(boxY + boxH / 2)
        when (page) {
            0 -> drawGoal(cx, cy)
            1 -> drawPush(cx, cy)
            2 -> drawWalk(cx, cy)
            3 -> drawMoney(cx, cy)
            else -> drawWin(cx, cy)
        }

        ui.textWrapped(s["howto.p$key.text"], 16f, boxY - 12, w - 32, Color.WHITE, Align.center)

        // Navegación
        prevBtn.rect.set(16f, 56f, 44f, 34f)
        nextBtn.rect.set(w - 60, 56f, 44f, 34f)
        backBtn.rect.set(floor(w / 2 - 60), 12f, 120f, 30f)
        ui.textCentered("$n / $PAGES", w / 2, 73f, Ui.MUTED)
        buttons.draw()
        batch.end()
    }

    // ------------------------------------------------------------ ilustraciones

    private fun drawGoal(cx: Float, cy: Float) {
        // Metas de la etapa -> una de ellas en el laberinto
        ui.goalPanel(cx - 96, cy - 35, listOf(0, 1, 2, 3))
        batch.draw(a.arrow, cx - 16, cy - 8)
        val pulse = 0.55f + 0.45f * sin(time * 6f)
        for (i in 0 until 2) {
            val x = cx + 16 + i * TILE
            batch.draw(a.tiles[Dirs.E or Dirs.W], x, cy - 16)
        }
        batch.draw(a.treasures[1], cx + 16 + TILE + 8, cy - 8)
        batch.color = Color(Ui.GOLD.r, Ui.GOLD.g, Ui.GOLD.b, pulse)
        batch.draw(a.cursor, cx + 16 + TILE, cy - 16)
        batch.color = Color.WHITE
        batch.draw(a.pawns[0], cx + 24, cy - 6)
    }

    private fun drawPush(cx: Float, cy: Float) {
        // Una fila que se desplaza al meter la pieza suelta
        val t = MathUtils.clamp((time % 2.4f - 0.6f) / 0.6f, 0f, 1f)
        val rowMasks = intArrayOf(Dirs.E or Dirs.W, Dirs.N or Dirs.E, Dirs.E or Dirs.W or Dirs.S, Dirs.N or Dirs.S)
        val x0 = cx - 2 * TILE + 16
        val y = cy - 20
        for (k in rowMasks.indices) {
            val alpha = if (k == rowMasks.lastIndex) 1f - t else 1f
            batch.color = Color(1f, 1f, 1f, alpha)
            batch.draw(a.tiles[rowMasks[k]], x0 + (k + t) * TILE, y)
        }
        batch.color = Color.WHITE
        batch.draw(a.tiles[Dirs.W or Dirs.S], x0 + (t - 1) * TILE, y)
        val bob = if (t == 0f) sin(time * 8f) * 1.5f else 0f
        batch.draw(a.arrow, x0 - TILE - 18 + bob, y + 8)
    }

    private val walkMasks = arrayOf(
        intArrayOf(Dirs.E or Dirs.S, Dirs.E or Dirs.W, Dirs.W or Dirs.S),
        intArrayOf(Dirs.N or Dirs.E, Dirs.N or Dirs.S, Dirs.N or Dirs.S),
        intArrayOf(Dirs.N or Dirs.E, Dirs.E or Dirs.W, Dirs.N or Dirs.W),
    )
    private val walkPath = listOf(0 to 0, 0 to 1, 0 to 2, 1 to 2, 2 to 2, 2 to 1, 2 to 0)

    private fun drawWalk(cx: Float, cy: Float) {
        val x0 = cx - 1.5f * TILE
        val y0 = cy + 0.5f * TILE
        for (r in 0 until 3) for (c in 0 until 3) {
            batch.draw(a.tiles[walkMasks[r][c]], x0 + c * TILE, y0 - r * TILE)
        }
        val alpha = 0.22f + 0.1f * sin(time * 5f)
        for ((r, c) in walkPath) ui.rect(x0 + c * TILE + 2, y0 - r * TILE + 2, TILE - 4f, TILE - 4f, Color(1f, 1f, 0.85f, alpha))
        // La ficha recorre el camino y vuelve a empezar
        val f = (time * 4f) % (walkPath.size + 2)
        val idx = f.toInt().coerceAtMost(walkPath.size - 1)
        val frac = if (idx < walkPath.size - 1) f - idx else 0f
        val (r1, c1) = walkPath[idx]
        val (r2, c2) = walkPath[(idx + 1).coerceAtMost(walkPath.size - 1)]
        val px = MathUtils.lerp(x0 + c1 * TILE, x0 + c2 * TILE, frac)
        val py = MathUtils.lerp(y0 - r1 * TILE, y0 - r2 * TILE, frac) + sin(frac * MathUtils.PI) * 4f
        batch.draw(a.pawns[0], floor(px + 8), floor(py + 10))
    }

    private fun drawMoney(cx: Float, cy: Float) {
        // Una moneda en el camino, una casilla "?" y una meta con su precio
        val y = cy - 16
        batch.draw(a.tiles[Dirs.E or Dirs.W], cx - 90, y)
        val bob = kotlin.math.abs(sin(time * 4f)) * 3f
        batch.draw(a.coin, cx - 82, y + 8 + bob)
        batch.draw(a.tiles[Dirs.E or Dirs.W], cx - 16, y)
        batch.draw(a.event, cx - 8, y + 8)
        ui.goalPanel(cx + 40, cy - 35, listOf(12), Ui.BORDER, coins = 1)
    }

    private fun drawWin(cx: Float, cy: Float) {
        val x = cx - TILE / 2f
        val y = cy - 24
        batch.draw(a.tiles[Dirs.N or Dirs.E], x, y)
        batch.draw(a.homes[0], x, y)
        val hop = kotlin.math.abs(sin(time * 4f)) * 6f
        batch.draw(a.pawns[0], x + 8, y + 10 + hop)
        // Las metas de las tres etapas, ya conseguidas
        for (k in 0 until 5) batch.draw(a.treasures[k * 5 + 1], cx - 48 + k * 20f, cy + 22)
    }

    companion object {
        const val PAGES = 5
        private val PAGE_TEXTS = intArrayOf(1, 2, 3, 5, 4)
    }
}
