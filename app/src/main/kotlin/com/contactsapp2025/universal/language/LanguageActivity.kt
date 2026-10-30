package com.contactsapp2025.universal.language

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import com.callsdk.CallSDKSettings
import com.contactsapp2025.universal.activities.MainActivity
import com.contactsapp2025.universal.activities.SimpleActivity
import com.contactsapp2025.universal.databinding.ActivityLanguageBinding
import com.contactsapp2025.universal.extensions.config
import com.contactsapp2025.universal.extensions.hideNavigationBar
import com.contactsapp2025.universal.extensions.showNavigationBar
import org.fossify.commons.extensions.isNetworkAvailable
import org.fossify.commons.extensions.isSystemInDarkMode
import org.fossify.commons.extensions.viewBinding

class LanguageActivity : SimpleActivity(), View.OnClickListener {

    private val binding by viewBinding(ActivityLanguageBinding::inflate)

    lateinit var adapter: LanguageAdapter

    override fun onResume() {
        super.onResume()

        val statusBarColor = if (isSystemInDarkMode()) {
            Color.BLACK
        } else {
            resources.getColor(org.fossify.commons.R.color.white)
        }
        updateStatusbarColor(statusBarColor)

        if (isNetworkAvailable()) {
            hideNavigationBar()
        } else {
            showNavigationBar()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        initView()
        setClick()

        binding.tapToContinue.isVisible = config.isFirstRun
    }

    private fun setClick() {
        binding.imgDone.setOnClickListener(this)
        binding.imgBack.setOnClickListener(this)
    }

    private fun initView() {
        adapter = LanguageAdapter()
        adapter.setSelectedLang(config.getLanguage() ?: "en")
        adapter.setItemList(ListUtils.getLanguageList())
        binding.rvLanguage.adapter = adapter
    }

    override fun onBackPressed() {
        if (config.isFirstRun) {
            gotoNext()
        } else {
            super.onBackPressed()
        }
        config.isFirstRun = false
    }

    override fun onClick(p0: View?) {
        when (p0) {
            binding.imgBack -> {
                onBackPressedDispatcher.onBackPressed()
                config.isFirstRun = false
            }

            binding.imgDone -> {
                if (config.isFirstRun) {
                    config.isFirstRun = false
                    gotoNext()
                } else {
                    gotoNext()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.adLayout.destroy()
    }

    private fun gotoNext() {
        val lang = adapter.getSelectedLanguage()
        config.setLanguage(lang)
        CallSDKSettings.setLanguage(lang)
        FunctionUtils.setLocale(this@LanguageActivity, config.getLanguage() ?: "en")
        if (config.getLanguage().isNullOrEmpty()) {
            config.setLanguage("en")
        }
        startActivity(Intent(this@LanguageActivity, MainActivity::class.java))
        finishAffinity()
    }
}
