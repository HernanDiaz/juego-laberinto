package com.hernan.laberinto.ios

import com.badlogic.gdx.backends.iosrobovm.IOSApplication
import com.badlogic.gdx.backends.iosrobovm.IOSApplicationConfiguration
import com.hernan.laberinto.LaberintoGame
import com.hernan.laberinto.Platform
import org.robovm.apple.foundation.NSAutoreleasePool
import org.robovm.apple.uikit.UIApplication

class IOSLauncher : IOSApplication.Delegate() {
    override fun createApplication(): IOSApplication {
        val config = IOSApplicationConfiguration().apply {
            orientationPortrait = true
            orientationLandscape = false
            useAccelerometer = false
            useCompass = false
        }
        return IOSApplication(LaberintoGame(IOSPlatform), config)
    }

    companion object {
        @JvmStatic
        fun main(argv: Array<String>) {
            val pool = NSAutoreleasePool()
            UIApplication.main<UIApplication, IOSLauncher>(argv, null, IOSLauncher::class.java)
            pool.close()
        }
    }
}

/**
 * En iOS no hay botón «Salir». La búsqueda automática de partidas por Wi-Fi (difusión UDP)
 * necesita un permiso especial de Apple (multicast); sin él, se juega uniéndose por IP.
 */
object IOSPlatform : Platform {
    override val canQuit = false
}
