# Forge DIY Architecture

本文件是项目路径和数据流的权威说明。当前状态见 [PROJECT.md](PROJECT.md)，机制入口见 [KEYWORDS.md](KEYWORDS.md)。

## Repository layout

| Concern | Authoritative source | Generated or deployed destination | Notes |
|---|---|---|---|
| DIY 卡牌 | `D:\Forge\forge-latest\custom\cards\` | `%APPDATA%\Forge\custom\cards\` | 安装脚本保持子目录结构 |
| 自定义版本 | `D:\Forge\forge-latest\custom\editions\` | `%APPDATA%\Forge\custom\editions\` | 正式 DIY 为 `PH01`，测试牌为 `TEST` |
| Token | `D:\Forge\forge-latest\custom\tokens\` | `%APPDATA%\Forge\custom\tokens\` | token 脚本同步到自定义目录；`tokens/pictures/` 同步到 `%LOCALAPPDATA%\Forge\Cache\pics\tokens\` |
| 卡图 | `D:\Forge\forge-latest\custom\cards\pictures\` | `%LOCALAPPDATA%\Forge\Cache\pics\cards\` | 版本图使用如 `PH01/<名称>.full.jpg` |
| DIY 测试 | `D:\Forge\forge-latest\custom\tests\` | 无 | Python 契约测试 |
| Forge 源码 | `D:\Forge\forge-latest\` | Maven 构建产物 | Java 引擎的权威来源；上游基线为 `ebf9001` |
| 中文卡牌资源 | 尚待创建的 `translations/cardnames-zh-CN.txt` | Forge `res/languages/cardnames-zh-CN.txt` | 当前仅有设计，未实现自动合并 |
| 桌面 JAR | Java 源码与 Maven 配置 | `forge-gui-desktop\target\forge-gui-desktop-2.0.14-SNAPSHOT-jar-with-dependencies.jar` | 构建产物，不直接手改源逻辑 |
| DIY 源码远端 | `D:\Forge\forge-latest\` | `https://github.com/GradibelPitt/forge` 的 `diy` 分支 | 引擎层更新验证后立即 push |
| 玩家运行仓库 | 构建后的 JAR、`forge-gui/res` 与 `custom` 受管内容 | `https://github.com/GradibelPitt/forge-diy-runtime` | 一键脚本 clone/update；卡牌可批量发布，引擎更新必须同步发布 |

## DIY card and edition sources

- `cards/<color>/`：Forge `.txt` 卡牌脚本。
- `cards/pictures/`：无版本通用图或按版本代码组织的卡图。
- `editions/Placeholder_Set.txt`：显示名为“炉石传说”的 `PH01` 正式 DIY 版本元数据和收藏编号。
- `editions/Test_Set.txt`：内部名称含 `test`（不区分大小写）的测试牌专用 `TEST` 版本。
- `tokens/`：需要时存放 token 脚本；没有 token 时可以不存在。
- `tests/`：检查脚本字段、版本登记和机制契约。
- `tools/new_card.py`、`find_similar.py`、`lint_card.py`：创建、查找官方范例和静态校验工具。

## Forge Java engine

主要模块：

- `forge-core/src/main/java/forge/deck/DeckFormat.java`：构筑合法性和 `IgnoreDeckLimits`。
- `forge-game/src/main/java/forge/game/keyword/Keyword.java`：关键词注册。
- `forge-game/src/main/java/forge/game/keyword/Boarding.java`：登船阈值、受伤角色统计与即时入场。
- `forge-game/src/main/java/forge/game/GameAction.java`：伤害批次及区域移动与 Boarding 的集成点。
- `forge-game/src/main/java/forge/game/combat/CombatUtil.java`：Superreach 阻挡规则。
- `forge-game/src/main/java/forge/game/ability/ApiType.java` 与 `ability/effects/CardDiscoverEffect.java`：炉石式发现 AbilityFactory API。
- `forge-game/src/main/java/forge/game/ability/effects/MakeCardEffect.java`：霍格的 `StartingDeckLegendaryPermanents` 专用来源；直接遍历注册主牌 `CardPool` 的计数条目和 `PaperCard` 类型元数据，不扫描全局卡库，也不物化临时候选 `Card`。
- `forge-game/src/main/java/forge/game/player/Player.java` 与 `ability/effects/TakeFatigueEffect.java`：每位玩家的单调疲劳计数、空牌库抽牌替代路径和可复用 `TakeFatigue` API。
- `forge-game/src/main/java/forge/game/player/PlayerSpellRuleRegistry.java`、`cost/CostAdjustment.java`、`staticability/StaticAbilityManaConvert.java` 与 `ability/effects/GrantSpellRuleEffect.java`：无需卡牌承载的玩家级永久施法规则；按稳定键登记，多玩家结算先对所有去重目标做无副作用预检再统一提交；注册表查询自身在费用与支付热路径只按该牌手规则数执行，不新增或依赖区域、卡牌数据库扫描；既有静态异能兼容扫描保持原行为。
- `forge-game/src/main/java/forge/game/ability/effects/BranchEffect.java`：分支能力解析；需将父能力的替代效应对象传入被选中的子能力。

对应 Java 测试位于各模块的 `src/test/java/`。

## Card images

源图片放在 `cards/pictures/`。当前已存在：

- `cards/pictures/markzul_imp.jpg`
- `cards/pictures/PH01/马克扎尔的小鬼.full.jpg`

`tools/install_to_forge.ps1` 将相对路径原样复制到 `%LOCALAPPDATA%\Forge\Cache\pics\cards`。游戏内按版本查图时，应优先使用 `PH01/<内部名称>.full.jpg` 形式。

本机的 `%LOCALAPPDATA%\Forge\Cache\pics\cards` 是指向 `D:\Forge\Cache\pics\cards` 的 NTFS Junction。Forge 仍使用默认路径，卡图文件实际存储在 D 盘；不要把该机器级部署状态误认为仓库内的可移植配置。

## Chinese localization

Forge 仓库内存在两份简体中文卡牌资源：

- `forge-gui/res/languages/cardnames-zh-CN.txt`
- `forge-gui-desktop/res/languages/cardnames-zh-CN.txt`

格式为 `内部名称|中文显示名|中文类别|中文规则文字`。DIY 设计要求未来以工作区 `translations/cardnames-zh-CN.txt` 为源，通过安装脚本幂等合并并先备份目标。该工作目前尚未实现；不要把直接修改构建资源当作已建立的工作流。

## Tests and validation tools

- Python：`python -m unittest discover -s tests -p "test_*.py"`。
- 单卡 lint：`python tools/lint_card.py cards/<path>.txt`。
- Forge Core 测试：从 `D:\Forge\forge-latest` 使用 Maven 的 `-pl forge-core -am`。
- Forge Game 测试：从 `D:\Forge\forge-latest` 使用 Maven 的 `-pl forge-game -am`。

具体已验证命令与状态见 [VERIFICATION.md](VERIFICATION.md)。

## Build and runtime artifacts

桌面聚合 JAR 当前存在于：

`D:\Forge\forge-latest\forge-gui-desktop\target\forge-gui-desktop-2.0.14-SNAPSHOT-jar-with-dependencies.jar`

它由 Maven 构建生成。2026-07-11 在补迁 `NewGame` 堆栈路由后重新构建的文件大小为 39,325,385 字节，SHA-256 为 `CF499A80DE72FA5390CF62DB7585BA5E0148985F01AC954ADE49767D8ED392FC`。部署引擎改动时必须先通过针对性和回归测试，再构建并确认实际运行客户端使用了新产物。

## Source-to-client data flow

```text
cards / editions / tokens ──install_to_forge.ps1──> %APPDATA%\Forge\custom
cards/pictures ─────────────install_to_forge.ps1──> %LOCALAPPDATA%\Forge\Cache\pics\cards
Forge Java source ──────────Maven package─────────> forge-gui-desktop aggregate JAR
aggregate JAR + res + DIY managed files ──commit/push──> forge-diy-runtime/app
translations (planned) ─────idempotent merge──────> Forge zh-CN language resources
```
