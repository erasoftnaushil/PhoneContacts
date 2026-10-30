package com.contactsapp2025.universal.activities

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.contactsapp2025.universal.R
import com.contactsapp2025.universal.extensions.applySystemBarsForTheme
import com.contactsapp2025.universal.extensions.config
import com.sdk.ads.Ads
import org.fossify.commons.extensions.isNetworkAvailable

class ScanningActivity : AppCompatActivity() {

    private var progressTimer: CountDownTimer? = null
    private var isProceeded = false
    private var isAdShown = false
    
    private var elapsedMillis: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!isNetworkAvailable() || config.hasReachedMainActivity) {
            proceedToMain()
            return
        }
        setContentView(R.layout.activity_scanning)
        applySystemBarsForTheme()

        startScanning()
    }

    private fun startScanning() {
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)
        val tvProgress = findViewById<TextView>(R.id.tvProgress)

        progressTimer = object : CountDownTimer(10000, 50) {
            override fun onTick(millisUntilFinished: Long) {
                elapsedMillis = 10000 - millisUntilFinished
                val progress = ((elapsedMillis * 100) / 10000).toInt()
                progressBar.progress = progress
                tvProgress.text = "$progress%"

                if (elapsedMillis >= 5000 && !isAdShown && !isProceeded) {
                    if (Ads.isInterstitialReady("interstitial_scanning")) {
                        isAdShown = true
                        showLoadedAd()
                    }
                }
            }

            override fun onFinish() {
                progressBar.progress = 100
                tvProgress.text = "100%"
                if (!isAdShown) {
                    proceedToMain()
                }
            }
        }.start()

        loadAd()
    }

    private fun loadAd() {
        Ads.loadInterstitial("interstitial_scanning")
    }

    private fun showLoadedAd() {
        Ads.showInterstitial(
            activity = this,
            placement = "interstitial_scanning",
            onDismissed = { proceedToMain() },
            onFailedToShow = { proceedToMain() }
        )
    }

    private fun proceedToMain() {
        if (!isProceeded) {
            isProceeded = true
            progressTimer?.cancel()
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    override fun onDestroy() {
        progressTimer?.cancel()
        super.onDestroy()
    }
}
