package com.novastream.app.data.iptv

import com.novastream.app.data.prefs.AppSettings
import kotlinx.coroutines.flow.first

/**
 * Registry for IPTV providers (v14).
 */
object IptvRegistry {

    fun builtInProviders(): List<IptvStreamingProvider> = PlutoTvProvider.allRegions()

    suspend fun activeProviders(appSettings: AppSettings): List<IptvStreamingProvider> {
        val enabled = appSettings.iptvEnabled.first()
        if (!enabled) return emptyList()
        val list = builtInProviders().toMutableList()
        val m3uUrl = appSettings.userM3uUrl.first()
        if (m3uUrl.isNotBlank()) {
            list.add(UserM3uProvider(m3uUrl))
        }
        return list
    }

    fun findById(id: String): IptvStreamingProvider? =
        builtInProviders().find { it.id == id }
}
