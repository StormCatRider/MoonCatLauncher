# Moon Cat Launcher

Minimalismo, privacidad y libertad. Moon Cat Launcher es un launcher Android en desarrollo con interfaz en español e inglés y compatibilidad desde Android 9 (API 28).

La rama `feature/mooncat-foundation` contiene la versión 0.1.0 en desarrollo. El [estado de desarrollo](DEVELOPMENT_STATUS.md) registra las funciones disponibles y las verificaciones realizadas. Los canales de distribución previstos son GitHub, F-Droid y Google Play.

La [política de privacidad](PRIVACY.md) describe el manejo local de datos y los permisos opcionales. Consulta [las especificaciones](docs/MOONCAT_SPECIFICATIONS.md) para conocer el alcance del proyecto.

## Compilar y probar

Instala el JDK 21 y el SDK de Android API 37. Configura `ANDROID_HOME` con la ruta del SDK y ejecuta desde la raíz de la rama de desarrollo.

```sh
./gradlew :app:testProdDebugUnitTest :app:assembleProdDebug
```

En Windows usa `gradlew.bat`. El APK de depuración se genera en `app/build/outputs/apk/prod/debug/`.

## Licencia y créditos

Moon Cat Launcher deriva de [mLauncher de CodeWorks Creative Hub](https://github.com/CodeWorksCreativeHub/mLauncher), que acredita a [Olauncher](https://github.com/tanujnotes/Olauncher) y [OlauncherCF](https://github.com/OlauncherCF/OlauncherCF). El código se distribuye bajo la [GNU General Public License v3.0](LICENSE).
