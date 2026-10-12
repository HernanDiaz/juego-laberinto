package com.hernan.laberinto

import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.Screen
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.hernan.laberinto.model.Level
import com.hernan.laberinto.model.Levels
import com.hernan.laberinto.screens.MenuScreen

/** [platform]: lo que depende de Android o del escritorio (p. ej. permisos de red). */
open class LaberintoGame(val platform: Platform = Platform.NONE) : Game() {
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

    override fun create() {
        Gdx.input.setCatchKey(Input.Keys.BACK, true)
        assets = Assets()
        batch = SpriteBatch()
        ui = Ui(assets, batch)
        audio = Audio()
        strings = Strings()
        levels = Levels.parse(Gdx.files.internal("levels.json").readString("UTF-8"))
        progress = Progress(levels.size)
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
