"""Genera las fuentes bitmap de los idiomas que no usan el alfabeto latino.

Uso:  python tools/generar_fuentes.py      (después de generar_sprites.py, que crea font.fnt)

- Chino, japonés y ruso: Fusion Pixel Font 12 px (OFL). Solo se incluyen los caracteres que
  aparecen en los textos de ese idioma, más ASCII (y el cirílico completo para el ruso).
- Hindi: llama a GenerarFuenteHindi.java (Java2D combina las letras devanagari).

Vuelve a ejecutarlo cada vez que cambies los textos de esos idiomas: un carácter nuevo que
no esté en la fuente no se dibujaría.
"""
import os
import subprocess
import tempfile
from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "assets")
FUENTES = os.path.join(ROOT, "tools", "fuentes")

FUSION = {
    "zh": "fusion-pixel-12px-proportional-zh_hans.ms.bitmap.ttf",
    "ja": "fusion-pixel-12px-proportional-ja.ms.bitmap.ttf",
    "ru": "fusion-pixel-12px-proportional-zh_hans.ms.bitmap.ttf",
}
# Nombre de cada idioma en su propia lengua (debe coincidir con Strings.LANGUAGE_NAMES)
NOMBRES = {"zh": "简体中文", "ja": "日本語", "ru": "Русский", "hi": "हिन्दी"}
CIRILICO = "".join(chr(c) for c in range(0x410, 0x450)) + "Ёё"
# Símbolos de moneda: Google Play manda el precio en la moneda de cada país
MONEDAS = "€£¥₹₽₩₺₫₱₪₴₦₡₸฿₲₵₭₮₼₾"

CELDA_ALTO = 16   # ascendente 13 + descendente 3 en Fusion Pixel 12 px
CENTRO = 10.5     # centro visual de mayúsculas y hanzi (medido en pantalla)
ATLAS_W = 512


def textos(lang):
    vals = []
    with open(os.path.join(ASSETS, "i18n", f"strings_{lang}.properties"), encoding="utf-8") as f:
        for linea in f:
            linea = linea.rstrip("\n")
            if linea.startswith("#") or "=" not in linea:
                continue
            vals.append(linea.split("=", 1)[1])
    vals.append(NOMBRES[lang])
    return vals


def fuente_fusion(lang):
    font = ImageFont.truetype(os.path.join(FUENTES, FUSION[lang]), 12)
    chars = set(chr(c) for c in range(32, 127))
    for t in textos(lang):
        chars.update(t)
    if lang == "ru":
        chars.update(CIRILICO)
    # Solo los símbolos de moneda que existen en la fuente (los demás se muestran con el código ISO)
    chars.update(ch for ch in MONEDAS if font.getmask(ch).getbbox() is not None)
    chars.discard("\n")
    chars = sorted(chars)

    glifos = []
    for ch in chars:
        avance = max(1, round(font.getlength(ch)))
        bbox = font.getbbox(ch)
        ancho = max(avance, bbox[2], 1)
        img = Image.new("RGBA", (ancho, CELDA_ALTO), (0, 0, 0, 0))
        d = ImageDraw.Draw(img)
        d.fontmode = "1"  # sin antialiasing: píxeles nítidos
        d.text((0, 0), ch, font=font, fill=(255, 255, 255, 255))
        glifos.append((ch, img, avance))

    # empaquetar en filas
    x = y = 0
    pos = []
    for _, img, _ in glifos:
        if x + img.width + 1 > ATLAS_W:
            x = 0
            y += CELDA_ALTO + 1
        pos.append((x, y))
        x += img.width + 1
    alto = 1
    while alto < y + CELDA_ALTO:
        alto *= 2
    atlas = Image.new("RGBA", (ATLAS_W, alto), (0, 0, 0, 0))
    for (_, img, _), p in zip(glifos, pos):
        atlas.paste(img, p)
    atlas.save(os.path.join(ASSETS, f"font_{lang}.png"))

    with open(os.path.join(ASSETS, f"font_{lang}.fnt"), "w", encoding="utf-8") as f:
        f.write(f'info face="pixel-{lang}" size=12 bold=0 italic=0 charset="" unicode=1 stretchH=100 smooth=0 aa=1 padding=0,0,0,0 spacing=1,1\n')
        f.write(f"common lineHeight=15 base=13 scaleW={ATLAS_W} scaleH={alto} pages=1 packed=0\n")
        f.write(f'page id=0 file="font_{lang}.png"\n')
        f.write(f"chars count={len(glifos)}\n")
        for (ch, img, avance), (px, py) in zip(glifos, pos):
            f.write(f"char id={ord(ch)} x={px} y={py} width={img.width} height={CELDA_ALTO} "
                    f"xoffset=0 yoffset=0 xadvance={avance} page=0 chnl=0\n")
    escribir_meta(lang, CENTRO, romper_en_cualquier_letra=lang in ("zh", "ja"))
    print(f"{lang}: {len(glifos)} caracteres")


def escribir_meta(lang, centro, romper_en_cualquier_letra=False):
    with open(os.path.join(ASSETS, f"font_{lang}.properties"), "w", encoding="utf-8") as f:
        f.write(f"capCenter={centro}\n")
        f.write(f"breakAnywhere={'true' if romper_en_cualquier_letra else 'false'}\n")


def buscar_java():
    home = os.environ.get("JAVA_HOME") or r"D:\AndroidStudio\jbr"
    return os.path.join(home, "bin", "java")


def fuente_hindi():
    with tempfile.NamedTemporaryFile("w", encoding="utf-8", suffix=".txt", delete=False) as tmp:
        tmp.write("\n".join(textos("hi") + ["₹"]))
        ruta = tmp.name
    try:
        subprocess.run([buscar_java(), os.path.join(ROOT, "tools", "GenerarFuenteHindi.java"), ruta,
                        os.path.join(FUENTES, "NotoSansDevanagari.ttf"), "13", ASSETS], check=True)
    finally:
        os.remove(ruta)
    escribir_meta("hi", 7)


if __name__ == "__main__":
    for lang in ("zh", "ja", "ru"):
        fuente_fusion(lang)
    fuente_hindi()
