package com.contactsapp2025.universal.language

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.contactsapp2025.universal.R
import com.contactsapp2025.universal.databinding.LanguageItemBinding

class LanguageAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var list = ArrayList<LanguageModel>()
    private var selectedPos = 0
    private var selectedLang = ""

    inner class LanguageViewHolder(val binding: LanguageItemBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val binding = LanguageItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return LanguageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = list[holder.adapterPosition]
        if (holder is LanguageViewHolder) {

            if (holder.adapterPosition == selectedPos){
                holder.binding.rbSelection.setImageResource(R.drawable.radio_select)
            }else{
                holder.binding.rbSelection.setImageResource(R.drawable.radio_unselect)
            }

            holder.binding.txtLangName.text = item.langName
            holder.binding.ivFlag.setImageResource(item.langFlag)
            holder.itemView.setOnClickListener {
                selectedLang = item.langCode
                selectedPos = holder.adapterPosition
                notifyDataSetChanged()
            }
        }
    }

    override fun getItemCount(): Int {
        return list.size
    }

    fun setItemList(list: ArrayList<LanguageModel>) {
        this.list = list
        list.forEachIndexed { index, languageModel ->
            if (languageModel.langCode == selectedLang) {
                selectedPos = index
            }
        }
        notifyDataSetChanged()
    }

    fun setSelectedLang(selectedLang: String) {
        this.selectedLang = selectedLang
    }

    fun getSelectedLanguage(): String {
        return selectedLang
    }
}
