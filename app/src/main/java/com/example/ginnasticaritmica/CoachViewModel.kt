package com.example.ginnasticaritmica

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class CoachViewModel : ViewModel() {
    // I dati presi esattamente dal tuo sito web!
    val coachName = MutableLiveData("Giorgia Neri")
    val atleteIscritte = MutableLiveData(7)
    val gareProgrammate = MutableLiveData(2)
    val masterclassAttive = MutableLiveData(2)
    // NUOVO: Creiamo una lista di atlete osservabile (LiveData)
    val atleteList = MutableLiveData<List<Atleta>>(
        listOf(
            Atleta("Sofia Rossi", "Gold"),
            Atleta("Martina Bianchi", "Silver LE"),
            Atleta("Giulia Verdi", "Silver LD"),
            Atleta("Chiara Neri", "LC"),
            Atleta("Elena Gialli", "LB")
        )
    )
}