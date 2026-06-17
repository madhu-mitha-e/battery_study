package com.example.battery_study

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ChargingReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d("ChargingReceiver", "Event: $action")
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            try {
                when (action) {
                    Intent.ACTION_POWER_CONNECTED -> {
                        logEvent(context, "CHARGING_STARTED")
                        startForegroundService(context)
                    }
                    Intent.ACTION_POWER_DISCONNECTED -> {
                        logEvent(context, "CHARGING_STOPPED")
                        stopForegroundService(context)
                    }
                    else -> logEvent(context, "BATTERY_EVENT")
                }
            } catch (e: Exception) {
                Log.e("ChargingReceiver", "Error: ${e.message}")
            }
        }
    }

    private fun logEvent(context: Context, eventType: String) {
        val prefs = context.getSharedPreferences(
            "FlutterSharedPreferences", Context.MODE_PRIVATE
        )
        val loggingActive = prefs.getBoolean("flutter.logging_active", true)
        if (!loggingActive) return

        val userId = prefs.getString("flutter.user_id", "UNKNOWN") ?: "UNKNOWN"
        val cityName = prefs.getString("flutter.city_name", "Chennai") ?: "Chennai"

        val data = BatteryHelper.getBatteryData(context)
        val weatherData = WeatherHelper.fetchWeather(cityName)

        if (weatherData.success) {
            prefs.edit()
                .putFloat("cached_ambient_temp",
                    weatherData.ambientTemperature.toFloat())
                .putFloat("cached_humidity",
                    weatherData.humidity.toFloat())
                .apply()
        }

        val ambientTemp = if (weatherData.success) weatherData.ambientTemperature
        else prefs.getFloat("cached_ambient_temp", -1.0f).toDouble()

        val humidity = if (weatherData.success) weatherData.humidity
        else prefs.getFloat("cached_humidity", -1.0f).toDouble()

        val db = BatteryDatabase(context)
        db.insertLog(
            userId = userId,
            soc = data.soc,
            temperature = data.temperature,
            voltage = data.voltage,
            chargingStatus = data.chargingStatus,
            chargingSource = data.chargingSource,
            isCharging = data.isCharging,
            screenOn = data.screenOn,
            cityName = cityName,
            ambientTemp = ambientTemp,
            humidity = humidity,
            logSource = eventType,
            chargingCurrentMa = data.chargingCurrentMa,
            remainingCapacityMah = data.remainingCapacityMah,
            batteryHealthPercent = data.batteryHealthPercent,
            batteryHealthState = data.batteryHealthState,
            deviceBrand = data.deviceBrand,
            deviceModel = data.deviceModel,
            osVersion = data.osVersion
        )
        Log.d("ChargingReceiver", "Logged $eventType " +
                "HealthState:${data.batteryHealthState}")
    }

    private fun startForegroundService(context: Context) {
        val serviceIntent = Intent(context, LoggingService::class.java)
        serviceIntent.action = "START"
        context.startForegroundService(serviceIntent)
    }

    private fun stopForegroundService(context: Context) {
        val serviceIntent = Intent(context, LoggingService::class.java)
        serviceIntent.action = "STOP"
        context.startService(serviceIntent)
    }
}