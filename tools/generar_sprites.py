"""Genera todos los sprites pixel art del juego en ../assets.

Uso:  python tools/generar_sprites.py
Requiere Pillow. Los resultados son deterministas (semilla fija), así que
puedes retocar los PNG a mano después o cambiar este script y regenerar.
"""
import os
import random
from PIL import Image, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "assets")
ANDROID_RES = os.path.join(ROOT, "android", "res")

OUTLINE = (24, 18, 30, 255)

PALETA = {
    "K": OUTLINE,
    "k": (45, 38, 58, 255),
    "Y": (250, 204, 64, 255),
    "y": (196, 134, 34, 255),
    "W": (248, 246, 236, 255),
    "w": (176, 178, 196, 255),
    "g": (118, 116, 138, 255),
    "R": (224, 58, 58, 255),
    "r": (146, 32, 48, 255),
    "B": (72, 128, 232, 255),
    "b": (40, 62, 150, 255),
    "C": (98, 214, 232, 255),
    "G": (92, 196, 84, 255),
    "e": (40, 122, 58, 255),
    "P": (162, 84, 206, 255),
    "p": (98, 44, 140, 255),
    "O": (244, 142, 44, 255),
    "N": (150, 96, 54, 255),
    "n": (92, 56, 32, 255),
    "S": (244, 176, 164, 255),
    "T": (232, 212, 160, 255),
    "t": (186, 154, 104, 255),
}

# --------------------------------------------------------------------------
# Tesoros (16x16). El script añade el contorno negro y centra el dibujo.
# El orden debe coincidir con Treasures.NAMES en el código Kotlin.
# --------------------------------------------------------------------------
TESOROS = [
    ("moneda", """
...YYYYYY...
..YWYYYYYYy.
.YWYYyyyYYYy
.YYYyYYYyYYy
.YYYyYYYYYYy
.YYYYyyyYYYy
.YYYYYYYyYYy
.YYYyYYYyYYy
.YYYYyyyYYYy
..YYYYYYYYy.
...yyyyyy...
"""),
    ("llave", """
.YYY..........
YyyyY.........
Yy.yYYYYYYYYYY
Yy.yYyyyyyyyyy
YyyyY.....Yy.Y
.YYY......Yy.Y
"""),
    ("corona", """
Y.....Y.....Y
YY...YYY...YY
YYY.YYWYY.YYY
YYYYYYYYYYYYY
YyRyyyByyyGyY
YYYYYYYYYYYYY
yyyyyyyyyyyyy
"""),
    ("gema", """
..CCCCCCC..
.CWCCCCCbC.
CWCCCCCCCbb
bCCCCCCCbbb
.bCCCCCCbb.
..bCCCCbb..
...bCCbb...
....bbb....
.....b.....
"""),
    ("anillo", """
....BBB....
...BWBbB...
....BBB....
...YYYYY...
..YW...yY..
.YW.....yY.
.Y.......y.
.Y.......y.
.Yy.....yy.
..yy...yy..
...yyyyy...
"""),
    ("pocion", """
....NNN....
....nnn....
....wWw....
....wWw....
...wWWww...
..wRRRRRw..
.wRSRRRRrw.
.wRSRRRRrw.
.wRRRRRRrw.
.wRRRRRrrw.
..wrrrrrw..
...wwwww...
"""),
    ("espada", """
......W......
.....WWw.....
.....WWw.....
.....WWw.....
.....WWw.....
.....WWw.....
.....WWw.....
.....WWw.....
..YYYYYYYYY..
..yyyyyyyyy..
.....NNn.....
.....NNn.....
.....YYy.....
"""),
    ("escudo", """
BBBBBBBBBBB
BWBBBYBBBBb
BBBBBYBBBBb
BBYYYYYYYBb
BBBBBYBBBBb
BBBBBYBBBBb
.BBBBYBBBb.
.BBBBBBBBb.
..BBBBBBb..
...BBBBb...
....bbb....
"""),
    ("calavera", """
...WWWWWW...
..WWWWWWWWw.
.WWWWWWWWWWw
.WWkkWWkkWWw
.WWkkWWkkWWw
.WWWWWWWWWWw
..WWWkkWWWw.
...WWWWWWw..
...WkWkWkw..
....wwwww...
"""),
    ("libro", """
..rRRRRRRRRR.
.rRRRRRRRRRRk
.rRRYYYYYRRRk
.rRRRRRRRRRRk
.rRRYYYYRRRRk
.rRRRRRRRRRRk
.rRRRRRRRRRRk
.rRRRRRRRRRRk
.rWWWWWWWWWWk
..rrrrrrrrrr.
"""),
    ("cofre", """
..NNNNNNNNNN..
.NNNNNNNNNNNN.
.NnnnnnnnnnnN.
.YYYYYYYYYYYY.
.NNNNNYYNNNNN.
.NNNNNykNNNNN.
.NnnnnnnnnnnN.
.NNNNNNNNNNNN.
.YnnnnnnnnnnY.
"""),
    ("vela", """
.....Y.....
....YOY....
....YOY....
.....O.....
.....k.....
....WWw....
....WWw....
....WWw....
....WWw....
....WWw....
..yYYYYYy..
.yYYYYYYYy.
"""),
    ("corazon", """
.RRR...RRR.
RWRRR.RRRRr
RWRRRRRRRRr
RRRRRRRRRRr
.RRRRRRRRr.
..RRRRRRr..
...RRRRr...
....RRr....
.....r.....
"""),
    ("estrella", """
......Y......
.....YYY.....
.....YWY.....
YYYYYYWYYYYYy
.YYYYYYYYYYy.
..YYYYYYYYy..
...YYYYYYy...
...YYYyYYy...
..YYYy.yYYy..
..Yyy...yyy..
"""),
    ("seta", """
....RRRRR....
..RRWWRRRRR..
.RRWWRRRWWRR.
RRRRRRRRWWRRr
RWWRRRRRRRRRr
rrrrrrrrrrrrr
....WWWWw....
....WWWWw....
....WWWWw....
....wwwww....
"""),
    ("manzana", """
......nG.....
......nGG....
..RRRRnRRR...
.RWRRRRRRRr..
.RWRRRRRRRr..
.RRRRRRRRRr..
.RRRRRRRRRr..
..RRRRRRRr...
...RRrRRr....
"""),
    ("fantasma", """
...WWWWW...
..WWWWWWW..
.WWWWWWWWw.
.WWkWWkWWw.
.WWkWWkWWw.
.WWWWWWWWw.
.WWWkkWWWw.
.WWWWWWWWw.
.WWWWWWWWw.
.WW.WWW.Ww.
.W...W...w.
"""),
    ("murcielago", """
.p.........p.
.pp..p.p..pp.
pPPp.ppp.pPPp
pPPPpYpYpPPPp
pPPPPpppPPPPp
pPP.pPPPp.PPp
pp...pPp...pp
p.....p.....p
"""),
    ("arana", """
.g.........g.
..g..ggg..g..
...ggwwwgg...
ggggRgggRgggg
...ggggggg...
..gggwgwggg..
.g.ggggggg.g.
g..ggggggg..g
..g..ggg..g..
.g.........g.
"""),
    ("buho", """
.N.......N.
.NN.....NN.
.NNNNNNNNN.
NWWWNNNWWWN
NWkWNNNWkWN
NWWWNONWWWN
NNNNNONNNNn
NNTTTTTTTNn
NTTnTTnTTNn
NNTTTTTTTNn
.NNTTTTTNn.
..OO...OO..
"""),
    ("raton", """
.ww.....ww....
wSSw...wSSw...
wSSwwwwwSSw...
.wwwwwwwwww...
.wwwkwwwkww...
.wwwwwwwwwww..
..wwwSSSwww...
...wwwwwww....
....wwwww..www
...........w..
"""),
    ("pez", """
....BBBB.......
..BBBBBBBB...B.
.BWkBBBBBBB.BB.
BBBBBBBBBBBBBB.
.BCBBBBBBBB.BB.
..BCCCCCBB...B.
....bbbb.......
"""),
    ("mapa", """
.TTTTTTTTTT.
TttTTTTTTTTt
.TTgTgTTTTT.
.TTTTTTTRTT.
.TTgTTTTTTT.
.TTTTTgTTRT.
.TTTTTTTTTT.
.TgTTTTTTgT.
TttTTTTTTTTt
.TTTTTTTTTT.
"""),
    ("reloj", """
NNNNNNNNNNN
.wWWWWWWWw.
.wYYYYYYYw.
..wYYYYYw..
...wYYYw...
....wYw....
....wYw....
...w.Y.w...
..w..Y..w..
.wYYYYYYYw.
.wWWWWWWWw.
NNNNNNNNNNN
"""),
]

PEON = """
.....XXXX.....
....XWXXXx....
....XXXXXx....
.....XXXx.....
....XXXXXx....
...XXXXXXXx...
....XXXXXx....
....XXXXXx....
...XXXXXXXx...
..XXXXXXXXXx..
..xxxxxxxxxx..
"""

COLORES_JUGADOR = [
    ((78, 140, 244, 255), (38, 70, 160, 255)),   # azul (humano)
    ((230, 64, 64, 255), (140, 30, 44, 255)),    # rojo (IA)
    ((92, 200, 88, 255), (36, 116, 52, 255)),    # verde
    ((250, 206, 64, 255), (180, 120, 30, 255)),  # amarillo
]


def dibujo_ascii(art, colores, size=16):
    filas = [f for f in art.strip("\n").split("\n")]
    alto = len(filas)
    ancho = max(len(f) for f in filas)
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    ox = (size - ancho) // 2
    oy = (size - alto) // 2
    for y, fila in enumerate(filas):
        for x, ch in enumerate(fila):
            if ch == ".":
                continue
            img.putpixel((ox + x, oy + y), colores[ch])
    return contorno(img)


def contorno(img):
    w, h = img.size
    src = img.copy()
    for y in range(h):
        for x in range(w):
            if src.getpixel((x, y))[3] != 0:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < w and 0 <= ny < h and src.getpixel((nx, ny))[3] != 0:
                    img.putpixel((x, y), OUTLINE)
                    break
    return img


# --------------------------------------------------------------------------
# Losetas 32x32: seto verde con camino de arena. Una por máscara de conexión
# (N=1, E=2, S=4, W=8) -> 16 losetas en tiles.png
# --------------------------------------------------------------------------
T = 32
P0, P1 = 10, 22  # el camino ocupa x/y en [P0, P1)

SETO = [(30, 72, 46), (46, 104, 56), (64, 132, 64), (104, 168, 80)]
SETO_BORDE = (22, 50, 36)
ARENA = (214, 178, 116)
ARENA_OSC = (176, 136, 86)
ARENA_CLARA = (234, 206, 146)
PIEDRA = (150, 116, 78)


def es_camino(mask, x, y):
    if P0 <= x < P1 and P0 <= y < P1:
        return True
    if mask & 1 and P0 <= x < P1 and y < P0:
        return True
    if mask & 4 and P0 <= x < P1 and y >= P1:
        return True
    if mask & 8 and P0 <= y < P1 and x < P0:
        return True
    if mask & 2 and P0 <= y < P1 and x >= P1:
        return True
    return False


def loseta(mask, rnd):
    img = Image.new("RGBA", (T, T))
    camino = [[es_camino(mask, x, y) for x in range(T)] for y in range(T)]
    for y in range(T):
        for x in range(T):
            if camino[y][x]:
                c = ARENA
                r = rnd.random()
                if r < 0.06:
                    c = ARENA_OSC
                elif r < 0.12:
                    c = ARENA_CLARA
                elif r < 0.14:
                    c = PIEDRA
                # sombra del seto sobre el camino (luz desde arriba-izquierda)
                if y > 0 and not camino[y - 1][x]:
                    c = ARENA_OSC
                elif y > 1 and not camino[y - 2][x]:
                    c = tuple((a + b) // 2 for a, b in zip(ARENA, ARENA_OSC))
                if x > 0 and not camino[y][x - 1]:
                    c = ARENA_OSC
            else:
                # textura de hojas
                r = rnd.random()
                if r < 0.18:
                    c = SETO[0]
                elif r < 0.30:
                    c = SETO[2]
                elif r < 0.34:
                    c = SETO[3]
                else:
                    c = SETO[1]
                # borde del seto junto al camino
                vecino_camino = False
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < T and 0 <= ny < T and camino[ny][nx]:
                        vecino_camino = True
                if vecino_camino:
                    c = SETO_BORDE
                elif y + 1 < T and camino[y + 1][x]:
                    c = SETO_BORDE
                # brillo en la parte alta del seto
                elif y > 0 and (y - 1 < 0 or camino[y - 1][x]) and rnd.random() < 0.7:
                    c = SETO[3]
            img.putpixel((x, y), c + (255,))
    # borde de la loseta para que se vea la rejilla
    for i in range(T):
        for (x, y) in ((i, 0), (0, i)):
            p = img.getpixel((x, y))
            img.putpixel((x, y), tuple(min(255, v + 18) for v in p[:3]) + (255,))
        for (x, y) in ((i, T - 1), (T - 1, i)):
            p = img.getpixel((x, y))
            img.putpixel((x, y), tuple(max(0, v - 40) for v in p[:3]) + (255,))
    return img


def generar_losetas():
    rnd = random.Random(1986)
    hoja = Image.new("RGBA", (T * 16, T))
    for mask in range(16):
        hoja.paste(loseta(mask, rnd), (mask * T, 0))
    hoja.save(os.path.join(ASSETS, "tiles.png"))


def generar_tesoros():
    hoja = Image.new("RGBA", (16 * len(TESOROS), 16), (0, 0, 0, 0))
    for i, (_, art) in enumerate(TESOROS):
        hoja.paste(dibujo_ascii(art, PALETA), (i * 16, 0))
    hoja.save(os.path.join(ASSETS, "treasures.png"))


def generar_peones():
    hoja = Image.new("RGBA", (16 * 4, 16), (0, 0, 0, 0))
    casas = Image.new("RGBA", (32 * 4, 32), (0, 0, 0, 0))
    for i, (claro, oscuro) in enumerate(COLORES_JUGADOR):
        pal = dict(PALETA)
        pal["X"] = claro
        pal["x"] = oscuro
        hoja.paste(dibujo_ascii(PEON, pal), (i * 16, 0))
        # marca de casilla de salida: anillo de color dentro del camino
        casa = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
        for y in range(32):
            for x in range(32):
                d2 = (x - 15.5) ** 2 + (y - 15.5) ** 2
                if 16 <= d2 <= 30:
                    casa.putpixel((x, y), oscuro)
                elif 30 < d2 <= 38:
                    casa.putpixel((x, y), claro)
        casas.paste(casa, (i * 32, 0))
    hoja.save(os.path.join(ASSETS, "pawns.png"))
    casas.save(os.path.join(ASSETS, "homes.png"))


def generar_ui():
    # ui.png: [0] flecha 16x16, [1] cursor 32x32 (en x=16), [2] pixel blanco
    ui = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    flecha = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for x in range(3, 13):
        mitad = (x - 3) * 0.6
        for y in range(16):
            if abs(y - 7.5) <= 6 - mitad:
                c = PALETA["Y"] if y < 8 else PALETA["y"]
                flecha.putpixel((x, y), c)
    flecha = contorno(flecha)
    ui.paste(flecha, (0, 0))
    cursor = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    for i in range(7):
        for (x, y) in ((i, 0), (0, i), (31 - i, 0), (31, i), (i, 31), (0, 31 - i), (31 - i, 31), (31, 31 - i)):
            cursor.putpixel((x, y), (255, 255, 255, 255))
        for (x, y) in ((i, 1), (1, i), (31 - i, 1), (30, i), (i, 30), (1, 31 - i), (31 - i, 30), (30, 31 - i)):
            cursor.putpixel((x, y), (255, 255, 255, 255))
    ui.paste(cursor, (16, 0))
    ui.putpixel((48, 0), (255, 255, 255, 255))
    # carta 24x30 en x=48? no cabe: la guardamos aparte
    ui.save(os.path.join(ASSETS, "ui.png"))

    carta = Image.new("RGBA", (26, 32), (0, 0, 0, 0))
    for y in range(32):
        for x in range(26):
            borde = x in (0, 25) or y in (0, 31)
            esquina = (x in (0, 25)) and (y in (0, 31))
            if esquina:
                continue
            if borde:
                carta.putpixel((x, y), OUTLINE)
            elif x in (1, 24) or y in (1, 30):
                carta.putpixel((x, y), PALETA["y"])
            elif x in (2, 23) or y in (2, 29):
                carta.putpixel((x, y), PALETA["Y"])
            else:
                carta.putpixel((x, y), PALETA["T"] if (x + y) % 7 else PALETA["t"])
    carta.save(os.path.join(ASSETS, "card.png"))


BOTON_COLORES = {
    # cara, brillo, sombra lateral, profundidad
    "verde": ((70, 150, 70), (150, 220, 110), (52, 116, 56), (30, 78, 44)),
    "pizarra": ((64, 74, 104), (120, 134, 176), (50, 58, 84), (30, 34, 52)),
}


def boton(colores, pulsado):
    """Botón 16x18 con relieve. Sin pulsar: 3 px de profundidad; pulsado: baja 2 px."""
    cara, brillo, lado, fondo = colores
    img = Image.new("RGBA", (16, 18), (0, 0, 0, 0))
    top = 2 if pulsado else 0
    prof = 1 if pulsado else 3
    bottom = 17
    for y in range(top, bottom + 1):
        for x in range(16):
            esquina_ext = (x in (0, 15)) and (y in (top, bottom))
            if esquina_ext:
                continue
            borde = x in (0, 15) or y in (top, bottom)
            if borde:
                c = OUTLINE[:3]
            elif y > bottom - 1 - prof:
                c = fondo
            elif y == top + 1 and not pulsado:
                c = brillo
            elif x == 1:
                c = brillo if not pulsado else cara
            elif x == 14:
                c = lado
            else:
                c = cara
            img.putpixel((x, y), c + (255,))
    # esquinas interiores redondeadas
    for (x, y) in ((1, top + 1), (14, top + 1), (1, bottom - 1), (14, bottom - 1)):
        img.putpixel((x, y), OUTLINE)
    return img


def generar_botones():
    # buttons.png: fila por color, [normal, pulsado]
    hoja = Image.new("RGBA", (32, 18 * len(BOTON_COLORES)), (0, 0, 0, 0))
    for i, colores in enumerate(BOTON_COLORES.values()):
        hoja.paste(boton(colores, False), (0, i * 18))
        hoja.paste(boton(colores, True), (16, i * 18))
    hoja.save(os.path.join(ASSETS, "buttons.png"))


ALTAVOZ = """
.....W....
....WW....
WWWWWW....
WWWWWW....
WWWWWW....
WWWWWW....
....WW....
.....W....
"""


def generar_iconos():
    # icons.png: [0] sonido activado, [1] sonido desactivado, [2] pausa (16x16)
    hoja = Image.new("RGBA", (48, 16), (0, 0, 0, 0))
    pausa = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(3, 13):
        for x in (4, 5, 6, 9, 10, 11):
            pausa.putpixel((x, y), PALETA["W"])
    hoja.paste(contorno(pausa), (32, 0))
    for i in range(2):
        img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        filas = ALTAVOZ.strip("\n").split("\n")
        for y, fila in enumerate(filas):
            for x, ch in enumerate(fila):
                if ch != ".":
                    img.putpixel((2 + x, 4 + y), PALETA["W"])
        if i == 0:
            for (x, y) in ((10, 6), (10, 9), (11, 7), (11, 8), (12, 4), (13, 5), (14, 6), (14, 7), (14, 8), (14, 9), (13, 10), (12, 11)):
                img.putpixel((x, y), PALETA["Y"])
        else:
            for k in range(5):
                img.putpixel((10 + k, 5 + k), PALETA["R"])
                img.putpixel((14 - k, 5 + k), PALETA["R"])
        hoja.paste(contorno(img), (i * 16, 0))
    hoja.save(os.path.join(ASSETS, "icons.png"))


def generar_fuente():
    """Fuente bitmap (AngelCode .fnt) a partir de la fuente por defecto de PIL."""
    font = ImageFont.load_default()
    # ASCII + Latin-1 completo: español, inglés, francés, alemán, italiano, portugués
    chars = [chr(c) for c in range(32, 127)] + [chr(c) for c in range(161, 256)]
    gw, gh = 6, 11
    cols = 16
    filas = (len(chars) + cols - 1) // cols
    img = Image.new("RGBA", (cols * (gw + 1), filas * (gh + 1)), (0, 0, 0, 0))
    lineas = []
    for i, ch in enumerate(chars):
        cx = (i % cols) * (gw + 1)
        cy = (i // cols) * (gh + 1)
        mask = font.getmask(ch)
        mw, mh = mask.size
        for y in range(mh):
            for x in range(mw):
                if mask.getpixel((x, y)):
                    img.putpixel((cx + x, cy + y), (255, 255, 255, 255))
        lineas.append(
            f"char id={ord(ch)} x={cx} y={cy} width={gw} height={gh} xoffset=0 yoffset=0 xadvance={gw} page=0 chnl=0"
        )
    img.save(os.path.join(ASSETS, "font.png"))
    with open(os.path.join(ASSETS, "font.fnt"), "w", encoding="utf-8") as f:
        f.write('info face="pixel" size=11 bold=0 italic=0 charset="" unicode=1 stretchH=100 smooth=0 aa=1 padding=0,0,0,0 spacing=1,1\n')
        f.write(f"common lineHeight=12 base=9 scaleW={img.width} scaleH={img.height} pages=1 packed=0\n")
        f.write('page id=0 file="font.png"\n')
        f.write(f"chars count={len(chars)}\n")
        f.write("\n".join(lineas) + "\n")


def generar_icono():
    base = Image.new("RGBA", (32, 32))
    tiles = Image.open(os.path.join(ASSETS, "tiles.png"))
    base.paste(tiles.crop((15 * T, 0, 16 * T, T)), (0, 0))
    tesoro = Image.open(os.path.join(ASSETS, "treasures.png")).crop((2 * 16, 0, 3 * 16, 16))
    base.alpha_composite(tesoro, (8, 8))
    for nombre, px in (("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)):
        d = os.path.join(ANDROID_RES, f"mipmap-{nombre}")
        os.makedirs(d, exist_ok=True)
        base.resize((px, px), Image.NEAREST).save(os.path.join(d, "ic_launcher.png"))


if __name__ == "__main__":
    os.makedirs(ASSETS, exist_ok=True)
    generar_losetas()
    generar_tesoros()
    generar_peones()
    generar_ui()
    generar_botones()
    generar_iconos()
    generar_fuente()
    generar_icono()
    print("Sprites generados en", ASSETS)
