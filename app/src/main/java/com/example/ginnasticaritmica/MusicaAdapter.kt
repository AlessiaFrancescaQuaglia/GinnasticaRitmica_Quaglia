package com.example.ginnasticaritmica
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class MusicaAdapter(
    private val dati: Map<String, String>, // Mappa Attrezzo -> Link
    private val context: android.content.Context
) : androidx.recyclerview.widget.RecyclerView.Adapter<MusicaAdapter.ViewHolder>() {

    private val chiavi = dati.keys.toList()

    class ViewHolder(view: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {
        val tvAttrezzo = view.findViewById<android.widget.TextView>(R.id.tvNomeAttrezzo)
        val btnAscolta = view.findViewById<android.widget.Button>(R.id.btnAscolta)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_musica, parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val attrezzo = chiavi[position]
        val link = dati[attrezzo] ?: ""
        holder.tvAttrezzo.text = attrezzo
        holder.btnAscolta.setOnClickListener {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
        }
    }

    override fun getItemCount() = dati.size
}