package com.novastream.app.data.provider

import android.content.Context
import com.novastream.app.data.scraper.InternationalSiteProfiles
import com.novastream.app.data.scraper.SiteProfile

class WiflixProvider(appContext: Context? = null) :
    ConfigurableSiteProvider(InternationalSiteProfiles.wiflix, appContext, "serie")

class Cuevana3Provider(appContext: Context? = null) :
    ConfigurableSiteProvider(InternationalSiteProfiles.cuevana3, appContext, "pelicula")

class FrenchStreamProvider(appContext: Context? = null) :
    ConfigurableSiteProvider(InternationalSiteProfiles.frenchStream, appContext, "s-tv")

/** @deprecated Use [ConfigurableSiteProvider] directly — kept for subclass compatibility. */
open class DynamicUrlSiteProvider(
    profile: SiteProfile,
    appContext: Context?,
    contentNeedle: String
) : ConfigurableSiteProvider(profile, appContext, contentNeedle)
