param(
    [Parameter(Mandatory = $true)][string]$RuntimeRoot,
    [string]$OutputRoot = (Join-Path $PSScriptRoot 'target\home3d')
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$sourceRoot = Split-Path $PSScriptRoot -Parent
$runtimeApp = Join-Path (Resolve-Path -LiteralPath $RuntimeRoot).Path 'app'
$aggregate = @(Get-ChildItem -LiteralPath $runtimeApp -Filter '*-jar-with-dependencies.jar')
if ($aggregate.Count -ne 1) { throw 'Expected one desktop aggregate JAR.' }
$javafx = Join-Path $sourceRoot 'forge-gui-neo\vendor\win'
if (-not (Test-Path -LiteralPath (Join-Path $javafx 'javafx-swing-21.0.5-win.jar'))) {
    throw 'The shared Neo JavaFX dependencies must be installed before building the 3D lobby.'
}
New-Item -ItemType Directory -Path $OutputRoot -Force | Out-Null
$OutputRoot = (Resolve-Path -LiteralPath $OutputRoot).Path
$classes = Join-Path $OutputRoot ('classes-' + [guid]::NewGuid().ToString('N'))
$payload = Join-Path $OutputRoot 'overlays'
New-Item -ItemType Directory -Path $classes, $payload -Force | Out-Null
$desktopSource = Join-Path $sourceRoot 'forge-gui-desktop\src\main\java\forge\screens\home'
$sources = @((Join-Path $desktopSource 'VHomeUI.java'), (Join-Path $desktopSource 'CHomeUI.java'))
$sources += @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'src\main\java') -Filter '*.java' -Recurse | ForEach-Object FullName)
$cp = "$classes;$javafx/*;$runtimeApp/overlays/*;$($aggregate[0].FullName)"
& javac '-J-Duser.language=en' --release 17 -encoding UTF-8 -cp $cp -d $classes @sources
if ($LASTEXITCODE -ne 0) { throw '3D lobby compilation failed.' }

function Test-HomeController([string]$Name) {
    return $Name -match '^forge/screens/home/(VHomeUI|CHomeUI)(\$[^/]+)?\.class$'
}
function Write-Jar([string]$Path, [hashtable]$Entries) {
    $file = [IO.File]::Open($Path, [IO.FileMode]::Create)
    $zip = [IO.Compression.ZipArchive]::new($file, [IO.Compression.ZipArchiveMode]::Create)
    try {
        foreach ($name in @($Entries.Keys | Sort-Object)) {
            $entry = $zip.CreateEntry($name, [IO.Compression.CompressionLevel]::Optimal)
            $entry.LastWriteTime = [DateTimeOffset]::new(2000, 1, 1, 0, 0, 0, [TimeSpan]::Zero)
            $stream = $entry.Open()
            try { $bytes = $Entries[$name]; $stream.Write($bytes, 0, $bytes.Length) }
            finally { $stream.Dispose() }
        }
    } finally { $zip.Dispose(); $file.Dispose() }
}
$desktop = @{}; $homeEntries = @{}
$baseline = [IO.Compression.ZipFile]::OpenRead((Join-Path $runtimeApp 'overlays\forge-gui-desktop.jar'))
try {
    foreach ($entry in $baseline.Entries) {
        if ($entry.FullName.EndsWith('/') -or (Test-HomeController $entry.FullName)) { continue }
        $stream = $entry.Open(); $buffer = [IO.MemoryStream]::new()
        try { $stream.CopyTo($buffer); $desktop[$entry.FullName] = $buffer.ToArray() }
        finally { $stream.Dispose(); $buffer.Dispose() }
    }
} finally { $baseline.Dispose() }
foreach ($file in Get-ChildItem -LiteralPath $classes -Filter '*.class' -Recurse) {
    $name = $file.FullName.Substring($classes.Length + 1).Replace('\', '/')
    if (Test-HomeController $name) { $desktop[$name] = [IO.File]::ReadAllBytes($file.FullName) }
    else { $homeEntries[$name] = [IO.File]::ReadAllBytes($file.FullName) }
}
Write-Jar (Join-Path $payload 'forge-gui-desktop.jar') $desktop
Write-Jar (Join-Path $payload 'forge-gui-home3d.jar') $homeEntries
Write-Output "HOME3D_BUILD=OK"
Write-Output "PAYLOAD=$payload"
Write-Output "CLASSES=$classes"
