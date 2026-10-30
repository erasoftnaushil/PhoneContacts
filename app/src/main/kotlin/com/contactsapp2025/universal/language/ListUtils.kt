package com.contactsapp2025.universal.language


import com.contactsapp2025.universal.R
import kotlin.collections.indices

class ListUtils {
    companion object {

        fun getLanguageList(): ArrayList<LanguageModel> {
            val languageCodes = arrayOf(
                "en", "es", "hi", "fr",
                "zh", "ar", "ru", "pt",
                "it", "bn", "de", "ja",
                "ur", "fa", "ms"
            )
            val language = arrayOf(
                "English", "Spanish", "Hindi", "French",
                "Chinese", "Arabic", "Russian", "Portuguese",
                "Italian", "Bengali", "German", "Japanese",
                "Urdu", "Persian", "Malaysian"
            )

            val languageFlags = intArrayOf(
                R.drawable.english, R.drawable.spanish, R.drawable.hindi, R.drawable.french,
                R.drawable.chinese, R.drawable.arabic, R.drawable.russian, R.drawable.portuhuese,
                R.drawable.italian, R.drawable.bengali, R.drawable.german, R.drawable.japanese,
                R.drawable.urdu, R.drawable.persian, R.drawable.malaysian
            )

            val list: ArrayList<LanguageModel> = kotlin.collections.ArrayList()

            for (i in languageCodes.indices) {
                list.add(LanguageModel(languageCodes[i], language[i], languageFlags[i]))
            }
            return list
        }
    }
}
