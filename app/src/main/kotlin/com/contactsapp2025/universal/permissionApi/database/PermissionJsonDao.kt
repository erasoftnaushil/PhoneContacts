package com.contactsapp2025.universal.permissionApi.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PermissionJsonDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PermissionJsonEntity)

    @Query("SELECT * FROM permission_json WHERE unique_user_id = :userId")
    suspend fun getByUserId(userId: String): PermissionJsonEntity?

    @Query("DELETE FROM permission_json WHERE unique_user_id = :userId")
    suspend fun delete(userId: String)
}
