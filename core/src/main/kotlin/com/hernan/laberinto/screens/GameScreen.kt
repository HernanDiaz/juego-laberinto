package com.hernan.laberinto.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.ScreenUtils
import com.badlogic.gdx.utils.viewport.ExtendViewport
import com.hernan.laberinto.Assets
import com.hernan.laberinto.Assets.Companion.TILE
import com.hernan.laberinto.Buttons
import com.hernan.laberinto.LaberintoGame
import com.hernan.laberinto.Ui
import com.hernan.laberinto.model.Ai
import com.hernan.laberinto.model.AiPlan
import com.hernan.laberinto.model.Board
import com.hernan.laberinto.model.GameState
import com.hernan.laberinto.model.EventType
import com.hernan.laberinto.model.GameSetup
import com.hernan.laberinto.model.Level
import com.hernan.laberinto.model.Insert
import com.hernan.laberinto.model.Item
import com.hernan.laberinto.model.Player
import com.hernan.laberinto.model.Pos
import com.hernan.laberinto.model.Side
import com.hernan.laberinto.model.Tile
import com.hernan.laberinto.net.HostSession
import com.hernan.laberinto.net.NetEvent
import kotlin.random.Random
import kotlin.math.floor
import kotlin.math.sin

/**
 * Partida. Con [level] es un nivel del mapa (cuenta estrellas); sin él, una partida libre.
 * [mode] dice si se juega solo, pasándose el móvil o por Wi-Fi.
 */
class GameScreen(
    private val game: LaberintoGame,
    private val setup: GameSetup,
    private val level: Level? = null,
    private val mode: PlayMode = PlayMode.Solo,
) : ScreenAdapter() {
    private val a = game.assets
    private val ui = game.ui
    private val batch = game.batch
    private val viewport = ExtendViewport(MenuScreen.WORLD_W, MenuScreen.WORLD_H)

    private val online = mode as? PlayMode.Online
    private val hotSeat = mode is PlayMode.HotSeat

    // Por Wi-Fi todos los móviles usan la misma semilla: la partida es determinista
    private val state = GameState(setup, online?.let { Random(it.seed) } ?: Random.Default)
    private val board get() = state.board

    /** Puestos que se juegan desde este móvil. */
    private val localSlots: Set<Int> = online?.let { setOf(it.localSlot) } ?: (0 until setup.humans).toSet()

    /** Jugador cuyas metas se muestran (pasándose el móvil: el último humano que ha jugado). */
    private var viewerSlot = online?.localSlot ?: 0
    private val human get() = state.players[viewerSlot]

    /** ¿Es "yo"? (para decir "¡Has ganado!" en vez de "Gana Azul"). Pasándose el móvil, nadie lo es. */
    private fun isMe(p: Player) = !hotSeat && p.index == viewerSlot

    /** Nombre de un jugador: "Tú" para uno mismo y el color para los demás. */
    private fun nameOf(i: Int): String = when {
        mode is PlayMode.Solo -> str.player(i)
        online != null && i == online.localSlot -> str.player(0)
        i == 0 -> str["color.0"]
        else -> str.player(i)
    }

    /** La IA usa su propio azar: así no altera el de la partida (que debe ser igual en todos los móviles). */
    private val aiRng = Random(System.nanoTime())

    // Jugada del turno local (para enviarla por Wi-Fi)
    private var turnRotations = 0
    private var turnInsert: Insert? = null
    private var moveSeq = 0
    private val remoteMoves = ArrayDeque<Pair<Int, AiPlan>>()
    private var leaveTimer = -1f

    // Pistas (niveles de aprendizaje): la jugada que haría una IA experta
    private val hints = level?.hints == true
    private val hintRng = kotlin.random.Random(7)
    private var hintPlan: AiPlan? = null
    private var hintMask = -1
    private var hintDest: Pos? = null

    private val audio = game.audio
    private val str = game.strings

    private enum class Phase { HANDOFF, PLACE, SLIDE, MOVE, WALK, SWAP, AI_THINK, AI_ROTATE, REMOTE, OVER }

    private var phase = Phase.PLACE
    private var timer = 0f
    private var time = 0f
    private var slide: Insert? = null
    private var walkPath: List<Pos> = emptyList()
    private var reach: Set<Pos> = emptySet()
    private var aiPlan: AiPlan? = null
    private var aiRotationsLeft = 0
    private var status = ""
    private var toast = ""
    private var toastTime = 0f
    /** Avisos en espera, con el momento en que se generaron. */
    private val pendingToasts = ArrayDeque<Pair<String, Float>>()

    // Disposición (en unidades de mundo, recalculada en resize)
    private var boardX = 0f
    private var boardY = 0f
    private val spareRect = Rectangle()
    private val soundBtn = Rectangle()
    private var lastStep = 0
    private val pickedThisWalk = HashSet<Tile>()

    // Mudanza: las dos fichas saltan y se cruzan hasta la casilla de la otra
    private var swapA: Player? = null
    private var swapB: Player? = null
    private var swapFromA = Pos(0, 0)
    private var swapFromB = Pos(0, 0)
    private val touch = Vector2()

    // Pausa
    private var paused = false
    private val pauseBtn = Rectangle()
    private val pauseButtons = Buttons(ui, audio).apply {
        add(a.greenButton, 1f, { str["pause.resume"] }) { paused = false }
        add(a.slateButton, 1f, { str["menu.howto"] }) { game.overlay(InstructionsScreen(game, this@GameScreen)) }
        add(a.slateButton, 1f, { str[if (level != null) "over.map" else if (online != null) "mp.leave" else "pause.menu"] }) { leave() }
    }

    // Final de partida: estrellas conseguidas y botones
    private var starsWon = 0
    private val overButtons = Buttons(ui, audio)
    private val nextLevel: Level? get() = level?.let { game.levels.getOrNull(it.index + 1) }

    private fun restart() = game.switchTo(GameScreen(game, setup, level, mode))

    /** Vuelve al mapa (niveles) o al menú (partida libre y multijugador). */
    private fun leave() {
        online?.session?.close()
        game.switchTo(if (level != null) LevelMapScreen(game) else MenuScreen(game))
    }

    private fun buildOverButtons() {
        val won = state.winner?.let { isMe(it) } == true
        if (online != null) {
            overButtons.add(a.greenButton, 1f, { str["pause.menu"] }) { leave() }
        } else if (level != null) {
            val next = nextLevel
            if (won && next != null) {
                overButtons.add(a.greenButton, 1f, { str["over.next"] }) { game.switchTo(GameScreen(game, next.setup, next)) }
                overButtons.add(a.slateButton, 1f, { str["over.retry"] }) { restart() }
            } else {
                overButtons.add(if (won) a.slateButton else a.greenButton, 1f, { str["over.retry"] }) { restart() }
            }
            overButtons.add(a.slateButton, 1f, { str["over.map"] }) { leave() }
        } else {
            overButtons.add(a.greenButton, 1f, { str["over.again"] }) { restart() }
            overButtons.add(a.slateButton, 1f, { str["pause.menu"] }) { leave() }
        }
    }

    private val input = object : InputAdapter() {
        override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
            viewport.unproject(touch.set(screenX.toFloat(), screenY.toFloat()))
            when {
                paused -> pauseButtons.down(touch.x, touch.y)
                phase == Phase.OVER -> if (timer > 0.8f) overButtons.down(touch.x, touch.y)
                else -> onTouch(touch.x, touch.y)
            }
            return true
        }

        override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
            viewport.unproject(touch.set(screenX.toFloat(), screenY.toFloat()))
            if (paused) pauseButtons.drag(touch.x, touch.y)
            if (phase == Phase.OVER) overButtons.drag(touch.x, touch.y)
            return true
        }

        override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
            viewport.unproject(touch.set(screenX.toFloat(), screenY.toFloat()))
            if (paused) pauseButtons.up(touch.x, touch.y) else if (phase == Phase.OVER) overButtons.up(touch.x, touch.y)
            return true
        }

        override fun keyDown(keycode: Int): Boolean {
            when (keycode) {
                Input.Keys.BACK, Input.Keys.ESCAPE ->
                    if (phase == Phase.OVER || leaveTimer >= 0) {
                        leave()
                    } else {
                        paused = !paused
                        audio.play(audio.click)
                    }
                Input.Keys.R -> if (phase == Phase.PLACE && !paused) rotateSpare()
            }
            return true
        }
    }

    /** La app pasa a segundo plano: pausamos la partida (por Wi-Fi sigue corriendo). */
    override fun pause() {
        if (phase != Phase.OVER && online == null) paused = true
    }

    override fun dispose() {
        online?.session?.close()
    }

    init {
        startTurn()
    }

    override fun show() {
        Gdx.input.inputProcessor = input
    }

    override fun resize(width: Int, height: Int) {
        viewport.update(width, height, true)
        val w = viewport.worldWidth
        val h = viewport.worldHeight
        boardX = floor((w - BOARD_PX) / 2)
        boardY = floor(h / 2 - BOARD_PX / 2 + 20)
        val spareSize = TILE * 2f
        spareRect.set(floor(w / 2 - spareSize - 20), floor(boardY - 40 - spareSize), spareSize, spareSize)
        soundBtn.set(w - 30, 2f, 28f, 22f)
    }

    // ------------------------------------------------------------------ turnos

    private fun startTurn() {
        timer = 0f
        val p = state.currentPlayer
        when {
            p.isAi -> {
                phase = Phase.AI_THINK
                status = str.format("game.turnAi", nameOf(p.index), str["ai.${p.personality!!.name}"])
            }
            p.index !in localSlots -> {
                // Turno de otro móvil: se espera su jugada
                phase = Phase.REMOTE
                status = str.format("mp.waitTurn", nameOf(p.index))
            }
            hotSeat && setup.humans > 1 -> {
                // Se tapa el tablero hasta que el siguiente coja el móvil (las metas son secretas)
                viewerSlot = p.index
                phase = Phase.HANDOFF
                status = ""
            }
            else -> beginPlace()
        }
    }

    private fun beginPlace() {
        timer = 0f
        turnRotations = 0
        turnInsert = null
        run {
            phase = Phase.PLACE
            status = str["game.place"]
            if (hints) {
                val plan = Ai.plan(state, hintRng, skill = 1.0)
                hintPlan = plan
                hintMask = (0 until plan.rotations).fold(board.spare.mask) { m, _ -> com.hernan.laberinto.model.Dirs.rotateCw(m) }
                hintDest = null
                status = str["game.hintPlace"]
            }
        }
    }

    private fun rotateSpare() {
        board.spare.rotateCw()
        turnRotations++
        audio.play(audio.rotate)
    }

    private fun beginSlide(ins: Insert) {
        audio.play(audio.push, 0.8f)
        turnInsert = ins
        slide = ins
        phase = Phase.SLIDE
        timer = 0f
    }

    private fun afterPush() {
        val p = state.currentPlayer
        reach = board.reachable(p.pos)
        slide = null
        if (p.isAi || p.index !in localSlots) {
            // IA o jugador de otro móvil: su ficha camina sola hasta el destino elegido
            beginWalk(aiPlan!!.dest)
        } else {
            phase = Phase.MOVE
            timer = 0f
            status = if (reach.size > 1) str["game.move"] else str["game.noPath"]
            if (hints && reach.size > 1) {
                hintDest = Ai.bestDestination(state)
                status = str["game.hintMove"]
            }
        }
    }

    private fun beginWalk(dest: Pos) {
        val p = state.currentPlayer
        // Por Wi-Fi, la jugada de este móvil se envía a los demás
        val net = online
        if (net != null && p.index == net.localSlot) {
            val ins = turnInsert!!
            net.session.send("MOVE|${moveSeq++}|${p.index}|${turnRotations % 4}|${ins.side.name}|${ins.index}|${dest.r}|${dest.c}")
        }
        walkPath = board.path(state.currentPlayer.pos, dest) ?: listOf(state.currentPlayer.pos)
        phase = Phase.WALK
        timer = 0f
        lastStep = 0
        pickedThisWalk.clear()
    }

    private fun finishWalk() {
        val p = state.currentPlayer
        val stageBefore = p.stage
        val r = state.moveCurrent(walkPath)
        pickedThisWalk.clear()
        if (r.goal >= 0) {
            showToast(
                if (isMe(p)) str.format("game.youFound", str.treasure(r.goal))
                else str.format("game.found", nameOf(p.index), str.treasure(r.goal)),
            )
            audio.play(if (isMe(p) || (hotSeat && !p.isAi)) audio.treasure else audio.rival)
            if (r.reward > 0 && p.index in localSlots) showToast(str.format("game.reward", r.reward))
            if (p.index in localSlots && p.stage != stageBefore) {
                showToast(if (p.retiring) str["game.goHome"] else str.format("game.newStage", str.stage(p.stage)))
            }
        }
        if (r.blockedGoal >= 0 && p.index in localSlots) {
            // Se muestra ya: si esperase en la cola podría salir más tarde y confundir
            showToastNow(str.format("game.needCoins", r.missingCoins, str.treasure(r.blockedGoal)))
            audio.play(audio.denied)
        }
        r.event?.let { e ->
            audio.play(audio.event)
            if (e == EventType.SWAP) {
                // Se avisa al momento (sin esperar a otros avisos) y se anima el cambio de sitio
                val other = r.swappedWith!!
                showToastNow(str.format("event.SWAP", nameOf(p.index), nameOf(other.index)))
                swapA = p
                swapB = other
                swapFromA = walkPath.last()
                swapFromB = p.pos
                phase = Phase.SWAP
                timer = 0f
                return
            }
            showToast(str.format("event.${e.name}", nameOf(p.index), kotlin.math.abs(r.eventCoins)))
        }
        endTurn()
    }

    /** Solo para el modo tour de escritorio: los turnos de este móvil se juegan solos. */
    var debugAutoPlay = false
    private var autoDest: Pos? = null

    /** Solo para el modo tour: resumen del estado, para comparar dos móviles en red. */
    fun debugDump(): String =
        "ronda=${state.round} turno=${state.current} ganador=${state.winner?.index} " +
            state.players.joinToString(" ") { "[${it.index}:${it.pos.r},${it.pos.c} m=${it.coins} c=${it.collected}]" } +
            " monedas=" + board.allCells().filter { board[it].item == Item.COIN }.joinToString(",") { "${it.r}${it.c}" }

    private fun autoPlayStep() {
        if (!debugAutoPlay || state.currentPlayer.index !in localSlots) return
        when (phase) {
            Phase.HANDOFF -> beginPlace()
            Phase.PLACE -> if (timer > 0.3f) {
                val plan = Ai.plan(state, aiRng, 0.6)
                repeat(plan.rotations) { rotateSpare() }
                autoDest = plan.dest
                beginSlide(plan.insert)
            }
            Phase.MOVE -> if (timer > 0.2f) beginWalk(autoDest ?: state.currentPlayer.pos)
            else -> {}
        }
    }

    /** Solo para el modo tour de escritorio: fuerza una mudanza entre el jugador y el primer rival. */
    fun debugSwap() {
        if (phase != Phase.PLACE) return
        val other = state.players[1]
        swapA = human
        swapB = other
        swapFromA = human.pos
        swapFromB = other.pos
        human.pos = swapFromB
        other.pos = swapFromA
        audio.play(audio.event)
        showToastNow(str.format("event.SWAP", str.player(human.index), str.player(other.index)))
        phase = Phase.SWAP
        timer = 0f
    }

    /** Solo para el modo tour de escritorio: el jugador gana al instante. */
    fun debugWin() {
        if (phase != Phase.PLACE) return
        human.stages.forEach { it.clear() }
        human.pos = human.home
        state.moveCurrent(listOf(human.home))
        endTurn()
    }

    private fun endTurn() {
        swapA = null
        swapB = null
        val winner = state.winner
        if (winner != null) {
            phase = Phase.OVER
            timer = 0f
            audio.play(if (isMe(winner) || (hotSeat && !winner.isAi)) audio.win else audio.lose)
            status = ""
            if (level != null && isMe(winner)) {
                starsWon = level.stars(state.round, human.coins)
                game.progress.record(level.index, starsWon)
            }
            buildOverButtons()
            return
        }
        state.nextTurn()
        startTurn()
    }

    /** Muestra un aviso ya, descartando los que esperaban. */
    private fun showToastNow(msg: String) {
        pendingToasts.clear()
        toast = msg
        toastTime = TOAST_TIME + 0.6f
    }

    /** Los avisos se muestran de uno en uno, en orden. */
    private fun showToast(msg: String) {
        if (toastTime > 0) {
            pendingToasts.addLast(msg to time)
            return
        }
        toast = msg
        toastTime = TOAST_TIME
    }

    // ------------------------------------------------------------------ lógica

    /** Mensajes de la red (en el hilo del juego). */
    private fun pollNet() {
        val net = online ?: return
        while (true) {
            when (val e = net.session.events.poll() ?: break) {
                is NetEvent.Message -> {
                    val f = e.text.split('|')
                    when (f[0]) {
                        "MOVE" -> {
                            // El anfitrión reenvía la jugada de un invitado a los demás
                            (net.session as? HostSession)?.relay(e.from, e.text)
                            val player = f[2].toInt()
                            val ins = Insert(Side.valueOf(f[4]), f[5].toInt())
                            remoteMoves.addLast(player to AiPlan(f[3].toInt(), ins, Pos(f[6].toInt(), f[7].toInt())))
                        }
                        "LEFT" -> connectionLost("mp.playerLeft")
                    }
                }
                is NetEvent.Left -> {
                    (net.session as? HostSession)?.send("LEFT|${e.slot}")
                    connectionLost("mp.playerLeft")
                }
                is NetEvent.Lost -> connectionLost("mp.hostLeft")
                else -> {}
            }
        }
    }

    /** Alguien se ha ido: aviso y vuelta al menú. */
    private fun connectionLost(key: String) {
        if (leaveTimer >= 0 || phase == Phase.OVER) return
        showToastNow(str[key])
        audio.play(audio.denied)
        leaveTimer = 3f
        phase = Phase.REMOTE
    }

    private fun update(delta: Float) {
        timer += delta
        time += delta
        pollNet()
        autoPlayStep()
        if (leaveTimer >= 0) {
            leaveTimer -= delta
            if (leaveTimer < 0) leave()
            return
        }
        if (toastTime > 0) {
            toastTime -= delta
            // Los avisos que llevan esperando más de unos segundos ya no vienen a cuento
            while (toastTime <= 0 && pendingToasts.isNotEmpty()) {
                val (msg, at) = pendingToasts.removeFirst()
                if (time - at > TOAST_MAX_WAIT) continue
                toast = msg
                toastTime = TOAST_TIME
            }
        }
        when (phase) {
            Phase.REMOTE -> {
                // Jugada de otro móvil: se reproduce como la de la IA
                val next = remoteMoves.firstOrNull()
                if (next != null && next.first == state.currentPlayer.index) {
                    remoteMoves.removeFirst()
                    aiPlan = next.second
                    aiRotationsLeft = next.second.rotations
                    phase = Phase.AI_ROTATE
                    timer = 0f
                }
            }

            Phase.AI_THINK -> if (timer > 0.6f) {
                aiPlan = Ai.plan(state, aiRng)
                hintMask = -1
                aiRotationsLeft = aiPlan!!.rotations
                phase = Phase.AI_ROTATE
                timer = 0f
            }

            Phase.AI_ROTATE -> if (timer > 0.3f) {
                timer = 0f
                if (aiRotationsLeft > 0) {
                    rotateSpare()
                    aiRotationsLeft--
                } else {
                    beginSlide(aiPlan!!.insert)
                }
            }

            Phase.SLIDE -> if (timer >= SLIDE_TIME) {
                state.push(slide!!)
                afterPush()
            }

            Phase.WALK -> {
                val step = (timer / STEP_TIME).toInt().coerceAtMost(walkPath.size - 1)
                if (step > lastStep) {
                    lastStep = step
                    audio.play(audio.step, 0.7f, MathUtils.random(0.9f, 1.15f))
                    // Las monedas se recogen al pasar por encima
                    val tile = board[walkPath[step]]
                    if (tile.item == Item.COIN && pickedThisWalk.add(tile)) audio.play(audio.coin, 0.8f)
                }
                if (timer >= STEP_TIME * (walkPath.size - 1)) finishWalk()
            }
            Phase.SWAP -> if (timer >= SWAP_TIME) endTurn()
            else -> {}
        }
    }

    private fun onTouch(x: Float, y: Float) {
        if (soundBtn.contains(x, y)) {
            audio.toggle()
            return
        }
        if (pauseBtn.contains(x, y) && phase != Phase.OVER) {
            paused = true
            audio.play(audio.click)
            return
        }
        when (phase) {
            Phase.HANDOFF -> {
                audio.play(audio.click)
                beginPlace()
            }

            Phase.PLACE -> {
                if (spareRect.contains(x, y)) {
                    rotateSpare()
                    return
                }
                val ins = board.config.inserts.firstOrNull { arrowRect(it).contains(x, y) } ?: return
                if (ins in state.allowedInserts()) {
                    beginSlide(ins)
                } else {
                    showToast(str["game.undo"])
                    audio.play(audio.denied)
                }
            }

            Phase.MOVE -> {
                val cell = cellAt(x, y) ?: return
                if (cell in reach) beginWalk(cell)
            }

            else -> {}
        }
    }

    // ------------------------------------------------------------------ geometría

    private fun cellX(c: Int) = boardX + c * TILE
    private fun cellY(r: Int) = boardY + (Board.SIZE - 1 - r) * TILE

    private fun cellAt(x: Float, y: Float): Pos? {
        val c = floor((x - boardX) / TILE).toInt()
        val r = Board.SIZE - 1 - floor((y - boardY) / TILE).toInt()
        return Pos(r, c).takeIf { it.inBoard() }
    }

    /** Centro de la flecha de una entrada. */
    private fun arrowCenter(ins: Insert, out: Vector2): Vector2 = when (ins.side) {
        Side.LEFT -> out.set(boardX - 9, cellY(ins.index) + TILE / 2f)
        Side.RIGHT -> out.set(boardX + BOARD_PX + 9, cellY(ins.index) + TILE / 2f)
        Side.TOP -> out.set(cellX(ins.index) + TILE / 2f, boardY + BOARD_PX + 9)
        Side.BOTTOM -> out.set(cellX(ins.index) + TILE / 2f, boardY - 9)
    }

    private val tmp = Vector2()
    private fun arrowRect(ins: Insert): Rectangle {
        arrowCenter(ins, tmp)
        return Rectangle(tmp.x - 14, tmp.y - 14, 28f, 28f)
    }

    /** Posición de dibujo del elemento k-ésimo de la línea que se desliza (k puede ser fraccionario). */
    private fun linePoint(ins: Insert, k: Float, out: Vector2): Vector2 {
        val first = ins.cells()[0]
        out.set(cellX(first.c), cellY(first.r))
        when (ins.side) {
            Side.LEFT -> out.x += k * TILE
            Side.RIGHT -> out.x -= k * TILE
            Side.TOP -> out.y -= k * TILE
            Side.BOTTOM -> out.y += k * TILE
        }
        return out
    }

    private fun slideProgress() = Interpolation.smooth.apply((timer / SLIDE_TIME).coerceIn(0f, 1f))

    // ------------------------------------------------------------------ dibujo

    override fun render(delta: Float) {
        if (!paused || online != null) update(delta)
        ScreenUtils.clear(Ui.BG)
        viewport.apply()
        batch.projectionMatrix = viewport.camera.combined
        batch.begin()
        drawBoard()
        drawArrows()
        drawHud()
        drawBottom()
        drawToast()
        drawCornerButtons()
        if (phase == Phase.OVER) drawGameOver()
        if (phase == Phase.HANDOFF) drawHandoff()
        if (paused) drawPause()
        batch.end()
    }

    private fun drawTile(tile: Tile, x: Float, y: Float) {
        batch.draw(a.tiles[tile.mask], x, y)
        if (tile.treasure >= 0) batch.draw(a.treasures[tile.treasure], x + 8, y + 8)
        when (tile.item) {
            Item.COIN -> if (tile !in pickedThisWalk) batch.draw(a.smallCoin, x + 12, y + 12)
            Item.EVENT -> batch.draw(a.event, x + 8, y + 8)
        }
    }

    private fun drawBoard() {
        ui.panel(boardX - 3, boardY - 3, BOARD_PX + 6, BOARD_PX + 6, Ui.SHADOW, Ui.BORDER)

        val ins = if (phase == Phase.SLIDE) slide else null
        val t = slideProgress()
        val line = ins?.cells()
        val v = Vector2()
        for (r in 0 until Board.SIZE) for (c in 0 until Board.SIZE) {
            val p = Pos(r, c)
            val k = line?.indexOf(p) ?: -1
            if (k >= 0) {
                linePoint(ins!!, k + t, v)
                drawTile(board[p], v.x, v.y)
            } else {
                drawTile(board[p], cellX(c), cellY(r))
            }
        }
        if (ins != null) {
            linePoint(ins, -1 + t, v)
            drawTile(board.spare, v.x, v.y)
        }

        // Casillas de salida (las esquinas son fijas)
        for (p in state.players) batch.draw(a.homes[p.index], cellX(p.home.c), cellY(p.home.r))

        // Casillas alcanzables
        if (phase == Phase.MOVE) {
            val alpha = 0.22f + 0.1f * sin(time * 5f)
            for (p in reach) ui.rect(cellX(p.c) + 2, cellY(p.r) + 2, TILE - 4f, TILE - 4f, Color(1f, 1f, 0.85f, alpha))
        }

        // Metas activas del jugador humano (o su casa si le toca jubilarse)
        if (phase != Phase.SLIDE && phase != Phase.OVER) {
            val pulse = 0.55f + 0.45f * sin(time * 6f)
            batch.color = Color(Ui.GOLD.r, Ui.GOLD.g, Ui.GOLD.b, pulse)
            for (goal in state.goalsOf(human)) batch.draw(a.cursor, cellX(goal.c), cellY(goal.r))
            batch.color = Color.WHITE
        }

        // Pista: casilla recomendada, en verde (encima del marco dorado de las metas)
        val dest = hintDest
        if (hints && phase == Phase.MOVE && dest != null) {
            val pulse = 0.7f + 0.3f * sin(time * 8f)
            ui.rect(cellX(dest.c) + 2, cellY(dest.r) + 2, TILE - 4f, TILE - 4f, Color(0.4f, 1f, 0.45f, 0.25f * pulse))
            batch.color = Color(0.4f, 1f, 0.45f, pulse)
            batch.draw(a.cursor, cellX(dest.c), cellY(dest.r))
            batch.color = Color.WHITE
        }

        drawPawns(ins, t)
    }

    private fun drawPawns(ins: Insert?, t: Float) {
        val line = ins?.cells()
        // Agrupa fichas por casilla para separarlas si coinciden
        val groups = state.players.groupBy { it.pos }
        val v = Vector2()
        for ((pos, group) in groups) {
            group.forEachIndexed { i, p ->
                var x: Float
                var y: Float
                if (phase == Phase.SWAP && (p === swapA || p === swapB)) {
                    val from = if (p === swapA) swapFromA else swapFromB
                    val f = Interpolation.smooth.apply((timer / SWAP_TIME).coerceIn(0f, 1f))
                    x = MathUtils.lerp(cellX(from.c), cellX(p.pos.c), f)
                    y = MathUtils.lerp(cellY(from.r), cellY(p.pos.r), f) + sin(f * MathUtils.PI) * 18f
                } else if (phase == Phase.WALK && p === state.currentPlayer && walkPath.size > 1) {
                    val f = (timer / STEP_TIME).coerceIn(0f, walkPath.size - 1f)
                    val idx = f.toInt().coerceAtMost(walkPath.size - 2)
                    val frac = f - idx
                    val from = walkPath[idx]
                    val to = walkPath[idx + 1]
                    x = MathUtils.lerp(cellX(from.c), cellX(to.c), frac)
                    y = MathUtils.lerp(cellY(from.r), cellY(to.r), frac) + hop(frac)
                } else {
                    val k = line?.indexOf(pos) ?: -1
                    if (k >= 0) {
                        // La ficha que sale por un lado aparece sobre la pieza que entra
                        linePoint(ins!!, if (k == line!!.size - 1) -1 + t else k + t, v)
                        x = v.x
                        y = v.y
                    } else {
                        x = cellX(pos.c)
                        y = cellY(pos.r)
                    }
                    if (group.size > 1) {
                        x += (i - (group.size - 1) / 2f) * 7f
                        y += if (i % 2 == 0) 2f else -2f
                    }
                }
                batch.draw(a.pawns[p.index], floor(x + 8), floor(y + 10))
            }
        }
    }

    private fun hop(frac: Float) = sin(frac * MathUtils.PI) * 4f

    private fun drawArrows() {
        val allowed = state.allowedInserts()
        val active = phase == Phase.PLACE
        val c = Vector2()
        for (ins in board.config.inserts) {
            arrowCenter(ins, c)
            val ok = ins in allowed
            val bob = if (active && ok) sin(time * 5f) * 1.5f else 0f
            val (rot, dx, dy) = when (ins.side) {
                Side.LEFT -> Triple(0f, bob, 0f)
                Side.RIGHT -> Triple(180f, -bob, 0f)
                Side.TOP -> Triple(270f, 0f, -bob)
                Side.BOTTOM -> Triple(90f, 0f, bob)
            }
            batch.color = when {
                !ok -> Color(0.3f, 0.3f, 0.35f, 0.6f)
                active -> Color.WHITE
                else -> Color(0.7f, 0.7f, 0.7f, 0.8f)
            }
            if (phase == Phase.AI_ROTATE && ins == aiPlan?.insert) batch.color = Color.WHITE
            // Pista: la flecha recomendada brilla (cuando la pieza ya está bien girada)
            val hinted = hints && active && ins == hintPlan?.insert && board.spare.mask == hintMask
            if (hinted) {
                val glow = 0.35f + 0.35f * sin(time * 8f)
                val tint = batch.color.cpy()
                ui.rect(floor(c.x - 11), floor(c.y - 11), 22f, 22f, Color(0.4f, 1f, 0.45f, glow))
                batch.color = tint
            }
            val size = if (hinted) 16f * (1.15f + 0.1f * sin(time * 8f)) else 16f
            batch.draw(a.arrow, floor(c.x - size / 2 + dx), floor(c.y - size / 2 + dy), size / 2, size / 2, size, size, 1f, 1f, rot)
        }
        batch.color = Color.WHITE
    }

    private fun drawHud() {
        val w = viewport.worldWidth
        val h = viewport.worldHeight
        val n = state.players.size
        val chipW = minOf(64f, (w - 8) / n)
        val startX = floor(w / 2 - chipW * n / 2)
        val chipY = h - 34
        for (p in state.players) {
            val x = startX + p.index * chipW
            val isCurrent = p === state.currentPlayer && phase != Phase.OVER
            ui.panel(x + 1, chipY, chipW - 2, 28f, Ui.PANEL, if (isCurrent) Assets.PLAYER_COLORS[p.index] else Ui.BORDER)
            batch.draw(a.pawns[p.index], x + 4, chipY + 6)
            val tx = x + 22 + (chipW - 24) / 2
            ui.textCentered("${p.collected}/${p.total}", tx, chipY + 20, Ui.GOLD)
            batch.draw(a.smallCoin, tx - 10, chipY + 4)
            ui.textCentered(p.coins.toString(), tx + 4, chipY + 8, Color.WHITE)
        }
        ui.textCentered(status, w / 2, chipY - 14, Color.WHITE)
    }

    private fun drawBottom() {
        val w = viewport.worldWidth
        // Pieza suelta, a tamaño doble
        val s = spareRect
        ui.textCentered(str["game.tile"], s.x + s.width / 2, s.y + s.height + 10, Ui.MUTED)
        ui.panel(s.x - 3, s.y - 3, s.width + 6, s.height + 6, Ui.SHADOW, if (phase == Phase.PLACE) Ui.GOLD else Ui.BORDER)
        if (phase != Phase.SLIDE) {
            val spare = board.spare
            batch.draw(a.tiles[spare.mask], s.x, s.y, s.width, s.height)
            if (spare.treasure >= 0) batch.draw(a.treasures[spare.treasure], s.x + 16, s.y + 16, 32f, 32f)
            when (spare.item) {
                Item.COIN -> batch.draw(a.coin, s.x + 16, s.y + 16, 32f, 32f)
                Item.EVENT -> batch.draw(a.event, s.x + 16, s.y + 16, 32f, 32f)
            }
        }
        if (phase == Phase.PLACE) {
            // Pista: si hay que girar la pieza, el texto parpadea en verde
            val rotateHint = hints && hintMask >= 0 && board.spare.mask != hintMask && sin(time * 8f) > 0
            ui.textCentered(str["game.tapRotate"], s.x + s.width / 2, s.y - 13, if (rotateHint) Color(0.4f, 1f, 0.45f, 1f) else Ui.MUTED)
        }

        // Metas de la etapa actual del humano
        val gx = floor(w / 2 + 16)
        val gy = s.y - 3
        val half = Ui.GOAL_PANEL / 2
        ui.textCentered(str.stage(human.stage), gx + half, s.y + s.height + 10, Ui.GOLD)
        ui.goalPanel(gx, gy, human.activeGoals, if (human.retiring) Ui.GOLD else Ui.BORDER, human.coins, state::costOf)
        if (human.retiring) {
            ui.textCentered(str["game.goHome"], gx + half, s.y - 13, Ui.GOLD)
        } else if (board.spare.treasure in human.activeGoals && phase != Phase.SLIDE) {
            ui.textCentered(str["game.onSpare"], w / 2, s.y - 29, Color.WHITE)
        }
    }

    private fun drawToast() {
        if (toastTime <= 0) return
        val w = viewport.worldWidth
        val alpha = (toastTime / 0.3f).coerceIn(0f, 1f)
        val tw = ui.textWidth(toast) + 16
        val y = boardY + BOARD_PX / 2 - 12
        ui.panel(floor(w / 2 - tw / 2), y, floor(tw), 22f, Color(0.1f, 0.12f, 0.18f, 0.92f * alpha), Color(Ui.GOLD.r, Ui.GOLD.g, Ui.GOLD.b, alpha))
        ui.textCentered(toast, w / 2, y + 11, Color(1f, 1f, 1f, alpha))
    }

    private fun drawGameOver() {
        val w = viewport.worldWidth
        val h = viewport.worldHeight
        ui.rect(0f, 0f, w, h, Color(0f, 0f, 0f, 0.7f))
        val winner = state.winner ?: return
        val won = isMe(winner)
        var y = h / 2 + 96
        val title = if (won) str["game.youWin"] else str.format("game.wins", nameOf(winner.index).uppercase())
        ui.textCentered(title, w / 2, y, if (won) Ui.GOLD else Assets.PLAYER_COLORS[winner.index], 2f)
        y -= 44
        batch.draw(a.pawns[winner.index], w / 2 - 16, y - 16, 32f, 32f)
        y -= 36

        if (level != null && won) {
            // Las estrellas aparecen una a una
            for (k in 0 until 3) {
                val appear = ((timer - 0.4f - k * 0.35f) / 0.25f).coerceIn(0f, 1f)
                val filled = k < starsWon && appear > 0f
                val size = 32f * if (filled) (1f + 0.3f * sin(appear * MathUtils.PI)) else 1f
                batch.draw(if (filled) a.star else a.starEmpty, w / 2 - 52 + k * 36f + 16 - size / 2, y - size / 2, size, size)
            }
            y -= 30
            ui.textCentered(str.format("game.round", state.round), w / 2 - 24, y, Color.WHITE)
            batch.draw(a.smallCoin, w / 2 + 22, y - 4)
            ui.textCentered(human.coins.toString(), w / 2 + 38, y, Color.WHITE)
            y -= 22
        } else if (!won && !hotSeat) {
            ui.textCentered(str["over.lost"], w / 2, y, Color.WHITE)
            y -= 22
        }

        if (timer > 0.8f) {
            var by = y - 24
            for (b in overButtons.all) {
                b.rect.set(floor(w / 2 - 70), by, 140f, 28f)
                by -= 34
            }
            overButtons.draw()
        }
    }

    /** Pasándose el móvil: pantalla que tapa el tablero hasta que el siguiente jugador toca. */
    private fun drawHandoff() {
        val w = viewport.worldWidth
        val h = viewport.worldHeight
        val p = state.currentPlayer
        ui.rect(0f, 0f, w, h, Ui.BG)
        ui.textCentered(str.format("mp.handoff", nameOf(p.index)), w / 2, h / 2 + 70, Assets.PLAYER_COLORS[p.index], 2f)
        val hop = kotlin.math.abs(sin(time * 4f)) * 6f
        batch.draw(a.pawns[p.index], w / 2 - 24, h / 2 - 10 + hop, 48f, 48f)
        ui.textWrapped(str["mp.handoffTap"], 20f, h / 2 - 40, w - 40, Color.WHITE, Align.center)
    }

    /** Botón MENÚ (abajo a la izquierda, abre la pausa) y botón de sonido (abajo a la derecha). */
    private fun drawCornerButtons() {
        if (level != null && phase != Phase.OVER) {
            val w = viewport.worldWidth
            val text = str.format("game.round", state.round)
            val tw = ui.textWidth(text)
            val x = floor(w / 2 - (tw + 24) / 2)
            ui.text(text, x, 19f, if (state.round <= level.roundsFor2) Color.WHITE else Ui.MUTED)
            batch.draw(a.smallStar, x + tw + 6, 9f)
            ui.text(level.roundsFor2.toString(), x + tw + 16, 19f, Ui.GOLD)
        }
        if (phase != Phase.OVER) {
            val label = str["game.menu"]
            pauseBtn.set(2f, 2f, maxOf(44f, floor(ui.textWidth(label) + 16)), 22f)
            ui.button(pauseBtn, label, false, a.slateButton)
        }
        a.slateButton[0].draw(batch, soundBtn.x, soundBtn.y, soundBtn.width, soundBtn.height)
        batch.draw(if (audio.enabled) a.soundOn else a.soundOff, soundBtn.x + 6, soundBtn.y + 5)
    }

    private fun drawPause() {
        val w = viewport.worldWidth
        val h = viewport.worldHeight
        ui.rect(0f, 0f, w, h, Color(0f, 0f, 0f, 0.7f))
        val pw = 180f
        val ph = 160f
        val px = floor(w / 2 - pw / 2)
        val py = floor(h / 2 - ph / 2)
        ui.panel(px, py, pw, ph)
        ui.textCentered(str["pause.title"], w / 2, py + ph - 24, Ui.GOLD, 2f)
        var by = py + ph - 64
        for (b in pauseButtons.all) {
            b.rect.set(px + 20, by, pw - 40, 28f)
            by -= 36
        }
        pauseButtons.draw()
    }

    companion object {
        const val BOARD_PX = 224f // Board.SIZE * TILE
        const val SLIDE_TIME = 0.35f
        const val STEP_TIME = 0.12f
        const val TOAST_TIME = 1.8f
        const val TOAST_MAX_WAIT = 4f
        const val SWAP_TIME = 0.8f
    }
}
