package com.example.ginnasticaritmica.ui.fragment

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.ginnasticaritmica.ui.adapter.GareAdapter
import com.example.ginnasticaritmica.R
import com.example.ginnasticaritmica.databinding.FragmentListaGareBinding
import com.example.ginnasticaritmica.model.Gara
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.util.Calendar
import java.util.Locale
import java.util.UUID

class ListaGareFragment : Fragment() {

    private var _binding: FragmentListaGareBinding? = null
    private val binding get() = _binding!!
    private val db = FirebaseFirestore.getInstance()

    private lateinit var adapter: GareAdapter
    private val listaGare = mutableListOf<Gara>()

    // Variabile per spegnere l'ascolto di Firebase quando usciamo dalla pagina
    private var garaListener: ListenerRegistration? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentListaGareBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Passo un blocco di codice direttamente nell'Adapter
        adapter = GareAdapter(listaGare) { garaSelezionata ->
            // USIAMO SAFE ARGS PER PASSARE L'ID DELLA GARA
            val action =
                ListaGareFragmentDirections.Companion.actionListaGareFragmentToIscrizioniGaraFragment(
                    garaId = garaSelezionata.id
                )
            findNavController().navigate(action)
        }

        binding.rvListaGare.layoutManager = LinearLayoutManager(requireContext())
        binding.rvListaGare.adapter = adapter

        // Inizializza l'ascolto del database in tempo reale
        caricaGareDaFirebase()

        binding.btnAggiungiGara.setOnClickListener { mostraDialogCreaGara() }
        binding.btnTornaIndietro.setOnClickListener { findNavController().navigateUp() }
    }

    //REAL-TIME DATA BINDING (Firestore)
    @SuppressLint("NotifyDataSetChanged")
    private fun caricaGareDaFirebase() {
        // addSnapshotListener garantisce che l'elenco gare sia sempre sincronizzato senza fare pull-to-refresh
        garaListener = db.collection("Gare").addSnapshotListener { snapshot, error ->
            if (error != null) {
                Toast.makeText(requireContext(), "Errore di caricamento", Toast.LENGTH_SHORT).show()
                return@addSnapshotListener
            }
            if (snapshot != null) {
                listaGare.clear()
                for (documento in snapshot.documents) {
                    // Deserializzazione da Documento NoSQL a Oggetto Kotlin
                    val gara = documento.toObject(Gara::class.java)
                    if (gara != null) {
                        listaGare.add(gara)
                    }
                }
                // Diciamo all'Adapter che i dati sono cambiati
                adapter.notifyDataSetChanged()
            }
        }
    }

    //UI GENERATOR: Costruzione dinamica dell'interfaccia di inserimento
    @SuppressLint("SetTextI18n")
    private fun mostraDialogCreaGara() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_crea_gara, null)

        // Risoluzione delle view interne al layout del Dialog
        val etNome = dialogView.findViewById<EditText>(R.id.etNomeGara)
        val etLuogo = dialogView.findViewById<EditText>(R.id.etLuogoGara)
        val tvData = dialogView.findViewById<TextView>(R.id.tvDataGara)
        val tvLivelli = dialogView.findViewById<TextView>(R.id.tvLivelliGara)
        val tvCategorie = dialogView.findViewById<TextView>(R.id.tvCategorieGara)
        val tvAttrezzi = dialogView.findViewById<TextView>(R.id.tvAttrezziGara)

        // Variabili di stato temporanee per comporre il JSON finale
        var dataScelta = ""
        val livelliScelti = mutableListOf<String>()
        val categorieScelte = mutableListOf<String>()
        val attrezziScelti = mutableListOf<String>()

        tvData.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(requireContext(), { _, year, month, day ->
                dataScelta = String.Companion.format(
                    Locale.getDefault(),
                    "%02d/%02d/%04d",
                    day,
                    month + 1,
                    year
                )
                tvData.text = "📅 Data: $dataScelta"
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        //MULTI-CHOICE DIALOG: Uso di array booleani per gestire selezioni multiple
        tvLivelli.setOnClickListener {
            val arrayLivelli = arrayOf("LA1", "LA2", "LB1", "LB2", "LC1", "LC2", "LD", "LE", "Gold")
            // Array di stato (tutti false all'inizio)
            val checked = BooleanArray(arrayLivelli.size)

            AlertDialog.Builder(requireContext()).setTitle("Livelli Ammessi")
                .setMultiChoiceItems(arrayLivelli, checked) { _, which, isChecked ->
                    val item = arrayLivelli[which]
                    if (isChecked) livelliScelti.add(item) else livelliScelti.remove(item)
                }.setPositiveButton("Conferma") { _, _ ->
                    tvLivelli.text = "⭐ Livelli: ${livelliScelti.joinToString()}"
                }.show()
        }

        //Stessa logica del multiscelta per Categorie e Attrezzi
        tvCategorie.setOnClickListener {
            val arrayCat = arrayOf("Allieve 1", "Allieve 2", "Allieve 3", "Allieve 4", "Allieve 5 – Prejunior", "Junior 1", "Junior 2", "Senior 1", "Senior 2", "Senior 3")
            val checked = BooleanArray(arrayCat.size)
            AlertDialog.Builder(requireContext()).setTitle("Categorie Ammesse")
                .setMultiChoiceItems(arrayCat, checked) { _, which, isChecked ->
                    val item = arrayCat[which]
                    if (isChecked) categorieScelte.add(item) else categorieScelte.remove(item)
                }.setPositiveButton("Conferma") { _, _ ->
                    tvCategorie.text = "👧 Categorie: ${categorieScelte.joinToString()}"
                }.show()
        }

        tvAttrezzi.setOnClickListener {
            val arrayAttrezzi = arrayOf("Corpo Libero", "Fune", "Cerchio", "Palla", "Clavette", "Nastro")
            val checked = BooleanArray(arrayAttrezzi.size)
            AlertDialog.Builder(requireContext()).setTitle("Attrezzi Ammessi")
                .setMultiChoiceItems(arrayAttrezzi, checked) { _, which, isChecked ->
                    val item = arrayAttrezzi[which]
                    if (isChecked) attrezziScelti.add(item) else attrezziScelti.remove(item)
                }.setPositiveButton("Conferma") { _, _ ->
                    tvAttrezzi.text = "🎗️ Attrezzi: ${attrezziScelti.joinToString()}"
                }.show()
        }

        // OBJECT CREATION & DATABASE COMMIT
        AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setPositiveButton("Crea Gara") { _, _ ->
                val nome = etNome.text.toString()

                if (nome.isNotEmpty() && dataScelta.isNotEmpty()) {

                    //Crea un ID univoco universale, garantendo che due gare non possano mai sovrascriversi a vicenda
                    val idGara = UUID.randomUUID().toString()

                    val nuovaGara = Gara(
                        id = idGara,
                        nome = nome,
                        luogo = etLuogo.text.toString(),
                        data = dataScelta,
                        livelliAmmessi = livelliScelti,
                        categorieAmmesse = categorieScelte,
                        attrezziAmmessi = attrezziScelti
                    )

                    // Scrittura diretta indicando l'ID del documento
                    db.collection("Gare").document(idGara).set(nuovaGara)
                        .addOnSuccessListener {
                            Toast.makeText(requireContext(), "Gara creata con successo!", Toast.LENGTH_SHORT).show()
                        }
                } else {
                    Toast.makeText(requireContext(), "Inserisci Nome e Data per procedere!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()

        //Spegniamo il listener di Firestore
        garaListener?.remove()

        _binding = null
    }
}