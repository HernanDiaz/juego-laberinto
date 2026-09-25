package com.hernan.laberinto

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.Music
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.utils.Disposable

/** Efectos y música generados por tools/generar_sonidos.py. El ajuste de sonido se guarda. */
class Audio : Disposable {
    private val prefs = Gdx.app.getPreferences("laberinto")
    private val sounds = ArrayList<Sound>()

    private fun sfx(name: String) = Gdx.audio.newSound(Gdx.files.internal("sfx/$name.wav")).also { sounds.add(it) }

    val rotate = sfx("rotate")
    val push = sfx("push")
    val step = sfx("step")
    val treasure = sfx("treasure")
    val rival = sfx("rival")
    val click = sfx("click")
    val denied = sfx("denied")
    val win = sfx("win")
    val coin = sfx("coin")
    val event = sfx("event")
    val lose = sfx("lose")

    private val music: Music = Gdx.audio.newMusic(Gdx.files.internal("sfx/music.wav")).apply {
        isLooping = true
        volume = 0.45f
    }

    var enabled = prefs.getBoolean("sound", true)
        private set

    fun play(sound: Sound, volume: Float = 1f, pitch: Float = 1f) {
        if (enabled) sound.play(volume, pitch, 0f)
    }

    fun startMusic() {
        if (enabled && !music.isPlaying) music.play()
    }

    fun stopMusic() = music.stop()

    fun toggle() {
        enabled = !enabled
        prefs.putBoolean("sound", enabled).flush()
        if (enabled) {
            music.play()
            play(click)
        } else {
            music.pause()
        }
    }

    override fun dispose() {
        sounds.forEach { it.dispose() }
        music.dispose()
    }
}
