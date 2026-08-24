param(
    [switch]$VerifyOnly,
    [switch]$IgnoreSystemJava
)

$ErrorActionPreference = "Stop"
$AppRoot = $PSScriptRoot
$CriticalManifest = Join-Path $AppRoot "manifest-critical.sha256"
$JarName = "forge-gui-desktop-2.0.14-SNAPSHOT-jar-with-dependencies.jar"
$JarPath = Join-Path $AppRoot $JarName
$PrivateJava = Join-Path $AppRoot "runtime\bin\javaw.exe"

function Show-FatalError([string]$Message) {
    Add-Type -AssemblyName PresentationFramework -ErrorAction SilentlyContinue
    [System.Windows.MessageBox]::Show($Message, "Forge DIY 启动失败", "OK", "Error") | Out-Null
}

function Test-Manifest([string]$ManifestPath) {
    if (-not (Test-Path -LiteralPath $ManifestPath)) { return $false }
    foreach ($line in Get-Content -LiteralPath $ManifestPath -Encoding UTF8) {
        if ([string]::IsNullOrWhiteSpace($line)) { continue }
        if ($line -notmatch '^([0-9A-Fa-f]{64}) \*(.+)$') { return $false }
        $expected = $Matches[1].ToUpperInvariant()
        $relative = $Matches[2].Replace('/', '\')
        $file = Join-Path $AppRoot $relative
        if (-not (Test-Path -LiteralPath $file -PathType Leaf)) { return $false }
        $actual = (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash
        if ($actual -ne $expected) { return $false }
    }
    return $true
}

function Get-JavaMajor([string]$JavaExe) {
    if (-not (Test-Path -LiteralPath $JavaExe -PathType Leaf)) { return 0 }
    try {
        $output = (& $JavaExe -version 2>&1 | Out-String)
        if ($output -match 'version\s+"(?:(1)\.)?(\d+)') {
            if ($Matches[1] -eq '1') { return [int]$Matches[2] }
            return [int]$Matches[2]
        }
    } catch { }
    return 0
}

function Find-SystemJava17 {
    $candidates = New-Object System.Collections.Generic.List[string]
    if ($env:JAVA_HOME) { $candidates.Add((Join-Path $env:JAVA_HOME "bin\javaw.exe")) }
    $command = Get-Command javaw.exe -ErrorAction SilentlyContinue
    if ($command) { $candidates.Add($command.Source) }
    foreach ($root in @(
        'HKLM:\SOFTWARE\JavaSoft\JDK',
        'HKLM:\SOFTWARE\Eclipse Adoptium\JDK',
        'HKLM:\SOFTWARE\WOW6432Node\JavaSoft\JDK'
    )) {
        if (-not (Test-Path $root)) { continue }
        Get-ChildItem $root -ErrorAction SilentlyContinue | ForEach-Object {
            $home = (Get-ItemProperty $_.PSPath -ErrorAction SilentlyContinue).JavaHome
            if ($home) { $candidates.Add((Join-Path $home "bin\javaw.exe")) }
        }
    }
    foreach ($candidate in $candidates | Select-Object -Unique) {
        if ((Get-JavaMajor $candidate) -ge 17) { return $candidate }
    }
    return $null
}

try {
    if (-not (Test-Manifest $CriticalManifest)) {
        throw "关键文件校验失败。请重新运行同一版本的 Forge DIY 安装器。"
    }

    & (Join-Path $AppRoot "install_diy_payload.ps1") -Quiet

    $java = $null
    if (-not $IgnoreSystemJava) { $java = Find-SystemJava17 }
    if (-not $java) {
        if ((Get-JavaMajor $PrivateJava) -lt 17) {
            throw "没有找到 Java 17 或更高版本，安装包内的私有 Java 也不可用。请重新安装。"
        }
        $java = $PrivateJava
    }

    $overlayRoot = Join-Path $AppRoot "overlays"
    $overlayJars = @()
    if (Test-Path -LiteralPath $overlayRoot -PathType Container) {
        $overlayJars = @(Get-ChildItem -LiteralPath $overlayRoot -Filter "*.jar" -File |
            Sort-Object Name | Select-Object -ExpandProperty FullName)
    }
    $classPathEntries = @($overlayJars) + @($JarPath)
    $classPath = [string]::Join([IO.Path]::PathSeparator, $classPathEntries)

    if ($VerifyOnly) {
        Write-Output "JAVA=$java"
        Write-Output "JAVA_MAJOR=$(Get-JavaMajor $java)"
        Write-Output "BUILD_ID=$(Get-Content (Join-Path $AppRoot 'BUILD-ID.txt') -Raw)"
        Write-Output "CLASSPATH=$classPath"
        exit 0
    }

    $arguments = @(
        '-Xmx4096m',
        '-Dio.netty.tryReflectionSetAccessible=true',
        '-Dfile.encoding=UTF-8',
        '-cp', $classPath,
        'forge.view.Main'
    )
    Start-Process -FilePath $java -ArgumentList $arguments -WorkingDirectory $AppRoot
} catch {
    Show-FatalError $_.Exception.Message
    exit 1
}
