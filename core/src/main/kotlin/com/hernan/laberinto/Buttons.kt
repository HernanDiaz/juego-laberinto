package com.hernan.laberinto

import com.badlogic.gdx.graphics.g2d.NinePatch
import com.badlogic.gdx.math.Rectangle

/** Botón con relieve; la etiqueta se evalúa al dibujar para que cambie con el idioma. */
class PixelButton(
    val style: Array<NinePatch>,
    val scale: Float = 1f,
    val label: () -> String,
    val onClick: () -> Unit,
) {
    val rect = Rectangle()
    var pressed = false
}

/** Gestiona pulsar / arrastrar fuera / soltar de un grupo de botones. Se activan al soltar. */
class Buttons(private val ui: Ui, private val audio: Audio) {
    private val list = ArrayList<PixelButton>()
    val all: List<PixelButton> get() = list

    fun add(style: Array<NinePatch>, scale: Float = 1f, label: () -> String, onClick: () -> Unit) =
        PixelButton(style, scale, label, onClick).also { list.add(it) }

    fun remove(button: PixelButton) {
        list.remove(button)
    }

    fun down(x: Float, y: Float): Boolean {
        val b = list.firstOrNull { it.rect.contains(x, y) } ?: return false
        b.pressed = true
        return true
    }

    fun drag(x: Float, y: Float) {
        list.forEach { if (it.pressed && !it.rect.contains(x, y)) it.pressed = false }
    }

    fun up(x: Float, y: Float): Boolean {
        val b = list.firstOrNull { it.pressed }
        list.forEach { it.pressed = false }
        if (b != null && b.rect.contains(x, y)) {
            audio.play(audio.click)
            b.onClick()
            return true
        }
        return false
    }

    fun draw() {
        list.forEach { ui.button(it.rect, it.label(), it.pressed, it.style, it.scale) }
    }
}
