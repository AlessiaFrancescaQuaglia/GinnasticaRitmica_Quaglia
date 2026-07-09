package com.example.ginnasticaritmica

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.ginnasticaritmica.databinding.FragmentGestioneAtleteBinding
import java.util.Calendar
import java.util.Locale

class GestioneAtleteFragment : Fragment() {

    private var _binding: FragmentGestioneAtleteBinding? = null
    private val binding get() = _binding!!

    // COLLEGIAMO IL CERVELLO (ViewModel)
    private val viewModel: GestioneAtleteViewModel by viewModels()
    private lateinit var adapter: AtletaAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGestioneAtleteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = AtletaAdapter(emptyList())
        binding.recyclerViewAtlete.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewAtlete.adapter = adapter

        binding.btnTornaIndietro.setOnClickListener { findNavController().navigateUp() }
        binding.fabAggiungiAtleta.setOnClickListener { mostraPopupAggiunta() }

        // MAGIA MVVM: Osserviamo la lista dal ViewModel. Se cambia nel DB, la lista si aggiorna da sola!
        viewModel.atleteList.observe(viewLifecycleOwner) { listaAggiornata ->
            adapter.updateData(listaAggiornata)
        }

        // MAGIA MVVM: Osserviamo i messaggi. Se il ViewModel vuole dire qualcosa, il Fragment mostra il Toast!
        viewModel.toastMessage.observe(viewLifecycleOwner) { messaggio ->
            Toast.makeText(requireContext(), messaggio, Toast.LENGTH_SHORT).show()
        }
    }

    private fun mostraPopupAggiunta() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_aggiungi_atleta, null)

        val etNome = dialogView.findViewById<EditText>(R.id.etNome)
        val etCognome = dialogView.findViewById<EditText>(R.id.etCognome)
        // Recuperiamo il nuovo campo Email dalla grafica del popup
        val etEmailAtleta = dialogView.findViewById<EditText>(R.id.etEmailAtleta)

        val btnDataNascita = dialogView.findViewById<android.widget.Button>(R.id.btnDataNascita)
        val btnDataCert = dialogView.findViewById<android.widget.Button>(R.id.btnDataCertificato)
        val spinner = dialogView.findViewById<android.widget.Spinner>(R.id.spinnerLivello)

        val livelli = arrayOf("LA1", "LA2", "LB1", "LB2", "LC1", "LC2", "LD", "LE", "Silver Base", "Gold")
        spinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, livelli)

        var dataNascitaScelta = ""
        var dataCertScelta = ""

        btnDataNascita.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(requireContext(), { _, y, m, d ->
                dataNascitaScelta = String.format(Locale.getDefault(), "%04d-%02d-%02d", y, m + 1, d)
                btnDataNascita.text = "Nata il: $dataNascitaScelta"
            }, 2015, 0, 1).show()
        }

        btnDataCert.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(requireContext(), { _, y, m, d ->
                dataCertScelta = String.format(Locale.getDefault(), "%04d-%02d-%02d", y, m + 1, d)
                btnDataCert.text = "Certificato: $dataCertScelta"
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Nuova Ginnasta")
            .setView(dialogView)
            .setPositiveButton("Salva") { _, _ ->
                val nome = etNome.text.toString().trim()
                val cognome = etCognome.text.toString().trim()
                val livello = spinner.selectedItem.toString()

                // Leggiamo l'email inserita e la forziamo in minuscolo per evitare mancati "match" futuri
                val emailAtleta = etEmailAtleta.text.toString().trim().lowercase()

                if (nome.isNotEmpty() && cognome.isNotEmpty() && dataNascitaScelta.isNotEmpty()) {
                    // Passiamo anche l'emailAtleta come sesto parametro al ViewModel!
                    viewModel.aggiungiAtleta(nome, cognome, livello, dataNascitaScelta, dataCertScelta, emailAtleta)
                } else {
                    Toast.makeText(requireContext(), "Nome, Cognome e Data Nascita sono obbligatori!", Toast.LENGTH_LONG).show()
                }
            }.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Non serve più spegnere il Listener qui, lo fa in automatico il ViewModel in onCleared()!
        _binding = null
    }
}