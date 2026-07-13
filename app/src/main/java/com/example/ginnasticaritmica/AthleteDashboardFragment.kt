package com.example.ginnasticaritmica

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.ginnasticaritmica.databinding.FragmentAthleteDashboardBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class AthleteDashboardFragment : Fragment() {

    private lateinit var binding: FragmentAthleteDashboardBinding
    private val db = FirebaseFirestore.getInstance()

    private var atletaListener: ListenerRegistration? = null
    // Variabile per gestire l'ascolto dei dati

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
            // 2. Cerchiamo nella collezione "Atlete" il documento che ha questa email. addSnapshotListener mi permette di non dover ricaricare la pagina.
            //Se l'allenatrice cambia la musica sul DB, la ginnasta vedrà l'aggiornamento istantaneamente sul telefono
            atletaListener = db.collection("Atlete")
                .whereEqualTo("email", emailAtletaLoggata.lowercase())
                .addSnapshotListener { snapshot, error ->

                    if (error != null) {
                        return@addSnapshotListener
                    }

                    if (snapshot != null && !snapshot.isEmpty) {
                        // Prendo il primo documento trovato
                        val documento = snapshot.documents[0]
                        // Estraggo la mappa (Attrezzo -> Link).
                        val tracce = documento.get("tracceMusicali") as? Map<String, String>

                        // 3. Gestione dinamica dell'interfaccia. Se la mappa contiene dati, mostro la RecyclerView.
                        // Se è vuota, nascondo la lista per non mostrare spazi vuoti nell'app.
                        if (!tracce.isNullOrEmpty()) {
                            binding.rvMusiche.visibility = View.VISIBLE
                            binding.rvMusiche.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
                            // Passo la mappa al MusicaAdapter che disegnerà le righe
                            binding.rvMusiche.adapter = MusicaAdapter(tracce, requireContext())
                        } else {
                            binding.rvMusiche.visibility = View.GONE
                        }
                    }
                }
        }
    }

    // Quando l'utente cambia schermata, il fragment viene distrutto.
    // Se non rimuovo il listener, l'app continuerebbe ad ascoltare il database inutilmente, sprecando batteria e causando potenziali errori di "null pointer" quando il fragment non esiste più.
    override fun onDestroyView() {
        super.onDestroyView()
        atletaListener?.remove()
    }
}