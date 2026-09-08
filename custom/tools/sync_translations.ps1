param (
    [string]$LanguagesDirectory = (Join-Path $PSScriptRoot '..\..\forge-gui\res\languages'),
    [string]$TranslationSource = (Join-Path $PSScriptRoot '..\translations\cardnames-zh-CN-custom.txt'),
    [switch]$CheckOnly,
    [switch]$Uninstall
)

$ErrorActionPreference = 'Stop'
$filename = 'cardnames-zh-CN-custom.txt'
$destination = Join-Path $LanguagesDirectory $filename
if ($Uninstall) {
    if (Test-Path -LiteralPath $destination -PathType Leaf) {
        Remove-Item -LiteralPath $destination -Force
    }
    return
}

# Validate the small source before writing anything. Never rewrite the base file.
$rows = [IO.File]::ReadAllLines((Resolve-Path -LiteralPath $TranslationSource).Path,
    [Text.UTF8Encoding]::new($false, $true))
$names = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
$lineNumber = 0
foreach ($line in $rows) {
    $lineNumber++
    if ([string]::IsNullOrWhiteSpace($line) -or $line.StartsWith('#')) { continue }
    $fields = $line.Split([char]'|')
    if ($fields.Count -ne 4 -or [string]::IsNullOrWhiteSpace($fields[0]) -or
            [string]::IsNullOrWhiteSpace($fields[1]) -or [string]::IsNullOrWhiteSpace($fields[2])) {
        throw "Invalid translation at line ${lineNumber}: expected name, display name, type and Oracle (4 fields)."
    }
    if ($fields[0] -ne $fields[0].Trim()) {
        throw "Translation key has surrounding whitespace at line $lineNumber."
    }
    # Forge normalizes functional-variant keys before inserting them into its maps.
    $key = $fields[0] -replace '\s*\$', ' $'
    if (-not $names.Add($key)) { throw "Duplicate translation key at line ${lineNumber}: $key" }
}
if (-not $CheckOnly) {
    New-Item -ItemType Directory -Path $LanguagesDirectory -Force | Out-Null
    Copy-Item -LiteralPath $TranslationSource -Destination $destination -Force
    if ((Get-FileHash -LiteralPath $TranslationSource -Algorithm SHA256).Hash -ne
            (Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash) {
        throw 'Custom translation hash mismatch after copy.'
    }
}
Write-Output "CUSTOM_TRANSLATIONS=OK ($($names.Count) records)"
