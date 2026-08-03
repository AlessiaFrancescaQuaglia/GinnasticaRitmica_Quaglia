package com.example.ginnasticaritmica.ui.fragment

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.ginnasticaritmica.IscrizioniGaraFragmentArgs
import com.example.ginnasticaritmica.R
import com.example.ginnasticaritmica.databinding.FragmentIscrizioniGareBinding
import com.example.ginnasticaritmica.model.Atleta
import com.example.ginnasticaritmica.model.Gara
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class IscrizioniGaraFragment : Fragment() {

    // ViewBinding: accesso sicuro agli elementi grafici
    private var _binding: FragmentIscrizioniGareBinding? = null
    private val binding get() = _binding!!

    // Inizializzazione Safe Args
    private val args: IscrizioniGaraFragmentArgs by navArgs()

    // Connessione al database Firestore
    private val db = FirebaseFirestore.getInstance()

    // Variabili d'appoggio per memorizzare la gara aperta e la lista di atlete idonee
    private var garaCorrente: Gara? = null
    private val atleteCompatibili = mutableListOf<Atleta>()
    private var garaListener: ListenerRegistration? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentIscrizioniGareBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Recuperiamo l'ID della gara dalla pagina precedente tramite Safe Args
        val garaId = args.garaId
        if (garaId.isEmpty()) return

        // Impostiamo la lista delle atlete da iscrivere
        binding.rvAtleteIscrizioni.layoutManager = LinearLayoutManager(requireContext())

        //Scarica e aggiorna i dati della gara in tempo reale
        garaListener = db.collection("Gare").document(garaId).addSnapshotListener { snapshot, _ ->
            if (snapshot != null && snapshot.exists()) {
                // Trasforma il JSON in oggetto Kotlin
                garaCorrente = snapshot.toObject(Gara::class.java)
                binding.tvNomeGaraTitolo.text = "Iscrizioni: ${garaCorrente?.nome ?: "Gara"}"

                // Ora che abbiamo i requisiti della gara, cerchiamo le atlete
                caricaAtleteCompatibili()
            }
        }
    }

    private fun caricaAtleteCompatibili() {
        val gara = garaCorrente ?: return
        val uidAllenatrice = FirebaseAuth.getInstance().currentUser?.uid ?: return

        //Scarica solo le atlete di questa allenatrice
        db.collection("Atlete")
            .whereEqualTo("idAllenatrice", uidAllenatrice)
            .get()
            .addOnSuccessListener { result ->
                atleteCompatibili.clear() // Svuota la lista per evitare duplicati

                for (doc in result) {
                    val atleta = doc.toObject(Atleta::class.java)

                    //Controlla se l'atleta ha il livello e categoria richiesti
                    val livelloOk = gara.livelliAmmessi.isEmpty() || gara.livelliAmmessi.contains(atleta.livello)
                    val categoriaOk = gara.categorieAmmesse.isEmpty() || gara.categorieAmmesse.contains(atleta.categoria)

                    // Se i requisiti combaciano, aggiunge l'atleta alla lista visibile
                    if (livelloOk && categoriaOk) {
                        atleta.id = doc.id // Assegna l'ID del documento all'oggetto
                        atleteCompatibili.add(atleta)
                    }
                }
                // Collega i dati trovati alla grafica tramite l'Adapter
                binding.rvAtleteIscrizioni.adapter = IscrizioniAdapter()
            }
    }

    private fun mostraDialogAttrezzi(atleta: Atleta) {
        val gara = garaCorrente ?: return

        // Trova solo gli attrezzi in comune tra gara e atleta
        val attrezziPossibili = if (gara.attrezziAmmessi.isNotEmpty()) {
            gara.attrezziAmmessi.intersect(atleta.attrezziUsati.toSet()).toTypedArray()
        } else {
            atleta.attrezziUsati.toTypedArray() // Se la gara non ha limiti, valgono tutti i suoi attrezzi
        }

        // se non ci sono attrezzi in comune, ferma l'iscrizione
        if (attrezziPossibili.isEmpty()) {
            Toast.makeText(requireContext(), "L'atleta non usa attrezzi ammessi in questa gara!", Toast.LENGTH_LONG).show()
            return
        }

        val attrezziSelezionati = mutableListOf<String>()
        // Controlla se l'atleta era già iscritta a questa gara in precedenza
        val giaIscritti = gara.atleteIscritte[atleta.id] ?: emptyList()

        // Array di booleani per spuntare in automatico gli attrezzi già scelti
        val checkedItems = BooleanArray(attrezziPossibili.size) { i ->
            val presente = giaIscritti.contains(attrezziPossibili[i])
            if (presente) attrezziSelezionati.add(attrezziPossibili[i])
            presente
        }

        // Creazione del popup visivo
        AlertDialog.Builder(requireContext())
            .setTitle("Iscrivi ${atleta.nome} con:")
            .setMultiChoiceItems(attrezziPossibili, checkedItems) { _, which, isChecked ->
                val attrezzo = attrezziPossibili[which]
                // Aggiorna la lista temporanea in base alle spunte dell'utente
                if (isChecked) attrezziSelezionati.add(attrezzo) else attrezziSelezionati.remove(attrezzo)
            }
            .setPositiveButton("Salva") { _, _ ->

                //Creiamo una mappa modificabile per non corrompere i dati originali
                val nuovaMappa = gara.atleteIscritte.toMutableMap()

                // Se ha tolto tutte le spunte, la disiscriviamo rimuovendo il suo ID
                if (attrezziSelezionati.isEmpty()) {
                    nuovaMappa.remove(atleta.id)
                } else {
                    // Altrimenti salviamo i nuovi attrezzi scelti
                    nuovaMappa[atleta.id] = attrezziSelezionati
                }

                // Scriviamo l'aggiornamento parziale (solo atleteIscritte) su Firestore
                db.collection("Gare").document(gara.id).update("atleteIscritte", nuovaMappa)
                    .addOnSuccessListener { Toast.makeText(requireContext(), "Iscrizione salvata!", Toast.LENGTH_SHORT).show() }
            }
            .setNegativeButton("Annulla", null).show()
    }

    // INNER CLASS ADAPTER: "inner" le permette di leggere le variabili del Fragment esterno
    inner class IscrizioniAdapter : RecyclerView.Adapter<IscrizioniAdapter.ViewHolder>() {

        // memorizza le view per evitare continui findViewById pesanti
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


        //funzione che viene chiamata per ogni elemento della lista
        @SuppressLint("SetTextI18n")
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val atleta = atleteCompatibili[position]
            val gara = garaCorrente ?: return

            // Riempimento testi base
            holder.tvNome.text = atleta.nome
            holder.tvDettagli.text = "${atleta.livello} - ${atleta.categoria}"

            // Controlliamo la mappa delle iscrizioni tramite l'ID dell'atleta
            val attrezziIscritti = gara.atleteIscritte[atleta.id]

            // se la lista attrezzi dell'atleta non è vuota, è iscritta
            if (!attrezziIscritti.isNullOrEmpty()) {
                holder.tvStato.text = "Iscritta: ${attrezziIscritti.joinToString()}"
                holder.tvStato.setTextColor("#008577".toColorInt())
                holder.btnIscrivi.text = "Modifica"
            } else {
                holder.tvStato.text = "Non iscritta"
                holder.tvStato.setTextColor(Color.RED)
                holder.btnIscrivi.text = "Iscrivi"
            }

            // Click sul bottone per aprire il popup degli attrezzi
            holder.btnIscrivi.setOnClickListener { mostraDialogAttrezzi(atleta) }
        }

        override fun getItemCount(): Int = atleteCompatibili.size
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Spegniamo il listener di Firestore e scolleghiamo la grafica
        garaListener?.remove()
        _binding = null
    }
}