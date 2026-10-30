package com.permissionsdk.db

import androidx.room.*
import com.permissionsdk.model.PermissionEntity

@Dao
interface PermissionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrUpdate(permission: PermissionEntity)

    @Query("SELECT * FROM permissions LIMIT 1")
    fun getPermissionData(): PermissionEntity?

    @Query("SELECT isSynced FROM permissions LIMIT 1")
    fun getIsSynced(): Boolean?
}
