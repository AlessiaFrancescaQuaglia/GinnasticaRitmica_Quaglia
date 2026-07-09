package com.example.ginnasticaritmica

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.ginnasticaritmica.databinding.FragmentAthleteDashboardBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class AthleteDashboardFragment : Fragment() {

    private lateinit var binding: FragmentAthleteDashboardBinding
    private val db = FirebaseFirestore.getInstance()
    private var atletaListener: ListenerRegistration? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentAthleteDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Recuperiamo l'email dell'atleta attualmente loggata
        val emailAtletaLoggata = FirebaseAuth.getInstance().currentUser?.email

        if (emailAtletaLoggata != null) {
            // 2. Cerchiamo nella collezione "Atlete" il documento che ha questa email!
            atletaListener = db.collection("Atlete")
                .whereEqualTo("email", emailAtletaLoggata.lowercase())
                .addSnapshotListener { snapshot, error ->

                    if (error != null) {
                        return@addSnapshotListener
                    }

                    if (snapshot != null && !snapshot.isEmpty) {
                        val documento = snapshot.documents[0]
                        val tracce = documento.get("tracceMusicali") as? Map<String, String>

                        if (!tracce.isNullOrEmpty()) {
                            binding.rvMusiche.visibility = View.VISIBLE
                            binding.rvMusiche.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
                            binding.rvMusiche.adapter = MusicaAdapter(tracce, requireContext())
                        } else {
                            binding.rvMusiche.visibility = View.GONE
                        }
                    }
                }
        }
    }

    // PULIZIA MEMORY LEAK (Regola d'oro per il massimo dei punti)
    override fun onDestroyView() {
        super.onDestroyView()
        atletaListener?.remove()
    }
}