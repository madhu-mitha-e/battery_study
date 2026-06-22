package com.example.battery_study

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

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
                    // Upload on every foreground service cycle too —
                    // dual upload path: alarm + service, independent of each other
                    uploadPendingLogs()
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
        if (intent?.action == "STOP") {
            Log.d("LoggingService", "Stop action received")
            isRunning = false
            handler.removeCallbacks(logRunnable)
            unregisterScreenReceiver()
            releaseWakeLock()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

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
        Log.d("LoggingService", "Service destroyed — rescheduling alarm as fallback")
        AlarmReceiver.scheduleNextAlarm(applicationContext)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d("LoggingService", "Task removed (swiped away) — rescheduling alarm + restarting service")

        // Reschedule alarm FIRST — this fires before process dies, so alarm survives
        // even if the service restart below fails
        AlarmReceiver.scheduleNextAlarm(applicationContext)

        // Try to restart the service — succeeds if battery exemption is granted
        try {
            val restartIntent = Intent(applicationContext, LoggingService::class.java)
            startForegroundService(restartIntent)
        } catch (e: Exception) {
            Log.e("LoggingService", "Service restart after swipe failed: ${e.message}")
            // Alarm reschedule above already handles recovery
        }
    }

    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "BatteryStudy::LoggingWakeLock"
            )
            wakeLock?.acquire(24 * 60 * 60 * 1000L)
            Log.d("LoggingService", "WakeLock acquired")
        } catch (e: Exception) {
            Log.e("LoggingService", "WakeLock error: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let { if (it.isHeld) it.release() }
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
        val prefs = getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
        val loggingActive = prefs.getBoolean("flutter.logging_active", true)
        if (!loggingActive) return

        val userId = prefs.getString("flutter.user_id", "UNKNOWN") ?: "UNKNOWN"
        val cityName = prefs.getString("flutter.city_name", "Chennai") ?: "Chennai"

        val data = BatteryHelper.getBatteryData(this)
        val weatherData = WeatherHelper.fetchWeather(cityName)

        if (weatherData.success) {
            prefs.edit()
                .putFloat("cached_ambient_temp", weatherData.ambientTemperature.toFloat())
                .putFloat("cached_humidity", weatherData.humidity.toFloat())
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
        Log.d("LoggingService", "Logged SoC:${data.soc}% HealthState:${data.batteryHealthState}")
    }

    /**
     * Upload pending logs to server — called every 10 min from the foreground service timer.
     * This is the SECOND upload path (first is AlarmReceiver every 15 min).
     * Having two independent upload paths means internet-available data reaches
     * the server regardless of which path is alive at any given moment.
     */
    private fun uploadPendingLogs() {
        val db = BatteryDatabase(this)
        val logs = db.getUnuploadedLogs()

        if (logs.isEmpty()) {
            Log.d("LoggingService", "No pending logs to upload")
            return
        }

        Log.d("LoggingService", "Service uploading ${logs.size} pending logs")

        try {
            val logsArray = JSONArray()
            val idsToMark = mutableListOf<Int>()

            for (log in logs) {
                val obj = JSONObject()
                obj.put("userId", log["userId"] ?: "UNKNOWN")
                obj.put("deviceBrand", log["deviceBrand"] ?: "")
                obj.put("deviceModel", log["deviceModel"] ?: "")
                obj.put("osVersion", log["osVersion"] ?: "")
                obj.put("batterySoc", (log["batterySoc"] as? String)?.toIntOrNull() ?: -1)
                obj.put("batteryTemperatureC", (log["batteryTemperatureC"] as? String)?.toDoubleOrNull() ?: -1.0)
                obj.put("batteryVoltageMv", (log["batteryVoltageMv"] as? String)?.toIntOrNull() ?: -1)
                obj.put("chargingStatus", log["chargingStatus"] ?: "UNKNOWN")
                obj.put("chargingSource", log["chargingSource"] ?: "UNKNOWN")
                obj.put("isCharging", (log["isCharging"] as? String)?.toIntOrNull() ?: -1)
                obj.put("screenOn", (log["screenOn"] as? String)?.toIntOrNull() ?: -1)
                obj.put("chargingCurrentMa", (log["chargingCurrentMa"] as? String)?.toDoubleOrNull() ?: -1.0)
                obj.put("remainingCapacityMah", (log["remainingCapacityMah"] as? String)?.toDoubleOrNull() ?: -1.0)
                obj.put("batteryHealthPercent", (log["batteryHealthPercent"] as? String)?.toDoubleOrNull() ?: -1.0)
                obj.put("batteryHealthState", log["batteryHealthState"] ?: "UNKNOWN")
                obj.put("timestamp", log["timestamp"] ?: "")
                obj.put("ambientTemperatureC", (log["ambientTemperatureC"] as? String)?.toDoubleOrNull() ?: -1.0)
                obj.put("humidity", (log["humidity"] as? String)?.toDoubleOrNull() ?: -1.0)
                obj.put("cityName", log["cityName"] ?: "")
                obj.put("logSource", log["logSource"] ?: "UNKNOWN")
                logsArray.put(obj)
                (log["id"] as? String)?.toIntOrNull()?.let { idsToMark.add(it) }
            }

            val payload = JSONObject()
            payload.put("logs", logsArray)

            // 60s timeouts — handles Render free tier cold starts (30-50s to wake)
            val client = OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()

            val body = payload.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("https://battery-backend-t82v.onrender.com/logs")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    db.markAsUploaded(idsToMark)
                    Log.d("LoggingService", "Service uploaded ${idsToMark.size} logs successfully")
                } else {
                    Log.e("LoggingService", "Service upload failed: HTTP ${response.code}")
                }
            }
        } catch (e: Exception) {
            Log.e("LoggingService", "Service upload exception: ${e.message}")
        }
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