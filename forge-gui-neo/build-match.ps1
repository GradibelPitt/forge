param(
    [Parameter(Mandatory = $true)][string]$RuntimeRoot,
    [string]$OutputRoot = (Join-Path $PSScriptRoot 'target\match')
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$sourceRoot = Split-Path $PSScriptRoot -Parent
$runtimeApp = Join-Path (Resolve-Path -LiteralPath $RuntimeRoot).Path 'app'
$spec = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'integration.json') -Raw | ConvertFrom-Json
foreach ($item in $spec.sha256.PSObject.Properties) {
    $actual = (Get-FileHash -LiteralPath (Join-Path $PSScriptRoot $item.Name) -Algorithm SHA256).Hash
    if ($actual -ne $item.Value) { throw "Dependency hash mismatch: $($item.Name)" }
}
$aggregate = @(Get-ChildItem -LiteralPath $runtimeApp -Filter '*-jar-with-dependencies.jar')
if ($aggregate.Count -ne 1) { throw 'Expected one desktop aggregate JAR.' }
New-Item -ItemType Directory -Path $OutputRoot -Force | Out-Null
$OutputRoot = (Resolve-Path -LiteralPath $OutputRoot).Path
$classes = Join-Path $OutputRoot ('classes-' + [guid]::NewGuid().ToString('N'))
$payload = Join-Path $OutputRoot 'overlays'
New-Item -ItemType Directory -Path $classes, $payload -Force | Out-Null
$neoBase = Join-Path $PSScriptRoot 'vendor\neo-forge-1.0.31-ui.jar'
$cp = "$classes;$PSScriptRoot/vendor/win/*;$runtimeApp/overlays/*;$($aggregate[0].FullName);$neoBase"
$sources = @($spec.sources | ForEach-Object { Join-Path $sourceRoot $_ })
& javac '-J-Duser.language=en' --release 17 -encoding UTF-8 -cp $cp -d $classes @sources
if ($LASTEXITCODE -ne 0) { throw 'Neo match Java compilation failed.' }

function Test-ClassFamily([string]$Name, [string[]]$Families) {
    foreach ($family in $Families) {
        if ($Name -eq "$family.class" -or $Name.StartsWith($family + '$')) { return $true }
    }
    return $false
}

function Write-PatchJar([string]$Baseline, [string]$Destination, [string[]]$Removed, [hashtable]$Added) {
    $entries = @{}
    $baseZip = [IO.Compression.ZipFile]::OpenRead($Baseline)
    try {
        foreach ($entry in $baseZip.Entries) {
            if ($entry.FullName.EndsWith('/') -or (Test-ClassFamily $entry.FullName $Removed)) { continue }
            $input = $entry.Open()
            $buffer = [IO.MemoryStream]::new()
            try { $input.CopyTo($buffer); $entries[$entry.FullName] = $buffer.ToArray() }
            finally { $input.Dispose(); $buffer.Dispose() }
        }
    } finally { $baseZip.Dispose() }
    foreach ($name in $Added.Keys) { $entries[$name] = [IO.File]::ReadAllBytes($Added[$name]) }
    $file = [IO.File]::Open($Destination, [IO.FileMode]::Create)
    $zip = [IO.Compression.ZipArchive]::new($file, [IO.Compression.ZipArchiveMode]::Create)
    try {
        foreach ($name in @($entries.Keys | Sort-Object)) {
            $entry = $zip.CreateEntry($name, [IO.Compression.CompressionLevel]::Optimal)
            $entry.LastWriteTime = [DateTimeOffset]::new(2000, 1, 1, 0, 0, 0, [TimeSpan]::Zero)
            $stream = $entry.Open()
            try { $stream.Write($entries[$name], 0, $entries[$name].Length) }
            finally { $stream.Dispose() }
        }
    } finally { $zip.Dispose(); $file.Dispose() }
}

$gameFamilies = @($spec.sources | Where-Object { $_.StartsWith('forge-game/') } | ForEach-Object { ($_ -split '/src/main/java/')[1] -replace '\.java$', '' })
$guiFamilies = @($spec.guiOverlayClasses)
$desktopFamilies = @($spec.sources | Where-Object { $_.StartsWith('forge-gui-desktop/') } | ForEach-Object { ($_ -split '/src/main/java/')[1] -replace '\.java$', '' })
$game = @{}; $desktop = @{}; $neo = @{}; $gui = @{}
foreach ($file in Get-ChildItem -LiteralPath $classes -Recurse -Filter '*.class') {
    $name = $file.FullName.Substring($classes.Length + 1).Replace('\', '/')
    if (Test-ClassFamily $name $gameFamilies) { $game[$name] = $file.FullName }
    elseif (Test-ClassFamily $name $guiFamilies) { $gui[$name] = $file.FullName }
    elseif (Test-ClassFamily $name $desktopFamilies) { $desktop[$name] = $file.FullName }
    else { $neo[$name] = $file.FullName }
}
Write-PatchJar (Join-Path $runtimeApp 'overlays\forge-game.jar') (Join-Path $payload 'forge-game.jar') $gameFamilies $game
Write-PatchJar (Join-Path $runtimeApp 'overlays\forge-gui.jar') (Join-Path $payload 'forge-gui.jar') $guiFamilies $gui
Write-PatchJar (Join-Path $runtimeApp 'overlays\forge-gui-desktop.jar') (Join-Path $payload 'forge-gui-desktop.jar') @($desktopFamilies + $spec.retiredDesktopClasses) $desktop
$neo['META-INF/licenses/NeoForge-LICENSE'] = Join-Path $PSScriptRoot 'vendor\NeoForge-LICENSE'
$neo['META-INF/licenses/OpenJFX-LICENSE'] = Join-Path $PSScriptRoot 'vendor\OpenJFX-LICENSE'
$neo['META-INF/licenses/OpenJFX-ADDITIONAL_LICENSE_INFO'] = Join-Path $PSScriptRoot 'vendor\OpenJFX-ADDITIONAL_LICENSE_INFO'
foreach ($notice in Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'vendor\openjfx-legal') -File) {
    $neo['META-INF/licenses/' + $notice.Name] = $notice.FullName
}
$neo['META-INF/sources/neo-forge-1.0.31-source.zip'] = Join-Path $PSScriptRoot 'vendor\neo-forge-1.0.31-source.zip'
$neo['META-INF/neo-integration.json'] = Join-Path $PSScriptRoot 'integration.json'
foreach ($resource in $spec.resources) {
    $name = ($resource -split '/src/main/resources/')[1]
    $neo[$name] = Join-Path $sourceRoot $resource
}
$neo['forge/neo/diy/match.css'] = Join-Path $PSScriptRoot 'src\main\resources\forge\neo\diy\match.css'
Write-PatchJar $neoBase (Join-Path $payload 'forge-gui-neo.jar') @() $neo
Copy-Item -Path (Join-Path $PSScriptRoot 'vendor\win\*.jar') -Destination $payload -Force
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'vendor\NeoForge-LICENSE') -Destination (Join-Path $OutputRoot 'NeoForge-LICENSE') -Force
Write-Output "NEO_MATCH_BUILD=OK"
Write-Output "PAYLOAD=$payload"
Write-Output "CLASSES=$classes"
