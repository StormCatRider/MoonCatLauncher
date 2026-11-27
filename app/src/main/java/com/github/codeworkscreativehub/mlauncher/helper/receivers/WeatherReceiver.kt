package com.github.codeworkscreativehub.mlauncher.helper.receivers

import android.content.Context
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// Temporary compatibility shim; no network or location access.
class WeatherReceiver(context: Context) {
    suspend fun getCurrentWeather(latitude: Double, longitude: Double): WeatherResponse? = null
    fun getWeatherEmoji(weatherCode: Int): String = ""
    suspend fun searchLocation(query: String): List<LocationResult> = emptyList()
    suspend fun loadWeatherForSavedLocation(latitude: Double, longitude: Double): WeatherResponse? = null
}

// --- 📦 Data Classes ---
@JsonClass(generateAdapter = true)
data class WeatherResponse(
    @field:Json(name = "current_weather")
    val currentWeather: CurrentWeather,

    @field:Json(name = "current_weather_units")
    val currentUnits: CurrentUnits,

    @field:Json(name = "timezone")
    val timezone: String
)

@JsonClass(generateAdapter = true)
data class GeocodingResponse(
    val results: List<LocationResult>?
)

@JsonClass(generateAdapter = true)
data class CurrentWeather(
    @field:Json(name = "temperature")
    val temperature: Double,

    @field:Json(name = "weathercode")
    val weatherCode: Int,
)

@JsonClass(generateAdapter = true)
data class CurrentUnits(
    @field:Json(name = "temperature")
    val temperatureUnit: String,

    @field:Json(name = "weathercode")
    val weatherCodeUnit: String,
)

@JsonClass(generateAdapter = true)
data class LocationResult(
    val name: String,
    val country: String,
    val latitude: Double,
    val longitude: Double,
    val admin1: String
) {
    var displayName = listOfNotNull(
        name,
        if (admin1.isNotBlank() &&
            !country.equals(admin1, ignoreCase = true) &&
            !country.contains(admin1, ignoreCase = true)
        ) admin1 else null,
        country
    ).joinToString(", ")

    var region = listOfNotNull(name, country).joinToString(", ")
}
