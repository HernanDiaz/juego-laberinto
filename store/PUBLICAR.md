# Cómo publicar "Las vueltas de la vida" en Google Play

Todo lo técnico está preparado en el repositorio. Esta guía cubre lo que hay que hacer a mano.

## 1. Crear la clave de firma (una sola vez)

En la carpeta del proyecto (el JDK de Android Studio trae `keytool`):

```
"D:\AndroidStudio\jbr\bin\keytool" -genkeypair -v -keystore vueltas-de-la-vida.jks -alias subida -keyalg RSA -keysize 2048 -validity 10000
```

Te pedirá una contraseña y unos datos (nombre, país...). Después copia `keystore.properties.example`
como `keystore.properties` y pon la contraseña que elegiste.

**Haz una copia de seguridad de `vueltas-de-la-vida.jks` y de la contraseña** (por ejemplo en un
gestor de contraseñas y en una memoria USB). Ninguno de los dos se sube a GitHub.

Con "Firma de apps de Google Play" (activada por defecto), esta es la *clave de subida*: si se
pierde, Google permite cambiarla con un trámite, pero es mejor no perderla.

## 2. Generar el paquete para Google Play

```
gradlew android:bundleRelease
```

Resultado: `android/build/outputs/bundle/release/android-release.aab` (firmado si existe `keystore.properties`).
Para cada actualización hay que subir `versionCode` (y normalmente `versionName`) en `android/build.gradle`.

## 3. Crear la app en Play Console

- **Crear app**: nombre "Las vueltas de la vida", idioma predeterminado Español (España),
  tipo *Juego*, *Gratis*.
- **Ficha de Play Store**: textos en `store/ficha/<idioma>.txt` (añade cada idioma como traducción).
  - Icono: `store/icono-512.png`
  - Imagen destacada: `store/destacada_es.png` (y `destacada_en.png` para inglés)
  - Capturas de teléfono: `store/capturas/es/` y `store/capturas/en/`
  - Categoría: Juegos › Puzles (o Tablero). Correo de contacto: el que quieras mostrar.

## 4. Contenido de la app (formularios)

Respuestas sugeridas; revísalas antes de enviarlas.

| Formulario | Respuesta |
|---|---|
| Política de privacidad | URL pública de `store/privacidad.html` (sustituye antes `CORREO_DE_CONTACTO`) |
| Anuncios | No contiene anuncios |
| Acceso a la app | Todas las funciones están disponibles sin acceso especial |
| Clasificación de contenido | Cuestionario IARC: sin violencia, sin sexo, sin lenguaje malsonante, sin drogas; los sucesos "lotería" y "herencia" son premios del juego, **no** hay apuestas con dinero real. No hay compras dentro de la app. |
| Público objetivo | Recomendado: 13 años o más (elegir menores de 13 activa los requisitos del programa "Familias") |
| Seguridad de los datos | La app no recoge ni comparte datos |
| Apps de noticias / salud / finanzas / gobierno | No |

## 5. Prueba cerrada y producción

- Pruebas › **Prueba cerrada** › crea una pista, sube el `.aab` y añade a los testers
  (lo más cómodo: un Grupo de Google con sus correos). Comparte con ellos el enlace de participación.
- Si tu cuenta tiene el requisito: **12 testers durante 14 días seguidos**; después, en el panel,
  "Solicitar acceso a producción".
- Producción › Crear versión › subir el mismo `.aab` (o uno nuevo con `versionCode` mayor) › Revisar y publicar.

## Comprobaciones antes de cada versión

- `gradlew core:test` (reglas, idiomas, fuentes y niveles)
- Probar el APK de depuración en un móvil: `gradlew android:assembleDebug`
- Si cambias textos de chino, japonés, ruso o hindi: `python tools/generar_fuentes.py`
