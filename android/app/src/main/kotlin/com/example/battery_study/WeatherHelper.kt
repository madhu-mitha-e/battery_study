package com.example.battery_study

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object WeatherHelper {

    data class WeatherData(
        val ambientTemperature: Double,
        val humidity: Double,
        val success: Boolean
    )

    private data class Coordinates(
        val lat: Double,
        val lon: Double
    )

    private val coordinateCache = mutableMapOf<String, Coordinates>()

    fun fetchWeather(cityName: String): WeatherData {
        return try {
            val coords = getCoordinates(cityName)
                ?: return WeatherData(0.0, 0.0, false)

            val urlString = "https://api.open-meteo.com/v1/forecast" +
                    "?latitude=${coords.lat}" +
                    "&longitude=${coords.lon}" +
                    "&current=temperature_2m,relative_humidity_2m" +
                    "&timezone=Asia/Kolkata"

            val response = fetchUrl(urlString)
                ?: return WeatherData(0.0, 0.0, false)

            val json = JSONObject(response)
            val current = json.getJSONObject("current")
            val temp = current.getDouble("temperature_2m")
            val humidity = current.getDouble("relative_humidity_2m")

            Log.d("WeatherHelper", "Weather — City:$cityName Temp:${temp}°C Humidity:${humidity}%")
            WeatherData(temp, humidity, true)

        } catch (e: Exception) {
            Log.e("WeatherHelper", "Error: ${e.message}")
            WeatherData(0.0, 0.0, false)
        }
    }

    private fun getCoordinates(cityName: String): Coordinates? {
        val key = cityName.lowercase().trim()

        coordinateCache[key]?.let { return it }

        return try {
            val encodedCity = java.net.URLEncoder.encode(cityName, "UTF-8")
            val urlString = "https://geocoding-api.open-meteo.com/v1/search" +
                    "?name=$encodedCity" +
                    "&count=1" +
                    "&language=en" +
                    "&format=json"

            val response = fetchUrl(urlString)
                ?: return null

            val json = JSONObject(response)
            val results = json.optJSONArray("results")
                ?: return null

            if (results.length() == 0) return null

            val first = results.getJSONObject(0)
            val lat = first.getDouble("latitude")
            val lon = first.getDouble("longitude")

            val coords = Coordinates(lat, lon)
            coordinateCache[key] = coords

            Log.d("WeatherHelper", "Geocoded $cityName → lat:$lat lon:$lon")
            coords

        } catch (e: Exception) {
            Log.e("WeatherHelper", "Geocoding error: ${e.message}")
            null
        }
    }

    private fun fetchUrl(urlString: String): String? {
        return try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.connect()

            if (connection.responseCode == 200) {
                connection.inputStream.bufferedReader().readText()
            } else {
                Log.e("WeatherHelper", "HTTP ${connection.responseCode} for $urlString")
                null
            }
        } catch (e: Exception) {
            Log.e("WeatherHelper", "Fetch error: ${e.message}")
            null
        }
    }
}