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

    val statiPresenza = mutableMapOf<String, Boolean>()

    class AppelloViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvNome: TextView = view.findViewById(R.id.tvNomeAtletaAppello)
        val radioGroup: RadioGroup = view.findViewById(R.id.radioGroupPresenza)
        val rbPresente: RadioButton = view.findViewById(R.id.rbPresente)
        val rbAssente: RadioButton = view.findViewById(R.id.rbAssente)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppelloViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_presenza, parent, false)
        return AppelloViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppelloViewHolder, position: Int) {
        val atleta = atlete[position]
        holder.tvNome.text = atleta.nome

        holder.radioGroup.setOnCheckedChangeListener(null)
        holder.radioGroup.clearCheck()

        // CONTROLLO INTELLIGENTE: Se l'utente non ha ancora cliccato nulla in questa sessione,
        // verifichiamo se l'atleta ha già la data di oggi salvata nel database
        val dataDiOggi = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        if (!statiPresenza.containsKey(atleta.id)) {
            if (atleta.datePresenze.contains(dataDiOggi)) {
                statiPresenza[atleta.id] = true
            } else if (atleta.dateAssenze.contains(dataDiOggi)) {
                statiPresenza[atleta.id] = false
            }
        }

        // Accendiamo i pallini corretti in base ai dati storici o ai click correnti
        when (statiPresenza[atleta.id]) {
            true -> holder.rbPresente.isChecked = true
            false -> holder.rbAssente.isChecked = true
            null -> {}
        }

        holder.radioGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rbPresente -> statiPresenza[atleta.id] = true
                R.id.rbAssente -> statiPresenza[atleta.id] = false
            }
        }
    }

    override fun getItemCount() = atlete.size

    fun updateData(nuoveAtlete: List<Atleta>) {
        atlete = nuoveAtlete
        notifyDataSetChanged()
    }
}