$ErrorActionPreference = 'Continue'
$Package = 'com.novastream.app'
$Activity = "$Package/.MainActivity"
$DumpRemote = '/sdcard/ns-provider-ui.xml'
$DumpLocal = Join-Path (Split-Path -Parent $MyInvocation.MyCommand.Path) 'ns-provider-ui.xml'

$providers = @(
    'SerienStream', 'SerienStream CX', 'AniWorld', 'KinoGer', 'Burning Series',
    'MegaKino', 'StreamKiste', 'FilmPalast', 'KinoZ', 'Free Catalog',
    'HydraHD', 'Cinezo', 'Shows.st', 'PhantomFlix', 'Flixer', 'DramaCool', 'PressPlay'
)

function Invoke-Adb([string]$Args) {
    & adb $Args.Split(' ', [System.StringSplitOptions]::RemoveEmptyEntries) 2>&1
}

function Wake-Device {
    Invoke-Adb 'shell input keyevent KEYCODE_WAKEUP' | Out-Null
    Start-Sleep -Milliseconds 400
    Invoke-Adb 'shell input keyevent 82' | Out-Null
    Start-Sleep -Milliseconds 400
}

function Get-UiDump {
    Invoke-Adb "shell uiautomator dump $DumpRemote" | Out-Null
    Start-Sleep -Milliseconds 300
    Invoke-Adb "pull $DumpRemote `"$DumpLocal`"" | Out-Null
    if (-not (Test-Path $DumpLocal)) { return $null }
    [xml](Get-Content -Raw $DumpLocal)
}

function Find-NodeByText([xml]$doc, [string]$text, [switch]$Partial) {
    if ($null -eq $doc) { return $null }
    $nodes = $doc.SelectNodes('//node[@text]')
    foreach ($n in $nodes) {
        $t = $n.GetAttribute('text')
        if (-not $t) { continue }
        if ($Partial) {
            if ($t -like "*$text*") { return $n }
        } elseif ($t -eq $text) {
            return $n
        }
    }
    return $null
}

function Tap-Node($node) {
    if ($null -eq $node) { return $false }
    $bounds = $node.GetAttribute('bounds')
    if (-not $bounds) { return $false }
    if ($bounds -match '\[(\d+),(\d+)\]\[(\d+),(\d+)\]') {
        $x = [int](([int]$Matches[1] + [int]$Matches[3]) / 2)
        $y = [int](([int]$Matches[2] + [int]$Matches[4]) / 2)
        Invoke-Adb "shell input tap $x $y" | Out-Null
        return $true
    }
    return $false
}

function Find-NodeByContentDesc([xml]$doc, [string]$desc, [switch]$Partial) {
    if ($null -eq $doc) { return $null }
    foreach ($n in $doc.SelectNodes('//node[@content-desc]')) {
        $t = $n.GetAttribute('content-desc')
        if (-not $t) { continue }
        if ($Partial) {
            if ($t -like "*$desc*") { return $n }
        } elseif ($t -eq $desc) {
            return $n
        }
    }
    return $null
}

function Tap-Text([string]$text, [switch]$Partial) {
    $doc = Get-UiDump
    $node = Find-NodeByText $doc $text -Partial:$Partial
    if (-not $node) { $node = Find-NodeByContentDesc $doc $text -Partial:$Partial }
    Tap-Node $node
}

function Count-HomeCards([xml]$doc) {
    if ($null -eq $doc) { return 0 }
    $count = 0
    foreach ($n in $doc.SelectNodes('//node')) {
        $cls = $n.'class'
        $desc = $n.'content-desc'
        $txt = $n.'text'
        if ($desc -match 'Poster|Cover|Serie|Film' -or $txt -match 'Staffel|Episode|Min\.') {
            $count++
        }
    }
    return $count
}

function Test-HomeLoaded([xml]$doc) {
    if ($null -eq $doc) { return @{ ok = $false; reason = 'no ui dump' } }
    $errorNode = Find-NodeByText $doc 'Erneut versuchen' -Partial
    if ($errorNode) { return @{ ok = $false; reason = 'retry button visible' } }
    $errorNode = Find-NodeByText $doc 'Verbindungsfehler' -Partial
    if ($errorNode) { return @{ ok = $false; reason = 'connection error' } }
    $errorNode = Find-NodeByText $doc 'Fehler' -Partial
    if ($errorNode) { return @{ ok = $false; reason = 'error banner' } }
    $loading = Find-NodeByText $doc 'Laden' -Partial
    if ($loading) { return @{ ok = $false; reason = 'still loading' } }

    $scrollable = $doc.SelectNodes("//*[@scrollable='true']")
    if ($scrollable.Count -gt 0) {
        return @{ ok = $true; reason = 'scrollable content' }
    }
    $images = $doc.SelectNodes("//*[@class='android.widget.Image']")
    if ($images.Count -ge 3) {
        return @{ ok = $true; reason = "images=$($images.Count)" }
    }
    return @{ ok = $false; reason = 'no catalog content detected' }
}

Write-Host "`n=== NovaStream Provider Device UI Test ===`n"
Wake-Device
Invoke-Adb 'logcat -c' | Out-Null
Invoke-Adb "shell am start -n $Activity" | Out-Null
Start-Sleep -Seconds 5
Tap-Text 'Settings' -Partial | Out-Null
Start-Sleep -Seconds 2

$results = @()

foreach ($name in $providers) {
    Write-Host "Testing $name..." -NoNewline

    Tap-Text 'Home' | Out-Null
    Start-Sleep -Seconds 1
    Tap-Text 'Settings' -Partial | Out-Null
    Start-Sleep -Seconds 2

    $doc = Get-UiDump
    $providerNode = Find-NodeByText $doc $name
    if (-not $providerNode) {
        for ($i = 0; $i -lt 15 -and -not $providerNode; $i++) {
            Invoke-Adb 'shell input swipe 540 1800 540 700 350' | Out-Null
            Start-Sleep -Milliseconds 700
            $doc = Get-UiDump
            $providerNode = Find-NodeByText $doc $name
        }
    }

    if (-not $providerNode) {
        Write-Host ' FAIL (provider row not found)'
        $results += [PSCustomObject]@{ Provider = $name; Status = 'FAIL'; Note = 'provider row not found in settings' }
        Tap-Text 'Home' | Out-Null
        Start-Sleep -Seconds 1
        continue
    }

    Tap-Node $providerNode | Out-Null
    Start-Sleep -Milliseconds 800

    Tap-Text 'Bestätigen' | Out-Null
    if (-not (Find-NodeByText (Get-UiDump) 'Bestätigen')) { Tap-Text 'Confirm' | Out-Null }
    Start-Sleep -Seconds 2

    Tap-Text 'Home' | Out-Null
    Start-Sleep -Seconds 12

    $homeDoc = Get-UiDump
    $homeCheck = Test-HomeLoaded $homeDoc

    $log = Invoke-Adb 'logcat -d -s HomeVM:E HomeVM:W'
    $logNote = ''
    if ($log -match 'load error') { $logNote = 'HomeVM load error' }

    if ($homeCheck.ok -and -not $logNote) {
        Write-Host " PASS ($($homeCheck.reason))"
        $results += [PSCustomObject]@{ Provider = $name; Status = 'PASS'; Note = $homeCheck.reason }
    } elseif ($homeCheck.ok) {
        Write-Host " PARTIAL ($logNote)"
        $results += [PSCustomObject]@{ Provider = $name; Status = 'PARTIAL'; Note = $logNote }
    } else {
        $failSuffix = if ([string]::IsNullOrEmpty($logNote)) { '' } else { "; $logNote" }
        Write-Host " FAIL ($($homeCheck.reason)$failSuffix)"
        $note = if ($logNote) { "$($homeCheck.reason); $logNote" } else { $homeCheck.reason }
        $results += [PSCustomObject]@{ Provider = $name; Status = 'FAIL'; Note = $note }
    }

    Invoke-Adb 'logcat -c' | Out-Null
}

Write-Host "`n--- Summary ---"
$results | Format-Table -AutoSize
$pass = ($results | Where-Object Status -eq 'PASS').Count
$partial = ($results | Where-Object Status -eq 'PARTIAL').Count
$fail = ($results | Where-Object Status -eq 'FAIL').Count
Write-Host "PASS=$pass PARTIAL=$partial FAIL=$fail TOTAL=$($results.Count)"
