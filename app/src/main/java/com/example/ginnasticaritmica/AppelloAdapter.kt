package com.example.ginnasticaritmica

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AppelloAdapter(private var atlete: List<Atleta>) : RecyclerView.Adapter<AppelloAdapter.AppelloViewHolder>() {

    // Salva i cambiamenti dell'allenatrice in un buffer temporaneo.finché non viene premuto "Salva" (o il tasto per inviare al DB), senza scrivere nel database a ogni singolo click.
    val statiPresenza = mutableMapOf<String, Boolean>()

    class AppelloViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        // ViewHolder:il pattern che evita di cercare ogni volta la vista nel layout
        val tvNome: TextView = view.findViewById(R.id.tvNomeAtletaAppello)
        val radioGroup: RadioGroup = view.findViewById(R.id.radioGroupPresenza)
        val rbPresente: RadioButton = view.findViewById(R.id.rbPresente)
        val rbAssente: RadioButton = view.findViewById(R.id.rbAssente)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppelloViewHolder {
        // Inflate: trasforma il file XML in oggetto grafico (View)
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_presenza, parent, false)
        return AppelloViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppelloViewHolder, position: Int) {
        val atleta = atlete[position]
        holder.tvNome.text = atleta.nome

        //Poiché Android riutilizza le stesse righe per risparmiare memoria, assicura che non ci siano ancora selezionate le opzioni di una riga vecchia.
        holder.radioGroup.setOnCheckedChangeListener(null)
        holder.radioGroup.clearCheck()

        // Se non c'è ancora un click salvato nel buffer, guardiamo lo storico del database.
        val dataDiOggi = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        if (!statiPresenza.containsKey(atleta.id)) {
            if (atleta.datePresenze.contains(dataDiOggi)) {
                statiPresenza[atleta.id] = true
            } else if (atleta.dateAssenze.contains(dataDiOggi)) {
                statiPresenza[atleta.id] = false
            }
        }

        // Visualizzazione: accensione del bottone in base allo stato nel buffer
        when (statiPresenza[atleta.id]) {
            true -> holder.rbPresente.isChecked = true
            false -> holder.rbAssente.isChecked = true
            null -> {} // Nessuno stato salvato, lasciamo vuoto
        }

        // Listener: ogni volta che l'allenatrice clicca, aggiorna il buffer temporaneo
        holder.radioGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rbPresente -> statiPresenza[atleta.id] = true
                R.id.rbAssente -> statiPresenza[atleta.id] = false
            }
        }
    }

    override fun getItemCount() = atlete.size

    // Metodo per aggiornare la lista senza ricreare l'adapter
    fun updateData(nuoveAtlete: List<Atleta>) {
        atlete = nuoveAtlete
        notifyDataSetChanged() // Dice alla lista di aggiornarsi perche i dati sono stati modificati
    }
}