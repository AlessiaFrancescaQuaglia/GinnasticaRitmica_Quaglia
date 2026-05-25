package com.example.ginnasticaritmica

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.ginnasticaritmica.databinding.ItemAtletaBinding

// L'Adapter prende in ingresso la lista delle ginnaste
class AtletaAdapter(private var atleteList: List<Atleta>) : RecyclerView.Adapter<AtletaAdapter.AtletaViewHolder>() {

    // Il ViewHolder "tiene in mano" il collegamento con il singolo rettangolo (item_atleta.xml)
    inner class AtletaViewHolder(val binding: ItemAtletaBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AtletaViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        // Usiamo il DataBinding per "gonfiare" la grafica
        val binding = ItemAtletaBinding.inflate(inflater, parent, false)
        return AtletaViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AtletaViewHolder, position: Int) {
        // Diamo in pasto l'atleta corretta alla variabile XML che hai creato nello step prima!
        val atletaCorrente = atleteList[position]
        holder.binding.atleta = atletaCorrente
        holder.binding.executePendingBindings() // Forza l'aggiornamento grafico immediato
    }

    override fun getItemCount(): Int {
        return atleteList.size
    }

    // Questa funzione ci servirà per aggiornare la lista quando aggiungi una nuova ginnasta
    fun updateData(nuovaLista: List<Atleta>) {
        atleteList = nuovaLista
        notifyDataSetChanged() // Avvisa la RecyclerView che i dati sono cambiati
    }
}