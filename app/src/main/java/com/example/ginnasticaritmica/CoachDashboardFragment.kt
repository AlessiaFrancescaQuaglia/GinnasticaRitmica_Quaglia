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
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class CoachDashboardFragment : Fragment() {

    private lateinit var binding: FragmentCoachDashboardBinding
    private val viewModel: CoachViewModel by activityViewModels()

    private val db = FirebaseFirestore.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentCoachDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.viewModel = viewModel
        binding.lifecycleOwner = viewLifecycleOwner

        binding.btnHamburger.setOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }

        binding.navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_gare -> {
                    Toast.makeText(requireContext(), "Cliccato su Gare", Toast.LENGTH_SHORT).show()
                }
                R.id.nav_atlete -> {
                    findNavController().navigate(R.id.action_coachDashboard_to_gestioneAtlete)
                }
                R.id.nav_logout -> {
                    findNavController().navigateUp()
                }
            }
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            true
        }

        // Avviamo il controllo di Database e Scadenze
        caricaRiepilogoDashboard()
    }

    private fun caricaRiepilogoDashboard() {
        // Scarichiamo le atlete dal database
        db.collection("Atlete").get().addOnSuccessListener { snapshot ->

            // 1. Aggiorniamo il GRANDE NUMERO VIOLA che hai creato tu
            binding.tvContatoreAtlete.text = snapshot.size().toString()

            // 2. Analizziamo le date dei certificati
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
                            val diffGiorni = TimeUnit.DAYS.convert(dataScad.time - dataOggi.time, TimeUnit.MILLISECONDS)

                            if (diffGiorni < 0) {
                                atleteCritiche.add("🔴 $nome (SCADUTO il $scadenza)")
                            } else if (diffGiorni in 0..30) {
                                atleteCritiche.add("🟡 $nome (Scade tra $diffGiorni giorni)")
                            }
                        }
                    } catch (e: Exception) {
                        // Ignoriamo la data in caso di formato errato
                    }
                } else {
                    atleteCritiche.add("🔴 $nome (Certificato Mancante)")
                }
            }

            // 3. Mostriamo i risultati nel nuovo Box Scadenze
            if (atleteCritiche.isEmpty()) {
                binding.tvAvvisiScadenze.text = "✅ Tutti i certificati sono in regola!"
                binding.tvAvvisiScadenze.setTextColor(Color.parseColor("#4CAF50")) // Verde scuro
            } else {
                binding.tvAvvisiScadenze.text = atleteCritiche.joinToString("\n\n")
                binding.tvAvvisiScadenze.setTextColor(Color.parseColor("#CF6679")) // Rosso di allerta
            }
        }.addOnFailureListener {
            binding.tvContatoreAtlete.text = "?"
            binding.tvAvvisiScadenze.text = "Impossibile caricare le scadenze al momento."
        }
    }
}