package com.example.ginnasticaritmica

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.ginnasticaritmica.databinding.FragmentGestioneAtleteBinding

class GestioneAtleteFragment : Fragment() {

    private lateinit var binding: FragmentGestioneAtleteBinding

    // Recuperiamo LO STESSO ViewModel della Dashboard
    private val viewModel: CoachViewModel by activityViewModels()
    private lateinit var adapter: AtletaAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentGestioneAtleteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Diciamo alla RecyclerView come disporsi (in verticale)
        binding.recyclerViewAtlete.layoutManager = LinearLayoutManager(requireContext())

        // 2. Inizializziamo l'Adapter con una lista vuota per cominciare
        adapter = AtletaAdapter(emptyList())
        binding.recyclerViewAtlete.adapter = adapter

        // 3. OSSERVIAMO il ViewModel: ogni volta che la lista cambia, l'Adapter si aggiorna da solo!
        viewModel.atleteList.observe(viewLifecycleOwner) { listaAggiornata ->
            adapter.updateData(listaAggiornata)
        }

        // 4. Cosa succede quando clicchi il bottone fluttuante "+"
        binding.fabAggiungiAtleta.setOnClickListener {
            // Per ora facciamo un test: aggiungiamo un'atleta finta
            val vecchiaLista = viewModel.atleteList.value ?: emptyList()
            val nuovaLista = vecchiaLista.toMutableList()
            nuovaLista.add(Atleta("Nuova Atleta Test", "Silver LA"))

            // Aggiorniamo la lista nel ViewModel (la RecyclerView si aggiornerà magicamente!)
            viewModel.atleteList.value = nuovaLista

            // BONUS: Aggiorniamo anche il numerino "7" della dashboard per riflettere il nuovo totale!
            viewModel.atleteIscritte.value = nuovaLista.size
        }
    }
}