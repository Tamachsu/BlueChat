package com.example.bluechat.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.bluechat.R
import com.example.bluechat.model.ChatMessage

class MessageAdapter(
    private val messages: MutableList<ChatMessage> = mutableListOf()
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_RECEIVED = 1
        private const val TYPE_SENT = 2
    }

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].isSent) TYPE_SENT else TYPE_RECEIVED
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_SENT) {
            val view = inflater.inflate(R.layout.item_mensagem_enviada, parent, false)
            SentViewHolder(view)
        } else {
            val view = inflater.inflate(R.layout.item_mensagem_recebida, parent, false)
            ReceivedViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val msg = messages[position]
        if (holder is SentViewHolder) {
            holder.txtMensagem.text = msg.text
            holder.txtHoraEEstado.text = "${msg.timestamp} ${msg.status}"
        } else if (holder is ReceivedViewHolder) {
            holder.txtMensagem.text = msg.text
            holder.txtHora.text = msg.timestamp
        }
    }

    override fun getItemCount(): Int = messages.size

    fun addMessage(message: ChatMessage) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    class SentViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtMensagem: TextView = view.findViewById(R.id.txtMensagem)
        val txtHoraEEstado: TextView = view.findViewById(R.id.txtHoraEEstado)
    }

    class ReceivedViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtMensagem: TextView = view.findViewById(R.id.txtMensagem)
        val txtHora: TextView = view.findViewById(R.id.txtHora)
    }
}
