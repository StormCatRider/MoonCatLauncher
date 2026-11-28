package com.github.codeworkscreativehub.mlauncher.mooncat.privacy

import com.github.codeworkscreativehub.mlauncher.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionCatalogTest {
    // Inventory of the ProdDebug APK, as reported by `aapt dump permissions`.
    private val apkPermissions = listOf(
        "android.permission.EXPAND_STATUS_BAR", "android.permission.PACKAGE_USAGE_STATS",
        "android.permission.QUERY_ALL_PACKAGES", "com.android.alarm.permission.SET_ALARM",
        "android.permission.ACCESS_HIDDEN_PROFILES", "android.permission.REQUEST_DELETE_PACKAGES",
        "android.permission.USE_BIOMETRIC", "android.permission.READ_CONTACTS",
        "android.permission.SET_WALLPAPER", "android.permission.VIBRATE",
        "android.permission.BIND_APPWIDGET", "android.permission.BIND_REMOTEVIEWS",
        "android.permission.USE_FINGERPRINT", "android.permission.WAKE_LOCK",
        "android.permission.ACCESS_NETWORK_STATE", "android.permission.RECEIVE_BOOT_COMPLETED",
        "android.permission.FOREGROUND_SERVICE",
        "app.mooncat.launcher.debug.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
    )

    @Test fun everyShippedPermissionHasAKnownExplanation() {
        for (permission in apkPermissions) {
            val info = PermissionCatalog.describe(permission, "app.mooncat.launcher.debug")
            assertNotEquals(permission, R.string.mooncat_perm_unknown, info.title)
        }
    }

    @Test fun unknownPermissionsAreFlaggedAsSensitive() {
        val info = PermissionCatalog.describe("android.permission.CAMERA", "app.mooncat.launcher")
        assertEquals(PermissionLevel.SENSITIVE, info.level)
        assertEquals(R.string.mooncat_perm_unknown, info.title)
    }

    @Test fun appDefinedPermissionsAreInternal() {
        val info = PermissionCatalog.describe("app.mooncat.launcher.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION", "app.mooncat.launcher")
        assertEquals(PermissionLevel.INTERNAL, info.level)
    }

    @Test fun forbiddenCapabilitiesAreNeverCatalogued() {
        val names = PermissionCatalog.entries.map { it.name }.toSet()
        assertTrue(PermissionCatalog.FORBIDDEN.none { it in names })
        assertFalse(apkPermissions.any { it in PermissionCatalog.FORBIDDEN })
        assertEquals(names.size, PermissionCatalog.entries.size)
    }
}
