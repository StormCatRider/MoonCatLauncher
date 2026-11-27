# Estado de desarrollo

## Moon Home 0.2.0 en desarrollo

- Ajustes → Mira & Siente → Apariencia de las aplicaciones de inicio ofrece cuatro modos. Solo texto es el valor inicial. Las preferencias anteriores de iconos se conservan al actualizar. El modo Solo icono mantiene el nombre accesible de la aplicación y el modo monocromático usa escala de grises.
- La compilación local de ProdDebug y las 6 pruebas unitarias pasaron. En el emulador API 37 sin conexión se recorrieron los cuatro modos con Reloj y se comprobó que Solo icono conserva el nombre accesible y persiste tras reiniciar el proceso.
- En `7c7c1907` también se ensamblaron PlayDebug y BetaDebug. El análisis de los tres APK de depuración confirmó SDK mínimo 28, identificadores esperados, `allowBackup=false` y ausencia de Internet, ubicación y `REORDER_TASKS`.
- [GitHub Actions](https://github.com/StormCatRider/MoonCatLauncher/actions/runs/45936628887) confirmó ensamblado, pruebas y controles de privacidad para `7c7c1907`.
- `7c7c1907` generó también ProdRelease como APK y AAB de desarrollo sin firma. Se repitió la auditoría del APK, con el paquete `app.mooncat.launcher`, SDK mínimo 28 y correcciones de privacidad confirmadas.
- Falta extender los modos por pantalla, carpeta y aplicación e integrar las carpetas anidadas, el arrastre y los modos de apertura.
- El número de versión del paquete sigue en `0.1.0` mientras se completa Moon Home.

## 0.1.0 Foundation

La rama de trabajo es `feature/mooncat-foundation`. El proyecto conserva Android 9 como mínimo, el identificador `app.mooncat.launcher` y la licencia GPL-3.0 con las atribuciones de mLauncher.

### Cambios realizados

- Retirado el gestor heredado de fallos que registraba acciones del usuario, escribía informes en Descargas y ofrecía enviarlos a un destinatario externo. Android vuelve a gestionar los fallos.
- Retirados los fragmentos, rutas y preferencias activas del clima. También se eliminó el enlace que podía pasar coordenadas al navegador. Se conservó la integración de alarmas con el reloj de Xiaomi.
- Retirados los accesos a catálogos externos de temas y palabras. Se conservó la importación local.
- Retirada la copia general en JSON de los ajustes. La restauración heredada podía borrar preferencias antes de validar el archivo. El borrado manual sigue disponible en Ajustes, en Datos locales.
- Añadido el aviso sobre conexión independiente en el selector de widgets en español e inglés.
- Limitado el servicio opcional de accesibilidad a acciones globales y retirada su capacidad de leer el contenido de las ventanas. Su descripción explica qué acciones habilita.
- El envío de una consulta del cajón ya no abre Google Play cuando no se selecciona un resultado local. La búsqueda web explícita sigue abriendo el navegador.
- El control de CI comprueba también el identificador, el SDK mínimo, la exclusión de ubicación en segundo plano y la desactivación de las copias automáticas en el APK ProdDebug.
- La bienvenida permite continuar sin estadísticas de uso y ya no presenta una solicitud de ubicación. El servicio de accesibilidad sigue siendo opcional.
- Fragment Testing, AndroidX Test Core y el recurso de inactividad de Espresso salen del APK principal. Fragment se declara como dependencia directa con la versión que el APK de depuración ya usaba.

### Verificación

- La [ejecución de referencia de Foundation](https://github.com/StormCatRider/MoonCatLauncher/actions/runs/45932135168) pasó para `3670d798`, con ensamblado de ProdDebug, pruebas unitarias y comprobación del APK para Internet y ubicación precisa o aproximada.
- El [primer cambio de privacidad](https://github.com/StormCatRider/MoonCatLauncher/actions/runs/45933518117) pasó ensamblado, pruebas unitarias y verificación del APK ProdDebug para `074cda5d`.
- La [verificación ampliada](https://github.com/StormCatRider/MoonCatLauncher/actions/runs/45933969090) pasó para `caf3eb13`, con ensamblado, pruebas unitarias y las comprobaciones adicionales del APK.
- La [compilación de la bienvenida corregida](https://github.com/StormCatRider/MoonCatLauncher/actions/runs/45934609004) pasó para `1ef1181b`.
- La [verificación de la limpieza de dependencias](https://github.com/StormCatRider/MoonCatLauncher/actions/runs/45935115562) pasó para `cd4b763a`.
- La prueba de instalación limpia de `074cda5d` en API 37 detectó que la bienvenida exigía acceso de uso para avanzar. Con el APK de `cd4b763a`, el emulador completó la bienvenida con el acceso de uso sin conceder, el servicio de accesibilidad desactivado y ninguna red predeterminada. Android reconoció el rol de inicio, el gesto hacia arriba abrió el cajón y una consulta sin coincidencias permaneció en el cajón.
- La compilación local de `cd4b763a` ensambló ProdDebug y pasó las 6 pruebas unitarias. `aapt` confirmó el identificador `app.mooncat.launcher.debug`, SDK mínimo 28, SDK objetivo 37 y `allowBackup=false`.
- En el emulador API 37 sin red se configuró un gesto para abrir la página de widgets, se comprobó el aviso y se enlazó el reloj analógico con la autorización puntual de Android.
- `assembleProdRelease` y `bundleProdRelease` generaron el APK y el AAB de Foundation sin firma. En el APK, `aapt` confirmó `app.mooncat.launcher`, SDK mínimo 28 y ausencia de Internet, ubicación y copias automáticas.
- También se ejecutaron `git diff --check` y el análisis XML de los recursos modificados.

### Permisos observados en ProdDebug

El APK de `cd4b763a` carece de permisos de Internet y de ubicación precisa, aproximada y en segundo plano. El inventario empaquetado incluye:

- Datos y perfiles de Android. `READ_CONTACTS`, `PACKAGE_USAGE_STATS`, `QUERY_ALL_PACKAGES` y `ACCESS_HIDDEN_PROFILES`.
- Integraciones del launcher. `REQUEST_DELETE_PACKAGES`, `EXPAND_STATUS_BAR`, `VIBRATE`, `SET_WALLPAPER`, `SET_ALARM`, `BIND_APPWIDGET` y `BIND_REMOTEVIEWS`.
- Biometría. `USE_BIOMETRIC` y `USE_FINGERPRINT`.
- Ejecución y compatibilidad. `ACCESS_NETWORK_STATE`, `FOREGROUND_SERVICE`, `RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK` y el permiso interno para receptores dinámicos.

El informe de fusión atribuye `ACCESS_NETWORK_STATE`, `FOREGROUND_SERVICE`, `RECEIVE_BOOT_COMPLETED` y `WAKE_LOCK` a WorkManager, usado por el widget local de palabra del día. AndroidX Biometric declara `USE_FINGERPRINT`. Fragment Testing y AndroidX Test añadían `REORDER_TASKS`, ausente en el APK de `cd4b763a`.

### Implementación parcial

- ProdDebug supera la prueba básica sin conexión en API 37. La revisión de permisos abarca los tres APK de depuración y ProdRelease de Foundation. Falta la comprobación en Android 9.
- Los modelos de distribución, su validador y el almacén local existen. Las carpetas, pantallas y alternativas de presentación todavía requieren integración en la interfaz.
- El modelo de temas existe. El editor, la previsualización y el formato de intercambio validado siguen pendientes.

### Trabajo pendiente

- Auditar los APK de distribución de Play y Beta cuando se preparen y repetir la prueba de arranque, rol de inicio y widgets en Android 9 durante la verificación de compatibilidad.
- Integrar las carpetas y la distribución persistente de Moon Home. Continuar con Moon Spaces, Moon Search, Moon Studio, Moon Shield y Moon Focus.
- Implementar respaldos manuales cifrados y validar una restauración antes de sustituir datos. Completar accesibilidad, rendimiento y las pruebas beta antes de preparar APK y AAB de distribución.
- La cita de Acerca de queda a la espera del texto del propietario.
