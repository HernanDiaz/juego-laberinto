package com.hernan.laberinto.net

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Partidas por Wi-Fi local, como en los otros juegos: un móvil crea la partida (anfitrión),
 * la anuncia por la red y los demás se unen (buscándola o por IP). Hasta 4 jugadores.
 *
 * Cada móvil simula la partida entera: la partida es determinista (misma semilla y mismas
 * jugadas = mismo resultado), así que solo viajan las jugadas. El anfitrión reenvía la jugada
 * de cada invitado a los demás.
 *
 * Mensajes: una línea de texto por mensaje, campos separados por '|':
 *   HELLO|versión                          invitado → anfitrión, al conectar
 *   LOBBY|jugadores                        anfitrión → invitados, al cambiar la sala
 *   START|semilla|metas|jugadores|puesto   anfitrión → cada invitado
 *   MOVE|turno|jugador|giros|lado|línea|fila|col   quien juega → anfitrión → resto
 *   LEFT|puesto                            anfitrión → invitados: alguien se fue
 *
 * Los hilos de red solo dejan eventos en [events]; la pantalla los procesa en su hilo.
 */
object Net {
    const val GAME_PORT = 45144
    const val DISCOVERY_PORT = 45143
    const val TAG = "VV2026"
    const val VERSION = 1
    const val MAX_PLAYERS = 4

    /**
     * IPv4 de esta máquina en la red local (para mostrarla y unirse por IP), la más probable
     * primero: se descartan adaptadores virtuales (WSL, VirtualBox, Docker...) y se prefieren
     * las direcciones típicas de una Wi-Fi doméstica (192.168.x.x, luego 10.x.x.x). Algunas
     * redes (universidades, empresas) usan direcciones públicas: también valen, al final.
     */
    fun localIps(): List<String> = runCatching {
        val virtual = Regex("(?i)vethernet|virtual|vmware|vbox|docker|wsl|hyper-v|tap|tun")
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback && !it.isVirtual && !virtual.containsMatchIn(it.displayName ?: "") }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .filter { !it.isLinkLocalAddress && !it.isLoopbackAddress }
            .map { it.hostAddress }
            .sortedBy { ip ->
                when {
                    ip.startsWith("192.168.") -> 0
                    ip.startsWith("10.") -> 1
                    ip.matches(Regex("""172\.(1[6-9]|2\d|3[01])\..*""")) -> 2
                    else -> 3
                }
            }
    }.getOrDefault(emptyList())

    /** Direcciones de difusión de cada red (más fiables en Android que 255.255.255.255). */
    internal fun broadcastAddresses(): List<InetAddress> {
        val list = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.interfaceAddresses }
                .mapNotNull { it.broadcast }
        }.getOrDefault(emptyList())
        return list + InetAddress.getByName("255.255.255.255")
    }
}

/** Lo que llega de la red, para procesarlo en el hilo del juego. */
sealed class NetEvent {
    /** Un mensaje de texto. En el anfitrión, [from] es el puesto del invitado que lo envió. */
    data class Message(val from: Int, val text: String) : NetEvent()

    /** (Anfitrión) se ha unido un invitado. */
    data class Joined(val slot: Int) : NetEvent()

    /** (Anfitrión) se ha ido un invitado. */
    data class Left(val slot: Int) : NetEvent()

    /** (Invitado) conectado con el anfitrión. */
    object Connected : NetEvent()

    /** (Invitado) no se pudo conectar o se perdió la conexión con el anfitrión. */
    data class Lost(val beforeConnecting: Boolean) : NetEvent()
}

abstract class NetSession {
    val events = ConcurrentLinkedQueue<NetEvent>()
    abstract val isHost: Boolean

    /** Envía una línea: el anfitrión a todos los invitados; un invitado, al anfitrión. */
    abstract fun send(line: String)
    abstract fun close()
}

/** Anfitrión: acepta invitados, los anuncia por la red y reenvía mensajes. */
class HostSession : NetSession() {
    override val isHost = true

    private class Conn(val slot: Int, val socket: Socket, val out: PrintWriter)

    private val server = ServerSocket().apply {
        reuseAddress = true
        bind(InetSocketAddress(Net.GAME_PORT))
    }
    private val conns = CopyOnWriteArrayList<Conn>()

    @Volatile private var open = true

    /** Una vez empezada la partida no entra nadie más y se deja de anunciar. */
    @Volatile var started = false

    val players get() = 1 + conns.size
    val slots: List<Int> get() = conns.map { it.slot }.sorted()

    init {
        thread("acepta") {
            while (open) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                if (started || players >= Net.MAX_PLAYERS) {
                    runCatching { socket.close() }
                    continue
                }
                val slot = (1 until Net.MAX_PLAYERS).first { s -> conns.none { it.slot == s } }
                socket.tcpNoDelay = true
                val conn = Conn(slot, socket, PrintWriter(socket.getOutputStream(), true))
                conns.add(conn)
                events.add(NetEvent.Joined(slot))
                thread("lee-$slot") { readLoop(conn) }
            }
        }
        thread("anuncia") {
            val udp = DatagramSocket().apply { broadcast = true }
            while (open) {
                if (!started) {
                    val data = "${Net.TAG}|$players|${Net.MAX_PLAYERS}".toByteArray()
                    for (addr in Net.broadcastAddresses()) {
                        runCatching { udp.send(DatagramPacket(data, data.size, addr, Net.DISCOVERY_PORT)) }
                    }
                }
                Thread.sleep(1000)
            }
            udp.close()
        }
    }

    private fun readLoop(conn: Conn) {
        runCatching {
            val reader = BufferedReader(InputStreamReader(conn.socket.getInputStream(), Charsets.UTF_8))
            while (open) {
                val line = reader.readLine() ?: break
                events.add(NetEvent.Message(conn.slot, line))
            }
        }
        if (conns.remove(conn)) {
            runCatching { conn.socket.close() }
            if (open) events.add(NetEvent.Left(conn.slot))
        }
    }

    override fun send(line: String) {
        for (c in conns) c.out.println(line)
    }

    fun sendTo(slot: Int, line: String) {
        conns.firstOrNull { it.slot == slot }?.out?.println(line)
    }

    /** Reenvía a todos menos a quien lo envió. */
    fun relay(from: Int, line: String) {
        for (c in conns) if (c.slot != from) c.out.println(line)
    }

    override fun close() {
        open = false
        runCatching { server.close() }
        for (c in conns) runCatching { c.socket.close() }
        conns.clear()
    }
}

/** Invitado: se conecta al anfitrión de [host]. */
class ClientSession(host: String) : NetSession() {
    override val isHost = false

    @Volatile private var open = true
    @Volatile private var out: PrintWriter? = null
    private var socket: Socket? = null

    init {
        thread("conecta") {
            val s = Socket()
            val ok = runCatching { s.connect(InetSocketAddress(host, Net.GAME_PORT), 4000) }.isSuccess
            if (!ok || !open) {
                runCatching { s.close() }
                if (open) events.add(NetEvent.Lost(beforeConnecting = true))
                return@thread
            }
            s.tcpNoDelay = true
            socket = s
            out = PrintWriter(s.getOutputStream(), true).also { it.println("HELLO|${Net.VERSION}") }
            events.add(NetEvent.Connected)
            runCatching {
                val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
                while (open) {
                    val line = reader.readLine() ?: break
                    events.add(NetEvent.Message(0, line))
                }
            }
            if (open) events.add(NetEvent.Lost(beforeConnecting = false))
        }
    }

    override fun send(line: String) {
        out?.println(line)
    }

    override fun close() {
        open = false
        runCatching { socket?.close() }
    }
}

/** Busca partidas anunciadas en la red local. */
class Discovery {
    data class Found(val ip: String, val players: Int, val max: Int, val seen: Long)

    private val found = ConcurrentHashMap<String, Found>()
    @Volatile private var open = true
    private val socket: DatagramSocket? = runCatching {
        DatagramSocket(null).apply {
            reuseAddress = true
            broadcast = true
            soTimeout = 500
            bind(InetSocketAddress(Net.DISCOVERY_PORT))
        }
    }.getOrNull()

    init {
        thread("busca") {
            val buf = ByteArray(256)
            val mine = Net.localIps().toSet()
            while (open && socket != null) {
                val packet = DatagramPacket(buf, buf.size)
                try {
                    socket.receive(packet)
                } catch (_: SocketTimeoutException) {
                    continue
                } catch (_: Exception) {
                    break
                }
                val parts = String(packet.data, 0, packet.length, Charsets.UTF_8).split('|')
                if (parts.size < 3 || parts[0] != Net.TAG) continue
                val ip = packet.address.hostAddress
                if (ip in mine) continue // nuestra propia partida
                found[ip] = Found(ip, parts[1].toIntOrNull() ?: 1, parts[2].toIntOrNull() ?: Net.MAX_PLAYERS, System.currentTimeMillis())
            }
        }
    }

    /** Partidas vistas en los últimos 3 segundos. */
    fun games(): List<Found> {
        val now = System.currentTimeMillis()
        return found.values.filter { now - it.seen < 3000 }.sortedBy { it.ip }
    }

    fun close() {
        open = false
        runCatching { socket?.close() }
    }
}

private fun thread(name: String, body: () -> Unit) =
    Thread(body, "red-$name").apply { isDaemon = true }.start()
