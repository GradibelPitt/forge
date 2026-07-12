param([switch]$Quiet)

$ErrorActionPreference = "Stop"
$ManagedRoot = Join-Path $PSScriptRoot "managed"
$CustomSource = Join-Path $ManagedRoot "custom"
$ForgeCustom = Join-Path ([Environment]::GetFolderPath('ApplicationData')) "Forge\custom"
$CardCache = Join-Path ([Environment]::GetFolderPath('LocalApplicationData')) "Forge\Cache\pics\cards"
$TokenCache = Join-Path ([Environment]::GetFolderPath('LocalApplicationData')) "Forge\Cache\pics\tokens"

function Copy-TreeFiles([string]$Source, [string]$Destination, [scriptblock]$Filter) {
    if (-not (Test-Path -LiteralPath $Source)) { return }
    Get-ChildItem -LiteralPath $Source -Recurse -File | Where-Object $Filter | ForEach-Object {
        $relative = $_.FullName.Substring($Source.Length).TrimStart('\')
        $target = Join-Path $Destination $relative
        $parent = Split-Path $target -Parent
        New-Item -ItemType Directory -Path $parent -Force | Out-Null
        Copy-Item -LiteralPath $_.FullName -Destination $target -Force
    }
}

Copy-TreeFiles (Join-Path $CustomSource "cards") (Join-Path $ForgeCustom "cards") { $_.Extension -eq '.txt' }
Copy-TreeFiles (Join-Path $CustomSource "editions") (Join-Path $ForgeCustom "editions") { $_.Extension -eq '.txt' }
Copy-TreeFiles (Join-Path $CustomSource "tokens") (Join-Path $ForgeCustom "tokens") { $_.Extension -eq '.txt' }
Copy-TreeFiles (Join-Path $CustomSource "cards\pictures") $CardCache { $true }
Copy-TreeFiles (Join-Path $CustomSource "tokens\pictures") $TokenCache { $true }

if (-not $Quiet) { Write-Output "Forge DIY 文件已同步。" }

