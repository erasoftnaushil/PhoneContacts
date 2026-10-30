package com.contactsapp2025.universal.fragments

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.View
import com.contactsapp2025.universal.R
import com.google.gson.Gson
import org.fossify.commons.extensions.areSystemAnimationsEnabled
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.helpers.CONTACTS_GRID_MAX_COLUMNS_COUNT
import org.fossify.commons.helpers.ContactsHelper
import org.fossify.commons.helpers.TAB_FAVORITES
import org.fossify.commons.helpers.VIEW_TYPE_GRID
import org.fossify.commons.models.contacts.Contact
import org.fossify.commons.views.MyGridLayoutManager
import org.fossify.commons.views.MyLinearLayoutManager
import org.fossify.commons.views.MyRecyclerView
import com.contactsapp2025.universal.activities.MainActivity
import com.contactsapp2025.universal.activities.SimpleActivity
import com.contactsapp2025.universal.adapters.ContactsAdapter
import com.contactsapp2025.universal.databinding.FragmentFavoritesBinding
import com.contactsapp2025.universal.databinding.FragmentLettersLayoutBinding
import com.contactsapp2025.universal.extensions.config
import com.contactsapp2025.universal.helpers.LOCATION_FAVORITES_TAB
import com.contactsapp2025.universal.interfaces.RefreshContactsListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val FAVORITES_GRID_COLUMNS = 4

class FavoritesFragment(context: Context, attributeSet: AttributeSet) : MyViewPagerFragment<MyViewPagerFragment.LetterLayout>(context, attributeSet) {
    private var favouriteContacts = listOf<Contact>()
    private var zoomListener: MyRecyclerView.MyZoomListener? = null
    private lateinit var binding: FragmentFavoritesBinding

    override fun onFinishInflate() {
        super.onFinishInflate()
        binding = FragmentFavoritesBinding.bind(this)
        innerBinding = LetterLayout(FragmentLettersLayoutBinding.bind(binding.root))
    }

    override fun fabClicked() {
        finishActMode()
        showAddFavoritesDialog()
    }

    override fun placeholderClicked() {
        showAddFavoritesDialog()
    }

    private fun getRecyclerAdapter() = innerBinding.fragmentList.adapter as? ContactsAdapter

    private fun showAddFavoritesDialog() {
        val act = activity ?: return
        act.startActivity(android.content.Intent(act, com.contactsapp2025.universal.activities.PickFavoritesActivity::class.java))
    }

    /** Called after the user changes Grid / List in the Display mode dialog. */
    fun onDisplayModeChanged() {
        forceListRedraw = true
        setupContactsFavoritesAdapter(favouriteContacts)
    }

    fun setupContactsFavoritesAdapter(contacts: List<Contact>) {
        favouriteContacts = contacts
        setupViewVisibility(favouriteContacts.isNotEmpty())
        val currAdapter = getRecyclerAdapter()

        val viewType = context.config.favoritesDisplayMode
        setFavoritesViewType(viewType)
        zoomListener = null // the favorites grid uses a fixed 4-column design
        if (currAdapter == null || forceListRedraw) {
            forceListRedraw = false
            val location = LOCATION_FAVORITES_TAB

            ContactsAdapter(
                activity = activity as SimpleActivity,
                contactItems = favouriteContacts.toMutableList(),
                refreshListener = activity as RefreshContactsListener,
                location = location,
//                viewType = viewType,
                removeListener = null,
                recyclerView = innerBinding.fragmentList,
                enableDrag = true,
            ) {
                (activity as RefreshContactsListener).contactClicked(it as Contact)
            }.apply {
                innerBinding.fragmentList.adapter = this
                setupZoomListener(zoomListener)
                onDragEndListener = {
                    val adapter = innerBinding.fragmentList.adapter
                    if (adapter is ContactsAdapter) {
                        val items = adapter.contactItems
                        saveCustomOrderToPrefs(items)
                        setupLetterFastscroller(items)
                    }
                }
            }

            if (context.areSystemAnimationsEnabled) {
                innerBinding.fragmentList.scheduleLayoutAnimation()
            }
        } else {
            currAdapter.apply {
                startNameWithSurname = context.config.startNameWithSurname
                showPhoneNumbers = context.config.showPhoneNumbers
                showContactThumbnails = context.config.showContactThumbnails
//                this.viewType = viewType
                updateItems(favouriteContacts)
            }
        }
    }

    fun updateFavouritesAdapter() {
        setupContactsFavoritesAdapter(favouriteContacts)
    }

    private fun setFavoritesViewType(viewType: Int) {
        if (viewType == VIEW_TYPE_GRID) {
            innerBinding.letterFastscroller.beGone()
            innerBinding.letterFastscrollerThumb.beGone()
            val padding = (8 * resources.displayMetrics.density).toInt()
            innerBinding.fragmentList.setPadding(padding, padding, padding, innerBinding.fragmentList.paddingBottom)
            if ((innerBinding.fragmentList.layoutManager as? MyGridLayoutManager)?.spanCount != FAVORITES_GRID_COLUMNS) {
                innerBinding.fragmentList.layoutManager = MyGridLayoutManager(context, FAVORITES_GRID_COLUMNS)
            }
        } else {
            innerBinding.letterFastscroller.beVisible()
            innerBinding.fragmentList.setPadding(0, 0, 0, innerBinding.fragmentList.paddingBottom)
            if (innerBinding.fragmentList.layoutManager !is MyLinearLayoutManager) {
                innerBinding.fragmentList.layoutManager = MyLinearLayoutManager(context)
            }
        }
    }

    private fun initZoomListener(viewType: Int) {
        if (viewType == VIEW_TYPE_GRID) {
            val layoutManager = innerBinding.fragmentList.layoutManager as MyGridLayoutManager
            zoomListener = object : MyRecyclerView.MyZoomListener {
                override fun zoomIn() {
                    if (layoutManager.spanCount > 1) {
                        reduceColumnCount()
                        getRecyclerAdapter()?.finishActMode()
                    }
                }

                override fun zoomOut() {
                    if (layoutManager.spanCount < CONTACTS_GRID_MAX_COLUMNS_COUNT) {
                        increaseColumnCount()
                        getRecyclerAdapter()?.finishActMode()
                    }
                }
            }
        } else {
            zoomListener = null
        }
    }

    private fun increaseColumnCount() {
        if (context.config.viewType == VIEW_TYPE_GRID) {
            context!!.config.contactsGridColumnCount += 1
            columnCountChanged()
        }
    }

    private fun reduceColumnCount() {
        if (context.config.viewType == VIEW_TYPE_GRID) {
            context!!.config.contactsGridColumnCount -= 1
            columnCountChanged()
        }
    }

    fun columnCountChanged() {
        (innerBinding.fragmentList.layoutManager as? MyGridLayoutManager)?.spanCount = context!!.config.contactsGridColumnCount
        getRecyclerAdapter()?.apply {
            notifyItemRangeChanged(0, favouriteContacts.size)
        }
    }

    private fun saveCustomOrderToPrefs(items: List<Contact>) {
        activity?.apply {
            val orderIds = items.map { it.id }
            val orderGsonString = Gson().toJson(orderIds)
            config.favoritesContactsOrder = orderGsonString
        }
    }

}
