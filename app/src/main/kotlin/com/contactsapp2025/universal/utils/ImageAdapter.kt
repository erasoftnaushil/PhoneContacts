package com.contactsapp2025.universal.utils

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.contactsapp2025.universal.R

class ImageAdapter(fragmentActivity: FragmentActivity) : FragmentStateAdapter(fragmentActivity) {

    private val imageList =
        listOf(
            R.drawable.image_1,
            R.drawable.image_2
        )

    override fun getItemCount(): Int = imageList.size

    override fun createFragment(position: Int): Fragment {
        return ImageFragment.newInstance(imageList[position])
    }
}

