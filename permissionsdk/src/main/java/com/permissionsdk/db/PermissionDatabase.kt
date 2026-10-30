package com.permissionsdk.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.permissionsdk.model.PermissionEntity

@Database(entities = [PermissionEntity::class], version = 1, exportSchema = false)
abstract class PermissionDatabase : RoomDatabase() {
    abstract fun permissionDao(): PermissionDao

    companion object {
        @Volatile
        private var INSTANCE: PermissionDatabase? = null

        fun getDatabase(context: Context): PermissionDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PermissionDatabase::class.java,
                    "new_permission_database_v1"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
