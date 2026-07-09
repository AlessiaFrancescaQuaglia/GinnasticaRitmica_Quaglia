package com.example.ginnasticaritmica

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.ginnasticaritmica.databinding.FragmentLoginBinding
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.FirebaseAuthUIActivityResultContract
import com.firebase.ui.auth.data.model.FirebaseAuthUIAuthenticationResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    // Il ricevitore per l'accesso con Google
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

        // 1. TASTO: ACCEDI COME ALLENATRICE (Manuale con controllo Ruolo)
        binding.btnAccediCoach.setOnClickListener {
            val email = binding.emailInput.text.toString().trim()
            val password = binding.passwordInput.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(requireContext(), "Inserisci email e password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener {
                    val userId = auth.currentUser?.uid ?: ""

                    // CONTROLLO DI SICUREZZA: Verifichiamo se è davvero un'allenatrice
                    db.collection("Utenti").document(userId).get()
                        .addOnSuccessListener { doc ->
                            val ruolo = doc.getString("ruolo") ?: "Allenatrice"
                            if (ruolo == "Allenatrice") {
                                findNavController().navigate(R.id.action_loginFragment_to_coachDashboardFragment)
                            } else {
                                auth.signOut() // Lo buttiamo fuori se ha sbagliato tasto!
                                Toast.makeText(requireContext(), "Errore: Questo account è registrato come Atleta!", Toast.LENGTH_LONG).show()
                            }
                        }
                }
                .addOnFailureListener {
                    Toast.makeText(requireContext(), "Email o Password errate", Toast.LENGTH_SHORT).show()
                }
        }

        // 2. TASTO: ACCEDI COME ATLETA (Manuale con controllo Ruolo)
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

                    // CONTROLLO DI SICUREZZA: Verifichiamo se è davvero un'atleta
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

        // 3. TASTO: REGISTRATI (Crea l'account sul cloud e lo imposta come Allenatrice)
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

        // 4. TASTO: ACCEDI CON GOOGLE (Rileva il ruolo in automatico dal database)
        binding.btnGoogle.setOnClickListener {
            val providers = arrayListOf(AuthUI.IdpConfig.GoogleBuilder().build())
            val signInIntent = AuthUI.getInstance().createSignInIntentBuilder().setAvailableProviders(providers).build()
            signInLauncher.launch(signInIntent)
        }
    }

    // Risultato dell'accesso con Google (Mostrato a lezione dal prof!)
    private fun onSignInResult(result: FirebaseAuthUIAuthenticationResult) {
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val user = FirebaseAuth.getInstance().currentUser
            if (user != null) {
                db.collection("Utenti").document(user.uid).get()
                    .addOnSuccessListener { document ->
                        if (!document.exists()) {
                            // Nuovo utente Google: lo salviamo come Allenatrice di default
                            val utente = hashMapOf(
                                "email" to user.email,
                                "ruolo" to "Allenatrice",
                                "nome" to user.displayName
                            )
                            db.collection("Utenti").document(user.uid).set(utente)
                            findNavController().navigate(R.id.action_loginFragment_to_coachDashboardFragment)
                        } else {
                            // Utente Google già esistente: l'app legge il ruolo e lo manda nel posto giusto!
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
        _binding = null
    }
}