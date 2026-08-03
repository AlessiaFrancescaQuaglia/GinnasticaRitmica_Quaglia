package com.example.ginnasticaritmica.ui.adapter

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.net.toUri
import androidx.recyclerview.widget.RecyclerView
import com.example.ginnasticaritmica.R

//Fa da "ponte" tra i dati grezzi (Mappa) e la grafica
class MusicaAdapter(
    private val dati: Map<String, String>, // Mappa Attrezzo -> Link
    private val context: Context
) : RecyclerView.Adapter<MusicaAdapter.ViewHolder>() {

    // Dato che le Mappe non hanno un ordine numerico, estraggo le chiavi (i nomi degli attrezzi) in una Lista.
    private val chiavi = dati.keys.toList()

    //Evita che il telefono ricalcoli i findViewById ogni volta che l'utente fa scroll
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvAttrezzo = view.findViewById<TextView>(R.id.tvNomeAttrezzo)!!
        val btnAscolta = view.findViewById<Button>(R.id.btnAscolta)!!
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        // Prende il disegno XML (item_musica) e lo trasforma in un oggetto Kotlin reale.
        ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_musica, parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        // Recupero chiave (attrezzo) e valore (link musicale) in base alla posizione della riga
        val attrezzo = chiavi[position]
        val link = dati[attrezzo] ?: ""

        holder.tvAttrezzo.text = attrezzo

        //Dico al sistema operativo "Voglio visualizzare questo URL".
        // Sarà Android a decidere automaticamente quale app usare.
        holder.btnAscolta.setOnClickListener {
            context.startActivity(Intent(Intent.ACTION_VIEW, link.toUri()))
        }
    }

    // Comunica alla RecyclerView quante righe totali ci sono da disegnare
    override fun getItemCount() = dati.size
}