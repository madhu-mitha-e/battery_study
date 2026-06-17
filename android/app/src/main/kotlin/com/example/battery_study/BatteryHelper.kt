package com.example.battery_study

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.util.Log

object BatteryHelper {

    data class BatteryData(
        val soc: Int,
        val temperature: Double,
        val voltage: Int,
        val chargingStatus: String,
        val chargingSource: String,
        val isCharging: Boolean,
        val screenOn: Boolean,
        val chargingCurrentMa: Double,
        val remainingCapacityMah: Double,
        val batteryHealthPercent: Double,
        val batteryHealthState: String,
        val deviceBrand: String,
        val deviceModel: String,
        val osVersion: String
    )

    fun getBatteryData(context: Context): BatteryData {
        val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = context.registerReceiver(null, intentFilter)

        val soc = batteryStatus?.getIntExtra(
            BatteryManager.EXTRA_LEVEL, -1) ?: -1

        val tempRaw = batteryStatus?.getIntExtra(
            BatteryManager.EXTRA_TEMPERATURE, -1000) ?: -1000
        val temperature = if (tempRaw == -1000) -1.0 else tempRaw / 10.0

        val voltage = batteryStatus?.getIntExtra(
            BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
        val status = batteryStatus?.getIntExtra(
            BatteryManager.EXTRA_STATUS, -1) ?: -1
        val plugged = batteryStatus?.getIntExtra(
            BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        val healthInt = batteryStatus?.getIntExtra(
            BatteryManager.EXTRA_HEALTH, -1) ?: -1

        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val chargingStatus = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "CHARGING"
            BatteryManager.BATTERY_STATUS_FULL -> "FULL"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "DISCHARGING"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "NOT_CHARGING"
            else -> "UNKNOWN"
        }

        val chargingSource = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "WIRELESS"
            else -> "NONE"
        }

        val batteryHealthState = when (healthInt) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "GOOD"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "OVERHEAT"
            BatteryManager.BATTERY_HEALTH_DEAD -> "DEAD"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "OVER_VOLTAGE"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "FAILURE"
            BatteryManager.BATTERY_HEALTH_COLD -> "COLD"
            else -> "UNKNOWN"
        }

        val powerManager = context.getSystemService(
            Context.POWER_SERVICE) as PowerManager
        val screenOn = powerManager.isInteractive

        val batteryManager = context.getSystemService(
            Context.BATTERY_SERVICE) as BatteryManager

        val currentNow = batteryManager.getLongProperty(
            BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val chargingCurrentMa = if (currentNow == Long.MIN_VALUE) -1.0
        else currentNow / 1000.0

        val chargeCounter = batteryManager.getLongProperty(
            BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        val remainingCapacityMah = if (chargeCounter == Long.MIN_VALUE) -1.0
        else chargeCounter / 1000.0

        val healthPercent = batteryManager.getIntProperty(
            BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val batteryHealthPercent = if (healthPercent == Integer.MIN_VALUE) -1.0
        else healthPercent.toDouble()

        val deviceBrand = Build.MANUFACTURER ?: "Unknown"
        val deviceModel = Build.MODEL ?: "Unknown"
        val osVersion = "Android ${Build.VERSION.RELEASE}"

        Log.d("BatteryHelper", "SoC:$soc% Temp:${temperature}°C " +
                "Current:${chargingCurrentMa}mA " +
                "Health:${batteryHealthPercent}% " +
                "HealthState:$batteryHealthState " +
                "Device:$deviceBrand $deviceModel $osVersion")

        return BatteryData(
            soc = soc,
            temperature = temperature,
            voltage = voltage,
            chargingStatus = chargingStatus,
            chargingSource = chargingSource,
            isCharging = isCharging,
            screenOn = screenOn,
            chargingCurrentMa = chargingCurrentMa,
            remainingCapacityMah = remainingCapacityMah,
            batteryHealthPercent = batteryHealthPercent,
            batteryHealthState = batteryHealthState,
            deviceBrand = deviceBrand,
            deviceModel = deviceModel,
            osVersion = osVersion
        )
    }
}