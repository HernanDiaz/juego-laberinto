#!/usr/bin/env bash
# Capturas para las tiendas con el modo tour (no mueve el ratón: inyecta las órdenes en el juego).
# Uso (desde la raíz, con JAVA_HOME apuntando al JDK):  bash tools/capturas_tienda.sh
# Después: python tools/generar_tienda.py  (las escala al tamaño de cada tienda)
set -e
cd "$(dirname "$0")/.."
OUT="$PWD/build/capturas"
rm -rf "$OUT" && mkdir -p "$OUT"

guion() { # $1 = idioma
    local l=$1
    echo "debug:stars:3,2,2;lang:$l;debug:menu;wait:40;shot:${l}_1_menu;\
debug:map;wait:40;shot:${l}_2_map;\
debug:play:3;wait:30;debug:autoplay;wait:500;debug:manual;wait:450;shot:${l}_3_game;\
debug:win;wait:120;shot:${l}_4_victory;\
debug:map;wait:30;debug:select:3;wait:30;shot:${l}_5_level;\
debug:multi;wait:30;shot:${l}_6_multi;\
debug:howto:1;wait:40;shot:${l}_7_howto"
}

# carpeta, tamaño de ventana, "ios" o nada.  Cada tienda escala luego sin suavizar:
#   android 540x960 x2 = 1080x1920 · iphone 402x874 x3 = 1206x2622 (6,3") · ipad 512x683 x4 = 2048x2732
for destino in "android 540x960" "iphone 402x874 ios" "ipad 512x683 ios"; do
    set -- $destino
    mkdir -p "$OUT/$1"
    for l in es en; do
        ./gradlew lwjgl3:run --console=plain -q --args="tour ../build/capturas/$1 $(guion $l) $2 ${3:-}" 2>&1 | grep -v "^w:" || true
    done
done
ls -R "$OUT"
