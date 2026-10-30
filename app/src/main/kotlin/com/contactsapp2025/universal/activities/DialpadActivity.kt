package com.contactsapp2025.universal.activities

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.provider.CallLog.Calls
import android.provider.ContactsContract
import android.telephony.PhoneNumberFormattingTextWatcher
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.activity.addCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.contactsapp2025.universal.R
import com.contactsapp2025.universal.adapters.CallLogItem
import com.contactsapp2025.universal.adapters.DialpadAction
import com.contactsapp2025.universal.adapters.DialpadAdapter
import com.contactsapp2025.universal.adapters.DialpadListItem
import com.contactsapp2025.universal.databinding.ActivityDialpadBinding
import com.contactsapp2025.universal.databinding.ItemDialpadKeyBinding
import com.contactsapp2025.universal.extensions.config
import com.contactsapp2025.universal.extensions.startCallIntent
import com.contactsapp2025.universal.helpers.HomePalette
import com.contactsapp2025.universal.helpers.HomeThemeHelper
import org.fossify.commons.dialogs.CallConfirmationDialog
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.ContactsHelper
import org.fossify.commons.helpers.KEY_PHONE
import org.fossify.commons.helpers.PERMISSION_READ_CALL_LOG
import org.fossify.commons.helpers.PERMISSION_READ_CONTACTS
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.models.contacts.Contact
import java.util.Calendar
import java.util.Locale
import androidx.core.graphics.toColorInt

/**
 * In-app dialer opened from the dial button on the home screen.
 *
 * - Empty input  -> recent calls grouped by day (Today / Yesterday / date)
 * - Typed digits -> contacts whose number or T9 name matches
 * - No match     -> Create new contact / Add to a contact / Send a message
 */
class DialpadActivity : SimpleActivity() {

    companion object {
        private const val MAX_RECENTS = 150
        private const val TONE_LENGTH_MS = 120
        private val T9 = mapOf(
            'a' to '2', 'b' to '2', 'c' to '2',
            'd' to '3', 'e' to '3', 'f' to '3',
            'g' to '4', 'h' to '4', 'i' to '4',
            'j' to '5', 'k' to '5', 'l' to '5',
            'm' to '6', 'n' to '6', 'o' to '6',
            'p' to '7', 'q' to '7', 'r' to '7', 's' to '7',
            't' to '8', 'u' to '8', 'v' to '8',
            'w' to '9', 'x' to '9', 'y' to '9', 'z' to '9'
        )
    }

    private val binding by viewBinding(ActivityDialpadBinding::inflate)
    private lateinit var adapter: DialpadAdapter
    private lateinit var palette: HomePalette

    private var allContacts = ArrayList<Contact>()
    private var recents = ArrayList<CallLogItem>()
    private var toneGenerator: ToneGenerator? = null
    private var isSearchMode = false
    private var isKeypadVisible = true

    override fun onCreate(savedInstanceState: Bundle?) {
        isMaterialActivity = true
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        palette = HomeThemeHelper.getPalette(this)

        applyWindowInsets()
        setupList()
        setupInput()
        setupKeypad()
        setupTopBar()
        applyPalette()

        onBackPressedDispatcher.addCallback(this) {
            when {
                isSearchMode -> closeSearch()
                !isKeypadVisible -> showKeypad(true)
                else -> finish()
            }
        }

        // number passed in (e.g. from a tel: link or another screen)
        val initialNumber = intent.getStringExtra(KEY_PHONE) ?: intent.data?.schemeSpecificPart
        if (!initialNumber.isNullOrEmpty()) {
            binding.dialpadInput.setText(initialNumber)
            binding.dialpadInput.setSelection(binding.dialpadInput.text.length)
        }

        loadData()
    }

    override fun onResume() {
        super.onResume()
        palette = HomeThemeHelper.getPalette(this)
        applyPalette()
        initToneGenerator()
    }

    override fun onPause() {
        super.onPause()
        toneGenerator?.release()
        toneGenerator = null
    }

    // region setup

    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.dialpadRoot) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.updatePadding(top = bars.top, bottom = maxOf(bars.bottom, ime.bottom))
            insets
        }
    }

    private fun setupList() {
        adapter = DialpadAdapter(
            activity = this,
            onCallNumber = { name, number -> placeCall(number, name) },
            onEntryClick = { number -> fillNumber(number) },
            onActionClick = { handleAction(it) }
        )
        binding.dialpadList.adapter = adapter
        binding.dialpadList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                // collapse the keypad while the user browses the list, like the stock dialer
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING && isKeypadVisible && !isSearchMode) {
                    showKeypad(false)
                }
            }
        })
        binding.dialpadShowKeypad.setOnClickListener { showKeypad(true) }
    }

    @SuppressLint("ClickableViewAccessibility", "ThreadConstraint")
    private fun setupInput() {
        binding.dialpadInput.apply {
            showSoftInputOnFocus = false // keep the system keyboard hidden, the dialpad is the keyboard
            addTextChangedListener(PhoneNumberFormattingTextWatcher(Locale.getDefault().country))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    updateNumberRow()
                    refreshList()
                }
            })
            requestFocus()
        }

        binding.dialpadBackspace.setOnClickListener {
            it.performHapticFeedback()
            deleteChar()
        }
        binding.dialpadBackspace.setOnLongClickListener {
            binding.dialpadInput.text.clear()
            true
        }
        binding.dialpadAddContact.setOnClickListener { handleAction(DialpadAction.CREATE_CONTACT) }
        binding.dialpadCallButton.setOnClickListener { onCallButtonClicked() }
        updateNumberRow()
    }

    private fun setupKeypad() {
        val keys = listOf(
            Triple(binding.dialpadKey1, '1', "∞∞"), // voicemail-style glyph like the mockup
            Triple(binding.dialpadKey2, '2', "ABC"),
            Triple(binding.dialpadKey3, '3', "DEF"),
            Triple(binding.dialpadKey4, '4', "GHI"),
            Triple(binding.dialpadKey5, '5', "JKL"),
            Triple(binding.dialpadKey6, '6', "MNO"),
            Triple(binding.dialpadKey7, '7', "PQRS"),
            Triple(binding.dialpadKey8, '8', "TUV"),
            Triple(binding.dialpadKey9, '9', "WXYZ"),
            Triple(binding.dialpadKeyStar, '*', ""),
            Triple(binding.dialpadKey0, '0', "+"),
            Triple(binding.dialpadKeyHash, '#', "")
        )

        keys.forEach { (key, char, letters) ->
            key.dialpadKeyDigit.text = char.toString()
            key.dialpadKeyLetters.text = letters
            key.dialpadKeyLetters.beVisibleIf(letters.isNotEmpty())
            if (char == '*') key.dialpadKeyDigit.textSize = 26f
            key.root.contentDescription = char.toString()
            key.root.setOnClickListener {
                it.performHapticFeedback()
                playTone(char)
                typeChar(char)
            }
        }

        binding.dialpadKey0.root.setOnLongClickListener {
            it.performHapticFeedback()
            typeChar('+')
            true
        }
    }

    private fun setupTopBar() {
        binding.dialpadClose.setOnClickListener { finish() }
        binding.dialpadSearch.setOnClickListener {
            if (isSearchMode) closeSearch() else openSearch()
        }
        binding.dialpadSearchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) = refreshList()
        })
    }

    private fun applyPalette() {
        updateStatusbarColor(palette.pageBg)
        updateNavigationBarColor(panelColor())
        window.decorView.setBackgroundColor(palette.pageBg)
        binding.dialpadRoot.setBackgroundColor(palette.pageBg)

        val titleTint = ColorStateList.valueOf(palette.itemTitleText)
        listOf(binding.dialpadClose, binding.dialpadSearch, binding.dialpadAddContact, binding.dialpadBackspace).forEach {
            ImageViewCompat.setImageTintList(it, titleTint)
        }

        binding.dialpadSearchInput.setTextColor(palette.itemTitleText)
        binding.dialpadSearchInput.setHintTextColor(palette.searchHint)
        binding.dialpadSearchInput.background = HomeThemeHelper.createSearchPillBackground(this, palette.searchBg)
        binding.dialpadPlaceholder.setTextColor(palette.itemSubtitleText)

        // bottom keypad panel with rounded top corners
        val r = HomeThemeHelper.dpToPx(this, 24f)
        binding.dialpadPanel.background = GradientDrawable().apply {
            cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            setColor(panelColor())
        }
        binding.dialpadDivider.setBackgroundColor(palette.itemDivider)
        binding.dialpadInput.setTextColor(palette.itemTitleText)
        binding.dialpadInput.highlightColor = palette.primaryBlue.adjustAlpha(0.3f)

        val keyColor = if (palette.isDark) "#272B3A".toColorInt() else palette.cardBg
        listOf(
            binding.dialpadKey1, binding.dialpadKey2, binding.dialpadKey3,
            binding.dialpadKey4, binding.dialpadKey5, binding.dialpadKey6,
            binding.dialpadKey7, binding.dialpadKey8, binding.dialpadKey9,
            binding.dialpadKeyStar, binding.dialpadKey0, binding.dialpadKeyHash
        ).forEach { key: ItemDialpadKeyBinding ->
            key.root.background = pill(keyColor, 26f)
            key.dialpadKeyDigit.setTextColor(palette.itemTitleText)
            key.dialpadKeyLetters.setTextColor(palette.itemSubtitleText)
        }

        binding.dialpadCallButton.background = pill(callGreen(), 24f)
        binding.dialpadShowKeypad.background = HomeThemeHelper.createOvalBackground(callGreen())
        ImageViewCompat.setImageTintList(binding.dialpadShowKeypad, ColorStateList.valueOf(Color.WHITE))
    }

    private fun panelColor() = Color.parseColor(if (palette.isDark) "#171923" else "#DDE9F6")

    private fun callGreen() = Color.parseColor(if (palette.isDark) "#2FA35A" else "#2E9B4F")

    private fun pill(color: Int, radiusDp: Float): RippleDrawable {
        val shape = GradientDrawable().apply {
            cornerRadius = HomeThemeHelper.dpToPx(this@DialpadActivity, radiusDp)
            setColor(color)
        }
        val ripple = if (palette.isDark) "#33FFFFFF" else "#22000000"
        return RippleDrawable(ColorStateList.valueOf(ripple.toColorInt()), shape, null)
    }

    // endregion

    // region input

    private fun typeChar(char: Char) {
        val input = binding.dialpadInput
        val length = input.text.length
        val start = input.selectionStart.takeIf { it >= 0 } ?: length
        val end = input.selectionEnd.takeIf { it >= 0 } ?: length
        input.text.replace(minOf(start, end), maxOf(start, end), char.toString())
    }

    private fun deleteChar() {
        val input = binding.dialpadInput
        val start = input.selectionStart
        val end = input.selectionEnd
        if (start < 0) return
        if (start != end) {
            input.text.delete(minOf(start, end), maxOf(start, end))
        } else if (start > 0) {
            input.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
            input.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
        }
    }

    private fun fillNumber(number: String) {
        binding.dialpadInput.setText(number)
        binding.dialpadInput.setSelection(binding.dialpadInput.text.length)
        if (isSearchMode) closeSearch()
        showKeypad(true)
    }

    private fun currentNumber() = binding.dialpadInput.text.toString().trim()

    private fun digitsQuery() = currentNumber().filter { it.isDigit() || it == '+' || it == '*' || it == '#' }

    private fun updateNumberRow() {
        val hasText = currentNumber().isNotEmpty()
        binding.dialpadBackspace.alpha = if (hasText) 1f else 0.35f
        binding.dialpadBackspace.isEnabled = hasText
        binding.dialpadAddContact.alpha = if (hasText) 1f else 0.35f
        binding.dialpadAddContact.isEnabled = hasText
        binding.dialpadInput.textSize = if (currentNumber().length > 14) 20f else 24f
    }

    private fun showKeypad(show: Boolean) {
        isKeypadVisible = show
        binding.dialpadPanel.beVisibleIf(show)
        binding.dialpadShowKeypad.beVisibleIf(!show)
        if (show) binding.dialpadInput.requestFocus()
    }

    private fun openSearch() {
        isSearchMode = true
        binding.dialpadSearchInput.visibility = View.VISIBLE
        binding.dialpadSearch.setImageResource(org.fossify.commons.R.drawable.ic_cross_vector)
        binding.dialpadClose.setImageResource(R.drawable.ic_back)
        binding.dialpadClose.setOnClickListener { closeSearch() }
        ImageViewCompat.setImageTintList(binding.dialpadClose, ColorStateList.valueOf(palette.itemTitleText))
        binding.dialpadPanel.beGone()
        binding.dialpadShowKeypad.beGone()
        binding.dialpadSearchInput.requestFocus()
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .showSoftInput(binding.dialpadSearchInput, InputMethodManager.SHOW_IMPLICIT)
        refreshList()
    }

    private fun closeSearch() {
        isSearchMode = false
        hideKeyboard(binding.dialpadSearchInput)
        binding.dialpadSearchInput.setText("")
        binding.dialpadSearchInput.visibility = View.INVISIBLE
        binding.dialpadSearch.setImageResource(org.fossify.commons.R.drawable.ic_search_vector)
        binding.dialpadClose.setImageResource(org.fossify.commons.R.drawable.ic_cross_vector)
        binding.dialpadClose.setOnClickListener { finish() }
        ImageViewCompat.setImageTintList(binding.dialpadClose, ColorStateList.valueOf(palette.itemTitleText))
        showKeypad(true)
        refreshList()
    }

    // endregion

    // region data

    private fun loadData() {
        if (hasPermission(PERMISSION_READ_CONTACTS)) {
            ContactsHelper(this).getContacts { contacts ->
                runOnUiThread {
                    allContacts = contacts
                    loadRecents()
                }
            }
        } else {
            loadRecents()
        }
    }

    private fun loadRecents() {
        if (!hasPermission(PERMISSION_READ_CALL_LOG)) {
            refreshList()
            return
        }
        val contactsSnapshot = ArrayList(allContacts)
        ensureBackgroundThread {
            val items = fetchRecents(contactsSnapshot)
            runOnUiThread {
                recents = items
                refreshList()
            }
        }
    }

    private fun fetchRecents(contacts: List<Contact>): ArrayList<CallLogItem> {
        val result = ArrayList<CallLogItem>()
        val contactMap = HashMap<String, Contact>()
        contacts.forEach { contact ->
            contact.phoneNumbers.forEach { phone ->
                val normalized = phone.normalizedNumber.ifEmpty { phone.value.normalizePhoneNumber() }
                if (normalized.isNotEmpty()) {
                    contactMap[normalized] = contact
                    if (normalized.length >= 9) contactMap[normalized.takeLast(9)] = contact
                }
            }
        }

        val projection = arrayOf(
            Calls._ID, Calls.NUMBER, Calls.CACHED_NAME, Calls.CACHED_PHOTO_URI,
            Calls.DATE, Calls.DURATION, Calls.TYPE, Calls.PHONE_ACCOUNT_ID
        )
        val seenAccounts = ArrayList<String>()
        try {
            contentResolver.query(Calls.CONTENT_URI, projection, null, null, "${Calls.DATE} DESC")?.use { cursor ->
                val accountCol = cursor.getColumnIndex(Calls.PHONE_ACCOUNT_ID)
                while (cursor.moveToNext() && result.size < MAX_RECENTS) {
                    val number = cursor.getStringValue(Calls.NUMBER) ?: ""
                    if (number.isEmpty()) continue
                    val date = cursor.getLongValue(Calls.DATE)
                    val type = cursor.getIntValue(Calls.TYPE)
                    val accountId = if (accountCol >= 0) cursor.getString(accountCol) ?: "" else ""
                    val simLabel = if (accountId.isEmpty()) {
                        "SIM1"
                    } else {
                        if (!seenAccounts.contains(accountId)) seenAccounts.add(accountId)
                        if (seenAccounts.indexOf(accountId) == 1 || accountId.contains("2")) "SIM2" else "SIM1"
                    }

                    val normalized = number.normalizePhoneNumber()
                    val contact = contactMap[normalized] ?: if (normalized.length >= 9) contactMap[normalized.takeLast(9)] else null
                    val cachedName = cursor.getStringValue(Calls.CACHED_NAME) ?: ""
                    val cachedPhoto = cursor.getStringValue(Calls.CACHED_PHOTO_URI) ?: ""

                    // merge consecutive calls from the same number/type/day, like the call history tab
                    val last = result.lastOrNull()
                    val lastNorm = last?.phoneNumber?.normalizePhoneNumber() ?: ""
                    val same = normalized.isNotEmpty() && (normalized == lastNorm ||
                        (normalized.length >= 9 && lastNorm.length >= 9 && normalized.takeLast(9) == lastNorm.takeLast(9)))
                    if (last != null && same && last.callType == type && isSameDay(last.timestamp, date)) {
                        result[result.lastIndex] = last.copy(callCount = last.callCount + 1)
                    } else {
                        result.add(
                            CallLogItem(
                                id = cursor.getIntValue(Calls._ID),
                                name = contact?.getNameToDisplay()?.ifEmpty { cachedName } ?: cachedName,
                                phoneNumber = number,
                                callType = type,
                                timestamp = date,
                                duration = cursor.getIntValue(Calls.DURATION),
                                photoUri = contact?.photoUri?.ifEmpty { cachedPhoto } ?: cachedPhoto,
                                matchedContact = contact,
                                simLabel = simLabel
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {
        }
        return result
    }

    private fun refreshList() {
        val list = ArrayList<DialpadListItem>()
        var placeholder = ""

        if (isSearchMode) {
            val query = binding.dialpadSearchInput.text.toString().trim()
            val matches = if (query.isEmpty()) emptyList() else searchContactsByText(query)
            if (matches.isNotEmpty()) {
                list.add(DialpadListItem.Header(getString(R.string.dialpad_contacts_header)))
                list.addAll(matches)
            } else if (query.isNotEmpty()) {
                placeholder = getString(org.fossify.commons.R.string.no_items_found)
            }
        } else {
            val query = digitsQuery()
            if (query.isEmpty()) {
                buildRecentSections(list)
                if (list.isEmpty()) {
                    placeholder = if (hasPermission(PERMISSION_READ_CALL_LOG)) {
                        getString(R.string.dialpad_no_recents)
                    } else {
                        getString(org.fossify.commons.R.string.no_permission)
                    }
                }
            } else {
                val matches = searchContactsByDigits(query)
                if (matches.isNotEmpty()) {
                    list.add(DialpadListItem.Header(getString(R.string.dialpad_contacts_header)))
                    list.addAll(matches)
                } else {
                    list.add(DialpadListItem.Action(DialpadAction.CREATE_CONTACT))
                    list.add(DialpadListItem.Action(DialpadAction.ADD_TO_CONTACT))
                    list.add(DialpadListItem.Action(DialpadAction.SEND_MESSAGE))
                }
            }
        }

        adapter.submit(list)
        binding.dialpadPlaceholder.text = placeholder
        binding.dialpadPlaceholder.beVisibleIf(placeholder.isNotEmpty())
        binding.dialpadList.scrollToPosition(0)
    }

    private fun buildRecentSections(list: ArrayList<DialpadListItem>) {
        var lastHeader = ""
        recents.forEach { item ->
            val header = dayHeader(item.timestamp)
            if (header != lastHeader) {
                list.add(DialpadListItem.Header(header))
                lastHeader = header
            }
            list.add(DialpadListItem.Recent(item))
        }
    }

    private fun dayHeader(timestamp: Long): String {
        val now = Calendar.getInstance()
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        return when {
            isSameDay(now.timeInMillis, timestamp) -> getString(org.fossify.commons.R.string.today)
            isSameDay(yesterday.timeInMillis, timestamp) -> getString(org.fossify.commons.R.string.yesterday)
            cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) ->
                java.text.SimpleDateFormat("EEEE, dd MMM", Locale.getDefault()).format(cal.time)
            else -> java.text.SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(cal.time)
        }
    }

    private fun isSameDay(t1: Long, t2: Long): Boolean {
        val c1 = Calendar.getInstance().apply { timeInMillis = t1 }
        val c2 = Calendar.getInstance().apply { timeInMillis = t2 }
        return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) && c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)
    }

    /** Matches typed digits against phone numbers and against T9-encoded names. */
    private fun searchContactsByDigits(query: String): List<DialpadListItem.ContactMatch> {
        val numberQuery = query.filter { it.isDigit() }
        val nameQuery = query.filter { it in '2'..'9' }.takeIf { it.length == query.length }
        val numberMatches = ArrayList<DialpadListItem.ContactMatch>()
        val nameMatches = ArrayList<DialpadListItem.ContactMatch>()
        val seen = HashSet<String>()

        allContacts.forEach { contact ->
            val name = contact.getNameToDisplay()
            val nameRange = if (nameQuery != null && name.isNotEmpty()) t9Range(name, nameQuery) else null

            var matchedByNumber = false
            if (numberQuery.isNotEmpty()) {
                contact.phoneNumbers.forEach { phone ->
                    val digits = phone.value.filter { it.isDigit() }
                    if (digits.contains(numberQuery) && seen.add("${contact.id}|$digits")) {
                        numberMatches.add(DialpadListItem.ContactMatch(contact, phone.value, nameRange, numberQuery))
                        matchedByNumber = true
                    }
                }
            }

            if (!matchedByNumber && nameRange != null) {
                val phone = contact.phoneNumbers.firstOrNull { it.isPrimary } ?: contact.phoneNumbers.firstOrNull()
                if (phone != null && seen.add("${contact.id}|${phone.value.filter { it.isDigit() }}")) {
                    nameMatches.add(DialpadListItem.ContactMatch(contact, phone.value, nameRange, ""))
                }
            }
        }

        // name-start matches first, then number matches
        nameMatches.sortBy { it.nameMatch?.first ?: Int.MAX_VALUE }
        return (nameMatches + numberMatches).take(100)
    }

    private fun searchContactsByText(query: String): List<DialpadListItem.ContactMatch> {
        val result = ArrayList<DialpadListItem.ContactMatch>()
        val digitQuery = query.filter { it.isDigit() }
        allContacts.forEach { contact ->
            val name = contact.getNameToDisplay()
            val idx = name.indexOf(query, ignoreCase = true)
            val phone = contact.phoneNumbers.firstOrNull { it.isPrimary } ?: contact.phoneNumbers.firstOrNull() ?: return@forEach
            when {
                idx >= 0 -> result.add(DialpadListItem.ContactMatch(contact, phone.value, idx until idx + query.length, ""))
                digitQuery.length == query.length && phone.value.filter { it.isDigit() }.contains(digitQuery) ->
                    result.add(DialpadListItem.ContactMatch(contact, phone.value, null, digitQuery))
            }
        }
        return result.take(100)
    }

    /** Returns the range in [name] whose T9 digits equal [query], preferring a word start. */
    private fun t9Range(name: String, query: String): IntRange? {
        val encoded = buildString {
            name.lowercase(Locale.getDefault()).forEach { c ->
                append(T9[c] ?: if (c.isDigit()) c else ' ')
            }
        }
        var index = encoded.indexOf(query)
        var firstAny: Int? = null
        while (index >= 0) {
            if (firstAny == null) firstAny = index
            if (index == 0 || encoded[index - 1] == ' ') return index until index + query.length
            index = encoded.indexOf(query, index + 1)
        }
        return firstAny?.let { it until it + query.length }
    }

    // endregion

    // region actions

    private fun onCallButtonClicked() {
        val number = currentNumber()
        if (number.isEmpty()) {
            // stock-dialer behaviour: first tap recalls the last dialed number
            val lastOutgoing = recents.firstOrNull { it.callType == Calls.OUTGOING_TYPE }?.phoneNumber
            if (lastOutgoing != null) {
                fillNumber(lastOutgoing)
            } else {
                toast(org.fossify.commons.R.string.no_phone_number_found)
            }
            return
        }
        placeCall(number, number)
    }

    private fun placeCall(number: String, displayName: String) {
        if (number.isEmpty()) {
            toast(org.fossify.commons.R.string.no_phone_number_found)
            return
        }
        if (config.showCallConfirmation) {
            CallConfirmationDialog(this, displayName) { startCallIntent(number) }
        } else {
            startCallIntent(number)
        }
    }

    private fun handleAction(action: DialpadAction) {
        val number = currentNumber()
        try {
            when (action) {
                DialpadAction.CREATE_CONTACT -> Intent(this, EditContactActivity::class.java).apply {
                    this.action = Intent.ACTION_INSERT
                    data = ContactsContract.Contacts.CONTENT_URI
                    if (number.isNotEmpty()) putExtra(KEY_PHONE, number)
                    startActivity(this)
                }

                DialpadAction.ADD_TO_CONTACT -> Intent(this, InsertOrEditContactActivity::class.java).apply {
                    this.action = Intent.ACTION_INSERT_OR_EDIT
                    type = ContactsContract.Contacts.CONTENT_ITEM_TYPE
                    if (number.isNotEmpty()) putExtra(KEY_PHONE, number)
                    startActivity(this)
                }

                DialpadAction.SEND_MESSAGE -> if (number.isNotEmpty()) launchSendSMSIntent(number)
            }
        } catch (e: ActivityNotFoundException) {
            toast(org.fossify.commons.R.string.no_app_found)
        } catch (e: Exception) {
            showErrorToast(e)
        }
    }

    // endregion

    // region tones

    private fun initToneGenerator() {
        if (toneGenerator != null) return
        toneGenerator = try {
            ToneGenerator(AudioManager.STREAM_DTMF, 80)
        } catch (_: RuntimeException) {
            null
        }
    }

    private fun playTone(char: Char) {
        val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        val tone = when (char) {
            '0' -> ToneGenerator.TONE_DTMF_0
            '1' -> ToneGenerator.TONE_DTMF_1
            '2' -> ToneGenerator.TONE_DTMF_2
            '3' -> ToneGenerator.TONE_DTMF_3
            '4' -> ToneGenerator.TONE_DTMF_4
            '5' -> ToneGenerator.TONE_DTMF_5
            '6' -> ToneGenerator.TONE_DTMF_6
            '7' -> ToneGenerator.TONE_DTMF_7
            '8' -> ToneGenerator.TONE_DTMF_8
            '9' -> ToneGenerator.TONE_DTMF_9
            '*' -> ToneGenerator.TONE_DTMF_S
            '#' -> ToneGenerator.TONE_DTMF_P
            else -> return
        }
        toneGenerator?.startTone(tone, TONE_LENGTH_MS)
    }

    // endregion
}
