package com.novastream.app.data.provider

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.domainDataStore by preferencesDataStore("provider_domains")

object ProviderDomainManager {

    private val domainPatterns: Map<String, List<String>> = mapOf(
        "wiflix" to listOf("https://flemmix.team/", "https://ww1.wiflix-adresses.fun/"),
        "frenchstream" to listOf("https://fs16.lol/", "https://fstream.info/"),
        "cuevana3" to listOf("https://www3.cuevana3.ai/", "https://cuevana3.io/"),
        "frembed" to listOf("https://frembed.casa/", "https://audin213.com/"),
        "megakino" to listOf(
            "https://megakino.ms", "https://megakino6.com", "https://megakino8.com",
            "https://megakino2.com", "https://megakino.how"
        ),
        "burningseries" to listOf("https://burningseries.cx", "https://bs.to")
    )

    private fun key(providerId: String) = stringPreferencesKey("domain_$providerId")

    suspend fun getResolvedBaseUrl(context: Context, providerId: String, fallback: String): String {
        val stored = context.domainDataStore.data.map { it[key(providerId)] }.first()
        if (!stored.isNullOrBlank()) return stored
        return domainPatterns[providerId]?.firstOrNull() ?: fallback
    }

    suspend fun setResolvedBaseUrl(context: Context, providerId: String, url: String) {
        context.domainDataStore.edit { it[key(providerId)] = url.trimEnd('/') }
    }

    fun alternateDomains(providerId: String): List<String> =
        domainPatterns[providerId].orEmpty()
}
