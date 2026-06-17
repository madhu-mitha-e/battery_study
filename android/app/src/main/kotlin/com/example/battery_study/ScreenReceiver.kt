package com.example.battery_study

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ScreenReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            try {
                when (action) {
                    Intent.ACTION_SCREEN_ON -> logEvent(context, "SCREEN_ON")
                    Intent.ACTION_SCREEN_OFF -> logEvent(context, "SCREEN_OFF")
                }
            } catch (e: Exception) {
                Log.e("ScreenReceiver", "Error: ${e.message}")
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

        val cachedAmbientTemp = prefs.getFloat(
            "cached_ambient_temp", -1.0f).toDouble()
        val cachedHumidity = prefs.getFloat(
            "cached_humidity", -1.0f).toDouble()

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
            ambientTemp = cachedAmbientTemp,
            humidity = cachedHumidity,
            logSource = eventType,
            chargingCurrentMa = data.chargingCurrentMa,
            remainingCapacityMah = data.remainingCapacityMah,
            batteryHealthPercent = data.batteryHealthPercent,
            batteryHealthState = data.batteryHealthState,
            deviceBrand = data.deviceBrand,
            deviceModel = data.deviceModel,
            osVersion = data.osVersion
        )
        Log.d("ScreenReceiver", "Logged $eventType " +
                "SoC:${data.soc}% " +
                "HealthState:${data.batteryHealthState}")
    }
}