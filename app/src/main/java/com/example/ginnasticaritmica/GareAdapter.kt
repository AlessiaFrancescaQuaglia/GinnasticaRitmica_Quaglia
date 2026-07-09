package com.example.ginnasticaritmica

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class GareAdapter(
    private val listaGare: List<Gara>,
    private val onIscrizioniClick: (Gara) -> Unit // Azione quando si clicca "Gestisci Iscrizioni"
) : RecyclerView.Adapter<GareAdapter.GaraViewHolder>() {

    class GaraViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvNome: TextView = view.findViewById(R.id.tvNomeGaraItem)
        val tvDataLuogo: TextView = view.findViewById(R.id.tvDataLuogoItem)
        val tvFiltri: TextView = view.findViewById(R.id.tvInfoFiltriItem)
        val btnIscrizioni: Button = view.findViewById(R.id.btnGestisciIscrizioni)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GaraViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_gara, parent, false)
        return GaraViewHolder(view)
    }

    override fun onBindViewHolder(holder: GaraViewHolder, position: Int) {
        val gara = listaGare[position]

        holder.tvNome.text = gara.nome
        holder.tvDataLuogo.text = "📅 ${gara.data} - 📍 ${gara.luogo}"

        // Uniamo le liste per fare un piccolo riassunto
        val riassuntoLivelli = if(gara.livelliAmmessi.isNotEmpty()) gara.livelliAmmessi.joinToString() else "Tutti"
        val riassuntoCategorie = if(gara.categorieAmmesse.isNotEmpty()) gara.categorieAmmesse.joinToString() else "Tutte"

        holder.tvFiltri.text = "⭐ Livelli: $riassuntoLivelli\n👧 Categorie: $riassuntoCategorie"

        holder.btnIscrizioni.setOnClickListener {
            onIscrizioniClick(gara)
        }
    }

    override fun getItemCount(): Int = listaGare.size
}