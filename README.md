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
- `ios/` – lanzador de iPhone/iPad (MobiVM/RoboVM). Solo se carga en un Mac: en Windows Gradle lo ignora.
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

## Multijugador
- **En este móvil**: de 2 a 4 personas (más rivales IA opcionales) pasándose el móvil; entre turnos
  una pantalla tapa el tablero para que nadie vea las metas secretas de los demás.
- **Por Wi-Fi** (como en los otros juegos): todos en la misma Wi-Fi, de 2 a 4 móviles. Uno crea la
  partida y los demás la encuentran solos o se unen por IP. Código en `core/.../net/Net.kt`.
  - La partida es determinista: todos usan la misma semilla y solo viajan las jugadas
    (`MOVE|turno|jugador|giros|lado|línea|fila|col`); el anfitrión las reenvía a los demás.
  - Puertos: 45144 (TCP, partida) y 45143 (UDP, anuncios). En Windows, el firewall pregunta la
    primera vez: hay que permitirlo en redes privadas.
  - Prueba automática con dos copias en el PC (ver `TourGame`): una con
    `debug:wifi;debug:host;...;debug:start;debug:autoplay` y otra con
    `debug:wifi;debug:join:127.0.0.1;...;debug:autoplay`; `debug:dump` imprime el estado para compararlo.

## iPhone / iPad (TestFlight, sin Mac)

El juego se compila para iOS en un Mac de GitHub (GitHub Actions, `.github/workflows/ios.yml`) y se
sube a **TestFlight**; desde ahí se instala en el iPhone. Es el mismo sistema que en los otros juegos,
pero aquí la app la compila **MobiVM (RoboVM)**, que convierte el código Kotlin/Java a código nativo
de iOS, y el workflow monta el `.xcarchive` a mano para que Xcode la firme y la suba.

> **Sin probar todavía:** este workflow no se ha ejecutado nunca. La primera vez puede fallar en
> algún paso (sobre todo en el último, la firma de un archivo que no ha creado Xcode) y habrá que
> ajustarlo mirando el registro de GitHub.

Para configurarlo (solo la primera vez):

1. **Apple Developer** (99 USD/año): la misma cuenta de los otros juegos.
2. **Crear la app** en [App Store Connect](https://appstoreconnect.apple.com) → *Apps* → **+** → *Nueva app*:
   - Plataforma iOS, nombre "Las vueltas de la vida" e idioma principal español.
   - ID de paquete (*Bundle ID*): `com.hernandiaz.vueltasdelavida` (el mismo que en Android). Si no
     aparece en la lista, créalo antes en developer.apple.com → *Identifiers* → **+** → *App IDs*.
   - SKU: cualquiera, por ejemplo `vueltas-de-la-vida`.
3. **Secretos en GitHub** (en este repositorio): *Settings* → *Secrets and variables* → *Actions*.
   Son los mismos cuatro de los otros juegos, se puede reutilizar la misma clave de API:
   `APPLE_TEAM_ID`, `ASC_KEY_ID`, `ASC_ISSUER_ID` y `ASC_KEY_P8` (el contenido completo del `.p8`).

Para compilar una versión: GitHub → *Actions* → **iOS (TestFlight)** → *Run workflow*. Nunca se lanza
solo. La primera vez tarda más (RoboVM compila toda la biblioteca de Java; luego queda en caché).
Cuando termina, aparece en App Store Connect → *TestFlight*.

Notas:
- **Minutos de GitHub:** en un repositorio privado, las máquinas Mac gastan minutos del plan x10.
- **Versión:** `ios/robovm.properties` (`app.version`); el número de compilación lo pone el workflow.
- **Iconos:** `python tools/generar_sprites.py` también genera los de iOS (`ios/data/Media.xcassets`).
- **Nombre en cada idioma:** `ios/data/<idioma>.lproj/InfoPlist.strings`.
- **Java 8:** RoboVM solo entiende bytecode de Java 8, así que `core` se compila para Java 8
  (Android y escritorio lo usan igual).
- **Búsqueda de partidas por Wi-Fi:** en iPhone necesita que Apple conceda el permiso *multicast*
  (`com.apple.developer.networking.multicast`, se pide en developer.apple.com/contact/request/networking-multicast).
  Mientras tanto se juega **uniéndose por IP**. iOS pide permiso de "red local" la primera vez.
- **Botón Salir:** en iOS no aparece, porque las apps no pueden cerrarse solas.
- Para comprobar en Windows que el lanzador de iOS compila (solo el código, no la app):
  `gradlew -DincluirIos=1 ios:compileKotlin`.

## Publicación en Google Play
- Juego totalmente gratuito: sin anuncios ni compras dentro de la app.
- `gradlew android:bundleRelease` genera el `.aab`; la firma se lee de `keystore.properties`
  (plantilla en `keystore.properties.example`, nunca se sube a git).
- Material de la ficha en `store/` (textos en 11 idiomas, icono, imagen destacada, capturas,
  política de privacidad). Guía paso a paso: `store/PUBLICAR.md`.
- Capturas: modo tour a 540x960 y `python tools/generar_tienda.py <carpeta>`.
