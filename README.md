<div align="center">
  <img src="https://raw.githubusercontent.com/StormCatRider/MoonCatLauncher/feature/mooncat-foundation/branding/logo-source.png" width="140" alt="Moon Cat Launcher">
  <h1>Moon Cat Launcher</h1>
  <p><b>Minimalismo, privacidad y libertad.</b><br>
  <i>Minimalism, privacy and freedom.</i></p>
  <p>
    <img src="https://img.shields.io/badge/Android-9%2B-C4B5FD?style=flat-square&logo=android&logoColor=white" alt="Android 9+">
    <img src="https://img.shields.io/badge/Kotlin-15182E?style=flat-square&logo=kotlin&logoColor=C4B5FD" alt="Kotlin">
    <img src="https://img.shields.io/badge/license-GPL--3.0-C4B5FD?style=flat-square" alt="GPL-3.0">
    <img src="https://img.shields.io/badge/estado-en%20desarrollo-15182E?style=flat-square" alt="En desarrollo">
  </p>
  <p><a href="#español">Español</a> · <a href="#english">English</a></p>
</div>

---

## Español

Moon Cat Launcher es un launcher para Android, minimalista y centrado en la privacidad, con interfaz en español e inglés. Está diseñado para funcionar en tu teléfono sin permiso propio de Internet, sin cuentas, sin publicidad, sin analítica y sin telemetría. Tus datos se quedan en tu dispositivo.

> **Estado:** versión 0.1.0 en desarrollo. El trabajo activo está en la rama [`feature/mooncat-foundation`](https://github.com/StormCatRider/MoonCatLauncher/tree/feature/mooncat-foundation); `main` se mantiene estable. Consulta el [estado de desarrollo](https://github.com/StormCatRider/MoonCatLauncher/blob/feature/mooncat-foundation/DEVELOPMENT_STATUS.md) para ver lo que ya funciona y lo que se ha verificado.

### Funciones

- **Moon Home:** inicio solo texto por defecto, o iconos originales, monocromos o solo iconos, con ajustes por página, carpeta y app. Carpetas anidadas con nombre, arrastrar y soltar, y apertura en línea, emergente o a pantalla completa.
- **Páginas y widgets:** varias páginas con nombre y número de apps propio; la tecla Inicio abre la página que elijas o la de widgets. Aviso de privacidad para widgets de terceros.
- **Cajón y búsqueda local:** vista en lista o cuadrícula, categorías automáticas y propias, y búsqueda de apps, contactos autorizados, notas, tareas y nombres de archivo en carpetas que tú autorices.
- **Temas:** claro, oscuro, AMOLED y temas propios con vista previa, guardar, duplicar, importar y exportar.
- **Productividad:** notas, tareas con fecha, calendario mensual, Pomodoro (con la alarma de tu app de reloj) y modo enfoque por sesión u horario.
- **Privacidad y seguridad:** inspector de permisos en lenguaje sencillo, protección biométrica de ajustes, carpetas y apps, apps ocultas, respaldos locales cifrados (AES-256-GCM) y sin copias automáticas a la nube.
- **Accesibilidad:** nombres accesibles, áreas táctiles de 48 dp y opción de reducir animaciones.

Moon Cat no es un firewall ni un bloqueo de apps a nivel del sistema: sus restricciones solo aplican dentro del launcher. Las apps y widgets de terceros funcionan con sus propios permisos.

### Compilar

Necesitas JDK 21 y el SDK de Android API 37, con `ANDROID_HOME` apuntando al SDK. Desde la rama de desarrollo:

```sh
./gradlew :app:testProdDebugUnitTest :app:assembleProdDebug
```

En Windows usa `gradlew.bat`. El APK queda en `app/build/outputs/apk/prod/debug/`.

### Hoja de ruta

0.1.0 Foundation → 0.2.0 Inicio y carpetas → 0.3.0 Páginas y widgets → 0.4.0 Cajón y búsqueda → 0.5.0 Temas → 0.6.0 Privacidad y seguridad → 0.7.0 Productividad → 0.8.0 Optimización → 0.9.0 Beta → **1.0.0**

Se distribuirá gratis, con donaciones voluntarias, en GitHub, F-Droid y Google Play.

---

## English

Moon Cat Launcher is a minimal, privacy-focused Android launcher in Spanish and English. It is designed to run without its own Internet permission, accounts, ads, analytics or telemetry. Your data stays on your device.

> **Status:** version 0.1.0 in development. Active work happens on the [`feature/mooncat-foundation`](https://github.com/StormCatRider/MoonCatLauncher/tree/feature/mooncat-foundation) branch; `main` stays stable.

**Highlights:** text-only home with optional icon modes; nested folders with drag and drop; named pages and a widget page; list/grid drawer with categories; local search across apps, authorized contacts, notes, tasks and file names; custom themes; notes, tasks, calendar, Pomodoro and focus mode; a plain-language permission inspector; biometric protection; encrypted local backups; reduced-motion option. Launcher restrictions are not a system-wide app lock, and third-party apps and widgets use their own permissions.

**Build:** JDK 21 and Android SDK API 37, then run `./gradlew :app:testProdDebugUnitTest :app:assembleProdDebug` from the development branch.

---

## Créditos y licencia · Credits and license

Moon Cat Launcher deriva de **[mLauncher](https://github.com/CodeWorksCreativeHub/mLauncher)** de CodeWorks Creative Hub, que a su vez acredita a [Olauncher](https://github.com/tanujnotes/Olauncher) y [OlauncherCF](https://github.com/OlauncherCF/OlauncherCF). Gracias a sus autores y colaboradores.

*Moon Cat Launcher is derived from [mLauncher](https://github.com/CodeWorksCreativeHub/mLauncher) by CodeWorks Creative Hub. Thanks to its authors and contributors.*

Distribuido bajo la [GNU General Public License v3.0](LICENSE).
