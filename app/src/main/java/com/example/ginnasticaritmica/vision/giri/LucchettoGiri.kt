package com.example.ginnasticaritmica.vision.giri

/**
 * Modulo di Gestione della Concorrenza
 * Poiché le pose rotazionali condividono spesso topologie simili durante le transizioni,
 * si rischierebbe una "Race Condition": due analizzatori potrebbero rivendicare
 * la stessa rotazione, corrompendo la Macchina a Stati.
 *
 * Questo oggetto implementa un pattern "Mutex" (Mutual Exclusion): non appena un
 * analizzatore acquisisce una figura con una buona approssimazione, chiude questo "lucchetto",
 * inibendo l'esecuzione di tutte le altre classi finché il movimento non è concluso.
 */
object LucchettoGiri {

    // Il Semaforo del Mutex.
    // Memorizza l'identificativo del thread che detiene il blocco.
    // Se è null, l'interprete è Idle.
    var giroAttivo: String? = null

    /**
     * Controllo di Concorrenza
     * Restituisce true solo se il sistema è libero (null)
     * oppure se a richiederlo è lo stesso analizzatore che lo aveva bloccato.
     */
    fun possoValutare(idGiro: String): Boolean {
        return giroAttivo == null || giroAttivo == idGiro
    }

    /**
     * Acquisizione del Lock.
     * Invocato non appena una classe identifica i requisiti biometrici
     * minimi per iniziare il conteggio di un giro.
     */
    fun blocca(idGiro: String) {
        giroAttivo = idGiro
    }

    /**
     * Rilascio Sicuro del Lock.
     * Contiene un controllo di identità vitale: un analizzatore può sbloccare il Mutex
     * solo se è il proprietario. Questo previene sblocchi accidentali
     */
    fun sblocca(idGiro: String) {
        if (giroAttivo == idGiro) {
            giroAttivo = null
        }
    }
}