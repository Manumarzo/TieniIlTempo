package com.example.tieniiltempo.chat

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.tieniiltempo.R

class ChatAdapter(private val chats: List<Chat>) :
    RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    inner class ChatViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val usernameText: TextView = view.findViewById(R.id.chatUsername)
        val lastMessageText: TextView = view.findViewById(R.id.chatLastMessage)
        val arrowImage: ImageView = view.findViewById(R.id.chatArrow)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val chat = chats[position]
        holder.usernameText.text = chat.username
        holder.lastMessageText.text = chat.lastMessage


        holder.itemView.setOnClickListener {
            val context = holder.itemView.context
            val intent = Intent(context, MessageActivity::class.java).apply {
                putExtra("USER_ID", chat.userId)
                putExtra("USER_NAME", chat.username)
            }
            context.startActivity(intent)
        }
    }

    override fun getItemCount(): Int = chats.size
}
