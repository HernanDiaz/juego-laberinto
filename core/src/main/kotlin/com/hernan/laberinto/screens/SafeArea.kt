package com.hernan.laberinto.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.utils.viewport.Viewport

/**
 * Zona segura de la pantalla: en iPhone, el notch o la isla de la cámara tapan la parte de arriba y
 * la barra de inicio la de abajo. Las pantallas dibujan solo dentro de la zona segura; fuera queda
 * el color de fondo.
 */
object SafeArea {
    /** (Modo tour) márgenes simulados para probar en el PC: izquierda, arriba, derecha, abajo. */
    var simulated: IntArray? = null

    val left get() = simulated?.get(0) ?: Gdx.graphics.safeInsetLeft
    val top get() = simulated?.get(1) ?: Gdx.graphics.safeInsetTop
    val right get() = simulated?.get(2) ?: Gdx.graphics.safeInsetRight
    val bottom get() = simulated?.get(3) ?: Gdx.graphics.safeInsetBottom
}

/** Como [Viewport.update] centrando la cámara, pero dejando fuera los márgenes de [SafeArea]. */
fun Viewport.updateSafe(width: Int, height: Int) {
    update(width - SafeArea.left - SafeArea.right, height - SafeArea.top - SafeArea.bottom, true)
    setScreenPosition(screenX + SafeArea.left, screenY + SafeArea.bottom)
}
