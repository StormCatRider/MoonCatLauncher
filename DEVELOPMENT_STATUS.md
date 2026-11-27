# Estado de desarrollo

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

### Verificación

- La [ejecución de referencia de Foundation](https://github.com/StormCatRider/MoonCatLauncher/actions/runs/45932135168) pasó para `3670d798`, con ensamblado de ProdDebug, pruebas unitarias y comprobación del APK para Internet y ubicación precisa o aproximada.
- El [primer cambio de privacidad](https://github.com/StormCatRider/MoonCatLauncher/actions/runs/45933518117) pasó ensamblado, pruebas unitarias y verificación del APK ProdDebug para `074cda5d`.
- La [verificación ampliada](https://github.com/StormCatRider/MoonCatLauncher/actions/runs/45933969090) pasó para `caf3eb13`, con ensamblado, pruebas unitarias y las comprobaciones adicionales del APK.
- En un emulador API 37 sin red predeterminada se instaló `074cda5d` y Android reconoció el rol de inicio. La prueba de instalación limpia detectó que la bienvenida exigía acceso de uso para avanzar. El código se corrigió y el recorrido completo requiere volver a probarse con el APK actualizado.
- Para los cambios de Foundation se ejecutaron `git diff --check` y el análisis XML de los recursos modificados. La verificación del APK nuevo y las pruebas en dispositivo requieren resultados propios.

### Permisos observados en ProdDebug

El APK de `074cda5d` carece de permisos de Internet y de ubicación precisa, aproximada y en segundo plano. El inventario empaquetado incluye:

- Datos y perfiles de Android. `READ_CONTACTS`, `PACKAGE_USAGE_STATS`, `QUERY_ALL_PACKAGES` y `ACCESS_HIDDEN_PROFILES`.
- Integraciones del launcher. `REQUEST_DELETE_PACKAGES`, `EXPAND_STATUS_BAR`, `VIBRATE`, `SET_WALLPAPER`, `SET_ALARM`, `BIND_APPWIDGET` y `BIND_REMOTEVIEWS`.
- Biometría. `USE_BIOMETRIC` y `USE_FINGERPRINT`.
- Ejecución y compatibilidad. `ACCESS_NETWORK_STATE`, `FOREGROUND_SERVICE`, `RECEIVE_BOOT_COMPLETED`, `REORDER_TASKS`, `WAKE_LOCK` y el permiso interno para receptores dinámicos.

Falta revisar el origen y la necesidad de los permisos de ejecución para cada variante. También falta revisar el comportamiento de las capacidades opcionales con el acceso denegado.

### Implementación parcial

- El manifiesto fuente desactiva las copias de Android y declara reglas de exclusión. Falta confirmar el manifiesto empaquetado por canal y verificar la ejecución sin conexión.
- Los modelos de distribución, su validador y el almacén local existen. Las carpetas, pantallas y alternativas de presentación todavía requieren integración en la interfaz.
- El modelo de temas existe. El editor, la previsualización y el formato de intercambio validado siguen pendientes.

### Trabajo pendiente

- Terminar la revisión de dependencias y permisos, y verificar el arranque, el rol de launcher y los widgets sin conexión en Android 9 y una versión reciente.
- Integrar las carpetas y la distribución persistente de Moon Home. Continuar con Moon Spaces, Moon Search, Moon Studio, Moon Shield y Moon Focus.
- Implementar respaldos manuales cifrados y validar una restauración antes de sustituir datos. Completar accesibilidad, rendimiento y las pruebas beta antes de preparar APK y AAB de distribución.
- La cita de Acerca de queda a la espera del texto del propietario.
