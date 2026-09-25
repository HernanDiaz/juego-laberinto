package com.hernan.laberinto

import com.badlogic.gdx.Gdx

/** Estrellas conseguidas en cada nivel, guardadas en las preferencias del dispositivo. */
class Progress(private val count: Int) {
    private val prefs = Gdx.app.getPreferences("laberinto")

    fun stars(level: Int) = prefs.getInteger("stars.$level", 0)

    /** El primer nivel está siempre abierto; los demás, al ganar el anterior. */
    fun unlocked(level: Int) = level == 0 || stars(level - 1) > 0

    /** Guarda el resultado si mejora el anterior. Devuelve true si es un récord. */
    fun record(level: Int, stars: Int): Boolean {
        if (stars <= stars(level)) return false
        prefs.putInteger("stars.$level", stars).flush()
        return true
    }

    /** Borra todo el progreso (lo usa el modo tour de pruebas). */
    fun reset() {
        for (i in 0 until count) prefs.remove("stars.$i")
        prefs.flush()
    }

    /** Nivel que toca jugar: el primero sin estrellas (o el último si están todos). */
    fun current() = (0 until count).firstOrNull { stars(it) == 0 } ?: (count - 1)
}
