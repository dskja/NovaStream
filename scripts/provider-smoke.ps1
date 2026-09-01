$ErrorActionPreference = 'Continue'
$ua = 'Mozilla/5.0 (Linux; Android 13; NovaStream) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36'

# All 51 NovaStream v10 providers with content-language tags (de/en/fr/es/it/pl/multi)
$providers = @(
    @{ id = 'serienstream'; name = 'SerienStream'; lang = 'de'; url = 'https://serienstream.to'; needle = 'serie' },
    @{ id = 'serienstream_cx'; name = 'SerienStream CX'; lang = 'de'; url = 'https://serienstream.cx'; needle = 'serie' },
    @{ id = 'aniworld'; name = 'AniWorld'; lang = 'de'; url = 'https://aniworld.to'; needle = 'anime' },
    @{ id = 'kinoger'; name = 'KinoGer'; lang = 'de'; url = 'https://kinoger.to'; needle = 'film' },
    @{ id = 'burningseries'; name = 'Burning Series'; lang = 'de'; url = 'https://burningseries.cx'; needle = 'serie' },
    @{ id = 'megakino'; name = 'MegaKino'; lang = 'de'; url = 'https://megakino.ms'; needle = 'film' },
    @{ id = 'streamkiste'; name = 'StreamKiste'; lang = 'de'; url = 'https://stream-kiste.de/serien'; needle = 'serien' },
    @{ id = 'filmpalast'; name = 'FilmPalast'; lang = 'de'; url = 'https://filmpalast.to'; needle = 'film' },
    @{ id = 'kinoz'; name = 'KinoZ'; lang = 'de'; url = 'https://kinoz.to'; needle = 'stream' },
    @{ id = 'hdfilme'; name = 'HDFilme'; lang = 'de'; url = 'https://hdfilme.win'; needle = 'film' },
    @{ id = 'einschalten'; name = 'Einschalten'; lang = 'de'; url = 'https://einschalten.in'; needle = 'movie' },
    @{ id = 'moflix'; name = 'Moflix'; lang = 'de'; url = 'https://moflix-stream.xyz'; needle = 'film' },
    @{ id = 'freecatalog'; name = 'Free Catalog'; lang = 'multi'; url = 'https://api.tvmaze.com/search/shows?q=breaking+bad'; needle = 'Breaking Bad' },
    @{ id = 'hydrahd'; name = 'HydraHD'; lang = 'en'; url = 'https://hydrahd.com'; needle = 'watch' },
    @{ id = 'cinezo'; name = 'Cinezo'; lang = 'en'; url = 'https://cinezo.org'; needle = 'movie' },
    @{ id = 'showsst'; name = 'Shows.st'; lang = 'en'; url = 'https://shows.st'; needle = 'tv' },
    @{ id = 'phantomflix'; name = 'PhantomFlix'; lang = 'en'; url = 'https://phantomflix.net'; needle = 'movie' },
    @{ id = 'flixer'; name = 'Flixer'; lang = 'en'; url = 'https://flixer.su'; needle = 'movie' },
    @{ id = 'sflix'; name = 'SFlix'; lang = 'en'; url = 'https://sflix.to'; needle = 'movie' },
    @{ id = 'ridomovies'; name = 'Ridomovies'; lang = 'en'; url = 'https://ridomovies.su'; needle = 'movie' },
    @{ id = 'anikoto'; name = 'Anikoto'; lang = 'en'; url = 'https://anikototv.to'; needle = 'watch' },
    @{ id = 'dramacool'; name = 'DramaCool'; lang = 'en'; url = 'https://dramacoole.buzz'; needle = 'drama' },
    @{ id = 'pressplay'; name = 'PressPlay'; lang = 'en'; url = 'https://pressplay.top'; needle = 'play' },
    @{ id = 'streamingcommunity_en'; name = 'StreamingCommunity EN'; lang = 'en'; url = 'https://streamingunity.cc'; needle = 'movie' },
    @{ id = 'mkissa'; name = 'Mkissa'; lang = 'en'; url = 'https://mkissa.com'; needle = 'movie' },
    @{ id = 'lookmovie2'; name = 'LookMovie2'; lang = 'en'; url = 'https://www.lookmovie2.to'; needle = 'movie' },
    @{ id = 'soap2day'; name = 'Soap2day'; lang = 'en'; url = 'https://soap2day.rs'; needle = 'movie' },
    @{ id = 'mkvmovies'; name = 'MkvMovies'; lang = 'en'; url = 'https://mkvmoviespoint.casa'; needle = 'movie' },
    @{ id = 'wiflix'; name = 'Wiflix'; lang = 'fr'; url = 'https://flemmix.team'; needle = 'serie' },
    @{ id = 'frenchstream'; name = 'French Stream'; lang = 'fr'; url = 'https://fs16.lol'; needle = 'film' },
    @{ id = 'frenchanime'; name = 'French Anime'; lang = 'fr'; url = 'https://french-anime.com'; needle = 'anime' },
    @{ id = 'frembed'; name = 'Frembed'; lang = 'fr'; url = 'https://frembed.casa'; needle = 'film' },
    @{ id = 'voirfilms'; name = 'Voirfilms'; lang = 'fr'; url = 'https://voirfilms.ws'; needle = 'film' },
    @{ id = 'nekosama'; name = 'Neko-Sama'; lang = 'fr'; url = 'https://neko-sama.fr'; needle = 'anime' },
    @{ id = 'fanpelis'; name = 'Fanpelis'; lang = 'es'; url = 'https://fanpelis.to'; needle = 'pelicula' },
    @{ id = 'animeflv'; name = 'AnimeFLV'; lang = 'es'; url = 'https://www3.animeflv.net'; needle = 'anime' },
    @{ id = 'jkanime'; name = 'JKAnime'; lang = 'es'; url = 'https://jkanime.net'; needle = 'anime' },
    @{ id = 'pelisplusto'; name = 'PelisPlus'; lang = 'es'; url = 'https://pelisplus.to'; needle = 'pelicula' },
    @{ id = 'doramasflix'; name = 'Doramasflix'; lang = 'es'; url = 'https://doramasflix.in'; needle = 'drama' },
    @{ id = 'cuevana3'; name = 'Cuevana3'; lang = 'es'; url = 'https://www3.cuevana3.ai'; needle = 'pelicula' },
    @{ id = 'pelisflix'; name = 'Pelisflix'; lang = 'es'; url = 'https://pelisflix20.biz'; needle = 'pelicula' },
    @{ id = 'guardaserie'; name = 'GuardaSerie'; lang = 'it'; url = 'https://guardoserie.study'; needle = 'serie' },
    @{ id = 'cb01'; name = 'CB01'; lang = 'it'; url = 'https://cb01official.uno'; needle = 'film' },
    @{ id = 'altadefinizione01'; name = 'Altadefinizione01'; lang = 'it'; url = 'https://altadefinizione-01.fun'; needle = 'film' },
    @{ id = 'animeunity'; name = 'AnimeUnity'; lang = 'it'; url = 'https://www.animeunity.so'; needle = 'anime' },
    @{ id = 'streamingcommunity_it'; name = 'StreamingCommunity IT'; lang = 'it'; url = 'https://streamingunity.cc'; needle = 'film' },
    @{ id = 'animeworld'; name = 'AnimeWorld'; lang = 'it'; url = 'https://www.animeworld.ac'; needle = 'anime' },
    @{ id = 'filmpertutti'; name = 'Filmpertutti'; lang = 'it'; url = 'https://filmpertutti.asia'; needle = 'film' },
    @{ id = 'cineblog01'; name = 'Cineblog01'; lang = 'it'; url = 'https://cineblog01.hair'; needle = 'film' },
    @{ id = 'filmyonline'; name = 'FilmyOnline'; lang = 'pl'; url = 'https://filmyonline.cc'; needle = 'film' },
    @{ id = 'zaluknij'; name = 'Zaluknij'; lang = 'pl'; url = 'https://zaluknij.cc'; needle = 'film' }
)

function Test-ProviderReachability {
    param($provider)
    try {
        $resp = Invoke-WebRequest -Uri $provider.url -UserAgent $ua -TimeoutSec 25 -MaximumRedirection 5 -UseBasicParsing
        $body = $resp.Content
        $len = if ($body) { $body.Length } else { 0 }
        $hit = $false
        if ($len -gt 500 -and $body -match '(?i)' + [regex]::Escape($provider.needle)) { $hit = $true }
        if ($len -gt 500 -and $body -match '(?i)(serie|film|anime|movie|stream|watch|show|pelicula|drama|play|tv)') { $hit = $true }
        $status = if ($resp.StatusCode -ge 200 -and $resp.StatusCode -lt 400 -and $len -gt 500 -and $hit) { 'PASS' }
                  elseif ($resp.StatusCode -ge 200 -and $resp.StatusCode -lt 400 -and $len -gt 200) { 'PARTIAL' }
                  else { 'FAIL' }
        [PSCustomObject]@{
            Status = $status
            Provider = $provider.name
            Id = $provider.id
            Lang = $provider.lang
            Code = $resp.StatusCode
            Bytes = $len
            Url = $provider.url
            Note = ''
        }
    } catch {
        [PSCustomObject]@{
            Status = 'FAIL'
            Provider = $provider.name
            Id = $provider.id
            Lang = $provider.lang
            Code = 0
            Bytes = 0
            Url = $provider.url
            Note = $_.Exception.Message
        }
    }
}

Write-Host "`n=== NovaStream Provider Live Smoke (51 providers, network) ===`n"
$results = foreach ($p in $providers) {
    Write-Host "Testing [$($p.lang)] $($p.name)..." -NoNewline
    $r = Test-ProviderReachability $p
    Write-Host " $($r.Status)"
    $r
}

$results | Format-Table -AutoSize
$pass = ($results | Where-Object Status -eq 'PASS').Count
$partial = ($results | Where-Object Status -eq 'PARTIAL').Count
$fail = ($results | Where-Object Status -eq 'FAIL').Count
Write-Host "PASS=$pass PARTIAL=$partial FAIL=$fail TOTAL=$($results.Count) EXPECTED=51"

Write-Host "`nBy language:"
$results | Group-Object Lang | Sort-Object Name | ForEach-Object {
    $p = ($_.Group | Where-Object Status -eq 'PASS').Count
    $f = ($_.Group | Where-Object Status -eq 'FAIL').Count
    Write-Host "  $($_.Name): PASS=$p FAIL=$f TOTAL=$($_.Count)"
}

if ($results.Count -ne 51) {
    Write-Error "Provider count mismatch: expected 51, got $($results.Count)"
    exit 1
}
