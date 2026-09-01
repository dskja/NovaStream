# NovaStream

<p align="center">
  <strong>High-End Android Streaming Client · 51 Providers · Global Edition · v10.0</strong>
</p>

---

## Highlights (v10.0)

| Feature | v9 | v10 |
|---------|:--:|:---:|
| Hilt factory for dynamic-URL providers (Wiflix, FrenchStream, Cuevana3) | — | ✓ |
| ProviderDomainManager alternate domains (28 unstable sites) | partial | ✓ |
| Provider Marketplace logos + region labels | — | ✓ |
| Content-language-aware latest episodes (TVMaze region) | — | ✓ |
| Genre hubs per ContentLanguage on Home/Browse | — | ✓ |
| Nightly CI smoke for all 51 providers × language tag | — | ✓ |

### v10 provider hardening

- **Hilt `@ApplicationContext`** — `ProviderRegistry.initialize()` from `ProviderModule` so dynamic-URL providers resolve domains via DataStore.
- **Domain mirrors** — `ProviderDomainManager` tracks alternate base URLs for Wiflix, FrenchStream, Cuevana3, SFlix, DramaCool, and 20+ other unstable international sites.
- **Marketplace polish** — every provider has `regionLabel` + favicon `logoUrl`; Coil renders logos in Provider Marketplace.
- **Global Home** — latest-episode row uses `loadNewest()` for all providers; Free Catalog respects `AppSettings.contentLanguage` TVMaze region; genre chips fall back to per-language defaults when a scraper has no native genres.
- **CI** — `scripts/provider-smoke.ps1` covers all 51 providers; `.github/workflows/provider-smoke-nightly.yml` runs daily at 03:00 UTC.

## Highlights (v9.0)

| Feature | v8 | v9 |
|---------|:--:|:--:|
| 51 streaming providers (DE/EN/FR/ES/IT/PL) | 17 | ✓ |
| Provider Marketplace + language filters | — | ✓ |
| Global Search across providers | — | ✓ |
| Universal HTML scraper + international SiteProfiles | partial | ✓ |
| Content language setting + provider grouping | — | ✓ |
| Provider health monitor + cooldown | partial | ✓ |

---

## Highlights (v8.0)

| Feature | v7 | v8 |
|---------|:--:|:--:|
| Parallel AJAX path probing (`async`/`select`) | — | ✓ |
| Burning Series captcha session reuse | — | ✓ |
| Provider health banner (slow load / errors) | — | ✓ |
| Performance mode (fewer parallel home loads) | — | ✓ |
| Provider switch confirmation dialog | — | ✓ |
| POST_NOTIFICATIONS before playback FGS | — | ✓ |
| Reduce motion disables shimmer placeholders | partial | ✓ |
| Request coalescing for home catalog | — | ✓ |
| Provider matrix with avg load times | — | ✓ |
| 40+ unit tests | partial | ✓ |

### v8 performance

NovaStream v8 focuses on perceived speed and resilience:

- **Parallel search paths** — `AjaxSearchClient` probes AJAX and HTML endpoints concurrently and uses the first successful response.
- **Request coalescing** — duplicate in-flight home catalog requests share one network round-trip via `RequestCoalescer`.
- **Performance mode** — Settings → Performance mode skips extended genre/latest parallel loads on Home for slower devices or VPN setups.
- **Provider health** — Home shows a banner when the last catalog load took >5s or failed; Settings shows average load time per provider.
- **Burning Series** — WebView captcha sessions are reused for catalog pagination instead of opening a fresh WebView every time.
- **Reduce motion** — disables hero auto-scroll and replaces animated shimmer skeletons with static placeholders.

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

See **Settings → Provider-Fähigkeiten** for the live matrix in the app (including average load times after v8).

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
- Tag `v10.0.0` creates a GitHub Release with APKs
- Nightly **Provider Smoke** workflow tests live reachability for all 51 providers

```bash
git tag v10.0.0
git push origin v10.0.0
```

### Provider smoke (local)

```powershell
./scripts/provider-smoke.ps1
```

## Tests (v8)

- `UniversalHtmlScraperTest` — fixture HTML for SerienStream, StreamKiste, Cinezo, Burning Series
- `AjaxSearchClientTest` — slug parsing + parallel first-success probing
- `RequestCoalescerTest` — concurrent deduplication
- `ProviderLoadMetricsTest` / `HomeViewModelLoadTest` — health banner thresholds
- `BrowseViewModelPaginationTest` — page merge + has-more logic
- `WatchRepositoryProviderScopeTest` — provider-scoped watchlist/progress (Robolectric + in-memory Room)
- `CatalogCacheEvictionTest` — TTL expiry and purge
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

## License

MIT — see [LICENSE](LICENSE).
