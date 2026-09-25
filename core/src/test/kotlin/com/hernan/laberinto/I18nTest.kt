package com.hernan.laberinto

import java.io.File
import java.io.InputStreamReader
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class I18nTest {
    private val assets = File("../assets")
    private val dir = File(assets, "i18n")

    private fun fileOf(lang: String) = if (lang == "en") "strings.properties" else "strings_$lang.properties"

    private fun load(name: String) = Properties().apply {
        InputStreamReader(File(dir, name).inputStream(), Charsets.UTF_8).use { load(it) }
    }

    private fun values(lang: String): List<String> {
        val p = load(fileOf(lang))
        val players = (0..3).map { p.getProperty("player.$it") }
        // game.wins pone el nombre en mayúsculas
        return p.stringPropertyNames().map { p.getProperty(it) } + players.map { it.uppercase() } +
            Strings.LANGUAGE_NAMES.getValue(lang)
    }

    /** Códigos de carácter que contiene un .fnt */
    private fun glyphs(fnt: String): Set<Int> =
        File(assets, fnt).readLines().filter { it.startsWith("char id=") }
            .map { it.substringAfter("char id=").substringBefore(' ').toInt() }.toSet()

    @Test
    fun todosLosIdiomasTienenLasMismasClaves() {
        val base = load("strings.properties").stringPropertyNames()
        for (lang in Strings.LANGUAGES.filter { it != "en" }) {
            val keys = load(fileOf(lang)).stringPropertyNames()
            assertEquals(emptySet(), base - keys, "Faltan claves en $lang")
            assertEquals(emptySet(), keys - base, "Claves sobrantes en $lang")
        }
    }

    @Test
    fun textosCortosCabenEnPantalla() {
        // 270 px de ancho y 6 px por carácter latino: las líneas de estado no pueden pasar de ~42 caracteres
        val short = listOf("game.place", "game.move", "game.noPath", "game.undo", "game.onSpare", "game.tapMenu")
        for (lang in Strings.LANGUAGES) {
            val p = load(fileOf(lang))
            for (k in short) assertTrue(p.getProperty(k).length <= 42, "$lang: $k es demasiado largo")
        }
    }

    @Test
    fun cadaFuenteTieneTodosLosCaracteresDeSuIdioma() {
        for (lang in Strings.LANGUAGES) {
            val fnt = if (File(assets, "font_$lang.fnt").exists()) "font_$lang.fnt" else "font.fnt"
            val available = glyphs(fnt)
            val clusters = File(assets, "font_$lang.map").takeIf { it.exists() }
                ?.readLines()?.filter { it.isNotBlank() }?.map { it.substringAfter('\t') }
                ?.sortedByDescending { it.length }.orEmpty()
            for (text in values(lang)) {
                // En hindi se quitan primero los aksharas que tienen glyph propio
                var rest = text
                for (c in clusters) rest = rest.replace(c, "")
                val missing = rest.codePoints().toArray().filter { it != ' '.code && it !in available }
                    .map { String(Character.toChars(it)) }
                assertTrue(missing.isEmpty(), "$lang ($fnt): faltan $missing en \"$text\". Ejecuta tools/generar_fuentes.py")
            }
        }
    }
}
