param([string]$Updater = (Join-Path $PSScriptRoot '../../forge-gui/src/main/resources/forge/download/diy-updater.ps1'))
$ErrorActionPreference = 'Stop'
. $Updater -LibraryOnly
function Assert($Condition, [string]$Message) { if (-not $Condition) { throw $Message } }
foreach ($status in @('A', 'M', 'D', 'R100')) {
    foreach ($path in @('forge-gui/res/adventure/world/map.tmx', 'forge-gui/res/adventure/world/script.java', 'forge-gui/res/skins/default/sprite_adventure.png')) {
        Assert ((Get-UpdateDecision $status $path) -eq 'skip') "Adventure must be excluded: $status $path"
    }
}
$temp = Join-Path ([IO.Path]::GetTempPath()) ('forge-adventure-test-' + [guid]::NewGuid().ToString('N'))
try {
    Invoke-Native git @('init', $temp) | Out-Null
    Invoke-Git $temp @('config', 'user.name', 'Updater Test') | Out-Null
    Invoke-Git $temp @('config', 'user.email', 'test@example.invalid') | Out-Null
    $excluded = @('forge-gui/res/adventure/world/map.tmx', 'forge-gui/res/adventure/world/config.json', 'forge-gui/res/skins/default/sprite_adventure.png')
    $retained = @('forge-gui/res/cardsfolder/a/adventure_awaits.txt', 'forge-gui/res/editions/Adventures.txt', 'forge-gui/res/skins/default/bg.png', 'forge-game/src/main/java/forge/game/ability/effects/VentureEffect.java', 'forge-game/src/main/java/forge/game/ability/effects/SubgameEffect.java')
    foreach ($path in ($excluded + $retained)) {
        $file = Join-Path $temp $path
        New-Item -ItemType Directory -Path (Split-Path $file) -Force | Out-Null
        Write-Utf8 $file 'fixture'
    }
    Invoke-Git $temp @('add', '.') | Out-Null
    Invoke-Git $temp @('commit', '-m', 'fixture') | Out-Null
    Set-DesktopSparseCheckout $temp
    foreach ($path in $excluded) { Assert (-not (Test-Path (Join-Path $temp $path))) "Sparse checkout materialized Adventure: $path" }
    foreach ($path in $retained) { Assert (Test-Path (Join-Path $temp $path)) "Sparse checkout lost protected resource: $path" }
    foreach ($path in $retained[0..1]) { Assert ((Get-UpdateDecision 'A' $path) -eq 'resource-audit') "Normal card resource excluded: $path" }
    foreach ($path in $retained[3..4]) { Assert ((Get-UpdateDecision 'M' $path) -eq 'merge') "Normal mechanic excluded: $path" }
    # Exercise the real copy function while capturing native arguments on every OS.
    function robocopy { $script:copyArgs = @($args); $global:LASTEXITCODE = 1 }
    Copy-DesktopResources (Join-Path $temp 'old') (Join-Path $temp 'new')
    $source = Join-Path $temp 'old/res'
    Assert ($script:copyArgs -contains '/XD') 'Directory exclusion missing'
    Assert ($script:copyArgs -contains (Join-Path $source 'adventure')) 'Adventure copy exclusion missing'
    Assert ($script:copyArgs -contains (Join-Path $source 'skins/default/sprite_adventure.png')) 'Adventure sprite copy exclusion missing'
    Assert ($script:copyArgs -contains '*.dck') 'Existing deck exclusion lost'
    'DIY_ADVENTURE_EXCLUSION=OK (real sparse checkout, resource policy, copy arguments, Venture/Subgame retained)'
} finally {
    if (Test-Path $temp) { Remove-Item -LiteralPath $temp -Recurse -Force }
}
