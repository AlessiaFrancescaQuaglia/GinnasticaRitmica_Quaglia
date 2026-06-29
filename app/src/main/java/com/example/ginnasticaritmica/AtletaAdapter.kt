package com.example.ginnasticaritmica

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.navigation.findNavController
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class AtletaAdapter(private var listaAtlete: List<Atleta>) :
    RecyclerView.Adapter<AtletaAdapter.AtletaViewHolder>() {

    class AtletaViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvNome: TextView = itemView.findViewById(R.id.tvNomeAtleta)
        val tvCategoria: TextView = itemView.findViewById(R.id.tvCategoriaAtleta)
        val tvIniziale: TextView = itemView.findViewById(R.id.tvIniziale)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AtletaViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_atleta, parent, false)
        return AtletaViewHolder(view)
    }

    override fun onBindViewHolder(holder: AtletaViewHolder, position: Int) {
        val atleta = listaAtlete[position]

        holder.tvNome.text = atleta.nome

        val livello = if (atleta.livello.isNotEmpty()) atleta.livello else "Livello ?"
        val categoria = if (atleta.categoria.isNotEmpty()) atleta.categoria else "Categoria ?"
        holder.tvCategoria.text = "$livello • $categoria"

        if (atleta.nome.isNotEmpty()) {
            holder.tvIniziale.text = atleta.nome.take(1).uppercase()
        } else {
            holder.tvIniziale.text = "-"
        }

        // --- LA MAGIA DEL SEMAFORO MEDICO ---
        var coloreSemaforo = Color.parseColor("#CF6679") // Rosso di default (se non ha la data)

        if (atleta.scadenzaCertificato.isNotEmpty() && atleta.scadenzaCertificato != "Nessuna scadenza inserita") {
            try {
                // Trasformiamo il testo "2024-10-15" in una vera Data calcolabile
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val dataScad = sdf.parse(atleta.scadenzaCertificato)
                val dataOggi = Date()

                if (dataScad != null) {
                    // Calcoliamo quanti millisecondi mancano, e li convertiamo in giorni!
                    val diffMillies = dataScad.time - dataOggi.time
                    val diffGiorni = TimeUnit.DAYS.convert(diffMillies, TimeUnit.MILLISECONDS)

                    coloreSemaforo = when {
                        diffGiorni < 0 -> Color.parseColor("#CF6679") // Rosso (Scaduto)
                        diffGiorni in 0..30 -> Color.parseColor("#FFB300") // Giallo (In scadenza)
                        else -> Color.parseColor("#03DAC5") // Verde (Valido)
                    }
                }
            } catch (e: Exception) {
                // Se c'è un errore nella data, resta rosso
            }
        }

        // Coloriamo il cerchietto dell'iniziale!
        holder.tvIniziale.backgroundTintList = ColorStateList.valueOf(coloreSemaforo)
        // ------------------------------------

        // LOGICA DI NAVIGAZIONE AL CLICK
        holder.itemView.setOnClickListener {
            val bundle = Bundle().apply {
                putString("atletaId", atleta.id)
                putString("atletaNome", atleta.nome)
            }
            it.findNavController().navigate(
                R.id.action_gestioneAtleteFragment_to_dettaglioAtletaFragment,
                bundle
            )
        }
    }

    override fun getItemCount(): Int = listaAtlete.size

    fun updateData(nuovaLista: List<Atleta>) {
        this.listaAtlete = nuovaLista
        notifyDataSetChanged()
    }
}