package com.slideindex.app.search.contacts

data class ContactSearchEntry(
    val id: Long,
    val lookupKey: String,
    val name: String,
    val phoneNumber: String,
    val formattedPhone: String,
    val fullPinyin: String,
    val initialPinyin: String,
    val photoUri: String? = null,
)
