package com.hernan.laberinto

import com.hernan.laberinto.net.ClientSession
import com.hernan.laberinto.net.HostSession
import com.hernan.laberinto.net.NetEvent
import com.hernan.laberinto.net.NetSession
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/** Anfitrión e invitados reales por la red local (127.0.0.1). */
class NetTest {
    private val sessions = ArrayList<NetSession>()

    @AfterTest
    fun cerrar() = sessions.forEach { it.close() }

    private fun <T : NetEvent> esperar(s: NetSession, tipo: Class<T>, ms: Long = 5000): T {
        val fin = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < fin) {
            val e = s.events.poll()
            if (e != null && tipo.isInstance(e)) return tipo.cast(e)
            if (e == null) Thread.sleep(10)
        }
        fail("No llegó ${tipo.simpleName}")
    }

    private fun mensaje(s: NetSession, empieza: String): NetEvent.Message {
        val fin = System.currentTimeMillis() + 5000
        while (System.currentTimeMillis() < fin) {
            val e = s.events.poll()
            if (e is NetEvent.Message && e.text.startsWith(empieza)) return e
            if (e == null) Thread.sleep(10)
        }
        fail("No llegó el mensaje $empieza")
    }

    @Test
    fun salaPartidaYJugadas() {
        val host = HostSession().also { sessions.add(it) }
        val a = ClientSession("127.0.0.1").also { sessions.add(it) }
        esperar(a, NetEvent.Connected::class.java)
        val joinedA = esperar(host, NetEvent.Joined::class.java)
        val b = ClientSession("127.0.0.1").also { sessions.add(it) }
        esperar(b, NetEvent.Connected::class.java)
        val joinedB = esperar(host, NetEvent.Joined::class.java)
        assertEquals(3, host.players)
        assertTrue(joinedA.slot != joinedB.slot)

        // El anfitrión empieza: cada invitado recibe su puesto
        host.started = true
        host.slots.forEachIndexed { k, slot -> host.sendTo(slot, "START|42|2|3|${k + 1}") }
        assertEquals("START|42|2|3|1", mensaje(a, "START").text)
        assertEquals("START|42|2|3|2", mensaje(b, "START").text)

        // Una jugada de A llega al anfitrión, que la reenvía a B (pero no de vuelta a A)
        mensaje(host, "HELLO") // saludos de los invitados
        a.send("MOVE|0|1|2|LEFT|1|3|4")
        val m = mensaje(host, "MOVE")
        host.relay(m.from, m.text)
        assertEquals("MOVE|0|1|2|LEFT|1|3|4", mensaje(b, "MOVE").text)
        Thread.sleep(200)
        assertTrue(a.events.none { it is NetEvent.Message && it.text.startsWith("MOVE") })

        // Una vez empezada la partida no entra nadie más
        val tarde = ClientSession("127.0.0.1").also { sessions.add(it) }
        esperar(tarde, NetEvent.Lost::class.java)

        // Si un invitado se va, el anfitrión se entera
        b.close()
        esperar(host, NetEvent.Left::class.java)
        // Si el anfitrión cierra, los invitados se enteran
        host.close()
        esperar(a, NetEvent.Lost::class.java)
    }
}
