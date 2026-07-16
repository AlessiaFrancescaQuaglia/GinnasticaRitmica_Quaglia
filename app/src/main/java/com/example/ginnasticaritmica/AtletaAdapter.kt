package com.example.ginnasticaritmica

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.graphics.toColorInt
import androidx.navigation.findNavController
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class AtletaAdapter(private var listaAtlete: List<Atleta>) :
    RecyclerView.Adapter<AtletaAdapter.AtletaViewHolder>() {

    // ViewHolder: pattern standard per ottimizzare la RecyclerView
    //permette di scoprire le View del layout una volta sola per riga.
    class AtletaViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvNome: TextView = itemView.findViewById(R.id.tvNomeAtleta)
        val tvCategoria: TextView = itemView.findViewById(R.id.tvCategoriaAtleta)
        val tvIniziale: TextView = itemView.findViewById(R.id.tvIniziale)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AtletaViewHolder {
        // modifica l'XML della singola riga per trasformarlo in un oggetto View
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_atleta, parent, false)
        return AtletaViewHolder(view)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: AtletaViewHolder, position: Int) {
        val atleta = listaAtlete[position]
        // Dati di base: Nome, Livello e Categoria
        holder.tvNome.text = atleta.nome
        //operatori condizionali per evitare stringhe vuote se il dato manca
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
                // Trasformiamo il testo in una vera Data calcolabile
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val dataScad = sdf.parse(atleta.scadenzaCertificato)
                val dataOggi = Date()

                if (dataScad != null) {
                    // Calcola quanti millisecondi mancano, e li convertiamo in giorni
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
            // Usiamo la classe generata da Safe Args per navigare verso il dettaglio dell'atleta
            val action = GestioneAtleteFragmentDirections.actionGestioneAtleteFragmentToDettaglioAtletaFragment(
                atletaId = atleta.id,
                atletaNome = atleta.nome
            )
            // Uso il Navigation Component per muovermi passando l'azione sicura
            it.findNavController().navigate(action)
        }
    }

    override fun getItemCount(): Int = listaAtlete.size

    // Metodo per ricaricare la RecyclerView quando Firestore invia nuovi dati
    fun updateData(nuovaLista: List<Atleta>) {
        this.listaAtlete = nuovaLista
        notifyDataSetChanged()
    }
}