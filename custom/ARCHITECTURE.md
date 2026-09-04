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
| 中文卡牌资源 | `D:\Forge\forge-latest\forge-gui\res\languages\cardnames-zh-CN.txt` | 开发客户端直接读取该文件；运行仓库使用 `D:\Forge\forge-diy-runtime\app\res\languages\cardnames-zh-CN.txt` | `publish_git_payload.ps1 -SyncCustom` 会自动同步运行版简中资源 |
| 模块 overlay JAR | Java 源码与 Maven 配置 | `D:\Forge\forge-diy-runtime\app\overlays\<module>.jar` | Java 小改的默认精准注入产物；只构建和发布受影响模块 |
| 桌面聚合 JAR | Java 源码与 Maven 配置 | `forge-gui-desktop\target\forge-gui-desktop-2.0.14-SNAPSHOT-jar-with-dependencies.jar` | 仅跨模块/API、依赖、资源打包边界或明确新基线时重建 |
| DIY 源码远端 | `D:\Forge\forge-latest\` | `https://github.com/GradibelPitt/forge` 的 `diy` 分支 | 每次卡牌或精准补丁验证完成后立即 commit 并 push |
| 玩家运行仓库 | overlay/聚合 JAR、`forge-gui/res` 与 `custom` 受管内容 | `https://github.com/GradibelPitt/forge-diy-runtime` 的 `main` 分支 | 每次制卡完成后同步 payload、门禁、commit、push，并核对远端 ref |

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
- `forge-game/src/main/java/forge/game/card/CardFactoryUtil.java`、`card/Card.java`、`spellability/SpellAbilityRestriction.java`、`GameAction.java` 与 `ability/effects/SetStateEffect.java`：奥秘的 `{1}{U}{U}` 隐藏施放、共用 `蓝色奥秘` 背面、正面施放禁令、任意进战场自动背面、仅对手回合免费翻面，以及翻面后牺牲并结算 `MysteryEffect`。`蓝色奥秘` 是 `TOKEN_HS` #8 的真实卡牌；`forge-core/.../ImageKeys.java` 将其他奥秘的背面统一映射为其卡牌键 `c:蓝色奥秘|TOKEN_HS|8`，并由 `cards/pictures/TOKEN_HS/蓝色奥秘.artcrop.jpg` 动态绘制完整卡面。`forge-gui-desktop/.../CardPanel.java` 与 `forge/CachedCardImage.java` 保证牌面朝下时战场牌框固定使用这一背面键，而操控者悬停详情仍可读取保留全部效果的真实正面；`forge-gui/res/lists/TypeLists.txt` 登记 `Mystery` 结界子类别。
- `forge-game/src/main/java/forge/game/keyword/Boarding.java`：登船阈值、受伤角色统计与即时入场。
- `forge-game/src/main/java/forge/game/GameAction.java`：伤害批次及区域移动与 Boarding 的集成点；也在任何起手／调度手牌产生前放逐 `GameRule` 牌，并在通用换区路径阻止这类牌离开放逐区。
- `forge-game/src/main/java/forge/game/mulligan/LondonMulligan.java`：伦敦调度以起始手牌数量而非手牌上限为基准，每次把当前手牌洗回并重新抓满起始数量；只在玩家最终保留时，才按累计计数选择牌并依选择顺序置于牌库底，且不会允许下一次调度所需沉底数量超过所抓手牌。
- `forge-game/src/main/java/forge/game/combat/CombatUtil.java`：Superreach 阻挡规则；通过 `StaticAbilityCanBeAttacked` 将显式标记的牌张及炉石模式下的所有对方生物加入现有鹏洛客／战役 `Card` defender 攻击管线，并按具体攻击者复核合法性；`canBlockByRestrictions` 提供不受横置与一般“不能阻挡”状态影响的成对阻挡限制，供炉石生物攻击目标反向复用；在 `PhaseHandler` 标记的风怒额外战斗中只允许具有 `Windfury` 的生物攻击。
- `forge-game/src/main/java/forge/game/phase/PhaseHandler.java` 与 `ExtraPhase.java`：复用完整战斗阶段组建立每回合至多一次的风怒额外战斗，开始时重置主动牌手的风怒生物，并以额外阶段元数据限制该战斗而不影响其他来源创建的额外战斗。
- `forge-game/src/main/java/forge/game/HearthstoneMode.java`：独立炉石模式的维持选地与持久伤害规则；`StaticAbilityCanBeAttacked` 在该模式下让每个生物自动成为攻击目标，但具体攻击者必须满足“若双方角色互换，该攻击者能阻挡目标”的成对限制，因此自动继承飞行／延势、马术、影遁、恐惧、威吓、保护等现有规则，不建立强制阻挡关系。`Game`／`Player` 负责开局生命、手牌上限和疲劳，`PhaseHandler` 接入维持与 cleanup 时点。
- `forge-game/src/main/java/forge/game/GameType.java` 与 `forge-core/src/main/java/forge/deck/DeckFormat.java`：炉石模式注册及至少 30 张主牌的构筑格式；桌面大厅入口由 `forge-gui-desktop/.../VLobby.java` 提供。
- `forge-game/src/main/java/forge/game/ability/ApiType.java` 与 `ability/effects/CardDiscoverEffect.java`：炉石式发现 AbilityFactory API。
- `forge-game/src/main/java/forge/game/card/CardProperty.java`：除既有当前名称筛选外，提供 `printedNamed<name>` 原始牌名筛选；复制状态改变当前牌名后仍可按关联 `PaperCard` 身份排除物理同名牌。
- `forge-game/src/main/java/forge/game/ability/effects/StealSameNameEffect.java`：按战场、手牌、牌库、坟墓场的固定顺序取得目标对手的第一张同名牌，不创建玩家选择窗口；战场对象更改操控者，其他对象以原实体转移到施放者手中并更改拥有者。
- `forge-game/src/main/java/forge/game/card/GameRuleCard.java`、`player/Player.java` 及 `ability/effects` 下的发现、制造、替换、选牌、直接打出与复制实现：统一保护只用于建立对局规则的牌；抽牌兜底会跳过并放逐，所有数据库生成和复制入口都不会物化或复制它们。
- `forge-game/src/main/java/forge/game/ability/effects/MakeCardEffect.java`：霍格的 `StartingDeckLegendaryPermanents` 专用来源；直接遍历注册主牌 `CardPool` 的计数条目和 `PaperCard` 类型元数据，不扫描全局卡库，也不物化临时候选 `Card`。
- `forge-game/src/main/java/forge/game/ability/AbilityUtils.java`：`Count$StartingDeckDuplicateNonlandNames` 直接读取异能起动牌手注册的主牌 `CardPool`，忽略地牌、按内部名称合并不同版本并返回重复非地牌名数量；供启迪者伊利斯一类起始构筑条件使用。
- 同一文件的 `RandomOpponentStartingDeckNonlands` 来源用于裂魂者阿扎莉娜：读取注册对手的主牌 `CardPool`，排除地牌，随机优先不同牌名后再从剩余副本补足指定数量。
- `forge-game/src/main/java/forge/game/player/Player.java` 与 `ability/effects/TakeFatigueEffect.java`：每位玩家的单调疲劳计数、空牌库抽牌替代路径和可复用 `TakeFatigue` API。
- `forge-game/src/main/java/forge/game/player/Player.java`、`replacement/ReplaceLifeReduced.java`、`ability/effects/LifeLoseEffect.java` 与 `InternalRadiationEffect.java`：为非伤害生命损失携带可选牌张来源，使 `LifeReduced` 的 `ValidSource$` 能区分被选来源；费用、疲劳、法力灼烧及伤害批次不伪造该来源，伤害继续在 `DamageDone` 层按来源处理。
- `forge-game/src/main/java/forge/game/trigger/TriggerDrawnAll.java`、`TriggerType.java` 与 `player/Player.java`：每次 `drawCards` 完成后按实际抓牌集合产生一个 `DrawnAll` 批次触发，同时保留既有 `Drawn` 逐张触发。
- `forge-game/src/main/java/forge/game/player/PlayerSpellRuleRegistry.java`、`cost/CostAdjustment.java`、`mana/ManaCostBeingPaid.java`、`staticability/StaticAbilityManaConvert.java`、`keyword/HarmonyKeyword.java` 与 `ability/effects/GrantSpellRuleEffect.java`：无需卡牌承载的玩家级永久施法规则；按稳定键登记，多玩家结算先对所有去重目标做无副作用预检再统一提交。显式叠加会把每次 `NameSnapshot` 一并写入独立规则，名称范围也参与 Harmony 覆盖／视图刷新判定。`Harmony` 按牌的拥有者注册表在游戏内 `Card`／`CardView` 动态投射可见“调和”标签，并使用专用先有色后通用减费；标签差分刷新公开牌，隐藏牌堆只用 epoch 惰性失效，不修改共享卡库规则对象。费用与支付热路径只按相关牌手规则数执行，不扫描卡牌数据库；既有静态异能兼容扫描保持原行为。
- `forge-game/src/main/java/forge/game/ability/effects/BranchEffect.java`：分支能力解析；需将父能力的替代效应对象传入被选中的子能力。
- `forge-game/src/main/java/forge/game/ability/RecoverableEffectException.java` 与 `AbilityUtils.resolveEffectSafely`：只接住明确保证当前效果尚未写入游戏状态的异常，跳过该效果并记录日志；未标记异常继续上抛。
- `forge-gui/src/main/java/forge/gamemodes/match/HostedMatch.java`、`MatchGameFailureCleanup.java`、`gui/interfaces/IGuiGame.java`、联机 `ProtocolMethod.afterGameFailure`／`GameClientHandler`，以及桌面 `CMatchUI`／`FNavigationBar`、移动端 `MatchController`：未知异常统一清空输入、结束后端游戏、走失败专用 GUI 回调；失败清理逐步执行，某个非致命清理步骤再次抛错也不能阻止后续控制器退出、强制关页和错误弹窗。桌面强制关闭比赛页而不调用 `VMatchUI.onClosing()`，因此不会弹认输确认或留下仍指向已销毁后端的比赛页；远端终止通知不再尝试同步已损坏的游戏图，libGDX 的关页和非阻塞弹窗都调度到 GL 线程。

对应 Java 测试位于各模块的 `src/test/java/`。

## Card images

源图片放在 `cards/pictures/`。当前已存在：

- `cards/pictures/markzul_imp.jpg`
- `cards/pictures/PH01/马克扎尔的小鬼.full.jpg`

`tools/install_to_forge.ps1` 将相对路径原样复制到 `%LOCALAPPDATA%\Forge\Cache\pics\cards`。游戏内按版本查图时，应优先使用 `PH01/<内部名称>.full.jpg` 形式。

本机的 `%LOCALAPPDATA%\Forge\Cache\pics\cards` 是指向 `D:\Forge\Cache\pics\cards` 的 NTFS Junction。Forge 仍使用默认路径，卡图文件实际存储在 D 盘；不要把该机器级部署状态误认为仓库内的可移植配置。

## Chinese localization

权威简体中文卡牌资源是 `forge-gui/res/languages/cardnames-zh-CN.txt`，格式为
`内部名称|中文显示名|中文类别|中文规则文字`。桌面开发客户端通过
`GuiDesktop.getAssetsDir()` 返回的 `../forge-gui/` 读取该外部资源；当前仓库不存在、也不应新增
`forge-gui-desktop/res/languages/cardnames-zh-CN.txt` 这一镜像。

`CardTranslation` 在客户端启动时预载翻译，不会因为卡牌脚本或卡图同步而自动重载。新增或修改卡牌后：

1. 在权威简中资源中新增或更新完整四字段记录；
2. 使用 `custom/tools/install_to_forge.ps1` 同步卡牌、版本与卡图；
3. 发布玩家运行包时使用 `forge-diy-runtime/tools/publish_git_payload.ps1 -SyncCustom`，该开关会自动同步简中资源到 `app/res/languages/cardnames-zh-CN.txt` 并校验两端 SHA-256；
4. 立即分别 commit 并 push 源码与运行仓库，核对两个远端 ref；
5. 重启客户端后才可把中文名称、类别和规则文字记为客户端已验证。

`install_to_forge.ps1` 不负责复制语言文件，因为开发客户端本来就直接读取权威资源；它也不能代替运行仓库的发布步骤。

## Tests and validation tools

- Python：`python -m unittest discover -s tests -p "test_*.py"`。
- 单卡 lint：`python tools/lint_card.py cards/<path>.txt`。
- Forge Core 测试：从 `D:\Forge\forge-latest` 使用 Maven 的 `-pl forge-core -am`。
- Forge Game 测试：从 `D:\Forge\forge-latest` 使用 Maven 的 `-pl forge-game -am`。

具体已验证命令与状态见 [VERIFICATION.md](VERIFICATION.md)。

## Build and runtime artifacts

桌面聚合 JAR 当前存在于：

`D:\Forge\forge-latest\forge-gui-desktop\target\forge-gui-desktop-2.0.14-SNAPSHOT-jar-with-dependencies.jar`

它由 Maven 构建生成。2026-07-11 在补迁 `NewGame` 堆栈路由后重新构建的文件大小为 39,325,385 字节，SHA-256 为 `CF499A80DE72FA5390CF62DB7585BA5E0148985F01AC954ADE49767D8ED392FC`。该值只是历史基线。日常 Java 小改先通过针对性和必要回归测试，再构建受影响模块并以 overlay 精准注入；只有跨越模块/API、依赖或资源打包边界时才重建聚合 JAR。两种路径都必须确认实际运行客户端加载了目标产物。

## Source-to-client data flow

```text
cards / editions / tokens ──install_to_forge.ps1──> %APPDATA%\Forge\custom
cards/pictures ─────────────install_to_forge.ps1──> %LOCALAPPDATA%\Forge\Cache\pics\cards
forge-gui/res/languages/cardnames-zh-CN.txt ──────> 开发客户端（启动时预载）
Forge Java source ──affected-module package──> module JAR ──publish_git_payload.ps1 -Module──> app/overlays
       └─cross-module/package boundary──> forge-gui-desktop aggregate JAR
JAR/overlays + res + DIY managed files ──publish_git_payload.ps1──> forge-diy-runtime/app
                                       └─SyncCustom 自动同步 zh-CN 卡牌资源
validated source ──commit/push──> forge:diy
validated runtime payload ──commit/push──> forge-diy-runtime:main ──bootstrap/update──> player client
```
