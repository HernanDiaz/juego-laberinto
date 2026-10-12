package com.hernan.laberinto.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.ScreenUtils
import com.badlogic.gdx.utils.viewport.ExtendViewport
import com.hernan.laberinto.Assets
import com.hernan.laberinto.Buttons
import com.hernan.laberinto.LaberintoGame
import com.hernan.laberinto.PixelButton
import com.hernan.laberinto.Ui
import com.hernan.laberinto.model.GameSetup
import com.hernan.laberinto.model.GameState
import com.hernan.laberinto.net.ClientSession
import com.hernan.laberinto.net.Discovery
import com.hernan.laberinto.net.HostSession
import com.hernan.laberinto.net.Net
import com.hernan.laberinto.net.NetEvent
import com.hernan.laberinto.net.NetSession
import kotlin.math.floor
import kotlin.random.Random

/** Base de las pantallas de menú: fondo, título, botones y opciones con < >. */
abstract class MenuLikeScreen(protected val game: LaberintoGame) : ScreenAdapter() {
    protected val a = game.assets
    protected val ui = game.ui
    protected val batch = game.batch
    protected val s = game.strings
    protected val viewport = ExtendViewport(MenuScreen.WORLD_W, MenuScreen.WORLD_H)
    private val background = MazeBackground(11)
    protected val buttons = Buttons(ui, game.audio)
    protected val touch = Vector2()

    /** Opciones con flechas que hay ahora en pantalla. */
    protected open val options: List<OptionRow> = emptyList()

    protected abstract fun back()
    protected abstract fun drawContent(w: Float, h: Float)
    protected open fun tick(delta: Float) {}

    private val input = object : InputAdapter() {
        override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
            viewport.unproject(touch.set(screenX.toFloat(), screenY.toFloat()))
            if (!buttons.down(touch.x, touch.y) && options.any { it.touch(touch.x, touch.y) }) {
                game.audio.play(game.audio.click)
            }
            return true
        }

        override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
            viewport.unproject(touch.set(screenX.toFloat(), screenY.toFloat()))
            buttons.drag(touch.x, touch.y)
            return true
        }

        override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
            viewport.unproject(touch.set(screenX.toFloat(), screenY.toFloat()))
            buttons.up(touch.x, touch.y)
            return true
        }

        override fun keyDown(keycode: Int): Boolean {
            if (keycode == Input.Keys.BACK || keycode == Input.Keys.ESCAPE) back()
            return true
        }
    }

    override fun show() {
        Gdx.input.inputProcessor = input
    }

    override fun resize(width: Int, height: Int) {
        viewport.update(width, height, true)
    }

    override fun render(delta: Float) {
        tick(delta)
        ScreenUtils.clear(Ui.BG)
        viewport.apply()
        batch.projectionMatrix = viewport.camera.combined
        val w = viewport.worldWidth
        val h = viewport.worldHeight
        batch.begin()
        background.draw(a, ui, batch, w, h, delta)
        drawContent(w, h)
        batch.end()
    }

    protected fun place(b: PixelButton, y: Float, height: Float = 28f, width: Float = 160f) {
        b.rect.set(floor(viewport.worldWidth / 2 - width / 2), y, width, height)
    }
}

/** Elegir: en este móvil o por Wi-Fi. */
class MultiplayerScreen(game: LaberintoGame) : MenuLikeScreen(game) {
    private val localBtn = buttons.add(a.greenButton, 1f, { s["mp.local"] }) { game.switchTo(LocalSetupScreen(game)) }
    private val wifiBtn = buttons.add(a.greenButton, 1f, { s["mp.wifi"] }) { game.switchTo(WifiScreen(game)) }
    private val backBtn = buttons.add(a.slateButton, 1f, { s["howto.back"] }) { back() }

    override fun back() = game.switchTo(MenuScreen(game))

    override fun drawContent(w: Float, h: Float) {
        ui.textCentered(s["menu.multiplayer"], w / 2, h * 0.8f, Ui.GOLD, 2f)
        // Fichas de los 4 colores
        for (k in 0 until 4) batch.draw(a.pawns[k], w / 2 - 44 + k * 22f, h * 0.68f)
        place(localBtn, floor(h * 0.52f), 34f)
        ui.textWrapped(s["mp.localDesc"], 20f, floor(h * 0.52f) - 6, w - 40, Ui.MUTED, Align.center)
        place(wifiBtn, floor(h * 0.34f), 34f)
        ui.textWrapped(s["mp.sameWifi"], 20f, floor(h * 0.34f) - 6, w - 40, Ui.MUTED, Align.center)
        place(backBtn, floor(h * 0.12f))
        buttons.draw()
    }
}

/** Varias personas en el mismo móvil (más rivales IA si se quiere). */
class LocalSetupScreen(game: LaberintoGame) : MenuLikeScreen(game) {
    private var humans = 2
    private var ais = 0
    private var goals = 2
    private var difficulty = 1
    private val maxGoals get() = GameState.maxGoalsFor(humans + ais)

    override val options = listOf(
        OptionRow({ s["mp.humans"] }, { humans.toString() }) {
            humans = (humans + it).coerceIn(2, 4)
            ais = ais.coerceAtMost(4 - humans)
            goals = goals.coerceAtMost(maxGoals)
        },
        OptionRow({ s["menu.opponents"] }, { ais.toString() }) {
            ais = (ais + it).coerceIn(0, 4 - humans)
            goals = goals.coerceAtMost(maxGoals)
        },
        OptionRow({ s["menu.treasures"] }, { goals.toString() }) { goals = (goals + it).coerceIn(1, maxGoals) },
        OptionRow({ s["menu.difficulty"] }, { s["difficulty.$difficulty"] }) { difficulty = (difficulty + it).coerceIn(0, 2) },
    )

    private val playBtn = buttons.add(a.greenButton, 2f, { s["menu.play"] }) {
        val setup = GameSetup(humans = humans, numAi = ais, goalsPerStage = goals, aiSkill = SKILLS[difficulty])
        game.switchTo(GameScreen(game, setup, mode = PlayMode.HotSeat))
    }
    private val backBtn = buttons.add(a.slateButton, 1f, { s["howto.back"] }) { back() }

    override fun back() = game.switchTo(MultiplayerScreen(game))

    override fun drawContent(w: Float, h: Float) {
        ui.textCentered(s["mp.local"], w / 2, h * 0.85f, Ui.GOLD, 2f)
        var y = floor(h * 0.7f)
        for (o in options) {
            o.draw(a, ui, batch, w, y)
            y -= 52
        }
        place(playBtn, y + 2, 38f, 140f)
        place(backBtn, y - 40, 28f, 140f)
        buttons.draw()
    }

    companion object {
        val SKILLS = doubleArrayOf(0.25, 0.55, 1.0)
    }
}

/**
 * Sala de Wi-Fi: buscar partidas, crear una (anfitrión) o unirse por IP.
 * Como en los otros juegos: todos los móviles en la misma Wi-Fi, hasta 4 jugadores.
 */
class WifiScreen(game: LaberintoGame) : MenuLikeScreen(game) {
    private enum class State { BROWSE, CONNECTING, HOSTING, JOINED }

    private var state = State.BROWSE
    private var discovery: Discovery? = Discovery().also { game.platform.lanDiscovery(true) }
    private var session: NetSession? = null
    private var lobbyPlayers = 1
    private var goals = 2
    private var handedOver = false
    private var message = ""
    private var messageTime = 0f

    override val options get() = if (state == State.HOSTING) hostOptions else emptyList()
    private val hostOptions = listOf(
        OptionRow({ s["menu.treasures"] }, { goals.toString() }) {
            goals = (goals + it).coerceIn(1, GameState.maxGoalsFor(Net.MAX_PLAYERS))
        },
    )

    // Botones de cada estado (se muestran solo los del estado actual)
    private val browse = Buttons(ui, game.audio)
    private val createBtn = browse.add(a.greenButton, 1f, { s["mp.create"] }) { host() }
    private val ipBtn = browse.add(a.slateButton, 1f, { s["mp.joinIp"] }) { askIp() }
    private val browseBack = browse.add(a.slateButton, 1f, { s["howto.back"] }) { back() }
    private val gameButtons = ArrayList<PixelButton>()
    private val found = Buttons(ui, game.audio)

    private val hostBtns = Buttons(ui, game.audio)
    private val startBtn = hostBtns.add(a.greenButton, 2f, { s["mp.start"] }) { start() }
    private val hostLeave = hostBtns.add(a.slateButton, 1f, { s["mp.leave"] }) { stopSession() }

    private val waitBtns = Buttons(ui, game.audio)
    private val waitLeave = waitBtns.add(a.slateButton, 1f, { s["mp.leave"] }) { stopSession() }

    private val activeButtons: Buttons
        get() = when (state) {
            State.BROWSE -> browse
            State.HOSTING -> hostBtns
            State.CONNECTING, State.JOINED -> waitBtns
        }

    private val input = object : InputAdapter() {
        override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
            viewport.unproject(touch.set(screenX.toFloat(), screenY.toFloat()))
            if (activeButtons.down(touch.x, touch.y)) return true
            if (state == State.BROWSE && found.down(touch.x, touch.y)) return true
            if (options.any { it.touch(touch.x, touch.y) }) {
                game.audio.play(game.audio.click)
                (session as? HostSession)?.send("LOBBY|$lobbyPlayers")
            }
            return true
        }

        override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
            viewport.unproject(touch.set(screenX.toFloat(), screenY.toFloat()))
            activeButtons.drag(touch.x, touch.y)
            found.drag(touch.x, touch.y)
            return true
        }

        override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
            viewport.unproject(touch.set(screenX.toFloat(), screenY.toFloat()))
            if (!activeButtons.up(touch.x, touch.y) && state == State.BROWSE) found.up(touch.x, touch.y)
            return true
        }

        override fun keyDown(keycode: Int): Boolean {
            if (keycode == Input.Keys.BACK || keycode == Input.Keys.ESCAPE) back()
            return true
        }
    }

    override fun show() {
        Gdx.input.inputProcessor = input
    }

    // Solo para el modo tour de escritorio (pruebas de red automáticas)
    fun debugHost() = host()
    fun debugJoin(ip: String) = join(ip)
    fun debugStart() = start()

    private fun showMessage(text: String) {
        message = text
        messageTime = 3f
    }

    override fun back() {
        if (state != State.BROWSE) stopSession() else game.switchTo(MultiplayerScreen(game))
    }

    private fun host() {
        val h = runCatching { HostSession() }.getOrNull()
        if (h == null) {
            showMessage(s["mp.failed"])
            return
        }
        session = h
        lobbyPlayers = 1
        state = State.HOSTING
    }

    private fun join(ip: String) {
        session = ClientSession(ip.trim())
        state = State.CONNECTING
    }

    private fun askIp() {
        val last = Net.localIps().firstOrNull()?.substringBeforeLast('.')?.plus(".") ?: ""
        Gdx.input.getTextInput(object : Input.TextInputListener {
            override fun input(text: String) {
                Gdx.app.postRunnable { if (text.isNotBlank()) join(text) }
            }

            override fun canceled() {}
        }, s["mp.ipTitle"], last, "192.168.1.20")
    }

    private fun stopSession() {
        session?.close()
        session = null
        state = State.BROWSE
    }

    /** (Anfitrión) Empieza la partida: cada invitado recibe la semilla y su puesto. */
    private fun start() {
        val host = session as? HostSession ?: return
        if (host.players < 2) {
            showMessage(s["mp.needTwo"])
            return
        }
        host.started = true
        val seed = Random.nextLong()
        val players = host.players
        // Puestos en la partida: 1, 2, 3 en el orden de llegada (las jugadas llevan este número)
        host.slots.forEachIndexed { k, slot -> host.sendTo(slot, "START|$seed|$goals|$players|${k + 1}") }
        launch(host, 0, seed, goals, players)
    }

    private fun launch(net: NetSession, localSlot: Int, seed: Long, goals: Int, players: Int) {
        handedOver = true
        val setup = GameSetup(humans = players, numAi = 0, goalsPerStage = goals.coerceAtMost(GameState.maxGoalsFor(players)))
        game.switchTo(GameScreen(game, setup, mode = PlayMode.Online(net, localSlot, seed)))
    }

    override fun tick(delta: Float) {
        if (messageTime > 0) messageTime -= delta
        val net = session ?: return
        while (true) {
            when (val e = net.events.poll() ?: break) {
                is NetEvent.Joined, is NetEvent.Left -> {
                    val host = net as HostSession
                    lobbyPlayers = host.players
                    host.send("LOBBY|$lobbyPlayers")
                }
                is NetEvent.Connected -> state = State.JOINED
                is NetEvent.Lost -> {
                    showMessage(s[if (e.beforeConnecting) "mp.failed" else "mp.hostLeft"])
                    stopSession()
                    return
                }
                is NetEvent.Message -> {
                    val f = e.text.split('|')
                    when (f[0]) {
                        "LOBBY" -> lobbyPlayers = f[1].toInt()
                        "START" -> {
                            launch(net, f[4].toInt(), f[1].toLong(), f[2].toInt(), f[3].toInt())
                            return
                        }
                        "HELLO" -> if (f.getOrNull(1)?.toIntOrNull() != Net.VERSION) {
                            showMessage(s["mp.failed"])
                        }
                    }
                }
            }
        }
    }

    override fun drawContent(w: Float, h: Float) {
        ui.textCentered(s["mp.wifi"], w / 2, h * 0.88f, Ui.GOLD, 2f)
        val ip = Net.localIps().firstOrNull()
        when (state) {
            State.BROWSE -> drawBrowse(w, h)
            State.CONNECTING -> {
                ui.textCentered(s["mp.connecting"], w / 2, h * 0.6f, Color.WHITE)
                place(waitLeave, floor(h * 0.2f))
                waitBtns.draw()
            }
            State.HOSTING -> {
                if (ip != null) ui.textCentered(s.format("mp.yourIp", ip), w / 2, h * 0.79f, Ui.MUTED)
                drawPlayers(w, h * 0.68f)
                ui.textCentered(s.format("mp.players", lobbyPlayers), w / 2, h * 0.6f, Color.WHITE)
                hostOptions[0].draw(a, ui, batch, w, floor(h * 0.44f))
                place(startBtn, floor(h * 0.27f), 38f, 140f)
                place(hostLeave, floor(h * 0.17f), 28f, 140f)
                hostBtns.draw()
            }
            State.JOINED -> {
                drawPlayers(w, h * 0.68f)
                ui.textCentered(s.format("mp.players", lobbyPlayers), w / 2, h * 0.6f, Color.WHITE)
                ui.textWrapped(s["mp.waitHost"], 20f, h * 0.5f, w - 40, Ui.MUTED, Align.center)
                place(waitLeave, floor(h * 0.2f))
                waitBtns.draw()
            }
        }
        if (messageTime > 0) {
            val tw = ui.textWidth(message) + 16
            ui.panel(floor(w / 2 - tw / 2), h * 0.08f, floor(tw), 22f, Color(0.1f, 0.12f, 0.18f, 0.95f), Ui.GOLD)
            ui.textCentered(message, w / 2, h * 0.08f + 11, Color.WHITE)
        }
    }

    private fun drawPlayers(w: Float, y: Float) {
        for (k in 0 until Net.MAX_PLAYERS) {
            batch.color = if (k < lobbyPlayers) Color.WHITE else Color(0.3f, 0.3f, 0.35f, 0.6f)
            batch.draw(a.pawns[k], w / 2 - 44 + k * 22f, y)
        }
        batch.color = Color.WHITE
    }

    private fun drawBrowse(w: Float, h: Float) {
        ui.textWrapped(s["mp.sameWifi"], 20f, h * 0.81f, w - 40, Ui.MUTED, Align.center)
        // Partidas encontradas: un botón por cada una
        val games = discovery?.games().orEmpty()
        while (gameButtons.size < games.size) {
            val k = gameButtons.size
            gameButtons.add(found.add(a.greenButton, 1f, {
                val g = discovery?.games()?.getOrNull(k)
                if (g == null) "" else s.format("mp.gameAt", g.ip, g.players)
            }) { discovery?.games()?.getOrNull(k)?.let { join(it.ip) } })
        }
        var y = floor(h * 0.66f)
        if (games.isEmpty()) {
            ui.textCentered(s["mp.searching"], w / 2, y + 10, Color.WHITE)
        }
        gameButtons.forEachIndexed { k, b ->
            if (k < games.size) {
                place(b, y, 28f, 200f)
                y -= 34
            } else {
                b.rect.set(-1000f, -1000f, 0f, 0f)
            }
        }
        if (games.isNotEmpty()) found.draw()
        place(createBtn, floor(h * 0.3f), 34f)
        place(ipBtn, floor(h * 0.21f))
        place(browseBack, floor(h * 0.12f))
        browse.draw()
    }

    override fun dispose() {
        discovery?.close()
        discovery = null
        game.platform.lanDiscovery(false)
        // La sesión pasa a la partida; si no se llegó a empezar, se cierra
        if (!handedOver) session?.close()
    }
}
