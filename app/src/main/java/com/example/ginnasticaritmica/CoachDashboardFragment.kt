package com.example.ginnasticaritmica

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

class CoachDashboardFragment : Fragment() {

    private lateinit var binding: FragmentCoachDashboardBinding
    private val viewModel: CoachViewModel by activityViewModels()

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

        // 1. Apre il menu laterale quando clicchi l'hamburger
        binding.btnHamburger.setOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }

        // 2. Gestisce i click sulle voci del menu laterale
        binding.navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_gare -> {
                    Toast.makeText(requireContext(), "Cliccato su Gare", Toast.LENGTH_SHORT).show()
                }
                R.id.nav_atlete -> {
                    Toast.makeText(requireContext(), "Cliccato su Gestione Atlete", Toast.LENGTH_SHORT).show()
                }
                R.id.nav_logout -> {
                    // Torna alla schermata di Login
                    findNavController().navigateUp()
                }
            }
            // Chiude il menu dopo aver cliccato
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            true
        }
    }
}