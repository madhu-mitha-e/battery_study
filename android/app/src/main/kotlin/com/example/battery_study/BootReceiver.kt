package com.example.battery_study

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d("BootReceiver", "Boot event: $action")

        val prefs = context.getSharedPreferences(
            "FlutterSharedPreferences", Context.MODE_PRIVATE
        )
        val onboardingDone = prefs.getBoolean("flutter.onboarding_done", false)
        val loggingActive = prefs.getBoolean("flutter.logging_active", true)

        if (!onboardingDone) {
            Log.d("BootReceiver", "Onboarding not done — skipping")
            return
        }

        if (!loggingActive) {
            Log.d("BootReceiver", "Logging paused — skipping")
            return
        }

        Log.d("BootReceiver", "Starting 24/7 foreground service after boot")
        AlarmReceiver.scheduleNextAlarm(context)
        val serviceIntent = Intent(context, LoggingService::class.java)
        context.startForegroundService(serviceIntent)
    }
}