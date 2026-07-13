package com.example.ginnasticaritmica

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.ginnasticaritmica.databinding.FragmentPresenzeBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class PresenzeFragment : Fragment() {

    private var _binding: FragmentPresenzeBinding? = null
    private val binding get() = _binding!!

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var adapter: AppelloAdapter

    // Lista attualmente in uso (filtrata o meno) e Lista "Backup" per ripristinare i filtri
    private var listaAtlete = mutableListOf<Atleta>()
    private val listaAtleteCompleta = mutableListOf<Atleta>()

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

        //SCARICAMENTO INIZIALE
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
        db.collection("Atlete")
            .whereEqualTo("idAllenatrice", idAllenatrice)
            .get()
            .addOnSuccessListener { documenti ->
                listaAtleteCompleta.clear()
                for (doc in documenti) {
                    val atleta = doc.toObject(Atleta::class.java)
                    listaAtleteCompleta.add(atleta)
                }
                // All'inizio mostro tutte le atlete
                listaAtlete = listaAtleteCompleta.toMutableList()
                adapter.updateData(listaAtlete)
            }
    }


    @SuppressLint("SetTextI18n")
    private fun salvaAppello() {
        // Preleviamo il buffer temporaneo compilato dall'allenatrice
        val stati = adapter.statiPresenza

        //Se viene segnata anche solo 1 presnza/assenza, salva, altrimenti blococ
        if (stati.isEmpty()) {
            Toast.makeText(requireContext(), "Segna almeno una presenza o assenza prima di salvare!", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnSalvaAppello.isEnabled = false
        binding.btnSalvaAppello.text = "Salvataggio..."

        // Iniziamo un "Batch": raggruppa le scritture per inviarle tutte insieme in una singola richiesta
        val batch = db.batch()
        val dataOggiIso = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        for ((idAtleta, isPresente) in stati) {
            val docRef = db.collection("Atlete").document(idAtleta)

            // ARRAY UNION e REMOVE ATOMICI: Garantisce la pulizia dei dati
            if (isPresente) {
                batch.update(docRef, "datePresenze", FieldValue.arrayUnion(dataOggiIso))
                batch.update(docRef, "dateAssenze", FieldValue.arrayRemove(dataOggiIso))
            } else {
                batch.update(docRef, "dateAssenze", FieldValue.arrayUnion(dataOggiIso))
                batch.update(docRef, "datePresenze", FieldValue.arrayRemove(dataOggiIso))
            }
        }

        // 'commit()' lancia l'operazione in blocco
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

        // Questo è il testo che cerchiamo all'inizio delle date (es: "2026-06-")
        val prefissoMese = String.format(Locale.getDefault(), "%04d-%02d-", annoCorrente, numeroMese)

        // 4. DATA MINING (Ricerca giorni attivi)
        // Usiamo un 'Set' invece di una List perché impedisce matematicamente di salvare duplicati
        val giorniAttivi = mutableSetOf<Int>()

        for (atleta in listaAtlete) {
            // Uniamo le due liste per controllare tutto
            val tutteLeDate = atleta.datePresenze + atleta.dateAssenze

            for (data in tutteLeDate) {
                if (data.startsWith(prefissoMese)) {
                    // Estraiamo il numero del giorno
                    val giorno = data.substringAfterLast("-").toIntOrNull()
                    if (giorno != null) {
                        giorniAttivi.add(giorno)
                    }
                }
            }
        }

        // Se non c'è stato nessun appello in quel mese, blocco
        if (giorniAttivi.isEmpty()) {
            Toast.makeText(requireContext(), "Nessun appello registrato a $nomeMese!", Toast.LENGTH_LONG).show()
            return
        }

        // Sort per garantire l'ordine cronologico nell'Excel
        val giorniOrdinati = giorniAttivi.sorted()

        //STRING BUILDER: Costruzione efficiente della stringa CSV (Comma Separated Values)
        val csvBuilder = StringBuilder()

        //CREAZIONE INTESTAZIONE (Solo per i giorni in cui c'è stata lezione)
        csvBuilder.append("Ginnasta;")
        for (giorno in giorniOrdinati) {
            csvBuilder.append("$giorno $nomeMese;")
        }
        csvBuilder.append("Tot. Presenze;Tot. Assenze\n")

        // cREAZIONE RIGHE
        for (atleta in listaAtlete) {
            csvBuilder.append("${atleta.nome};")

            var countPresenze = 0
            var countAssenze = 0

            // Analizziamo SOLO i giorni in cui c'è stato allenamento
            for (giorno in giorniOrdinati) {
                val dataDaCercare = String.format(Locale.getDefault(), "%04d-%02d-%02d", annoCorrente, numeroMese, giorno)

                if (atleta.datePresenze.contains(dataDaCercare)) {
                    csvBuilder.append("X;")
                    countPresenze++
                } else if (atleta.dateAssenze.contains(dataDaCercare)) {
                    csvBuilder.append("A;")
                    countAssenze++
                } else {
                    csvBuilder.append("-;") // Era assente giustificata o non iscritta quel giorno
                }
            }

            csvBuilder.append("$countPresenze;$countAssenze\n")
        }

        // FILE PROVIDER
        try {
            // Creo il file fisicamente nella cache segreta dell'app
            val file = java.io.File(requireContext().cacheDir, "Report_Presenze_$nomeMese.csv")
            java.io.FileWriter(file).use { it.write(csvBuilder.toString()) }

            // Chiedo il permesso tramite un 'Provider' per far leggere questo file a Whatsapp, Email o Excel
            val uri = androidx.core.content.FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.provider",
                file
            )

            // Lancio l'Intent
            val intentInvia = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_SUBJECT, "Report Excel Presenze - $nomeMese")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(Intent.createChooser(intentInvia, "Apri o invia Excel tramite:"))

        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Errore nella generazione dell'Excel", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    // 7. CLIENT-SIDE FILTERING (Filtro dinamico della lista)
    @SuppressLint("SetTextI18n")
    private fun mostraDialogFiltroGruppo() {
        val gruppi = arrayOf("Mostra Tutte", "ago", "pre ago", "promo1", "promo2", "corsi grandi", "corsi piccoli")
        AlertDialog.Builder(requireContext()).setTitle("Quale gruppo fa lezione oggi?")
            .setItems(gruppi) { _, quale ->
                val gruppoScelto = gruppi[quale]
                binding.btnFiltraGruppo.text = "🔍 Filtra per Gruppo: $gruppoScelto"

                // Uso la funzione '.filter' di Kotlin per scremare la lista master senza toccare il Database
                listaAtlete = if (gruppoScelto == "Mostra Tutte") {
                    listaAtleteCompleta.toMutableList()
                } else {
                    // Filtra la lista mostrando SOLO quelle del gruppo scelto!
                    listaAtleteCompleta.filter { it.gruppo == gruppoScelto }.toMutableList()
                }
                adapter.updateData(listaAtlete) // Mando la lista ristretta all'Adapter
            }.setNegativeButton("Annulla", null).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}