package com.example.ginnasticaritmica

import android.annotation.SuppressLint
import android.content.res.ColorStateList
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
import androidx.core.graphics.toColorInt

class AtletaAdapter(private var listaAtlete: List<Atleta>) :
    RecyclerView.Adapter<AtletaAdapter.AtletaViewHolder>() {

    // 1. ViewHolder: pattern standard per ottimizzare la RecyclerView
    // Ci permette di "scoprire" le View del layout una volta sola per riga.
    class AtletaViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvNome: TextView = itemView.findViewById(R.id.tvNomeAtleta)
        val tvCategoria: TextView = itemView.findViewById(R.id.tvCategoriaAtleta)
        val tvIniziale: TextView = itemView.findViewById(R.id.tvIniziale)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AtletaViewHolder {
        // "Gonfiamo" (inflate) l'XML della singola riga per trasformarlo in un oggetto View
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_atleta, parent, false)
        return AtletaViewHolder(view)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: AtletaViewHolder, position: Int) {
        val atleta = listaAtlete[position]
        // 2. Dati di base: Nome, Livello e Categoria
        holder.tvNome.text = atleta.nome
        // Uso operatori condizionali per evitare stringhe vuote se il dato manca
        val livello = atleta.livello.ifEmpty { "Livello ?" }
        val categoria = atleta.categoria.ifEmpty { "Categoria ?" }
        holder.tvCategoria.text = "$livello • $categoria"

        //creazione dell'iniziale per il profilo
        if (atleta.nome.isNotEmpty()) {
            holder.tvIniziale.text = atleta.nome.take(1).uppercase()
        } else {
            holder.tvIniziale.text = "-"
        }

        // LOGICA CERTIFICATO MEDICO:
        // Parto dal presupposto che se non ho dati, il certificato è "non valido" (Rosso)
        var coloreSemaforo = "#CF6679".toColorInt()

        // Controllo se esiste effettivamente una data scritta
        if (atleta.scadenzaCertificato.isNotEmpty() && atleta.scadenzaCertificato != "Nessuna scadenza inserita") {
            try {
                // Trasformiamo il testo "2024-10-15" in una vera Data calcolabile
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val dataScad = sdf.parse(atleta.scadenzaCertificato)
                val dataOggi = Date()

                if (dataScad != null) {
                    // Calcoliamo quanti millisecondi mancano, e li convertiamo in giorni
                    val diffMillies = dataScad.time - dataOggi.time
                    val diffGiorni = TimeUnit.DAYS.convert(diffMillies, TimeUnit.MILLISECONDS)

                    coloreSemaforo = when {
                        diffGiorni < 0 -> "#CF6679".toColorInt() // Rosso (Scaduto)
                        diffGiorni in 0..30 -> "#FFB300".toColorInt() // Giallo (In scadenza)
                        else -> "#03DAC5".toColorInt() // Verde (Valido)
                    }
                }
            } catch (_: Exception) {
                // Se c'è un errore nella data, resta rosso
            }
        }

        // coloro il cerchietto iniziale in base al colore dello sfondo
        holder.tvIniziale.backgroundTintList = ColorStateList.valueOf(coloreSemaforo)

        // LOGICA DI NAVIGAZIONE AL CLICK
        holder.itemView.setOnClickListener {
            // Il blocco è formato da due righe:ID e Nome dell'atleta
            // per far sapere alla prossima pagina di chi deve scaricare i dati completi.
            val bundle = Bundle().apply {
                putString("atletaId", atleta.id)
                putString("atletaNome", atleta.nome)
            }
            // Uso il Navigation Component per muovermi
            it.findNavController().navigate(
                R.id.action_gestioneAtleteFragment_to_dettaglioAtletaFragment,
                bundle
            )
        }
    }

    override fun getItemCount(): Int = listaAtlete.size

    // Metodo per ricaricare la RecyclerView quando Firestore invia nuovi dati
    fun updateData(nuovaLista: List<Atleta>) {
        this.listaAtlete = nuovaLista
        notifyDataSetChanged()
    }
}