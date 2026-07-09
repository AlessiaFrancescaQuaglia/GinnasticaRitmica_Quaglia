package com.example.ginnasticaritmica

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
import com.example.ginnasticaritmica.databinding.FragmentListaGareBinding
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Calendar
import java.util.Locale
import java.util.UUID

class ListaGareFragment : Fragment() {

    private var _binding: FragmentListaGareBinding? = null
    private val binding get() = _binding!!
    private val db = FirebaseFirestore.getInstance()

    // --- NUOVO: Variabili per la lista ---
    private lateinit var adapter: GareAdapter
    private val listaGare = mutableListOf<Gara>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentListaGareBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // --- NUOVO: Configurazione RecyclerView ---
        adapter = GareAdapter(listaGare) { garaSelezionata ->
            // Passa l'ID della gara alla nuova pagina e naviga!
            val bundle = Bundle()
            bundle.putString("garaId", garaSelezionata.id)
            androidx.navigation.fragment.NavHostFragment.findNavController(this)
                .navigate(R.id.iscrizioniGaraFragment, bundle)
        }
        binding.rvListaGare.layoutManager = LinearLayoutManager(requireContext())
        binding.rvListaGare.adapter = adapter

        // Scarica le gare in tempo reale da Firebase!
        caricaGareDaFirebase()

        // Bottone per aggiungere gara
        binding.btnAggiungiGara.setOnClickListener {
            mostraDialogCreaGara()
        }
        // Al click sulla freccia, torna indietro alla Dashboard!
        binding.btnTornaIndietro.setOnClickListener {
            findNavController().navigateUp()
        }
    }

    // --- NUOVO: Funzione magica per scaricare in Tempo Reale ---
    private fun caricaGareDaFirebase() {
        // Usiamo addSnapshotListener così se aggiungi una gara, la lista si aggiorna da sola!
        db.collection("Gare").addSnapshotListener { snapshot, error ->
            if (error != null) {
                Toast.makeText(requireContext(), "Errore di caricamento", Toast.LENGTH_SHORT).show()
                return@addSnapshotListener
            }
            if (snapshot != null) {
                listaGare.clear()
                for (documento in snapshot.documents) {
                    val gara = documento.toObject(Gara::class.java)
                    if (gara != null) {
                        listaGare.add(gara)
                    }
                }
                adapter.notifyDataSetChanged() // Diciamo all'Adapter di ridisegnare la grafica
            }
        }
    }

    private fun mostraDialogCreaGara() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_crea_gara, null)
        val etNome = dialogView.findViewById<EditText>(R.id.etNomeGara)
        val etLuogo = dialogView.findViewById<EditText>(R.id.etLuogoGara)
        val tvData = dialogView.findViewById<TextView>(R.id.tvDataGara)
        val tvLivelli = dialogView.findViewById<TextView>(R.id.tvLivelliGara)
        val tvCategorie = dialogView.findViewById<TextView>(R.id.tvCategorieGara)
        val tvAttrezzi = dialogView.findViewById<TextView>(R.id.tvAttrezziGara)

        var dataScelta = ""
        val livelliScelti = mutableListOf<String>()
        val categorieScelte = mutableListOf<String>()
        val attrezziScelti = mutableListOf<String>()

        tvData.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(requireContext(), { _, year, month, day ->
                dataScelta = String.format(Locale.getDefault(), "%02d/%02d/%04d", day, month + 1, year)
                tvData.text = "📅 Data: $dataScelta"
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        tvLivelli.setOnClickListener {
            val arrayLivelli = arrayOf("LA1", "LA2", "LB1", "LB2", "LC1", "LC2", "LD", "LE", "Gold")
            val checked = BooleanArray(arrayLivelli.size)
            AlertDialog.Builder(requireContext()).setTitle("Livelli Ammessi")
                .setMultiChoiceItems(arrayLivelli, checked) { _, which, isChecked ->
                    val item = arrayLivelli[which]
                    if (isChecked) livelliScelti.add(item) else livelliScelti.remove(item)
                }.setPositiveButton("Conferma") { _, _ ->
                    tvLivelli.text = "⭐ Livelli: ${livelliScelti.joinToString()}"
                }.show()
        }

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

        AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setPositiveButton("Crea Gara") { _, _ ->
                val nome = etNome.text.toString()
                if (nome.isNotEmpty() && dataScelta.isNotEmpty()) {
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
        _binding = null
    }
}