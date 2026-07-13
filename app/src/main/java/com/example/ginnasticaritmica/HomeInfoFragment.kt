package com.example.ginnasticaritmica

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.ginnasticaritmica.databinding.FragmentHomeInfoBinding

//Una schermata semplice che funge da punto di ingresso.
class HomeInfoFragment : Fragment() {

    //'binding' assicura l'accesso non nullo.
    private var _binding: FragmentHomeInfoBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeInfoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        //Sfrutta il Navigation Component di Jetpack per muoversi.
        binding.btnAccedi.setOnClickListener {
            findNavController().navigate(R.id.action_homeInfoFragment_to_loginFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        //Annullo il binding. In Android, la "View" del Fragment viene distrutta prima del Fragment stesso; non annullarlo intaserebbe la RAM.
        _binding = null
    }
}