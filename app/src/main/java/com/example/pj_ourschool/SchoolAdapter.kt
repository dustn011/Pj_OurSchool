package com.example.pj_ourschool

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class SchoolAdapter(
    private val schoolList: List<String>,
    private val onItemClick: (String) -> Unit,
    private val onSchoolSelected: (String) -> Unit // 학교 선택 시 검색창 텍스트 변경 콜백
) : RecyclerView.Adapter<SchoolAdapter.ViewHolder>() {

    private var selectedPosition = RecyclerView.NO_POSITION

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textSchoolName: TextView = itemView.findViewById(R.id.textSchoolName)

        init {
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val clickedSchoolName = schoolList[position]
                    val previousSelectedPosition = selectedPosition
                    selectedPosition = position
                    notifyItemChanged(previousSelectedPosition)
                    notifyItemChanged(selectedPosition)
                    onItemClick(clickedSchoolName)
                    onSchoolSelected(clickedSchoolName) // 학교 선택 시 콜백 호출
                }
            }
        }

        fun bind(schoolName: String) {
            textSchoolName.text = schoolName

            if (adapterPosition == selectedPosition) {
                itemView.background = ContextCompat.getDrawable(itemView.context, R.drawable.selected_item_background)
                textSchoolName.setTextColor(Color.WHITE)
            } else {
                itemView.background = ContextCompat.getDrawable(itemView.context, android.R.color.transparent)
                textSchoolName.setTextColor(ContextCompat.getColor(itemView.context, android.R.color.black))
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_school, parent, false)
        return ViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(schoolList[position])
    }

    override fun getItemCount() = schoolList.size

    // 선택 상태 초기화 함수
    fun clearSelection() {
        val previousSelectedPosition = selectedPosition
        selectedPosition = RecyclerView.NO_POSITION
        notifyItemChanged(previousSelectedPosition)
    }
}