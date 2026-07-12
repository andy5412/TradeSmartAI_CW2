package com.andysin.tradesmartai

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class HistoryAdapter(private var itemList: List<ItemEntity>) :
    RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    // 呢個 class 用嚟綁定 item_history.xml 入面嘅 UI 元件
    class HistoryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivPhoto: ImageView = view.findViewById(R.id.ivItemPhoto)
        val tvName: TextView = view.findViewById(R.id.tvItemName)
        val tvPrice: TextView = view.findViewById(R.id.tvEstimatedPrice)
        val tvLocation: TextView = view.findViewById(R.id.tvLocation)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return HistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val item = itemList[position]

        // 填入文字資料
        holder.tvName.text = item.itemName
        holder.tvPrice.text = "估價: $${item.aiEstimatedPrice}"
        holder.tvLocation.text = "📍 ${item.location}"

        // 讀取並顯示手機入面嘅實體相片
        val imgFile = File(item.imagePath)
        if (imgFile.exists()) {
            val bitmap = BitmapFactory.decodeFile(imgFile.absolutePath)
            holder.ivPhoto.setImageBitmap(bitmap)
        }
    }

    override fun getItemCount(): Int {
        return itemList.size
    }

    // 當數據庫有更新時，呼叫呢個 function 刷新畫面
    fun updateData(newList: List<ItemEntity>) {
        itemList = newList
        notifyDataSetChanged()
    }
}