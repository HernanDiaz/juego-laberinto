#!/usr/bin/env bash
# Vídeos para las tiendas (vista previa de la App Store y vídeo de YouTube para Google Play).
# Graba con el modo tour (tiempo fijo de 1/30 s por fotograma) y monta con ffmpeg, con la música del juego.
# Uso (desde la raíz, con JAVA_HOME apuntando al JDK):  bash tools/video_tienda.sh [es|en ...]
# Resultado: store/videos/<formato>_<idioma>.mp4
set -e
cd "$(dirname "$0")/.."
IDIOMAS=${*:-es en}
mkdir -p store/videos

guion() { # $1 = idioma.  Unos 28 s: la App Store admite de 15 a 30.
    echo "debug:stars:3,2,2;lang:$1;debug:menu;wait:5;rec:on;wait:60;\
debug:map;wait:60;debug:select:3;wait:45;\
debug:play:3;wait:20;debug:autoplay;wait:500;\
debug:win;wait:90;debug:multi;wait:60;rec:off"
}

# formato, ventana, tamaño final, "ios" o nada
#   iphone: vista previa de 6,7"/6,9" (886x1920) · ipad: 13" (1200x1600) · youtube: 1080x1920
for f in "iphone 443x960 886x1920 ios" "ipad 600x800 1200x1600 ios" "youtube 540x960 1080x1920"; do
    set -- $f
    for l in $IDIOMAS; do
        dir="build/video/$1_$l"
        rm -rf "$dir" && mkdir -p "$dir"
        # el juego corre en assets/: la carpeta va relativa (la ruta del proyecto tiene espacios)
        ./gradlew lwjgl3:run --console=plain -q --args="tour ../$dir $(guion $l) $2 ${4:-}" 2>&1 | grep -v "^w:" || true
        n=$(ls "$dir" | wc -l)
        ffmpeg -y -loglevel error -framerate 30 -i "$dir/f_%05d.png" \
            -stream_loop -1 -i assets/sfx/music.wav \
            -filter_complex "[0:v]scale=${3/x/:}:flags=neighbor,format=yuv420p[v];[1:a]aformat=sample_rates=44100:channel_layouts=stereo,afade=t=out:st=$(( n / 30 - 2 )):d=2[a]" \
            -map "[v]" -map "[a]" -frames:v "$n" -shortest \
            -c:v libx264 -profile:v high -level 4.0 -crf 18 -r 30 -c:a aac -b:a 256k \
            -movflags +faststart "store/videos/$1_$l.mp4"
        echo "store/videos/$1_$l.mp4: $n fotogramas ($(( n / 30 )) s)"
    done
done
