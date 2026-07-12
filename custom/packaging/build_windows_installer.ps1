param(
    [switch]$StageOnly,
    [switch]$SkipRuntime,
    [string]$BuildId = (Get-Date -Format 'yyyyMMdd-HHmmss')
)

$ErrorActionPreference = "Stop"
$CustomRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$RepoRoot = Resolve-Path (Join-Path $CustomRoot "..")
$OutputRoot = Join-Path $RepoRoot "dist"
$WorkRoot = Join-Path $PSScriptRoot "out"
$StageRoot = Join-Path $WorkRoot "stage"
$JarName = "forge-gui-desktop-2.0.14-SNAPSHOT-jar-with-dependencies.jar"
$JarSource = Join-Path $RepoRoot "forge-gui-desktop\target\$JarName"
$ExeSource = Join-Path $RepoRoot "forge-gui-desktop\target\forge.exe"

# Authoritative DIY payload inputs: custom\cards, custom\tokens, custom\editions.
# Deliberately never copy the user's Forge official image cache.
$CustomCards = Join-Path $RepoRoot "custom\cards"
$CustomTokens = Join-Path $RepoRoot "custom\tokens"
$CustomEditions = Join-Path $RepoRoot "custom\editions"
$ZhCn = Join-Path $RepoRoot "forge-gui\res\languages\cardnames-zh-CN.txt"

function Reset-Directory([string]$Path) {
    $resolvedParent = [IO.Path]::GetFullPath((Split-Path $Path -Parent))
    $allowedParent = [IO.Path]::GetFullPath($WorkRoot)
    if (-not $resolvedParent.StartsWith($allowedParent, [StringComparison]::OrdinalIgnoreCase)) {
        throw "拒绝清理工作目录之外的路径: $Path"
    }
    if (Test-Path -LiteralPath $Path) { Remove-Item -LiteralPath $Path -Recurse -Force }
    New-Item -ItemType Directory -Path $Path -Force | Out-Null
}

function Copy-Tree([string]$Source, [string]$Destination) {
    if (-not (Test-Path -LiteralPath $Source)) { throw "缺少打包输入: $Source" }
    New-Item -ItemType Directory -Path $Destination -Force | Out-Null
    & robocopy $Source $Destination /E /COPY:DAT /DCOPY:DAT /R:2 /W:1 /NFL /NDL /NJH /NJS /NP | Out-Null
    if ($LASTEXITCODE -gt 7) { throw "robocopy 复制失败，退出码: $LASTEXITCODE" }
}

function Write-HashManifest([string]$Root, [string]$Path, [string[]]$RelativeFiles) {
    $writer = Join-Path $PSScriptRoot "write_manifest.py"
    & python $writer $Root $Path @($RelativeFiles | Sort-Object -Unique)
    if ($LASTEXITCODE -ne 0) { throw "生成 SHA-256 清单失败。" }
}

Reset-Directory $StageRoot
New-Item -ItemType Directory -Path $OutputRoot -Force | Out-Null

foreach ($required in @($JarSource, $ExeSource, $ZhCn)) {
    if (-not (Test-Path -LiteralPath $required)) { throw "缺少构建产物: $required" }
}

Copy-Item -LiteralPath $JarSource -Destination (Join-Path $StageRoot $JarName)
Copy-Item -LiteralPath $ExeSource -Destination (Join-Path $StageRoot "forge.exe")
Copy-Tree (Join-Path $RepoRoot "forge-gui\res") (Join-Path $StageRoot "res")
Copy-Item -LiteralPath $ZhCn -Destination (Join-Path $StageRoot "res\languages\cardnames-zh-CN.txt") -Force

$ManagedCustom = Join-Path $StageRoot "managed\custom"
Copy-Tree $CustomCards (Join-Path $ManagedCustom "cards")
Copy-Tree $CustomTokens (Join-Path $ManagedCustom "tokens")
Copy-Tree $CustomEditions (Join-Path $ManagedCustom "editions")
Copy-Item -LiteralPath (Join-Path $PSScriptRoot "launch_forge_diy.ps1") -Destination $StageRoot
Copy-Item -LiteralPath (Join-Path $PSScriptRoot "install_diy_payload.ps1") -Destination $StageRoot
[IO.File]::WriteAllText((Join-Path $StageRoot "BUILD-ID.txt"), "$BuildId`r`n", [Text.UTF8Encoding]::new($false))

if (-not $SkipRuntime) {
    $java = (Get-Command java.exe -ErrorAction Stop).Source
    $jdk = Split-Path (Split-Path $java -Parent) -Parent
    $jlink = Join-Path $jdk "bin\jlink.exe"
    if (-not (Test-Path $jlink)) { throw "当前 Java 不包含 jlink: $jdk" }
    $runtime = Join-Path $StageRoot "runtime"
    & $jlink --add-modules ALL-MODULE-PATH --strip-debug --no-header-files --no-man-pages --compress=2 --output $runtime
    if ($LASTEXITCODE -ne 0) { throw "jlink 创建私有 Java 17 失败。" }
}

$critical = @(
    $JarName,
    'BUILD-ID.txt',
    'launch_forge_diy.ps1',
    'install_diy_payload.ps1',
    'res\languages\cardnames-zh-CN.txt'
)
$critical += Get-ChildItem (Join-Path $StageRoot 'managed') -Recurse -File | ForEach-Object {
    $_.FullName.Substring($StageRoot.Length + 1)
}
if (-not $SkipRuntime) { $critical += 'runtime\bin\javaw.exe' }
Write-HashManifest $StageRoot (Join-Path $StageRoot "manifest-critical.sha256") $critical

$writer = Join-Path $PSScriptRoot "write_manifest.py"
& python $writer $StageRoot (Join-Path $StageRoot "manifest.sha256") --all
if ($LASTEXITCODE -ne 0) { throw "生成完整 SHA-256 清单失败。" }

if ($StageOnly) {
    Write-Output "STAGE=$StageRoot"
    exit 0
}

$isccCandidates = @(
    "C:\Program Files (x86)\Inno Setup 6\ISCC.exe",
    "C:\Program Files\Inno Setup 6\ISCC.exe"
)
$iscc = $isccCandidates | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
if (-not $iscc) { throw "找不到 ISCC.exe。请安装 Inno Setup 6。" }

& $iscc "/DStagingDir=$StageRoot" "/DBuildId=$BuildId" "/DDistDir=$OutputRoot" (Join-Path $PSScriptRoot "ForgeDIY.iss")
if ($LASTEXITCODE -ne 0) { throw "Inno Setup 构建失败。" }

$setup = Join-Path $OutputRoot "ForgeDIY-$BuildId-Setup.exe"
$setupHash = (Get-FileHash -LiteralPath $setup -Algorithm SHA256).Hash
[IO.File]::WriteAllText("$setup.sha256", "$setupHash *$(Split-Path $setup -Leaf)`r`n", [Text.UTF8Encoding]::new($false))
$readme = @"
Forge DIY 一键安装包
构建 ID: $BuildId
安装包 SHA-256: $setupHash

双击安装即可。若系统没有 Java 17 或更高版本，程序会自动使用包内私有 Java。
本包包含 DIY 规则、卡牌和自定义图片，不包含 Forge 官方卡图缓存。
两位玩家请使用同一个安装包版本进行联机。
"@
[IO.File]::WriteAllText((Join-Path $OutputRoot "ForgeDIY-$BuildId-README.txt"), $readme, [Text.UTF8Encoding]::new($false))
Write-Output "SETUP=$setup"
