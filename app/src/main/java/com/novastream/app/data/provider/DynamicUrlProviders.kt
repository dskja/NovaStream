package com.novastream.app.data.provider

import android.content.Context
import com.novastream.app.data.scraper.InternationalSiteProfiles
import com.novastream.app.data.scraper.SiteProfile
import kotlinx.coroutines.sync.Mutex
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

    override val changeUrlMutex: Mutex = Mutex()

    @Volatile
    private var _resolvedBase: String? = null

    init {
        ProviderDomainResolver.registerInvalidator(id) { _resolvedBase = null }
    }

    override suspend fun resolveBaseUrl(forceRefresh: Boolean): String = changeUrlMutex.withLock {
        if (!forceRefresh && _resolvedBase != null) return _resolvedBase!!
        val resolved = ProviderDomainResolver.resolveActiveBaseUrl(
            providerId = id,
            defaultBaseUrl = defaultBaseUrl,
            contentNeedle = contentNeedle,
            appContext = appContext,
            forceRefresh = forceRefresh
        )
        _resolvedBase = resolved
        _resolvedBase!!
    }
}
