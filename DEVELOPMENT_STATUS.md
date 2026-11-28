# Estado de desarrollo

Se retiraron scripts y automatizaciones del fork para Discord, Crowdin, changelog, borrado de releases y Fastlane, junto con los metadatos de tienda de mLauncher. Los formularios de incidencias enlazan al proyecto Moon Cat. Se conservan la licencia, los créditos, el historial y los temas y archivos de palabras que siguen siendo importables.

## Moon Home 0.2.0 en desarrollo

- Ajustes → Mira & Siente → Apariencia de las aplicaciones de inicio ofrece cuatro modos. Solo texto es el valor inicial. Las preferencias anteriores de iconos se conservan al actualizar. El modo Solo icono mantiene el nombre accesible de la aplicación y el modo monocromático usa escala de grises.
- La compilación local de ProdDebug y las 6 pruebas unitarias pasaron. En el emulador API 37 sin conexión se recorrieron los cuatro modos con Reloj y se comprobó que Solo icono conserva el nombre accesible y persiste tras reiniciar el proceso.
- En `7c7c1907` también se ensamblaron PlayDebug y BetaDebug. El análisis de los tres APK de depuración confirmó SDK mínimo 28, identificadores esperados, `allowBackup=false` y ausencia de Internet, ubicación y `REORDER_TASKS`.
- [GitHub Actions](https://github.com/StormCatRider/MoonCatLauncher/actions/runs/45936628887) confirmó ensamblado, pruebas y controles de privacidad para `7c7c1907`.
- `7c7c1907` generó también ProdRelease como APK y AAB de desarrollo sin firma. Se repitió la auditoría del APK, con el paquete `app.mooncat.launcher`, SDK mínimo 28 y correcciones de privacidad confirmadas.
- Mantener pulsada una posición del inicio permite crear una carpeta. El editor admite subcarpetas, renombrado y aplicaciones por perfil. El guardado local valida la distribución y evita borrar carpetas con contenido. Los favoritos existentes se conservan debajo de la carpeta de esa posición.
- `ProdDebug` y las 7 pruebas unitarias pasaron con la integración de carpetas. Una prueba también verifica el recorrido JSON de una carpeta anidada y la identidad de perfil.
- En el emulador API 37 sin red se creó Weekend → Tools → Reloj desde el inicio. Tras detener y volver a abrir el launcher, el árbol seguía disponible y Reloj se abrió desde la subcarpeta.
- El editor permite mover elementos entre carpetas de la misma pantalla y cambiar su orden. Las carpetas aceptan arrastrar un elemento sobre una subcarpeta o antes de una aplicación. Las pruebas unitarias verifican la conservación de descendientes y el rechazo de ciclos. Queda por probar el gesto de arrastre en un dispositivo.
- Las carpetas se abren dentro del inicio por defecto. Cada carpeta puede guardarse en modo emergente o de pantalla completa. Se comprobaron los tres modos en el emulador API 37 sin conexión. El modo de pantalla completa persistió tras reiniciar y la ventana ocupó los 1080 × 2400 píxeles del emulador. Pasaron `ProdDebug` y las 9 pruebas unitarias.
- Arrastrar un favorito sobre otro en el inicio crea una carpeta con ambos. También se admite soltar un favorito sobre una carpeta existente. Las referencias de origen se conservan para restaurar la posición anterior al quitar un favorito de la carpeta. En API 37 sin red se arrastró Reloj sobre Calendario, se verificó que los dos aparecieran una sola vez, se abrió Reloj tras reiniciar y se comprobó que volvió a su posición al quitarlo de la carpeta. Pasaron `ProdDebug` y las 10 pruebas unitarias.
- El menú de una posición del inicio permite ajustar la página y, si contiene un favorito, la aplicación. El editor de carpetas admite un ajuste de grupo y un ajuste por elemento. La prioridad es elemento, aplicación, carpeta más cercana, página y valor global. Cada ajuste admite volver a heredar. La selección por página utiliza la paginación actual del inicio.
- Pasaron `ProdDebug` y las 10 pruebas unitarias con la precedencia ampliada. En el emulador API 37 sin red, Reloj sustituyó el modo Solo icono de su página por Solo texto. Calendario heredó Solo icono y cambió a Solo texto al aplicar la preferencia de su carpeta, incluso tras reiniciar.
- Falta verificar en dispositivo el arrastre dentro de carpetas y completar la personalización restante de Moon Home.
- El número de versión del paquete sigue en `0.1.0` mientras se completa Moon Home.

## Respaldo cifrado (adelanto de 0.6.0)

- Ajustes → Avanzado → Datos locales ofrece crear y restaurar un respaldo cifrado. El archivo se elige con el selector de documentos de Android, sin permisos de almacenamiento.
- Formato `MCLB` versión 1: PBKDF2-HMAC-SHA256 con 600 000 iteraciones y sal aleatoria de 16 bytes, AES-256-GCM con nonce de 12 bytes y la cabecera autenticada. Contraseña mínima de 8 caracteres.
- Cada preferencia conserva su tipo. La restauración descifra y valida todo el contenido, incluida la distribución, antes de modificar datos, y sustituye las preferencias en un único `commit`.
- Borrar todos los datos ahora vacía también el almacén de carpetas y distribución, que antes se conservaba.
- 7 pruebas unitarias cubren ida y vuelta con tipos, contraseña incorrecta, manipulación de cabecera y cuerpo, truncado, versión no admitida, tamaño máximo, contraseña corta y contenido inválido. Pasaron las 17 pruebas y `ProdDebug`.
- En el emulador API 37 sin red se creó la carpeta Lunar y se respaldó. El archivo de 776 bytes no contenía texto legible. Después se borraron los datos, se comprobó que la distribución quedó vacía, se rechazó una contraseña incorrecta sin cambios y se restauró con la correcta. Lunar volvió tras el reinicio.
- La prueba detectó que el campo de contraseña mostraba el texto. Se corrigió y se verificó que queda oculto. La confirmación con la tecla Listo del teclado compila y pasa las pruebas, pero falta verificarla en un dispositivo.
- La bienvenida ya no afirma que la accesibilidad sea necesaria para continuar.

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
- Completar accesibilidad, rendimiento y las pruebas beta antes de preparar APK y AAB de distribución.
- La cita de Acerca de queda a la espera del texto del propietario.

## Temas (adelanto de 0.5.0)

- La importación de temas heredada escribía cualquier clave del archivo en los ajustes. Un archivo con una clave que no fuera de color podía guardar un entero donde la app espera otro tipo y provocar un cierre en cada arranque. Ahora se valida el archivo completo, solo se aceptan las claves de color conocidas con formato `#AARRGGBB` y se escribe en un único `commit`. La lectura se limita a 64 KiB.
- Ajustes → Avanzado → Theme Creations → Mis temas ofrece los temas Lunar, Claro y AMOLED, vista previa antes de aplicar, guardar el tema actual con nombre, duplicar, renombrar y eliminar. Admite hasta 50 temas con nombres únicos sin distinguir mayúsculas. Los temas guardados forman parte de las preferencias, así que entran en el respaldo cifrado y se borran con Borrar todos los datos.
- 8 pruebas unitarias nuevas cubren el formato, la exclusión de claves desconocidas, el rechazo completo de archivos inválidos, los presets y los límites de nombres. Pasaron las 25 pruebas y `ProdDebug`. Falta verificar la interfaz de Mis temas en un dispositivo.

## Logo

- El logo del propietario se conserva en `branding/logo-source.png`. `branding/generate-icons.ps1` genera el icono adaptativo, el logo de la app sin esquinas blancas y el icono de tienda de 512 px.
- El icono adaptativo usa la ilustración como capa de fondo y un vector del gato sobre la luna como capa monocroma para los iconos temáticos. Acerca de ya no usa el icono de mLauncher. `aapt` confirmó el icono adaptativo en el APK ProdDebug. Falta verlo en un dispositivo.

## Inspector de permisos (0.6.0)

- Ajustes → Avanzado → Permisos y privacidad muestra qué puede usar el launcher y por qué, con una explicación en español e inglés para cada permiso y acceso especial: inicio predeterminado, contactos, acceso de uso, notificaciones, accesibilidad y administrador del dispositivo.
- El estado se lee en el dispositivo a partir de la información del paquete instalado y del estado de cada acceso especial. Cada entrada configurable abre la pantalla de ajustes de Android correspondiente, porque el launcher no puede revocar sus propios permisos.
- La vista básica muestra los permisos sensibles y los accesos especiales. La vista avanzada muestra todos los permisos con su nombre técnico.
- Una línea comprueba en tiempo de ejecución que la instalación no solicita Internet ni ubicación. Los permisos sin explicación se marcan como sensibles.
- 4 pruebas unitarias verifican que cada permiso del APK ProdDebug tiene explicación y que no se catalogan capacidades prohibidas. Pasaron las 29 pruebas y `ProdDebug`.
- Falta verificar la interfaz en un dispositivo. El emulador API 37 local estaba en uso por otro proceso sin modo de solo lectura.

## Protección con el bloqueo de pantalla (0.6.0)

- El menú de edición de una carpeta permite protegerla con el bloqueo de pantalla (huella, rostro, PIN o patrón). Cada apertura de la carpeta o de sus subcarpetas pide confirmación. Mover un elemento a una carpeta protegida ya no la abre, y al salir del inicio se cierran las carpetas protegidas abiertas en línea. Activar y quitar la protección también piden confirmación.
- La distribución solo admite la marca de protección en carpetas. Los diseños anteriores se leen sin protección. Una prueba unitaria cubre ambos casos.
- Corrección: sin bloqueo de pantalla en el teléfono, la autenticación no hacía nada, así que con el bloqueo de ajustes activado o con aplicaciones bloqueadas el usuario quedaba fuera, por ejemplo tras quitar su PIN. Ahora la acción continúa con un aviso de que la protección está inactiva, porque sin bloqueo de pantalla Android no puede verificar a nadie.
- El bloqueo de ajustes heredado cubre la única ruta de navegación a Ajustes. Pasaron las 30 pruebas y `ProdDebug`. Falta verificar en un dispositivo.
