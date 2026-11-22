# YT sin Shorts

App Android personal que abre **m.youtube.com** en un WebView y elimina los Shorts:

- Oculta los estantes de Shorts (inicio, suscripciones, búsqueda, canales), la pestaña
  Shorts de la barra inferior y cualquier video que enlace a `/shorts/`.
- Si igual llegás a un Short (un link compartido, por ejemplo), lo abre en el
  reproductor normal: `/shorts/ID` → `/watch?v=ID`.
- Pantalla completa con rotación a horizontal, botón atrás que navega el historial y
  links externos en el navegador.
- Login con Google: la app se presenta como Chrome móvil. Si Google lo bloquea, la app
  funciona igual sin cuenta.

## Instalar

1. En el celular, abrí la página de
   [Releases](https://github.com/juanmamix/no_shorts/releases/latest) y descargá el
   `yt-sin-shorts-N.apk` más reciente.
2. Abrilo y aceptá «Instalar apps desconocidas» para el navegador si te lo pide.
3. Para actualizar, instalá el APK nuevo encima: se conserva la sesión porque todos los
   builds se firman con la misma clave.

Cada push a `main` compila un APK firmado y lo publica como Release `build-N`
(`.github/workflows/build.yml`). La firma sale de los secrets `KEYSTORE_BASE64`,
`KEYSTORE_PASSWORD`, `KEY_ALIAS` y `KEY_PASSWORD`. El keystore nunca va al repo.

## Actualizar los selectores cuando YouTube cambie

Todo está en `app/src/main/assets/`, comentado:

- `noshorts.css`: selectores CSS que se ocultan con `display: none`. Es lo primero que
  hay que tocar.
- `noshorts.js`: el objeto `CONFIG` al principio lista los contenedores, títulos de
  estantes y pestañas/chips que se ocultan por texto («Shorts»). También contiene la
  redirección de `/shorts/` para la navegación interna de YouTube.

Para encontrar el selector nuevo, abrí m.youtube.com en Chrome de escritorio, activá la
vista de móvil en DevTools (Ctrl+Shift+M), inspeccioná el Short que aparece y agregá el
nombre de su elemento (por ejemplo `ytm-algo-renderer`) a la lista. Hacé push a `main`
y en unos minutos tenés el APK nuevo en Releases.

## Compilar localmente

Con Android Studio (o JDK 17 + Android SDK con platform 36):

```bash
./gradlew assembleDebug
```
