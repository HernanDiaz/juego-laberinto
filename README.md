# Las vueltas de la vida (pixel art)

Juego de laberinto para Android hecho con libGDX + Kotlin. El laberinto es el camino de la vida:
cada jugador persigue metas secretas en tres etapas (juventud, adultez y madurez), en el orden
que quiera dentro de cada etapa, y gana el primero que vuelve a casa para jubilarse.

- **Dinero:** las monedas del camino se recogen al pasar. Algunas metas cuestan monedas y otras
  dan dinero (primer sueldo, trabajo, ascenso). Precios y recompensas en `Goals.COST` / `Goals.REWARD`.
- **Sucesos:** terminar en una casilla "?" provoca herencia, lotería, multa, factura, regalo o mudanza.
- **Rivales:** cada IA tiene una personalidad (ambicioso, aventurero o tranquilo) que cambia sus prioridades.

## Niveles
8 niveles en `assets/levels.json` (tablero, rivales, precios, sucesos y umbrales de estrellas); sus
nombres y descripciones son `level.N.name` / `level.N.desc` en los textos. Estrellas: 1 por ganar,
2 si además ganas en `stars.rounds` rondas o menos, 3 si además terminas con `stars.coins` monedas.
`LevelsTest` juega cada nivel con IAs e imprime rondas y monedas típicas para ajustar los umbrales.

En `board`, `pushLines` son las filas/columnas que se empujan (las casillas cuya fila y columna no se
empujan son fijas) y `straight`, `corner`, `tee`, `cross`, `deadEnd` son los pesos de cada pieza.

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
