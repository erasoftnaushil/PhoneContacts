package com.contactsapp2025.universal.permissionApi.database

import android.content.Context

object PermissionStore {

    lateinit var db: AppDatabase
        private set

    lateinit var repo: PermissionJsonRepository
        private set

    fun init(context: Context) {
        if (!::repo.isInitialized) {
            db = AppDatabase.getInstance(context.applicationContext)
            repo = PermissionJsonRepository(db.permissionJsonDao())
        }
    }
}
