package com.example.ginnasticaritmica

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.graphics.Bitmap
import android.graphics.Color
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
import androidx.navigation.fragment.navArgs // IMPORTANTE: Import per Safe Args
import com.bumptech.glide.Glide
import com.example.ginnasticaritmica.databinding.FragmentDettaglioAtletaBinding
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import androidx.core.graphics.toColorInt

//PANNELLO DI CONTROLLO ATLETA: Integra permessi, CRUD, grafici e Media Streaming (Delegato).
class DettaglioAtletaFragment : Fragment() {

    private var _binding: FragmentDettaglioAtletaBinding? = null
    private val binding get() = _binding!!

    // Inizializzazione Safe Args
    private val args: DettaglioAtletaFragmentArgs by navArgs()

    private val db = FirebaseFirestore.getInstance()
    private var atletaId: String? = null
    private var atletaCorrente: Atleta? = null

    // Deleghiamo la riproduzione al nostro Manager esterno
    private lateinit var musicManager: MusicManager
    private var tracceDisponibili: Map<String, String> = emptyMap()

    // Chiede il permesso della fotocamera
    private val requestCameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            takePictureLauncher.launch(null)
        } else {
            Toast.makeText(requireContext(), "Permesso fotocamera negato", Toast.LENGTH_SHORT).show()
        }
    }

    // Launcher per scattare la foto
    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) salvaFotoInBase64(bitmap)
        else Toast.makeText(requireContext(), "Azione annullata", Toast.LENGTH_SHORT).show()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDettaglioAtletaBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // INIZIALIZZAZIONE MUSIC MANAGER
        musicManager = MusicManager(
            context = requireContext(),
            onPrepared = {
                binding.btnRiproduciMusica.visibility = View.GONE
                binding.btnFermaMusica.visibility = View.VISIBLE
            },
            onCompletion = {
                binding.btnRiproduciMusica.visibility = View.VISIBLE
                binding.btnFermaMusica.visibility = View.GONE
            },
            onError = {
                Toast.makeText(requireContext(), "Errore di riproduzione.", Toast.LENGTH_LONG).show()
                binding.btnRiproduciMusica.visibility = View.VISIBLE
                binding.btnFermaMusica.visibility = View.GONE
            }
        )

        // Estrazione dati sicura tramite Safe Args (sostituisce il vecchio Bundle)
        atletaId = args.atletaId
        atletaId?.let { scaricaProfiloAtleta(it) }

        // Mappatura modifiche anagrafiche, tecniche e account
        binding.tvNomeDettaglio.text = args.atletaNome

        // GESTIONE BOTTONI
        binding.btnTornaIndietro.setOnClickListener { findNavController().navigateUp() }
        binding.btnModificaLivello.setOnClickListener { mostraDialogLivello() }
        binding.btnModificaNascita.setOnClickListener { mostraCalendarioNascita() }
        binding.btnModificaAttrezzi.setOnClickListener { mostraDialogAttrezzi() }
        binding.btnModificaGruppo.setOnClickListener { mostraDialogGruppo() }

        binding.btnModificaEmail.setOnClickListener {
            val inputEmail = EditText(requireContext())
            inputEmail.hint = "Inserisci l'email della ginnasta"
            inputEmail.inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS

            AlertDialog.Builder(requireContext())
                .setTitle("Collega/Modifica Email")
                .setView(inputEmail)
                .setPositiveButton("Salva") { _, _ ->
                    val nuovaEmail = inputEmail.text.toString().trim().lowercase()
                    if (nuovaEmail.isNotEmpty()) {
                        atletaId?.let { id ->
                            db.collection("Atlete").document(id).update("email", nuovaEmail)
                                .addOnSuccessListener {
                                    Toast.makeText(requireContext(), "Email aggiornata!", Toast.LENGTH_SHORT).show()
                                    binding.tvEmailAtletaDettaglio.text = "Email di accesso: $nuovaEmail"
                                }
                        }
                    }
                }
                .setNegativeButton("Annulla", null)
                .show()
        }

        binding.btnEliminaAtleta.setOnClickListener { mostraConfermaEliminazione() }

        binding.btnModificaFoto.setOnClickListener {
            if (androidx.core.content.ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                takePictureLauncher.launch(null)
            } else {
                requestCameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
            }
        }

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

        //GESTIONE BOTTONI DIARIO PRESENZE
        binding.btnAggiungiPresenza.setOnClickListener {
            val dataOggi = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            atletaCorrente?.let {
                val nuovePresenze = it.datePresenze.toMutableList()
                if (!nuovePresenze.contains(dataOggi)) {
                    nuovePresenze.add(dataOggi)
                    it.datePresenze = nuovePresenze
                    impostaGrafico(it.datePresenze.size, it.dateAssenze.size)
                    db.collection("Atlete").document(atletaId!!).update("datePresenze", FieldValue.arrayUnion(dataOggi))
                } else {
                    Toast.makeText(requireContext(), "Presenza di oggi già registrata!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.btnAggiungiAssenza.setOnClickListener {
            val dataOggi = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            atletaCorrente?.let {
                val nuoveAssenze = it.dateAssenze.toMutableList()
                if (!nuoveAssenze.contains(dataOggi)) {
                    nuoveAssenze.add(dataOggi)
                    it.dateAssenze = nuoveAssenze
                    impostaGrafico(it.datePresenze.size, it.dateAssenze.size)
                    db.collection("Atlete").document(atletaId!!).update("dateAssenze", FieldValue.arrayUnion(dataOggi))
                } else {
                    Toast.makeText(requireContext(), "Assenza di oggi già registrata!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // GRAFICA E AGGIORNAMENTI
    @SuppressLint("SetTextI18n")
    private fun scaricaProfiloAtleta(id: String) {
        db.collection("Atlete").document(id).get().addOnSuccessListener { documento ->
            if (documento != null && documento.exists()) {
                val atleta = documento.toObject(Atleta::class.java)
                atleta?.let {
                    atletaCorrente = it
                    tracceDisponibili = it.tracceMusicali

                    binding.tvLivelloScelto.text = "Livello: ${it.livello.ifEmpty { "Non inserito" }}"
                    binding.tvDataNascita.text = "Data di nascita: ${it.dataNascita.ifEmpty { "Non inserita" }}"
                    binding.tvAttrezziUsati.text = "Attrezzi dell'anno: ${if (it.attrezziUsati.isNotEmpty()) it.attrezziUsati.joinToString(", ") else "Nessuno"}"
                    binding.tvGruppoScelto.text = "Gruppo: ${it.gruppo.ifEmpty { "Non assegnato" }}"
                    binding.tvScadenzaCertificato.text = it.scadenzaCertificato.ifEmpty { "Nessuna scadenza inserita" }
                    binding.tvStatoMusica.text = if (it.tracceMusicali.isNotEmpty()) "Musica caricata" else "Nessuna traccia caricata"

                    val emailSalvata = documento.getString("email") ?: ""
                    if (emailSalvata.isNotEmpty()) {
                        binding.tvEmailAtletaDettaglio.text = "Email di accesso: $emailSalvata"
                    } else {
                        binding.tvEmailAtletaDettaglio.text = "Email di accesso: Non inserita"
                    }

                    aggiornaIntestazione()
                    impostaGrafico(it.datePresenze.size, it.dateAssenze.size)

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

    @SuppressLint("SetTextI18n")
    private fun aggiornaIntestazione() {
        val liv = atletaCorrente?.livello?.takeIf { it.isNotEmpty() } ?: "Livello ?"
        val cat = atletaCorrente?.categoria?.takeIf { it.isNotEmpty() } ?: "Categoria ?"
        binding.tvCategoriaDettaglio.text = "$liv • $cat"
    }

    private fun impostaGrafico(presenze: Int, assenze: Int) {
        val chart = binding.pieChartPresenze

        if (presenze == 0 && assenze == 0) {
            chart.clear()
            return
        }

        val entries = ArrayList<PieEntry>()
        if (presenze > 0) entries.add(PieEntry(presenze.toFloat(), "Presenze"))
        if (assenze > 0) entries.add(PieEntry(assenze.toFloat(), "Assenze"))

        val dataSet = PieDataSet(entries, "")
        dataSet.colors = listOf(
            "#03DAC5".toColorInt(),
            "#CF6679".toColorInt()
        )
        dataSet.valueTextColor = Color.WHITE
        dataSet.valueTextSize = 14f

        chart.data = PieData(dataSet)
        chart.description.isEnabled = false
        chart.legend.textColor = Color.WHITE
        chart.setHoleColor("#2A2A2A".toColorInt())
        chart.setTransparentCircleAlpha(0)
        chart.animateY(1000)
        chart.invalidate()
    }

    // DELEGAZIONE BASE64 AL PHOTO MANAGER
    private fun salvaFotoInBase64(bitmap: Bitmap) {
        Toast.makeText(requireContext(), "Elaborazione foto...", Toast.LENGTH_SHORT).show()

        // Chiamo la funzione dal nuovo Singleton
        val stringaBase64 = PhotoManager.encodeBitmapToBase64(bitmap)

        atletaId?.let { id ->
            db.collection("Atlete").document(id).update("fotoUrl", stringaBase64)
                .addOnSuccessListener {
                    Toast.makeText(requireContext(), "Foto profilo aggiornata!", Toast.LENGTH_SHORT).show()
                    scaricaProfiloAtleta(id)
                }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun mostraDialogLivello() {
        val livelli = arrayOf("LA1", "LA2", "LB1", "LB2", "LC1", "LC2", "LD", "LE", "Gold")
        AlertDialog.Builder(requireContext()).setTitle("Imposta Livello ")
            .setItems(livelli) { _, quale ->
                val livelloScelto = livelli[quale]
                binding.tvLivelloScelto.text = "Livello: $livelloScelto"
                atletaCorrente?.livello = livelloScelto
                aggiornaIntestazione()
                aggiornaDatoSuFirebase("livello", livelloScelto)
            }.setNegativeButton("Annulla", null).show()
    }

    @SuppressLint("SetTextI18n")
    private fun mostraCalendarioNascita() {
        val cal = Calendar.getInstance()
        DatePickerDialog(requireContext(), { _, year, month, day ->

            val nuovaCategoria = CategoriaUtils.calcolaCategoria(year, month, day)
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

    @SuppressLint("SetTextI18n")
    private fun mostraDialogAttrezzi() {
        val tuttiGliAttrezzi = arrayOf("Corpo Libero", "Fune", "Cerchio", "Palla", "Clavette", "Nastro")
        val attrezziGiaScelti = BooleanArray(tuttiGliAttrezzi.size)
        val listaAttuali = atletaCorrente?.attrezziUsati ?: emptyList()
        for (i in tuttiGliAttrezzi.indices) if (listaAttuali.contains(tuttiGliAttrezzi[i])) attrezziGiaScelti[i] = true
        val nuovaListaAttrezzi = listaAttuali.toMutableList()

        AlertDialog.Builder(requireContext()).setTitle("Quali attrezzi usa quest'anno?")
            .setMultiChoiceItems(tuttiGliAttrezzi, attrezziGiaScelti) { _, quale, isChecked ->
                val attrezzoCliccato = tuttiGliAttrezzi[quale]
                if (isChecked) nuovaListaAttrezzi.add(attrezzoCliccato) else nuovaListaAttrezzi.remove(attrezzoCliccato)
            }.setPositiveButton("Salva") { _, _ ->
                val listaPulita = nuovaListaAttrezzi.distinct()
                binding.tvAttrezziUsati.text = "Attrezzi dell'anno: ${if (listaPulita.isNotEmpty()) listaPulita.joinToString(", ") else "Nessuno"}"
                aggiornaDatoSuFirebase("attrezziUsati", listaPulita)
                atletaCorrente?.attrezziUsati = listaPulita
            }.setNegativeButton("Annulla", null).show()
    }

    @SuppressLint("SetTextI18n")
    private fun mostraDialogGruppo() {
        val gruppi = arrayOf("ago", "preago", "promo1", "promo2", "corsi grandi", "corsi piccoli")
        AlertDialog.Builder(requireContext()).setTitle("Assegna Gruppo")
            .setItems(gruppi) { _, quale ->
                val gruppoScelto = gruppi[quale]
                binding.tvGruppoScelto.text = "Gruppo: $gruppoScelto"
                atletaCorrente?.gruppo = gruppoScelto
                aggiornaDatoSuFirebase("gruppo", gruppoScelto)
            }.setNegativeButton("Annulla", null).show()
    }

    private fun mostraConfermaEliminazione() {
        AlertDialog.Builder(requireContext()).setTitle("Eliminare Ginnasta?")
            .setMessage("Sei sicura di voler eliminare definitivamente questa atleta?")
            .setPositiveButton("Sì, Elimina") { _, _ -> eliminaAtleta() }
            .setNegativeButton("Annulla", null).show()
    }

    private fun aggiornaDatoSuFirebase(campo: String, nuovoValore: Any) {
        atletaId?.let { id -> db.collection("Atlete").document(id).update(campo, nuovoValore) }
    }

    private fun eliminaAtleta() {
        atletaId?.let { id ->
            db.collection("Atlete").document(id).delete().addOnSuccessListener {
                Toast.makeText(requireContext(), "Ginnasta eliminata", Toast.LENGTH_SHORT).show()
                findNavController().navigateUp()
            }
        }
    }

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

    @SuppressLint("SetTextI18n")
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

    // RIPRODUZIONE DELEGATA AL MUSIC MANAGER
    private fun impostaBottoniRiproduzione() {
        binding.btnRiproduciMusica.setOnClickListener {
            if (tracceDisponibili.isEmpty()) return@setOnClickListener
            val attrezzi = tracceDisponibili.keys.toTypedArray()

            AlertDialog.Builder(requireContext()).setTitle("Quale base vuoi riprodurre?")
                .setItems(attrezzi) { _, quale ->
                    tracceDisponibili[attrezzi[quale]]?.let { url ->
                        Toast.makeText(requireContext(), "Caricamento traccia...", Toast.LENGTH_SHORT).show()
                        musicManager.play(url)
                    }
                }.setNegativeButton("Annulla", null).show()
        }

        binding.btnFermaMusica.setOnClickListener {
            musicManager.stop()
            binding.btnRiproduciMusica.visibility = View.VISIBLE
            binding.btnFermaMusica.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        musicManager.stop()
        _binding = null
    }
}