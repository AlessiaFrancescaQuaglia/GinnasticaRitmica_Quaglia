package com.example.ginnasticaritmica

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.util.Calendar

class GestioneAtleteViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // MAGIA MVVM: Il listener è gestito qui dentro!
    private var atleteListener: ListenerRegistration? = null

    // LIVEDATA: Questi "tubi" inviano i dati aggiornati al Fragment in automatico
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
                    _atleteList.value = lista // Aggiorniamo il tubo coi nuovi dati!
                }
            }
    }

    // Sostituisci la vecchia funzione con questa (aggiunto il parametro emailAtleta)
    fun aggiungiAtleta(nome: String, cognome: String, livello: String, dataNascita: String, dataCertificato: String, emailAtleta: String) {
        val idAllenatriceAttuale = auth.currentUser?.uid ?: return

        val cat = calcolaCategoriaDaData(dataNascita)
        val idDocumento = db.collection("Atlete").document().id

        val nuovaAtleta = hashMapOf(
            "id" to idDocumento,
            "nome" to "$nome $cognome",
            "categoria" to cat,
            "livello" to livello,
            "dataNascita" to dataNascita,
            "scadenzaCertificato" to dataCertificato,
            "idAllenatrice" to idAllenatriceAttuale,
            "email" to emailAtleta, // <-- SALVIAMO L'EMAIL QUI!
            "linkMusica" to ""      // Campo vuoto pronto per essere aggiornato dall'allenatrice
        )

        db.collection("Atlete").document(idDocumento).set(nuovaAtleta)
            .addOnSuccessListener { _toastMessage.value = "Ginnasta iscritta con successo!" }
            .addOnFailureListener { e -> _toastMessage.value = "Errore: ${e.message}" }
    }

    private fun calcolaCategoriaDaData(dataStr: String): String {
        val parts = dataStr.split("-")
        val annoNascita = parts[0].toInt()
        val annoCorrente = Calendar.getInstance().get(Calendar.YEAR)
        val eta = annoCorrente - annoNascita
        return when {
            eta in 8..9 -> "Allieve 1"
            eta == 10 -> "Allieve 2"
            eta == 11 -> "Allieve 3"
            eta == 12 -> "Allieve 4"
            eta == 13 -> "Allieve 5 – Prejunior"
            eta == 14 -> "Junior 1"
            eta == 15 -> "Junior 2"
            eta == 16 -> "Senior 1"
            eta in 17..18 -> "Senior 2"
            else -> "Senior 3"
        }
    }

    // Quando il Fragment muore, il ViewModel pulisce il database da solo!
    override fun onCleared() {
        super.onCleared()
        atleteListener?.remove()
    }
}