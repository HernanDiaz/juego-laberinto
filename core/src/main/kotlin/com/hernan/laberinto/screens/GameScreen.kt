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
import kotlin.math.floor
import kotlin.math.sin

/** Partida. Con [level] es un nivel del mapa (cuenta estrellas); sin él, una partida libre. */
class GameScreen(
    private val game: LaberintoGame,
    private val setup: GameSetup,
    private val level: Level? = null,
) : ScreenAdapter() {
    private val a = game.assets
    private val ui = game.ui
    private val batch = game.batch
    private val viewport = ExtendViewport(MenuScreen.WORLD_W, MenuScreen.WORLD_H)

    private val state = GameState(setup)
    private val board get() = state.board
    private val human = state.players[0]
    private val audio = game.audio
    private val str = game.strings

    private enum class Phase { PLACE, SLIDE, MOVE, WALK, SWAP, AI_THINK, AI_ROTATE, OVER }

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
    private val pendingToasts = ArrayDeque<String>()

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
        add(a.slateButton, 1f, { str[if (level != null) "over.map" else "pause.menu"] }) { leave() }
    }

    // Final de partida: estrellas conseguidas y botones
    private var starsWon = 0
    private val overButtons = Buttons(ui, audio)
    private val nextLevel: Level? get() = level?.let { game.levels.getOrNull(it.index + 1) }

    private fun restart() = game.switchTo(GameScreen(game, setup, level))

    /** Vuelve al mapa (niveles) o al menú (partida libre). */
    private fun leave() = game.switchTo(if (level != null) LevelMapScreen(game) else MenuScreen(game))

    private fun buildOverButtons() {
        val won = state.winner === human
        if (level != null) {
            val next = nextLevel
            if (won && next != null) {
                overButtons.add(a.greenButton, 1f, { str["over.next"] }) {
                    // Si el siguiente es del juego completo y no lo tiene, se ofrece la compra en el mapa
                    game.switchTo(if (game.canPlay(next)) GameScreen(game, next.setup, next) else LevelMapScreen(game, openUpgrade = true))
                }
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
                    if (phase == Phase.OVER) {
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

    /** La app pasa a segundo plano: pausamos la partida. */
    override fun pause() {
        if (phase != Phase.OVER) paused = true
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
        if (p.isAi) {
            phase = Phase.AI_THINK
            status = str.format("game.turnAi", str.player(p.index), str["ai.${p.personality!!.name}"])
        } else {
            phase = Phase.PLACE
            status = str["game.place"]
        }
    }

    private fun rotateSpare() {
        board.spare.rotateCw()
        audio.play(audio.rotate)
    }

    private fun beginSlide(ins: Insert) {
        audio.play(audio.push, 0.8f)
        slide = ins
        phase = Phase.SLIDE
        timer = 0f
    }

    private fun afterPush() {
        val p = state.currentPlayer
        reach = board.reachable(p.pos)
        slide = null
        if (p.isAi) {
            beginWalk(aiPlan!!.dest)
        } else {
            phase = Phase.MOVE
            timer = 0f
            status = if (reach.size > 1) str["game.move"] else str["game.noPath"]
        }
    }

    private fun beginWalk(dest: Pos) {
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
                if (p === human) str.format("game.youFound", str.treasure(r.goal))
                else str.format("game.found", str.player(p.index), str.treasure(r.goal)),
            )
            audio.play(if (p === human) audio.treasure else audio.rival)
            if (r.reward > 0 && p === human) showToast(str.format("game.reward", r.reward))
            if (p === human && p.stage != stageBefore) {
                showToast(if (p.retiring) str["game.goHome"] else str.format("game.newStage", str.stage(p.stage)))
            }
        }
        if (r.blockedGoal >= 0 && p === human) {
            showToast(str.format("game.needCoins", r.missingCoins, str.treasure(r.blockedGoal)))
            audio.play(audio.denied)
        }
        r.event?.let { e ->
            audio.play(audio.event)
            if (e == EventType.SWAP) {
                // Se avisa al momento (sin esperar a otros avisos) y se anima el cambio de sitio
                val other = r.swappedWith!!
                showToastNow(str.format("event.SWAP", str.player(p.index), str.player(other.index)))
                swapA = p
                swapB = other
                swapFromA = walkPath.last()
                swapFromB = p.pos
                phase = Phase.SWAP
                timer = 0f
                return
            }
            showToast(str.format("event.${e.name}", str.player(p.index), kotlin.math.abs(r.eventCoins)))
        }
        endTurn()
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
            audio.play(if (winner === human) audio.win else audio.lose)
            status = ""
            if (level != null && winner === human) {
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
            pendingToasts.addLast(msg)
            return
        }
        toast = msg
        toastTime = TOAST_TIME
    }

    // ------------------------------------------------------------------ lógica

    private fun update(delta: Float) {
        timer += delta
        time += delta
        if (toastTime > 0) {
            toastTime -= delta
            if (toastTime <= 0 && pendingToasts.isNotEmpty()) {
                toast = pendingToasts.removeFirst()
                toastTime = TOAST_TIME
            }
        }
        when (phase) {
            Phase.AI_THINK -> if (timer > 0.6f) {
                aiPlan = Ai.plan(state)
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
        if (!paused) update(delta)
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
            batch.draw(a.arrow, floor(c.x - 8 + dx), floor(c.y - 8 + dy), 8f, 8f, 16f, 16f, 1f, 1f, rot)
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
        if (phase == Phase.PLACE) ui.textCentered(str["game.tapRotate"], s.x + s.width / 2, s.y - 13, Ui.MUTED)

        // Metas de la etapa actual del humano
        val gx = floor(w / 2 + 16)
        val gy = s.y - 3
        val half = Ui.GOAL_PANEL / 2
        ui.textCentered(str.stage(human.stage), gx + half, s.y + s.height + 10, Ui.GOLD)
        ui.goalPanel(gx, gy, human.activeGoals, if (human.retiring) Ui.GOLD else Ui.BORDER, human.coins)
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
        val won = winner === human
        var y = h / 2 + 96
        val title = if (won) str["game.youWin"] else str.format("game.wins", str.player(winner.index).uppercase())
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
        } else if (!won) {
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
        const val SWAP_TIME = 0.8f
    }
}
