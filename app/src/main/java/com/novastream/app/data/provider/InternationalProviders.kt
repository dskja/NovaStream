package com.novastream.app.data.provider

import android.content.Context
import com.novastream.app.data.scraper.InternationalSiteProfiles

class SflixProvider(appContext: Context? = null) :
    DynamicUrlSiteProvider(InternationalSiteProfiles.sflix, appContext, "/tv-show/")

class RidomoviesProvider : ConfigurableSiteProvider(InternationalSiteProfiles.ridomovies)
class AnikotoProvider : ConfigurableSiteProvider(InternationalSiteProfiles.anikoto)

class HdFilmeProvider(appContext: Context? = null) :
    DynamicUrlSiteProvider(InternationalSiteProfiles.hdfilme, appContext, "stream")
class EinschaltenProvider : ConfigurableSiteProvider(InternationalSiteProfiles.einschalten)
class FrenchAnimeProvider : ConfigurableSiteProvider(InternationalSiteProfiles.frenchAnime)
class FrembedProvider : ConfigurableSiteProvider(InternationalSiteProfiles.frembed)
class FanpelisProvider : ConfigurableSiteProvider(InternationalSiteProfiles.fanpelis)
class AnimeFlvProvider : ConfigurableSiteProvider(InternationalSiteProfiles.animeFlv)
class JkAnimeProvider : ConfigurableSiteProvider(InternationalSiteProfiles.jkAnime)
class PelisplustoProvider : ConfigurableSiteProvider(InternationalSiteProfiles.pelisplusto)
class DoramasflixProvider : ConfigurableSiteProvider(InternationalSiteProfiles.doramasflix)
class GuardaSerieProvider : ConfigurableSiteProvider(InternationalSiteProfiles.guardaSerie)
class Cb01Provider : ConfigurableSiteProvider(InternationalSiteProfiles.cb01)
class Altadefinizione01Provider : ConfigurableSiteProvider(InternationalSiteProfiles.altadefinizione01)
class AnimeUnityProvider : ConfigurableSiteProvider(InternationalSiteProfiles.animeUnity)
class StreamingCommunityItProvider : ConfigurableSiteProvider(InternationalSiteProfiles.streamingCommunityIt)
class StreamingCommunityEnProvider : ConfigurableSiteProvider(InternationalSiteProfiles.streamingCommunityEn)
class FilmyOnlineProvider : ConfigurableSiteProvider(InternationalSiteProfiles.filmyOnline)
class ZaluknijProvider : ConfigurableSiteProvider(InternationalSiteProfiles.zaluknij)
class MkissaProvider : ConfigurableSiteProvider(InternationalSiteProfiles.mkissa)
class MoflixProvider(appContext: Context? = null) :
    DynamicUrlSiteProvider(InternationalSiteProfiles.moflix, appContext, "stream/")
class AnimeworldProvider : ConfigurableSiteProvider(InternationalSiteProfiles.animeworld)
class Lookmovie2Provider : ConfigurableSiteProvider(InternationalSiteProfiles.lookmovie2)
class PelisflixProvider : ConfigurableSiteProvider(InternationalSiteProfiles.pelisflix)
class FilmpertuttiProvider : ConfigurableSiteProvider(InternationalSiteProfiles.filmpertutti)
class Cineblog01Provider : ConfigurableSiteProvider(InternationalSiteProfiles.cineblog01)
class VoirfilmsProvider : ConfigurableSiteProvider(InternationalSiteProfiles.voirfilms)
class NekoSamaProvider : ConfigurableSiteProvider(InternationalSiteProfiles.nekoSama)
class Soap2dayProvider : ConfigurableSiteProvider(InternationalSiteProfiles.soap2day)
class MkvMoviesProvider : ConfigurableSiteProvider(InternationalSiteProfiles.mkvMovies)
class HiAnimeProvider : ConfigurableSiteProvider(InternationalSiteProfiles.hianime)
class AnimeFenixProvider : ConfigurableSiteProvider(InternationalSiteProfiles.animefenix)
class TioAnimeProvider : ConfigurableSiteProvider(InternationalSiteProfiles.tioanime)
class SeriesFlixProvider : ConfigurableSiteProvider(InternationalSiteProfiles.seriesflix)
class AnyMovieProvider : ConfigurableSiteProvider(InternationalSiteProfiles.anymovie)
class FlixLatamProvider : ConfigurableSiteProvider(InternationalSiteProfiles.flixlatam)
class OtakuFrProvider : ConfigurableSiteProvider(InternationalSiteProfiles.otakufr)
class LatAnimeProvider : ConfigurableSiteProvider(InternationalSiteProfiles.latanime)
class GuardaFlixProvider : ConfigurableSiteProvider(InternationalSiteProfiles.guardaflix)
class CineCalidadProvider : ConfigurableSiteProvider(InternationalSiteProfiles.cinecalidad)
