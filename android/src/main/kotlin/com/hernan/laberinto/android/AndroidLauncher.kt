package com.hernan.laberinto.android

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Bundle
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.hernan.laberinto.LaberintoGame
import com.hernan.laberinto.Platform

class AndroidLauncher : AndroidApplication() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val config = AndroidApplicationConfiguration().apply {
            useImmersiveMode = true
        }
        initialize(LaberintoGame(AndroidPlatform(this)), config)
    }
}

/**
 * Muchos móviles descartan los paquetes de difusión de la Wi-Fi para ahorrar batería:
 * mientras se buscan partidas se pide un MulticastLock para recibir los anuncios.
 */
class AndroidPlatform(context: Context) : Platform {
    private val lock = (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
        .createMulticastLock("vueltas-de-la-vida").apply { setReferenceCounted(false) }

    override fun lanDiscovery(active: Boolean) {
        runCatching { if (active) lock.acquire() else if (lock.isHeld) lock.release() }
    }
}
