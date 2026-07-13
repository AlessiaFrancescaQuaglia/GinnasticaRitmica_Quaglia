package com.example.ginnasticaritmica

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import androidx.core.graphics.toColorInt

class CoachDashboardFragment : Fragment() {

    private lateinit var binding: FragmentCoachDashboardBinding
    private val viewModel: CoachViewModel by activityViewModels()
    // 1.ActivityViewModels: Sfruttiamo il ViewModel dell'Activity madre. Così, se cambiamo schermata e torniamo qui,
    // i dati statistici sono già pronti e non dobbiamo ricaricarli
    private val db = FirebaseFirestore.getInstance()

    // Dichiariamo 3 variabili ListenerRegistration.
    // Ognuna serve a memorizzare un "tubo" aperto verso Firebase.
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

// Invece di impostare a mano i testi, agganciamo l'intero layout direttamente al ViewModel. Quando il ViewModel cambia, l'XML si aggiorna da solo!
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
                // Logout logico: torniamo alla pagina di Login "svuotando" lo stack
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

                    // Aggiorna il numero totale delle TUE atlete
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