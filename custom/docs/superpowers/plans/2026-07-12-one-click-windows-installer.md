# Forge DIY 一键 Windows 安装器 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 生成一个离线、自带 Java 回退、只携带 DIY 图片且能锁定双方联机关键版本的中文 Windows 安装 EXE。

**Architecture:** PowerShell 构建器从当前工作区的最新桌面产物与 `custom` 权威源组装 staging 目录，生成 SHA-256 受管清单；Windows PowerShell 启动器优先选择合格的系统 Java 17+，否则使用安装目录内由 `jlink` 生成的私有运行时，并在启动前校验关键文件。Inno Setup 将 staging 目录封装成按用户安装的中文 EXE，并负责快捷方式、升级覆盖与卸载。

**Tech Stack:** Maven、Launch4j、PowerShell 5.1、Python unittest、JDK 17 `jdeps/jlink`、Inno Setup 6。

---

### Task 1: 打包契约测试

**Files:**
- Create: `custom/tests/test_windows_installer_package.py`

- [ ] **Step 1:** 写入失败测试，要求存在 `packaging/build_windows_installer.ps1`、`packaging/launch_forge_diy.ps1`、`packaging/ForgeDIY.iss`，并断言构建脚本明确排除官方图片缓存、包含 custom 卡牌/Token/图片、生成 SHA-256 清单和构建 ID。
- [ ] **Step 2:** 断言启动器检查 Java 主版本 17、提供 `runtime/bin/javaw.exe` 回退、校验联机关键清单，并使用聚合 JAR 启动 `forge.view.Main`。
- [ ] **Step 3:** 运行 `python -m unittest tests.test_windows_installer_package -v`，确认因文件不存在而失败。

### Task 2: 启动器与 staging 构建器

**Files:**
- Create: `custom/packaging/launch_forge_diy.ps1`
- Create: `custom/packaging/build_windows_installer.ps1`
- Create: `custom/packaging/ForgeDIY.iss`

- [ ] **Step 1:** 实现启动器：验证 `manifest-critical.sha256`，检测 `javaw.exe -version` 主版本，优先使用合格系统 Java，否则使用 `{app}/runtime/bin/javaw.exe`；失败时显示中文错误并退出。
- [ ] **Step 2:** 实现 staging 构建：复制最新 `forge.exe`、聚合 JAR、Forge 必需 `res` 与卡牌规则脚本；复制 PH01、DIY cards/tokens、DIY 图片和 zh-CN；禁止读取或复制 `%LOCALAPPDATA%/Forge/Cache/pics/cards` 官方缓存。
- [ ] **Step 3:** 使用 `jdeps --ignore-missing-deps --multi-release 17` 推导模块，并用 `jlink` 创建私有 Java 17；若模块推导不足，加入 Forge 桌面所需的明确模块集合。
- [ ] **Step 4:** 生成 `BUILD-ID.txt`、完整 `manifest.sha256` 与启动必需的 `manifest-critical.sha256`；相对路径使用稳定排序。
- [ ] **Step 5:** 实现 Inno Setup：按用户安装、中文界面、创建桌面/开始菜单快捷方式、保留用户牌组/设置/官方缓存、升级时覆盖受管文件、卸载时只移除安装目录。
- [ ] **Step 6:** 运行打包契约测试，确认转绿。

### Task 3: 重建最新引擎与测试

**Files:**
- Build output: `forge-gui-desktop/target/forge-gui-desktop-2.0.14-SNAPSHOT-jar-with-dependencies.jar`
- Build output: `forge-gui-desktop/target/forge.exe`

- [ ] **Step 1:** 从 `custom` 运行 `python -m unittest discover -s tests -p "test_*.py"` 和全部自定义卡 lint。
- [ ] **Step 2:** 使用 repo-local Maven settings 安装改动过的依赖模块并运行相关 Java 测试。
- [ ] **Step 3:** 重新 package `forge-gui-desktop`，要求 `BUILD SUCCESS`，并记录 JAR 与 `forge.exe` 哈希。
- [ ] **Step 4:** 用 `javap` 或 JAR 内容检查确认 `TakeFatigueEffect`、`CardDiscoverEffect`、`Boarding`、最大手牌与 `NewGame` 路由等当前 DIY 引擎代码确实进入聚合 JAR。

### Task 4: 生成安装 EXE

**Files:**
- Create: `dist/ForgeDIY-<build-id>-Setup.exe`
- Create: `dist/ForgeDIY-<build-id>-Setup.exe.sha256`
- Create: `dist/ForgeDIY-<build-id>-README.txt`

- [ ] **Step 1:** 检测 Inno Setup；若未安装，则通过可信发行渠道安装 Inno Setup 6 构建工具。
- [ ] **Step 2:** 执行 `build_windows_installer.ps1` 组装 staging、创建私有运行时并调用 `ISCC.exe`。
- [ ] **Step 3:** 检查最终 EXE 的 SHA-256、文件版本信息与签名状态；未进行代码签名时明确记录 Windows SmartScreen 可能提示“未知发布者”。

### Task 5: 独立安装验证

**Files:**
- Temporary: `custom/packaging/tmp/install-a/`
- Temporary: `custom/packaging/tmp/install-b/`

- [ ] **Step 1:** 以静默参数把同一 EXE 安装到两个独立测试目录，不触碰当前玩家数据。
- [ ] **Step 2:** 在隐藏系统 Java 的测试环境变量条件下运行启动器校验模式，确认选择私有 Java 17。
- [ ] **Step 3:** 对两个安装目录生成联机关键清单并逐字节比较，要求完全一致。
- [ ] **Step 4:** 扫描两个安装目录，确认不存在官方卡图缓存或非 DIY 卡图，且所有 PH01 与 DIY token 图片均存在。
- [ ] **Step 5:** 重复安装到 `install-a`，确认受管文件哈希不变且无旧版重复文件。
- [ ] **Step 6:** 运行最终 DIY 全量测试，更新 `custom/VERIFICATION.md`，只把实际完成的自动化和启动验证写成已完成；真实双机联机保留为人工验收。
