package com.example.ginnasticaritmica

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class CoachViewModel : ViewModel() {
    // I dati presi esattamente dal tuo sito web!
    val coachName = MutableLiveData("Giorgia Neri")
    val atleteIscritte = MutableLiveData(7)
    val gareProgrammate = MutableLiveData(2)
    val masterclassAttive = MutableLiveData(2)
}