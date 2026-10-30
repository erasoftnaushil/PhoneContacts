package com.contactsapp2025.universal.activities

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.net.toUri
import androidx.core.view.isVisible
import com.callsdk.CallSDKSettings
import com.contactsapp2025.universal.R
import com.contactsapp2025.universal.databinding.ActivitySettingsBinding
import com.contactsapp2025.universal.dialogs.ExportContactsDialog
import com.contactsapp2025.universal.dialogs.ManageAutoBackupsDialog
import com.contactsapp2025.universal.dialogs.ManageVisibleFieldsDialog
import com.contactsapp2025.universal.dialogs.ManageVisibleTabsDialog
import com.contactsapp2025.universal.extensions.*
import com.contactsapp2025.universal.helpers.Settings.Companion.THEME_DARK
import com.contactsapp2025.universal.helpers.Settings.Companion.THEME_LIGHT
import com.contactsapp2025.universal.helpers.Settings.Companion.THEME_SYSTEM
import com.contactsapp2025.universal.helpers.VcfExporter
import com.contactsapp2025.universal.language.FunctionUtils
import com.contactsapp2025.universal.language.LanguageActivity
import org.fossify.commons.dialogs.FilePickerDialog
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.*
import org.fossify.commons.models.RadioItem
import java.io.OutputStream
import java.util.Locale
import kotlin.system.exitProcess

class SettingsActivity : SimpleActivity() {
    companion object {
        private const val PICK_IMPORT_SOURCE_INTENT = 1
        private const val PICK_EXPORT_FILE_INTENT = 2
    }

    private val binding by viewBinding(ActivitySettingsBinding::inflate)

    private var ignoredExportContactSources = HashSet<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        isMaterialActivity = true
        super.onCreate(savedInstanceState)

        FunctionUtils.setLocale(this, config.getLanguage() ?: "en")
        setContentView(binding.root)
//        updateMaterialActivityViews(binding.settingsCoordinator, binding.settingsHolder, useTransparentNavigation = true, useTopSearchMenu = false)
        setupMaterialScrollListener(binding.settingsNestedScrollview, binding.settingsToolbar)
        binding.themeSectionLabel.setOnClickListener {
            showThemeSelectionDialog()
        }
        binding.settingsManagePrivacyHolder.setOnClickListener {
            val intent =
                Intent(Intent.ACTION_VIEW, getString(R.string.privacy_policy_link).toUri())
            startActivity(intent)
        }
    }

    private fun showProgressBar(show: Boolean) {
        binding.settingsProgressBar.isVisible = show
    }

    private fun showThemeSelectionDialog() {
        val items = arrayListOf(
            RadioItem(THEME_SYSTEM, getString(org.fossify.commons.R.string.system_default)),
            RadioItem(THEME_LIGHT, getString(org.fossify.commons.R.string.light_theme)),
            RadioItem(THEME_DARK, getString(org.fossify.commons.R.string.dark_theme))
        )

        RadioGroupDialog(this@SettingsActivity, items, config.theme) {
            if (config.theme != it as Int) {
                config.theme = it

                CallSDKSettings.setThemeMode(when (baseConfig.theme) {
                    THEME_SYSTEM -> 0
                    THEME_LIGHT -> 1
                    THEME_DARK -> 2
                    else -> {0}
                })
                AppCompatDelegate.setDefaultNightMode(it)
                recreate()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        setupToolbar(binding.settingsToolbar, NavigationIcon.Arrow)

//        setupCustomizeColors()
        setUpTheme()
        setupManageShownContactFields()
        setupManageShownTabs()
        setupFontSize()
        setupUseEnglish()
        setupLanguage()
        setupShowContactThumbnails()
        setupShowPhoneNumbers()
        setupEnableNumberFormatting()
        setupShowContactsWithNumbers()
        setupStartNameWithSurname()
        setupMergeDuplicateContacts()
        setupShowCallConfirmation()
        setupShowDialpadButton()
        setupShowPrivateContacts()
        setupOnContactClick()
        setupDefaultTab()
        setupEnableAutomaticBackups()
        setupManageAutomaticBackups()
        setupExportContacts()
        setupImportContacts()
        updateTextColors(binding.settingsHolder)

//        arrayOf(
//            binding.settingsColorCustomizationSectionLabel,
//            binding.settingsGeneralSettingsLabel,
//            binding.settingsMainScreenLabel,
//            binding.settingsListViewLabel,
//            binding.settingsBackupsLabel,
//            binding.settingsMigratingLabel
//        ).forEach {
//            it.setTextColor(getProperPrimaryColor())
//        }
    }

    private fun setUpTheme() {
        when (config.theme) {
            THEME_SYSTEM -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            THEME_LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            THEME_DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        }
        binding.themeValue.text = getThemeText()
    }

    private fun getThemeText() = getString(
        when (config.theme) {
            THEME_LIGHT -> org.fossify.commons.R.string.light_theme
            THEME_DARK -> org.fossify.commons.R.string.dark_theme
            else -> org.fossify.commons.R.string.system_default
        }
    )

    private fun setupCustomizeColors() {
        binding.settingsColorCustomizationHolder.setOnClickListener {
//            startCustomizationActivity()
        }
    }

    private fun setupManageShownContactFields() {
        binding.settingsManageContactFieldsHolder.setOnClickListener {
            ManageVisibleFieldsDialog(this) {}
        }
    }

    private fun setupManageShownTabs() {
        binding.settingsManageShownTabsHolder.setOnClickListener {
            ManageVisibleTabsDialog(this)
        }
    }

    private fun setupDefaultTab() {
        binding.settingsDefaultTab.text = getDefaultTabText()
        binding.settingsDefaultTabHolder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(TAB_CALL_HISTORY, getString(org.fossify.commons.R.string.call_history_tab)),
                RadioItem(TAB_CONTACTS, getString(org.fossify.commons.R.string.contacts_tab)),
                RadioItem(TAB_FAVORITES, getString(org.fossify.commons.R.string.favorites_tab)),
                RadioItem(TAB_LAST_USED, getString(org.fossify.commons.R.string.last_used_tab))
            )

            RadioGroupDialog(this@SettingsActivity, items, config.defaultTab) {
                config.defaultTab = it as Int
                binding.settingsDefaultTab.text = getDefaultTabText()
            }
        }
    }

    private fun getDefaultTabText() = getString(
        when (baseConfig.defaultTab) {
            TAB_CALL_HISTORY -> org.fossify.commons.R.string.call_history_tab
            TAB_CONTACTS -> org.fossify.commons.R.string.contacts_tab
            TAB_FAVORITES -> org.fossify.commons.R.string.favorites_tab
            TAB_GROUPS -> org.fossify.commons.R.string.groups_tab
            else -> org.fossify.commons.R.string.last_used_tab
        }
    )

    private fun setupFontSize() {
        binding.settingsFontSize.text = getFontSizeText()
        binding.settingsFontSizeHolder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(FONT_SIZE_SMALL, getString(org.fossify.commons.R.string.small)),
                RadioItem(FONT_SIZE_MEDIUM, getString(org.fossify.commons.R.string.medium)),
                RadioItem(FONT_SIZE_LARGE, getString(org.fossify.commons.R.string.large)),
                RadioItem(FONT_SIZE_EXTRA_LARGE, getString(org.fossify.commons.R.string.extra_large))
            )

            RadioGroupDialog(this@SettingsActivity, items, config.fontSize) {
                config.fontSize = it as Int
                binding.settingsFontSize.text = getFontSizeText()
            }
        }
    }

    private fun setupUseEnglish() {
        binding.settingsUseEnglishHolder.beVisibleIf((config.wasUseEnglishToggled || Locale.getDefault().language != "en") && !isTiramisuPlus())
        binding.settingsUseEnglish.isChecked = config.useEnglish
        binding.settingsUseEnglishHolder.setOnClickListener {
            binding.settingsUseEnglish.toggle()
            config.useEnglish = binding.settingsUseEnglish.isChecked
            exitProcess(0)
        }
    }

    private fun setupLanguage() {
        val languageCode = config.getLanguage()
        if (!languageCode.isNullOrEmpty()) {
            binding.settingsLanguage.text = Locale(languageCode).displayLanguage
        } else {
            binding.settingsLanguage.text = Locale.getDefault().displayLanguage
        }
        binding.settingsLanguageHolder.setOnClickListener {
            startActivity(Intent(this, LanguageActivity::class.java))
        }
    }

    private fun setupShowContactThumbnails() {
        binding.settingsShowContactThumbnails.isChecked = config.showContactThumbnails
        binding.settingsShowContactThumbnailsHolder.setOnClickListener {
            binding.settingsShowContactThumbnails.toggle()
            config.showContactThumbnails = binding.settingsShowContactThumbnails.isChecked
        }
    }

    private fun setupEnableNumberFormatting() {
        binding.settingsFormatPhoneNumbers.isChecked = config.formatPhoneNumbers
        binding.settingsFormatPhoneNumbersHolder.setOnClickListener {
            binding.settingsFormatPhoneNumbers.toggle()
            config.formatPhoneNumbers = binding.settingsFormatPhoneNumbers.isChecked
        }
    }

    private fun setupShowPhoneNumbers() {
        binding.settingsShowPhoneNumbers.isChecked = config.showPhoneNumbers
        binding.settingsShowPhoneNumbersHolder.setOnClickListener {
            binding.settingsShowPhoneNumbers.toggle()
            config.showPhoneNumbers = binding.settingsShowPhoneNumbers.isChecked
        }
    }

    private fun setupShowContactsWithNumbers() {
        binding.settingsShowOnlyContactsWithNumbers.isChecked = config.showOnlyContactsWithNumbers
        binding.settingsShowOnlyContactsWithNumbersHolder.setOnClickListener {
            binding.settingsShowOnlyContactsWithNumbers.toggle()
            config.showOnlyContactsWithNumbers = binding.settingsShowOnlyContactsWithNumbers.isChecked
        }
    }

    private fun setupStartNameWithSurname() {
        binding.settingsStartNameWithSurname.isChecked = config.startNameWithSurname
        binding.settingsStartNameWithSurnameHolder.setOnClickListener {
            binding.settingsStartNameWithSurname.toggle()
            config.startNameWithSurname = binding.settingsStartNameWithSurname.isChecked
        }
    }

    private fun setupShowDialpadButton() {
        binding.settingsShowDialpadButton.isChecked = config.showDialpadButton
        binding.settingsShowDialpadButtonHolder.setOnClickListener {
            binding.settingsShowDialpadButton.toggle()
            config.showDialpadButton = binding.settingsShowDialpadButton.isChecked
        }
    }

    private fun setupShowPrivateContacts() {
        binding.settingsShowPrivateContacts.isChecked = config.showPrivateContacts
        binding.settingsShowPrivateContactsHolder.setOnClickListener {
            binding.settingsShowPrivateContacts.toggle()
            config.showPrivateContacts = binding.settingsShowPrivateContacts.isChecked
        }
    }

    private fun setupOnContactClick() {
        binding.settingsOnContactClick.text = getOnContactClickText()
        binding.settingsOnContactClickHolder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(ON_CLICK_CALL_CONTACT, getString(R.string.call_contact)),
                RadioItem(ON_CLICK_VIEW_CONTACT, getString(R.string.view_contact)),
                RadioItem(ON_CLICK_EDIT_CONTACT, getString(R.string.edit_contact))
            )

            RadioGroupDialog(this@SettingsActivity, items, config.onContactClick) {
                config.onContactClick = it as Int
                binding.settingsOnContactClick.text = getOnContactClickText()
            }
        }
    }

    private fun getOnContactClickText() = getString(
        when (config.onContactClick) {
            ON_CLICK_CALL_CONTACT -> R.string.call_contact
            ON_CLICK_VIEW_CONTACT -> R.string.view_contact
            else -> R.string.edit_contact
        }
    )

    private fun setupShowCallConfirmation() {
        binding.settingsShowCallConfirmation.isChecked = config.showCallConfirmation
        binding.settingsShowCallConfirmationHolder.setOnClickListener {
            binding.settingsShowCallConfirmation.toggle()
            config.showCallConfirmation = binding.settingsShowCallConfirmation.isChecked
        }
    }

    private fun setupMergeDuplicateContacts() {
        binding.settingsMergeDuplicateContacts.isChecked = config.mergeDuplicateContacts
        binding.settingsMergeDuplicateContactsHolder.setOnClickListener {
            binding.settingsMergeDuplicateContacts.toggle()
            config.mergeDuplicateContacts = binding.settingsMergeDuplicateContacts.isChecked
        }
    }

    private fun setupEnableAutomaticBackups() {
//        binding.settingsBackupsLabel.beVisibleIf(isRPlus())
        binding.settingsEnableAutomaticBackupsHolder.beVisibleIf(isRPlus())
        binding.settingsEnableAutomaticBackups.isChecked = config.autoBackup
        binding.settingsEnableAutomaticBackupsHolder.setOnClickListener {
            val wasBackupDisabled = !config.autoBackup
            Log.d("AutoBackup", "settingsEnableAutomaticBackups clicked. wasBackupDisabled=$wasBackupDisabled")
            if (wasBackupDisabled) {
                ManageAutoBackupsDialog(
                    activity = this,
                    onSuccess = {
                        Log.d("AutoBackup", "ManageAutoBackupsDialog succeeded. Enabling auto backup and scheduling alarm.")
                        enableOrDisableAutomaticBackups(true)
                        scheduleNextAutomaticBackup()
                    }
                )
            } else {
                Log.d("AutoBackup", "Disabling auto backup and cancelling alarm.")
                cancelScheduledAutomaticBackup()
                enableOrDisableAutomaticBackups(false)
            }
        }
    }

    private fun setupManageAutomaticBackups() {
        binding.settingsManageAutomaticBackupsHolder.beVisibleIf(isRPlus() && config.autoBackup)
        binding.settingsManageAutomaticBackupsHolder.setOnClickListener {
            Log.d("AutoBackup", "settingsManageAutomaticBackups clicked. Opening ManageAutoBackupsDialog.")
            ManageAutoBackupsDialog(
                activity = this,
                onSuccess = {
                    Log.d("AutoBackup", "ManageAutoBackupsDialog reconfigured successfully. Re-scheduling next automatic backup.")
                    scheduleNextAutomaticBackup()
                }
            )
        }
    }

    private fun enableOrDisableAutomaticBackups(enable: Boolean) {
        Log.d("AutoBackup", "enableOrDisableAutomaticBackups: enable=$enable")
        config.autoBackup = enable
        binding.settingsEnableAutomaticBackups.isChecked = enable
        binding.settingsManageAutomaticBackupsHolder.beVisibleIf(enable)
    }

    private fun setupExportContacts() {
        binding.contactsExportHolder.setOnClickListener {
            tryExportContacts()
        }
    }

    private val pickVcf =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                importViaSystemContactsApps(uri)
            }
        }

    fun Activity.importViaSystemContactsApps(vcfUri: Uri) {
        val candidates = ArrayList<Intent>()

        // A) Some OEM Contacts apps support this directly
        candidates += Intent("com.android.contacts.action.IMPORT_VCARD").apply {
            setDataAndType(vcfUri, "text/x-vcard")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        // B) Standard view
        candidates += Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(vcfUri, "text/x-vcard")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        // C) Some apps handle send/open as stream
        candidates += Intent(Intent.ACTION_SEND).apply {
            type = "text/x-vcard"
            putExtra(Intent.EXTRA_STREAM, vcfUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        // D) Another common MIME that works on some devices
        candidates += Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(vcfUri, "text/vcard")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        // Pick the first intent that resolves
        val pm = packageManager
        val resolved = candidates.firstOrNull { it.resolveActivity(pm) != null }

        if (resolved == null) {
            Toast.makeText(this, "No Contacts app found to import VCF on this device.", Toast.LENGTH_LONG).show()
            return
        }

        // Grant URI permission to every app that can handle the chosen intent
        val resInfoList = pm.queryIntentActivities(resolved, 0)
        for (ri in resInfoList) {
            val pkg = ri.activityInfo.packageName
            grantUriPermission(pkg, vcfUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            startActivity(Intent.createChooser(resolved, "Import contacts"))
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to open importer: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }



    private fun setupImportContacts() {


        binding.contactsImportHolder.setOnClickListener {
            tryImportContacts()

           // pickVcf.launch(arrayOf("text/x-vcard", "text/vcard", "application/vcard", "*/*"))

        }
    }

    private fun tryImportContacts() {
        if (isQPlus()) {
            Intent(Intent.ACTION_GET_CONTENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/x-vcard" // Standard MIME type for VCF files
                try {
                    startActivityForResult(this, PICK_IMPORT_SOURCE_INTENT)
                } catch (e: ActivityNotFoundException) {
                    toast(org.fossify.commons.R.string.system_service_disabled, Toast.LENGTH_LONG)
                    showProgressBar(false)
                } catch (e: Exception) {
                    showErrorToast(e)
                    showProgressBar(false)
                }
            }
        } else {
            handlePermission(PERMISSION_READ_STORAGE) {
                if (it) {
                    importContacts()
                } else {
                    showProgressBar(false)
                }
            }
        }
    }

    private fun importContacts() {
        FilePickerDialog(this) {
            showImportContactsDialog(it, callback = {

            }) {
                showProgressBar(true)
            }
        }
    }


    private fun tryExportContacts() {
        if (isQPlus()) {
            ExportContactsDialog(this, config.lastExportPath, true) { file, ignoredContactSources ->
                ignoredExportContactSources = ignoredContactSources

                Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    type = "text/x-vcard"
                    putExtra(Intent.EXTRA_TITLE, file.name)
                    addCategory(Intent.CATEGORY_OPENABLE)

                    try {
                        startActivityForResult(this, PICK_EXPORT_FILE_INTENT)
                    } catch (e: ActivityNotFoundException) {
                        toast(org.fossify.commons.R.string.no_app_found, Toast.LENGTH_LONG)
                        showProgressBar(false)
                    } catch (e: Exception) {
                        showErrorToast(e)
                        showProgressBar(false)
                    }
                }
            }
        } else {
            handlePermission(PERMISSION_WRITE_STORAGE) {
                if (it) {
                    ExportContactsDialog(this, config.lastExportPath, false) { file, ignoredContactSources ->
                        getFileOutputStream(file.toFileDirItem(this), true) {
                            exportContactsTo(ignoredContactSources, it)
                        }
                    }
                } else {
                    showProgressBar(false)
                }
            }
        }
    }

    private fun exportContactsTo(ignoredContactSources: HashSet<String>, outputStream: OutputStream?) {
        showProgressBar(true)
        ContactsHelper(this).getContacts(getAll = true, gettingDuplicates = false, ignoredContactSources = ignoredContactSources) { contacts ->
            if (contacts.isEmpty()) {
                toast(org.fossify.commons.R.string.no_entries_for_exporting)
                showProgressBar(false)
            } else {
                VcfExporter().exportContacts(
                    context = this,
                    outputStream = outputStream,
                    contacts = contacts,
                    showExportingToast = true
                ) { result ->
                    toast(
                        when (result) {
                            VcfExporter.ExportResult.EXPORT_OK -> org.fossify.commons.R.string.exporting_successful
                            VcfExporter.ExportResult.EXPORT_PARTIAL -> org.fossify.commons.R.string.exporting_some_entries_failed
                            else -> org.fossify.commons.R.string.exporting_failed
                        }
                    )
                    runOnUiThread {
                        showProgressBar(false)
                    }
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        if (requestCode == PICK_IMPORT_SOURCE_INTENT && resultCode == RESULT_OK && resultData?.data != null) {
            tryImportContactsFromFile(resultData.data!!, {
                runOnUiThread {
                    showProgressBar(false)
                }
            }, {
                runOnUiThread {
                    showProgressBar(true)
                }
            })
        } else if (requestCode == PICK_EXPORT_FILE_INTENT && resultCode == RESULT_OK && resultData?.data != null) {
            try {
                val outputStream = contentResolver.openOutputStream(resultData.data!!)
                exportContactsTo(ignoredExportContactSources, outputStream)
            } catch (e: Exception) {
                showErrorToast(e)
            }
        }
    }
}
