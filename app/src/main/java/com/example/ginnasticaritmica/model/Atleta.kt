package com.example.ginnasticaritmica.model

data class Atleta(
    var id: String = "",
    var nome: String = "",
    var categoria: String = "",
    var livello: String = "",
    var telefonoContatto: String = "",
    var dataNascita: String = "",
    var attrezziUsati: List<String> = emptyList(),
    var scadenzaCertificato: String = "",
    var tracceMusicali: Map<String, String> = emptyMap(),
    var fotoUrl: String = "",
    var idAllenatrice: String = "",
    var datePresenze: List<String> = emptyList(),
    var dateAssenze: List<String> = emptyList(),
    var gruppo: String = ""
)