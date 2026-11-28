package com.github.codeworkscreativehub.mlauncher.mooncat.privacy

import com.github.codeworkscreativehub.mlauncher.R

/** How much a capability matters to the user's privacy. Basic view shows SENSITIVE and SPECIAL only. */
enum class PermissionLevel { SENSITIVE, SPECIAL, NORMAL, INTERNAL }

data class PermissionInfo(val name: String, val level: PermissionLevel, val title: Int, val reason: Int)

/** Plain-language explanation for every permission and special access the launcher can hold. */
object PermissionCatalog {
    const val USAGE_ACCESS = "special:usage_access"
    const val NOTIFICATION_ACCESS = "special:notification_access"
    const val ACCESSIBILITY = "special:accessibility"
    const val DEVICE_ADMIN = "special:device_admin"
    const val DEFAULT_HOME = "special:default_home"

    /** Capabilities Moon Cat must never request. The inspector verifies their absence at runtime. */
    val FORBIDDEN = setOf(
        "android.permission.INTERNET",
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.ACCESS_COARSE_LOCATION",
        "android.permission.ACCESS_BACKGROUND_LOCATION"
    )

    private val workManager = R.string.mooncat_perm_work_manager_reason

    val entries: List<PermissionInfo> = listOf(
        PermissionInfo(DEFAULT_HOME, PermissionLevel.SPECIAL, R.string.mooncat_perm_default_home, R.string.mooncat_perm_default_home_reason),
        PermissionInfo("android.permission.READ_CONTACTS", PermissionLevel.SENSITIVE, R.string.mooncat_perm_contacts, R.string.mooncat_perm_contacts_reason),
        PermissionInfo(USAGE_ACCESS, PermissionLevel.SPECIAL, R.string.mooncat_perm_usage, R.string.mooncat_perm_usage_reason),
        PermissionInfo(NOTIFICATION_ACCESS, PermissionLevel.SPECIAL, R.string.mooncat_perm_notifications, R.string.mooncat_perm_notifications_reason),
        PermissionInfo(ACCESSIBILITY, PermissionLevel.SPECIAL, R.string.mooncat_perm_accessibility, R.string.mooncat_perm_accessibility_reason),
        PermissionInfo(DEVICE_ADMIN, PermissionLevel.SPECIAL, R.string.mooncat_perm_device_admin, R.string.mooncat_perm_device_admin_reason),
        PermissionInfo("android.permission.QUERY_ALL_PACKAGES", PermissionLevel.SENSITIVE, R.string.mooncat_perm_query_packages, R.string.mooncat_perm_query_packages_reason),
        PermissionInfo("android.permission.ACCESS_HIDDEN_PROFILES", PermissionLevel.SENSITIVE, R.string.mooncat_perm_hidden_profiles, R.string.mooncat_perm_hidden_profiles_reason),
        PermissionInfo("android.permission.PACKAGE_USAGE_STATS", PermissionLevel.INTERNAL, R.string.mooncat_perm_usage, R.string.mooncat_perm_usage_reason),
        PermissionInfo("android.permission.REQUEST_DELETE_PACKAGES", PermissionLevel.NORMAL, R.string.mooncat_perm_uninstall, R.string.mooncat_perm_uninstall_reason),
        PermissionInfo("android.permission.EXPAND_STATUS_BAR", PermissionLevel.NORMAL, R.string.mooncat_perm_status_bar, R.string.mooncat_perm_status_bar_reason),
        PermissionInfo("com.android.alarm.permission.SET_ALARM", PermissionLevel.NORMAL, R.string.mooncat_perm_alarm, R.string.mooncat_perm_alarm_reason),
        PermissionInfo("android.permission.USE_BIOMETRIC", PermissionLevel.NORMAL, R.string.mooncat_perm_biometric, R.string.mooncat_perm_biometric_reason),
        PermissionInfo("android.permission.USE_FINGERPRINT", PermissionLevel.NORMAL, R.string.mooncat_perm_biometric, R.string.mooncat_perm_biometric_reason),
        PermissionInfo("android.permission.SET_WALLPAPER", PermissionLevel.NORMAL, R.string.mooncat_perm_wallpaper, R.string.mooncat_perm_wallpaper_reason),
        PermissionInfo("android.permission.VIBRATE", PermissionLevel.NORMAL, R.string.mooncat_perm_vibrate, R.string.mooncat_perm_vibrate_reason),
        PermissionInfo("android.permission.BIND_APPWIDGET", PermissionLevel.NORMAL, R.string.mooncat_perm_widgets, R.string.mooncat_perm_widgets_reason),
        PermissionInfo("android.permission.BIND_REMOTEVIEWS", PermissionLevel.NORMAL, R.string.mooncat_perm_widgets, R.string.mooncat_perm_widgets_reason),
        PermissionInfo("android.permission.WAKE_LOCK", PermissionLevel.NORMAL, R.string.mooncat_perm_background, workManager),
        PermissionInfo("android.permission.RECEIVE_BOOT_COMPLETED", PermissionLevel.NORMAL, R.string.mooncat_perm_background, workManager),
        PermissionInfo("android.permission.FOREGROUND_SERVICE", PermissionLevel.NORMAL, R.string.mooncat_perm_background, workManager),
        PermissionInfo("android.permission.ACCESS_NETWORK_STATE", PermissionLevel.NORMAL, R.string.mooncat_perm_network_state, R.string.mooncat_perm_network_state_reason)
    )

    private val byName = entries.associateBy { it.name }

    /** Permissions defined by this app itself (e.g. the dynamic receiver guard) are internal plumbing. */
    fun describe(permission: String, packageName: String): PermissionInfo =
        byName[permission] ?: if (permission.startsWith("$packageName.")) {
            PermissionInfo(permission, PermissionLevel.INTERNAL, R.string.mooncat_perm_internal, R.string.mooncat_perm_internal_reason)
        } else {
            PermissionInfo(permission, PermissionLevel.SENSITIVE, R.string.mooncat_perm_unknown, R.string.mooncat_perm_unknown_reason)
        }
}
