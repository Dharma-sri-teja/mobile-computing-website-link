package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        NetworkMetricEntity::class,
        SwitchingHistoryEntity::class,
        PredictionLogEntity::class,
        ConnectionEventEntity::class,
        KnownWifiNetworkEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun networkMetricDao(): NetworkMetricDao
    abstract fun switchingHistoryDao(): SwitchingHistoryDao
    abstract fun predictionLogDao(): PredictionLogDao
    abstract fun connectionEventDao(): ConnectionEventDao
    abstract fun knownWifiNetworkDao(): KnownWifiNetworkDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "netswitch_telemetry.db"
                ).fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
