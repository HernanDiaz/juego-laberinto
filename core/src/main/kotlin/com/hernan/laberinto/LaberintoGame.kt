package com.hernan.laberinto

import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.Screen
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.hernan.laberinto.model.Level
import com.hernan.laberinto.model.Levels
import com.hernan.laberinto.screens.MenuScreen

/**
 * [storeFactory] crea la tienda de la plataforma (Google Play en Android, simulada en escritorio).
 * Se crea en create() porque necesita libGDX ya iniciado.
 */
open class LaberintoGame(private val storeFactory: () -> Store = { FakeStore() }) : Game() {
    lateinit var assets: Assets
        private set
    lateinit var batch: SpriteBatch
        private set
    lateinit var ui: Ui
        private set
    lateinit var audio: Audio
        private set
    lateinit var strings: Strings
        private set
    lateinit var levels: List<Level>
        private set
    lateinit var progress: Progress
        private set
    lateinit var store: Store
        private set

    /** Precio del juego completo listo para dibujar con la fuente del idioma actual, o null. */
    fun priceLabel(): String? {
        val p = store.price ?: return null
        val shaped = strings.display(p)
        return if (ui.canRender(shaped)) shaped else store.priceFallback
    }

    /** Los niveles marcados como de pago solo se juegan con el juego completo. */
    fun canPlay(level: Level) = !level.premium || store.owned

    override fun create() {
        Gdx.input.setCatchKey(Input.Keys.BACK, true)
        assets = Assets()
        batch = SpriteBatch()
        ui = Ui(assets, batch)
        audio = Audio()
        strings = Strings()
        levels = Levels.parse(Gdx.files.internal("levels.json").readString("UTF-8"))
        progress = Progress(levels.size)
        store = storeFactory()
        strings.onChange = { ui.useFont(assets.font(strings.fontFile)) }
        ui.useFont(assets.font(strings.fontFile))
        audio.startMusic()
        setScreen(MenuScreen(this))
    }

    /** Cambia de pantalla liberando la anterior. */
    fun switchTo(next: Screen) {
        val old = screen
        setScreen(next)
        old?.dispose()
    }

    /** Muestra una pantalla temporal (p. ej. instrucciones) sin liberar la actual. */
    fun overlay(next: Screen) {
        setScreen(next)
    }

    /** Vuelve a una pantalla que se conservó, liberando la actual. */
    fun returnTo(previous: Screen) {
        val old = screen
        setScreen(previous)
        old?.dispose()
    }

    override fun resume() {
        super.resume()
        store.refresh()
    }

    override fun dispose() {
        store.dispose()
        screen?.dispose()
        batch.dispose()
        assets.dispose()
        audio.dispose()
    }
}
