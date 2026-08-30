package com.example.aivoiceassistant

/** Satu entri riwayat percakapan: perintah pengguna + respons AI + waktu. */
data class HistoryItem(
    val commandText: String,
    val responseText: String,
    val timestamp: Long = System.currentTimeMillis()
)
