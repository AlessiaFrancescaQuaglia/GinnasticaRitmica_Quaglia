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

    //funzione per aggiungere un nuovo atleta
    fun aggiungiAtleta(nome: String, cognome: String, livello: String, dataNascita: String, dataCertificato: String, emailAtleta: String) {
        val idAllenatriceAttuale = auth.currentUser?.uid ?: return

        // --- MAGIA SRP: Richiamiamo la logica centralizzata dal file Utils ---
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

