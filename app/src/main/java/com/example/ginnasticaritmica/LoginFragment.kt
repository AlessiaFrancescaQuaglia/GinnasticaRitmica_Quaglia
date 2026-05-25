package com.example.ginnasticaritmica

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.ginnasticaritmica.databinding.FragmentLoginBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class LoginFragment : Fragment() {

    private lateinit var binding: FragmentLoginBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // 1. Bottone ACCEDI (Logica Allenatrice)
        binding.btnAccedi.setOnClickListener {
            val email = binding.emailInput.text.toString().trim()
            val password = binding.passwordInput.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(requireContext(), "Inserisci email e password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Tentativo di Login (supponiamo che chi usa questo bottone sia l'allenatrice)
            auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener {
                    findNavController().navigate(R.id.action_loginFragment_to_coachDashboardFragment)
                }
                .addOnFailureListener {
                    Toast.makeText(requireContext(), "Errore login Allenatrice", Toast.LENGTH_SHORT).show()
                }
        }

        // 2. Bottone "ENTRA COME ATLETA"
        binding.btnAthlete.setOnClickListener {
            // Qui facciamo navigare direttamente alla dashboard atleta
            findNavController().navigate(R.id.action_loginFragment_to_athleteDashboardFragment)
        }
    }

    // Funzione comodissima per non ripetere il codice
    private fun navigaInBaseAlRuolo(ruolo: String) {
        if (ruolo == "Allenatrice") {
            findNavController().navigate(R.id.action_loginFragment_to_coachDashboardFragment)
        } else {
            findNavController().navigate(R.id.action_loginFragment_to_athleteDashboardFragment)
        }
    }
}