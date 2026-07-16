package com.example.ginnasticaritmica

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
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

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Recupero l'email dell'atleta attualmente loggata
        val emailAtletaLoggata = FirebaseAuth.getInstance().currentUser?.email

        if (emailAtletaLoggata != null) {
            //cerca nella collezione "Atlete" il documento che ha questa email. addSnapshotListener permette di non dover ricaricare la pagina.
            //Se l'allenatrice cambia la musica sul DB, la ginnasta vedrà l'aggiornamento istantaneamente sul telefono
            atletaListener = db.collection("Atlete")
                .whereEqualTo("email", emailAtletaLoggata.lowercase())
                .addSnapshotListener { snapshot, error ->

                    if (error != null) {
                        return@addSnapshotListener
                    }

                    if (snapshot != null && !snapshot.isEmpty) {

                        //  Nasconde nil testo "Controllo della tua scheda in corso..." se è presente la scheda
                        binding.tvStatoMusica.visibility = View.GONE

                        // Prendo il primo documento trovato
                        val documento = snapshot.documents[0]
                        // Estraggo la mappa (Attrezzo e Link musicale)
                        val tracce = documento.get("tracceMusicali") as? Map<String, String>

                        //Gestione dinamica dell'interfaccia. Se la mappa contiene dati, mostra la RecyclerView.
                        // Se è vuota, nasconde la lista per non mostrare spazi vuoti nell'app.
                        if (!tracce.isNullOrEmpty()) {
                            binding.rvMusiche.visibility = View.VISIBLE
                            binding.rvMusiche.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
                            // Passa la mappa al MusicaAdapter che disegnerà le righe
                            binding.rvMusiche.adapter = MusicaAdapter(tracce, requireContext())
                        } else {
                            binding.rvMusiche.visibility = View.GONE
                            // Se la scheda c'è ma non ha ancora musiche assegnate
                            binding.tvStatoMusica.text = "Nessuna musica assegnata al momento."
                            binding.tvStatoMusica.visibility = View.VISIBLE
                        }
                    } else {
                        // SCHEDA NON TROVATA
                        // Cambia il testo per avvisare la ginnasta invece di lasciare tutto bloccato
                        binding.tvStatoMusica.text = "Scheda non trovata.\nL'allenatrice non ha ancora collegato la tua email ($emailAtletaLoggata)."
                        binding.tvStatoMusica.visibility = View.VISIBLE
                        binding.rvMusiche.visibility = View.GONE
                    }
                }
        }
        // GESTIONE LOGOUT
        binding.btnLogoutAtleta.setOnClickListener {
            //chiusura sessione su Firebase
            FirebaseAuth.getInstance().signOut()
            Toast.makeText(requireContext(), "Disconnessione effettuata", Toast.LENGTH_SHORT).show()

            // Navigazione verso la schermata iniziale
            findNavController().navigate(R.id.homeInfoFragment)
        }
    }

    // Quando l'utente cambia schermata, il fragment viene distrutto.
    // Se non rimuovo il listener, l'app continuerebbe ad ascoltare il database inutilmente, sprecando batteria e causando potenziali errori di "null pointer" quando il fragment non esiste più.
    override fun onDestroyView() {
        super.onDestroyView()
        atletaListener?.remove()
    }
}