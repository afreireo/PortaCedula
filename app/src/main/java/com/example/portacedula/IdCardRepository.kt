package com.example.portacedula

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("idcard_prefs")

class IdCardRepository(private val context: Context) {
    private val KEY_CARDS = stringPreferencesKey("cards_list")

    val cardsFlow = context.dataStore.data.map { p ->
        val json = p[KEY_CARDS] ?: "[]"
        try {
            Json.decodeFromString<List<IdCard>>(json)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun saveCards(cards: List<IdCard>) {
        context.dataStore.edit { it[KEY_CARDS] = Json.encodeToString(cards) }
    }

    suspend fun addCard(card: IdCard) {
        context.dataStore.edit { p ->
            val current = (Json.decodeFromString<List<IdCard>>(p[KEY_CARDS] ?: "[]")).toMutableList()
            current.add(card)
            p[KEY_CARDS] = Json.encodeToString(current)
        }
    }
}
