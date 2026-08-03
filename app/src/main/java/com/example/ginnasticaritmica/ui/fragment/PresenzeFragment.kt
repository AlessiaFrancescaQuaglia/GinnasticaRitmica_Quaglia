package com.example.ginnasticaritmica.ui.fragment

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.ginnasticaritmica.ui.adapter.AppelloAdapter
import com.example.ginnasticaritmica.databinding.FragmentPresenzeBinding
import com.example.ginnasticaritmica.model.Atleta
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.collections.iterator

class PresenzeFragment : Fragment() {

    private var _binding: FragmentPresenzeBinding? = null
    private val binding get() = _binding!!

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var adapter: AppelloAdapter

    // Lista attualmente in uso (filtrata o meno) e Lista "Backup" per ripristinare i filtri
    private var listaAtlete = mutableListOf<Atleta>()
    private val listaAtleteCompleta = mutableListOf<Atleta>()

    // Memoria di stato per il filtro (così non si resetta quando Firebase si aggiorna)
    private var gruppoAttualeFiltro = "Mostra Tutte"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPresenzeBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val dataDiOggi = SimpleDateFormat("dd MMMM yyyy", Locale.ITALY).format(Date())
        binding.btnTornaIndietro.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.tvDataOggi.text = "Data: $dataDiOggi"

        // Inizializzazione Adapter
        adapter = AppelloAdapter(listaAtlete)
        binding.rvAppello.layoutManager = LinearLayoutManager(requireContext())
        binding.rvAppello.adapter = adapter

        // SCARICAMENTO REAL-TIME (Snapshot)
        caricaAtleteAppello()

        // Tasto Salva Appello
        binding.btnSalvaAppello.setOnClickListener { salvaAppello() }
        // Tasto Filtra Gruppo
        binding.btnFiltraGruppo.setOnClickListener { mostraDialogFiltroGruppo() }
        // Tasto Esporta Report
        binding.btnEsportaReport.setOnClickListener { mostraDialogSceltaMese() }
    }

    private fun caricaAtleteAppello() {
        val idAllenatrice = auth.currentUser?.uid ?: return

        // USO DEL LISTENER: Adesso la memoria locale si aggiorna istantaneamente ogni volta che premi "Salva"
        db.collection("Atlete")
            .whereEqualTo("idAllenatrice", idAllenatrice)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener

                if (snapshot != null) {
                    listaAtleteCompleta.clear()
                    for (doc in snapshot.documents) {
                        val atleta = doc.toObject(Atleta::class.java)
                        if (atleta != null) listaAtleteCompleta.add(atleta)
                    }

                    // Riapplichiamo il filtro corrente (così l'interfaccia non si blocca)
                    listaAtlete = if (gruppoAttualeFiltro == "Mostra Tutte") {
                        listaAtleteCompleta.toMutableList()
                    } else {
                        listaAtleteCompleta.filter { it.gruppo == gruppoAttualeFiltro }.toMutableList()
                    }
                    adapter.updateData(listaAtlete)
                }
            }
    }


    @SuppressLint("SetTextI18n")
    private fun salvaAppello() {
        val stati = adapter.statiPresenza

        if (stati.isEmpty()) {
            Toast.makeText(requireContext(), "Segna almeno una presenza o assenza prima di salvare!", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnSalvaAppello.isEnabled = false
        binding.btnSalvaAppello.text = "Salvataggio..."

        val batch = db.batch()
        val dataOggiIso = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

        for ((idAtleta, isPresente) in stati) {
            val docRef = db.collection("Atlete").document(idAtleta)

            if (isPresente) {
                batch.update(docRef, "datePresenze", FieldValue.arrayUnion(dataOggiIso))
                batch.update(docRef, "dateAssenze", FieldValue.arrayRemove(dataOggiIso))
            } else {
                batch.update(docRef, "dateAssenze", FieldValue.arrayUnion(dataOggiIso))
                batch.update(docRef, "datePresenze", FieldValue.arrayRemove(dataOggiIso))
            }
        }

        batch.commit().addOnSuccessListener {
            Toast.makeText(requireContext(), "Appello parziale salvato!", Toast.LENGTH_SHORT).show()
            binding.btnSalvaAppello.isEnabled = true
            binding.btnSalvaAppello.text = "Salva Appello del Giorno"
        }.addOnFailureListener {
            Toast.makeText(requireContext(), "Errore di rete", Toast.LENGTH_SHORT).show()
            binding.btnSalvaAppello.isEnabled = true
            binding.btnSalvaAppello.text = "Ritenta"
        }
    }

    // LOGICA DI ESPORTAZIONE
    private fun mostraDialogSceltaMese() {
        val mesi = arrayOf("Gennaio", "Febbraio", "Marzo", "Aprile", "Maggio", "Giugno", "Luglio", "Agosto", "Settembre", "Ottobre", "Novembre", "Dicembre")

        AlertDialog.Builder(requireContext())
            .setTitle("Scegli il mese da esportare")
            .setItems(mesi) { _, quale ->
                esportaDatiMese(quale + 1, mesi[quale])
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun esportaDatiMese(numeroMese: Int, nomeMese: String) {
        val annoCorrente = Calendar.getInstance().get(Calendar.YEAR)

        // Usiamo Locale.US per evitare che i numeri vengano formattati in lingue locali
        val prefissoMese = String.Companion.format(Locale.US, "%04d-%02d-", annoCorrente, numeroMese)

        val giorniAttivi = mutableSetOf<Int>()

        for (atleta in listaAtlete) {
            val tutteLeDate = atleta.datePresenze + atleta.dateAssenze

            for (data in tutteLeDate) {
                if (data.startsWith(prefissoMese)) {
                    val giorno = data.substringAfterLast("-").toIntOrNull()
                    if (giorno != null) {
                        giorniAttivi.add(giorno)
                    }
                }
            }
        }

        if (giorniAttivi.isEmpty()) {
            Toast.makeText(requireContext(), "Nessun appello registrato a $nomeMese!", Toast.LENGTH_LONG).show()
            return
        }

        val giorniOrdinati = giorniAttivi.sorted()
        val csvBuilder = StringBuilder()

        csvBuilder.append("Ginnasta;")
        for (giorno in giorniOrdinati) {
            csvBuilder.append("$giorno $nomeMese;")
        }
        csvBuilder.append("Tot. Presenze;Tot. Assenze\n")

        for (atleta in listaAtlete) {
            csvBuilder.append("${atleta.nome};")

            var countPresenze = 0
            var countAssenze = 0

            for (giorno in giorniOrdinati) {
                val dataDaCercare = String.Companion.format(Locale.US, "%04d-%02d-%02d", annoCorrente, numeroMese, giorno)

                if (atleta.datePresenze.contains(dataDaCercare)) {
                    csvBuilder.append("X;")
                    countPresenze++
                } else if (atleta.dateAssenze.contains(dataDaCercare)) {
                    csvBuilder.append("A;")
                    countAssenze++
                } else {
                    csvBuilder.append("-;")
                }
            }

            csvBuilder.append("$countPresenze;$countAssenze\n")
        }

        try {
            val file = File(requireContext().cacheDir, "Report_Presenze_$nomeMese.csv")
            FileWriter(file).use { it.write(csvBuilder.toString()) }

            val uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.provider",
                file
            )

            // Apre il file con un Intent
            val intentApri = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "text/csv")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            // Avviamo l'intento. Se il telefono non ha app compatibili, non crasha grazie al blocco catch!
            startActivity(Intent.createChooser(intentApri, "Apri il report con:"))

        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Errore nella generazione dell'Excel", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun mostraDialogFiltroGruppo() {
        val gruppi = arrayOf("Mostra Tutte", "ago", "pre ago", "promo1", "promo2", "corsi grandi", "corsi piccoli")
        AlertDialog.Builder(requireContext()).setTitle("Quale gruppo fa lezione oggi?")
            .setItems(gruppi) { _, quale ->

                // Aggiorniamo la memoria di stato del filtro
                gruppoAttualeFiltro = gruppi[quale]
                binding.btnFiltraGruppo.text = "🔍 Filtra per Gruppo: $gruppoAttualeFiltro"

                listaAtlete = if (gruppoAttualeFiltro == "Mostra Tutte") {
                    listaAtleteCompleta.toMutableList()
                } else {
                    listaAtleteCompleta.filter { it.gruppo == gruppoAttualeFiltro }.toMutableList()
                }
                adapter.updateData(listaAtlete)
            }.setNegativeButton("Annulla", null).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}