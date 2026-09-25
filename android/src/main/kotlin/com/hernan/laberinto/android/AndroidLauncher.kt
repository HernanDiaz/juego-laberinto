package com.hernan.laberinto.android

import android.content.pm.ApplicationInfo
import android.os.Bundle
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.hernan.laberinto.FakeStore
import com.hernan.laberinto.LaberintoGame

class AndroidLauncher : AndroidApplication() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val config = AndroidApplicationConfiguration().apply {
            useImmersiveMode = true
        }
        // La versión de pruebas (depurable, instalada sin Google Play) usa la compra simulada:
        // "Comprar" desbloquea el juego completo al instante y sin cobrar.
        val debuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        initialize(LaberintoGame { if (debuggable) FakeStore() else PlayStore(this) }, config)
    }
}
