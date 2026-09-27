package com.hernan.laberinto

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.utils.I18NBundle
import java.util.Locale

/**
 * Textos traducidos (assets/i18n/strings_*.properties; strings.properties es el inglés).
 * Usa el idioma del dispositivo si está disponible y recuerda el que elija el jugador.
 *
 * Cada idioma indica su fuente bitmap. Para el hindi, además, cada grupo de letras (akshara)
 * se sustituye por el glyph ya combinado que generó tools/GenerarFuenteHindi.java.
 */
class Strings {
    private val prefs = Gdx.app.getPreferences("laberinto")
    private lateinit var bundle: I18NBundle
    private var clusters: Map<String, Char> = emptyMap()
    private var maxCluster = 0

    /** Se llama al cambiar de idioma (para cambiar la fuente). */
    var onChange: (() -> Unit)? = null

    var language: String = prefs.getString("lang", "").takeIf { it in LANGUAGES } ?: deviceLanguage()
        private set

    init {
        I18NBundle.setSimpleFormatter(true)
        load()
    }

    private fun deviceLanguage(): String {
        val lang = when (val l = Locale.getDefault().language) {
            "in" -> "id" // código antiguo de Java/Android para indonesio
            else -> l
        }
        return lang.takeIf { it in LANGUAGES } ?: "en"
    }

    private fun load() {
        // Se carga el archivo del idioma directamente (Locale.ROOT) para evitar que libGDX
        // caiga en el idioma del sistema o en códigos antiguos como "in" para indonesio.
        val base = if (language == "en") "i18n/strings" else "i18n/strings_$language"
        bundle = I18NBundle.createBundle(Gdx.files.internal(base), Locale.ROOT, "UTF-8")

        val map = Gdx.files.internal("font_$language.map")
        clusters = if (map.exists()) {
            map.readString("UTF-8").lineSequence().filter { it.isNotBlank() }.associate { line ->
                val (hex, text) = line.split('\t', limit = 2)
                text to hex.toInt(16).toChar()
            }
        } else {
            emptyMap()
        }
        maxCluster = clusters.keys.maxOfOrNull { it.length } ?: 0
    }

    /** Cambia al idioma siguiente (o anterior) de la lista y lo guarda. */
    fun cycle(step: Int) {
        val i = LANGUAGES.indexOf(language)
        language = LANGUAGES[(i + step + LANGUAGES.size) % LANGUAGES.size]
        prefs.putString("lang", language).flush()
        load()
        onChange?.invoke()
    }

    /** Fuente bitmap de este idioma. */
    val fontFile get() = if (Gdx.files.internal("font_$language.fnt").exists()) "font_$language.fnt" else "font.fnt"

    /** Sustituye los aksharas por sus glyphs (solo hindi). Es idempotente. */
    private fun shape(s: String): String {
        if (clusters.isEmpty()) return s
        val out = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            var matched = false
            for (len in minOf(maxCluster, s.length - i) downTo 1) {
                val g = clusters[s.substring(i, i + len)] ?: continue
                out.append(g)
                i += len
                matched = true
                break
            }
            if (!matched) out.append(s[i++])
        }
        return out.toString()
    }

    operator fun get(key: String): String = shape(bundle.get(key))
    fun format(key: String, vararg args: Any): String = shape(bundle.format(key, *args))

    fun treasure(i: Int) = get("treasure.$i")
    fun player(i: Int) = get("player.$i")
    fun stage(i: Int) = get("stage.$i")

    /** Título del juego; puede tener varias líneas. */
    val titleLines get() = get("app.title").split('\n')
    val languageName get() = shape(LANGUAGE_NAMES.getValue(language))

    companion object {
        val LANGUAGES = listOf("es", "en", "fr", "de", "it", "pt", "zh", "ja", "ru", "id", "hi")
        val LANGUAGE_NAMES = mapOf(
            "es" to "Español", "en" to "English", "fr" to "Français",
            "de" to "Deutsch", "it" to "Italiano", "pt" to "Português",
            "zh" to "简体中文", "ja" to "日本語", "ru" to "Русский",
            "id" to "Indonesia", "hi" to "हिन्दी",
        )
    }
}
