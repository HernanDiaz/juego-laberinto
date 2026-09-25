"""Genera efectos de sonido y música chiptune en ../assets/sfx (WAV 22050 Hz mono).

Uso:  python tools/generar_sonidos.py
Solo usa la biblioteca estándar. Resultado determinista (semilla fija).
"""
import math
import os
import random
import struct
import wave

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "assets", "sfx")
SR = 22050
rnd = random.Random(7)


def midi(n):
    return 440.0 * 2 ** ((n - 69) / 12)


NOTAS = {"C": 0, "C#": 1, "D": 2, "D#": 3, "E": 4, "F": 5, "F#": 6, "G": 7, "G#": 8, "A": 9, "A#": 10, "B": 11}


def nota(s):
    """'A4' -> número MIDI."""
    return 12 * (int(s[-1]) + 1) + NOTAS[s[:-1]]


# ---------------------------------------------------------------- osciladores

def square(phase, duty=0.5):
    return 1.0 if (phase % 1.0) < duty else -1.0


def triangle(phase):
    p = phase % 1.0
    return 4 * p - 1 if p < 0.5 else 3 - 4 * p


def tono(freq, dur, wave_fn=square, vol=0.5, attack=0.005, decay=None, slide_to=None, duty=0.5):
    """Nota con envolvente simple. decay=None -> sostenida con release corto."""
    n = int(dur * SR)
    out = []
    phase = 0.0
    for i in range(n):
        t = i / SR
        f = freq if slide_to is None else freq + (slide_to - freq) * (i / n)
        phase += f / SR
        if wave_fn is square:
            s = square(phase, duty)
        else:
            s = wave_fn(phase)
        env = min(1.0, t / attack) if attack > 0 else 1.0
        if decay is not None:
            env *= math.exp(-t / decay)
        else:
            rel = 0.02
            if t > dur - rel:
                env *= max(0.0, (dur - t) / rel)
        out.append(s * env * vol)
    return out


def ruido(dur, vol=0.5, decay=0.05, hold=1):
    """Ruido blanco; hold>1 lo hace más grave (sample & hold)."""
    n = int(dur * SR)
    out = []
    v = 0.0
    for i in range(n):
        if i % hold == 0:
            v = rnd.uniform(-1, 1)
        out.append(v * vol * math.exp(-(i / SR) / decay))
    return out


def silencio(dur):
    return [0.0] * int(dur * SR)


def mezclar(*pistas):
    n = max(len(p) for p in pistas)
    out = [0.0] * n
    for p in pistas:
        for i, s in enumerate(p):
            out[i] += s
    return out


def en(buf, pos, sonido):
    """Suma 'sonido' dentro de 'buf' a partir de la muestra 'pos'."""
    for i, s in enumerate(sonido):
        j = pos + i
        if j < len(buf):
            buf[j] += s


def guardar(nombre, datos):
    os.makedirs(OUT, exist_ok=True)
    with wave.open(os.path.join(OUT, nombre), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        frames = b"".join(struct.pack("<h", int(max(-1.0, min(1.0, s)) * 32000)) for s in datos)
        w.writeframes(frames)


# ---------------------------------------------------------------- efectos

def efectos():
    # Girar la pieza: dos blips rápidos
    guardar("rotate.wav", tono(880, 0.035, vol=0.25, decay=0.03) + tono(1320, 0.045, vol=0.25, decay=0.03))

    # Empujar la fila: rozamiento de piedra + golpe final
    roce = ruido(0.30, vol=0.35, decay=0.5, hold=14)
    for i in range(len(roce)):
        roce[i] *= min(1.0, i / 800)
    golpe = tono(140, 0.12, triangle, vol=0.6, decay=0.05, slide_to=50)
    buf = roce + silencio(0.12)
    en(buf, int(0.28 * SR), golpe)
    en(buf, int(0.28 * SR), ruido(0.06, vol=0.3, decay=0.02, hold=4))
    guardar("push.wav", buf)

    # Paso: tic corto (el juego varía el tono)
    guardar("step.wav", mezclar(tono(520, 0.04, triangle, vol=0.35, decay=0.012), ruido(0.02, vol=0.08, decay=0.006, hold=2)))

    # Tesoro: arpegio ascendente brillante
    buf = []
    for n in ("C5", "E5", "G5", "C6"):
        buf += tono(midi(nota(n)), 0.07, vol=0.22, duty=0.25, decay=0.08)
    buf += tono(midi(nota("E6")), 0.25, vol=0.2, duty=0.25, decay=0.12)
    guardar("treasure.wav", buf)

    # Tesoro de un rival: más grave y corto
    buf = []
    for n in ("A3", "C4", "E4"):
        buf += tono(midi(nota(n)), 0.07, vol=0.2, duty=0.25, decay=0.07)
    guardar("rival.wav", buf)

    # Clic de botón
    guardar("click.wav", tono(660, 0.03, vol=0.22, decay=0.02) + tono(990, 0.03, vol=0.18, decay=0.02))

    # Movimiento no permitido
    guardar("denied.wav", tono(110, 0.08, vol=0.25, duty=0.3) + silencio(0.04) + tono(98, 0.12, vol=0.25, duty=0.3))

    # Victoria: fanfarria
    buf = silencio(1.6)
    t = 0.0
    for n, d in (("C5", 0.12), ("C5", 0.12), ("C5", 0.12), ("G5", 0.36), ("E5", 0.12), ("G5", 0.6)):
        en(buf, int(t * SR), tono(midi(nota(n)), d, vol=0.2, duty=0.25))
        en(buf, int(t * SR), tono(midi(nota(n)) / 2, d, triangle, vol=0.3))
        t += d
    guardar("win.wav", buf)

    # Derrota: descendente
    buf = []
    for n, d in (("G4", 0.2), ("E4", 0.2), ("C4", 0.2), ("B3", 0.5)):
        buf += tono(midi(nota(n)), d, vol=0.2, duty=0.5, slide_to=midi(nota(n)) * (0.94 if d > 0.3 else 1))
    guardar("lose.wav", buf)


# ---------------------------------------------------------------- música

BPM = 112
CORCHEA = 60 / BPM / 2  # duración de una corchea

# (nota, corcheas); None = silencio. 8 compases de 8 corcheas.
MELODIA = [
    ("A4", 2), ("C5", 2), ("E5", 1), ("D5", 1), ("C5", 2),
    ("A4", 2), ("F4", 2), ("A4", 1), ("C5", 1), ("A4", 2),
    ("G4", 2), ("C5", 2), ("E5", 2), ("G5", 2),
    ("D5", 3), ("B4", 1), ("G4", 4),
    ("E5", 2), ("D5", 1), ("C5", 1), ("A4", 2), ("C5", 2),
    ("F5", 2), ("E5", 1), ("D5", 1), ("C5", 2), ("A4", 2),
    ("G4", 1), ("A4", 1), ("C5", 2), ("E5", 2), ("D5", 2),
    ("B4", 2), ("G#4", 2), ("E4", 3), (None, 1),
]
BAJOS = ["A2", "F2", "C3", "G2", "A2", "F2", "C3", "E2"]


def musica():
    compases = len(BAJOS)
    total = int(compases * 8 * CORCHEA * SR)
    buf = [0.0] * total

    # Melodía (cuadrada 25%)
    t = 0
    for n, dur in MELODIA:
        if n is not None:
            en(buf, int(t * CORCHEA * SR), tono(midi(nota(n)), dur * CORCHEA * 0.92, vol=0.11, duty=0.25))
            # eco suave
            en(buf, int((t + 0.75) * CORCHEA * SR), tono(midi(nota(n)), dur * CORCHEA * 0.6, vol=0.035, duty=0.25))
        t += dur

    # Bajo (triangular): raíz y octava alternando
    for c, raiz in enumerate(BAJOS):
        r = nota(raiz)
        for k in range(8):
            n = r if k % 2 == 0 else r + 12
            en(buf, int((c * 8 + k) * CORCHEA * SR), tono(midi(n), CORCHEA * 0.85, triangle, vol=0.22))

    # Percusión: bombo en 1 y 3, charles en contratiempos
    for c in range(compases):
        for k in range(8):
            pos = int((c * 8 + k) * CORCHEA * SR)
            if k in (0, 4):
                en(buf, pos, tono(120, 0.12, triangle, vol=0.35, decay=0.04, slide_to=45))
            if k % 2 == 1:
                en(buf, pos, ruido(0.03, vol=0.06, decay=0.01, hold=1))
            if k == 4 and c % 2 == 1:
                en(buf, pos, ruido(0.08, vol=0.1, decay=0.03, hold=3))

    guardar("music.wav", buf)


if __name__ == "__main__":
    efectos()
    musica()
    print("Sonidos generados en", OUT)
