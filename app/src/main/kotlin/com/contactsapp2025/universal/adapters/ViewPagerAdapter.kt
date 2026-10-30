package com.contactsapp2025.universal.adapters

import android.view.View
import android.view.ViewGroup
import androidx.viewpager.widget.PagerAdapter
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.helpers.TAB_CALL_HISTORY
import org.fossify.commons.helpers.TAB_CONTACTS
import org.fossify.commons.helpers.TAB_FAVORITES
import org.fossify.commons.helpers.TAB_GROUPS
import com.contactsapp2025.universal.R
import com.contactsapp2025.universal.activities.SimpleActivity
import com.contactsapp2025.universal.fragments.MyViewPagerFragment

class ViewPagerAdapter(val activity: SimpleActivity, val currTabsList: ArrayList<Int>, val showTabs: Int) : PagerAdapter() {

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val layout = getFragment(position)
        val view = activity.layoutInflater.inflate(layout, container, false)
        container.addView(view)

        (view as MyViewPagerFragment<*>).apply {
            setupFragment(activity)
            setupColors(activity.getProperTextColor(), activity.getProperPrimaryColor())
        }

        return view
    }

    override fun destroyItem(container: ViewGroup, position: Int, item: Any) {
        container.removeView(item as View)
    }

    override fun getCount() = currTabsList.filter { it and showTabs != 0 }.size

    override fun isViewFromObject(view: View, item: Any) = view == item

    private fun getFragment(position: Int): Int {
        val fragments = arrayListOf<Int>()
        if (showTabs and TAB_CALL_HISTORY != 0) {
            fragments.add(R.layout.fragment_call_history)
        }
        if (showTabs and TAB_CONTACTS != 0) {
            fragments.add(R.layout.fragment_contacts)
        }
        if (showTabs and TAB_FAVORITES != 0) {
            fragments.add(R.layout.fragment_favorites)
        }

        return fragments[position]
    }
}
