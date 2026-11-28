package com.github.codeworkscreativehub.mlauncher.mooncat.a11y

import android.content.Context
import android.provider.Settings
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import com.github.codeworkscreativehub.mlauncher.data.Prefs

/**
 * "Reduce animations": the user's choice in Moon Cat, or Android's own "Remove animations"
 * accessibility setting, which sets the animator duration scale to zero.
 */
object ReducedMotion {
    const val KEY = "MOONCAT_REDUCE_ANIMATIONS"

    fun userChoice(context: Context): Boolean = Prefs(context).prefsNormal.getBoolean(KEY, false)

    fun setUserChoice(context: Context, reduce: Boolean) {
        Prefs(context).prefsNormal.edit().putBoolean(KEY, reduce).apply()
    }

    fun systemRemovesAnimations(context: Context): Boolean =
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

    fun isOn(context: Context): Boolean = userChoice(context) || systemRemovesAnimations(context)

    /** A transition that does nothing, used in place of screen slide animations. */
    fun none(): Animation = AlphaAnimation(1f, 1f).apply { duration = 0 }
}
