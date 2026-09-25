package com.drklo.pomodoro.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ProjectEntity::class,
        DayStatEntity::class,
        PomodoroLogEntity::class,
        ActivitySessionEntity::class
    ],
    version = 4,
    // Schemas are exported to app/schemas and committed: without them migrations cannot be tested.
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun dayStatDao(): DayStatDao
    abstract fun pomodoroLogDao(): PomodoroLogDao
    abstract fun activitySessionDao(): ActivitySessionDao

    companion object {
        const val NAME = "pomodoro.db"

        private const val VERSION_2 = 2
        private const val VERSION_3 = 3
        private const val VERSION_4 = 4

        /** v2 adds the pomodoro_log table for statistics. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS pomodoro_log (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "projectId INTEGER NOT NULL, " +
                        "startEpochMs INTEGER NOT NULL, " +
                        "endEpochMs INTEGER NOT NULL, " +
                        "durationSeconds INTEGER NOT NULL, " +
                        "dayKey TEXT NOT NULL)"
                )
            }
        }

        /**
         * v3 makes deletion soft: a removed project keeps its row and gets an [archivedAt] stamp,
         * so the pomodoros it owns keep their name and colour in the reports. Nullable on purpose —
         * null means "active", and every existing row is active.
         */
        val MIGRATION_2_3 = object : Migration(VERSION_2, VERSION_3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE projects ADD COLUMN archivedAt INTEGER")
            }
        }

        /** v4 adds the real elapsed-time journal used by free timers and future weekly reports. */
        val MIGRATION_3_4 = object : Migration(VERSION_3, VERSION_4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS activity_session (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "name TEXT NOT NULL, " +
                        "kind TEXT NOT NULL, " +
                        "projectId INTEGER, " +
                        "startEpochMs INTEGER NOT NULL, " +
                        "endEpochMs INTEGER NOT NULL, " +
                        "durationSeconds INTEGER NOT NULL, " +
                        "dayKey TEXT NOT NULL)"
                )
            }
        }
    }
}
