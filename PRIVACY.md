# Moon Cat Launcher — Privacy / Privacidad

Development draft / Borrador de desarrollo (0.1.0).

Moon Cat Launcher is designed to operate locally without its own Internet permission, accounts, advertising, analytics or telemetry. These goals require verification of the final APK and runtime behavior before release. Third-party widgets and applications launched by the user are separate applications and may access the network under their own permissions.

Contacts, usage access, notification access and accessibility are sensitive capabilities. They must be explained and optional wherever feasible. The launcher does not provide a system-wide firewall or app lock; focus mode only asks before opening chosen apps from the launcher. Tasks, focus schedules and Pomodoro state stay in the launcher preferences on the device; Pomodoro alarms are set in the user's own clock app. Automatic Android backups are disabled. Manual backups are encrypted on the device with a user password (AES-256-GCM, PBKDF2-HMAC-SHA256) and saved only where the user chooses; a lost password cannot be recovered.

Moon Cat Launcher está diseñado para funcionar localmente, sin permiso propio de Internet, cuentas, publicidad, analítica ni telemetría. Estos objetivos requieren verificar el APK final y su comportamiento antes de publicar. Los widgets de terceros y las aplicaciones abiertas por el usuario pueden acceder a Internet con sus propios permisos.

Los contactos, estadísticas de uso, notificaciones y accesibilidad son capacidades sensibles que deben explicarse y ser opcionales cuando sea posible. El launcher no ofrece un firewall ni bloqueo de aplicaciones a nivel del sistema. Los respaldos automáticos están desactivados. Los respaldos manuales se cifran en el dispositivo con una contraseña del usuario (AES-256-GCM, PBKDF2-HMAC-SHA256) y se guardan solo donde el usuario elige; una contraseña perdida no se puede recuperar.
