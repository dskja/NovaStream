$ErrorActionPreference = 'Continue'
$ua = 'Mozilla/5.0 (Linux; Android 15; Pixel 9) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36'

$providers = @(
    @{ id='serienstream'; name='SerienStream'; home='https://serienstream.to/'; search='https://serienstream.to/suche/Dark'; needle='serie' },
    @{ id='serienstream_cx'; name='SerienStream CX'; home='https://serienstream.cx/'; search='https://serienstream.cx/suche/Dark'; needle='serie' },
    @{ id='aniworld'; name='AniWorld'; home='https://aniworld.to/'; search='https://aniworld.to/animes?search=Naruto'; needle='anime' },
    @{ id='kinoger'; name='KinoGer'; home='https://kinoger.to/'; search='https://kinoger.to/search?query=Avatar'; needle='film' },
    @{ id='burningseries'; name='Burning Series'; home='https://burningseries.cx/'; search='https://burningseries.cx/andelselect'; needle='serie' },
    @{ id='megakino'; name='MegaKino'; home='https://megakino.ms/'; search='https://megakino.ms/search?query=Avatar'; needle='film'; altHome='https://megakino1.org/'; altSearch='https://megakino1.org/search?query=Avatar' },
    @{ id='streamkiste'; name='StreamKiste'; home='https://stream-kiste.de/serien'; search='https://stream-kiste.de/search?q=Dark'; needle='serien' },
    @{ id='filmpalast'; name='FilmPalast'; home='https://filmpalast.to/'; search='https://filmpalast.to/search?headerSearchText=Avatar'; needle='film' },
    @{ id='kinoz'; name='KinoZ'; home='https://kinoz.to/'; search='https://kinoz.to/Search.html?q=Avatar'; needle='stream' },
    @{ id='freecatalog'; name='Free Catalog'; home='https://api.tvmaze.com/search/shows?q=Breaking+Bad'; search='https://api.tvmaze.com/search/shows?q=Dark'; needle='Breaking Bad' },
    @{ id='hydrahd'; name='HydraHD'; home='https://hydrahd.com/series/'; search='https://hydrahd.com/index.php?s=Avatar'; needle='watch' },
    @{ id='cinezo'; name='Cinezo'; home='https://cinezo.org/'; search='https://cinezo.org/search?q=Avatar'; needle='movie' },
    @{ id='showsst'; name='Shows.st'; home='https://shows.st/'; search='https://shows.st/search?q=Dark'; needle='tv' },
    @{ id='phantomflix'; name='PhantomFlix'; home='https://phantomflix.net/'; search='https://phantomflix.net/search?q=Avatar'; needle='movie' },
    @{ id='flixer'; name='Flixer'; home='https://flixer.su/shows'; search='https://flixer.su/search?q=Avatar'; needle='movie' },
    @{ id='dramacool'; name='DramaCool'; home='https://dramacoole.buzz/'; search='https://dramacoole.buzz/search?keyword=Squid+Game'; needle='drama' },
    @{ id='pressplay'; name='PressPlay'; home='https://pressplay.top/'; search='https://pressplay.top/search?q=Dark'; needle='play' }
)

function Resolve-Dns([string]$hostName) {
    try {
        $r = Resolve-DnsName -Name $hostName -ErrorAction Stop | Where-Object { $_.Type -in 'A','AAAA' }
        return ($r | Select-Object -First 2 | ForEach-Object { $_.IPAddress }) -join ', '
    } catch {
        return 'NXDOMAIN'
    }
}

function Test-Url([string]$url) {
    try {
        $resp = Invoke-WebRequest -Uri $url -UserAgent $ua -TimeoutSec 30 -MaximumRedirection 8 -UseBasicParsing
        $body = $resp.Content
        $len = if ($body) { $body.Length } else { 0 }
        return @{
            ok = ($resp.StatusCode -ge 200 -and $resp.StatusCode -lt 400 -and $len -gt 200)
            code = [int]$resp.StatusCode
            bytes = $len
            final = $resp.BaseResponse.ResponseUri.AbsoluteUri
            note = ''
        }
    } catch {
        $code = 0
        if ($_.Exception.Response) { $code = [int]$_.Exception.Response.StatusCode }
        return @{ ok = $false; code = $code; bytes = 0; final = $url; note = $_.Exception.Message }
    }
}

function Score-Provider($p) {
    $hostName = ([uri]$p.home).Host
    $dns = Resolve-Dns $hostName
    $homeResult = Test-Url $p.home
    $searchResult = Test-Url $p.search
    $altNote = ''
    if (-not $homeResult.ok -and $p.altHome) {
        $altHomeResult = Test-Url $p.altHome
        if ($altHomeResult.ok) { $altNote = "alt home OK ($($p.altHome))" }
    }
    if (-not $searchResult.ok -and $p.altSearch) {
        $altSearchResult = Test-Url $p.altSearch
        if ($altSearchResult.ok) { $altNote = if ($altNote) { "$altNote; alt search OK" } else { "alt search OK ($($p.altSearch))" } }
    }

    $status = 'FAIL'
    if ($dns -eq 'NXDOMAIN') { $status = 'FAIL' }
    elseif ($homeResult.ok -and $searchResult.ok) { $status = 'PASS' }
    elseif ($homeResult.ok -or $searchResult.ok -or $altNote) { $status = 'PARTIAL' }

    [PSCustomObject]@{
        Status = $status
        Provider = $p.name
        Id = $p.id
        Dns = $dns
        Home = if ($homeResult.ok) { "OK $($homeResult.code)/$($homeResult.bytes)b" } else { "FAIL $($homeResult.code) $($homeResult.note)" }
        Search = if ($searchResult.ok) { "OK $($searchResult.code)/$($searchResult.bytes)b" } else { "FAIL $($searchResult.code) $($searchResult.note)" }
        Note = $altNote
    }
}

Write-Host "`n=== NovaStream Full Provider Test (DNS + Home + Search) ===`n"
$results = foreach ($p in $providers) {
    Write-Host "Testing $($p.name)..." -NoNewline
    $r = Score-Provider $p
    Write-Host " $($r.Status)"
    $r
}

$results | Format-Table -AutoSize -Wrap
$pass = ($results | Where-Object Status -eq 'PASS').Count
$partial = ($results | Where-Object Status -eq 'PARTIAL').Count
$fail = ($results | Where-Object Status -eq 'FAIL').Count
Write-Host "PASS=$pass PARTIAL=$partial FAIL=$fail TOTAL=$($results.Count)"
