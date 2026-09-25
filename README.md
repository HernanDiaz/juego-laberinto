# Laberinto (pixel art)

Juego para Android inspirado en el juego de mesa *Labyrinth*, hecho con libGDX + Kotlin.

## Módulos
- `core/` – lógica (tablero, reglas, IA) y pantallas.
- `android/` – lanzador de Android.
- `lwjgl3/` – lanzador de escritorio para probar rápido en el PC.
- `assets/` – sprites y sonidos generados por los scripts de `tools/`.

## Comandos
```
gradlew lwjgl3:run            # jugar en el PC
gradlew core:test             # tests de reglas + partida IA vs IA
gradlew android:assembleDebug # APK en android/build/outputs/apk/debug/
python tools/generar_sprites.py   # sprites (necesita Pillow)
python tools/generar_sonidos.py   # efectos y música chiptune
```

## Idiomas
Español, inglés, francés, alemán, italiano, portugués, chino simplificado, japonés, ruso, indonesio e hindi.

- Textos en `assets/i18n/strings_<idioma>.properties` (UTF-8); `strings.properties` es el inglés.
- Idiomas latinos: fuente pixel `font.fnt` (generar_sprites.py).
- Chino, japonés y ruso: `font_<idioma>.fnt`, creada con Fusion Pixel Font 12 px (OFL) solo con los
  caracteres usados. Hindi: `font_hi.fnt` + `font_hi.map`, cada akshara ya combinado por Java2D.
- **Si cambias textos de zh/ja/ru/hi, ejecuta `python tools/generar_fuentes.py`.** `gradlew core:test`
  avisa si falta alguna clave o algún carácter en una fuente.
- Para añadir un idioma: crea el archivo, añádelo a `Strings.LANGUAGES` y `LANGUAGE_NAMES`.
- Licencias de las fuentes en `assets/licencias/` (van dentro del APK, como exige la OFL).

## Pruebas automáticas con capturas (escritorio)
```
gradlew lwjgl3:run --args="tour <carpeta> lang:es;shot:menu;tap:216,490;wait:60;shot:partida"
```
Pasos: `wait:N`, `tap:X,Y`, `key:BACK`, `lang:xx`, `shot:nombre` (ver `TourGame.kt`).
Requiere JDK 17+ (el de Android Studio sirve: `D:\AndroidStudio\jbr`).
