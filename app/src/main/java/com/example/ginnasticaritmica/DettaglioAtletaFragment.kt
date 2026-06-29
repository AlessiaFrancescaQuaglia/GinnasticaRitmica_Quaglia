package com.example.ginnasticaritmica

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.graphics.Bitmap
import android.media.MediaPlayer
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.example.ginnasticaritmica.databinding.FragmentDettaglioAtletaBinding
import com.google.firebase.firestore.FirebaseFirestore
import java.io.ByteArrayOutputStream
import java.util.Calendar
import java.util.Locale

class DettaglioAtletaFragment : Fragment() {

    private var _binding: FragmentDettaglioAtletaBinding? = null
    private val binding get() = _binding!!

    private val db = FirebaseFirestore.getInstance()
    private var atletaId: String? = null

    private var atletaCorrente: Atleta? = null
    private var mediaPlayer: MediaPlayer? = null
    private var tracceDisponibili: Map<String, String> = emptyMap()

    // 1. NUOVO ATTIVATORE DELLA FOTOCAMERA (Prende un'anteprima leggera perfetta per i profili)
// 1. Lanciatore per chiedere il permesso
    private val requestCameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            // Se l'utente dice SI, apriamo la fotocamera
            takePictureLauncher.launch(null)
        } else {
            // Se l'utente dice NO
            Toast.makeText(requireContext(), "Permesso fotocamera negato", Toast.LENGTH_SHORT).show()
        }
    }

    // 2. Lanciatore per scattare la foto (rimane identico a prima)
    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            salvaFotoInBase64(bitmap)
        } else {
            Toast.makeText(requireContext(), "Azione annullata", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDettaglioAtletaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        atletaId = arguments?.getString("atletaId")
        val atletaNome = arguments?.getString("atletaNome")
        binding.tvNomeDettaglio.text = atletaNome

        atletaId?.let { scaricaProfiloAtleta(it) }

        // --- GESTIONE BOTTONI PROFILO TECNICO ---

        binding.btnModificaLivello.setOnClickListener { mostraDialogLivello() }
        binding.btnModificaNascita.setOnClickListener { mostraCalendarioNascita() }
        binding.btnModificaAttrezzi.setOnClickListener { mostraDialogAttrezzi() }
        binding.btnEliminaAtleta.setOnClickListener { mostraConfermaEliminazione() }

        // 2. COLLEGHIAMO IL BOTTONE ALLA FOTOCAMERA
        // COLLEGHIAMO IL BOTTONE ALLA FOTOCAMERA (CON CONTROLLO PERMESSI)
        binding.btnModificaFoto.setOnClickListener {
            // Chiediamo ad Android se abbiamo già il permesso
            if (androidx.core.content.ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                // Abbiamo già il permesso, apriamo subito la fotocamera!
                takePictureLauncher.launch(null)
            } else {
                // Non abbiamo il permesso, mostriamo il popup per chiederlo!
                requestCameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
            }
        }

        // --- GESTIONE BOTTONI CERTIFICATO E MUSICA ---

        binding.btnAggiornaCertificato.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(requireContext(), { _, year, month, day ->
                val data = String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month + 1, day)
                binding.tvScadenzaCertificato.text = data
                aggiornaDatoSuFirebase("scadenzaCertificato", data)
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        binding.btnCaricaMusica.setOnClickListener { mostraDialogSceltaAttrezzoDropbox() }

        impostaBottoniRiproduzione()
    }

    // --- AGGIORNAMENTO GRAFICA E VISUALIZZAZIONE FOTO ---

    private fun scaricaProfiloAtleta(id: String) {
        db.collection("Atlete").document(id).get().addOnSuccessListener { documento ->
            if (documento != null && documento.exists()) {
                val atleta = documento.toObject(Atleta::class.java)
                atleta?.let {
                    atletaCorrente = it
                    tracceDisponibili = it.tracceMusicali

                    binding.tvLivelloScelto.text = "Livello: ${if (it.livello.isNotEmpty()) it.livello else "Non inserito"}"
                    binding.tvDataNascita.text = "Data di nascita: ${if (it.dataNascita.isNotEmpty()) it.dataNascita else "Non inserita"}"
                    binding.tvAttrezziUsati.text = "Attrezzi dell'anno: ${if (it.attrezziUsati.isNotEmpty()) it.attrezziUsati.joinToString(", ") else "Nessuno"}"
                    binding.tvScadenzaCertificato.text = if (it.scadenzaCertificato.isNotEmpty()) it.scadenzaCertificato else "Nessuna scadenza inserita"
                    binding.tvStatoMusica.text = if (it.tracceMusicali.isNotEmpty()) "Musica caricata" else "Nessuna traccia caricata"

                    aggiornaIntestazione()

                    // 3. GLIDE ORA LEGGE SIA I LINK CHE LE FOTO TRASFORMATE IN TESTO
                    if (it.fotoUrl.isNotEmpty()) {
                        try {
                            if (it.fotoUrl.startsWith("http")) {
                                Glide.with(this).load(it.fotoUrl).circleCrop().into(binding.imgProfiloAtleta)
                            } else {
                                val imageByteArray = Base64.decode(it.fotoUrl, Base64.NO_WRAP)
                                Glide.with(this).load(imageByteArray).circleCrop().into(binding.imgProfiloAtleta)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }
    }

    private fun aggiornaIntestazione() {
        val liv = atletaCorrente?.livello?.takeIf { it.isNotEmpty() } ?: "Livello ?"
        val cat = atletaCorrente?.categoria?.takeIf { it.isNotEmpty() } ?: "Categoria ?"
        binding.tvCategoriaDettaglio.text = "$liv • $cat"
    }

    // 4. LA MAGIA: TRASFORMA LA FOTO IN TESTO (BASE64) E LA SALVA SU FIRESTORE
    private fun salvaFotoInBase64(bitmap: Bitmap) {
        Toast.makeText(requireContext(), "Elaborazione foto...", Toast.LENGTH_SHORT).show()

        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos) // Comprime al 70% per renderla leggerissima
        val data = baos.toByteArray()
        val stringaBase64 = Base64.encodeToString(data, Base64.NO_WRAP)

        atletaId?.let { id ->
            db.collection("Atlete").document(id).update("fotoUrl", stringaBase64)
                .addOnSuccessListener {
                    Toast.makeText(requireContext(), "Foto profilo aggiornata!", Toast.LENGTH_SHORT).show()
                    scaricaProfiloAtleta(id) // Ricarica la grafica per mostrare la foto
                }
                .addOnFailureListener {
                    Toast.makeText(requireContext(), "Errore durante il salvataggio", Toast.LENGTH_SHORT).show()
                }
        }
    }

    // --- FUNZIONI PROFILO TECNICO (LIVELLO, NASCITA E ATTREZZI) ---

    private fun mostraDialogLivello() {
        val livelli = arrayOf("LA1", "LA2", "LB1", "LB2", "LC1", "LC2", "LD", "LE", "Gold")
        AlertDialog.Builder(requireContext())
            .setTitle("Imposta Livello ")
            .setItems(livelli) { _, quale ->
                val livelloScelto = livelli[quale]
                binding.tvLivelloScelto.text = "Livello: $livelloScelto"
                atletaCorrente?.livello = livelloScelto
                aggiornaIntestazione()
                aggiornaDatoSuFirebase("livello", livelloScelto)
            }.setNegativeButton("Annulla", null).show()
    }

    private fun mostraCalendarioNascita() {
        val cal = Calendar.getInstance()
        val annoCorrente = cal.get(Calendar.YEAR)

        DatePickerDialog(requireContext(), { _, year, month, day ->
            var eta = annoCorrente - year
            if (cal.get(Calendar.MONTH) < month || (cal.get(Calendar.MONTH) == month && cal.get(Calendar.DAY_OF_MONTH) < day)) {
                eta--
            }
            val nuovaCategoria = when {
                eta in 8..9 -> "Allieve 1"; eta == 10 -> "Allieve 2"; eta == 11 -> "Allieve 3"
                eta == 12 -> "Allieve 4"; eta == 13 -> "Allieve 5 – Prejunior"; eta == 14 -> "Junior 1"
                eta == 15 -> "Junior 2"; eta == 16 -> "Senior 1"; eta in 17..18 -> "Senior 2"
                eta >= 19 -> "Senior 3"; else -> "Pre-agonismo (< 8 anni)"
            }
            val dataScelta = String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month + 1, day)

            binding.tvDataNascita.text = "Data di nascita: $dataScelta"
            atletaCorrente?.categoria = nuovaCategoria
            aggiornaIntestazione()

            atletaId?.let { id ->
                val aggiornamenti = mapOf("dataNascita" to dataScelta, "categoria" to nuovaCategoria)
                db.collection("Atlete").document(id).update(aggiornamenti)
                    .addOnSuccessListener { Toast.makeText(requireContext(), "Categoria aggiornata a $nuovaCategoria!", Toast.LENGTH_LONG).show() }
            }
        }, cal.get(Calendar.YEAR) - 10, cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun mostraDialogAttrezzi() {
        val tuttiGliAttrezzi = arrayOf("Corpo Libero", "Fune", "Cerchio", "Palla", "Clavette", "Nastro")
        val attrezziGiaScelti = BooleanArray(tuttiGliAttrezzi.size)
        val listaAttuali = atletaCorrente?.attrezziUsati ?: emptyList()

        for (i in tuttiGliAttrezzi.indices) {
            if (listaAttuali.contains(tuttiGliAttrezzi[i])) attrezziGiaScelti[i] = true
        }
        val nuovaListaAttrezzi = listaAttuali.toMutableList()

        AlertDialog.Builder(requireContext())
            .setTitle("Quali attrezzi usa quest'anno?")
            .setMultiChoiceItems(tuttiGliAttrezzi, attrezziGiaScelti) { _, quale, isChecked ->
                val attrezzoCliccato = tuttiGliAttrezzi[quale]
                if (isChecked) nuovaListaAttrezzi.add(attrezzoCliccato) else nuovaListaAttrezzi.remove(attrezzoCliccato)
            }
            .setPositiveButton("Salva") { _, _ ->
                val listaPulita = nuovaListaAttrezzi.distinct()
                binding.tvAttrezziUsati.text = "Attrezzi dell'anno: ${if (listaPulita.isNotEmpty()) listaPulita.joinToString(", ") else "Nessuno"}"
                aggiornaDatoSuFirebase("attrezziUsati", listaPulita)
                atletaCorrente?.attrezziUsati = listaPulita
            }.setNegativeButton("Annulla", null).show()
    }

    private fun mostraConfermaEliminazione() {
        AlertDialog.Builder(requireContext())
            .setTitle("Eliminare Ginnasta?")
            .setMessage("Sei sicura di voler eliminare definitivamente questa atleta?")
            .setPositiveButton("Sì, Elimina") { _, _ -> eliminaAtleta() }
            .setNegativeButton("Annulla", null).show()
    }

    private fun aggiornaDatoSuFirebase(campo: String, nuovoValore: Any) {
        atletaId?.let { id ->
            db.collection("Atlete").document(id).update(campo, nuovoValore)
                .addOnSuccessListener { Toast.makeText(requireContext(), "Dati aggiornati!", Toast.LENGTH_SHORT).show() }
        }
    }

    private fun eliminaAtleta() {
        atletaId?.let { id ->
            db.collection("Atlete").document(id).delete().addOnSuccessListener {
                Toast.makeText(requireContext(), "Ginnasta eliminata", Toast.LENGTH_SHORT).show()
                findNavController().navigateUp()
            }
        }
    }

    // --- FUNZIONI CARICAMENTO DROPBOX ---

    private fun mostraDialogSceltaAttrezzoDropbox() {
        val attrezzi = arrayOf("Corpo Libero", "Fune", "Cerchio", "Palla", "Clavette", "Nastro")
        AlertDialog.Builder(requireContext()).setTitle("Scegli l'attrezzo")
            .setItems(attrezzi) { _, quale -> mostraPopupInserimentoLink(attrezzi[quale]) }
            .setNegativeButton("Annulla", null).show()
    }

    private fun mostraPopupInserimentoLink(attrezzo: String) {
        val inputLink = EditText(requireContext())
        inputLink.hint = "Incolla qui il link di Dropbox"
        inputLink.setPadding(32, 32, 32, 32)
        AlertDialog.Builder(requireContext()).setTitle("Musica per $attrezzo").setView(inputLink)
            .setPositiveButton("Salva") { _, _ ->
                val link = inputLink.text.toString().trim()
                if (link.isNotEmpty()) salvaLinkDropboxSuFirestore(attrezzo, link)
            }.setNegativeButton("Annulla", null).show()
    }

    private fun salvaLinkDropboxSuFirestore(attrezzo: String, linkOriginale: String) {
        val id = atletaId ?: return
        val linkModificato = linkOriginale.replace("www.dropbox.com", "dl.dropboxusercontent.com").replace("?dl=0", "")
        db.collection("Atlete").document(id).update("tracceMusicali.$attrezzo", linkModificato)
            .addOnSuccessListener {
                binding.tvStatoMusica.text = "Tracce salvate. Ultima: $attrezzo"
                tracceDisponibili = tracceDisponibili + (attrezzo to linkModificato)
                Toast.makeText(requireContext(), "Link salvato!", Toast.LENGTH_SHORT).show()
            }
    }

    // --- FUNZIONI LETTORE MUSICALE ---

    private fun impostaBottoniRiproduzione() {
        binding.btnRiproduciMusica.setOnClickListener {
            if (tracceDisponibili.isEmpty()) return@setOnClickListener
            val attrezzi = tracceDisponibili.keys.toTypedArray()
            AlertDialog.Builder(requireContext()).setTitle("Quale base vuoi riprodurre?")
                .setItems(attrezzi) { _, quale ->
                    tracceDisponibili[attrezzi[quale]]?.let { url -> avviaRiproduzione(url) }
                }.setNegativeButton("Annulla", null).show()
        }
        binding.btnFermaMusica.setOnClickListener { fermaRiproduzione() }
    }

    private fun avviaRiproduzione(url: String) {
        fermaRiproduzione()
        Toast.makeText(requireContext(), "Caricamento traccia...", Toast.LENGTH_SHORT).show()
        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(android.media.AudioAttributes.Builder().setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC).setUsage(android.media.AudioAttributes.USAGE_MEDIA).build())
                setDataSource(requireContext(), android.net.Uri.parse(url))
                setOnErrorListener { _, _, _ ->
                    Toast.makeText(requireContext(), "Errore di riproduzione.", Toast.LENGTH_LONG).show()
                    fermaRiproduzione()
                    true
                }
                setOnPreparedListener {
                    start()
                    binding.btnRiproduciMusica.visibility = View.GONE
                    binding.btnFermaMusica.visibility = View.VISIBLE
                }
                setOnCompletionListener { fermaRiproduzione() }
                prepareAsync()
            }
        } catch (e: Exception) {}
    }

    private fun fermaRiproduzione() {
        mediaPlayer?.let { if (it.isPlaying) it.stop(); it.release() }
        mediaPlayer = null
        binding.btnRiproduciMusica.visibility = View.VISIBLE
        binding.btnFermaMusica.visibility = View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        fermaRiproduzione()
        _binding = null
    }
}