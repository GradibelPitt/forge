param([string]$Guard = (Join-Path $PSScriptRoot '../../forge-gui/src/main/resources/forge/download/DiyProtection.java'))
$ErrorActionPreference='Stop'
. (Join-Path (Split-Path $Guard) 'diy-updater.ps1') -LibraryOnly
$temp=Join-Path ([IO.Path]::GetTempPath()) ('guard-platform-'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory $temp | Out-Null
try {
 $classes=Join-Path $temp 'classes'
 New-Item -ItemType Directory $classes | Out-Null
 Invoke-Native 'javac' @('-encoding','UTF-8','-d',$classes,$Guard) | Out-Null
 $root=Join-Path $temp 'source';$official=Join-Path $temp 'official'
 $dir=Join-Path $root 'forge-game/src/main/java/forge/game'
 New-Item -ItemType Directory $dir -Force | Out-Null
 $officialDir=Join-Path $official 'forge-core/src/main/java/forge'
 New-Item -ItemType Directory $officialDir -Force | Out-Null
 Write-Utf8 (Join-Path $officialDir 'Official.java') 'package forge; public class Official {}'
 $file=Join-Path $dir 'DiyExample.java'
 $code="package forge.game;`npublic class DiyExample {`n public int discover() {`n  return 42;`n }`n}`n"
 Write-Utf8 $file $code
 $catalog=Join-Path $temp 'guard.tsv'
 Invoke-Native 'java' @('-cp',$classes,'DiyProtection','catalog',$root,$official,$catalog) | Out-Null
 Write-Utf8 $file ($code.Replace("`n","`r`n"))
 Invoke-Native 'java' @('-cp',$classes,'DiyProtection','verify',$root,$catalog) | Out-Null
 Write-Utf8 $file ($code.Replace('return 42','return 43'))
 $blocked=$false
 try { Invoke-Native 'java' @('-cp',$classes,'DiyProtection','verify',$root,$catalog) | Out-Null } catch { $blocked=$true }
 if(-not $blocked){throw 'Real DIY mechanism change passed the line-ending guard'}
 Remove-Item -LiteralPath $file
 $blocked=$false
 try { Invoke-Native 'java' @('-cp',$classes,'DiyProtection','verify',$root,$catalog) | Out-Null } catch { $blocked=$true }
 if(-not $blocked){throw 'DIY mechanism deletion passed the guard'}
 'DIY_PROTECTION_PLATFORM=OK (LF/CRLF parity, mutation and deletion rejected)'
} finally { Remove-Item -LiteralPath $temp -Recurse -Force }
