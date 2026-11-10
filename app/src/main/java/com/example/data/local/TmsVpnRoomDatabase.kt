package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Main Room database for local TMS VPN storage.
 * Stores local user VPN configurations for offline access and local-first reliability.
 */
@Database(
    entities = [LocalVpnConfigEntity::class],
    version = 1,
    exportSchema = false
)
abstract class TmsVpnRoomDatabase : RoomDatabase() {

    abstract fun vpnConfigDao(): LocalVpnConfigDao

    companion object {
        @Volatile
        private var INSTANCE: TmsVpnRoomDatabase? = null

        fun getDatabase(context: Context): TmsVpnRoomDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TmsVpnRoomDatabase::class.java,
                    "tms_vpn_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
