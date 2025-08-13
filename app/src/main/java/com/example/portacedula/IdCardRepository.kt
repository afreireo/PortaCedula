package com.example.portacedula

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("idcard_prefs")

class IdCardRepository(private val context: Context) {
    private val KEY_FRONT = stringPreferencesKey("front_uri")
    private val KEY_BACK  = stringPreferencesKey("back_uri")

    val cardFlow = context.dataStore.data.map { p ->
        IdCard(
            p[KEY_FRONT].takeUnless { it.isNullOrBlank() },
            p[KEY_BACK].takeUnless { it.isNullOrBlank() }
        )
    }

    suspend fun setFront(uri: String?) { context.dataStore.edit { it[KEY_FRONT] = uri.orEmpty() } }
    suspend fun setBack(uri: String?)  { context.dataStore.edit { it[KEY_BACK]  = uri.orEmpty() } }
}
