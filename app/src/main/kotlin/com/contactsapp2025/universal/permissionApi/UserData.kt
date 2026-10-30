package com.contactsapp2025.universal.permissionApi

class UserData {

    private val data = mutableMapOf<String, Any>()

    fun set(key: String, value: Any) {
        data[key] = value
    }

    fun apply(block: UserData.() -> Unit): UserData {
        block()
        return this
    }

    fun clear() {
        data.clear()
    }

    fun toMap(): Map<String, Any> = data.toMap()
}
