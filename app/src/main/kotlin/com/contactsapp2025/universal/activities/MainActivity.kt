package com.contactsapp2025.universal.activities

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.graphics.Color
import android.graphics.drawable.Icon
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.content.res.ColorStateList
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import androidx.activity.addCallback
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.viewpager.widget.ViewPager
import com.contactsapp2025.universal.BuildConfig
import com.contactsapp2025.universal.R
import com.contactsapp2025.universal.adapters.ViewPagerAdapter
import com.contactsapp2025.universal.databinding.ActivityMainBinding
import com.contactsapp2025.universal.dialogs.ChangeSortingDialog
import com.contactsapp2025.universal.dialogs.FilterContactSourcesDialog
import com.contactsapp2025.universal.extensions.*
import com.contactsapp2025.universal.fragments.FavoritesFragment
import com.contactsapp2025.universal.fragments.MyViewPagerFragment
import com.contactsapp2025.universal.helpers.ALL_TABS_MASK
import com.contactsapp2025.universal.helpers.Config
import com.contactsapp2025.universal.helpers.PermissionsHelper.isPermissionGranted
import com.contactsapp2025.universal.helpers.tabsList
import com.contactsapp2025.universal.interfaces.RefreshContactsListener
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.messaging.FirebaseMessaging
import com.jksol.notificationsdk.NotificationSdk
import com.sdk.ads.Ads
import com.sdk.ads.core.callback.AdLifecycleListener
import com.sdk.ads.core.model.AdEvent
import org.fossify.commons.databases.ContactsDatabase
import org.fossify.commons.dialogs.ChangeViewTypeDialog
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.*
import org.fossify.commons.models.RadioItem
import org.fossify.commons.models.contacts.Contact
import java.util.Arrays
import java.util.Locale
import androidx.core.view.isEmpty
import androidx.core.view.size
import androidx.core.view.get
import com.contactsapp2025.universal.helpers.ContactFeatureNotificationEngine
import com.contactsapp2025.universal.helpers.ContactFeatureNotificationHelper
import com.contactsapp2025.universal.helpers.HomeThemeHelper.createBottomNavBarBackground
import com.contactsapp2025.universal.helpers.HomeThemeHelper.createOvalBackground
import com.contactsapp2025.universal.helpers.HomeThemeHelper.createRoundedSquareBackground
import com.contactsapp2025.universal.helpers.HomeThemeHelper.dpToPx
import com.contactsapp2025.universal.helpers.HomeThemeHelper.getPalette
import com.contactsapp2025.universal.helpers.LOCATION_FAVORITES_TAB
import com.contactsapp2025.universal.helpers.OnboardingNotificationEngine

class MainActivity : SimpleActivity(), RefreshContactsListener {
    private var werePermissionsHandled = false
    private var isFirstResume = true
    private var isGettingContacts = false
    private var isBannerAdLoaded = false

    private var storedShowContactThumbnails = false
    private var storedShowPhoneNumbers = false
    private var storedStartNameWithSurname = false
    private var storedFontSize = 0
    private var storedShowTabs = 0
    private val binding by viewBinding(ActivityMainBinding::inflate)
    private val REQUEST_CODE_OVERLAY_PERMISSION = 101
    lateinit var dialog: Dialog

    companion object {
        var isActivityAlive = false
    }

    override fun attachBaseContext(newBase: Context) {
        val languageCode = Config(newBase).getLanguage()
        if (!languageCode.isNullOrEmpty()) {
            val locale = Locale(languageCode)
            Locale.setDefault(locale)
            val configuration = newBase.resources.configuration
            configuration.setLocale(locale)
            configuration.setLayoutDirection(locale)
            super.attachBaseContext(newBase.createConfigurationContext(configuration))
        } else {
            super.attachBaseContext(newBase)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        isActivityAlive = true

        // ðŸ”¥ Force PAN â€“ this overrides Fossify / edge-to-edge behavior
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
        )

        val languageCode = config.getLanguage() // Use the existing config instance
        if (!languageCode.isNullOrEmpty()) {
            val locale = Locale(languageCode)
            Locale.setDefault(locale)
            val configuration = resources.configuration
            configuration.setLocale(locale)
            configuration.setLayoutDirection(locale)
            resources.updateConfiguration(configuration, resources.displayMetrics)
        }
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        hideNavigationBar()
        dismissActiveNotifications()
        Log.e("AAA", "onCreate: Home ")
        appLaunched(BuildConfig.APPLICATION_ID)
        config.hasReachedMainActivity = true
        Log.d("OnboardingReminder", "MainActivity created. Setting isOnboardingCompleted to true.")
        OnboardingNotificationEngine.completeOnboarding(this, "app")
        ContactFeatureNotificationEngine.start(this)

//        NotificationSdk.fireTestFcmNotification(this)
        
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w("FCMToken", "Fetching FCM registration token failed", task.exception)
                return@addOnCompleteListener
            }
            val token = task.result
            Log.d("FCMToken", "FCM Token on startup: $token")
        }

        // update common fields in permission API
//        updateCommonFields(this)

        setupOptionsMenu()
        refreshMenuItems()
        updateMaterialActivityViews(binding.mainCoordinator, binding.mainHolder, useTransparentNavigation = false, useTopSearchMenu = true)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.mainTabsHolder) { v, insets ->
            v.setPadding(0, 0, 0, 0)
            insets
        }
        storeStateVariables()
        setupTabs()
        setupTabColors()
        Ads.requestConsent(this)
        checkContactPermissions()
        loadBannerAd()

        onBackPressedDispatcher.addCallback(this) {
            // Handle back press here
            onBackPressed1()
        }



        if (isNetworkAvailable()) {
            val oneDayMillis = 24 * 60 * 60 * 1000
            if (System.currentTimeMillis() - config.lastFetched >= oneDayMillis || config.getAppList().isEmpty()) {
                fetchRemoteConfigData()
            }
        }
        dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(false)

        runOnUiThread {
            checkAllPermissionsAndShowMessage()
        }
        binding.enableOverlay.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (!isPermissionGranted(this, Manifest.permission.POST_NOTIFICATIONS)) {
                    // Step 1: About to request notification permission
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 300)
                    // Step 2: System dialog opens
                }
            } else {
                binding.rlOverlay.visibility = View.GONE
            }
        }

    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
         if (requestCode == 300) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            ) {
                if (
                    isPermissionGranted(this, Manifest.permission.POST_NOTIFICATIONS)
                ) {
                    binding.rlOverlay.visibility = View.GONE
                }
            } else {
                if (!shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                    openAppSettings()
                }
            }
        }
    }

    private fun checkAllPermissionsAndShowMessage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (
                isPermissionGranted(this, Manifest.permission.POST_NOTIFICATIONS)
            ) {
                binding.rlOverlay.visibility = View.GONE
            } else {
                binding.rlOverlay.visibility = View.VISIBLE
            }
        }
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        intent.data = "package:$packageName".toUri()
        startActivityForResult(intent, 123)
    }

    private fun loadBannerAd() {
        if (isNetworkAvailable()) {
            binding.adLayout.setAdLifecycleListener(object : AdLifecycleListener {
                override fun onAdLoaded(event: AdEvent) {
                    isBannerAdLoaded = true
                    applyNavigationBarVisibility()
                }

                override fun onAdFailedToLoad(event: AdEvent) {
                    isBannerAdLoaded = false
                    applyNavigationBarVisibility()
                }
            })
        } else {
            isBannerAdLoaded = false
            applyNavigationBarVisibility()
        }
    }

    private fun applyNavigationBarVisibility() {
        if (isBannerAdLoaded) {
            hideNavigationBar()
        } else {
            showNavigationBar()
        }
    }

    private fun fetchRemoteConfigData() {
        try {
            val remoteConfig = FirebaseRemoteConfig.getInstance()
            remoteConfig.fetch(0)
            remoteConfig.fetchAndActivate()
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        val appListJson = remoteConfig.getString("appList")
                        config.lastFetched = System.currentTimeMillis()
                        config.saveAppList(appListJson)
                    } else {
                        Log.d("RemoteConfig", "Fetch failed")
                    }
                }
        } catch (e: Exception) {
            Log.e("RemoteConfig", "fetchRemoteConfigData: error ${e.message}")
        }
    }


    //request overlay permission result
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_OVERLAY_PERMISSION) {
            if (dialog.isShowing) {
                dialog.dismiss()
            }
        } else if (requestCode == 123) {
            if (isPermissionGranted(this, Manifest.permission.POST_NOTIFICATIONS)) {
                // allow notification permission
               // onNotificationResult(this@MainActivity,  true)
            } else {
                // deny notification permission
            }
            checkAllPermissionsAndShowMessage()
        }
    }


    private fun checkContactPermissions() {
        handlePermission(PERMISSION_READ_CONTACTS) {
            werePermissionsHandled = true
            if (it) {
                handlePermission(PERMISSION_WRITE_CONTACTS) {
                    handlePermission(PERMISSION_GET_ACCOUNTS) {
                        handlePermission(PERMISSION_READ_CALL_LOG) {
                            initFragments()
                        }
                    }
                }
            } else {
                handlePermission(PERMISSION_READ_CALL_LOG) {
                    initFragments()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (storedShowPhoneNumbers != config.showPhoneNumbers) {
            System.exit(0)
            return
        }

        if (storedShowTabs != config.showTabs) {
            storedShowTabs = config.showTabs
            config.lastUsedViewPagerPage = 0
            setupTabs()
            binding.viewPager.adapter = ViewPagerAdapter(this, tabsList, config.showTabs)
            binding.viewPager.currentItem = getDefaultTab()
            refreshContacts(ALL_TABS_MASK)
        }

        val configShowContactThumbnails = config.showContactThumbnails
        if (storedShowContactThumbnails != configShowContactThumbnails) {
            getAllFragments().forEach {
                it?.showContactThumbnailsChanged(configShowContactThumbnails)
            }
        }

        val palette = com.contactsapp2025.universal.helpers.HomeThemeHelper.getPalette(this)
        window.decorView.setBackgroundColor(palette.pageBg)
        binding.mainCoordinator.setBackgroundColor(palette.pageBg)
        binding.mainHolder.setBackgroundColor(palette.pageBg)
        binding.viewPager.setBackgroundColor(palette.pageBg)
        binding.bottomBarContainer.setBackgroundColor(palette.bottomNavBg)

        getAllFragments().forEach {
            it?.setupColors(palette.itemTitleText, palette.primaryBlue)
        }
        findViewById<com.contactsapp2025.universal.fragments.CallHistoryFragment>(R.id.call_history_fragment)?.updateChipStyles()

        updateMenuColors()
        setupTabColors()

        val configStartNameWithSurname = config.startNameWithSurname
        if (storedStartNameWithSurname != configStartNameWithSurname) {
            findViewById<MyViewPagerFragment<*>>(R.id.contacts_fragment)?.startNameWithSurnameChanged(configStartNameWithSurname)
            findViewById<MyViewPagerFragment<*>>(R.id.favorites_fragment)?.startNameWithSurnameChanged(configStartNameWithSurname)
        }

        val configFontSize = config.fontSize
        if (storedFontSize != configFontSize) {
            getAllFragments().forEach {
                it?.fontSizeChanged()
            }
        }

        if (werePermissionsHandled && !isFirstResume) {
            if (binding.viewPager.adapter == null) {
                initFragments()
            } else {
                refreshContacts(ALL_TABS_MASK)
            }
        }

        val fabBlueColor = palette.primaryBlue
        val dialpadIcon = resources.getColoredDrawableWithColor(org.fossify.commons.R.drawable.ic_dialpad_vector, Color.WHITE)
        binding.mainDialpadButton.apply {
            setImageDrawable(dialpadIcon)
            background = createOvalBackground(fabBlueColor)
            beVisibleIf(config.showDialpadButton)
        }
        val plusIcon = resources.getColoredDrawableWithColor(org.fossify.commons.R.drawable.ic_plus_vector, palette.fabAddIcon)
        binding.mainFab.apply {
            setImageDrawable(plusIcon)
            background = createRoundedSquareBackground(this@MainActivity, palette.fabAddBg, 16f)
        }
        updateFabVisibilityForCurrentTab()

        isFirstResume = false
        checkShortcuts()
        applyNavigationBarVisibility()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applyNavigationBarVisibility()
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        if (useDynamicTheme) {
            setTheme(getThemeId(showTransparentTop = showTransparentTop))
            updateBackgroundColor(getProperBackgroundColor())
        }
        val palette = com.contactsapp2025.universal.helpers.HomeThemeHelper.getPalette(this)
        window.decorView.setBackgroundColor(palette.pageBg)
        binding.mainCoordinator.setBackgroundColor(palette.pageBg)
        binding.mainHolder.setBackgroundColor(palette.pageBg)
        binding.viewPager.setBackgroundColor(palette.pageBg)
        binding.bottomBarContainer.setBackgroundColor(palette.bottomNavBg)

        getAllFragments().forEach {
            it?.setupColors(palette.itemTitleText, palette.primaryBlue)
        }
        findViewById<com.contactsapp2025.universal.fragments.CallHistoryFragment>(R.id.call_history_fragment)?.updateChipStyles()
        setupTabs()
        updateMenuColors()
        setupTabColors()

        if (isNetworkAvailable() && binding.adLayout.isEmpty()) {
            binding.adLayout.visibility = View.VISIBLE
            binding.adLayout.refreshBanner()
        }
        applyNavigationBarVisibility()
    }

    override fun onPause() {
        super.onPause()
        storeStateVariables()
        config.lastUsedViewPagerPage = binding.viewPager.currentItem
    }

    override fun onDestroy() {
        isActivityAlive = false
        isBannerAdLoaded = false
        binding.adLayout.destroy()
        super.onDestroy()
        if (!isChangingConfigurations) {
            ContactsDatabase.destroyInstance()
        }
    }

    fun onBackPressed1() {
        if (binding.mainMenu.isSearchOpen) {
            binding.mainMenu.closeSearch()
            applyNavigationBarVisibility()
        } else {
            finishAffinity()
        }
    }

    private fun refreshMenuItems() {
        binding.mainMenu.getToolbar().menu.apply {
            findItem(R.id.sort)?.isVisible = true
            findItem(R.id.filter)?.isVisible = true
            findItem(R.id.more_options)?.isVisible = true
            findItem(R.id.dialpad)?.isVisible = false
            findItem(R.id.change_view_type)?.isVisible = false
            findItem(R.id.column_count)?.isVisible = false
            findItem(R.id.settings)?.isVisible = false
            findItem(R.id.more_apps_from_us)?.isVisible = false
            findItem(R.id.about)?.isVisible = false
        }
    }

    private fun setupOptionsMenu() {
        binding.mainMenu.getToolbar().inflateMenu(R.menu.menu)
        binding.mainMenu.getToolbar().overflowIcon?.setTint(ContextCompat.getColor(this, R.color.home_item_title_text))
        binding.mainMenu.toggleHideOnScroll(false)
        binding.mainMenu.setupMenu()
        binding.mainMenu.updateHintText(getString(R.string.search_contacts))
        binding.mainMenu.onSearchClosedListener = {
            getAllFragments().forEach {
                it?.onSearchClosed()
            }
        }

        binding.mainMenu.onSearchTextChangedListener = { text ->
            getCurrentFragment()?.onSearchQueryChanged(text)
        }

        binding.mainMenu.getToolbar().setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
//                R.id.sort -> showSortingDialog(showCustomSorting = getCurrentFragment() is FavoritesFragment)
                R.id.filter -> showFilterDialog()
                R.id.more_options -> if (getCurrentFragment() is FavoritesFragment) showFavoritesOptions() else showHomeOptionsPopup()
                R.id.dialpad -> launchDialpad()
                R.id.change_view_type -> changeViewType()
                R.id.column_count -> changeColumnCount()
                R.id.settings -> launchSettings()
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }
    }

    private fun showHomeOptionsPopup() {
        hideKeyboard()
        val palette = com.contactsapp2025.universal.helpers.HomeThemeHelper.getPalette(this)
        val popupBinding = com.contactsapp2025.universal.databinding.PopupHomeOptionsBinding.inflate(layoutInflater)
        popupBinding.popupOptionsCard.background = com.contactsapp2025.universal.helpers.HomeThemeHelper.createPopupCardBackground(this, palette.cardBg, palette.itemDivider)

        val iconTint = ColorStateList.valueOf(palette.itemTitleText)
        arrayOf(
            popupBinding.menuIconGroups,
            popupBinding.menuIconAllCalls,
            popupBinding.menuIconOutgoingCalls,
            popupBinding.menuIconIncomingCalls,
            popupBinding.menuIconMissedCalls,
            popupBinding.menuIconBlockedCalls,
            popupBinding.menuIconSettings
        ).forEach {
            androidx.core.widget.ImageViewCompat.setImageTintList(it, iconTint)
        }

        arrayOf(
            popupBinding.menuTextGroups,
            popupBinding.menuTextAllCalls,
            popupBinding.menuTextOutgoingCalls,
            popupBinding.menuTextIncomingCalls,
            popupBinding.menuTextMissedCalls,
            popupBinding.menuTextBlockedCalls,
            popupBinding.menuTextSettings
        ).forEach {
            it.setTextColor(palette.itemTitleText)
        }

        val popupWidth = com.contactsapp2025.universal.helpers.HomeThemeHelper.dpToPx(this, 205f).toInt()
        val popupWindow = android.widget.PopupWindow(
            popupBinding.root,
            popupWidth,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            elevation = com.contactsapp2025.universal.helpers.HomeThemeHelper.dpToPx(this@MainActivity, 10f)
            isOutsideTouchable = true
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        }

        fun applyCallFilterAndSwitchTab(filter: com.contactsapp2025.universal.fragments.CallHistoryFragment.CallFilter) {
            popupWindow.dismiss()
            val visibleTabs = tabsList.filter { (config.showTabs and it) != 0 }
            val recentTabIndex = visibleTabs.indexOf(TAB_CALL_HISTORY)
            if (recentTabIndex != -1 && binding.viewPager.currentItem != recentTabIndex) {
                binding.viewPager.currentItem = recentTabIndex
            }
            findViewById<com.contactsapp2025.universal.fragments.CallHistoryFragment>(R.id.call_history_fragment)?.selectFilterFromMenu(filter)
        }

        popupBinding.menuItemGroups.setOnClickListener {
            popupWindow.dismiss()
            startActivity(Intent(this, GroupsActivity::class.java))
        }
        popupBinding.menuItemAllCalls.setOnClickListener {
            applyCallFilterAndSwitchTab(com.contactsapp2025.universal.fragments.CallHistoryFragment.CallFilter.ALL)
        }
        popupBinding.menuItemOutgoingCalls.setOnClickListener {
            applyCallFilterAndSwitchTab(com.contactsapp2025.universal.fragments.CallHistoryFragment.CallFilter.OUTGOING)
        }
        popupBinding.menuItemIncomingCalls.setOnClickListener {
            applyCallFilterAndSwitchTab(com.contactsapp2025.universal.fragments.CallHistoryFragment.CallFilter.INCOMING)
        }
        popupBinding.menuItemMissedCalls.setOnClickListener {
            applyCallFilterAndSwitchTab(com.contactsapp2025.universal.fragments.CallHistoryFragment.CallFilter.MISSED)
        }
        popupBinding.menuItemBlockedCalls.setOnClickListener {
            applyCallFilterAndSwitchTab(com.contactsapp2025.universal.fragments.CallHistoryFragment.CallFilter.BLOCKED)
        }
        popupBinding.menuItemSettings.setOnClickListener {
            popupWindow.dismiss()
            launchSettings()
        }

        val anchor = binding.mainMenu.getToolbar()
        val offsetX = anchor.width - popupWidth - com.contactsapp2025.universal.helpers.HomeThemeHelper.dpToPx(this, 4f).toInt()
        val offsetY = com.contactsapp2025.universal.helpers.HomeThemeHelper.dpToPx(this, 4f).toInt()
        popupWindow.showAsDropDown(anchor, offsetX, offsetY)
    }

    /** Favorites tab 3-dot menu: Edit favorites / Display mode / Settings. */
    private fun showFavoritesOptions() {
        hideKeyboard()
        com.contactsapp2025.universal.dialogs.FavoritesOptionsDialog(
            this,
            onEditFavorites = { startActivity(Intent(this, EditFavoritesActivity::class.java)) },
            onDisplayMode = {
                com.contactsapp2025.universal.dialogs.DisplayModeDialog(this) {
                    findViewById<FavoritesFragment>(R.id.favorites_fragment)?.onDisplayModeChanged()
                }
            },
            onSettings = { launchSettings() }
        )
    }

    private fun changeViewType() {
        ChangeViewTypeDialog(this) {
            refreshMenuItems()
            findViewById<FavoritesFragment>(R.id.favorites_fragment)?.updateFavouritesAdapter()
        }
    }

    private fun changeColumnCount() {
        val items = ArrayList<RadioItem>()
        for (i in 1..CONTACTS_GRID_MAX_COLUMNS_COUNT) {
            items.add(RadioItem(i, resources.getQuantityString(org.fossify.commons.R.plurals.column_counts, i, i)))
        }

        val currentColumnCount = config.contactsGridColumnCount
        RadioGroupDialog(this, items, currentColumnCount) {
            val newColumnCount = it as Int
            if (currentColumnCount != newColumnCount) {
                config.contactsGridColumnCount = newColumnCount
                findViewById<FavoritesFragment>(R.id.favorites_fragment)?.columnCountChanged()
            }
        }
    }

    private fun updateMenuColors() {
        val palette = com.contactsapp2025.universal.helpers.HomeThemeHelper.getPalette(this)
        val pageBgColor = palette.pageBg
        val searchBgColor = palette.searchBg
        val searchHintColor = palette.searchHint
        val titleTextColor = palette.itemTitleText

        updateStatusbarColor(pageBgColor)
        binding.mainMenu.updateColors()
        binding.mainMenu.setBackgroundColor(pageBgColor)
        binding.mainMenu.binding.topAppBarLayout.setBackgroundColor(pageBgColor)
        binding.mainMenu.binding.topToolbarHolder.background?.applyColorFilter(searchBgColor)
        binding.mainMenu.binding.topToolbarSearch.setTextColor(titleTextColor)
        binding.mainMenu.binding.topToolbarSearch.setHintTextColor(searchHintColor)
        binding.mainMenu.binding.topToolbarSearchIcon.applyColorFilter(titleTextColor)

        val toolbar = binding.mainMenu.getToolbar()
        toolbar.overflowIcon?.setTint(titleTextColor)
        val menu = toolbar.menu
        for (i in 0 until menu.size) {
            val item = menu[i]
            item.icon?.setTint(titleTextColor)
            val title = item.title?.toString() ?: ""
            if (title.isNotEmpty()) {
                val spannable = SpannableString(title)
                spannable.setSpan(ForegroundColorSpan(titleTextColor), 0, spannable.length, Spannable.SPAN_INCLUSIVE_INCLUSIVE)
                item.title = spannable
            }
        }
    }

    private fun storeStateVariables() {
        config.apply {
            storedShowContactThumbnails = showContactThumbnails
            storedShowPhoneNumbers = showPhoneNumbers
            storedStartNameWithSurname = startNameWithSurname
            storedShowTabs = showTabs
            storedFontSize = fontSize
        }
    }

    @SuppressLint("NewApi")
    private fun checkShortcuts() {
        val appIconColor = config.appIconColor
        if (isNougatMR1Plus() && config.lastHandledShortcutColor != appIconColor) {
            val createNewContact = getCreateNewContactShortcut(appIconColor)

            try {
                shortcutManager.dynamicShortcuts = Arrays.asList(createNewContact)
                config.lastHandledShortcutColor = appIconColor
            } catch (_: Exception) {
            }
        }
    }

    @SuppressLint("NewApi")
    private fun getCreateNewContactShortcut(appIconColor: Int): ShortcutInfo {
        val newEvent = getString(org.fossify.commons.R.string.create_new_contact)
        val drawable = resources.getDrawable(org.fossify.commons.R.drawable.shortcut_plus)
        (drawable as LayerDrawable).findDrawableByLayerId(org.fossify.commons.R.id.shortcut_plus_background).applyColorFilter(appIconColor)
        val bmp = drawable.convertToBitmap()

        val intent = Intent(this, EditContactActivity::class.java)
        intent.action = Intent.ACTION_VIEW
        return ShortcutInfo.Builder(this, "create_new_contact")
            .setShortLabel(newEvent)
            .setLongLabel(newEvent)
            .setIcon(Icon.createWithBitmap(bmp))
            .setIntent(intent)
            .build()
    }

    private fun getCurrentFragment(): MyViewPagerFragment<*>? {
        val showTabs = config.showTabs
        Log.d("TEST", "getCurrentFragment: ====> showTabs : $showTabs")
        val fragments = arrayListOf<MyViewPagerFragment<*>>()
        if (showTabs and TAB_CALL_HISTORY != 0) {
            fragments.add(findViewById(R.id.call_history_fragment))
        }

        if (showTabs and TAB_CONTACTS != 0) {
            fragments.add(findViewById(R.id.contacts_fragment))
        }

        if (showTabs and TAB_FAVORITES != 0) {
            fragments.add(findViewById(R.id.favorites_fragment))
        }

        return fragments.getOrNull(binding.viewPager.currentItem)
    }

    private fun setupTabColors() {
        val palette = getPalette(this)
        val padV = dpToPx(this, 8f).toInt()
        binding.mainTabsHolder.setPadding(0, 0, 0, 0)
        binding.mainTabsHolder.backgroundTintList = null
        binding.mainTabsHolder.background = createBottomNavBarBackground(this, palette.bottomNavBg, palette.itemDivider)
        binding.mainTabsHolder.isItemActiveIndicatorEnabled = true
        binding.mainTabsHolder.itemActiveIndicatorShapeAppearance = com.google.android.material.shape.ShapeAppearanceModel.builder()
            .setAllCornerSizes(com.google.android.material.shape.RelativeCornerSize(0.5f))
            .build()
        binding.mainTabsHolder.itemActiveIndicatorColor = ColorStateList.valueOf(palette.bottomNavPill)
        binding.mainTabsHolder.itemActiveIndicatorWidth = dpToPx(this, 56f).toInt()
        binding.mainTabsHolder.itemActiveIndicatorHeight = dpToPx(this, 30f).toInt()
        binding.mainTabsHolder.itemPaddingTop = padV
        binding.mainTabsHolder.itemPaddingBottom = padV
        binding.mainTabsHolder.itemBackground = null
        updateNavigationBarColor(palette.bottomNavBg)

        val activeColor = palette.bottomNavActive
        val inactiveColor = palette.bottomNavUnselected
        val states = arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(-android.R.attr.state_checked)
        )
        val colors = intArrayOf(activeColor, inactiveColor)
        val tabColorStateList = ColorStateList(states, colors)
        binding.mainTabsHolder.itemIconTintList = tabColorStateList
        binding.mainTabsHolder.itemTextColor = tabColorStateList

        val menuView = binding.mainTabsHolder.getChildAt(0) as? android.view.ViewGroup
        if (menuView != null) {
            menuView.minimumHeight = 0
            menuView.setPadding(0, 0, 0, 0)
            for (i in 0 until menuView.childCount) {
                val itemView = menuView.getChildAt(i)
                itemView.minimumHeight = 0
                itemView.background = null
                itemView.setPadding(0, 0, 0, 0)
                itemView.findViewById<android.widget.TextView>(com.google.android.material.R.id.navigation_bar_item_small_label_view)?.apply {
                    setTextColor(tabColorStateList)
                    setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11.5f)
                }
                itemView.findViewById<android.widget.TextView>(com.google.android.material.R.id.navigation_bar_item_large_label_view)?.apply {
                    setTextColor(tabColorStateList)
                    setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11.5f)
                }
            }
        }
    }

    private fun updateFabVisibilityForCurrentTab() {
        val visibleTabs = tabsList.filter { (config.showTabs and it) != 0 }
        val tabValue = visibleTabs.getOrNull(binding.viewPager.currentItem) ?: visibleTabs.firstOrNull()
        val isContactsTab = tabValue == TAB_CONTACTS
        val isFavoritesTab = tabValue == TAB_FAVORITES
        binding.mainFab.beVisibleIf(isContactsTab || isFavoritesTab)
        binding.mainDialpadButton.beVisibleIf(config.showDialpadButton)
    }

    private fun initFragments() {
        binding.mainFab.setOnClickListener {
            val currentFragment = getCurrentFragment()
            currentFragment?.fabClicked()
        }

        binding.viewPager.offscreenPageLimit = tabsList.size - 1
        binding.viewPager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrollStateChanged(state: Int) {}

            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}

            override fun onPageSelected(position: Int) {
                val visibleTabs = tabsList.filter { (config.showTabs and it) != 0 }
                val tabValue = visibleTabs.getOrNull(position)
                if (tabValue != null) {
                    val menuItem = binding.mainTabsHolder.menu.findItem(tabValue)
                    menuItem?.isChecked = true

                    // Update main FAB content description based on the active tab
                    when (tabValue) {
                        TAB_CALL_HISTORY -> binding.mainFab.contentDescription = getString(org.fossify.commons.R.string.create_new_contact)
                        TAB_CONTACTS -> binding.mainFab.contentDescription = getString(org.fossify.commons.R.string.create_new_contact)
                        TAB_FAVORITES -> binding.mainFab.contentDescription = getString(org.fossify.commons.R.string.add_favorites)
                        TAB_GROUPS -> binding.mainFab.contentDescription = getString(R.string.create_group)
                    }
                    updateFabVisibilityForCurrentTab()
                }
                getAllFragments().forEach {
                    it?.finishActMode()
                }
                refreshMenuItems()
            }
        })

        binding.viewPager.onGlobalLayout {
            refreshContacts(ALL_TABS_MASK)
            refreshMenuItems()
        }

        if (intent?.action == Intent.ACTION_VIEW && intent.data != null) {
            tryImportContactsFromFile(intent.data!!, {
                if (it) {
                    runOnUiThread {
                        refreshContacts(ALL_TABS_MASK)
                    }
                }
            }, {})
            intent.data = null
        }

        binding.mainDialpadButton.setOnClickListener {
            launchDialpad()
        }

        handleNotificationDestinationIntent(intent)
    }

    private fun setupTabs() {
        binding.mainTabsHolder.menu.clear()
        tabsList.forEachIndexed { index, value ->
            if (config.showTabs and value != 0) {
                val menuItem = binding.mainTabsHolder.menu.add(android.view.Menu.NONE, value, index, getTabLabel(index))
                menuItem.icon = getTabIcon(index)
            }
        }

        binding.mainTabsHolder.setOnItemSelectedListener { item ->
            val visibleTabs = tabsList.filter { (config.showTabs and it) != 0 }
            val targetIndex = visibleTabs.indexOf(item.itemId)
            if (targetIndex != -1 && binding.viewPager.currentItem != targetIndex) {
                binding.mainMenu.closeSearch()
                binding.viewPager.currentItem = targetIndex
            }
            true
        }

        binding.mainTabsHolder.beGoneIf(binding.mainTabsHolder.menu.size <= 1)

        val visibleTabs = tabsList.filter { (config.showTabs and it) != 0 }
        val currentTab = visibleTabs.getOrNull(binding.viewPager.currentItem)
        if (currentTab != null) {
            binding.mainTabsHolder.menu.findItem(currentTab)?.isChecked = true
        }
    }

    private fun showSortingDialog(showCustomSorting: Boolean) {
        ChangeSortingDialog(this, showCustomSorting) {
            refreshContacts(TAB_CONTACTS or TAB_FAVORITES)
        }
    }

    fun showFilterDialog() {
        FilterContactSourcesDialog(this) {
            findViewById<MyViewPagerFragment<*>>(R.id.contacts_fragment)?.forceListRedraw = true
            refreshContacts(TAB_CONTACTS or TAB_FAVORITES)
        }
    }

    private fun launchDialpad() {
        hideKeyboard()
        try {
            startActivity(Intent(this, DialpadActivity::class.java))
        } catch (e: Exception) {
            showErrorToast(e)
        }
    }

    private fun launchSettings() {
        hideKeyboard()
        startActivity(Intent(applicationContext, SettingsActivity::class.java))
    }

    override fun refreshContacts(refreshTabsMask: Int) {
        if (isDestroyed || isFinishing || isGettingContacts) {
            return
        }
        isGettingContacts = true

        if (binding.viewPager.adapter == null) {
            binding.viewPager.adapter = ViewPagerAdapter(this, tabsList, config.showTabs)
            binding.viewPager.currentItem = getDefaultTab()
            updateFabVisibilityForCurrentTab()
        }
        ContactsHelper(this).getContacts { contacts ->
            isGettingContacts = false
            if (refreshTabsMask and TAB_CALL_HISTORY != 0) {
                findViewById<MyViewPagerFragment<*>>(R.id.call_history_fragment)?.apply {
                    skipHashComparing = true
                    refreshContacts(contacts)
                }
            }

            if (refreshTabsMask and TAB_CONTACTS != 0) {
                findViewById<MyViewPagerFragment<*>>(R.id.contacts_fragment)?.apply {
                    skipHashComparing = true
                    refreshContacts(contacts)
                }
            }

            if (refreshTabsMask and TAB_FAVORITES != 0) {
                findViewById<MyViewPagerFragment<*>>(R.id.favorites_fragment)?.apply {
                    skipHashComparing = true
                    refreshContacts(contacts)
                }
            }

            if (refreshTabsMask and TAB_GROUPS != 0) {
                findViewById<MyViewPagerFragment<*>>(R.id.groups_fragment)?.apply {
                    if (refreshTabsMask == TAB_GROUPS) {
                        skipHashComparing = true
                    }
                    refreshContacts(contacts)
                }
            }

            if (binding.mainMenu.isSearchOpen) {
                getCurrentFragment()?.onSearchQueryChanged(binding.mainMenu.getCurrentQuery())
            }
        }
    }

    override fun contactClicked(contact: Contact) {
        handleGenericContactClick(contact)
    }

    private fun getAllFragments() = arrayListOf<MyViewPagerFragment<*>?>(
        findViewById(R.id.call_history_fragment),
        findViewById(R.id.contacts_fragment),
        findViewById(R.id.favorites_fragment),
        findViewById(R.id.groups_fragment)
    )

    private fun getDefaultTab(): Int {
        val showTabsMask = config.showTabs
        val visibleTabs = tabsList.filter { (showTabsMask and it) != 0 }
        val isLaunchedFromHistory = (intent?.flags?.and(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) ?: 0) != 0
        val category = if (!isLaunchedFromHistory) intent?.getStringExtra("notification_category") else null
        val tabPos = if (!isLaunchedFromHistory) intent?.getIntExtra("tab_position", -1) else -1
        if (category == "favorites" || tabPos == LOCATION_FAVORITES_TAB) {
            val favoritesIndex = visibleTabs.indexOf(TAB_FAVORITES)
            if (favoritesIndex != -1) {
                return favoritesIndex
            }
        }

        if (config.defaultTab == TAB_LAST_USED) {
            return config.lastUsedViewPagerPage.coerceIn(0, (visibleTabs.size - 1).coerceAtLeast(0))
        }

        val defaultIndex = visibleTabs.indexOf(config.defaultTab)
        return if (defaultIndex != -1) defaultIndex else 0
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        dismissActiveNotifications()
        handleNotificationDestinationIntent(intent)
    }

    private fun handleNotificationDestinationIntent(targetIntent: Intent?) {
        if (targetIntent == null) return

        if ((targetIntent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0) {
            targetIntent.removeExtra("destination_class")
            return
        }

        val destinationClassName = targetIntent.getStringExtra("destination_class")
        if (!destinationClassName.isNullOrEmpty() && destinationClassName != MainActivity::class.java.name) {
            try {
                val destinationClass = Class.forName(destinationClassName)
                val intentToLaunch = Intent(this, destinationClass).apply {
                    targetIntent.extras?.let { putExtras(it) }
                    removeExtra("destination_class")
                }
                startActivity(intentToLaunch)
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to start target activity: $destinationClassName", e)
            }
            targetIntent.removeExtra("destination_class")
        } else {
            val category = targetIntent.getStringExtra("notification_category")
            val tabPos = targetIntent.getIntExtra("tab_position", -1)
            if (category == "favorites" || tabPos == LOCATION_FAVORITES_TAB) {
                val visibleTabs = tabsList.filter { (config.showTabs and it) != 0 }
                val favoritesIndex = visibleTabs.indexOf(TAB_FAVORITES)
                if (favoritesIndex != -1 && binding.viewPager.currentItem != favoritesIndex) {
                    binding.viewPager.currentItem = favoritesIndex
                }
            }
        }
    }

    private fun dismissActiveNotifications() {
        try {
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(ContactFeatureNotificationHelper.NOTIFICATION_ID)
            notificationManager.cancel(OnboardingNotificationEngine.NOTIFICATION_ID)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
