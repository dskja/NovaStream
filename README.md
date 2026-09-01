# NovaStream

<p align="center">
  <strong>High-End Android Streaming Client · Multi-Provider · Hilt DI · i18n · v7.0</strong>
</p>

---

## Highlights (v7.0)

| Feature | v6 | v7 |
|---------|:--:|:--:|
| Hilt dependency injection | — | ✓ |
| `@HiltViewModel` (Home, Browse, Player, Detail) | — | ✓ |
| German + English string resources | — | ✓ |
| Accessibility (section headings, merged poster semantics) | — | ✓ |
| Reduce motion (disables hero auto-scroll) | — | ✓ |
| Provider capability matrix in Settings | — | ✓ |
| Unit tests (scraper fixtures, capabilities, browse filters) | partial | ✓ |
| 17 streaming providers | ✓ | ✓ |
| Universal HTML scraper + site profiles | ✓ | ✓ |
| Continue Watching / Watchlist / Room | ✓ | ✓ |
| Multi-hoster player + DNS-over-HTTPS | ✓ | ✓ |
| GitHub update checker | ✓ | ✓ |

### v7 architecture

```
di/
  DatabaseModule.kt    Room singleton
  RepositoryModule.kt  NovaStreamRepository, WatchRepository
  ProviderModule.kt    AppSettings, provider list
ui/
  *ViewModel.kt        @HiltViewModel + constructor injection
res/
  values/strings.xml       German (default)
  values-en/strings.xml    English
```

## Provider capability matrix

| Provider | Movies | Pagination | Latest episodes |
|----------|:------:|:----------:|:---------------:|
| SerienStream / CX | — | ✓ | ✓ |
| AniWorld | — | ✓ | ✓ |
| KinoGer | ✓ | ✓ | — |
| Burning Series | — | ✓ | — |
| MegaKino | ✓ | ✓ | ✓ |
| StreamKiste | ✓ | ✓ | ✓ |
| FilmPalast | ✓ | ✓ | — |
| KinoZ | ✓ | ✓ | — |
| Free Catalog | ✓ | ✓ | ✓ |
| FMHY sites (Cinezo, HydraHD, …) | varies | ✓ | — |

See **Settings → Provider-Fähigkeiten** for the live matrix in the app.

## Build

```bash
# Debug APK
./gradlew :app:assembleDebug

# Release APK
./gradlew :app:assembleRelease

# Unit tests
./gradlew :app:testDebugUnitTest
```

### GitHub Actions

Push to `main`, pull requests, and manual **Actions → Build APK** runs CI.

- Artifacts: `NovaStream-debug-apk`, `NovaStream-release-apk`
- Tag `v7.0.0` creates a GitHub Release with APKs

```bash
git tag v7.0.0
git push origin v7.0.0
```

## Tests (v7)

- `UniversalHtmlScraperTest` — fixture HTML for SerienStream, StreamKiste, Cinezo
- `ProviderCapabilitiesTest` — pagination / movies / latest flags per provider
- `BrowseViewModelTest` — content filter (all / series / movies)

Fixtures live under `app/src/test/resources/html/`.

## Updates

The app checks `https://github.com/dskja/NovaStream/releases/latest`.
Settings → App-Updates for manual checks and APK download.

## Requirements

- Android 5.1+ (API 22)
- Internet
- HLS/MP4 codec support

## Disclaimer

Inoffizieller Client zu Bildungszwecken. Nutzung auf eigene Verantwortung.
Quellen-Websites sind nicht affiliated.

## License

MIT
