package com.example.ginnasticaritmica.ui.fragment

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.toColorInt
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.ginnasticaritmica.viewmodel.CoachViewModel
import com.example.ginnasticaritmica.R
import com.example.ginnasticaritmica.worker.ScadenzaWorker
import com.example.ginnasticaritmica.databinding.FragmentCoachDashboardBinding
import com.firebase.ui.auth.AuthUI
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class CoachDashboardFragment : Fragment() {

    private lateinit var binding: FragmentCoachDashboardBinding
    private val viewModel: CoachViewModel by activityViewModels()
    // ActivityViewModels: Sfrutta il ViewModel dell'Activity madre. Così, se si cambia schermata e si ritorna a quella precedente
    // i dati statistici sono già pronti e non sono da ricaricare
    private val db = FirebaseFirestore.getInstance()

    // Dichiaro 3 variabili ListenerRegistration.
    // Ognuna serve a memorizzare l'ascolto del DB.
    private var allenatriceListener: ListenerRegistration? = null
    private var gareListener: ListenerRegistration? = null
    private var atleteListener: ListenerRegistration? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentCoachDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Recupero nome dell'allenatrice per il benvenuto
        val uidAllenatrice = FirebaseAuth.getInstance().currentUser?.uid
        if (uidAllenatrice != null) {

            allenatriceListener = db.collection("Allenatrici").document(uidAllenatrice).addSnapshotListener { documento, error ->
                if (error != null) return@addSnapshotListener
                if (documento != null && documento.exists()) {
                    val nomeDatabase = documento.getString("nome") ?: "Allenatrice"
                    binding.tvBenvenuto.text = "Benvenuta, $nomeDatabase!"
                }
            }
        }

        // ATTIVAZIONE NOTIFICHE SOLO PER ALLENATRICE
        // Questo timer si attiva SOLO quando l'allenatrice entra nel suo pannello
        val controlloScadenzeRequest = PeriodicWorkRequestBuilder<ScadenzaWorker>(1, TimeUnit.DAYS).build()

        WorkManager.getInstance(requireContext()).enqueueUniquePeriodicWork(
            "ControlloScadenzeGiornaliero",
            ExistingPeriodicWorkPolicy.KEEP,
            controlloScadenzeRequest
        )

        // Test immediato diu debug
        val testImmediato = OneTimeWorkRequestBuilder<ScadenzaWorker>().build()
        WorkManager.getInstance(requireContext()).enqueueUniqueWork(
        "TestImmediato",
        ExistingWorkPolicy.REPLACE,
        testImmediato
        )

        // Invece di impostare a mano i testi,l'intero layout viene agganciato direttamente al ViewModel. Quando il ViewModel cambia, l'XML si aggiorna da solo
        binding.viewModel = viewModel
        binding.lifecycleOwner = viewLifecycleOwner

        // Gestione apertura menù laterale
        binding.btnHamburger.setOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }

        // Gestione dei click sui tasti del menù laterale
        binding.navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_home_dashboard -> {
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                }
                R.id.profiloAllenatriceFragment -> {
                    findNavController().navigate(R.id.profiloAllenatriceFragment)
                }
                R.id.listaGareFragment -> {
                    findNavController().navigate(R.id.listaGareFragment)
                }
                R.id.nav_atlete -> {
                    findNavController().navigate(R.id.action_coachDashboard_to_gestioneAtlete)
                }
                R.id.presenzeFragment -> {
                    findNavController().navigate(R.id.presenzeFragment)
                }
                // Logout logico: torna alla pagina di Login "svuotando" lo stack
                R.id.nav_logout -> {
                    //spengo i timer in background
                    WorkManager.getInstance(requireContext()).cancelUniqueWork("ControlloScadenzeGiornaliero")
                    WorkManager.getInstance(requireContext()).cancelUniqueWork("ControlloScadenzeImmediato")
                    //scollega fisicamente l'account Google e Firebase
                    AuthUI.getInstance()
                        .signOut(requireContext())
                        .addOnCompleteListener {
                            //quando ha finito di scollegarsi, ritornas alla pagina di Login
                            findNavController().navigateUp()
                        }
                }
            }
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            true
        }

        // Avvio della funzione che carica tutti i dati della Dashboard
        caricaRiepilogoDashboard()
    }


    @SuppressLint("SetTextI18n")
    private fun caricaRiepilogoDashboard() {

        //CARICAMENTO GARE IN TEMPO REALE
        gareListener = db.collection("Gare").addSnapshotListener { snapshot, error ->
            if (error != null) {
                binding.tvNumeroGareDashboard.text = "?"
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val numeroGare = snapshot.size()
                binding.tvNumeroGareDashboard.text = "$numeroGare"
            }
        }

        //CARICAMENTO ATLETE E CALCOLO SCADENZE CERTIFICATI (IN TEMPO REALE)
        val uidAllenatrice = FirebaseAuth.getInstance().currentUser?.uid

        if (uidAllenatrice != null) {
            atleteListener = db.collection("Atlete")
            //la query NON scarica tutte le atlete della palestra, ma solo quelle che sono state assegnate all'allenatrice corrente.
                .whereEqualTo("idAllenatrice", uidAllenatrice)
                .addSnapshotListener { snapshot, error ->

                    if (error != null || snapshot == null) {
                        binding.tvContatoreAtlete.text = "?"
                        binding.tvAvvisiScadenze.text = "Impossibile caricare le scadenze al momento."
                        return@addSnapshotListener
                    }

                    // Aggiorna il numero totale delle atlete dell'allenatice loggata
                    binding.tvContatoreAtlete.text = snapshot.size().toString()

                    val atleteCritiche = mutableListOf<String>()
                    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    val dataOggi = Date()

                    // Ciclo per controllare ogni singola atleta trovata e valutare le scadenze dei certificati
                    for (documento in snapshot.documents) {
                        val nome = documento.getString("nome") ?: "Sconosciuta"
                        val scadenza = documento.getString("scadenzaCertificato") ?: ""

                        if (scadenza.isNotEmpty() && scadenza != "Nessuna scadenza inserita") {
                            try {
                                val dataScad = sdf.parse(scadenza)
                                if (dataScad != null) {
                                    val diffGiorni = TimeUnit.DAYS.convert(
                                        dataScad.time - dataOggi.time,
                                        TimeUnit.MILLISECONDS
                                    )

                                    // Classificazione degli stati critici
                                    if (diffGiorni < 0) {
                                        atleteCritiche.add("🔴 $nome (SCADUTO il $scadenza)")
                                    } else if (diffGiorni in 0..30) {
                                        atleteCritiche.add("🟡 $nome (Scade tra $diffGiorni giorni)")
                                    }
                                }
                            } catch (_: Exception) {
                            }
                        } else {
                            atleteCritiche.add("🔴 $nome (Certificato Mancante)")
                        }
                    }

                    // Aggiornamento dinamico della UI con feedback a colori
                    if (atleteCritiche.isEmpty()) {
                        binding.tvAvvisiScadenze.text = "✅ Tutti i certificati sono in regola!"
                        binding.tvAvvisiScadenze.setTextColor("#4CAF50".toColorInt())
                    } else {
                        binding.tvAvvisiScadenze.text = atleteCritiche.joinToString("\n\n")
                        binding.tvAvvisiScadenze.setTextColor("#CF6679".toColorInt())
                    }
                }
        }
    }

    //PREVENZIONE DEI MEMORY LEAK
    override fun onDestroyView() {
        super.onDestroyView()
        // Quando il fragment muore, i 3 listener asincroni vanno spenti manualmente.
        // Altrimenti, proverebbero ad aggiornare grafiche che non esistono più, causando un crash dell'app
        allenatriceListener?.remove()
        gareListener?.remove()
        atleteListener?.remove()
    }
}