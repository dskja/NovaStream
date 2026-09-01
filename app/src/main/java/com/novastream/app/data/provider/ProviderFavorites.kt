package com.novastream.app.data.provider

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.favoritesDataStore by preferencesDataStore("provider_favorites")

object ProviderFavorites {
    private val FAVORITE_IDS = stringSetPreferencesKey("favorite_provider_ids")

    fun favoriteIdsFlow(context: Context): Flow<Set<String>> =
        context.favoritesDataStore.data.map { it[FAVORITE_IDS].orEmpty() }

    suspend fun toggleFavorite(context: Context, providerId: String) {
        context.favoritesDataStore.edit { prefs ->
            val current = prefs[FAVORITE_IDS].orEmpty().toMutableSet()
            if (providerId in current) current.remove(providerId) else current.add(providerId)
            prefs[FAVORITE_IDS] = current
        }
    }

    suspend fun setFavorites(context: Context, ids: Set<String>) {
        context.favoritesDataStore.edit { it[FAVORITE_IDS] = ids }
    }

    suspend fun isFavorite(context: Context, providerId: String): Boolean =
        providerId in favoriteIdsFlow(context).first()
}
