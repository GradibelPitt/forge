# PowerShell Script to Sync Custom Cards to Forge
# Usage:
#   powershell -ExecutionPolicy Bypass -File .\install_to_forge.ps1
#   powershell -ExecutionPolicy Bypass -File .\install_to_forge.ps1 -Uninstall

param (
    [switch]$Uninstall = $false
)

$ErrorActionPreference = "Stop"

# Define Paths
$AppData = [System.Environment]::GetFolderPath('ApplicationData')
$ForgeCustomDir = Join-Path $AppData "Forge\custom"

$WorkspaceRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$WorkspaceCards = Join-Path $WorkspaceRoot "cards"
$WorkspaceEditions = Join-Path $WorkspaceRoot "editions"
$WorkspaceTokens = Join-Path $WorkspaceRoot "tokens"
$WorkspacePictures = Join-Path $WorkspaceCards "pictures"
$WorkspaceTokenPictures = Join-Path $WorkspaceTokens "pictures"

$ForgeCards = Join-Path $ForgeCustomDir "cards"
$ForgeEditions = Join-Path $ForgeCustomDir "editions"
$ForgeTokens = Join-Path $ForgeCustomDir "tokens"
$ForgeConstructedDecks = Join-Path $AppData "Forge\decks\constructed"
$ForgeCardPictures = Join-Path $env:LOCALAPPDATA "Forge\Cache\pics\cards"
$ForgeTokenPictures = Join-Path $env:LOCALAPPDATA "Forge\Cache\pics\tokens"
# Retired source path: "colorless\炉石传说.txt". Build the Chinese name from
# code points because Windows PowerShell 5.1 reads UTF-8 files without a BOM
# using the active ANSI code page.
$HearthstoneCardName = -join ([char[]](0x7089, 0x77F3, 0x4F20, 0x8BF4))
$RetiredCardPaths = @("colorless\$HearthstoneCardName.txt")

function Remove-RetiredHearthstoneCardFromDecks {
    if (-not (Test-Path -LiteralPath $ForgeConstructedDecks -PathType Container)) {
        return 0
    }

    $migrated = 0
    Get-ChildItem -LiteralPath $ForgeConstructedDecks -Recurse -Filter "*.dck" -File | ForEach-Object {
        $lines = [IO.File]::ReadAllLines($_.FullName, [Text.Encoding]::UTF8)
        $updated = New-Object 'System.Collections.Generic.List[string]'
        $removed = $false
        foreach ($line in $lines) {
            $trimmed = $line.Trim()
            $request = $trimmed -replace '^\d+\s+', ''
            if ($trimmed -match '^\d+\s+' -and $request.StartsWith("$HearthstoneCardName|PH01")) {
                $removed = $true
            } else {
                $updated.Add($line)
            }
        }
        if ($removed) {
            $backup = $_.FullName + ".pre-hearthstone-mode.bak"
            if (-not (Test-Path -LiteralPath $backup)) {
                Copy-Item -LiteralPath $_.FullName -Destination $backup
            }
            [IO.File]::WriteAllLines($_.FullName, $updated, [Text.UTF8Encoding]::new($false))
            Write-Host "Migrated Deck: $($_.Name)" -ForegroundColor DarkGray
            $script:migratedDecks++
        }
    }
    return $script:migratedDecks
}

if ($Uninstall) {
    Write-Host "Uninstalling DIY cards from Forge..." -ForegroundColor Yellow

    # Remove editions
    if (Test-Path $WorkspaceEditions) {
        Get-ChildItem -Path $WorkspaceEditions -Filter "*.txt" | ForEach-Object {
            $target = Join-Path $ForgeEditions $_.Name
            if (Test-Path $target) {
                Remove-Item -Path $target -Force
                Write-Host "Removed Edition: $($_.Name)" -ForegroundColor DarkGray
            }
        }
    }

    # Remove tokens
    if (Test-Path $WorkspaceTokens) {
        Get-ChildItem -Path $WorkspaceTokens -Filter "*.txt" | ForEach-Object {
            $target = Join-Path $ForgeTokens $_.Name
            if (Test-Path $target) {
                Remove-Item -Path $target -Force
                Write-Host "Removed Token: $($_.Name)" -ForegroundColor DarkGray
            }
        }
    }

    # Remove cards (recursively find .txt under workspace cards, delete from Forge cards)
    if (Test-Path $WorkspaceCards) {
        Get-ChildItem -Path $WorkspaceCards -Recurse -Filter "*.txt" | ForEach-Object {
            # Compute relative path from workspace cards root
            $relPath = $_.FullName.Substring($WorkspaceCards.Length + 1)
            $target = Join-Path $ForgeCards $relPath
            if (Test-Path $target) {
                Remove-Item -Path $target -Force
                Write-Host "Removed Card: $relPath" -ForegroundColor DarkGray
            }
        }
    }

    if (Test-Path $WorkspacePictures) {
        Get-ChildItem -Path $WorkspacePictures -Recurse -File | ForEach-Object {
            $relPath = $_.FullName.Substring($WorkspacePictures.Length + 1)
            $target = Join-Path $ForgeCardPictures $relPath
            if (Test-Path $target) {
                Remove-Item -Path $target -Force
                Write-Host "Removed Card Picture: $relPath" -ForegroundColor DarkGray
            }
        }
    }

    if (Test-Path $WorkspaceTokenPictures) {
        Get-ChildItem -Path $WorkspaceTokenPictures -Recurse -File | ForEach-Object {
            $relPath = $_.FullName.Substring($WorkspaceTokenPictures.Length + 1)
            $target = Join-Path $ForgeTokenPictures $relPath
            if (Test-Path $target) {
                Remove-Item -Path $target -Force
                Write-Host "Removed Token Picture: $relPath" -ForegroundColor DarkGray
            }
        }
    }

    Write-Host "Uninstall complete." -ForegroundColor Green
    exit 0
}

Write-Host "Syncing DIY cards to Forge AppData directory..." -ForegroundColor Cyan
Write-Host "Destination: $ForgeCustomDir" -ForegroundColor Gray

# Ensure target directories exist
$dirs = @($ForgeCards, $ForgeEditions, $ForgeTokens, $ForgeCardPictures, $ForgeTokenPictures)
foreach ($dir in $dirs) {
    if (-not (Test-Path $dir)) {
        New-Item -ItemType Directory -Path $dir | Out-Null
        Write-Host "Created Directory: $dir" -ForegroundColor DarkGray
    }
}

# Remove scripts retired in favor of engine-owned game modes. This avoids
# leaving stale cards behind when synchronizing an existing Forge profile.
foreach ($relPath in $RetiredCardPaths) {
    $retiredTarget = Join-Path $ForgeCards $relPath
    if (Test-Path -LiteralPath $retiredTarget) {
        Remove-Item -LiteralPath $retiredTarget -Force
        Write-Host "Removed Retired Card: $relPath" -ForegroundColor DarkGray
    }
}
$migratedDecks = 0
Remove-RetiredHearthstoneCardFromDecks | Out-Null

# Copy Editions
if (Test-Path $WorkspaceEditions) {
    Get-ChildItem -Path $WorkspaceEditions -Filter "*.txt" | ForEach-Object {
        $dest = Join-Path $ForgeEditions $_.Name
        Copy-Item -Path $_.FullName -Destination $dest -Force
        Write-Host "Synced Edition: $($_.Name)" -ForegroundColor Gray
    }
}

# Copy Tokens
if (Test-Path $WorkspaceTokens) {
    Get-ChildItem -Path $WorkspaceTokens -Filter "*.txt" | ForEach-Object {
        $dest = Join-Path $ForgeTokens $_.Name
        Copy-Item -Path $_.FullName -Destination $dest -Force
        Write-Host "Synced Token: $($_.Name)" -ForegroundColor Gray
    }
}

# Copy Cards (maintain folder structure)
if (Test-Path $WorkspaceCards) {
    Get-ChildItem -Path $WorkspaceCards -Recurse -Filter "*.txt" | ForEach-Object {
        # Compute relative path
        $relPath = $_.FullName.Substring($WorkspaceCards.Length + 1)
        $destFile = Join-Path $ForgeCards $relPath
        
        # Ensure parent folder in Forge exists
        $destParent = Split-Path $destFile -Parent
        if (-not (Test-Path $destParent)) {
            New-Item -ItemType Directory -Path $destParent | Out-Null
        }

        Copy-Item -Path $_.FullName -Destination $destFile -Force
        Write-Host "Synced Card: $relPath" -ForegroundColor Gray
    }
}

# Copy card pictures to Forge's default local image cache, preserving the
# edition/name path expected by Forge's image lookup.
if (Test-Path $WorkspacePictures) {
    Get-ChildItem -Path $WorkspacePictures -Recurse -File | ForEach-Object {
        $relPath = $_.FullName.Substring($WorkspacePictures.Length + 1)
        $destFile = Join-Path $ForgeCardPictures $relPath
        $destParent = Split-Path $destFile -Parent
        if (-not (Test-Path $destParent)) {
            New-Item -ItemType Directory -Path $destParent | Out-Null
        }

        Copy-Item -Path $_.FullName -Destination $destFile -Force
        Write-Host "Synced Card Picture: $relPath" -ForegroundColor Gray
    }
}

# Copy custom token pictures to the token image cache. The filename must match
# the TokenScript filename because Forge derives the token image key from it.
if (Test-Path $WorkspaceTokenPictures) {
    Get-ChildItem -Path $WorkspaceTokenPictures -Recurse -File | ForEach-Object {
        $relPath = $_.FullName.Substring($WorkspaceTokenPictures.Length + 1)
        $destFile = Join-Path $ForgeTokenPictures $relPath
        $destParent = Split-Path $destFile -Parent
        if (-not (Test-Path $destParent)) {
            New-Item -ItemType Directory -Path $destParent | Out-Null
        }

        Copy-Item -Path $_.FullName -Destination $destFile -Force
        Write-Host "Synced Token Picture: $relPath" -ForegroundColor Gray
    }
}

Write-Host "Sync complete! Custom cards are ready to play in Forge." -ForegroundColor Green
Write-Host "Remember to restart Forge (or reload via Developer Mode) to see the changes." -ForegroundColor Yellow
