package com.example.battery_study

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BatteryDatabase(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    getDatabasePath(context.applicationContext),
    null,
    7
) {

    companion object {
        private const val DB_NAME = "battery_study.db"
        val dateFormat = SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss", Locale.getDefault()
        )

        fun getDatabasePath(context: Context): String {
            val dir = context.getExternalFilesDir(null) ?: context.filesDir
            if (!dir.exists()) dir.mkdirs()
            val path = File(dir, DB_NAME).absolutePath
            Log.d("BatteryDatabase", "DB path: $path")
            return path
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS battery_logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                userId TEXT NOT NULL,
                deviceBrand TEXT NOT NULL DEFAULT '',
                deviceModel TEXT NOT NULL DEFAULT '',
                osVersion TEXT NOT NULL DEFAULT '',
                batterySoc INTEGER NOT NULL DEFAULT -1,
                batteryTemperatureC REAL NOT NULL DEFAULT -1,
                batteryVoltageMv INTEGER NOT NULL DEFAULT -1,
                chargingStatus TEXT NOT NULL DEFAULT 'UNKNOWN',
                chargingSource TEXT NOT NULL DEFAULT 'UNKNOWN',
                isCharging INTEGER NOT NULL DEFAULT -1,
                screenOn INTEGER NOT NULL DEFAULT -1,
                chargingCurrentMa REAL NOT NULL DEFAULT -1,
                remainingCapacityMah REAL NOT NULL DEFAULT -1,
                batteryHealthPercent REAL NOT NULL DEFAULT -1,
                batteryHealthState TEXT NOT NULL DEFAULT 'UNKNOWN',
                timestamp TEXT NOT NULL,
                ambientTemperatureC REAL NOT NULL DEFAULT -1,
                humidity REAL NOT NULL DEFAULT -1,
                cityName TEXT NOT NULL DEFAULT '',
                logSource TEXT NOT NULL DEFAULT 'UNKNOWN',
                isUploaded INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
        Log.d("BatteryDatabase", "Database created version 7")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS battery_logs")
        onCreate(db)
        Log.d("BatteryDatabase", "Upgraded to version $newVersion")
    }

    fun insertLog(
        userId: String,
        soc: Int,
        temperature: Double,
        voltage: Int,
        chargingStatus: String,
        chargingSource: String,
        isCharging: Boolean,
        screenOn: Boolean,
        cityName: String,
        ambientTemp: Double,
        humidity: Double,
        logSource: String,
        deviceBrand: String = "",
        deviceModel: String = "",
        osVersion: String = "",
        chargingCurrentMa: Double = -1.0,
        remainingCapacityMah: Double = -1.0,
        batteryHealthPercent: Double = -1.0,
        batteryHealthState: String = "UNKNOWN"
    ): Long {
        return try {
            val db = writableDatabase
            val timestamp = dateFormat.format(Date())
            val values = ContentValues().apply {
                put("userId", userId)
                put("deviceBrand", deviceBrand)
                put("deviceModel", deviceModel)
                put("osVersion", osVersion)
                put("batterySoc", soc)
                put("batteryTemperatureC", temperature)
                put("batteryVoltageMv", voltage)
                put("chargingStatus", chargingStatus)
                put("chargingSource", chargingSource)
                put("isCharging", if (isCharging) 1 else 0)
                put("screenOn", if (screenOn) 1 else 0)
                put("chargingCurrentMa", chargingCurrentMa)
                put("remainingCapacityMah", remainingCapacityMah)
                put("batteryHealthPercent", batteryHealthPercent)
                put("batteryHealthState", batteryHealthState)
                put("timestamp", timestamp)
                put("ambientTemperatureC", ambientTemp)
                put("humidity", humidity)
                put("cityName", cityName)
                put("logSource", logSource)
                put("isUploaded", 0)
            }
            val id = db.insert("battery_logs", null, values)
            Log.d("BatteryDatabase", "Inserted id=$id " +
                    "source=$logSource soc=$soc " +
                    "health=${batteryHealthPercent}% " +
                    "healthState=$batteryHealthState " +
                    "device=$deviceBrand $deviceModel")
            id
        } catch (e: Exception) {
            Log.e("BatteryDatabase", "Insert failed: ${e.message}")
            -1L
        }
    }

    fun getTotalCount(): Int {
        return try {
            val db = readableDatabase
            val cursor = db.rawQuery(
                "SELECT COUNT(*) FROM battery_logs", null)
            cursor.moveToFirst()
            val count = cursor.getInt(0)
            cursor.close()
            count
        } catch (e: Exception) {
            0
        }
    }

    fun getUploadedCount(): Int {
        return try {
            val db = readableDatabase
            val cursor = db.rawQuery(
                "SELECT COUNT(*) FROM battery_logs WHERE isUploaded = 1",
                null
            )
            cursor.moveToFirst()
            val count = cursor.getInt(0)
            cursor.close()
            count
        } catch (e: Exception) {
            0
        }
    }

    fun getUnuploadedLogs(): List<Map<String, Any>> {
        return try {
            val db = readableDatabase
            val cursor = db.query(
                "battery_logs", null,
                "isUploaded = ?", arrayOf("0"),
                null, null, "id ASC", "500"
            )
            val logs = mutableListOf<Map<String, Any>>()
            while (cursor.moveToNext()) {
                val log = mutableMapOf<String, Any>()
                for (i in 0 until cursor.columnCount) {
                    log[cursor.getColumnName(i)] = cursor.getString(i) ?: ""
                }
                logs.add(log)
            }
            cursor.close()
            logs
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun markAsUploaded(ids: List<Int>) {
        try {
            val db = writableDatabase
            val idString = ids.joinToString(",")
            db.execSQL(
                "UPDATE battery_logs SET isUploaded = 1 " +
                "WHERE id IN ($idString)"
            )
        } catch (e: Exception) {
            Log.e("BatteryDatabase", "markUploaded failed: ${e.message}")
        }
    }

    fun getRecentLogs(limit: Int): List<Map<String, Any>> {
        return try {
            val db = readableDatabase
            val cursor = db.query(
                "battery_logs", null,
                null, null, null, null,
                "id DESC", limit.toString()
            )
            val logs = mutableListOf<Map<String, Any>>()
            while (cursor.moveToNext()) {
                val log = mutableMapOf<String, Any>()
                for (i in 0 until cursor.columnCount) {
                    log[cursor.getColumnName(i)] = cursor.getString(i) ?: ""
                }
                logs.add(log)
            }
            cursor.close()
            logs
        } catch (e: Exception) {
            emptyList()
        }
    }
}