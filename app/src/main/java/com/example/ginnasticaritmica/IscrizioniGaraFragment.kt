package com.example.ginnasticaritmica

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.ginnasticaritmica.databinding.FragmentIscrizioniGareBinding
import com.google.firebase.firestore.FirebaseFirestore

class IscrizioniGaraFragment : Fragment() {

    private var _binding: FragmentIscrizioniGareBinding? = null
    private val binding get() = _binding!!
    private val db = FirebaseFirestore.getInstance()

    private var garaCorrente: Gara? = null
    private val atleteCompatibili = mutableListOf<Atleta>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentIscrizioniGareBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val garaId = arguments?.getString("garaId") ?: return
        binding.rvAtleteIscrizioni.layoutManager = LinearLayoutManager(requireContext())

        db.collection("Gare").document(garaId).addSnapshotListener { snapshot, _ ->
            if (snapshot != null && snapshot.exists()) {
                garaCorrente = snapshot.toObject(Gara::class.java)
                binding.tvNomeGaraTitolo.text = "Iscrizioni: ${garaCorrente?.nome ?: "Gara"}"
                caricaAtleteCompatibili()
            }
        }
    }

    private fun caricaAtleteCompatibili() {
        val gara = garaCorrente ?: return

        db.collection("Atlete").get().addOnSuccessListener { result ->
            atleteCompatibili.clear()
            for (doc in result) {
                val atleta = doc.toObject(Atleta::class.java)

                // DIFESA CONTRO I CAMPI VUOTI DI FIREBASE!
                val livelliGara = gara.livelliAmmessi ?: emptyList()
                val catGara = gara.categorieAmmesse ?: emptyList()
                val livelloAtleta = atleta.livello ?: ""
                val catAtleta = atleta.categoria ?: ""

                val livelloOk = livelliGara.isEmpty() || livelliGara.contains(livelloAtleta)
                val categoriaOk = catGara.isEmpty() || catGara.contains(catAtleta)

                if (livelloOk && categoriaOk) {
                    // Sicurezza extra: assegniamo forzatamente l'ID del documento all'atleta
                    atleta.id = doc.id
                    atleteCompatibili.add(atleta)
                }
            }
            binding.rvAtleteIscrizioni.adapter = IscrizioniAdapter()
        }
    }

    private fun mostraDialogAttrezzi(atleta: Atleta) {
        val gara = garaCorrente ?: return

        // DIFESA EXTRA: Assicuriamoci che niente sia nullo prima di controllare
        val attrezziGara = gara.attrezziAmmessi ?: emptyList()
        val attrezziAtleta = atleta.attrezziUsati ?: emptyList()
        val atleteIscritte = gara.atleteIscritte ?: mutableMapOf()

        val attrezziPossibili = if (attrezziGara.isNotEmpty()) {
            attrezziGara.intersect(attrezziAtleta.toSet()).toTypedArray()
        } else {
            attrezziAtleta.toTypedArray()
        }

        if (attrezziPossibili.isEmpty()) {
            Toast.makeText(requireContext(), "L'atleta non usa attrezzi ammessi in questa gara!", Toast.LENGTH_LONG).show()
            return
        }

        val attrezziSelezionati = mutableListOf<String>()
        val giaIscritti = atleteIscritte[atleta.id] ?: emptyList()

        val checkedItems = BooleanArray(attrezziPossibili.size) { i ->
            val presente = giaIscritti.contains(attrezziPossibili[i])
            if (presente) attrezziSelezionati.add(attrezziPossibili[i])
            presente
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Iscrivi ${atleta.nome} con:")
            .setMultiChoiceItems(attrezziPossibili, checkedItems) { _, which, isChecked ->
                val attrezzo = attrezziPossibili[which]
                if (isChecked) attrezziSelezionati.add(attrezzo) else attrezziSelezionati.remove(attrezzo)
            }
            .setPositiveButton("Salva") { _, _ ->
                // Usiamo l'armatura anche per salvare
                val nuovaMappa = atleteIscritte.toMutableMap()
                if (attrezziSelezionati.isEmpty()) {
                    nuovaMappa.remove(atleta.id)
                } else {
                    nuovaMappa[atleta.id] = attrezziSelezionati
                }

                db.collection("Gare").document(gara.id).update("atleteIscritte", nuovaMappa)
                    .addOnSuccessListener { Toast.makeText(requireContext(), "Iscrizione salvata!", Toast.LENGTH_SHORT).show() }
            }
            .setNegativeButton("Annulla", null).show()
    }

    // --- ADAPTER BLINDATO ---
    inner class IscrizioniAdapter : RecyclerView.Adapter<IscrizioniAdapter.ViewHolder>() {
        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvNome: TextView = view.findViewById(R.id.tvNomeAtletaIscrizione)
            val tvDettagli: TextView = view.findViewById(R.id.tvDettagliAtletaIscrizione)
            val tvStato: TextView = view.findViewById(R.id.tvStatoIscrizione)
            val btnIscrivi: Button = view.findViewById(R.id.btnGestisciAttrezzi)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_atleta_iscrizione, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val atleta = atleteCompatibili[position]
            val gara = garaCorrente ?: return
            val atleteIscritte = gara.atleteIscritte ?: mutableMapOf() // Evitiamo null!

            holder.tvNome.text = atleta.nome ?: "Sconosciuta"
            holder.tvDettagli.text = "${atleta.livello ?: "?"} - ${atleta.categoria ?: "?"}"

            val attrezziIscritti = atleteIscritte[atleta.id]
            if (attrezziIscritti != null && attrezziIscritti.isNotEmpty()) {
                holder.tvStato.text = "✅ Iscritta: ${attrezziIscritti.joinToString()}"
                holder.tvStato.setTextColor(android.graphics.Color.parseColor("#008577"))
                holder.btnIscrivi.text = "Modifica"
            } else {
                holder.tvStato.text = "❌ Non iscritta"
                holder.tvStato.setTextColor(android.graphics.Color.RED)
                holder.btnIscrivi.text = "Iscrivi"
            }

            holder.btnIscrivi.setOnClickListener { mostraDialogAttrezzi(atleta) }
        }

        override fun getItemCount(): Int = atleteCompatibili.size
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}