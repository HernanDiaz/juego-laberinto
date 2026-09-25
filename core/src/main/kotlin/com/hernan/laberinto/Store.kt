package com.hernan.laberinto

import com.badlogic.gdx.Gdx

/**
 * Compra única "juego completo" (niveles 4 a 8 y partida libre con más rivales).
 * En Android la gestiona Google Play (PlayStore, en el módulo android); en escritorio es simulada.
 */
interface Store {
    /** ¿Tiene el juego completo? */
    val owned: Boolean

    /** Compra hecha pero pendiente de pago (p. ej. pago en efectivo en una tienda). */
    val pending: Boolean

    /** Precio con formato local ("0,99 €"), o null si todavía no se sabe o no hay tienda. */
    val price: String?

    /** El mismo precio con el código ISO ("0.99 EUR"), por si la fuente no tiene el símbolo. */
    val priceFallback: String? get() = price

    /** Se llama en el hilo del juego cuando cambia algo (compra hecha, precio recibido...). */
    var onChange: (() -> Unit)?

    /** Abre la compra de Google Play (o la simulada). */
    fun purchase()

    /** Vuelve a consultar las compras (al volver a la app). */
    fun refresh() {}

    fun dispose() {}

    companion object {
        /** Identificador del producto en Play Console. */
        const val PRODUCT_ID = "juego_completo"
    }
}

/** Tienda simulada para escritorio y pruebas: la compra se completa al instante. */
class FakeStore : Store {
    private val prefs = Gdx.app.getPreferences("laberinto")
    override var owned = prefs.getBoolean("fakeStore.owned", false)
        private set
    override val pending = false
    override val price = "0,99 €"
    override val priceFallback = "0.99 EUR"
    override var onChange: (() -> Unit)? = null

    override fun purchase() {
        setOwned(true)
    }

    /** Solo para pruebas: quita o da el juego completo. */
    fun setOwned(value: Boolean) {
        owned = value
        prefs.putBoolean("fakeStore.owned", value).flush()
        Gdx.app.postRunnable { onChange?.invoke() }
    }
}
