package com.hernan.laberinto

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.NinePatch
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.Disposable
import com.hernan.laberinto.model.Board
import com.hernan.laberinto.model.Goals
import java.util.Properties
import kotlin.math.roundToInt

/** Sprites generados por tools/generar_sprites.py */
class Assets : Disposable {
    private val textures = ArrayList<Texture>()

    private fun tex(name: String) = Texture(Gdx.files.internal(name)).also { textures.add(it) }

    private val tilesTex = tex("tiles.png")
    private val treasuresTex = tex("treasures.png")
    private val pawnsTex = tex("pawns.png")
    private val homesTex = tex("homes.png")
    private val uiTex = tex("ui.png")

    /** Loseta por máscara de conexiones (N=1, E=2, S=4, W=8). */
    val tiles = Array(16) { TextureRegion(tilesTex, it * TILE, 0, TILE, TILE) }
    val treasures = Array(Board.TREASURES) { TextureRegion(treasuresTex, it * 16, 0, 16, 16) }
    val pawns = Array(4) { TextureRegion(pawnsTex, it * 16, 0, 16, 16) }
    val homes = Array(4) { TextureRegion(homesTex, it * TILE, 0, TILE, TILE) }
    val arrow = TextureRegion(uiTex, 0, 0, 16, 16)
    val cursor = TextureRegion(uiTex, 16, 0, 32, 32)
    val pixel = TextureRegion(uiTex, 48, 0, 1, 1)
    val card = TextureRegion(tex("card.png"))

    private val buttonsTex = tex("buttons.png")
    private fun button(row: Int, pressed: Int) =
        NinePatch(TextureRegion(buttonsTex, pressed * 16, row * 18, 16, 18), 4, 4, 4, 5)

    /** Botones con relieve: [normal, pulsado]. */
    val greenButton = arrayOf(button(0, 0), button(0, 1))
    val slateButton = arrayOf(button(1, 0), button(1, 1))

    private val iconsTex = tex("icons.png")
    val soundOn = TextureRegion(iconsTex, 0, 0, 16, 16)
    val soundOff = TextureRegion(iconsTex, 16, 0, 16, 16)
    val pause = TextureRegion(iconsTex, 32, 0, 16, 16)

    private val itemsTex = tex("items.png")
    val coin = TextureRegion(itemsTex, 0, 0, 16, 16)
    val event = TextureRegion(itemsTex, 16, 0, 16, 16)
    val smallCoin = TextureRegion(itemsTex, 32, 0, 8, 8)

    private val mapTex = tex("map.png")
    val star = TextureRegion(mapTex, 0, 0, 16, 16)
    val starEmpty = TextureRegion(mapTex, 16, 0, 16, 16)
    val smallStar = TextureRegion(mapTex, 32, 0, 8, 8)
    val smallStarEmpty = TextureRegion(mapTex, 40, 0, 8, 8)
    val lock = TextureRegion(mapTex, 48, 0, 16, 16)
    val nodeOpen = TextureRegion(mapTex, 0, 16, 28, 28)
    val nodeLocked = TextureRegion(mapTex, 28, 16, 28, 28)

    /** Fuente bitmap y el centro visual de sus mayúsculas (px desde el borde superior de la línea). */
    class FontInfo(val font: BitmapFont, val capCenter: Float, val breakAnywhere: Boolean)

    private val fonts = HashMap<String, FontInfo>()

    /** Carga (una vez) una fuente generada por tools/generar_sprites.py o tools/generar_fuentes.py. */
    fun font(file: String): FontInfo = fonts.getOrPut(file) {
        val font = BitmapFont(Gdx.files.internal(file)).apply { setUseIntegerPositions(true) }
        val meta = Properties()
        val metaFile = Gdx.files.internal(file.removeSuffix(".fnt") + ".properties")
        if (metaFile.exists()) metaFile.reader("UTF-8").use { meta.load(it) }
        FontInfo(font, meta.getProperty("capCenter", "7.5").toFloat(), meta.getProperty("breakAnywhere") == "true")
    }

    override fun dispose() {
        textures.forEach { it.dispose() }
        fonts.values.forEach { it.font.dispose() }
    }

    companion object {
        const val TILE = 32


        val PLAYER_COLORS = listOf(
            Color.valueOf("4e8cf4"), Color.valueOf("e64040"),
            Color.valueOf("5cc858"), Color.valueOf("face40"),
        )
    }
}

/** Utilidades de dibujo pixel art sobre un SpriteBatch. */
class Ui(private val a: Assets, private val batch: SpriteBatch) {
    private val layout = GlyphLayout()
    private var font: BitmapFont = a.font("font.fnt").font
    private var capCenter = 7.5f
    private var breakAnywhere = false

    /** ¿Tiene la fuente actual todos los caracteres de [text]? */
    fun canRender(text: String) = text.all { it == ' ' || font.data.hasGlyph(it) }

    /** Altura de línea de la fuente actual (a escala 1). */
    val lineHeight get() = font.lineHeight

    /** Cambia la fuente usada para todos los textos (depende del idioma). */
    fun useFont(info: Assets.FontInfo) {
        font = info.font
        capCenter = info.capCenter
        breakAnywhere = info.breakAnywhere
    }

    fun rect(x: Float, y: Float, w: Float, h: Float, color: Color) {
        batch.color = color
        batch.draw(a.pixel, x, y, w, h)
        batch.color = Color.WHITE
    }

    fun panel(x: Float, y: Float, w: Float, h: Float, fill: Color = PANEL, border: Color = BORDER) {
        rect(x, y, w, h, border)
        rect(x + 1, y + 1, w - 2, h - 2, fill)
    }

    fun textWidth(s: String, scale: Float = 1f): Float {
        font.data.setScale(scale)
        layout.setText(font, s)
        font.data.setScale(1f)
        return layout.width
    }

    /** Dibuja texto con sombra. [y] es la parte superior del texto. */
    fun text(s: String, x: Float, y: Float, color: Color = Color.WHITE, scale: Float = 1f, align: Int = Align.left) {
        val f = font
        f.data.setScale(scale)
        layout.setText(f, s)
        val dx = when (align) {
            Align.center -> -layout.width / 2
            Align.right -> -layout.width
            else -> 0f
        }
        val px = (x + dx).roundToInt().toFloat()
        val py = y.roundToInt().toFloat()
        f.color = SHADOW
        f.draw(batch, s, px + scale, py - scale)
        f.color = color
        f.draw(batch, s, px, py)
        f.color = Color.WHITE
        f.data.setScale(1f)
    }

    /** Texto con ajuste de línea dentro de [width]. [y] es la parte superior. Devuelve la altura usada. */
    fun textWrapped(s: String, x: Float, y: Float, width: Float, color: Color = Color.WHITE, align: Int = Align.center): Float {
        if (breakAnywhere) return textWrappedCjk(s, x, y, width, color, align)
        val f = font
        layout.setText(f, s, color, width, align, true)
        f.color = SHADOW
        f.draw(batch, s, x + 1, y - 1, width, align, true)
        f.color = color
        f.draw(batch, s, x, y, width, align, true)
        f.color = Color.WHITE
        return layout.height
    }

    /**
     * Chino y japonés no separan palabras con espacios: se parte en cualquier carácter,
     * pero sin empezar una línea con un signo de cierre (regla "kinsoku").
     */
    private fun textWrappedCjk(s: String, x: Float, y: Float, width: Float, color: Color, align: Int): Float {
        val lines = ArrayList<String>()
        val cur = StringBuilder()
        for (ch in s) {
            if (cur.isNotEmpty() && ch !in NO_LINE_START && textWidth(cur.toString() + ch) > width) {
                lines.add(cur.toString())
                cur.clear()
            }
            cur.append(ch)
        }
        if (cur.isNotEmpty()) lines.add(cur.toString())
        val lh = font.lineHeight
        lines.forEachIndexed { i, line ->
            val lx = when (align) {
                Align.center -> x + width / 2
                Align.right -> x + width
                else -> x
            }
            text(line, lx, y - i * lh, color, 1f, align)
        }
        return lines.size * lh
    }

    /** Texto centrado vertical y horizontalmente en (cx, cy), tomando como referencia las mayúsculas. */
    fun textCentered(s: String, cx: Float, cy: Float, color: Color = Color.WHITE, scale: Float = 1f) {
        text(s, cx, cy + capCenter * scale, color, scale, Align.center)
    }

    /**
     * Panel de 70x70 con las metas de la etapa actual (iconos a tamaño doble).
     * Sin metas pendientes muestra la casilla de casa (toca jubilarse).
     */
    fun goalPanel(
        x: Float,
        y: Float,
        goals: List<Int>,
        border: Color = GOLD,
        coins: Int = Int.MAX_VALUE,
        costOf: (Int) -> Int = { Goals.COST[it] },
    ) {
        panel(x, y, GOAL_PANEL, GOAL_PANEL, SHADOW, border)
        val slots = when (goals.size) {
            0, 1 -> listOf(19f to 19f)
            2 -> listOf(3f to 19f, 35f to 19f)
            else -> listOf(3f to 35f, 35f to 35f, 3f to 3f, 35f to 3f)
        }
        if (goals.isEmpty()) {
            batch.draw(a.homes[0], x + 3, y + 3, 64f, 64f)
            return
        }
        goals.take(slots.size).forEachIndexed { i, g ->
            val (dx, dy) = slots[i]
            batch.draw(a.treasures[g], x + dx, y + dy, 32f, 32f)
            // Precio: moneda pequeña + número (en rojo si no te llega el dinero)
            val cost = costOf(g)
            if (cost > 0) {
                batch.draw(a.smallCoin, x + dx + 20, y + dy)
                text(cost.toString(), x + dx + 30, y + dy + 12, if (cost > coins) Color.valueOf("ff6060") else Color.WHITE)
            }
        }
    }

    /** Botón pixel art con relieve; el texto baja con el botón al pulsarlo. */
    fun button(r: Rectangle, label: String, pressed: Boolean, style: Array<NinePatch> = a.slateButton, scale: Float = 1f) {
        style[if (pressed) 1 else 0].draw(batch, r.x, r.y, r.width, r.height)
        // Cara del botón: 1 px de borde arriba (3 si está pulsado) y 4 px abajo (2 si está pulsado)
        val faceCenter = r.y + (r.height - 5) / 2 + if (pressed) 2 else 4
        textCentered(label, r.x + r.width / 2, faceCenter.roundToInt().toFloat(), Color.WHITE, scale)
    }

    companion object {
        val BG = Color.valueOf("141820")
        val PANEL = Color.valueOf("262b3a")
        val BORDER = Color.valueOf("5a6488")
        val SHADOW = Color.valueOf("0a0c12")
        val GOLD = Color.valueOf("facc40")
        val MUTED = Color.valueOf("9aa2c0")
        const val GOAL_PANEL = 70f
        private const val NO_LINE_START = "。、，．・：；！？）」』】〉》”’ーぁぃぅぇぉっゃゅょァィゥェォッャュョ…,.!?:;)"

    }
}
