package com.example.ginnasticaritmica

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.ginnasticaritmica.databinding.FragmentCoachDashboardBinding
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
    private val db = FirebaseFirestore.getInstance()

    // MAGIA DA 30 E LODE: Tre interruttori per spegnere i tre ascolti in background!
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Recupero nome dell'allenatrice per il benvenuto
        val uidAllenatrice = FirebaseAuth.getInstance().currentUser?.uid
        if (uidAllenatrice != null) {
            // Agganciamo l'interruttore 1
            allenatriceListener = db.collection("Allenatrici").document(uidAllenatrice).addSnapshotListener { documento, error ->
                if (error != null) {
                    return@addSnapshotListener
                }

                if (documento != null && documento.exists()) {
                    val nomeDatabase = documento.getString("nome") ?: "Allenatrice"
                    binding.tvBenvenuto.text = "Benvenuta, $nomeDatabase!"
                }
            }
        }

        // Collegamento variabili al file grafico
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
                    binding.drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START)
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
                R.id.nav_logout -> {
                    findNavController().navigateUp()
                }
            }
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            true
        }

        // Avviamo l'unica funzione che carica tutti i dati della Dashboard!
        caricaRiepilogoDashboard()
    }

    /**
     * Funzione unica e centralizzata per gestire il riepilogo della Dashboard.
     * Si occupa di caricare il contatore delle Gare, le Atlete e le Scadenze.
     */
    private fun caricaRiepilogoDashboard() {

        // --- 1. CARICAMENTO GARE IN TEMPO REALE ---
        // Agganciamo l'interruttore 2
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

        // --- 2. CARICAMENTO ATLETE E CALCOLO SCADENZE CERTIFICATI (IN TEMPO REALE) ---
        val uidAllenatrice = FirebaseAuth.getInstance().currentUser?.uid

        if (uidAllenatrice != null) {
            // Agganciamo l'interruttore 3
            atleteListener = db.collection("Atlete")
                .whereEqualTo("idAllenatrice", uidAllenatrice) // IL FILTRO MAGICO! Prende solo le TUE atlete
                .addSnapshotListener { snapshot, error ->

                    if (error != null || snapshot == null) {
                        binding.tvContatoreAtlete.text = "?"
                        binding.tvAvvisiScadenze.text = "Impossibile caricare le scadenze al momento."
                        return@addSnapshotListener
                    }

                    // Aggiorna il numero totale delle TUE atlete
                    binding.tvContatoreAtlete.text = snapshot.size().toString()

                    val atleteCritiche = mutableListOf<String>()
                    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    val dataOggi = Date()

                    // Ciclo per controllare ogni singola atleta trovata
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

                                    if (diffGiorni < 0) {
                                        atleteCritiche.add("🔴 $nome (SCADUTO il $scadenza)")
                                    } else if (diffGiorni in 0..30) {
                                        atleteCritiche.add("🟡 $nome (Scade tra $diffGiorni giorni)")
                                    }
                                }
                            } catch (e: Exception) {
                                // Formato data errato, proseguiamo col prossimo documento
                            }
                        } else {
                            atleteCritiche.add("🔴 $nome (Certificato Mancante)")
                        }
                    }

                    // Stampa visiva degli avvisi sulle scadenze
                    if (atleteCritiche.isEmpty()) {
                        binding.tvAvvisiScadenze.text = "✅ Tutti i certificati sono in regola!"
                        binding.tvAvvisiScadenze.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
                    } else {
                        binding.tvAvvisiScadenze.text = atleteCritiche.joinToString("\n\n")
                        binding.tvAvvisiScadenze.setTextColor(android.graphics.Color.parseColor("#CF6679"))
                    }
                }
        }
    }

    // --- PULIZIA FINALE ---
    override fun onDestroyView() {
        super.onDestroyView()
        // Spegniamo tutti i "rubinetti" aperti verso Firebase per non consumare batteria!
        allenatriceListener?.remove()
        gareListener?.remove()
        atleteListener?.remove()
    }
}