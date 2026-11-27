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

### Verificación

- La [ejecución de referencia de Foundation](https://github.com/StormCatRider/MoonCatLauncher/actions/runs/45932135168) pasó para `3670d798`, con ensamblado de ProdDebug, pruebas unitarias y comprobación del APK para Internet y ubicación precisa o aproximada.
- Para los cambios de Foundation se ejecutaron `git diff --check` y el análisis XML de los recursos modificados. La verificación del APK nuevo y las pruebas en dispositivo requieren resultados propios.

### Implementación parcial

- El manifiesto fuente desactiva las copias de Android y declara reglas de exclusión. Falta confirmar el manifiesto empaquetado por canal y verificar la ejecución sin conexión.
- Los modelos de distribución, su validador y el almacén local existen. Las carpetas, pantallas y alternativas de presentación todavía requieren integración en la interfaz.
- El modelo de temas existe. El editor, la previsualización y el formato de intercambio validado siguen pendientes.

### Trabajo pendiente

- Terminar la revisión de dependencias y permisos, y verificar el arranque, el rol de launcher y los widgets sin conexión en Android 9 y una versión reciente.
- Integrar las carpetas y la distribución persistente de Moon Home. Continuar con Moon Spaces, Moon Search, Moon Studio, Moon Shield y Moon Focus.
- Implementar respaldos manuales cifrados y validar una restauración antes de sustituir datos. Completar accesibilidad, rendimiento y las pruebas beta antes de preparar APK y AAB de distribución.
- La cita de Acerca de queda a la espera del texto del propietario.
