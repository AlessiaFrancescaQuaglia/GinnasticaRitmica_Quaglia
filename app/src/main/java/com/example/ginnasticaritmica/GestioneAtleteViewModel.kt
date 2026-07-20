package com.example.ginnasticaritmica

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class GestioneAtleteViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private var atleteListener: ListenerRegistration? = null

    // LIVEDATA: Il Fragment osserva queste variabili per aggiornare questi dati
    private val _atleteList = MutableLiveData<List<Atleta>>()
    val atleteList: LiveData<List<Atleta>> get() = _atleteList

    private val _toastMessage = MutableLiveData<String>()
    val toastMessage: LiveData<String> get() = _toastMessage

    init {
        // Appena il ViewModel nasce, inizia a scaricare i dati
        caricaAtleteDalCloud()
    }


    // funzione per scaricare la lista di atleti dal database
    private fun caricaAtleteDalCloud() {
        val idAllenatriceAttuale = auth.currentUser?.uid ?: return
        atleteListener = db.collection("Atlete")
            .whereEqualTo("idAllenatrice", idAllenatriceAttuale)
            .addSnapshotListener { valore, errore ->
                if (errore != null) return@addSnapshotListener

                if (valore != null) {
                    val lista = mutableListOf<Atleta>()
                    for (documento in valore.documents) {
                        val atleta = documento.toObject(Atleta::class.java)
                        if (atleta != null) lista.add(atleta)
                    }
                    _atleteList.value = lista // Aggiorniamo il LiveData con la nuova lista di atleti
                }
            }
    }

    // funzione per aggiungere un nuovo atleta o collegarne uno esistente
    fun aggiungiAtleta(nome: String, cognome: String, livello: String, dataNascita: String, dataCertificato: String, emailAtleta: String) {
        val idAllenatriceAttuale = auth.currentUser?.uid ?: return

        // Uniamo nome e cognome esattamente come li salviamo nel database
        val nomeCompleto = "$nome $cognome"

        // Eseguiamo una "Composite Query" per cercare corrispondenze esatte su 2 campi
        db.collection("Atlete")
            .whereEqualTo("nome", nomeCompleto)
            .whereEqualTo("dataNascita", dataNascita)
            .whereEqualTo("idAllenatrice", idAllenatriceAttuale)
            .get()
            .addOnSuccessListener { querySnapshot ->

                val emailPulita = emailAtleta.trim().lowercase()

                if (querySnapshot != null && !querySnapshot.isEmpty) {

                    // L'ATLETA ESISTE GIÀ
                    // Recuperiamo il documento esistente (prendiamo il primo risultato trovato)
                    val documentoEsistente = querySnapshot.documents[0]
                    val idAtletaEsistente = documentoEsistente.id

                    // Prepariamo la mappa dei dati da aggiornare
                    val aggiornamenti = mutableMapOf<String, Any>(
                        "idAllenatrice" to idAllenatriceAttuale
                    )

                    // Se la nuova allenatrice ha inserito un'email, approfittiamo per aggiornare il profilo
                    if (emailPulita.isNotEmpty()) {
                        aggiornamenti["email"] = emailPulita
                    }

                    // Aggiorniamo la scheda senza creare duplicati
                    db.collection("Atlete").document(idAtletaEsistente)
                        .update(aggiornamenti)
                        .addOnSuccessListener {
                            _toastMessage.value = "$nomeCompleto era già registrata ed è stata trasferita nel tuo gruppo!"
                        }
                        .addOnFailureListener { e ->
                            _toastMessage.value = "Errore nel trasferimento: ${e.message}"
                        }
                } else {
                    // L'ATLETA NON ESISTE
                    creaNuovoDocumentoAtleta(nome, cognome, livello, dataNascita, dataCertificato, emailPulita, idAllenatriceAttuale)
                }
            }
            .addOnFailureListener { e ->
                _toastMessage.value = "Errore durante il controllo del database: ${e.message}"
            }
    }

    // Funzione helper privata per creare fisicamente un nuovo atleta sul DB
    private fun creaNuovoDocumentoAtleta(
        nome: String,
        cognome: String,
        livello: String,
        dataNascita: String,
        dataCertificato: String,
        emailAtleta: String,
        idAllenatriceAttuale: String
    ) {
        val cat = CategoriaUtils.calcolaCategoriaDaStringa(dataNascita)
        val idDocumento = db.collection("Atlete").document().id

        val nuovaAtleta = hashMapOf(
            "id" to idDocumento,
            "nome" to "$nome $cognome",
            "categoria" to cat,
            "livello" to livello,
            "dataNascita" to dataNascita,
            "scadenzaCertificato" to dataCertificato,
            "idAllenatrice" to idAllenatriceAttuale,
            "email" to emailAtleta,
            "linkMusica" to ""
        )

        // Aggiungiamo l'atleta al database
        db.collection("Atlete").document(idDocumento).set(nuovaAtleta)
            .addOnSuccessListener { _toastMessage.value = "Ginnasta iscritta con successo!" }
            .addOnFailureListener { e -> _toastMessage.value = "Errore: ${e.message}" }
    }

    // Quando il Fragment muore, il ViewModel pulisce il database da solo
    override fun onCleared() {
        super.onCleared()
        atleteListener?.remove()
    }
}