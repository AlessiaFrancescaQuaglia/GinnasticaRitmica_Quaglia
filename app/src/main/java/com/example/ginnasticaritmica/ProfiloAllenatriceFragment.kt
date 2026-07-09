package com.example.ginnasticaritmica

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.ginnasticaritmica.databinding.FragmentProfiloAllenatriceBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class ProfiloAllenatriceFragment : Fragment() {

    private lateinit var binding: FragmentProfiloAllenatriceBinding
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private var pdfSelezionatoUri: Uri? = null
    private var uriTesseraSalvato: String? = null

    private val selettoreFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            pdfSelezionatoUri = uri
            requireContext().contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            binding.tvStatoFile.text = "Nuovo PDF selezionato. Premi 'Salva'."
            binding.tvStatoFile.setTextColor(android.graphics.Color.parseColor("#4CAF50"))

            // Nascondiamo "Carica" e mostriamo "Cambia"
            binding.btnCaricaTessera.visibility = View.GONE
            binding.btnCambiaTessera.visibility = View.VISIBLE
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentProfiloAllenatriceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnTornaIndietro.setOnClickListener { findNavController().navigateUp() }
        caricaDatiProfilo()

        // Tasti per caricare/cambiare
        val apriSelettore = { selettoreFile.launch(arrayOf("application/pdf")) }
        binding.btnCaricaTessera.setOnClickListener { apriSelettore() }
        binding.btnCambiaTessera.setOnClickListener { apriSelettore() }

        // Tasto Visualizza
        binding.btnVisualizzaTessera.setOnClickListener {
            // Se c'è un PDF nuovo selezionato apriamo quello, altrimenti quello vecchio
            val uriDaAprire = pdfSelezionatoUri?.toString() ?: uriTesseraSalvato
            uriDaAprire?.let { uriString ->
                try {
                    val intent = Intent(Intent.ACTION_VIEW)
                    intent.setDataAndType(Uri.parse(uriString), "application/pdf")
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Nessun lettore PDF installato sul dispositivo", Toast.LENGTH_LONG).show()
                }
            }
        }

        binding.btnSalvaProfilo.setOnClickListener { salvaDatiProfilo() }
    }

    private fun caricaDatiProfilo() {
        val userId = auth.currentUser?.uid ?: return

        db.collection("Allenatrici").document(userId).get().addOnSuccessListener { documento ->
            if (documento.exists()) {
                binding.etNomeAllenatrice.setText(documento.getString("nome") ?: "")
                binding.etCognomeAllenatrice.setText(documento.getString("cognome") ?: "")
                binding.etDataNascitaAllenatrice.setText(documento.getString("dataNascita") ?: "")

                uriTesseraSalvato = documento.getString("uriTesseraPdf")

                if (!uriTesseraSalvato.isNullOrEmpty()) {
                    binding.btnCaricaTessera.visibility = View.GONE
                    binding.btnVisualizzaTessera.visibility = View.VISIBLE
                    binding.btnCambiaTessera.visibility = View.VISIBLE
                    binding.tvStatoFile.text = "Tessera salvata."
                }
            }
        }
    }

    private fun salvaDatiProfilo() {
        val userId = auth.currentUser?.uid ?: return
        val nome = binding.etNomeAllenatrice.text.toString().trim()
        val cognome = binding.etCognomeAllenatrice.text.toString().trim()
        val dataNascita = binding.etDataNascitaAllenatrice.text.toString().trim()

        if (nome.isEmpty()) {
            Toast.makeText(requireContext(), "Il Nome è obbligatorio!", Toast.LENGTH_SHORT).show()
            return
        }

        val datiAggiornati = mutableMapOf<String, Any>(
            "nome" to nome,
            "cognome" to cognome,
            "dataNascita" to dataNascita
        )

        if (pdfSelezionatoUri != null) {
            datiAggiornati["uriTesseraPdf"] = pdfSelezionatoUri.toString()
        }

        // ECCO LA MAGIA: SetOptions.merge() crea il documento se non c'è, o lo aggiorna se esiste!
        db.collection("Allenatrici").document(userId)
            .set(datiAggiornati, SetOptions.merge())
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Profilo aggiornato!", Toast.LENGTH_SHORT).show()
                findNavController().navigateUp()
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Errore nel salvataggio", Toast.LENGTH_SHORT).show()
            }
    }
}