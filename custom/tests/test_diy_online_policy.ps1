param([string]$Updater = (Join-Path $PSScriptRoot '../../forge-gui/src/main/resources/forge/download/diy-updater.ps1'))
$ErrorActionPreference='Stop'
. $Updater -LibraryOnly
function Assert($Value,$Message) { if (-not $Value) { throw $Message } }
foreach($status in @('A','M','D','R100')) {
 foreach($path in @('forge-gui/src/main/java/forge/gamemodes/net/NewProtocol.java','forge-gui/src/test/java/forge/gamemodes/net/ServerTest.java','forge-gui-desktop/src/main/java/forge/screens/home/online/NewLobby.java','forge-gui-desktop/src/main/java/forge/screens/home/VLobby.java','forge-gui/src/main/java/forge/interfaces/IGameController.java')) {
  Assert ((Get-UpdateDecision $status $path) -eq 'skip') "Official online payload selected: $status $path"
 }
}
Assert ((Get-UpdateDecision M 'forge-game/src/main/java/forge/game/card/Card.java') -eq 'merge') 'Native game changes unnecessarily frozen'
Assert ((Get-UpdateDecision M 'forge-core/src/main/java/forge/util/CardTranslation.java') -eq 'merge') 'Translation not allowed'
Assert ((Get-UpdateDecision D 'forge-game/src/main/java/forge/game/OldOfficial.java') -eq 'review-delete') 'Native deletion requires a blob check, not unconditional rejection'
$temp=Join-Path ([IO.Path]::GetTempPath()) ('online-policy-'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory $temp | Out-Null
try {
 Invoke-Native git @('init',$temp) | Out-Null
 Invoke-Git $temp @('config','user.name','Policy Test') | Out-Null
 Invoke-Git $temp @('config','user.email','test@example.invalid') | Out-Null
 $native='forge-game/src/main/java/forge/game/OldOfficial.java'
 $network='forge-gui/src/main/java/forge/gamemodes/net/Protocol.java'
 foreach($p in @($native,$network)) { New-Item -ItemType Directory (Split-Path (Join-Path $temp $p)) -Force | Out-Null; Write-Utf8 (Join-Path $temp $p) 'original' }
 Invoke-Git $temp @('add','--',$native,$network) | Out-Null
 Invoke-Git $temp @('commit','-m','base') | Out-Null
 $base=(Invoke-Git $temp @('rev-parse','HEAD')).Trim()
 Invoke-Git $temp @('rm','--',$native) | Out-Null
 Write-Utf8 (Join-Path $temp $network) 'official network update must never apply'
 Invoke-Git $temp @('commit','-am','official') | Out-Null
 $target=(Invoke-Git $temp @('rev-parse','HEAD')).Trim()
 Invoke-Git $temp @('checkout','--detach',$base) | Out-Null
 $plan=@(Get-UpdatePlan $temp $base $target)
 Assert (($plan | Where-Object path -eq $native).decision -eq 'merge') 'Untouched official Java deletion blocked'
 Write-Utf8 (Join-Path $temp $native) 'DIY mechanism'
 Invoke-Git $temp @('commit','-am','custom') | Out-Null
 Assert ((@(Get-UpdatePlan $temp $base $target) | Where-Object path -eq $native).decision -eq 'block') 'DIY Java deletion was allowed'
 Invoke-Git $temp @('checkout','--detach',$base) | Out-Null
 Merge-UpdatePaths $temp $base $target @($network,$native) $temp
 Assert ((Get-Content (Join-Path $temp $network) -Raw) -eq 'original') 'Network changed through direct merge call'
 Assert (-not (Test-Path (Join-Path $temp $native))) 'Native deletion did not apply'
 'DIY_ONLINE_POLICY=OK'
} finally { Remove-Item -LiteralPath $temp -Recurse -Force }
