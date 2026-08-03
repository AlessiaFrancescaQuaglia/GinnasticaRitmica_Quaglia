package com.example.ginnasticaritmica.ui.fragment

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.ginnasticaritmica.R
import com.example.ginnasticaritmica.databinding.FragmentLoginBinding
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.FirebaseAuthUIActivityResultContract
import com.firebase.ui.auth.data.model.FirebaseAuthUIAuthenticationResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

//GATEWAY DI SICUREZZA: Smista l'accesso in base alla gestione dei Ruoli
class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    //FIREBASE UI LAUNCHER: Gestisce il flusso asincrono del login con Google e gestisce i risultati
    private val signInLauncher = registerForActivityResult(
        FirebaseAuthUIActivityResultContract()
    ) { res ->
        this.onSignInResult(res)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        //TASTO: ACCEDI COME ALLENATRICE
        binding.btnAccediCoach.setOnClickListener {
            // Rimuove gli spazi vuoti accidentali inseriti dall'utente
            val email = binding.emailInput.text.toString().trim()
            val password = binding.passwordInput.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(requireContext(), "Inserisci email e password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener {
                    val userId = auth.currentUser?.uid ?: ""

                    //Controllo di Sicurezza lato Client: Verifichiamo se l'utente autenticato ha davvero il permesso di entrare qui
                    db.collection("Utenti").document(userId).get()
                        .addOnSuccessListener { doc ->
                            val ruolo = doc.getString("ruolo") ?: "Allenatrice"
                            if (ruolo == "Allenatrice") {
                                findNavController().navigate(R.id.action_loginFragment_to_coachDashboardFragment)
                            } else {
                                // SICUREZZA DIFENSIVA: Se ha sbagliato tasto, revochiamo il token e lo disconnettiamo
                                auth.signOut()
                                Toast.makeText(requireContext(), "Errore: Questo account è registrato come Atleta!", Toast.LENGTH_LONG).show()
                            }
                        }
                }
                .addOnFailureListener {
                    Toast.makeText(requireContext(), "Email o Password errate", Toast.LENGTH_SHORT).show()
                }
        }

        // TASTO: ACCEDI COME ATLETA
        binding.btnAccediAtleta.setOnClickListener {
            val email = binding.emailInput.text.toString().trim()
            val password = binding.passwordInput.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(requireContext(), "Inserisci email e password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener {
                    val userId = auth.currentUser?.uid ?: ""

                    //Verifichiamo se è davvero un'atleta
                    db.collection("Utenti").document(userId).get()
                        .addOnSuccessListener { doc ->
                            val ruolo = doc.getString("ruolo") ?: "Atleta"
                            if (ruolo == "Atleta") {
                                findNavController().navigate(R.id.action_loginFragment_to_athleteDashboardFragment)
                            } else {
                                auth.signOut() // Lo buttiamo fuori se ha sbagliato tasto!
                                Toast.makeText(requireContext(), "Errore: Questo account è registrato come Allenatrice!", Toast.LENGTH_LONG).show()
                            }
                        }
                }
                .addOnFailureListener {
                    Toast.makeText(requireContext(), "Email o Password errate", Toast.LENGTH_SHORT).show()
                }
        }

        // TASTO: REGISTRATI COME ALLENATRICE
        binding.btnRegistrati.setOnClickListener {
            val email = binding.emailInput.text.toString().trim()
            val password = binding.passwordInput.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(requireContext(), "Inserisci email e password per registrarti", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener {
                    val userId = auth.currentUser?.uid ?: ""

                    // Inseriamo il ruolo nel database di Firestore associandolo all'UID generato da Firebase Authentication
                    val utente = hashMapOf(
                        "email" to email,
                        "ruolo" to "Allenatrice"
                    )

                    db.collection("Utenti").document(userId).set(utente)
                        .addOnSuccessListener {
                            Toast.makeText(requireContext(), "Registrazione completata!", Toast.LENGTH_SHORT).show()
                            findNavController().navigate(R.id.action_loginFragment_to_coachDashboardFragment)
                        }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(requireContext(), "Errore registrazione: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }

        //TASTO: REGISTRATI COME ATLETA
        binding.btnRegistratiAtleta.setOnClickListener {
            val email = binding.emailInput.text.toString().trim()
            val password = binding.passwordInput.text.toString().trim()

            // Controllo campi vuoti
            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(requireContext(), "Inserisci Email e Password per registrarti", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Creazione account su Firebase Auth
            FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val user = task.result?.user
                        if (user != null) {

                            //Salviamo il ruolo "Atleta" nel Database
                            val mappaUtente = hashMapOf(
                                "email" to email,
                                "ruolo" to "Atleta"
                            )

                            FirebaseFirestore.getInstance().collection("Utenti").document(user.uid)
                                .set(mappaUtente)
                                .addOnSuccessListener {
                                    Toast.makeText(requireContext(), "Registrazione Atleta completata!", Toast.LENGTH_SHORT).show()
                                    // Manda l'atleta direttamente alla sua Dashboard
                                    findNavController().navigate(R.id.action_loginFragment_to_athleteDashboardFragment)
                                }
                        }
                    } else {
                        Toast.makeText(requireContext(), "Errore registrazione: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                    }
                }
        }

        //TASTO: ACCEDI CON GOOGLE
        binding.btnGoogle.setOnClickListener {
            val providers = arrayListOf(AuthUI.IdpConfig.GoogleBuilder().build())
            val signInIntent = AuthUI.getInstance().createSignInIntentBuilder().setAvailableProviders(providers).setIsSmartLockEnabled(false).build()
            signInLauncher.launch(signInIntent)
        }
    }

    // Risultato dell'accesso con Google
    private fun onSignInResult(result: FirebaseAuthUIAuthenticationResult) {
        if (result.resultCode == Activity.RESULT_OK) {
            val user = FirebaseAuth.getInstance().currentUser
            if (user != null) {

                //Controlla se l'utente esiste già
                db.collection("Utenti").document(user.uid).get()
                    .addOnSuccessListener { document ->
                        if (!document.exists()) {
                            // First Login: lo salviamo come Allenatrice di default (Registrazione implicita)
                            val utente = hashMapOf(
                                "email" to user.email,
                                "ruolo" to "Allenatrice",
                                "nome" to user.displayName
                            )
                            db.collection("Utenti").document(user.uid).set(utente)
                            findNavController().navigate(R.id.action_loginFragment_to_coachDashboardFragment)
                        } else {
                            // Login Successivo: l'app legge il ruolo e fa "routing" dinamico
                            val ruolo = document.getString("ruolo") ?: "Allenatrice"
                            if (ruolo == "Allenatrice") {
                                findNavController().navigate(R.id.action_loginFragment_to_coachDashboardFragment)
                            } else {
                                findNavController().navigate(R.id.action_loginFragment_to_athleteDashboardFragment)
                            }
                        }
                    }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        //MEMORY LEAK PREVENTION: Sgancia il binding grafico alla distruzione della View
        _binding = null
    }
}