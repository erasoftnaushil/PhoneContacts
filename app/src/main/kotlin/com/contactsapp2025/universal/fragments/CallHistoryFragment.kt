package com.contactsapp2025.universal.fragments

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.provider.CallLog.Calls
import android.util.AttributeSet
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.ImageViewCompat
import com.contactsapp2025.universal.activities.EditContactActivity
import com.contactsapp2025.universal.activities.SimpleActivity
import com.contactsapp2025.universal.adapters.CallHistoryAdapter
import com.contactsapp2025.universal.adapters.CallLogItem
import com.contactsapp2025.universal.databinding.FragmentCallHistoryBinding
import com.contactsapp2025.universal.databinding.FragmentLayoutBinding
import com.contactsapp2025.universal.extensions.config
import com.contactsapp2025.universal.helpers.HomeThemeHelper
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.PERMISSION_READ_CALL_LOG
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.helpers.getProperText
import org.fossify.commons.models.contacts.Contact
import java.util.Calendar

class CallHistoryFragment(context: Context, attributeSet: AttributeSet) :
    MyViewPagerFragment<MyViewPagerFragment.FragmentLayout>(context, attributeSet) {

    enum class CallFilter {
        ALL, OUTGOING, INCOMING, MISSED, BLOCKED
    }

    private lateinit var binding: FragmentCallHistoryBinding
    private var allCallItems = ArrayList<CallLogItem>()
    private var currentFilter = CallFilter.ALL
    private var currentSearchQuery = ""

    override fun onFinishInflate() {
        super.onFinishInflate()
        binding = FragmentCallHistoryBinding.bind(this)
        innerBinding = FragmentLayout(FragmentLayoutBinding.bind(binding.root))
        setupFilterChips()
    }

    private fun setupFilterChips() {
        binding.chipAll.setOnClickListener { selectFilter(CallFilter.ALL) }
        binding.chipOutgoing.setOnClickListener { selectFilter(CallFilter.OUTGOING) }
        binding.chipIncoming.setOnClickListener { selectFilter(CallFilter.INCOMING) }
        binding.chipMissed.setOnClickListener { selectFilter(CallFilter.MISSED) }
        binding.chipBlocked.setOnClickListener { selectFilter(CallFilter.BLOCKED) }
        updateChipStyles()
    }

    fun selectFilterFromMenu(filter: CallFilter) {
        currentFilter = filter
        updateChipStyles()
        applyFiltersAndUpdateAdapter()
    }

    fun getCurrentFilter(): CallFilter = currentFilter

    private fun selectFilter(filter: CallFilter) {
        if (currentFilter == filter) return
        currentFilter = filter
        updateChipStyles()
        applyFiltersAndUpdateAdapter()
    }

    fun updateChipStyles() {
        if (!::binding.isInitialized) return
        val palette = HomeThemeHelper.getPalette(context)
        binding.callHistoryFragment.setBackgroundColor(palette.pageBg)
        innerBinding.fragmentWrapper.setBackgroundColor(palette.pageBg)
        innerBinding.fragmentList.setBackgroundColor(palette.pageBg)

        val missedRedColor = Color.parseColor("#EF4444")

        fun styleChip(
            chipLayout: LinearLayout,
            iconView: ImageView,
            textView: TextView,
            isSelected: Boolean,
            unselectedIconColor: Int = palette.chipUnselectedText
        ) {
            val bgColor = if (isSelected) palette.primaryBlue else palette.chipUnselectedBg
            chipLayout.background = HomeThemeHelper.createChipBackground(context, bgColor)
            val textColor = if (isSelected) Color.WHITE else palette.chipUnselectedText
            val iconColor = if (isSelected) Color.WHITE else unselectedIconColor
            textView.setTextColor(textColor)
            ImageViewCompat.setImageTintList(iconView, ColorStateList.valueOf(iconColor))
        }

        styleChip(binding.chipAll, binding.chipAllIcon, binding.chipAllText, currentFilter == CallFilter.ALL)
        styleChip(binding.chipOutgoing, binding.chipOutgoingIcon, binding.chipOutgoingText, currentFilter == CallFilter.OUTGOING)
        styleChip(binding.chipIncoming, binding.chipIncomingIcon, binding.chipIncomingText, currentFilter == CallFilter.INCOMING)
        styleChip(binding.chipMissed, binding.chipMissedIcon, binding.chipMissedText, currentFilter == CallFilter.MISSED, missedRedColor)
        styleChip(binding.chipBlocked, binding.chipBlockedIcon, binding.chipBlockedText, currentFilter == CallFilter.BLOCKED)

        (innerBinding.fragmentList.adapter as? CallHistoryAdapter)?.notifyDataSetChanged()
    }

    override fun fabClicked() {
        activity?.hideKeyboard()
        Intent(context, EditContactActivity::class.java).apply {
            context.startActivity(this)
        }
    }

    override fun placeholderClicked() {
        val act = activity ?: return
        if (!act.hasPermission(PERMISSION_READ_CALL_LOG)) {
            act.handlePermission(PERMISSION_READ_CALL_LOG) { granted ->
                if (granted) {
                    refreshCallHistory(allContacts)
                }
            }
        }
    }

    fun refreshCallHistory(contacts: ArrayList<Contact>) {
        val act = activity ?: return
        if (!act.hasPermission(PERMISSION_READ_CALL_LOG)) {
            act.runOnUiThread {
                innerBinding.fragmentList.beGone()
                innerBinding.fragmentPlaceholder.text = act.getString(org.fossify.commons.R.string.no_permission)
                innerBinding.fragmentPlaceholder.beVisible()
                innerBinding.fragmentPlaceholder2.text = act.getString(org.fossify.commons.R.string.grant_permission)
                innerBinding.fragmentPlaceholder2.beVisible()
            }
            return
        }

        ensureBackgroundThread {
            val items = fetchCallLogs(act, contacts)
            act.runOnUiThread {
                allCallItems = items
                applyFiltersAndUpdateAdapter()
            }
        }
    }

    private fun getFilteredItems(): ArrayList<CallLogItem> {
        val byType = when (currentFilter) {
            CallFilter.ALL -> allCallItems
            CallFilter.OUTGOING -> allCallItems.filter { it.callType == Calls.OUTGOING_TYPE }
            CallFilter.INCOMING -> allCallItems.filter { it.callType == Calls.INCOMING_TYPE }
            CallFilter.MISSED -> allCallItems.filter { it.callType == Calls.MISSED_TYPE || it.callType == Calls.REJECTED_TYPE }
            CallFilter.BLOCKED -> allCallItems.filter {
                it.callType == Calls.BLOCKED_TYPE ||
                    context.config.isLocalNumberBlocked(it.phoneNumber) ||
                    context.isNumberBlocked(it.phoneNumber)
            }
        }

        if (currentSearchQuery.isEmpty()) {
            return ArrayList(byType)
        }

        val fixedText = currentSearchQuery
        val shouldNormalize = fixedText.normalizeString() == fixedText
        val normPhone = fixedText.normalizePhoneNumber()
        return ArrayList(
            byType.filter {
                getProperText(it.getDisplayTitle(), shouldNormalize).contains(fixedText, true) ||
                    it.phoneNumber.contains(fixedText, true) ||
                    (normPhone.isNotEmpty() && it.phoneNumber.normalizePhoneNumber().contains(normPhone, true))
            }
        )
    }

    fun applyFiltersAndUpdateAdapter() {
        val filtered = getFilteredItems()
        val highlight = if (currentSearchQuery.isNotEmpty()) currentSearchQuery.normalizeString() else ""
        setupCallHistoryAdapter(filtered, highlight)
    }

    private fun fetchCallLogs(context: Context, contacts: List<Contact>): ArrayList<CallLogItem> {
        val result = ArrayList<CallLogItem>()
        val projection = arrayOf(
            Calls._ID,
            Calls.NUMBER,
            Calls.CACHED_NAME,
            Calls.CACHED_PHOTO_URI,
            Calls.DATE,
            Calls.DURATION,
            Calls.TYPE,
            Calls.PHONE_ACCOUNT_ID
        )

        val contactMap = HashMap<String, Contact>()
        contacts.forEach { contact ->
            contact.phoneNumbers.forEach { phone ->
                val normalized = phone.normalizedNumber.ifEmpty { phone.value.normalizePhoneNumber() }
                if (normalized.isNotEmpty()) {
                    contactMap[normalized] = contact
                    if (normalized.length >= 9) {
                        contactMap[normalized.takeLast(9)] = contact
                    }
                }
            }
        }

        val seenAccountIds = ArrayList<String>()

        try {
            val sortOrder = "${Calls.DATE} DESC"
            context.contentResolver.query(Calls.CONTENT_URI, projection, null, null, sortOrder)?.use { cursor ->
                var count = 0
                val accountColIndex = cursor.getColumnIndex(Calls.PHONE_ACCOUNT_ID)
                while (cursor.moveToNext() && count < 500) {
                    val id = cursor.getIntValue(Calls._ID)
                    val number = cursor.getStringValue(Calls.NUMBER) ?: ""
                    if (number.isEmpty()) continue

                    val cachedName = cursor.getStringValue(Calls.CACHED_NAME) ?: ""
                    val cachedPhotoUri = cursor.getStringValue(Calls.CACHED_PHOTO_URI) ?: ""
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

                    val normalized = number.normalizePhoneNumber()
                    val matchedContact = contactMap[normalized]
                        ?: if (normalized.length >= 9) contactMap[normalized.takeLast(9)] else null

                    val resolvedName = matchedContact?.getNameToDisplay()?.ifEmpty { cachedName } ?: cachedName
                    val resolvedPhotoUri = matchedContact?.photoUri?.ifEmpty { cachedPhotoUri } ?: cachedPhotoUri

                    val lastItem = result.lastOrNull()
                    val lastNormalized = lastItem?.phoneNumber?.normalizePhoneNumber() ?: ""
                    val sameNumber = normalized.isNotEmpty() && (
                        normalized == lastNormalized ||
                            (normalized.length >= 9 && lastNormalized.length >= 9 && normalized.takeLast(9) == lastNormalized.takeLast(9))
                        )

                    if (lastItem != null && sameNumber && lastItem.callType == type && isSameDay(lastItem.timestamp, date)) {
                        result[result.lastIndex] = lastItem.copy(callCount = lastItem.callCount + 1)
                    } else {
                        result.add(
                            CallLogItem(
                                id = id,
                                name = resolvedName,
                                phoneNumber = number,
                                callType = type,
                                timestamp = date,
                                duration = duration,
                                photoUri = resolvedPhotoUri,
                                matchedContact = matchedContact,
                                callCount = 1,
                                simLabel = simLabel
                            )
                        )
                    }
                    count++
                }
            }
        } catch (_: Exception) {
        }

        return result
    }

    private fun isSameDay(timestamp1: Long, timestamp2: Long): Boolean {
        val cal1 = Calendar.getInstance().apply { timeInMillis = timestamp1 }
        val cal2 = Calendar.getInstance().apply { timeInMillis = timestamp2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
            cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    private fun setupCallHistoryAdapter(items: ArrayList<CallLogItem>, highlightText: String = "") {
        val act = activity as? SimpleActivity ?: return
        val palette = HomeThemeHelper.getPalette(act)
        innerBinding.fragmentPlaceholder.text = act.getString(org.fossify.commons.R.string.no_items_found)
        innerBinding.fragmentPlaceholder.setTextColor(palette.itemSubtitleText)
        innerBinding.fragmentPlaceholder.beVisibleIf(items.isEmpty())
        innerBinding.fragmentPlaceholder2.beGone()
        innerBinding.fragmentList.beVisibleIf(items.isNotEmpty())

        val currAdapter = innerBinding.fragmentList.adapter as? CallHistoryAdapter
        if (currAdapter == null || forceListRedraw) {
            forceListRedraw = false
            CallHistoryAdapter(
                simpleActivity = act,
                callItems = items,
                recyclerView = innerBinding.fragmentList,
                onBlockStateChanged = {
                    applyFiltersAndUpdateAdapter()
                }
            ) { clicked ->
                val callLogItem = clicked as? CallLogItem ?: return@CallHistoryAdapter
                (innerBinding.fragmentList.adapter as? CallHistoryAdapter)?.toggleExpanded(callLogItem.id)
            }.apply {
                if (highlightText.isNotEmpty()) {
                    updateItems(items, highlightText)
                }
                innerBinding.fragmentList.adapter = this
            }

            if (context.areSystemAnimationsEnabled) {
                innerBinding.fragmentList.scheduleLayoutAnimation()
            }
        } else {
            currAdapter.apply {
                showContactThumbnails = context.config.showContactThumbnails
                fontSize = act.getTextSize()
                updateItems(items, highlightText)
            }
        }
    }

    fun onCallHistorySearchQueryChanged(fixedText: String) {
        currentSearchQuery = fixedText
        applyFiltersAndUpdateAdapter()
    }

    fun onCallHistorySearchClosed() {
        currentSearchQuery = ""
        applyFiltersAndUpdateAdapter()
    }
}
