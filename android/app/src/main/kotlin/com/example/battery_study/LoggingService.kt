package com.example.battery_study

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LoggingService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val logInterval = 10 * 60 * 1000L
    private var isRunning = false
    private var screenReceiver: ScreenReceiver? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val logRunnable = object : Runnable {
        override fun run() {
            if (isRunning) {
                CoroutineScope(Dispatchers.IO).launch {
                    logBatteryData()
                }
                handler.postDelayed(this, logInterval)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (isRunning) {
            Log.d("LoggingService", "Already running — ignoring duplicate start")
            return START_STICKY
        }

        Log.d("LoggingService", "Starting 24/7 foreground service")
        startForeground(NOTIFICATION_ID, buildNotification())
        isRunning = true
        handler.post(logRunnable)
        registerScreenReceiver()

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        handler.removeCallbacks(logRunnable)
        unregisterScreenReceiver()
        releaseWakeLock()
        Log.d("LoggingService", "Service destroyed — scheduling alarm restart fallback")
        AlarmReceiver.scheduleNextAlarm(applicationContext)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d("LoggingService", "Task removed — restarting service")
        val restartIntent = Intent(applicationContext, LoggingService::class.java)
        startForegroundService(restartIntent)
    }

    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "BatteryStudy::LoggingWakeLock"
            )
            wakeLock?.acquire(24 * 60 * 60 * 1000L) // 24h max, refreshed on restart
            Log.d("LoggingService", "WakeLock acquired")
        } catch (e: Exception) {
            Log.e("LoggingService", "WakeLock error: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) it.release()
            }
            wakeLock = null
        } catch (e: Exception) {
            Log.e("LoggingService", "WakeLock release error: ${e.message}")
        }
    }

    private fun registerScreenReceiver() {
        try {
            if (screenReceiver != null) return
            screenReceiver = ScreenReceiver()
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            }
            registerReceiver(screenReceiver, filter)
            Log.d("LoggingService", "ScreenReceiver registered")
        } catch (e: Exception) {
            Log.e("LoggingService", "ScreenReceiver error: ${e.message}")
        }
    }

    private fun unregisterScreenReceiver() {
        try {
            screenReceiver?.let {
                unregisterReceiver(it)
                screenReceiver = null
            }
        } catch (e: Exception) {
            Log.e("LoggingService", "Unregister error: ${e.message}")
        }
    }

    private fun logBatteryData() {
        val prefs = getSharedPreferences(
            "FlutterSharedPreferences", Context.MODE_PRIVATE
        )
        val loggingActive = prefs.getBoolean("flutter.logging_active", true)
        if (!loggingActive) return

        val userId = prefs.getString("flutter.user_id", "UNKNOWN") ?: "UNKNOWN"
        val cityName = prefs.getString("flutter.city_name", "Chennai") ?: "Chennai"

        val data = BatteryHelper.getBatteryData(this)
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

        val db = BatteryDatabase(this)
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
            logSource = "FOREGROUND_SERVICE",
            chargingCurrentMa = data.chargingCurrentMa,
            remainingCapacityMah = data.remainingCapacityMah,
            batteryHealthPercent = data.batteryHealthPercent,
            batteryHealthState = data.batteryHealthState,
            deviceBrand = data.deviceBrand,
            deviceModel = data.deviceModel,
            osVersion = data.osVersion
        )
        Log.d("LoggingService", "Logged SoC:${data.soc}% " +
                "HealthState:${data.batteryHealthState}")
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Battery Study",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Battery research logging — running 24/7"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Battery Study")
            .setContentText("Logging battery data for research")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "battery_study_channel"
        private const val NOTIFICATION_ID = 1001
    }
}