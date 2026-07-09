package com.example.ginnasticaritmica

data class Gara(
    var id: String = "",
    var nome: String = "",
    var data: String = "",
    var luogo: String = "",

    // Le liste che faranno da "Filtro" per le iscrizioni!
    var livelliAmmessi: List<String> = emptyList(),
    var categorieAmmesse: List<String> = emptyList(),
    var attrezziAmmessi: List<String> = emptyList(),

    // Qui dentro salveremo "Chi" si iscrive e con "Quali" attrezzi
    var atleteIscritte: MutableMap<String, List<String>> = mutableMapOf()
)