# NovaStream

<p align="center">
  <strong>Ein moderner Android Streaming-Client für Serien</strong>
</p>

---

## Features

- **Modernes UI** – Dark Theme mit Gradienten und flüssigen Animationen
- **Continue Watching** – Setzt Wiedergabe genau dort fort, wo du aufgehört hast
- **Watchlist** – Speichere Serien die du schauen möchtest
- **Multi-Provider** – 9 Quellen: SerienStream (.to / .cx), AniWorld, KinoGer, Burning Series, MegaKino, StreamKiste, FilmPalast, KinoZ
- **Massiv ausgebaute Scraper** – strukturierte Home-Sektionen, Genres, Neue Episoden, Beliebte, Katalog, Metadata (Jahr, Genres, Rating, Backdrop)
- **Multi-Hoster Support** – VOE, Streamtape, FileMoon, Vidoza, Doodstream, Mixdrop, FireStream, Vidara, Vinovo, Playmate, Vidmoly, Upstream, …
- **DNS-over-HTTPS** – Umgeht ISP-DNS-Blockaden (Cloudflare 1.1.1.1)
- **Episoden-Thumbnails** – Vorschau-Bilder für jede Episode
- **Fortschritts-Anzeige** – Sieh welche Episoden du bereits geschaut hast
- **Auto-Play nächste Folge** – Nahtloser Übergang zur nächsten Episode
- **Recent Searches** – Zuletzt gesuchte Serien schnell wiederfinden
- **Landscape Player** – Automatische Drehung beim Abspielen
- **Settings** – Provider-Wechsel, Datenverwaltung, Stats und Info

## Provider

| Provider | Fokus | Base |
|----------|-------|------|
| SerienStream | Serien | serienstream.to |
| SerienStream CX | Serien (Mirror) | serienstream.cx |
| AniWorld | Anime | aniworld.to |
| KinoGer | Filme/Serien | kinoger.to |
| Burning Series | Serien | bs.to / burningseries.cx |
| MegaKino | Filme/Serien | megakino.ms |
| StreamKiste | Filme/Serien | stream-kiste.de |
| FilmPalast | Filme/Serien | filmpalast.to |
| KinoZ | Filme/Serien | kinoz.to |

## Tech Stack

- **Kotlin** 2.0.21
- **Jetpack Compose** (Material 3)
- **Media3 / ExoPlayer** 1.5.1 (HLS, MP4)
- **Room Database** 2.6.1 (Watch Progress, Watchlist)
- **DataStore Preferences** (Recent Searches, Provider)
- **Coil** 2.7.0 (Image Loading)
- **Retrofit + OkHttp** (Networking, DNS-over-HTTPS)
- **Jsoup** 1.18.3 (HTML Parsing)
- **Navigation Compose** 2.8.5
- **KSP** (Kotlin Symbol Processing)

## Architektur

```
app/src/main/java/com/novastream/app/
├── data/
│   ├── api/          # NetworkModule, Scrapers, API Interfaces
│   ├── db/           # Room Database, Entities, DAOs
│   ├── model/        # Series, Episode, HomeCatalog, StreamSource, …
│   ├── provider/     # StreamingProvider-Implementierungen
│   ├── prefs/        # AppSettings
│   └── repository/   # NovaStreamRepository, WatchRepository
├── ui/
│   ├── components/   # Shared Composables
│   ├── detail/       # Detail Screen + ViewModel
│   ├── home/         # Home Screen + ViewModel
│   ├── navigation/   # NavHost
│   ├── player/       # Player Screen + ViewModel
│   ├── search/       # Search Screen
│   ├── settings/     # Settings / Provider-Auswahl
│   ├── theme/        # Colors, Theme
│   └── watchlist/    # Watchlist Screen
└── util/             # HosterResolver, VoeWebViewResolver, ErrorMapper
```

## Build

```bash
# Debug APK
./gradlew :app:assembleDebug

# Release APK
./gradlew :app:assembleRelease
```

## Installation

1. APK auf Android-Gerät (7.0+) übertragen
2. "Aus unbekannten Quellen installieren" erlauben
3. App öffnen, Provider wählen und Serien streamen

## System Requirements

- Android 7.0 (API 24) oder höher
- Internetverbindung
- Codec-Unterstützung für HLS/MP4

## Disclaimer

NovaStream ist ein inoffizieller Client und nicht mit den Quellen-Websites affiliated. Die App ist nur für Bildungszwecke. Verwende sie auf eigene Verantwortung.

## License

Dieses Projekt ist Open Source unter der MIT License.
