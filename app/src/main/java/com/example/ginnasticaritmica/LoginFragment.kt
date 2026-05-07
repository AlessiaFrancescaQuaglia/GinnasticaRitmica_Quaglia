package com.example.ginnasticaritmica

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.ginnasticaritmica.databinding.FragmentLoginBinding

class LoginFragment : Fragment() {

    // Utilizziamo lo stile esatto delle dispense per dichiarare il binding
    private lateinit var binding: FragmentLoginBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate del layout usando il data binding come da slide
        binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Se l'utente clicca sul bottone Allenatrice, viaggia verso la Coach Dashboard
        binding.btnCoach.setOnClickListener {
            findNavController().navigate(R.id.action_login_to_coachDashboard)
        }

        // Se l'utente clicca sul bottone Atleta, viaggia verso la Athlete Dashboard
        binding.btnAthlete.setOnClickListener {
            findNavController().navigate(R.id.action_login_to_athleteDashboard)
        }
    }
}