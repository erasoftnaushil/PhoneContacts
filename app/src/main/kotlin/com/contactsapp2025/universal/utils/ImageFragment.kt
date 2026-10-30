package com.contactsapp2025.universal.utils

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModelProvider
import com.contactsapp2025.universal.R

class ImageFragment : Fragment(R.layout.fragment_image) {

    private val sharedViewModel: SharedViewModel by activityViewModels()

    companion object {
        private const val ARG_IMAGE_RESOURCE = "image_resource"

        fun newInstance(imageResource: Int): ImageFragment {
            val fragment = ImageFragment()
            val args = Bundle()
            args.putInt(ARG_IMAGE_RESOURCE, imageResource)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val imageView: ImageView = view.findViewById(R.id.imageView)
        val imageResource = arguments?.getInt(ARG_IMAGE_RESOURCE)
        imageView.setOnClickListener {
            imageResource?.let { sharedViewModel.onItemClick(it) }
        }
        imageView.setImageResource(imageResource ?: R.drawable.image_1) // Placeholder if no image is found

    }
}

