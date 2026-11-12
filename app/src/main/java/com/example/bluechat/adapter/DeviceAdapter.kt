package com.example.bluechat.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.bluechat.R
import com.example.bluechat.model.BluetoothDeviceModel

class DeviceAdapter(
    private val devices: MutableList<BluetoothDeviceModel>,
    private val onItemClick: (BluetoothDeviceModel) -> Unit
) : RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder>() {

    class DeviceViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val container: View = view.findViewById(R.id.layoutItemDispositivo)
        val txtNome: TextView = view.findViewById(R.id.txtNomeDispositivo)
        val txtSubtitulo: TextView = view.findViewById(R.id.txtSubtituloDispositivo)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_dispositivo, parent, false)
        return DeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val item = devices[position]
        holder.txtNome.text = item.name
        holder.txtSubtitulo.text = item.statusText

        val context = holder.itemView.context
        if (item.isPaired && position == 0) {
            // First paired item selected highlight style
            holder.container.background = ContextCompat.getDrawable(context, R.drawable.bg_device_item_selected)
            holder.txtSubtitulo.setTextColor(ContextCompat.getColor(context, R.color.primary_blue))
        } else {
            holder.container.background = ContextCompat.getDrawable(context, R.drawable.bg_device_item)
            holder.txtSubtitulo.setTextColor(ContextCompat.getColor(context, R.color.text_grey))
        }

        holder.itemView.setOnClickListener {
            onItemClick(item)
        }
    }

    override fun getItemCount(): Int = devices.size

    fun updateDevices(newDevices: List<BluetoothDeviceModel>) {
        devices.clear()
        devices.addAll(newDevices)
        notifyDataSetChanged()
    }
}
