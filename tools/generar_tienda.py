"""Material gráfico para las fichas de Google Play y de la App Store (carpeta store/).

Uso:
  1. bash tools/capturas_tienda.sh   (capturas con el modo tour en build/capturas/<tienda>/)
  2. python tools/generar_tienda.py  (o indicando otra carpeta de capturas)

Genera, escalando sin suavizar (pixel art):
  - store/capturas/<idioma>/N_nombre.png          Google Play, 1080x1920 (x2)
  - store/capturas_ios/iphone/<idioma>/...        App Store iPhone 6,3", 1206x2622 (x3)
  - store/capturas_ios/ipad/<idioma>/...          App Store iPad 13", 2048x2732 (x4)
  - store/destacada_<idioma>.png                  imagen destacada de Google Play, 1024x500
El icono de 512x512 (store/icono-512.png) lo genera generar_sprites.py.
"""
import glob
import os
import random
import sys
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "assets")
STORE = os.path.join(ROOT, "store")

TITULOS = {
    "es": ["LAS VUELTAS", "DE LA VIDA"],
    "en": ["TWISTS", "OF LIFE"],
}
ORO = (250, 204, 64)
SOMBRA = (10, 12, 18)


def fuente_latina():
    """Glifos de la fuente pixel del juego (assets/font.fnt)."""
    img = Image.open(os.path.join(ASSETS, "font.png")).convert("RGBA")
    glifos = {}
    with open(os.path.join(ASSETS, "font.fnt"), encoding="utf-8") as f:
        for linea in f:
            if not linea.startswith("char id="):
                continue
            campos = dict(p.split("=") for p in linea.split()[1:])
            x, y, w, h = (int(campos[k]) for k in ("x", "y", "width", "height"))
            glifos[int(campos["id"])] = (img.crop((x, y, x + w, y + h)), int(campos["xadvance"]))
    return glifos


def texto(lienzo, glifos, cadena, x, y, escala, color):
    """Dibuja [cadena] con la fuente pixel a [escala], con sombra. (x, y) = esquina superior izquierda."""
    for dx, dy, col in ((escala, escala, SOMBRA), (0, 0, color)):
        cx = x
        for ch in cadena:
            glifo, avance = glifos[ord(ch)]
            mascara = glifo.split()[3].resize((glifo.width * escala, glifo.height * escala), Image.NEAREST)
            capa = Image.new("RGBA", mascara.size, col + (255,))
            lienzo.paste(capa, (cx + dx, y + dy), mascara)
            cx += avance * escala


def ancho_texto(glifos, cadena, escala):
    return sum(glifos[ord(ch)][1] for ch in cadena) * escala


def destacada(idioma, glifos):
    W, H, E = 1024, 500, 4  # escala del pixel art
    tiles = Image.open(os.path.join(ASSETS, "tiles.png")).convert("RGBA")
    rnd = random.Random(11)
    arte = Image.new("RGBA", (W // E + 32, H // E + 32))
    for ty in range(0, arte.height, 32):
        for tx in range(0, arte.width, 32):
            m = rnd.choice([3, 5, 6, 7, 9, 10, 11, 12, 13, 14, 15])
            arte.paste(tiles.crop((m * 32, 0, m * 32 + 32, 32)), (tx, ty))
    # metas y personas repartidas por la mitad derecha
    metas = Image.open(os.path.join(ASSETS, "treasures.png")).convert("RGBA")
    personas = Image.open(os.path.join(ASSETS, "pawns.png")).convert("RGBA")
    for k, (gx, gy) in enumerate([(178, 28), (205, 82), (182, 102), (232, 18), (218, 50), (246, 72)]):
        g = [2, 9, 12, 16, 11, 7][k]
        arte.alpha_composite(metas.crop((g * 16, 0, g * 16 + 16, 16)), (gx, gy))
    for k, (px, py) in enumerate([(192, 56), (238, 96), (176, 72)]):
        arte.alpha_composite(personas.crop((k * 16, 0, k * 16 + 16, 16)), (px, py))
    fondo = arte.crop((0, 0, W // E, H // E)).resize((W, H), Image.NEAREST)

    # oscurecer la izquierda para que se lea el título
    sombra = Image.new("RGBA", (W, H))
    for x in range(W):
        a = int(235 * min(1.0, max(0.0, 1.25 - x / (W * 0.62))))
        for y in range(H):
            sombra.putpixel((x, y), (12, 14, 20, a))
    fondo.alpha_composite(sombra)

    lineas = TITULOS[idioma]
    escala = 9
    alto_linea = 12 * escala
    y0 = (H - alto_linea * len(lineas)) // 2 - 10
    for i, linea in enumerate(lineas):
        texto(fondo, glifos, linea, 50, y0 + i * alto_linea, escala, ORO)
    return fondo.convert("RGB")


# carpeta de build/capturas -> (carpeta en store/, escala)
TIENDAS = {
    "android": ("capturas", 2),
    "iphone": (os.path.join("capturas_ios", "iphone"), 3),
    "ipad": (os.path.join("capturas_ios", "ipad"), 4),
}


def main():
    origen = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, "build", "capturas")
    glifos = fuente_latina()
    for tienda, (carpeta, escala) in TIENDAS.items():
        for ruta in sorted(glob.glob(os.path.join(origen, tienda, "*_*.png"))):
            nombre = os.path.basename(ruta)
            idioma, resto = nombre.split("_", 1)
            img = Image.open(ruta).convert("RGB")
            destino = os.path.join(STORE, carpeta, idioma)
            os.makedirs(destino, exist_ok=True)
            img.resize((img.width * escala, img.height * escala), Image.NEAREST).save(os.path.join(destino, resto))
    for idioma in TITULOS:
        destacada(idioma, glifos).save(os.path.join(STORE, f"destacada_{idioma}.png"))
    print("Material de la tienda en", STORE)


if __name__ == "__main__":
    main()
