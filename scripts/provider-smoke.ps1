$ErrorActionPreference = 'Continue'
$ua = 'Mozilla/5.0 (Linux; Android 13; NovaStream) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36'

$providers = @(
    @{ id = 'serienstream'; name = 'SerienStream'; url = 'https://serienstream.to'; needle = 'serie' },
    @{ id = 'serienstream_cx'; name = 'SerienStream CX'; url = 'https://serienstream.cx'; needle = 'serie' },
    @{ id = 'aniworld'; name = 'AniWorld'; url = 'https://aniworld.to'; needle = 'anime' },
    @{ id = 'kinoger'; name = 'KinoGer'; url = 'https://kinoger.to'; needle = 'film' },
    @{ id = 'burningseries'; name = 'Burning Series'; url = 'https://burningseries.cx'; needle = 'serie' },
    @{ id = 'megakino'; name = 'MegaKino'; url = 'https://megakino6.com'; needle = 'film' },
    @{ id = 'streamkiste'; name = 'StreamKiste'; url = 'https://stream-kiste.de/serien'; needle = 'serien' },
    @{ id = 'filmpalast'; name = 'FilmPalast'; url = 'https://filmpalast.to'; needle = 'film' },
    @{ id = 'kinoz'; name = 'KinoZ'; url = 'https://kinoz.to'; needle = 'stream' },
    @{ id = 'freecatalog'; name = 'Free Catalog'; url = 'https://api.tvmaze.com/search/shows?q=breaking+bad'; needle = 'Breaking Bad' },
    @{ id = 'hydrahd'; name = 'HydraHD'; url = 'https://hydrahd.com'; needle = 'watch' },
    @{ id = 'cinezo'; name = 'Cinezo'; url = 'https://cinezo.org'; needle = 'movie' },
    @{ id = 'showsst'; name = 'Shows.st'; url = 'https://shows.st'; needle = 'tv' },
    @{ id = 'phantomflix'; name = 'PhantomFlix'; url = 'https://phantomflix.net'; needle = 'movie' },
    @{ id = 'flixer'; name = 'Flixer'; url = 'https://flixer.su'; needle = 'movie' },
    @{ id = 'dramacool'; name = 'DramaCool'; url = 'https://dramacoole.buzz'; needle = 'drama' },
    @{ id = 'pressplay'; name = 'PressPlay'; url = 'https://pressplay.top'; needle = 'play' }
)

function Test-ProviderReachability {
    param($provider)
    try {
        $resp = Invoke-WebRequest -Uri $provider.url -UserAgent $ua -TimeoutSec 25 -MaximumRedirection 5 -UseBasicParsing
        $body = $resp.Content
        $len = if ($body) { $body.Length } else { 0 }
        $hit = $false
        if ($len -gt 500 -and $body -match '(?i)' + [regex]::Escape($provider.needle)) { $hit = $true }
        if ($len -gt 500 -and $body -match '(?i)(serie|film|anime|movie|stream|watch|show)') { $hit = $true }
        $status = if ($resp.StatusCode -ge 200 -and $resp.StatusCode -lt 400 -and $len -gt 500 -and $hit) { 'PASS' }
                  elseif ($resp.StatusCode -ge 200 -and $resp.StatusCode -lt 400 -and $len -gt 200) { 'PARTIAL' }
                  else { 'FAIL' }
        [PSCustomObject]@{
            Status = $status
            Provider = $provider.name
            Id = $provider.id
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
            Code = 0
            Bytes = 0
            Url = $provider.url
            Note = $_.Exception.Message
        }
    }
}

Write-Host "`n=== NovaStream Provider Live Smoke (network) ===`n"
$results = foreach ($p in $providers) {
    Write-Host "Testing $($p.name)..." -NoNewline
    $r = Test-ProviderReachability $p
    Write-Host " $($r.Status)"
    $r
}

$results | Format-Table -AutoSize
$pass = ($results | Where-Object Status -eq 'PASS').Count
$partial = ($results | Where-Object Status -eq 'PARTIAL').Count
$fail = ($results | Where-Object Status -eq 'FAIL').Count
Write-Host "PASS=$pass PARTIAL=$partial FAIL=$fail TOTAL=$($results.Count)"
