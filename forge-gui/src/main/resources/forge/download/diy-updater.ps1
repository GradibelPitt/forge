param(
    [string]$Request,
    [switch]$LibraryOnly,
    [switch]$PlanOnly,
    [switch]$NoActivate,
    [string]$SourceRoot,
    [string]$UpstreamTarget = 'master',
    [string]$UpstreamBase = 'ebf900109c882d7027b0651ddcff65a57519237a'
)
$ErrorActionPreference = 'Stop'

function Write-Utf8([string]$Path, [string]$Text) {
    [IO.File]::WriteAllText($Path, $Text, (New-Object Text.UTF8Encoding($false)))
}
function Assert-ChildPath([string]$Root, [string]$Path) {
    $prefix = [IO.Path]::GetFullPath($Root).TrimEnd('\', '/') + [IO.Path]::DirectorySeparatorChar
    $full = [IO.Path]::GetFullPath($Path)
    if (-not $full.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Path is outside update directory: $full"
    }
    # Refuse junctions inside the managed update tree (the install root itself may be migrated).
    $part = $full
    while ($part.Length -ge $prefix.Length) {
        $item = Get-Item -LiteralPath $part -Force -ErrorAction SilentlyContinue
        if ($item -and ($item.Attributes -band [IO.FileAttributes]::ReparsePoint)) {
            throw "Update path contains a junction or symlink: $part"
        }
        $part = [IO.Path]::GetDirectoryName($part)
    }
}
function Quote-Native([string]$Value) {
    # CommandLineToArgvW quoting, including backslashes immediately before quotes/end.
    return '"' + [regex]::Replace([regex]::Replace($Value, '(\\*)"', '$1$1\"'), '(\\+)$', '$1$1') + '"'
}
function Invoke-Native([string]$Exe, [string[]]$Arguments, [string]$OutputFile = '') {
    $info = New-Object Diagnostics.ProcessStartInfo
    $info.FileName = $Exe
    $info.Arguments = ($Arguments | ForEach-Object { Quote-Native $_ }) -join ' '
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    $info.StandardOutputEncoding = New-Object Text.UTF8Encoding($false)
    $info.StandardErrorEncoding = New-Object Text.UTF8Encoding($false)
    $process = New-Object Diagnostics.Process
    $process.StartInfo = $info
    [void]$process.Start()
    $stdout = $process.StandardOutput.ReadToEndAsync()
    $stderr = $process.StandardError.ReadToEndAsync()
    $process.WaitForExit()
    $output = $stdout.GetAwaiter().GetResult()
    $errorText = $stderr.GetAwaiter().GetResult()
    $code = $process.ExitCode
    $process.Dispose()
    if ($code -ne 0) { throw "$Exe exited $code : $errorText $output" }
    if ($OutputFile) { Write-Utf8 $OutputFile $output } else { return $output }
}
function Invoke-Git([string]$Root, [string[]]$Arguments, [string]$OutputFile = '') {
    Invoke-Native 'git' (@('-c', "safe.directory=$Root", '-c', 'core.quotepath=false', '-c', 'core.longpaths=true',
        '-c', 'core.autocrlf=false', '--literal-pathspecs', '-C', $Root) + $Arguments) $OutputFile
}
function Get-UpdateDecision([string]$Status, [string]$Path) {
    if ($Path -match '(^|/)\.\.(/|$)|[:\\\x00-\x1f]' -or $Path.StartsWith('/')) { return 'block' }
    # Official deck examples are outside the allowed update surface, not a reason to block new cards.
    if ($Path -match '(?i)\.dck$') { return 'skip' }
    if ($Path -match '^(pom\.xml|forge-(core|game|ai|gui|gui-desktop)/pom\.xml)$') { return 'block' }
    $engine = $Path -match '^forge-(game|ai)/src/(main|test)/java/.+\.java$' -or
        $Path -match '^forge-core/src/(main|test)/java/forge/(card|deck|item|mana)/.+\.java$' -or
        $Path -match '^forge-core/src/(main|test)/java/forge/(CardStorageReader|StaticData)\w*\.java$' -or
        $Path -match '^forge-gui/src/(main|test)/java/forge/(player|ai)/.+\.java$'
    if ($engine) {
        if ($Status -eq 'A' -or $Status -eq 'M') { return 'merge' }
        return 'block'
    }
    # UI, skins, translations, custom/, updater, launchers and build machinery remain DIY-owned.
    if ($Path -match '^forge-gui/res/cardsfolder/.+\.txt$' -and $Status -eq 'A') { return 'merge' }
    if ($Path -match '^forge-gui/res/editions/[^/]+\.txt$' -and $Status -in @('A', 'M')) { return 'merge' }
    return 'skip'
}
function Get-UpdatePlan([string]$Root, [string]$Base, [string]$Target) {
    $rows = Invoke-Git $Root @('diff', '--name-status', '--no-renames', $Base, $Target)
    $plan = @()
    foreach ($row in ($rows -split "`n")) {
        if (-not $row.Trim()) { continue }
        $fields = $row.TrimEnd("`r") -split "`t", 2
        $decision = Get-UpdateDecision $fields[0] $fields[1]
        if ($fields[1] -eq 'pom.xml' -and $fields[0] -eq 'M') {
            # A release label alone does not require replacing DIY build configuration.
            $oldPom = Invoke-Git $Root @('show', "${Base}:pom.xml")
            $newPom = Invoke-Git $Root @('show', "${Target}:pom.xml")
            if (($oldPom -replace '<versionCode>[^<]+</versionCode>', '<versionCode/>') -eq
                ($newPom -replace '<versionCode>[^<]+</versionCode>', '<versionCode/>')) { $decision = 'skip' }
        }
        $plan += [pscustomobject]@{status=$fields[0]; path=$fields[1]; decision=$decision}
    }
    return $plan
}
function Merge-UpdatePaths([string]$Root, [string]$Base, [string]$Target, [string[]]$Paths, [string]$Job) {
    for ($offset = 0; $offset -lt $Paths.Count; $offset += 30) {
        $batch = @($Paths | Select-Object -Skip $offset -First 30)
        $patch = Join-Path $Job "upstream-$offset.patch"
        Invoke-Git $Root (@('diff', '--binary', '--full-index', '--no-renames', '--diff-filter=AM', $Base, $Target, '--') + $batch) $patch
        # apply --3way uses the original blob IDs and keeps non-overlapping DIY modifications.
        # Any conflict occurs only in this disposable source tree; no runtime is touched.
        Invoke-Git $Root @('apply', '--3way', '--index', '--whitespace=nowarn', $patch) | Out-Null
    }
}
function Get-JavaHome([string]$Preferred, [string]$Tools) {
    $choices = @($Preferred, $env:JAVA_HOME)
    $javac = Get-Command javac.exe -ErrorAction SilentlyContinue
    if ($javac) { $choices += Split-Path (Split-Path $javac.Source) }
    foreach ($dir in @((Join-Path $env:LOCALAPPDATA 'Programs/Eclipse Adoptium'), (Join-Path $env:ProgramFiles 'Eclipse Adoptium'), (Join-Path $Tools 'jdk'))) {
        if (Test-Path -LiteralPath $dir) { $choices += @(Get-ChildItem -LiteralPath $dir -Directory -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName) }
    }
    foreach ($candidate in $choices) {
        if ($candidate -and (Test-Path -LiteralPath (Join-Path $candidate 'bin/javac.exe'))) {
            $version = Invoke-Native (Join-Path $candidate 'bin/javac.exe') @('-version')
            if ($version -match 'javac 17\.') { return $candidate }
        }
    }
    Write-Host 'Downloading a verified Java 17 compiler...'
    $assets = Invoke-RestMethod 'https://api.adoptium.net/v3/assets/latest/17/hotspot?architecture=x64&image_type=jdk&os=windows&vendor=eclipse'
    $package = @($assets)[0].binary.package
    if ($package.link -notmatch '^https://github\.com/adoptium/' -or $package.checksum -notmatch '^[a-fA-F0-9]{64}$') { throw 'Unexpected JDK download metadata.' }
    $zip = Join-Path $Tools 'jdk.zip'
    Invoke-WebRequest -UseBasicParsing $package.link -OutFile $zip
    if ((Get-FileHash -LiteralPath $zip -Algorithm SHA256).Hash -ne $package.checksum) { throw 'JDK checksum mismatch.' }
    $jdkRoot = Join-Path $Tools 'jdk'
    Expand-Archive -LiteralPath $zip -DestinationPath $jdkRoot -Force
    return Get-JavaHome '' $Tools
}
function Get-Maven([string]$Tools) {
    $command = Get-Command mvn.cmd -ErrorAction SilentlyContinue
    if ($command) { return $command.Source }
    $maven = Join-Path $Tools 'apache-maven-3.9.16/bin/mvn.cmd'
    if (-not (Test-Path -LiteralPath $maven)) {
        Write-Host 'Downloading verified Maven build tools...'
        $url = 'https://archive.apache.org/dist/maven/maven-3/3.9.16/binaries/apache-maven-3.9.16-bin.zip'
        $zip = Join-Path $Tools 'maven.zip'
        $checksum = ((Invoke-WebRequest -UseBasicParsing ($url + '.sha512')).Content -split '\s+')[0]
        if ($checksum -notmatch '^[a-fA-F0-9]{128}$') { throw 'Invalid Maven checksum metadata.' }
        Invoke-WebRequest -UseBasicParsing $url -OutFile $zip
        if ((Get-FileHash -LiteralPath $zip -Algorithm SHA512).Hash -ne $checksum) { throw 'Maven checksum mismatch.' }
        Expand-Archive -LiteralPath $zip -DestinationPath $Tools -Force
    }
    return $maven
}
function Assert-DiyClasses([string]$Jar) {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [IO.Compression.ZipFile]::OpenRead($Jar)
    try {
        foreach ($name in @('forge/game/ability/effects/CardDiscoverEffect.class',
            'forge/game/ability/effects/CardDiscoverCandidateFilter.class', 'forge/game/keyword/Boarding.class',
            'forge/game/keyword/HarmonyKeyword.class', 'forge/game/player/PlayerSpellRuleRegistry.class',
            'forge/download/DiyUpdateBridge.class', 'forge/download/diy-updater.ps1')) {
            if (-not $zip.GetEntry($name)) { throw "Compiled DIY component missing: $name" }
        }
    } finally { $zip.Dispose() }
}
function Write-ActivePointer([string]$Root, $Pointer) {
    $active = Join-Path $Root 'active.json'
    $next = Join-Path $Root ('active-' + [guid]::NewGuid().ToString('N') + '.json')
    Assert-ChildPath $Root $next
    Write-Utf8 $next ($Pointer | ConvertTo-Json -Depth 8)
    if (Test-Path -LiteralPath $active) {
        [IO.File]::Replace($next, $active, (Join-Path $Root ('previous-' + [guid]::NewGuid().ToString('N') + '.json')))
    } else { [IO.File]::Move($next, $active) }
}

if ($LibraryOnly) { return }
$job = Split-Path -Parent ([IO.Path]::GetFullPath($Request))
$lock = $null
try {
    $config = Get-Content -LiteralPath $Request -Raw -Encoding UTF8 | ConvertFrom-Json
    $install = [IO.Path]::GetFullPath($config.installRoot)
    $updates = Join-Path $install 'updates'
    Assert-ChildPath $updates $job
    $repo = Join-Path $install 'repo'
    $releaseFile = Join-Path $repo 'release.json'
    $release = Get-Content -LiteralPath $releaseFile -Raw -Encoding UTF8 | ConvertFrom-Json
    $releaseHash = (Get-FileHash -LiteralPath $releaseFile -Algorithm SHA256).Hash
    if ($release.sourceCommit -notmatch '^[a-f0-9]{40}$') { throw 'Invalid DIY source commit.' }
    $activeApp = [IO.Path]::GetFullPath($config.appRoot)
    if ($activeApp -ne [IO.Path]::GetFullPath((Join-Path $repo 'app'))) { Assert-ChildPath (Join-Path $updates 'versions') $activeApp }
    # An OS file lock is released on crash, so it cannot leave a stale PID lock.
    $lock = [IO.File]::Open((Join-Path $updates 'update.lock'), 'OpenOrCreate', 'ReadWrite', 'None')
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    if (-not (Get-Command git.exe -ErrorAction SilentlyContinue)) { throw 'Git is required; run the ForgeDIY launcher once to prepare it.' }
    $generation = [guid]::NewGuid().ToString('N')
    $versionRoot = Join-Path $updates "versions/$generation"
    $source = Join-Path $versionRoot 'source'
    Assert-ChildPath $updates $source
    New-Item -ItemType Directory -Path $versionRoot -Force | Out-Null
    $previous = Join-Path (Split-Path $activeApp) 'update-state.json'
    if (Test-Path -LiteralPath $previous) {
        $state = Get-Content -LiteralPath $previous -Raw -Encoding UTF8 | ConvertFrom-Json
        if ($state.baseReleaseHash -ne $releaseHash) { throw 'DIY baseline changed; restart through the launcher before updating.' }
        $UpstreamBase = $state.upstreamCommit
        $previousSource = Join-Path (Split-Path $activeApp) 'source'
        Assert-ChildPath (Join-Path $updates 'versions') $previousSource
        Invoke-Native git @('-c', 'core.autocrlf=false', '-c', 'core.longpaths=true', 'clone', '--no-hardlinks', $previousSource, $source) | Out-Null
    } elseif ($SourceRoot) {
        # Developer/test input is cloned, never modified in place.
        Invoke-Native git @('-c', 'core.autocrlf=false', '-c', 'core.longpaths=true', 'clone', '--no-hardlinks', $SourceRoot, $source) | Out-Null
    } else {
        Write-Host 'Fetching the exact DIY source baseline...'
        Invoke-Native git @('init', $source) | Out-Null
        Invoke-Git $source @('fetch', '--depth=1', '--no-tags', 'https://github.com/GradibelPitt/forge.git', $release.sourceCommit) | Out-Null
        Invoke-Git $source @('checkout', '-b', 'local-diy', 'FETCH_HEAD') | Out-Null
    }
    Write-Host 'Fetching official source changes...'
    Invoke-Git $source @('fetch', '--depth=1', '--no-tags', 'https://github.com/Card-Forge/forge.git', $UpstreamBase) | Out-Null
    Invoke-Git $source @('fetch', '--depth=1', '--no-tags', 'https://github.com/Card-Forge/forge.git', $UpstreamTarget) | Out-Null
    $target = (Invoke-Git $source @('rev-parse', 'FETCH_HEAD')).Trim()
    $plan = @(Get-UpdatePlan $source $UpstreamBase $target)
    Write-Utf8 (Join-Path $job 'plan.json') (ConvertTo-Json -InputObject $plan -Depth 6)
    $blocked = @($plan | Where-Object { $_.decision -eq 'block' })
    $paths = @($plan | Where-Object { $_.decision -eq 'merge' } | Select-Object -ExpandProperty path)
    Write-Host "Selected $($paths.Count) paths; blocked $($blocked.Count); official target $target"
    if ($PlanOnly) { Write-Utf8 (Join-Path $job 'result.txt') 'Plan completed; no runtime changes.'; exit 0 }
    if ($blocked.Count) { throw "Review required for deleted/renamed engine files or build dependencies: $($blocked.path -join ', '). See plan.json." }
    if (-not $paths.Count) { Write-Utf8 (Join-Path $job 'result.txt') 'No eligible updates. Current DIY version retained.'; exit 0 }
    Merge-UpdatePaths $source $UpstreamBase $target $paths $job
    $deleted = Invoke-Git $source @('diff', '--cached', '--name-only', '--diff-filter=D')
    if ($deleted.Trim()) { throw 'Update would delete existing source files.' }
    Invoke-Git $source @('diff', '--cached', '--check') | Out-Null
    Invoke-Git $source @('-c', 'user.name=ForgeDIY Local Updater', '-c', 'user.email=local-updater@invalid', 'commit', '-m', "Local selective upstream update $target") | Out-Null
    $tools = Join-Path $updates 'tools'
    New-Item -ItemType Directory -Path $tools -Force | Out-Null
    $jdk = Get-JavaHome $config.javaHome $tools
    $maven = Get-Maven $tools
    $env:JAVA_HOME = $jdk
    $env:PATH = (Join-Path $jdk 'bin') + ';' + $env:PATH
    $mavenCache = Join-Path $env:USERPROFILE '.m2/repository'
    if (-not (Test-Path -LiteralPath $mavenCache)) { $mavenCache = Join-Path $tools 'm2' }
    Write-Host 'Compiling and testing the merged DIY engine and preserved DIY desktop...'
    Push-Location $source
    try {
        & $maven '-B' '-ntp' "-Dmaven.repo.local=$mavenCache" '-pl' 'forge-gui-desktop' '-am' 'clean' 'package'
        if ($LASTEXITCODE -ne 0) { throw "Compilation or regression tests failed ($LASTEXITCODE)." }
    } finally { Pop-Location }
    $jar = @(Get-ChildItem -LiteralPath (Join-Path $source 'forge-gui-desktop/target') -File -Filter '*-jar-with-dependencies.jar')
    if ($jar.Count -ne 1) { throw 'Expected exactly one compiled desktop JAR.' }
    Assert-DiyClasses $jar[0].FullName
    $app = Join-Path $versionRoot 'app'
    New-Item -ItemType Directory -Path $app | Out-Null
    # Copy only application resources. Never traverse user profiles, managed custom payload or decks.
    & robocopy (Join-Path $activeApp 'res') (Join-Path $app 'res') /E /XJ /XF *.dck /R:1 /W:1 /NFL /NDL /NJH /NJS /NP
    if ($LASTEXITCODE -gt 7) { throw 'Cannot stage existing application resources.' }
    foreach ($path in $paths) {
        if ($path.StartsWith('forge-gui/res/')) {
            $dest = Join-Path $app ($path.Substring('forge-gui/'.Length))
            Assert-ChildPath $app $dest
            New-Item -ItemType Directory -Path (Split-Path $dest) -Force | Out-Null
            Copy-Item -LiteralPath (Join-Path $source $path) -Destination $dest
        }
    }
    Copy-Item -LiteralPath $jar[0].FullName -Destination $app
    $jarHash = (Get-FileHash -LiteralPath (Join-Path $app $jar[0].Name) -Algorithm SHA256).Hash
    Write-Utf8 (Join-Path $app 'BUILD-ID.txt') ("DIY-upstream-" + $target.Substring(0, 12))
    $state = [ordered]@{schema=1; generation=$generation; upstreamCommit=$target; baseReleaseHash=$releaseHash;
        sourceCommit=(Invoke-Git $source @('rev-parse', 'HEAD')).Trim(); jar=$jar[0].Name; jarHash=$jarHash}
    $hashes = @(Get-ChildItem -LiteralPath $app -Recurse -File | ForEach-Object {
        $relative = $_.FullName.Substring($app.Length + 1).Replace('\', '/')
        (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash + ' *' + $relative
    })
    Write-Utf8 (Join-Path $app 'manifest-critical.sha256') (($hashes | Sort-Object) -join "`n")
    $state['manifestHash'] = (Get-FileHash -LiteralPath (Join-Path $app 'manifest-critical.sha256') -Algorithm SHA256).Hash
    Write-Utf8 (Join-Path $versionRoot 'update-state.json') ($state | ConvertTo-Json)
    if ((Get-FileHash -LiteralPath $releaseFile -Algorithm SHA256).Hash -ne $releaseHash) { throw 'DIY release changed during compilation; staged update was not activated.' }
    if (-not $NoActivate) { Write-ActivePointer $updates $state }
    Write-Utf8 (Join-Path $job 'result.txt') 'DIY update compiled and validated. Save your work, close Forge and use the ForgeDIY launcher to load it. The previous version is retained.'
    Write-Host 'DIY_UPDATE_READY'
} catch {
    $detail = $_.Exception.Message
    Write-Host $detail
    Write-Utf8 (Join-Path $job 'result.txt') ("DIY update stopped; current game files are unchanged.`n" + $detail)
    exit 1
} finally {
    if ($lock) { $lock.Dispose() }
}
