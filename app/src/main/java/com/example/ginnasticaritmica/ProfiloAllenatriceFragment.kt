package com.example.ginnasticaritmica

import android.annotation.SuppressLint
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
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri

class ProfiloAllenatriceFragment : Fragment() {

    private lateinit var binding: FragmentProfiloAllenatriceBinding
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private var pdfSelezionatoUri: Uri? = null
    private var uriTesseraSalvato: String? = null

    //SCOPED STORAGE: Launcher per aprire in sicurezza i file
    @SuppressLint("SetTextI18n")
    private val selettoreFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            pdfSelezionatoUri = uri

            // Chiedo ad Android il permesso "permanente" di leggere questo file.
            // Senza questo, se chiudo l'app e la riapro, l'URI salvato darebbe un errore di sicurezza
            requireContext().contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )

            binding.tvStatoFile.text = "Nuovo PDF selezionato. Premi 'Salva'."
            binding.tvStatoFile.setTextColor("#4CAF50".toColorInt())

            // Gestione dinamica dei bottoni per la UX
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

        // Carico subito i dati esistenti da Firebase
        caricaDatiProfilo()

        // Assegnazione della stessa Lambda a due bottoni diversi per non duplicare codice
        val apriSelettore = { selettoreFile.launch(arrayOf("application/pdf")) }
        binding.btnCaricaTessera.setOnClickListener { apriSelettore() }
        binding.btnCambiaTessera.setOnClickListener { apriSelettore() }

        // DELEGAZIONE LETTURA PDF: Affido a un'app esterna l'apertura del documento
        binding.btnVisualizzaTessera.setOnClickListener {
            // Logica di fallback: do priorità al PDF appena scelto, sennò uso quello vecchio dal DB
            val uriDaAprire = pdfSelezionatoUri?.toString() ?: uriTesseraSalvato
            uriDaAprire?.let { uriString ->
                try {
                    val intent = Intent(Intent.ACTION_VIEW)
                    intent.setDataAndType(uriString.toUri(), "application/pdf")
                    // Aggiungo il flag per autorizzare temporaneamente l'app terza a leggere il mio file
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    startActivity(intent)
                } catch (_: Exception) {
                    // 5. EXCEPTION HANDLING: Previene il crash se il telefono non ha lettori PDF installati
                    Toast.makeText(requireContext(), "Nessun lettore PDF installato sul dispositivo", Toast.LENGTH_LONG).show()
                }
            }
        }

        binding.btnSalvaProfilo.setOnClickListener { salvaDatiProfilo() }
    }

    @SuppressLint("SetTextI18n")
    private fun caricaDatiProfilo() {
        val userId = auth.currentUser?.uid ?: return

        db.collection("Allenatrici").document(userId).get().addOnSuccessListener { documento ->
            if (documento.exists()) {
                binding.etNomeAllenatrice.setText(documento.getString("nome") ?: "")
                binding.etCognomeAllenatrice.setText(documento.getString("cognome") ?: "")
                binding.etDataNascitaAllenatrice.setText(documento.getString("dataNascita") ?: "")

                uriTesseraSalvato = documento.getString("uriTesseraPdf")

                // Aggiornamento grafico se la tessera è già presente nel DB
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

        // Aggiungo l'URI del file al DB solo se l'utente ha effettivamente selezionato un nuovo PDF
        if (pdfSelezionatoUri != null) {
            datiAggiornati["uriTesseraPdf"] = pdfSelezionatoUri.toString()
        }

        // 6. UPSERT (Update/Insert): Scrittura intelligente su Firestore
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