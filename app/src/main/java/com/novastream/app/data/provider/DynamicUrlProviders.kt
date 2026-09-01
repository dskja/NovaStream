package com.novastream.app.data.provider

import android.content.Context
import com.novastream.app.data.scraper.InternationalSiteProfiles
import com.novastream.app.data.scraper.SiteProfile
import kotlinx.coroutines.sync.withLock

class WiflixProvider(appContext: Context? = null) :
    DynamicUrlSiteProvider(InternationalSiteProfiles.wiflix, appContext, "serie")

class Cuevana3Provider(appContext: Context? = null) :
    DynamicUrlSiteProvider(InternationalSiteProfiles.cuevana3, appContext, "pelicula")

class FrenchStreamProvider(appContext: Context? = null) :
    DynamicUrlSiteProvider(InternationalSiteProfiles.frenchStream, appContext, "s-tv")

open class DynamicUrlSiteProvider(
    profile: SiteProfile,
    private val appContext: Context?,
    private val contentNeedle: String
) : ConfigurableSiteProvider(profile), DynamicBaseUrlProvider {

    override val defaultBaseUrl: String = profile.baseUrl

    override val baseUrl: String
        get() = _resolvedBase ?: defaultBaseUrl

    @Volatile
    private var _resolvedBase: String? = null

    override suspend fun resolveBaseUrl(forceRefresh: Boolean): String = changeUrlMutex.withLock {
        if (!forceRefresh && _resolvedBase != null) return _resolvedBase!!
        val ctx = appContext
        val resolved = if (ctx != null) {
            ProviderDomainManager.getResolvedBaseUrl(ctx, id, defaultBaseUrl)
        } else {
            ProviderHttp.resolveWorkingBase(
                ProviderDomainManager.alternateDomains(id).ifEmpty { listOf(defaultBaseUrl) },
                contentNeedle = contentNeedle,
                webViewFallback = true
            ) ?: defaultBaseUrl
        }
        _resolvedBase = resolved.trimEnd('/')
        _resolvedBase!!
    }
}
