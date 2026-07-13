package com.example.ginnasticaritmica

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

// Estendendo ViewModel(), diciamo ad Android che questa classe deve "sopravvivere"
// alla distruzione del Fragment (es. Quando si ruota il telefono o si cambia pagina).
class CoachViewModel : ViewModel() {

    // Usiamo MutableLiveData per poter inserire nuovi valori.
    // Quando il valore cambia, il file XML si aggiorna da solo
    val coachName = MutableLiveData<String>()
    val atleteIscritte = MutableLiveData(0)
    val gareProgrammate = MutableLiveData(0)
    val masterclassAttive = MutableLiveData(0)

    // Lista reale che conterrà le atlete scaricate dal Cloud
    val atleteList = MutableLiveData<List<Atleta>>(emptyList())

    // Istanze di Firebase (Auth per l'utente, Firestore per il database)
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()


    init {
        // Il blocco init viene eseguito una vola solA quando il ViewModel viene creato.
        caricaDatiAllenatrice()
    }

    private fun caricaDatiAllenatrice() {
        // Se non c'è un utente loggato, la funzione si ferma qui (return) per evitare crash.
        val utenteCorrente = auth.currentUser ?: return
        val uid = utenteCorrente.uid

        //Se l'utente si è registrato con Google, il nome potrebbe essere già dentro l'Auth.
        if (!utenteCorrente.displayName.isNullOrEmpty()) {
            coachName.value = utenteCorrente.displayName
        } else {
            //Se il nome non è nell'Auth andiamo a cercarlo nel documento dell'Allenatrice su Firestore.
            db.collection("Allenatrici").document(uid).get()
                .addOnSuccessListener { documento ->
                    if (documento.exists()) {
                        coachName.value = documento.getString("nome") ?: "Allenatrice"
                    } else {
                        //Se il documento non c'è, mettiamo un nome generico di sicurezza
                        coachName.value = "Allenatrice"
                    }
                }
                .addOnFailureListener {
                    //Anche in caso di mancanza di connessione internet, evitiamo che il campo resti vuoto
                    coachName.value = "Allenatrice"
                }
        }

        //Qui usiamo .get() invece di .addSnapshotListener().
        // Questo fa una singola "fotografia" al database per inizializzare i valori della dashboard
        db.collection("Atlete")
            // Isolamento dei dati: chiedo solo le atlete di QUESTA allenatrice
            .whereEqualTo("idAllenatrice", uid)
            .get()
            .addOnSuccessListener { snapshot ->
                // Aggiorna il numero totale di atleti della Dashboard
                atleteIscritte.value = snapshot.size()

                //trasformiamo i JSON di Firebase in veri oggetti Kotlin
                val listaVereAtlete = mutableListOf<Atleta>()
                for (doc in snapshot.documents) {
                    val atleta = doc.toObject(Atleta::class.java)
                    if (atleta != null) {
                        listaVereAtlete.add(atleta)
                    }
                }
                // Scrivendo nel LiveData,attiviamo l'aggiornamento automatico della UI
                atleteList.value = listaVereAtlete
            }

        //mettiamo a 0 le sezioni non ancora implementate per evitare valori "null" sulla grafica.
        gareProgrammate.value = 0
        masterclassAttive.value = 0
    }
}