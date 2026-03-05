package com.example.portacedula

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class IdCard(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Nueva Tarjeta",
    val frontUri: String? = null,
    val backUri: String? = null,
    val isFavorite: Boolean = false
)
