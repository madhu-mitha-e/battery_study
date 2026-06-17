import 'package:sqflite/sqflite.dart';
import 'package:path/path.dart';
import '../models/battery_log.dart';

class DatabaseService {
  static final DatabaseService instance = DatabaseService._init();
  static Database? _database;

  DatabaseService._init();

  Future<Database> get database async {
    if (_database != null) return _database!;
    _database = await _initDB('battery_study.db');
    return _database!;
  }

  Future<Database> _initDB(String filePath) async {
    final dbPath = await getDatabasesPath();
    final path = join(dbPath, filePath);
    return await openDatabase(
      path,
      version: 2,
      onCreate: _createDB,
      onUpgrade: _upgradeDB,
    );
  }

  Future _createDB(Database db, int version) async {
    await db.execute('''
      CREATE TABLE battery_logs (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        userId TEXT NOT NULL,
        deviceBrand TEXT NOT NULL,
        deviceModel TEXT NOT NULL,
        osVersion TEXT NOT NULL,
        batterySoc INTEGER NOT NULL,
        batteryTemperatureC REAL NOT NULL,
        batteryVoltageMv INTEGER NOT NULL,
        chargingStatus TEXT NOT NULL,
        chargingSource TEXT NOT NULL,
        isCharging INTEGER NOT NULL,
        screenOn INTEGER NOT NULL DEFAULT 1,
        timestamp TEXT NOT NULL,
        ambientTemperatureC REAL NOT NULL,
        humidity REAL NOT NULL,
        cityName TEXT NOT NULL,
        isUploaded INTEGER NOT NULL DEFAULT 0
      )
    ''');
  }

  Future _upgradeDB(Database db, int oldVersion, int newVersion) async {
    if (oldVersion < 2) {
      await db.execute(
        'ALTER TABLE battery_logs ADD COLUMN screenOn INTEGER NOT NULL DEFAULT 1',
      );
    }
  }

  Future<int> insertLog(BatteryLog log) async {
    final db = await database;
    return await db.insert('battery_logs', log.toMap());
  }

  Future<List<BatteryLog>> getUnuploadedLogs() async {
    final db = await database;
    final maps = await db.query(
      'battery_logs',
      where: 'isUploaded = ?',
      whereArgs: [0],
    );
    return maps.map((map) => BatteryLog.fromMap(map)).toList();
  }

  Future<void> markAsUploaded(List<int> ids) async {
    final db = await database;
    await db.update(
      'battery_logs',
      {'isUploaded': 1},
      where: 'id IN (${ids.map((_) => '?').join(',')})',
      whereArgs: ids,
    );
  }

  Future<int> getTotalLogs() async {
    final db = await database;
    final result = await db.rawQuery(
      'SELECT COUNT(*) FROM battery_logs',
    );
    return Sqflite.firstIntValue(result) ?? 0;
  }

  Future<int> getUploadedLogs() async {
    final db = await database;
    final result = await db.rawQuery(
      'SELECT COUNT(*) FROM battery_logs WHERE isUploaded = 1',
    );
    return Sqflite.firstIntValue(result) ?? 0;
  }

  Future<List<BatteryLog>> getRecentLogs(int limit) async {
    final db = await database;
    final maps = await db.query(
      'battery_logs',
      orderBy: 'id DESC',
      limit: limit,
    );
    return maps.map((map) => BatteryLog.fromMap(map)).toList();
  }

  Future<void> close() async {
    final db = await database;
    db.close();
  }
}