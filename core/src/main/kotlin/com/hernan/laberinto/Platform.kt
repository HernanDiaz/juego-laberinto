package com.hernan.laberinto

/** Lo que depende de la plataforma (Android, iOS, escritorio). */
interface Platform {
    /** En iOS las apps no se cierran solas (Apple lo rechaza): sin botón «Salir». */
    val canQuit: Boolean get() = true

    /**
     * Mientras se buscan partidas por Wi-Fi: en Android hace falta pedir permiso al sistema
     * para recibir los anuncios de difusión de la red (MulticastLock).
     */
    fun lanDiscovery(active: Boolean) {}

    companion object {
        val NONE = object : Platform {}
    }
}
