package com.contactsapp2025.universal.utils

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class SharedViewModel : ViewModel() {
    private val _clickEvent = MutableLiveData<Int>()
    val clickEvent: LiveData<Int> get() = _clickEvent

    // Function to trigger a click event
    fun onItemClick(imageResId: Int) {
        _clickEvent.value = imageResId
    }
}
