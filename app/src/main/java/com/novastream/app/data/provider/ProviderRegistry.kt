package com.novastream.app.data.provider

import android.content.Context

/**
 * Central registry for all streaming providers with language tags and capability metadata.
 * Dynamic-URL providers receive [Context] via [initialize] from Hilt [com.novastream.app.di.ProviderModule].
 */
object ProviderRegistry {

    @Volatile
    private var entriesCache: List<RegisteredProvider>? = null

    /** Called once at app startup from Hilt with @ApplicationContext. */
    fun initialize(context: Context) {
        if (entriesCache == null) {
            synchronized(this) {
                if (entriesCache == null) {
                    entriesCache = buildRegistry(context.applicationContext)
                }
            }
        }
    }

    private val builtInEntries: List<RegisteredProvider>
        get() = entriesCache ?: buildRegistry(appContext = null).also { entriesCache = it }

    private fun allEntries(): List<RegisteredProvider> =
        builtInEntries + SiteProfileImporter.registeredProviders()

    val providers: List<StreamingProvider> get() = allEntries().map { it.provider }

    val defaultProvider: StreamingProvider get() = builtInEntries.first().provider

    fun allRegistered(): List<RegisteredProvider> = allEntries()

    fun findRegistered(id: String): RegisteredProvider? =
        allEntries().find { it.provider.id == id }

    fun getProvider(id: String): StreamingProvider =
        findRegistered(id)?.provider ?: defaultProvider

    fun getProviderOrNull(id: String): StreamingProvider? =
        findRegistered(id)?.provider

    fun isValidProviderId(id: String): Boolean =
        allEntries().any { it.provider.id == id }

    fun getGroupedByLanguage(): Map<ContentLanguage, List<ProviderInfo>> =
        allEntries()
            .groupBy { it.contentLanguage }
            .mapValues { (_, list) -> list.map { it.toProviderInfo() } }
            .toSortedMap(compareBy { it.tag })

    fun getFiltered(
        language: ContentLanguage? = null,
        favoriteIds: Set<String> = emptySet(),
        favoritesOnly: Boolean = false
    ): List<ProviderInfo> {
        var list = allEntries()
        if (favoritesOnly) {
            list = list.filter { it.provider.id in favoriteIds }
        } else if (language != null && language != ContentLanguage.MULTI) {
            list = list.filter { it.contentLanguage == language || it.contentLanguage == ContentLanguage.MULTI }
        }
        return list
            .sortedWith(
                compareByDescending<RegisteredProvider> { it.provider.id in favoriteIds }
                    .thenBy { it.provider.displayName.lowercase() }
            )
            .map { it.toProviderInfo() }
    }

    fun getProviderInfos(): List<ProviderInfo> = allEntries().map { it.toProviderInfo() }

    fun contentLanguageOf(providerId: String): ContentLanguage =
        findRegistered(providerId)?.contentLanguage ?: ContentLanguage.MULTI

    private fun RegisteredProvider.toProviderInfo(): ProviderInfo = ProviderInfo(
        id = provider.id,
        displayName = provider.displayName,
        baseUrl = provider.baseUrl,
        supportsSeries = support.series,
        supportsMovies = support.movies,
        catalogHint = provider.catalogHint,
        capabilities = support.capabilities,
        contentLanguage = contentLanguage,
        regionLabel = regionLabel,
        logoUrl = logoUrl
    )

    @Suppress("LongMethod")
    private fun buildRegistry(appContext: Context?): List<RegisteredProvider> {
        val ctx = appContext
        return listOf(
            reg(SerienStreamProvider(), ContentLanguage.DE, movies = false, cap = capsSs()),
            reg(SerienStreamCxProvider(), ContentLanguage.DE, movies = false, cap = capsSs()),
            reg(AniWorldProvider(), ContentLanguage.DE, movies = false, cap = capsAniworld()),
            reg(KinoGerProvider(), ContentLanguage.DE, movies = true, cap = capsKinoger()),
            reg(BurningSeriesProvider(), ContentLanguage.DE, movies = false, cap = capsBs()),
            reg(MegaKinoProvider(), ContentLanguage.DE, movies = true, cap = capsMegakino()),
            reg(StreamKisteProvider(), ContentLanguage.DE, movies = true, cap = capsStreamkiste()),
            reg(FilmPalastProvider(), ContentLanguage.DE, movies = true, cap = capsFilmpalast()),
            reg(KinoZProvider(), ContentLanguage.DE, movies = true, cap = capsKinoz()),
            reg(HdFilmeProvider(), ContentLanguage.DE, movies = true, cap = capsFmhy()),
            reg(EinschaltenProvider(), ContentLanguage.DE, movies = true, series = false, cap = capsFmhy()),
            reg(FreeCatalogProvider(), ContentLanguage.MULTI, movies = false, cap = capsFree(), region = "Global"),
            reg(FreeCatalogBrowseProvider(), ContentLanguage.MULTI, movies = false, cap = capsFreeBrowse(), region = "Free Browse"),
            reg(HydraHdProvider(), ContentLanguage.EN, movies = true, cap = capsFmhy()),
            reg(CinezoProvider(), ContentLanguage.EN, movies = true, cap = capsFmhy()),
            reg(ShowsStProvider(), ContentLanguage.EN, movies = true, cap = capsFmhy()),
            reg(PhantomFlixProvider(), ContentLanguage.EN, movies = true, cap = capsFmhy()),
            reg(FlixerProvider(), ContentLanguage.EN, movies = true, cap = capsFmhy()),
            reg(SflixProvider(), ContentLanguage.EN, movies = true, cap = capsFmhy()),
            reg(RidomoviesProvider(), ContentLanguage.EN, movies = true, cap = capsFmhy()),
            reg(AnikotoProvider(), ContentLanguage.EN, movies = true, cap = capsFmhy()),
            reg(DramaCoolProvider(), ContentLanguage.EN, movies = true, cap = capsFmhy()),
            reg(PressPlayProvider(), ContentLanguage.EN, movies = true, cap = capsFmhy()),
            reg(WiflixProvider(ctx), ContentLanguage.FR, movies = true, cap = capsFmhy()),
            reg(FrenchStreamProvider(ctx), ContentLanguage.FR, movies = true, cap = capsFmhy()),
            reg(FrenchAnimeProvider(), ContentLanguage.FR, movies = true, cap = capsFmhy()),
            reg(FrembedProvider(), ContentLanguage.FR, movies = true, cap = capsFmhy()),
            reg(FanpelisProvider(), ContentLanguage.ES, movies = true, cap = capsFmhy()),
            reg(AnimeFlvProvider(), ContentLanguage.ES, movies = false, cap = capsFmhy()),
            reg(JkAnimeProvider(), ContentLanguage.ES, movies = true, cap = capsFmhy()),
            reg(PelisplustoProvider(), ContentLanguage.ES, movies = true, cap = capsFmhy()),
            reg(DoramasflixProvider(), ContentLanguage.ES, movies = true, cap = capsFmhy()),
            reg(GuardaSerieProvider(), ContentLanguage.IT, movies = false, cap = capsFmhy()),
            reg(Cb01Provider(), ContentLanguage.IT, movies = true, cap = capsFmhy()),
            reg(Altadefinizione01Provider(), ContentLanguage.IT, movies = true, cap = capsFmhy()),
            reg(AnimeUnityProvider(), ContentLanguage.IT, movies = true, cap = capsFmhy()),
            reg(StreamingCommunityItProvider(), ContentLanguage.IT, movies = true, cap = capsFmhy()),
            reg(StreamingCommunityEnProvider(), ContentLanguage.EN, movies = true, cap = capsFmhy()),
            reg(FilmyOnlineProvider(), ContentLanguage.PL, movies = true, cap = capsFmhy()),
            reg(ZaluknijProvider(), ContentLanguage.PL, movies = true, cap = capsFmhy()),
            reg(MkissaProvider(), ContentLanguage.EN, movies = true, cap = capsFmhy()),
            reg(Lookmovie2Provider(), ContentLanguage.EN, movies = true, cap = capsFmhy()),
            reg(Soap2dayProvider(), ContentLanguage.EN, movies = true, cap = capsFmhy()),
            reg(MkvMoviesProvider(), ContentLanguage.EN, movies = true, cap = capsFmhy()),
            reg(MoflixProvider(), ContentLanguage.DE, movies = true, cap = capsFmhy()),
            reg(Cuevana3Provider(ctx), ContentLanguage.ES, movies = true, cap = capsFmhy()),
            reg(PelisflixProvider(), ContentLanguage.ES, movies = true, cap = capsFmhy()),
            reg(AnimeworldProvider(), ContentLanguage.IT, movies = true, cap = capsFmhy()),
            reg(FilmpertuttiProvider(), ContentLanguage.IT, movies = true, cap = capsFmhy()),
            reg(Cineblog01Provider(), ContentLanguage.IT, movies = true, cap = capsFmhy()),
            reg(VoirfilmsProvider(), ContentLanguage.FR, movies = true, cap = capsFmhy()),
            reg(NekoSamaProvider(), ContentLanguage.FR, movies = true, cap = capsFmhy()),
            reg(HiAnimeProvider(), ContentLanguage.EN, movies = true, cap = capsFmhy()),
            reg(AnimeFenixProvider(), ContentLanguage.ES, movies = true, cap = capsFmhy()),
            reg(TioAnimeProvider(), ContentLanguage.ES, movies = true, cap = capsFmhy()),
            reg(SeriesFlixProvider(), ContentLanguage.ES, movies = true, cap = capsFmhy()),
            reg(AnyMovieProvider(), ContentLanguage.EN, movies = true, cap = capsFmhy()),
            reg(FlixLatamProvider(), ContentLanguage.ES, movies = true, cap = capsFmhy()),
            reg(OtakuFrProvider(), ContentLanguage.FR, movies = true, cap = capsFmhy()),
            reg(LatAnimeProvider(), ContentLanguage.ES, movies = true, cap = capsFmhy()),
            reg(GuardaFlixProvider(), ContentLanguage.IT, movies = true, cap = capsFmhy()),
            reg(CineCalidadProvider(), ContentLanguage.ES, movies = true, series = false, cap = capsFmhy())
        )
    }

    private fun reg(
        provider: StreamingProvider,
        lang: ContentLanguage,
        movies: Boolean,
        series: Boolean = true,
        cap: ProviderCapabilities = ProviderCapabilities(),
        region: String? = null
    ) = RegisteredProvider(
        provider = provider,
        support = ProviderSupport(movies = movies, series = series, capabilities = cap),
        contentLanguage = lang,
        regionLabel = region ?: defaultRegion(lang),
        logoUrl = faviconFor(provider.baseUrl)
    )

    private fun defaultRegion(lang: ContentLanguage): String = when (lang) {
        ContentLanguage.DE -> "Germany"
        ContentLanguage.EN -> "International"
        ContentLanguage.FR -> "France"
        ContentLanguage.ES -> "Spain / LATAM"
        ContentLanguage.IT -> "Italy"
        ContentLanguage.PL -> "Poland"
        ContentLanguage.AR -> "Arab World"
        ContentLanguage.MULTI -> "Global"
    }

    private fun faviconFor(baseUrl: String): String? {
        val host = try {
            java.net.URI(baseUrl).host
        } catch (_: Exception) {
            null
        }
        return host?.let { "https://www.google.com/s2/favicons?domain=$it&sz=128" }
    }

    private fun capsSs() = ProviderCapabilities(true, true, true)
    private fun capsAniworld() = ProviderCapabilities(true, true, false)
    private fun capsKinoger() = ProviderCapabilities(true, false, true, "/stream/")
    private fun capsBs() = ProviderCapabilities(true, false, false)
    private fun capsMegakino() = ProviderCapabilities(true, true, false, "/filme")
    private fun capsStreamkiste() = ProviderCapabilities(true, true, false, "/filme")
    private fun capsFilmpalast() = ProviderCapabilities(true, false, true, "/movies/new")
    private fun capsKinoz() = ProviderCapabilities(true, false, true, "/Stream/")
    private fun capsFree() = ProviderCapabilities(true, true, false)
    private fun capsFreeBrowse() = ProviderCapabilities(true, false, false)
    private fun capsFmhy() = ProviderCapabilities(true, false, true, "/movie")
}
