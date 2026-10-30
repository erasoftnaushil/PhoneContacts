package com.contactsapp2025.universal.activities

import android.annotation.SuppressLint
import android.app.ProgressDialog
import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.provider.CallLog.Calls
import android.provider.ContactsContract
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.PopupWindow
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.core.widget.ImageViewCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.FitCenter
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import com.contactsapp2025.universal.R
import com.contactsapp2025.universal.databinding.*
import com.contactsapp2025.universal.dialogs.ChooseSocialDialog
import com.contactsapp2025.universal.dialogs.ContactActionConfirmDialog
import com.contactsapp2025.universal.dialogs.ManageVisibleFieldsDialog
import com.contactsapp2025.universal.extensions.*
import com.contactsapp2025.universal.helpers.*
import com.contactsapp2025.universal.language.FunctionUtils
import org.fossify.commons.dialogs.CallConfirmationDialog
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.dialogs.SelectAlarmSoundDialog
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.*
import org.fossify.commons.models.PhoneNumber
import org.fossify.commons.models.RadioItem
import org.fossify.commons.models.contacts.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import java.util.Locale
import androidx.core.graphics.toColorInt

class ViewContactActivity : ContactActivity() {
    private enum class DetailViewMode {
        CONTACT_WITH_HISTORY,
        CONTACT_DETAILS,
        CALL_HISTORY_ONLY
    }

    private data class ContactCallEntry(
        val id: Int,
        val phoneNumber: String,
        val callType: Int,
        val timestamp: Long,
        val duration: Int,
        val simLabel: String
    )

    private var isViewIntent = false
    private var wasEditLaunched = false
    private var duplicateContacts = ArrayList<Contact>()
    private var contactSources = ArrayList<ContactSource>()
    private var showFields = 0
    private var fullContact: Contact? = null    // contact with all fields filled from duplicates
    private var duplicateInitialized = false
    private val mergeDuplicate: Boolean get() = config.mergeDuplicateContacts
    private val binding by viewBinding(ActivityViewContactBinding::inflate)
    var fromCDO = false

    private var currentViewMode: DetailViewMode? = null
    private var startedInHistoryMode = false
    private var contactCallLogs = ArrayList<ContactCallEntry>()

    companion object {
        private const val COMPARABLE_PHONE_NUMBER_LENGTH = 9
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        showTransparentTop = true
        super.onCreate(savedInstanceState)
        applySystemBarsForTheme()
        FunctionUtils.setLocale(this, config.getLanguage() ?: "en")
        setContentView(binding.root)

        if (checkAppSideloading()) {
            return
        }

        fromCDO = intent.getBooleanExtra("fromCDO", false)

        showFields = config.showContactFields
        binding.contactWrapper.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        setupMenu()
        applyThemeColors()
    }

    override fun onResume() {
        super.onResume()
        applyThemeColors()
        isViewIntent = intent.action == ContactsContract.QuickContact.ACTION_QUICK_CONTACT || intent.action == Intent.ACTION_VIEW
        if (isViewIntent) {
            handlePermission(PERMISSION_READ_CONTACTS) {
                if (it) {
                    ensureBackgroundThread {
                        initContact()
                    }
                } else {
                    toast(org.fossify.commons.R.string.no_contacts_permission)
                    finish()
                }
            }
        } else {
            ensureBackgroundThread {
                initContact()
            }
        }
    }

    private fun applyThemeColors() {
        val palette = HomeThemeHelper.getPalette(this)
        window.decorView.setBackgroundColor(palette.pageBg)
        binding.contactWrapper.setBackgroundColor(palette.pageBg)
        binding.contactScrollview.setBackgroundColor(palette.pageBg)
        binding.contactAppbar.setBackgroundColor(palette.pageBg)

        ImageViewCompat.setImageTintList(binding.btnToolbarBack, ColorStateList.valueOf(palette.itemTitleText))
        ImageViewCompat.setImageTintList(binding.btnToolbarEdit, ColorStateList.valueOf(palette.itemTitleText))
        ImageViewCompat.setImageTintList(binding.btnToolbarMore, ColorStateList.valueOf(palette.itemTitleText))
        binding.tvToolbarTitle.setTextColor(palette.itemTitleText)

        binding.contactName.setTextColor(palette.itemTitleText)
        binding.contactHeaderDivider.setBackgroundColor(palette.itemDivider)
        binding.btnViewContactDetails.setTextColor(palette.primaryBlue)
        binding.tvNoCallHistory.setTextColor(palette.itemSubtitleText)

        val actionCircleColor = if (palette.isDark) palette.fabAddBg else "#C2EAFC".toColorInt()
        val actionIconTint = ColorStateList.valueOf(palette.fabAddIcon)
        arrayOf(
            binding.circleActionCall,
            binding.circleActionText,
            binding.circleActionVideo,
            binding.circleActionBlock
        ).forEach {
            it.background = HomeThemeHelper.createOvalBackground(actionCircleColor)
        }
        arrayOf(
            binding.imgActionCall,
            binding.imgActionText,
            binding.imgActionVideo,
            binding.imgActionBlock
        ).forEach {
            ImageViewCompat.setImageTintList(it, actionIconTint)
        }
        arrayOf(
            binding.txtActionCall,
            binding.txtActionText,
            binding.txtActionVideo,
            binding.txtActionBlock
        ).forEach {
            it.setTextColor(palette.itemTitleText)
        }

        binding.contactDetailsCard.background = HomeThemeHelper.createCardItemBackground(
            context = this,
            cardColor = palette.cardBg,
            isDark = palette.isDark,
            isFirst = true,
            isLast = true
        )

        val pillRadius = HomeThemeHelper.dpToPx(this, 24f)
        val pillShape = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = pillRadius
            setColor(palette.primaryBlue)
        }
        val pillMask = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = pillRadius
            setColor(Color.WHITE)
        }
        binding.btnCallHistory.background = RippleDrawable(
            ColorStateList.valueOf(Color.parseColor("#33FFFFFF")),
            pillShape,
            pillMask
        )
    }

    override fun onBackPressed() {
        handleBackNavigation()
    }

    private fun handleBackNavigation() {
        if (binding.contactPhotoBig.alpha == 1f) {
            hideBigContactPhoto()
            return
        }
        when (currentViewMode) {
            DetailViewMode.CALL_HISTORY_ONLY -> {
                switchViewMode(DetailViewMode.CONTACT_DETAILS)
            }
            DetailViewMode.CONTACT_DETAILS -> {
                if (startedInHistoryMode && contactCallLogs.isNotEmpty()) {
                    switchViewMode(DetailViewMode.CONTACT_WITH_HISTORY)
                } else {
                    finishOrReturnHome()
                }
            }
            else -> {
                finishOrReturnHome()
            }
        }
    }

    private fun finishOrReturnHome() {
        if (isTaskRoot) {
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                startActivity(this)
            }
        }
        finish()
    }

    private fun switchViewMode(mode: DetailViewMode) {
        currentViewMode = mode
        when (mode) {
            DetailViewMode.CONTACT_WITH_HISTORY -> {
                binding.tvToolbarTitle.beGone()
                binding.toolbarActionsHolder.beVisible()
                binding.contactProfileHeader.beVisible()
                binding.btnViewContactDetails.beVisible()
                binding.contactDetailsSection.beGone()
                binding.contactHistorySection.beVisible()
                binding.tvNoCallHistory.beGone()
            }
            DetailViewMode.CONTACT_DETAILS -> {
                binding.tvToolbarTitle.beGone()
                binding.toolbarActionsHolder.beVisible()
                binding.contactProfileHeader.beVisible()
                binding.btnViewContactDetails.beGone()
                binding.contactDetailsSection.beVisible()
                binding.contactHistorySection.beGone()
            }
            DetailViewMode.CALL_HISTORY_ONLY -> {
                binding.tvToolbarTitle.beVisible()
                binding.toolbarActionsHolder.beGone()
                binding.contactProfileHeader.beGone()
                binding.btnViewContactDetails.beGone()
                binding.contactDetailsSection.beGone()
                binding.contactHistorySection.beVisible()
                binding.tvNoCallHistory.beVisibleIf(contactCallLogs.isEmpty())
            }
        }
        binding.contactScrollview.scrollTo(0, 0)
    }

    private fun setupMenu() {
        (binding.contactAppbar.layoutParams as RelativeLayout.LayoutParams).topMargin = statusBarHeight

        binding.btnToolbarBack.setOnClickListener {
            handleBackNavigation()
        }

        binding.btnToolbarEdit.setOnClickListener {
            if (contact != null) {
                launchEditContact(contact!!, false)
            }
        }

        binding.btnToolbarMore.setOnClickListener {
            showMoreOptionsPopup()
        }

        binding.btnViewContactDetails.setOnClickListener {
            switchViewMode(DetailViewMode.CONTACT_DETAILS)
        }

        binding.btnCallHistory.setOnClickListener {
            switchViewMode(DetailViewMode.CALL_HISTORY_ONLY)
        }
    }

    private fun showMoreOptionsPopup() {
        val c = contact ?: return
        val palette = HomeThemeHelper.getPalette(this)
        val popupBinding = PopupViewContactOptionsBinding.inflate(layoutInflater)
        popupBinding.popupContactOptionsCard.background = HomeThemeHelper.createPopupCardBackground(
            this,
            palette.cardBg,
            palette.itemDivider
        )

        val isSavedContact = c.id != 0
        popupBinding.menuItemFavorites.beVisibleIf(isSavedContact)
        popupBinding.menuItemDelete.beVisibleIf(isSavedContact)

        val isStarred = c.starred == 1
        if (isStarred) {
            popupBinding.menuIconFavorites.setImageResource(org.fossify.commons.R.drawable.ic_star_vector)
            ImageViewCompat.setImageTintList(
                popupBinding.menuIconFavorites,
                ColorStateList.valueOf(Color.parseColor("#F59E0B"))
            )
        } else {
            popupBinding.menuIconFavorites.setImageResource(org.fossify.commons.R.drawable.ic_star_outline_vector)
            ImageViewCompat.setImageTintList(
                popupBinding.menuIconFavorites,
                ColorStateList.valueOf(palette.itemTitleText)
            )
        }

        val textTint = ColorStateList.valueOf(palette.itemTitleText)
        ImageViewCompat.setImageTintList(popupBinding.menuIconDelete, textTint)
        ImageViewCompat.setImageTintList(popupBinding.menuIconShare, textTint)

        popupBinding.menuTextFavorites.setTextColor(palette.itemTitleText)
        popupBinding.menuTextDelete.setTextColor(palette.itemTitleText)
        popupBinding.menuTextShare.setTextColor(palette.itemTitleText)

        val popupWidth = HomeThemeHelper.dpToPx(this, 175f).toInt()
        val popupWindow = PopupWindow(
            popupBinding.root,
            popupWidth,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            elevation = HomeThemeHelper.dpToPx(this@ViewContactActivity, 10f)
            isOutsideTouchable = true
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }

        popupBinding.menuItemFavorites.setOnClickListener {
            popupWindow.dismiss()
            toggleFavoriteStatus()
        }

        popupBinding.menuItemDelete.setOnClickListener {
            popupWindow.dismiss()
            deleteContactWithMergeLogic()
        }

        popupBinding.menuItemShare.setOnClickListener {
            popupWindow.dismiss()
            val toShare = fullContact ?: contact
            if (toShare != null) {
                shareContact(toShare)
            }
        }

        val anchor = binding.btnToolbarMore
        val offsetX = anchor.width - popupWidth - HomeThemeHelper.dpToPx(this, 4f).toInt()
        val offsetY = HomeThemeHelper.dpToPx(this, 4f).toInt()
        popupWindow.showAsDropDown(anchor, offsetX, offsetY)
    }

    private fun toggleFavoriteStatus() {
        val c = contact ?: return
        if (c.id == 0) return
        val newIsStarred = if (c.starred == 1) 0 else 1
        c.starred = newIsStarred
        fullContact?.starred = newIsStarred
        ensureBackgroundThread {
            val contacts = arrayListOf(c)
            if (newIsStarred == 1) {
                ContactsHelper(this).addFavorites(contacts)
            } else {
                ContactsHelper(this).removeFavorites(contacts)
            }
        }
    }

    private fun initContact() {
        var wasLookupKeyUsed = false
        var contactId: Int
        try {
            contactId = intent.getIntExtra(CONTACT_ID, 0)
        } catch (_: Exception) {
            return
        }

        if (contactId == 0 && isViewIntent) {
            val data = intent.data
            if (data != null) {
                val rawId = if (data.path!!.contains("lookup")) {
                    val lookupKey = getLookupKeyFromUri(data)
                    if (lookupKey != null) {
                        contact = ContactsHelper(this).getContactWithLookupKey(lookupKey)
                        fullContact = contact
                        wasLookupKeyUsed = true
                    }

                    getLookupUriRawId(data)
                } else {
                    getContactUriRawId(data)
                }

                if (rawId != -1) {
                    contactId = rawId
                }
            }
        }

        val phoneFromIntent = intent.getStringExtra(KEY_PHONE)?.trim() ?: ""
        val nameFromIntent = intent.getStringExtra(KEY_NAME)?.trim() ?: ""

        if (contactId != 0 && !wasLookupKeyUsed) {
            contact = ContactsHelper(this).getContactWithId(contactId, intent.getBooleanExtra(IS_PRIVATE, false))
            fullContact = contact

            if (contact == null) {
                runOnUiThread {
                    if (!wasEditLaunched) {
                        toast(org.fossify.commons.R.string.unknown_error_occurred)
                    }
                    finish()
                }
            } else {
                runOnUiThread {
                    gotContact()
                }
            }
        } else if (contactId == 0 && !wasLookupKeyUsed && phoneFromIntent.isNotEmpty()) {
            ContactsHelper(this).getContacts { contacts ->
                val normalized = phoneFromIntent.normalizePhoneNumber()
                val matched = contacts.firstOrNull { c ->
                    c.doesHavePhoneNumber(phoneFromIntent) ||
                        (normalized.length >= COMPARABLE_PHONE_NUMBER_LENGTH && c.phoneNumbers.any {
                            val pNorm = it.normalizedNumber.ifEmpty { it.value.normalizePhoneNumber() }
                            pNorm.length >= COMPARABLE_PHONE_NUMBER_LENGTH && pNorm.takeLast(COMPARABLE_PHONE_NUMBER_LENGTH) == normalized.takeLast(COMPARABLE_PHONE_NUMBER_LENGTH)
                        })
                }
                if (matched != null) {
                    ensureBackgroundThread {
                        contact = ContactsHelper(this).getContactWithId(matched.id, matched.isPrivate()) ?: matched
                        fullContact = contact
                        runOnUiThread {
                            gotContact()
                        }
                    }
                } else {
                    val tempPhone = PhoneNumber(
                        value = phoneFromIntent,
                        type = ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE,
                        label = "",
                        normalizedNumber = normalized
                    )
                    contact = Contact(
                        id = 0,
                        firstName = nameFromIntent,
                        phoneNumbers = arrayListOf(tempPhone),
                        contactId = 0
                    )
                    fullContact = contact
                    runOnUiThread {
                        gotContact()
                    }
                }
            }
        } else {
            if (contact == null) {
                runOnUiThread {
                    finish()
                }
            } else {
                runOnUiThread {
                    gotContact()
                }
            }
        }
    }

    private fun gotContact() {
        if (isDestroyed || isFinishing || contact == null) {
            return
        }

        binding.contactScrollview.beVisible()
        setupViewContact()

        if (contact!!.photoUri.isEmpty() && contact!!.photo == null) {
            showPhotoPlaceholder(binding.contactPhoto)
        } else {
            updateContactPhoto(contact!!.photoUri, binding.contactPhoto, contact!!.photo)
            val options = RequestOptions()
                .transform(FitCenter(), RoundedCorners(resources.getDimension(org.fossify.commons.R.dimen.normal_margin).toInt()))

            Glide.with(this)
                .load(contact!!.photo ?: currentContactPhotoPath)
                .apply(options)
                .into(binding.contactPhotoBig)

            binding.contactPhoto.setOnClickListener {
                binding.contactPhotoBig.alpha = 0f
                binding.contactPhotoBig.beVisible()
                binding.contactPhotoBig.animate().alpha(1f).start()
            }

            binding.contactPhotoBig.setOnClickListener {
                hideBigContactPhoto()
            }
        }

        binding.contactSendSms.setOnClickListener { trySendSMS() }
        binding.contactStartCall.setOnClickListener {
            if (contact!!.phoneNumbers.isNotEmpty()) {
                tryInitiateCall(contact!!) {
                    startCallIntent(it)
                }
            } else {
                toast(org.fossify.commons.R.string.no_phone_number_found)
            }
        }
        binding.contactVideoCall.setOnClickListener {
            tryInitiateVideoCall()
        }
        binding.contactBlockAction.setOnClickListener {
            showBlockConfirmDialog()
        }

        binding.contactSendSms.setOnLongClickListener { toast(org.fossify.commons.R.string.send_sms); true }
        binding.contactStartCall.setOnLongClickListener { toast(R.string.call_contact); true }

        updateBlockActionLabel()
        applyThemeColors()
    }

    private fun getAllContactNumbers(): List<String> {
        val numbers = LinkedHashSet<String>()
        contact?.phoneNumbers?.forEach {
            if (it.value.isNotBlank()) numbers.add(it.value)
        }
        fullContact?.phoneNumbers?.forEach {
            if (it.value.isNotBlank()) numbers.add(it.value)
        }
        intent.getStringExtra(KEY_PHONE)?.trim()?.takeIf { it.isNotEmpty() }?.let {
            numbers.add(it)
        }
        return numbers.toList()
    }

    private fun isContactBlocked(): Boolean {
        val numbers = getAllContactNumbers()
        if (numbers.isEmpty()) return false
        return numbers.any { config.isLocalNumberBlocked(it) || isNumberBlocked(it) }
    }

    private fun updateBlockActionLabel() {
        val blocked = isContactBlocked()
        binding.txtActionBlock.text = getString(if (blocked) R.string.action_unblock else R.string.action_block)
    }

    private fun showBlockConfirmDialog() {
        val numbers = getAllContactNumbers()
        if (numbers.isEmpty()) {
            toast(org.fossify.commons.R.string.no_phone_number_found)
            return
        }
        val currentlyBlocked = isContactBlocked()
        val title = getString(if (currentlyBlocked) R.string.unblock_contact_title else R.string.block_contact_title)
        val message = getString(if (currentlyBlocked) R.string.unblock_contact_message else R.string.block_contact_message)
        val positiveText = getString(if (currentlyBlocked) R.string.action_unblock else R.string.action_block)

        ContactActionConfirmDialog(
            activity = this,
            title = title,
            message = message,
            positiveText = positiveText
        ) {
            val newBlocked = !currentlyBlocked
            numbers.forEach { number ->
                config.setLocalNumberBlocked(number, newBlocked)
                if (isDefaultDialer()) {
                    try {
                        if (newBlocked) {
                            addBlockedNumber(number)
                        } else {
                            deleteBlockedNumber(number)
                        }
                    } catch (_: Exception) {
                    }
                }
            }
            updateBlockActionLabel()
            toast(if (newBlocked) R.string.number_blocked_toast else R.string.number_unblocked_toast)
        }
    }

    private fun tryInitiateVideoCall() {
        val numbers = (fullContact ?: contact)?.phoneNumbers ?: arrayListOf()
        if (numbers.isEmpty()) {
            val fallbackPhone = intent.getStringExtra(KEY_PHONE)?.trim() ?: ""
            if (fallbackPhone.isNotEmpty()) {
                launchVideoCallToNumber(fallbackPhone)
            } else {
                toast(org.fossify.commons.R.string.no_phone_number_found)
            }
            return
        }
        if (numbers.size == 1) {
            launchVideoCallToNumber(numbers.first().value)
        } else {
            val primaryNumber = numbers.find { it.isPrimary }
            if (primaryNumber != null) {
                launchVideoCallToNumber(primaryNumber.value)
            } else {
                val items = ArrayList<RadioItem>()
                numbers.forEachIndexed { index, phoneNumber ->
                    items.add(RadioItem(index, phoneNumber.value, phoneNumber.value))
                }
                RadioGroupDialog(this, items) {
                    launchVideoCallToNumber(it as String)
                }
            }
        }
    }

    private fun launchVideoCallToNumber(phoneNumber: String) {
        val uri = Uri.fromParts("tel", phoneNumber, null)
        handlePermission(PERMISSION_CALL_PHONE) { granted ->
            try {
                val videoIntent = Intent(if (granted) Intent.ACTION_CALL else Intent.ACTION_DIAL, uri).apply {
                    putExtra(TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE, VideoProfile.STATE_BIDIRECTIONAL)
                    putExtra("videocall", true)
                    putExtra("android.telecom.extra.START_CALL_WITH_VIDEO_STATE", VideoProfile.STATE_BIDIRECTIONAL)
                }
                launchActivityIntent(videoIntent)
            } catch (_: Exception) {
                startCallIntent(phoneNumber)
            }
        }
    }

    private fun setupViewContact() {
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        setupNames()

        ContactsHelper(this).getContactSources {
            contactSources = it
            runOnUiThread {
                setupContactDetails()
            }
        }

        initializeDuplicateContacts {
            duplicateInitialized = true
            setupContactDetails()
            loadContactCallHistory()
        }
    }

    private fun setupContactDetails() {
        if (isFinishing || isDestroyed || contact == null) {
            return
        }

        setupPhoneNumbers()
        setupEmails()
        setupAddresses()
        setupIMs()
        setupEvents()
        setupWebsites()
        setupGroups()
        setupContactSources()
        setupNotes()
        setupRingtone()
        setupOrganization()
        updateBlockActionLabel()
        applyThemeColors()
    }

    private fun loadContactCallHistory() {
        val numbers = getAllContactNumbers()
        if (numbers.isEmpty() || !hasPermission(PERMISSION_READ_CALL_LOG)) {
            contactCallLogs = ArrayList()
            renderContactCallHistory()
            if (currentViewMode == null) {
                startedInHistoryMode = false
                switchViewMode(DetailViewMode.CONTACT_DETAILS)
            }
            return
        }

        ensureBackgroundThread {
            val logs = fetchCallLogsForNumbers(numbers)
            runOnUiThread {
                if (isDestroyed || isFinishing) return@runOnUiThread
                contactCallLogs = logs
                renderContactCallHistory()
                if (currentViewMode == null) {
                    if (logs.isNotEmpty()) {
                        startedInHistoryMode = true
                        switchViewMode(DetailViewMode.CONTACT_WITH_HISTORY)
                    } else {
                        startedInHistoryMode = false
                        switchViewMode(DetailViewMode.CONTACT_DETAILS)
                    }
                } else if (currentViewMode == DetailViewMode.CALL_HISTORY_ONLY) {
                    binding.tvNoCallHistory.beVisibleIf(logs.isEmpty())
                }
            }
        }
    }

    private fun fetchCallLogsForNumbers(targetNumbers: List<String>): ArrayList<ContactCallEntry> {
        val result = ArrayList<ContactCallEntry>()
        val normalizedTargets = targetNumbers.map { it.normalizePhoneNumber() }.filter { it.isNotEmpty() }
        val tailTargets = normalizedTargets.map {
            if (it.length >= COMPARABLE_PHONE_NUMBER_LENGTH) it.takeLast(COMPARABLE_PHONE_NUMBER_LENGTH) else it
        }.toSet()

        val projection = arrayOf(
            Calls._ID,
            Calls.NUMBER,
            Calls.DATE,
            Calls.DURATION,
            Calls.TYPE,
            Calls.PHONE_ACCOUNT_ID
        )

        val seenAccountIds = ArrayList<String>()
        try {
            val sortOrder = "${Calls.DATE} DESC"
            contentResolver.query(Calls.CONTENT_URI, projection, null, null, sortOrder)?.use { cursor ->
                val accountColIndex = cursor.getColumnIndex(Calls.PHONE_ACCOUNT_ID)
                var scanned = 0
                while (cursor.moveToNext() && scanned < 1000) {
                    scanned++
                    val number = cursor.getStringValue(Calls.NUMBER) ?: ""
                    if (number.isEmpty()) continue
                    val norm = number.normalizePhoneNumber()
                    val tail = if (norm.length >= COMPARABLE_PHONE_NUMBER_LENGTH) norm.takeLast(COMPARABLE_PHONE_NUMBER_LENGTH) else norm
                    val matches = normalizedTargets.contains(norm) || (tail.isNotEmpty() && tailTargets.contains(tail))
                    if (!matches) continue

                    val id = cursor.getIntValue(Calls._ID)
                    val date = cursor.getLongValue(Calls.DATE)
                    val duration = cursor.getIntValue(Calls.DURATION)
                    val type = cursor.getIntValue(Calls.TYPE)
                    val rawAccountId = if (accountColIndex >= 0) (cursor.getString(accountColIndex) ?: "") else ""
                    val simLabel = if (rawAccountId.isEmpty()) {
                        "SIM1"
                    } else {
                        if (!seenAccountIds.contains(rawAccountId)) {
                            seenAccountIds.add(rawAccountId)
                        }
                        val slotIdx = seenAccountIds.indexOf(rawAccountId)
                        if (slotIdx == 1 || rawAccountId.contains("2")) "SIM2" else "SIM1"
                    }

                    result.add(
                        ContactCallEntry(
                            id = id,
                            phoneNumber = number,
                            callType = type,
                            timestamp = date,
                            duration = duration,
                            simLabel = simLabel
                        )
                    )
                }
            }
        } catch (_: Exception) {
        }
        return result
    }

    private fun renderContactCallHistory() {
        val palette = HomeThemeHelper.getPalette(this)
        binding.contactHistoryCardsHolder.removeAllViews()
        if (contactCallLogs.isEmpty()) {
            return
        }

        val groupedByDate = LinkedHashMap<String, ArrayList<ContactCallEntry>>()
        val dateFormat = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault())
        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

        contactCallLogs.forEach { entry ->
            val dateHeader = dateFormat.format(Date(entry.timestamp))
            val list = groupedByDate.getOrPut(dateHeader) { ArrayList() }
            list.add(entry)
        }

        for ((dateTitle, entries) in groupedByDate) {
            val groupBinding = ItemContactCallHistoryGroupBinding.inflate(
                layoutInflater,
                binding.contactHistoryCardsHolder,
                false
            )
            groupBinding.historyGroupCard.background = HomeThemeHelper.createCardItemBackground(
                context = this,
                cardColor = palette.cardBg,
                isDark = palette.isDark,
                isFirst = true,
                isLast = true
            )
            groupBinding.historyGroupDate.text = dateTitle
            groupBinding.historyGroupDate.setTextColor(palette.itemTitleText)

            entries.forEach { entry ->
                val entryBinding = ItemContactCallHistoryEntryBinding.inflate(
                    layoutInflater,
                    groupBinding.historyGroupEntriesHolder,
                    false
                )
                val isBlocked = entry.callType == Calls.BLOCKED_TYPE ||
                    config.isLocalNumberBlocked(entry.phoneNumber) ||
                    isNumberBlocked(entry.phoneNumber)

                val iconRes = when {
                    isBlocked -> R.drawable.ic_call_blocked_vector
                    entry.callType == Calls.OUTGOING_TYPE -> R.drawable.ic_call_outgoing_vector
                    entry.callType == Calls.MISSED_TYPE || entry.callType == Calls.REJECTED_TYPE -> R.drawable.ic_call_missed_vector
                    else -> R.drawable.ic_call_incoming_vector
                }
                entryBinding.historyEntryTypeIcon.setImageResource(iconRes)

                entryBinding.historyEntryTime.text = timeFormat.format(Date(entry.timestamp)).uppercase(Locale.getDefault())
                entryBinding.historyEntryTime.setTextColor(palette.itemTitleText)

                val typeLabel = when {
                    isBlocked -> getString(R.string.blocked_call_label)
                    entry.callType == Calls.OUTGOING_TYPE -> getString(R.string.outgoing_call_label)
                    entry.callType == Calls.MISSED_TYPE -> getString(R.string.missed_call_label)
                    entry.callType == Calls.REJECTED_TYPE -> getString(R.string.rejected_call_label)
                    else -> getString(R.string.incoming_call_label)
                }
                val mins = entry.duration / 60
                val secs = entry.duration % 60
                val durationStr = getString(R.string.call_duration_mins_secs, mins, secs)
                entryBinding.historyEntrySubtitle.text = "$typeLabel, $durationStr  •  ${entry.simLabel}"
                entryBinding.historyEntrySubtitle.setTextColor(palette.itemSubtitleText)

                entryBinding.historyEntryRoot.setOnClickListener {
                    startCallIntent(entry.phoneNumber)
                }

                groupBinding.historyGroupEntriesHolder.addView(entryBinding.root)
            }

            binding.contactHistoryCardsHolder.addView(groupBinding.root)
        }
    }

    private fun launchEditContact(contact: Contact, editExactContact: Boolean) {
        wasEditLaunched = true
        duplicateInitialized = false
        if (editExactContact) {
            editContact(contact)
        } else {
            editContact(contact)
        }
    }

    private fun setupNames() {
        var displayName = contact!!.getNameToDisplay()
        if (contact!!.nickname.isNotEmpty()) {
            displayName += " (${contact!!.nickname})"
        }
        if (displayName.isBlank()) {
            displayName = contact!!.phoneNumbers.firstOrNull()?.value
                ?: intent.getStringExtra(KEY_PHONE).orEmpty()
        }

        binding.contactName.text = displayName
        binding.contactName.copyOnLongClick(displayName)
        binding.contactName.beVisibleIf(displayName.isNotEmpty())
    }

    private fun setupPhoneNumbers() {
        val palette = HomeThemeHelper.getPalette(this)
        var phoneNumbers = contact!!.phoneNumbers.toMutableSet() as LinkedHashSet<PhoneNumber>

        if (mergeDuplicate) {
            duplicateContacts.forEach {
                phoneNumbers.addAll(it.phoneNumbers)
            }
        }

        if (duplicateInitialized) {
            val contactDefaultsNumbers = contact!!.phoneNumbers.filter { it.isPrimary }
            val duplicateContactsDefaultNumbers = duplicateContacts.flatMap { it.phoneNumbers }.filter { it.isPrimary }
            val defaultNumbers = (contactDefaultsNumbers + duplicateContactsDefaultNumbers).toSet()

            if (defaultNumbers.size > 1 && defaultNumbers.distinctBy { it.normalizedNumber }.size > 1) {
                phoneNumbers.forEach { it.isPrimary = false }
            } else if (defaultNumbers.size == 1) {
                if (mergeDuplicate) {
                    val defaultNumber = defaultNumbers.first()
                    val candidate = phoneNumbers.find { it.normalizedNumber == defaultNumber.normalizedNumber && !it.isPrimary }
                    candidate?.isPrimary = true
                } else {
                    duplicateContactsDefaultNumbers.forEach { defaultNumber ->
                        val candidate = phoneNumbers.find { it.normalizedNumber == defaultNumber.normalizedNumber && !it.isPrimary }
                        candidate?.isPrimary = true
                    }
                }
            }
        }

        phoneNumbers = phoneNumbers.distinctBy {
            if (it.normalizedNumber.length >= COMPARABLE_PHONE_NUMBER_LENGTH) {
                it.normalizedNumber.substring(it.normalizedNumber.length - COMPARABLE_PHONE_NUMBER_LENGTH)
            } else {
                it.normalizedNumber
            }
        }.toMutableSet() as LinkedHashSet<PhoneNumber>

        phoneNumbers = phoneNumbers.sortedBy { it.type }.toMutableSet() as LinkedHashSet<PhoneNumber>
        fullContact!!.phoneNumbers = phoneNumbers.toMutableList() as ArrayList<PhoneNumber>
        binding.contactNumbersHolder.removeAllViews()

        if (phoneNumbers.isNotEmpty() && showFields and SHOW_PHONE_NUMBERS_FIELD != 0) {
            phoneNumbers.forEach { phoneNumber ->
                ItemViewPhoneNumberBinding.inflate(layoutInflater, binding.contactNumbersHolder, false).apply {
                    binding.contactNumbersHolder.addView(root)
                    if (config.formatPhoneNumbers) {
                        contactNumber.text = phoneNumber.value.formatPhoneNumber()
                    } else {
                        contactNumber.text = phoneNumber.value
                    }
                    contactNumber.setTextColor(palette.itemTitleText)
                    contactNumberType.text = getPhoneNumberTypeText(phoneNumber.type, phoneNumber.label)
                    contactNumberType.setTextColor(palette.itemSubtitleText)
                    ImageViewCompat.setImageTintList(rowIcon, ColorStateList.valueOf(palette.itemTitleText))
                    root.copyOnLongClick(phoneNumber.value)

                    root.setOnClickListener {
                        if (config.showCallConfirmation) {
                            CallConfirmationDialog(this@ViewContactActivity, phoneNumber.value) {
                                startCallIntent(phoneNumber.value)
                            }
                        } else {
                            startCallIntent(phoneNumber.value)
                        }
                    }

                    defaultToggleIcon.isVisible = phoneNumber.isPrimary
                    defaultToggleIcon.applyColorFilter(palette.itemTitleText)
                }
            }
            binding.contactNumbersHolder.beVisible()
        } else {
            binding.contactNumbersHolder.beGone()
        }
    }

    private fun setupEmails() {
        val palette = HomeThemeHelper.getPalette(this)
        binding.contactEmailsHolder.removeAllViews()
        val emails = contact!!.emails
        if (emails.isNotEmpty() && showFields and SHOW_EMAILS_FIELD != 0) {
            emails.forEach {
                ItemViewEmailBinding.inflate(layoutInflater, binding.contactEmailsHolder, false).apply {
                    val email = it
                    binding.contactEmailsHolder.addView(root)
                    val isRTL = TextUtils.getLayoutDirectionFromLocale(Locale.getDefault()) == View.LAYOUT_DIRECTION_RTL
                    contactEmail.gravity = if (isRTL) Gravity.END else Gravity.START
                    contactEmail.text = email.value
                    contactEmail.setTextColor(palette.itemTitleText)
                    contactEmailType.text = getString(R.string.email_label)
                    contactEmailType.setTextColor(palette.itemSubtitleText)
                    ImageViewCompat.setImageTintList(rowIcon, ColorStateList.valueOf(palette.itemTitleText))
                    root.copyOnLongClick(email.value)

                    root.setOnClickListener {
                        sendEmailIntent(email.value)
                    }
                }
            }
            binding.contactEmailsHolder.beVisible()
        } else {
            binding.contactEmailsHolder.beGone()
        }
    }

    private fun setupAddresses() {
        val palette = HomeThemeHelper.getPalette(this)
        var addresses = contact!!.addresses.toMutableSet() as LinkedHashSet<Address>

        if (mergeDuplicate) {
            duplicateContacts.forEach {
                addresses.addAll(it.addresses)
            }
        }

        addresses = addresses.sortedBy { it.type }.toMutableSet() as LinkedHashSet<Address>
        fullContact!!.addresses = addresses.toMutableList() as ArrayList<Address>
        binding.contactAddressesHolder.removeAllViews()

        if (addresses.isNotEmpty() && showFields and SHOW_ADDRESSES_FIELD != 0) {
            addresses.forEach {
                ItemViewAddressBinding.inflate(layoutInflater, binding.contactAddressesHolder, false).apply {
                    val address = it
                    binding.contactAddressesHolder.addView(root)
                    val isRTL = TextUtils.getLayoutDirectionFromLocale(Locale.getDefault()) == View.LAYOUT_DIRECTION_RTL
                    contactAddress.gravity = if (isRTL) Gravity.END else Gravity.START
                    contactAddress.text = address.value
                    contactAddress.setTextColor(palette.itemTitleText)
                    contactAddressType.text = getString(R.string.place_label)
                    contactAddressType.setTextColor(palette.itemSubtitleText)
                    ImageViewCompat.setImageTintList(rowIcon, ColorStateList.valueOf(palette.itemTitleText))
                    root.copyOnLongClick(address.value)

                    root.setOnClickListener {
                        sendAddressIntent(address.value)
                    }
                }
            }
            binding.contactAddressesHolder.beVisible()
        } else {
            binding.contactAddressesHolder.beGone()
        }
    }

    private fun setupIMs() {
        val palette = HomeThemeHelper.getPalette(this)
        var IMs = contact!!.IMs.toMutableSet() as LinkedHashSet<IM>

        if (mergeDuplicate) {
            duplicateContacts.forEach {
                IMs.addAll(it.IMs)
            }
        }

        IMs = IMs.sortedBy { it.type }.toMutableSet() as LinkedHashSet<IM>
        fullContact!!.IMs = IMs.toMutableList() as ArrayList<IM>
        binding.contactImsHolder.removeAllViews()

        if (IMs.isNotEmpty() && showFields and SHOW_IMS_FIELD != 0) {
            IMs.forEach {
                ItemViewImBinding.inflate(layoutInflater, binding.contactImsHolder, false).apply {
                    binding.contactImsHolder.addView(root)
                    val isRTL = TextUtils.getLayoutDirectionFromLocale(Locale.getDefault()) == View.LAYOUT_DIRECTION_RTL
                    contactIm.gravity = if (isRTL) Gravity.END else Gravity.START

                    contactIm.text = it.value
                    contactIm.setTextColor(palette.itemTitleText)
                    contactImType.text = getIMTypeText(it.type, it.label)
                    contactImType.setTextColor(palette.itemSubtitleText)
                    ImageViewCompat.setImageTintList(rowIcon, ColorStateList.valueOf(palette.itemTitleText))
                    root.copyOnLongClick(it.value)
                }
            }
            binding.contactImsHolder.beVisible()
        } else {
            binding.contactImsHolder.beGone()
        }
    }

    private fun setupEvents() {
        val palette = HomeThemeHelper.getPalette(this)
        var events = contact!!.events.toMutableSet() as LinkedHashSet<Event>

        if (mergeDuplicate) {
            duplicateContacts.forEach {
                events.addAll(it.events)
            }
        }

        events = events.sortedBy { it.type }.toMutableSet() as LinkedHashSet<Event>
        fullContact!!.events = events.toMutableList() as ArrayList<Event>
        binding.contactEventsHolder.removeAllViews()

        if (events.isNotEmpty() && showFields and SHOW_EVENTS_FIELD != 0) {
            events.forEach {
                ItemViewEventBinding.inflate(layoutInflater, binding.contactEventsHolder, false).apply {
                    binding.contactEventsHolder.addView(root)
                    it.value.getDateTimeFromDateString(true, contactEvent)
                    contactEvent.setTextColor(palette.itemTitleText)
                    contactEventType.setText(getEventTextId(it.type))
                    contactEventType.setTextColor(palette.itemSubtitleText)
                    ImageViewCompat.setImageTintList(rowIcon, ColorStateList.valueOf(palette.itemTitleText))
                    root.copyOnLongClick(it.value)
                }
            }
            binding.contactEventsHolder.beVisible()
        } else {
            binding.contactEventsHolder.beGone()
        }
    }

    private fun setupWebsites() {
        val palette = HomeThemeHelper.getPalette(this)
        var websites = contact!!.websites.toMutableSet() as LinkedHashSet<String>

        if (mergeDuplicate) {
            duplicateContacts.forEach {
                websites.addAll(it.websites)
            }
        }

        websites = websites.sorted().toMutableSet() as LinkedHashSet<String>
        fullContact!!.websites = websites.toMutableList() as ArrayList<String>
        binding.contactWebsitesHolder.removeAllViews()

        if (websites.isNotEmpty() && showFields and SHOW_WEBSITES_FIELD != 0) {
            websites.forEach {
                val url = it
                ItemWebsiteBinding.inflate(layoutInflater, binding.contactWebsitesHolder, false).apply {
                    binding.contactWebsitesHolder.addView(root)
                    val isRTL = TextUtils.getLayoutDirectionFromLocale(Locale.getDefault()) == View.LAYOUT_DIRECTION_RTL
                    contactWebsite.gravity = if (isRTL) Gravity.END else Gravity.START
                    contactWebsite.text = url
                    contactWebsite.setTextColor(palette.itemTitleText)
                    contactWebsiteType.text = getString(R.string.website_label)
                    contactWebsiteType.setTextColor(palette.itemSubtitleText)
                    ImageViewCompat.setImageTintList(rowIcon, ColorStateList.valueOf(palette.itemTitleText))
                    root.copyOnLongClick(url)

                    root.setOnClickListener {
                        openWebsiteIntent(url)
                    }
                }
            }
            binding.contactWebsitesHolder.beVisible()
        } else {
            binding.contactWebsitesHolder.beGone()
        }
    }

    private fun setupGroups() {
        val palette = HomeThemeHelper.getPalette(this)
        var groups = contact!!.groups.toMutableSet() as LinkedHashSet<Group>

        if (mergeDuplicate) {
            duplicateContacts.forEach {
                groups.addAll(it.groups)
            }
        }

        groups = groups.sortedBy { it.title }.toMutableSet() as LinkedHashSet<Group>
        fullContact!!.groups = groups.toMutableList() as ArrayList<Group>
        binding.contactGroupsHolder.removeAllViews()

        if (groups.isNotEmpty() && showFields and SHOW_GROUPS_FIELD != 0) {
            groups.forEach {
                ItemViewGroupBinding.inflate(layoutInflater, binding.contactGroupsHolder, false).apply {
                    binding.contactGroupsHolder.addView(root)
                    val isRTL = TextUtils.getLayoutDirectionFromLocale(Locale.getDefault()) == View.LAYOUT_DIRECTION_RTL
                    contactGroup.gravity = if (isRTL) Gravity.END else Gravity.START
                    contactGroup.text = it.title
                    contactGroup.setTextColor(palette.itemTitleText)
                    contactGroupType.setTextColor(palette.itemSubtitleText)
                    ImageViewCompat.setImageTintList(rowIcon, ColorStateList.valueOf(palette.itemTitleText))
                    root.copyOnLongClick(it.title)
                }
            }
            binding.contactGroupsHolder.beVisible()
        } else {
            binding.contactGroupsHolder.beGone()
        }
    }

    private fun setupContactSources() {
        val palette = HomeThemeHelper.getPalette(this)
        binding.contactSourcesHolder.removeAllViews()
        if (showFields and SHOW_CONTACT_SOURCE_FIELD != 0 && contact!!.id != 0) {
            var sources = HashMap<Contact, String>()
            sources[contact!!] = getPublicContactSourceSync(contact!!.source, contactSources)

            if (mergeDuplicate) {
                duplicateContacts.forEach {
                    sources[it] = getPublicContactSourceSync(it.source, contactSources)
                }
            }

            if (sources.size > 1) {
                sources = sources.toList().sortedBy { (_, value) -> value.lowercase(Locale.getDefault()) }.toMap() as LinkedHashMap<Contact, String>
            }

            for ((key, value) in sources) {
                ItemViewContactSourceBinding.inflate(layoutInflater, binding.contactSourcesHolder, false).apply {
                    contactSource.copyOnLongClick(value)
                    binding.contactSourcesHolder.addView(root)
                    val isRTL = TextUtils.getLayoutDirectionFromLocale(Locale.getDefault()) == View.LAYOUT_DIRECTION_RTL
                    contactSource.gravity = if (isRTL) Gravity.END else Gravity.START
                    contactSource.text = if (value == "") getString(R.string.phone_storage) else value
                    contactSource.setTextColor(palette.itemTitleText)
                    contactSourceType.setTextColor(palette.itemSubtitleText)
                    ImageViewCompat.setImageTintList(rowIcon, ColorStateList.valueOf(palette.itemTitleText))

                    contactSource.setOnClickListener {
                        launchEditContact(key, true)
                    }

                    if (value.lowercase(Locale.getDefault()) == WHATSAPP) {
                        contactSourceImage.setImageDrawable(getPackageDrawable(WHATSAPP_PACKAGE))
                        contactSourceImage.beVisible()
                        contactSourceImage.setOnClickListener {
                            showSocialActions(key.id)
                        }
                    }

                    if (value.lowercase(Locale.getDefault()) == SIGNAL) {
                        contactSourceImage.setImageDrawable(getPackageDrawable(SIGNAL_PACKAGE))
                        contactSourceImage.beVisible()
                        contactSourceImage.setOnClickListener {
                            showSocialActions(key.id)
                        }
                    }

                    if (value.lowercase(Locale.getDefault()) == VIBER) {
                        contactSourceImage.setImageDrawable(getPackageDrawable(VIBER_PACKAGE))
                        contactSourceImage.beVisible()
                        contactSourceImage.setOnClickListener {
                            showSocialActions(key.id)
                        }
                    }

                    if (value.lowercase(Locale.getDefault()) == TELEGRAM) {
                        contactSourceImage.setImageDrawable(getPackageDrawable(TELEGRAM_PACKAGE))
                        contactSourceImage.beVisible()
                        contactSourceImage.setOnClickListener {
                            showSocialActions(key.id)
                        }
                    }

                    if (value.lowercase(Locale.getDefault()) == THREEMA) {
                        contactSourceImage.setImageDrawable(getPackageDrawable(THREEMA_PACKAGE))
                        contactSourceImage.beVisible()
                        contactSourceImage.setOnClickListener {
                            showSocialActions(key.id)
                        }
                    }
                }
            }

            binding.contactSourcesHolder.beVisible()
        } else {
            binding.contactSourcesHolder.beGone()
        }
    }

    private fun setupNotes() {
        val palette = HomeThemeHelper.getPalette(this)
        val notes = contact!!.notes
        if (notes.isNotEmpty() && showFields and SHOW_NOTES_FIELD != 0) {
            val isRTL = TextUtils.getLayoutDirectionFromLocale(Locale.getDefault()) == View.LAYOUT_DIRECTION_RTL
            binding.contactNotes.gravity = if (isRTL) Gravity.END else Gravity.START
            binding.contactNotes.text = notes
            binding.contactNotes.setTextColor(palette.itemTitleText)
            binding.contactNotesLabel.setTextColor(palette.itemSubtitleText)
            ImageViewCompat.setImageTintList(binding.contactNotesImage, ColorStateList.valueOf(palette.itemTitleText))
            binding.contactNotesRow.beVisible()
            binding.contactNotes.beVisible()
            binding.contactNotesRow.copyOnLongClick(notes)
        } else {
            binding.contactNotesRow.beGone()
        }
    }

    private fun setupRingtone() {
        val palette = HomeThemeHelper.getPalette(this)
        if (showFields and SHOW_RINGTONE_FIELD != 0 && contact!!.id != 0) {
            binding.contactRingtoneRow.beVisible()
            binding.contactRingtone.beVisible()
            binding.contactRingtone.setTextColor(palette.itemTitleText)
            binding.contactRingtoneLabel.setTextColor(palette.itemSubtitleText)
            ImageViewCompat.setImageTintList(binding.contactRingtoneImage, ColorStateList.valueOf(palette.itemTitleText))

            val ringtone = contact!!.ringtone
            if (ringtone?.isEmpty() == true) {
                binding.contactRingtone.text = getString(org.fossify.commons.R.string.no_sound)
            } else if (ringtone?.isNotEmpty() == true && ringtone != getDefaultRingtoneUri().toString()) {
                if (ringtone == SILENT) {
                    binding.contactRingtone.text = getString(org.fossify.commons.R.string.no_sound)
                } else {
                    systemRingtoneSelected(ringtone.toUri())
                }
            } else {
                binding.contactRingtoneRow.beGone()
                return
            }

            binding.contactRingtoneRow.copyOnLongClick(binding.contactRingtone.text.toString())

            binding.contactRingtoneRow.setOnClickListener {
                val ringtonePickerIntent = getRingtonePickerIntent()
                try {
                    startActivityForResult(ringtonePickerIntent, INTENT_SELECT_RINGTONE)
                } catch (_: Exception) {
                    val currentRingtone = contact!!.ringtone ?: getDefaultAlarmSound(RingtoneManager.TYPE_RINGTONE).uri
                    SelectAlarmSoundDialog(
                        this@ViewContactActivity,
                        currentRingtone,
                        AudioManager.STREAM_RING,
                        PICK_RINGTONE_INTENT_ID,
                        RingtoneManager.TYPE_RINGTONE,
                        true,
                        onAlarmPicked = {
                            binding.contactRingtone.text = it?.title
                            ringtoneUpdated(it?.uri)
                        },
                        onAlarmSoundDeleted = {}
                    )
                }
            }
        } else {
            binding.contactRingtoneRow.beGone()
        }
    }

    private fun setupOrganization() {
        val palette = HomeThemeHelper.getPalette(this)
        val organization = contact!!.organization
        if (organization.isNotEmpty() && showFields and SHOW_ORGANIZATION_FIELD != 0) {
            binding.contactOrganizationCompany.text = organization.company
            binding.contactOrganizationCompany.setTextColor(palette.itemTitleText)

            val isRTL = TextUtils.getLayoutDirectionFromLocale(Locale.getDefault()) == View.LAYOUT_DIRECTION_RTL
            binding.contactOrganizationCompany.gravity = if (isRTL) Gravity.END else Gravity.START

            binding.contactOrganizationJobPosition.text = organization.jobPosition
            binding.contactOrganizationJobPosition.setTextColor(palette.itemSubtitleText)
            binding.contactOrganizationJobPosition.gravity = if (isRTL) Gravity.END else Gravity.START

            ImageViewCompat.setImageTintList(binding.contactOrganizationImage, ColorStateList.valueOf(palette.itemTitleText))
            binding.contactOrganizationRow.beVisible()
            binding.contactOrganizationCompany.beGoneIf(organization.company.isEmpty())
            binding.contactOrganizationJobPosition.beGoneIf(organization.jobPosition.isEmpty())
            binding.contactOrganizationCompany.copyOnLongClick(binding.contactOrganizationCompany.value)
            binding.contactOrganizationJobPosition.copyOnLongClick(binding.contactOrganizationJobPosition.value)
        } else {
            binding.contactOrganizationRow.beGone()
        }
    }

    private fun showSocialActions(contactId: Int) {
        ensureBackgroundThread {
            val actions = getSocialActions(contactId)
            runOnUiThread {
                if (!isDestroyed && !isFinishing) {
                    ChooseSocialDialog(this@ViewContactActivity, actions) { action ->
                        Intent(Intent.ACTION_VIEW).apply {
                            val uri = ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, action.dataId)
                            setDataAndType(uri, action.mimetype)
                            flags = Intent.FLAG_ACTIVITY_CLEAR_TASK
                            try {
                                startActivity(this)
                            } catch (_: SecurityException) {
                                handlePermission(PERMISSION_CALL_PHONE) { success ->
                                    if (success) {
                                        startActivity(this)
                                    } else {
                                        toast(org.fossify.commons.R.string.no_phone_call_permission)
                                    }
                                }
                            } catch (_: ActivityNotFoundException) {
                                toast(org.fossify.commons.R.string.no_app_found)
                            } catch (e: Exception) {
                                showErrorToast(e)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun customRingtoneSelected(ringtonePath: String) {
        binding.contactRingtone.text = ringtonePath.getFilenameFromPath()
        ringtoneUpdated(ringtonePath)
    }

    override fun systemRingtoneSelected(uri: Uri?) {
        val contactRingtone = RingtoneManager.getRingtone(this, uri)
        binding.contactRingtone.text = contactRingtone.getTitle(this)
        ringtoneUpdated(uri?.toString() ?: "")
    }

    private fun ringtoneUpdated(path: String?) {
        contact!!.ringtone = path

        ensureBackgroundThread {
            if (contact!!.isPrivate()) {
                LocalContactsHelper(this).updateRingtone(contact!!.contactId, path ?: "")
            } else {
                ContactsHelper(this).updateRingtone(contact!!.contactId.toString(), path ?: "")
            }
        }
    }

    private fun initializeDuplicateContacts(callback: () -> Unit) {
        if (contact!!.id == 0) {
            duplicateContacts = ArrayList()
            callback()
            return
        }
        getDuplicateContacts(contact!!, false) {
            duplicateContacts = it
            runOnUiThread {
                callback()
            }
        }
    }

    @SuppressLint("StringFormatInvalid")
    private fun deleteContactWithMergeLogic() {
        ContactActionConfirmDialog(
            activity = this,
            title = getString(R.string.are_you_sure_title),
            message = getString(R.string.delete_contact_message),
            positiveText = getString(org.fossify.commons.R.string.delete)
        ) {
            val pBar = ProgressDialog(this)
            pBar.setMessage(getString(R.string.deleting_contacts_progress, 1))
            pBar.setCancelable(false)
            pBar.show()
            if (contact != null) {
                ContactsHelper(this).deleteContact(contact!!, mergeDuplicate) {
                    runOnUiThread {
                        pBar.dismiss()
                        finish()
                    }
                }
            } else {
                Toast.makeText(this@ViewContactActivity, "Invalid contact to delete ", Toast.LENGTH_SHORT).show()
                pBar.dismiss()
            }
        }
    }

    private fun hideBigContactPhoto() {
        binding.contactPhotoBig.animate().alpha(0f).withEndAction { binding.contactPhotoBig.beGone() }.start()
    }

    private fun View.copyOnLongClick(value: String) {
        setOnLongClickListener {
            copyToClipboard(value)
            true
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.frameAd.destroy()
    }
}
