package com.hernan.laberinto

import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.Screen
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.hernan.laberinto.screens.MenuScreen

open class LaberintoGame : Game() {
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

    override fun create() {
        Gdx.input.setCatchKey(Input.Keys.BACK, true)
        assets = Assets()
        batch = SpriteBatch()
        ui = Ui(assets, batch)
        audio = Audio()
        strings = Strings()
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

    override fun dispose() {
        screen?.dispose()
        batch.dispose()
        assets.dispose()
        audio.dispose()
    }
}
