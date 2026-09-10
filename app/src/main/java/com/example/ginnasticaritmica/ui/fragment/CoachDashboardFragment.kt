package com.example.ginnasticaritmica.ui.fragment

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ContextThemeWrapper
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.activity.OnBackPressedCallback
import androidx.navigation.fragment.findNavController
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.ginnasticaritmica.viewmodel.CoachViewModel
import com.example.ginnasticaritmica.R
import com.example.ginnasticaritmica.worker.ScadenzaWorker
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
        // BLOCCO TASTO INDIETRO: Impedisce di chiudere l'app o tornare al login
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Mostra un piccolo avviso testuale invece di chiudere l'app
                Toast.makeText(requireContext(), "Usa il menu in alto a sinistra per disconnetterti", Toast.LENGTH_SHORT).show()
            }
        })

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
        val controlloScadenzeRequest = PeriodicWorkRequestBuilder<ScadenzaWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(requireContext()).enqueueUniquePeriodicWork(
            "ControlloScadenzeGiornaliero",
            ExistingPeriodicWorkPolicy.KEEP,
            controlloScadenzeRequest
        )

        binding.viewModel = viewModel
        binding.lifecycleOwner = viewLifecycleOwner

        binding.topAppBar.setNavigationOnClickListener { viewMenu ->

            // Creazione standard: prenderà automaticamente il design arrotondato dal themes.xml
            val contestoConTema = android.view.ContextThemeWrapper(requireContext(), R.style.StileMenuScuro)
            val popup = PopupMenu(contestoConTema, viewMenu)
            popup.menuInflater.inflate(R.menu.coach_drawer_menu, popup.menu)

            // Forza la visibilità delle icone
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                popup.setForceShowIcon(true)
            }

            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.profiloAllenatriceFragment -> {
                        findNavController().navigate(R.id.profiloAllenatriceFragment)
                        true
                    }
                    R.id.nav_logout -> {
                        WorkManager.getInstance(requireContext()).cancelUniqueWork("ControlloScadenzeGiornaliero")
                        WorkManager.getInstance(requireContext()).cancelUniqueWork("ControlloScadenzeImmediato")

                        com.firebase.ui.auth.AuthUI.getInstance()
                            .signOut(requireContext())
                            .addOnCompleteListener {
                                findNavController().navigateUp()
                            }
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }

        caricaRiepilogoDashboard()
    }

    @SuppressLint("SetTextI18n")
    private fun caricaRiepilogoDashboard() {
        // CARICAMENTO GARE IN TEMPO REALE
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

        // CARICAMENTO ATLETE E CALCOLO SCADENZE CERTIFICATI
        val uidAllenatrice = FirebaseAuth.getInstance().currentUser?.uid

        if (uidAllenatrice != null) {
            atleteListener = db.collection("Atlete")
                .whereEqualTo("idAllenatrice", uidAllenatrice)
                .addSnapshotListener { snapshot, error ->

                    if (error != null || snapshot == null) {
                        binding.tvContatoreAtlete.text = "?"
                        binding.tvAvvisiScadenze.text = "Impossibile caricare le scadenze al momento."
                        return@addSnapshotListener
                    }

                    binding.tvContatoreAtlete.text = snapshot.size().toString()

                    val atleteCritiche = mutableListOf<String>()
                    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    val dataOggi = Date()

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
                            } catch (_: Exception) {}
                        } else {
                            atleteCritiche.add("🔴 $nome (Certificato Mancante)")
                        }
                    }

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

    override fun onDestroyView() {
        super.onDestroyView()
        allenatriceListener?.remove()
        gareListener?.remove()
        atleteListener?.remove()
    }
}