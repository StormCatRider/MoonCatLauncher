package com.github.codeworkscreativehub.mlauncher.mooncat.privacy

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.app.NotificationManagerCompat
import com.github.codeworkscreativehub.common.getLocalizedString
import com.github.codeworkscreativehub.mlauncher.R
import com.github.codeworkscreativehub.mlauncher.helper.hasUsageAccessPermission
import com.github.codeworkscreativehub.mlauncher.helper.ismlauncherDefault
import com.github.codeworkscreativehub.mlauncher.helper.receivers.DeviceAdmin
import com.google.android.material.dialog.MaterialAlertDialogBuilder

enum class PermissionState { GRANTED, NOT_GRANTED, INSTALL_TIME }

data class InspectedPermission(val info: PermissionInfo, val state: PermissionState, val settings: Intent?)

/**
 * Local permission inspector. Reads this installation's own package info and special-access
 * state on the device; nothing leaves the phone. The launcher cannot revoke its own permissions,
 * so each entry opens the matching Android settings screen instead.
 */
class PermissionInspector(private val context: Context) {
    private val packageName = context.packageName

    private fun packageInfo(): PackageInfo =
        context.packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)

    private fun appDetails() = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))

    private fun state(granted: Boolean) = if (granted) PermissionState.GRANTED else PermissionState.NOT_GRANTED

    private fun accessibilityEnabled(): Boolean =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            .orEmpty().split(':').any { it.startsWith("$packageName/") }

    private fun deviceAdminActive(): Boolean =
        context.getSystemService(DevicePolicyManager::class.java)
            ?.isAdminActive(ComponentName(context, DeviceAdmin::class.java)) == true

    fun inspect(): List<InspectedPermission> {
        val info = packageInfo()
        val special = listOf(
            InspectedPermission(PermissionCatalog.describe(PermissionCatalog.DEFAULT_HOME, packageName),
                state(ismlauncherDefault(context)), Intent(Settings.ACTION_HOME_SETTINGS)),
            InspectedPermission(PermissionCatalog.describe(PermissionCatalog.USAGE_ACCESS, packageName),
                state(hasUsageAccessPermission(context)), Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)),
            InspectedPermission(PermissionCatalog.describe(PermissionCatalog.NOTIFICATION_ACCESS, packageName),
                state(packageName in NotificationManagerCompat.getEnabledListenerPackages(context)),
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)),
            InspectedPermission(PermissionCatalog.describe(PermissionCatalog.ACCESSIBILITY, packageName),
                state(accessibilityEnabled()), Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)),
            InspectedPermission(PermissionCatalog.describe(PermissionCatalog.DEVICE_ADMIN, packageName),
                state(deviceAdminActive()), Intent(Settings.ACTION_SECURITY_SETTINGS))
        )
        val requested = info.requestedPermissions.orEmpty()
        val flags = info.requestedPermissionsFlags ?: IntArray(requested.size)
        val declared = requested.mapIndexed { index, name ->
            val catalogued = PermissionCatalog.describe(name, packageName)
            val granted = flags.getOrElse(index) { 0 } and PackageInfo.REQUESTED_PERMISSION_GRANTED != 0
            val runtime = name == "android.permission.READ_CONTACTS"
            InspectedPermission(
                catalogued,
                if (runtime) state(granted) else if (granted) PermissionState.INSTALL_TIME else PermissionState.NOT_GRANTED,
                if (runtime) appDetails() else null
            )
        }
        return special + declared
    }

    /** Verified from this installation's manifest, not assumed from the source code. */
    fun forbiddenRequested(): List<String> =
        packageInfo().requestedPermissions.orEmpty().filter { it in PermissionCatalog.FORBIDDEN }

    fun show(advanced: Boolean = false) {
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val all = inspect()
        val visible = if (advanced) all else all.filter {
            it.info.level == PermissionLevel.SENSITIVE || it.info.level == PermissionLevel.SPECIAL
        }
        val list = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), dp(8))
        }
        val forbidden = forbiddenRequested()
        list.addView(TextView(context).apply {
            text = if (forbidden.isEmpty()) getLocalizedString(R.string.mooncat_perm_offline_verified)
            else getLocalizedString(R.string.mooncat_perm_offline_failed).format(forbidden.joinToString())
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 0, 0, dp(12))
        })
        val seen = mutableSetOf<Int>()
        for (item in visible) {
            // Basic view merges entries that share one explanation (e.g. biometric and fingerprint).
            if (!advanced && !seen.add(item.info.title)) continue
            val status = getLocalizedString(
                when (item.state) {
                    PermissionState.GRANTED -> R.string.mooncat_perm_state_granted
                    PermissionState.NOT_GRANTED -> R.string.mooncat_perm_state_not_granted
                    PermissionState.INSTALL_TIME -> R.string.mooncat_perm_state_install
                }
            )
            list.addView(TextView(context).apply {
                text = "${getLocalizedString(item.info.title)} · $status"
                setTypeface(typeface, Typeface.BOLD)
                setPadding(0, dp(10), 0, dp(2))
            })
            list.addView(TextView(context).apply {
                text = buildString {
                    append(getLocalizedString(item.info.reason))
                    if (advanced && !item.info.name.startsWith("special:")) append("\n").append(item.info.name)
                    if (item.settings != null) append("\n").append(getLocalizedString(R.string.mooncat_perm_open_settings))
                }
                alpha = 0.8f
                minHeight = dp(24)
                item.settings?.let { intent ->
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        try {
                            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        } catch (_: Exception) {
                            context.startActivity(appDetails().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    }
                }
            })
        }
        list.addView(TextView(context).apply {
            text = getLocalizedString(R.string.mooncat_perm_footer)
            alpha = 0.7f
            setPadding(0, dp(16), 0, 0)
        })
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.mooncat_perm_title)
            .setView(ScrollView(context).apply { addView(list) })
            .setNeutralButton(if (advanced) R.string.mooncat_perm_basic else R.string.mooncat_perm_advanced) { _, _ -> show(!advanced) }
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }
}
