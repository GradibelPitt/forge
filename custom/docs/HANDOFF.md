# Forge DIY 项目交接简报

本文用于向新的维护者或 Agent 交接 Forge DIY 的卡牌制作、验证、部署、Git 发布和 Windows 启动链。当前源码、自动测试、本机部署、Git 发布和客户端实测是彼此独立的状态，不得互相替代。

## 交接锚点

截至 2026-07-24：

- Forge 源码分支：`GradibelPitt/forge` 的 `diy`
- Forge 功能基线（本交接文档提交前）：`c83d1a8800d6f6b6d5ea2d21ff093b0b23f44480`
- Runtime 分支：`GradibelPitt/forge-diy-runtime` 的 `main`
- Runtime 提交：`56d073acef4caf98e08924ce91187a8c50fb2135`
- Runtime Build ID：`20260724-diy-content-sync`
- `manifest-critical.sha256`：199 项
- 最近发布验证：DIY Python 全量测试 338/338；runtime `SCRIPT_TESTS=OK`；公开远端独立克隆验证通过
- 当前 `moduleOverlays` 为空；运行包直接使用桌面聚合 JAR

上述功能基线、runtime 提交和数量是交接时的快照。后续发布必须以当前 Git HEAD、`release.json`、`BUILD-ID.txt` 和实际测试结果为准。

## 权威源与部署边界

| 内容 | 权威源 | 部署或发布目标 |
|---|---|---|
| DIY 卡牌脚本 | `custom/cards/` | `%APPDATA%\Forge\custom\cards`；runtime `app/managed/custom/cards` |
| PH01/TEST 版本 | `custom/editions/` | `%APPDATA%\Forge\custom\editions`；runtime `app/managed/custom/editions` |
| Token | `custom/tokens/` | `%APPDATA%\Forge\custom\tokens`；runtime `app/managed/custom/tokens` |
| 卡图 | `custom/cards/pictures/` | `%LOCALAPPDATA%\Forge\Cache\pics\cards`；runtime 受管卡图 |
| Token 图 | `custom/tokens/pictures/` | `%LOCALAPPDATA%\Forge\Cache\pics\tokens`；runtime 受管 Token 图 |
| 简体中文卡牌资源 | `forge-gui/res/languages/cardnames-zh-CN.txt` | runtime `app/res/languages/cardnames-zh-CN.txt` |
| Java 引擎 | Forge 各 Java 模块 | 模块 overlay 或桌面聚合 JAR |
| 玩家运行包 | `forge-diy-runtime` | 朋友机器的 `%LOCALAPPDATA%\ForgeDIY\repo` |

`forge-latest/custom` 是 DIY 内容、版本、图片、测试与文档的权威源；AppData、图片缓存、Maven `target` 和 runtime `app` 都是部署或发布产物，不应作为优先编辑位置。

## 当前卡牌制作与更新流程

### 1. 确认需求与实现入口

1. 读取 `AGENTS.md`，再按任务读取 `PROJECT.md`、`ARCHITECTURE.md`、`CARDS.md`、`KEYWORDS.md`、`VERIFICATION.md` 和相关 `reference/`。
2. 检查当前卡牌脚本、Forge Java 源码和官方卡牌范例。
3. 优先使用已存在的 Forge DSL；只有 DSL 确实无法实现时才改 Java。
4. 不得发明 Ability 参数、触发器、过滤器、关键字或替换效应字段。

### 2. 测试驱动与最小实现

1. 新卡、新功能或修复先写能够正确失败的目标契约测试。
2. 建立或修改 `cards/<颜色>/<卡名>.txt`。
3. 只实现让目标测试通过的最小逻辑；Oracle/显示文字不能代替 `K:`、`A:`、`T:`、`S:`、`R:`、`SVar:` 或 Java 行为。
4. 所有 `Execute$`、`SubAbility$`、`ReplaceWith$`、`AdditionalAbility$` 引用必须闭合。

### 3. 版本、Token 与中文资源

- 正式 DIY 卡登记到 `editions/Placeholder_Set.txt` 的 `PH01`。
- 内部 `Name:` 含 `test`（不区分大小写）的测试卡只登记到 `editions/Test_Set.txt`。
- 卡牌内部名称、版本表名称和卡图文件名必须逐字一致。
- Token 脚本放在 `tokens/`，Token 图片放在 `tokens/pictures/`。
- 中文名称、类别和规则文字更新到 `forge-gui/res/languages/cardnames-zh-CN.txt`。

当前 `tools/install_to_forge.ps1` 不单独同步 `cardnames-zh-CN.txt`。对外 runtime 发布中文资源时必须使用 `tools/publish_git_payload.ps1 -SyncLocalization`。

### 4. 卡图流程

收到图片后必须先实际打开检查，不能根据文件名、扩展名、分辨率或 `_full` 等字样猜测图片类型。

#### 纯原画

1. 原文件备份到 `tools/card-artwork/`，不得覆盖。
2. 只做确定性裁切和转码，不使用 AI 扩图。
3. 默认裁成横向约 `1.37:1` 的高质量 RGB JPEG。
4. 输出到 `cards/pictures/PH01/<卡名>.artcrop.jpg`。
5. PH01 首条同名记录必须含 `@Custom`，让 Forge 使用动态牌框与 Crop 图片。

#### 完整成品卡图

- 图中已经具有最终牌名、费用、类别、规则文字、P/T 或忠诚度及完整牌框时，保存为 `<卡名>.full.jpg`。
- 单画面完整卡图记录不得加 `@Custom`。
- 只有用户明确要求生成完整扩画牌面时才使用 Card Conjurer。

### 5. 自动验证

至少按改动范围运行：

```powershell
python tools\lint_card.py cards\<颜色>\<卡名>.txt
python -m unittest tests.test_<target>
python -m unittest discover -s tests -p "test_*.py"
```

涉及 Java 时还需要：

- 对应模块的目标 Java 测试；
- 必要的模块回归；
- 构建对应模块或桌面聚合 JAR；
- 用 JAR 内容或 `javap` 确认新类/方法确实进入最终产物。

普通卡牌、图片、版本、Token、中文和纯 DSL 修改不得触发不必要的 JAR 或完整离线 ZIP 重建。

### 6. 本机部署与客户端验证

源内容正确后运行：

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\install_to_forge.ps1
```

随后：

1. 比较源脚本、版本表、Token、卡图与部署副本的 SHA-256。
2. 重启 Forge 或使用可靠的资源重载。
3. 在客户端核对牌框、中文、费用、类别、规则文字和 P/T。
4. 在实际对局中验证目标选择、费用支付、触发、替换效应、随机性和联机语义。

磁盘哈希一致只证明部署字节一致；自动测试通过也不能替代客户端显示或玩法验收。

### 7. Git 保存与对外发布

#### 普通卡牌和数据层修改

- 验证后及时创建本地提交，防止工作丢失。
- 默认按批次、联机节点或用户明确要求再 push。

#### Java/引擎修改

- 完成测试后立即提交并 push 到 `forge:diy`。
- 窄范围单模块修改优先发布对应 overlay JAR。
- 跨模块/API 兼容性变化或 intentional baseline 才重建桌面聚合 JAR。
- 同步更新 runtime，避免联机双方加载不同引擎。

#### Runtime Git 发布

日常内容发布应在 `D:\Forge\forge-diy-runtime` 根目录运行：

```powershell
.\tools\publish_git_payload.ps1 `
  -ForgeRoot D:\Forge\forge-latest `
  -BuildId <build-id> `
  -SyncCustom `
  -SyncLocalization
```

该脚本负责同步受管内容，并刷新：

- `app/BUILD-ID.txt`
- `app/managed/custom/`
- `app/res/languages/cardnames-zh-CN.txt`
- `app/manifest-critical.sha256`
- `release.json`

如果指定 `-Module`，还会更新 `app/overlays/<module>.jar`。

发布前必须先暂存最终 runtime payload，再运行：

```powershell
.\tests\test_scripts.ps1
```

`test_scripts.ps1` 会比较 manifest、磁盘和 Git 索引字节；未暂存的新 payload 可能导致门禁失败。最终 push 后应核对远端 ref，并在高风险或共享发布时从公开 GitHub 做干净克隆验证。

## Windows 启动与安装脚本历史

### 历史阶段：Inno Setup 离线安装器框架

2026-07-12 实现过一套离线安装器构建框架：

- `packaging/build_windows_installer.ps1`
- `packaging/ForgeDIY.iss`
- `packaging/launch_forge_diy.ps1`
- `packaging/install_diy_payload.ps1`
- `packaging/write_manifest.py`
- `tests/test_windows_installer_package.py`

设计流程：

1. 复制最新 `forge.exe`、桌面聚合 JAR、Forge `res`、DIY 卡牌/Token/版本/图片和中文资源到 staging。
2. 使用 `jlink` 生成不污染系统 `PATH` 的私有 Java 17。
3. 生成 `BUILD-ID.txt`、`manifest.sha256` 和 `manifest-critical.sha256`。
4. 使用 Inno Setup 生成按用户安装的中文安装包、桌面/开始菜单快捷方式和卸载入口。

设计目标产物：

- `ForgeDIY-<build-id>-Setup.exe`
- `ForgeDIY-<build-id>-Setup.exe.sha256`
- `ForgeDIY-<build-id>-README.txt`

当前仓库没有保留下来的 `packaging/dist`、Setup EXE 或双目录安装结果。能够确认的是构建框架和契约测试已经实现；不能据此宣称最终 EXE、双目录安装验收或真实双机联机已完成。

### 现役阶段：Git Runtime Launcher

实际持续使用的交付入口位于 `forge-diy-runtime`：

- `一键安装并启动.cmd`
- `强制修复并启动.cmd`
- `ForgeDIY_Repair.bat`
- `bootstrap.ps1`
- `tools/sync_profile.ps1`
- `tools/publish_git_payload.ps1`
- `tests/test_scripts.ps1`
- `tests/test_profile_sync.ps1`

现役执行链：

```text
CMD/BAT
  -> 从 GitHub raw 下载 bootstrap.ps1
  -> 查找或安装 Git
  -> clone/fetch/reset forge-diy-runtime
  -> 校验 app/manifest-critical.sha256
  -> 查找或下载 Java 17
  -> sync_profile.ps1 同步 DIY profile/cache
  -> 创建 Forge DIY.lnk
  -> overlays 优先于聚合 JAR 组成 classpath
  -> 启动 Forge 并记录日志
```

`bootstrap.ps1` 中仍保留一个下载 release ZIP 的旧辅助函数，但当前主执行路径没有调用它。`release.json` 的 delivery 为 `git`，实际运行内容直接来自 Git clone 中的 `app/`。

#### 普通入口

`一键安装并启动.cmd`：

1. 切换 UTF-8；
2. 下载最新 `bootstrap.ps1` 到 `%TEMP%`；
3. 执行 bootstrap；
4. 失败时保留窗口中的错误信息。

这是朋友机器正常安装和更新的首选入口。

#### 强制修复入口

`强制修复并启动.cmd`：

1. 只删除 `%LOCALAPPDATA%\ForgeDIY\repo`；
2. 重新下载 bootstrap；
3. 全新克隆并启动。

它不会删除玩家牌组、存档、Forge 偏好或整个图片缓存。仅在正常入口失败、旧 clone 损坏或 manifest 无法修复时使用。

`ForgeDIY_Repair.bat` 是功能相同的英文 ASCII 兼容版，用于中文文件名、聊天传输或 Windows VM 环境不可靠的情况。

### Bootstrap 当前行为

`bootstrap.ps1`：

- 优先使用现有 Git；否则尝试 Winget 或便携 MinGit；
- 使用 `core.autocrlf=false` 和 Git long paths；
- 克隆或更新到 `%LOCALAPPDATA%\ForgeDIY\repo`；
- manifest 失败时执行干净重克隆；
- 通过 `java.exe` 的捕获输出检测 Java 17+，再用 `javaw.exe` 或 Java 启动；
- 没有 Java 时下载便携 JRE 17 到 `%LOCALAPPDATA%\ForgeDIY\java17`；
- 调用 `sync_profile.ps1`，逐文件复制并校验 cards、editions、tokens 和图片；
- 保留其他 Forge 偏好，只把 `UI_CARD_ART_FORMAT` 设置为 `Crop`；
- 创建桌面 `Forge DIY.lnk`；
- 使用 `-Xmx2048m` 启动；
- 将日志写入 `%LOCALAPPDATA%\ForgeDIY\logs\forge-stdout.log` 和 `forge-stderr.log`；
- 用 10 秒探针检测进程是否立即退出。

10 秒探针只证明 Forge 没有立即退出，不代表主界面完全加载或游戏功能已经验收。

### Gauntlet 启动规避的历史边界

早期 VM 启动排障曾通过删除 runtime 中旧的 Gauntlet `.dat` 文件规避反序列化崩溃。Quest、Puzzle 和 Gauntlet 桌面模式从正常主页与启动初始化路径退役后，该临时删除逻辑已经从当前 bootstrap 移除；不得重新把历史 workaround 当成现役启动步骤。

## 已生成或保留的文件

### Git 中当前有效

- 三个入口：`一键安装并启动.cmd`、`强制修复并启动.cmd`、`ForgeDIY_Repair.bat`
- `bootstrap.ps1`
- `tools/build_release.ps1`
- `tools/sync_profile.ps1`
- `tools/publish_git_payload.ps1`
- `tests/test_scripts.ps1`
- `tests/test_profile_sync.ps1`
- `release.json`
- `app/forge.exe`
- `app/forge-gui-desktop-2.0.14-SNAPSHOT-jar-with-dependencies.jar`
- `app/managed/custom/`
- `app/res/`
- `app/BUILD-ID.txt`
- `app/manifest-critical.sha256`

### 本地历史完整构建产物

维护机的 runtime `out/` 当前保留：

- 8 个 `ForgeDIY-runtime-*.zip`
- 7 个 `ForgeDIY-source-*.zip`
- 多个 `stage-*` 目录
- 最早可见完整构建：`20260712-004204`
- 最晚可见完整 ZIP 构建：`20260716-221200-deck-construction-rules`

这些 `out/` 文件是本机历史构建归档，不属于当前 Git payload，也不是日常内容发布的首选方式。

### 桌面交付副本

历史上曾生成或复制中文 CMD、ASCII BAT 和修复 ZIP 到维护机桌面，并核对过源/桌面 SHA-256。当前仍保留的重命名副本包括：

- `一键安装.cmd`：与仓库 `一键安装并启动.cmd` 字节一致；
- `缓存修复.bat`：与仓库 `ForgeDIY_Repair.bat` 字节一致。

旧名称的强制修复 CMD、`ForgeDIY_Repair.zip` 和 `Forge DIY.lnk` 当前未保留。桌面副本只是方便分发的副本，runtime 仓库中的文件才是权威来源。

### 启动时在朋友机器生成

- `%TEMP%\forge-diy-bootstrap.ps1`
- `%LOCALAPPDATA%\ForgeDIY\repo`
- `%LOCALAPPDATA%\ForgeDIY\java17`（仅无合格 Java 时）
- `%LOCALAPPDATA%\ForgeDIY\tools\mingit`（仅无 Git 且安装回退时）
- `%LOCALAPPDATA%\ForgeDIY\logs\forge-stdout.log`
- `%LOCALAPPDATA%\ForgeDIY\logs\forge-stderr.log`
- 桌面 `Forge DIY.lnk`
- `%APPDATA%\Forge\custom` 中的受管 DIY 内容
- `%LOCALAPPDATA%\Forge\Cache\pics` 中的受管 DIY 卡图和 Token 图

## 交接验收边界

- 普通更新先使用 `一键安装并启动.cmd`；不应先手工清缓存。
- 强制修复只用于正常更新失败、clone 损坏或 manifest 不一致。
- `publish_git_payload.ps1` 只生成/同步 payload 和元数据，不负责测试、Git commit、push 或客户端验收。
- `tools/build_release.ps1` 用于明确要求的完整 ZIP 发布；日常卡牌或窄模块更新不应调用。
- manifest、脚本测试、Git 远端一致和 fresh clone 不能代替实际客户端显示、玩法或联机验收。
- 客户端验收必须记录实际加载的 Build ID、JAR/overlay、关键卡牌行为和双方版本一致性。
