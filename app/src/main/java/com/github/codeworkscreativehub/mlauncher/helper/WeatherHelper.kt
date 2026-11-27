package com.github.codeworkscreativehub.mlauncher.helper

import android.content.Context
import androidx.lifecycle.LifecycleOwner

// Compatibility shim pending removal of legacy weather resources.
class WeatherHelper(context: Context, lifecycleOwner: LifecycleOwner, updateWeatherUi: (String) -> Unit) {
    fun getWeather() = Unit
}
