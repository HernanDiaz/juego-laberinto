package com.hernan.laberinto.screens

import com.hernan.laberinto.net.NetSession

/** Cómo se juega una partida. */
sealed class PlayMode {
    /** Un jugador contra la IA (niveles y partida libre). */
    object Solo : PlayMode()

    /** Varias personas en el mismo móvil, pasándoselo por turnos (más rivales IA opcionales). */
    object HotSeat : PlayMode()

    /** Por Wi-Fi: cada uno en su móvil. [localSlot] es el puesto de este móvil. */
    class Online(val session: NetSession, val localSlot: Int, val seed: Long) : PlayMode()
}
