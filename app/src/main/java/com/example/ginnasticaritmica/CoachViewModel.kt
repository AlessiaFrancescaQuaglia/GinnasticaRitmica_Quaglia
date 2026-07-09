package com.example.ginnasticaritmica

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class CoachViewModel : ViewModel() {

    // Cambiamo i dati fissi in LiveData vuoti o a 0, pronti a essere popolati
    val coachName = MutableLiveData<String>()
    val atleteIscritte = MutableLiveData<Int>(0)
    val gareProgrammate = MutableLiveData<Int>(0)
    val masterclassAttive = MutableLiveData<Int>(0)

    // Lista reale che conterrà le atlete scaricate dal Cloud
    val atleteList = MutableLiveData<List<Atleta>>(emptyList())

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    init {
        // Questa funzione parte in automatico appena ti sposti nella dashboard
        caricaDatiAllenatrice()
    }

    private fun caricaDatiAllenatrice() {
        val utenteCorrente = auth.currentUser ?: return
        val uid = utenteCorrente.uid

        // 1. CHIEDIAMO A FIREBASE IL VERO NOME DELL'ALLENATRICE
        // Se si è registrata con Google/Email, proviamo a prenderlo dal profilo Auth
        if (!utenteCorrente.displayName.isNullOrEmpty()) {
            coachName.value = utenteCorrente.displayName
        } else {
            // Altrimenti andiamo nella collezione "Allenatrici" (o come hai chiamato la cartella su Firestore)
            db.collection("Allenatrici").document(uid).get()
                .addOnSuccessListener { documento ->
                    if (documento.exists()) {
                        coachName.value = documento.getString("nome") ?: "Allenatrice"
                    } else {
                        coachName.value = "Allenatrice" // Nome di backup se non trova nulla
                    }
                }
                .addOnFailureListener {
                    coachName.value = "Allenatrice"
                }
        }

        // 2. CHIEDIAMO A FIREBASE IL NUMERO REALE DI ATLETE ISCRITTE PER QUESTA ALLENATRICE
        db.collection("Atlete")
            .whereEqualTo("idAllenatrice", uid)
            .get()
            .addOnSuccessListener { snapshot ->
                // Aggiorna il GRANDE NUMERO VIOLA della Dashboard all'istante!
                atleteIscritte.value = snapshot.size()

                // Aggiorna anche la lista delle atlete reale usata dall'app
                val listaVereAtlete = mutableListOf<Atleta>()
                for (doc in snapshot.documents) {
                    val atleta = doc.toObject(Atleta::class.java)
                    if (atleta != null) {
                        listaVereAtlete.add(atleta)
                    }
                }
                atleteList.value = listaVereAtlete
            }

        // 3. DA FARE IN FUTURO: Qui farai le query per le Gare e le Masterclass reali
        // Per ora lasciamo 0 o dei numeri temporanei se non hai ancora le tabelle sul database
        gareProgrammate.value = 0
        masterclassAttive.value = 0
    }
}