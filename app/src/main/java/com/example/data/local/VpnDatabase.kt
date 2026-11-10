package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room Database for VPN configurations.
 */
@Database(
    entities = [VpnConfig::class],
    version = 1,
    exportSchema = false
)
abstract class VpnDatabase : RoomDatabase() {

    abstract fun vpnConfigDao(): VpnConfigDao

    companion object {
        @Volatile
        private var INSTANCE: VpnDatabase? = null

        fun getDatabase(context: Context): VpnDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    VpnDatabase::class.java,
                    "vpn_config_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
