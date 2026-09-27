package com.hernan.laberinto.screens

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.math.Rectangle
import com.hernan.laberinto.Assets
import com.hernan.laberinto.Ui
import kotlin.random.Random

/** Fondo de laberinto que se desplaza lentamente, oscurecido para que se lea el texto encima. */
class MazeBackground(seed: Int = 42) {
    private val rnd = Random(seed)
    private val tiles = IntArray(200) { rnd.nextInt(16) }
    private var time = 0f

    fun draw(a: Assets, ui: Ui, batch: SpriteBatch, w: Float, h: Float, delta: Float) {
        time += delta
        val scroll = (time * 6f) % Assets.TILE
        var i = 0
        var y = -Assets.TILE.toFloat() + scroll
        while (y < h) {
            var x = 0f
            while (x < w) {
                batch.draw(a.tiles[tiles[i++ % tiles.size]], x, y)
                x += Assets.TILE
            }
            y += Assets.TILE
        }
        ui.rect(0f, 0f, w, h, Color(0.05f, 0.06f, 0.09f, 0.72f))
    }
}

/** Fila de opción con flechas < > (idioma, número de rivales...). */
class OptionRow(val label: () -> String, val value: () -> String, val change: (Int) -> Unit) {
    private val minus = Rectangle()
    private val plus = Rectangle()

    fun draw(a: Assets, ui: Ui, batch: SpriteBatch, w: Float, y: Float) {
        ui.textCentered(label(), w / 2, y + 26, Color.WHITE)
        ui.panel(w / 2 - 44, y - 4, 88f, 20f)
        ui.textCentered(value(), w / 2, y + 6, Ui.GOLD)
        minus.set(w / 2 - 76, y - 8, 28f, 28f)
        plus.set(w / 2 + 48, y - 8, 28f, 28f)
        batch.draw(a.arrow, minus.x + 6, minus.y + 6, 8f, 8f, 16f, 16f, 1f, 1f, 180f)
        batch.draw(a.arrow, plus.x + 6, plus.y + 6)
    }

    /** Devuelve true si el toque ha cambiado la opción. */
    fun touch(x: Float, y: Float): Boolean {
        val step = when {
            minus.contains(x, y) -> -1
            plus.contains(x, y) -> 1
            else -> return false
        }
        change(step)
        return true
    }
}
