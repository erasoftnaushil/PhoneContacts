package com.contactsapp2025.universal.dialog

import android.app.Dialog
import android.os.Bundle
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import com.contactsapp2025.universal.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class ProgressDialogFragment : DialogFragment() {

    private var messageView: TextView? = null

    companion object {
        private const val ARG_TOTAL_CONTACTS = "total_contacts"
        fun newInstance(totalContacts: Int) = ProgressDialogFragment().apply {
            arguments = bundleOf(ARG_TOTAL_CONTACTS to totalContacts)
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = layoutInflater.inflate(R.layout.dialog_progress, null)
        messageView = view.findViewById(R.id.message)
        val totalContacts = arguments?.getInt(ARG_TOTAL_CONTACTS) ?: 0
        messageView?.text = getString(R.string.deleting_contacts_progress,totalContacts)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(view)
            .create()

        dialog.setCanceledOnTouchOutside(false)
        isCancelable = false
        return dialog
    }

    override fun onDestroyView() {
        messageView = null // Avoid memory leaks
        super.onDestroyView()
    }
}
