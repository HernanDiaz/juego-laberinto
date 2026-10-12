package com.hernan.laberinto

/** Lo que depende de la plataforma (Android, escritorio). */
interface Platform {
    /**
     * Mientras se buscan partidas por Wi-Fi: en Android hace falta pedir permiso al sistema
     * para recibir los anuncios de difusión de la red (MulticastLock).
     */
    fun lanDiscovery(active: Boolean) {}

    companion object {
        val NONE = object : Platform {}
    }
}
