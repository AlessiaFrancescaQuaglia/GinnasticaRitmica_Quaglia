package com.example.ginnasticaritmica

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.ginnasticaritmica.databinding.FragmentGestioneAtleteBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Calendar

class GestioneAtleteFragment : Fragment() {

    private var _binding: FragmentGestioneAtleteBinding? = null
    private val binding get() = _binding!!

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var adapter: AtletaAdapter
    private val listaAtlete = mutableListOf<Atleta>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGestioneAtleteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Prepariamo il motore della lista scorrevole
        adapter = AtletaAdapter(listaAtlete)

        binding.btnTornaIndietro.setOnClickListener {
            findNavController().navigateUp()
        }
        binding.recyclerViewAtlete.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewAtlete.adapter = adapter

        // 2. Quando l'allenatrice preme il tasto "+"
        binding.fabAggiungiAtleta.setOnClickListener {
            mostraPopupAggiunta()
        }

        // 3. Scarichiamo le atlete dal cloud in tempo reale
        caricaAtleteDalCloud()
    }

    private fun mostraPopupAggiunta() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_aggiungi_atleta, null)
        val etNome = dialogView.findViewById<EditText>(R.id.etNome)
        val etCognome = dialogView.findViewById<EditText>(R.id.etCognome)
        val btnDataNascita = dialogView.findViewById<android.widget.Button>(R.id.btnDataNascita)
        val btnDataCert = dialogView.findViewById<android.widget.Button>(R.id.btnDataCertificato)
        val spinner = dialogView.findViewById<android.widget.Spinner>(R.id.spinnerLivello)

        // Configura Spinner Livelli
        val livelli = arrayOf("LA1", "LA2", "LB1", "LB2", "LC1", "LC2", "LD", "LE", "Silver Base", "Gold")
        spinner.adapter = android.widget.ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, livelli)

        var dataNascitaScelta = ""
        var dataCertScelta = ""

        btnDataNascita.setOnClickListener {
            val cal = Calendar.getInstance()
            android.app.DatePickerDialog(requireContext(), { _, y, m, d ->
                dataNascitaScelta = String.format("%04d-%02d-%02d", y, m + 1, d)
                btnDataNascita.text = "Nata il: $dataNascitaScelta"
            }, 2015, 0, 1).show()
        }

        btnDataCert.setOnClickListener {
            val cal = Calendar.getInstance()
            android.app.DatePickerDialog(requireContext(), { _, y, m, d ->
                dataCertScelta = String.format("%04d-%02d-%02d", y, m + 1, d)
                btnDataCert.text = "Certificato: $dataCertScelta"
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Nuova Ginnasta")
            .setView(dialogView)
            .setPositiveButton("Salva") { _, _ ->
                val nome = etNome.text.toString().trim()
                val cognome = etCognome.text.toString().trim()
                val livello = spinner.selectedItem.toString()

                if (nome.isNotEmpty() && cognome.isNotEmpty() && dataNascitaScelta.isNotEmpty()) {
                    // Calcolo Categoria automatico (copiato dal tuo DettaglioFragment)
                    val cat = calcolaCategoriaDaData(dataNascitaScelta)

                    val nuovaAtleta = Atleta(
                        id = db.collection("Atlete").document().id,
                        nome = "$nome $cognome",
                        categoria = cat,
                        livello = livello,
                        dataNascita = dataNascitaScelta,
                        scadenzaCertificato = dataCertScelta,
                        idAllenatrice = auth.currentUser?.uid ?: ""
                    )

                    db.collection("Atlete").document(nuovaAtleta.id).set(nuovaAtleta)
                        .addOnSuccessListener { Toast.makeText(requireContext(), "Iscritta!", Toast.LENGTH_SHORT).show() }
                } else {
                    Toast.makeText(requireContext(), "Nome, Cognome e Data Nascita sono obbligatori!", Toast.LENGTH_LONG).show()
                }
            }.show()
    }

    private fun calcolaCategoriaDaData(dataStr: String): String {
        val parts = dataStr.split("-")
        val annoNascita = parts[0].toInt()
        val annoCorrente = Calendar.getInstance().get(Calendar.YEAR)
        val eta = annoCorrente - annoNascita
        return when {
            eta in 8..9 -> "Allieve 1"; eta == 10 -> "Allieve 2"; eta == 11 -> "Allieve 3"
            eta == 12 -> "Allieve 4"; eta == 13 -> "Allieve 5 – Prejunior"; eta == 14 -> "Junior 1"
            eta == 15 -> "Junior 2"; eta == 16 -> "Senior 1"; eta in 17..18 -> "Senior 2"
            else -> "Senior 3"
        }
    }

    private fun salvaAtletaSulCloud(nome: String, categoria: String) {
        val idAllenatriceAttuale = auth.currentUser?.uid ?: return

        // Creiamo un nuovo documento vuoto nella cartella "Atlete" su Firestore
        val nuovoDocRef = db.collection("Atlete").document()

        // Costruiamo la ginnasta usando l'ID generato e associandola all'allenatrice
        val nuovaAtleta = Atleta(
            id = nuovoDocRef.id, // Usa 'id' come nella tua data class
            nome = nome,
            categoria = categoria,
            idAllenatrice = idAllenatriceAttuale
        )

        // Inviamo i dati al database
        nuovoDocRef.set(nuovaAtleta)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Ginnasta salvata!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Errore: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun caricaAtleteDalCloud() {
        val idAllenatriceAttuale = auth.currentUser?.uid ?: return

        // Filtriamo nel cloud prendendo solo le ginnaste di questa allenatrice
        db.collection("Atlete")
            .whereEqualTo("idAllenatrice", idAllenatriceAttuale)
            .addSnapshotListener { valore, errore ->
                if (errore != null) return@addSnapshotListener

                if (valore != null) {
                    listaAtlete.clear()
                    for (documento in valore.documents) {
                        val atleta = documento.toObject(Atleta::class.java)
                        if (atleta != null) {
                            listaAtlete.add(atleta)
                        }
                    }
                    adapter.updateData(listaAtlete)
                }
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}