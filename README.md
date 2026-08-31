# NovaStream

<p align="center">
  <strong>High-End Android Streaming Client · Multi-Provider · Free Metadata · Auto-Updates</strong>
</p>

---

## Highlights (v4.0)

- **17 Provider** – DE-Klassiker + FMHY-Quellen + Free Catalog
- **Universal Html Scraper** – profilbasiert für alle Sites, nicht nur SerienStream
- **Free Catalog (TVMaze)** – TMDb-Alternative **ohne API-Key**, inkl. Cast, Rating, Episoden
- **Embed-Player-Stack** – VidSrc / 2Embed / VidLink / VidLove (IMDb/TMDb)
- **GitHub Update-Checker** – erkennt neue Releases und APK-Downloads
- **Metadata Enrichment** – Detail-Screen mit Genres, Cast, Network, IMDb
- Continue Watching, Watchlist, Multi-Hoster, DNS-over-HTTPS, Autoplay

## Provider

| Provider | Typ |
|----------|-----|
| SerienStream / CX | Serien (DE) |
| AniWorld | Anime |
| KinoGer | Filme/Serien |
| Burning Series | Serien |
| MegaKino | Filme/Serien |
| StreamKiste | Filme/Serien |
| FilmPalast | Filme/Serien |
| KinoZ | Filme/Serien |
| **Free Catalog (TVMaze)** | Globaler Katalog + Embeds |
| HydraHD | FMHY |
| Cinezo | FMHY / TMDb-IDs |
| Shows.st | FMHY |
| PhantomFlix | FMHY |
| Flixer | FMHY |
| DramaCool | Asian Drama |
| PressPlay | FMHY |

## Architektur

```
data/
  api/          NovaStreamScraper, NetworkModule
  scraper/      UniversalHtmlScraper + SiteProfiles (alle Provider)
  meta/         FreeMetaService (TVMaze, kein Key)
  provider/     17 StreamingProvider-Implementierungen
  repository/   NovaStreamRepository
util/
  HosterResolver, EmbedStreamResolver, UpdateChecker
ui/
  home, detail (+ Meta), settings (+ Updates), player, search, watchlist
```

## Build

```bash
# Debug APK
./gradlew :app:assembleDebug

# Release APK
./gradlew :app:assembleRelease
```

### GitHub Actions

Bei Push auf `main`, Pull Requests und manuell unter **Actions → Build APK** wird automatisch gebaut.

- Artifacts: `NovaStream-debug-apk`, `NovaStream-release-apk`
- Tag `v*` (z.B. `v4.0.1`) erstellt zusätzlich ein GitHub Release mit APKs

```bash
git tag v4.0.1
git push origin v4.0.1
```

## Updates

Die App prüft automatisch `https://github.com/dskja/NovaStream/releases/latest`.
Unter Settings → App-Updates kannst du manuell prüfen und die APK öffnen.

## Requirements

- Android 7.0+ (API 24)
- Internet
- HLS/MP4 Codec-Support

## Disclaimer

Inoffizieller Client zu Bildungszwecken. Nutzung auf eigene Verantwortung.
Quellen-Websites sind nicht affiliated.

## License

MIT
