package com.github.codeworkscreativehub.mlauncher.ui

import android.app.WallpaperManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.graphics.createBitmap
import androidx.fragment.app.Fragment
import com.github.codeworkscreativehub.mlauncher.data.Constants
import com.github.codeworkscreativehub.mlauncher.data.Prefs
import com.github.codeworkscreativehub.mlauncher.helper.updateAllWidgets
import com.github.codeworkscreativehub.mlauncher.helper.utils.SystemBarObserver

open class BaseFragment : Fragment() {

    private val prefs: Prefs by lazy { Prefs(requireContext()) }

    private val systemBarObserver: SystemBarObserver by lazy { SystemBarObserver(prefs) }

    /** Screen transitions are skipped when the user asked to reduce animations. */
    override fun onCreateAnimation(transit: Int, enter: Boolean, nextAnim: Int): android.view.animation.Animation? {
        val context = context ?: return super.onCreateAnimation(transit, enter, nextAnim)
        return if (nextAnim != 0 && com.github.codeworkscreativehub.mlauncher.mooncat.a11y.ReducedMotion.isOn(context))
            com.github.codeworkscreativehub.mlauncher.mooncat.a11y.ReducedMotion.none()
        else super.onCreateAnimation(transit, enter, nextAnim)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Attach the observer; lazy properties will initialize here
        lifecycle.addObserver(systemBarObserver)

        updateAllWidgets(requireContext())

        val themeMode = when (prefs.appTheme) {
            Constants.Theme.Light -> AppCompatDelegate.MODE_NIGHT_NO
            Constants.Theme.Dark -> AppCompatDelegate.MODE_NIGHT_YES
            Constants.Theme.System -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(themeMode)
    }

    override fun onStart() {
        super.onStart()

        if (prefs.forceWallpaper) {
            val context = requireContext()
            val wallpaperManager = WallpaperManager.getInstance(context)
            val backgroundColor = prefs.backgroundColor

            val width = wallpaperManager.desiredMinimumWidth
            val height = wallpaperManager.desiredMinimumHeight

            val bitmap = createBitmap(width, height)
            bitmap.eraseColor(backgroundColor)

            // Home screen
            wallpaperManager.setBitmap(
                bitmap,
                null,
                true,
                WallpaperManager.FLAG_SYSTEM
            )

            // Lock screen (API 24+)
            wallpaperManager.setBitmap(
                bitmap,
                null,
                true,
                WallpaperManager.FLAG_LOCK
            )
        }
    }
}

