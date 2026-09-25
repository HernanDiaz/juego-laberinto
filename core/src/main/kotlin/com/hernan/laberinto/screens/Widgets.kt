package com.hernan.laberinto.screens

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.math.Rectangle
import com.hernan.laberinto.Assets
import com.hernan.laberinto.Ui
import kotlin.random.Random

/** Fondo de laberinto que se desplaza lentamente, oscurecido para que se lea el texto encima. */
class MazeBackground(seed: Int = 42) {
    private val rnd = Random(seed)
    private val tiles = IntArray(200) { rnd.nextInt(16) }
    private var time = 0f

    fun draw(a: Assets, ui: Ui, batch: SpriteBatch, w: Float, h: Float, delta: Float) {
        time += delta
        val scroll = (time * 6f) % Assets.TILE
        var i = 0
        var y = -Assets.TILE.toFloat() + scroll
        while (y < h) {
            var x = 0f
            while (x < w) {
                batch.draw(a.tiles[tiles[i++ % tiles.size]], x, y)
                x += Assets.TILE
            }
            y += Assets.TILE
        }
        ui.rect(0f, 0f, w, h, Color(0.05f, 0.06f, 0.09f, 0.72f))
    }
}

/** Fila de opción con flechas < > (idioma, número de rivales...). */
class OptionRow(val label: () -> String, val value: () -> String, val change: (Int) -> Unit) {
    private val minus = Rectangle()
    private val plus = Rectangle()

    fun draw(a: Assets, ui: Ui, batch: SpriteBatch, w: Float, y: Float) {
        ui.textCentered(label(), w / 2, y + 26, Color.WHITE)
        ui.panel(w / 2 - 44, y - 4, 88f, 20f)
        ui.textCentered(value(), w / 2, y + 6, Ui.GOLD)
        minus.set(w / 2 - 76, y - 8, 28f, 28f)
        plus.set(w / 2 + 48, y - 8, 28f, 28f)
        batch.draw(a.arrow, minus.x + 6, minus.y + 6, 8f, 8f, 16f, 16f, 1f, 1f, 180f)
        batch.draw(a.arrow, plus.x + 6, plus.y + 6)
    }

    /** Devuelve true si el toque ha cambiado la opción. */
    fun touch(x: Float, y: Float): Boolean {
        val step = when {
            minus.contains(x, y) -> -1
            plus.contains(x, y) -> 1
            else -> return false
        }
        change(step)
        return true
    }
}

/**
 * Ventana para comprar el juego completo. Se abre con [open]; mientras está visible recibe
 * los toques. Si la compra se completa muestra un agradecimiento y se cierra sola.
 */
class UpgradePanel(private val game: com.hernan.laberinto.LaberintoGame) {
    private val a = game.assets
    private val ui = game.ui
    private val s = game.strings
    private val buttons = com.hernan.laberinto.Buttons(ui, game.audio)
    private val buyBtn = buttons.add(a.greenButton, 1f, {
        game.priceLabel()?.let { s.format("shop.buy", it) } ?: s["shop.buyNoPrice"]
    }) { buy() }
    private val laterBtn = buttons.add(a.slateButton, 1f, { s["shop.later"] }) { close() }

    var visible = false
        private set
    private var thanksTime = 0f
    private var message = ""
    private var messageTime = 0f

    /** Se llama al cerrarse después de comprar (para refrescar la pantalla de debajo). */
    var onPurchased: (() -> Unit)? = null

    fun open() {
        visible = true
        thanksTime = 0f
        messageTime = 0f
        game.store.refresh()
    }

    fun close() {
        visible = false
    }

    private fun buy() {
        if (game.store.price == null && game.store !is com.hernan.laberinto.FakeStore) {
            message = s["shop.unavailable"]
            messageTime = 2.5f
            return
        }
        game.store.purchase()
    }

    fun down(x: Float, y: Float): Boolean {
        if (!visible) return false
        if (thanksTime <= 0f) buttons.down(x, y)
        return true
    }

    fun drag(x: Float, y: Float) {
        if (visible) buttons.drag(x, y)
    }

    fun up(x: Float, y: Float): Boolean {
        if (!visible) return false
        if (thanksTime <= 0f) buttons.up(x, y)
        return true
    }

    fun draw(batch: SpriteBatch, w: Float, h: Float, delta: Float) {
        if (!visible) return
        val store = game.store
        if (store.owned && thanksTime <= 0f) {
            thanksTime = 2f
            game.audio.play(game.audio.win)
        }
        if (thanksTime > 0f) {
            thanksTime -= delta
            if (thanksTime <= 0f) {
                visible = false
                onPurchased?.invoke()
                return
            }
        }
        if (messageTime > 0f) messageTime -= delta

        ui.rect(0f, 0f, w, h, Color(0f, 0f, 0f, 0.75f))
        val pw = 240f
        val ph = 220f
        val px = kotlin.math.floor(w / 2 - pw / 2)
        val py = kotlin.math.floor(h / 2 - ph / 2)
        ui.panel(px, py, pw, ph, Ui.PANEL, Ui.GOLD)
        var y = py + ph - 20
        ui.textCentered(s["shop.title"], w / 2, y, Ui.GOLD, 2f)
        y -= 26
        // Iconos de los niveles de pago
        val premium = game.levels.filter { it.premium }
        premium.forEachIndexed { k, level ->
            batch.draw(a.treasures[level.icon], w / 2 - premium.size * 10f + k * 20f + 2, y - 16)
        }
        y -= 30
        if (thanksTime > 0f) {
            ui.textWrapped(s["shop.thanks"], px + 12, y, pw - 24, Color.WHITE, com.badlogic.gdx.utils.Align.center)
            return
        }
        val text = if (store.pending) s["shop.pending"] else s["shop.text"]
        ui.textWrapped(text, px + 12, y, pw - 24, Color.WHITE, com.badlogic.gdx.utils.Align.center)
        buyBtn.rect.set(px + 14, py + 14, 118f, 30f)
        laterBtn.rect.set(px + pw - 98, py + 14, 84f, 30f)
        buttons.draw()
        if (messageTime > 0f) {
            ui.textCentered(message, w / 2, py - 14, Color.valueOf("ff8080"))
        }
    }
}
