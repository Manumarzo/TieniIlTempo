package com.example.tieniiltempo.user

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.tieniiltempo.R

class UserAdapter(
    private val users: List<User>,
    private val onUserClick: (User) -> Unit,
    private val onDisassociateClick: (User) -> Unit
    ) : RecyclerView.Adapter<UserAdapter.UserViewHolder>() {

    inner class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameText: TextView = itemView.findViewById(R.id.userNameText)
        val disassociateButton: ImageButton = itemView.findViewById(R.id.disassociateButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_user, parent, false)
        return UserViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        val user = users[position]
        holder.nameText.text = "${user.name} ${user.surname}"

        // Click su tutta la card tranne il pulsante
        holder.itemView.setOnClickListener {
            onUserClick(user)
        }

        // Disassociazione con il bottone
        holder.disassociateButton.setOnClickListener {
            onDisassociateClick(user)
        }
    }

    override fun getItemCount(): Int = users.size
}


