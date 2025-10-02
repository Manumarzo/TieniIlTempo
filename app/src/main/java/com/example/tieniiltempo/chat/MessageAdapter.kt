package com.example.tieniiltempo.chat

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.tieniiltempo.R
import com.google.firebase.auth.FirebaseAuth

class MessageAdapter(private val messages: List<Message>) : RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {

    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
    private val VIEW_TYPE_SENT = 1
    private val VIEW_TYPE_RECEIVED = 2

    override fun getItemViewType(position: Int): Int {
        val message = messages[position]
        return if (message.senderId == currentUserId) {
            VIEW_TYPE_SENT
        } else {
            VIEW_TYPE_RECEIVED
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        val view = if (viewType == VIEW_TYPE_SENT) {
            layoutInflater.inflate(R.layout.item_message_sent, parent, false)
        } else {
            layoutInflater.inflate(R.layout.item_message_received, parent, false)
        }
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(messages[position])
    }

    override fun getItemCount() = messages.size

    inner class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageText: TextView = itemView.findViewById(R.id.messageText)
        private val messageImage: ImageView = itemView.findViewById(R.id.messageImageView)
        private val locationLayout: LinearLayout = itemView.findViewById(R.id.locationLayout)
        private val locationText: TextView = itemView.findViewById(R.id.locationText)

        fun bind(message: Message) {
            // Nascondi tutti gli elementi per iniziare
            messageText.visibility = View.GONE
            messageImage.visibility = View.GONE
            locationLayout.visibility = View.GONE

            when (message.type) {
                MessageType.TEXT -> {
                    messageText.visibility = View.VISIBLE
                    messageText.text = message.text
                }
                MessageType.IMAGE -> {
                    // Mostra sia l'immagine che la didascalia (se presente)
                    if (message.text.isNotBlank()) {
                        messageText.visibility = View.VISIBLE
                        messageText.text = message.text
                    }
                    messageImage.visibility = View.VISIBLE
                    Glide.with(itemView.context)
                        .load(message.imageUri)
                        .into(messageImage)
                }
                MessageType.LOCATION -> {
                    locationLayout.visibility = View.VISIBLE
                    locationText.text = message.text

                    locationLayout.setOnClickListener {
                        val gmmIntentUri = Uri.parse("geo:${message.latitude},${message.longitude}?q=${message.latitude},${message.longitude}(Posizione)")
                        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                        mapIntent.setPackage("com.google.android.apps.maps")
                        if (mapIntent.resolveActivity(itemView.context.packageManager) != null) {
                            itemView.context.startActivity(mapIntent)
                        } else {
                            Toast.makeText(itemView.context, "Google Maps non è installato.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

}