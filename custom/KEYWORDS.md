# Forge DIY Keywords and Engine APIs

## Durability

- **Status:** 已实现为引擎级数值关键字。
- **Player-facing behavior:** 具有耐久 N 的永久物进战场时上面有 N 个耐久指示物；当最后一个耐久指示物从其上移去时，将它牺牲。
- **DSL:** `K:Durability:N`，例如 `K:Durability:2`。
- **Java implementation:** `Keyword.java` 注册数值关键字；`CardFactoryUtil.java` 建立进场放置耐久指示物的替代式效应与最后一个耐久指示物被移去时的牺牲触发器；`CounterEnumType.java` 注册耐久与秘银指示物。
- **Edge cases:** 耐久本身不会按回合自动移去指示物；只有其他费用或效应移去最后一个耐久指示物时才会触发牺牲。

## ColorChoice（有色费用择一减免）

- **Status:** 已实现，供 `ReduceCost` 静态异能使用。
- **DSL:** `ColorChoice$ U B`。对每一个 `Amount`，操控者从费用中仍存在的指定有色法术力符号中选择一个，将其减少；不会同时减少多个颜色，也不会减少无色费用。
- **Java implementation:** `forge-game/.../cost/CostAdjustment.java`。
- **Tests:** `forge-game/src/test/java/forge/game/cost/CostAdjustmentColorChoiceTest.java`。

本文件记录自定义关键词及类似关键词的引擎 API。卡牌清单见 [CARDS.md](CARDS.md)，设计理由见 [docs/DESIGN.md](docs/DESIGN.md)。

## Superreach

- **Status:** 已实现；存在 Java 回归测试，历史记录显示已构建部署。
- **Player-facing behavior:** 可阻挡任意数量攻击者，并忽略由攻击生物自身施加的阻挡限制；不会绕过阻挡者自身或全局限制。
- **DSL:** `K:Superreach`，通常配合 `CanBlockAny$ True` 或卡牌自身的多阻挡能力。
- **Java implementation:** `forge-game/.../keyword/Keyword.java` 注册；`forge-game/.../combat/CombatUtil.java` 判定。
- **Tests:** `forge-game/src/test/java/forge/game/combat/CombatUtilTest.java`；DIY 测试 `tests/test_superreach_cards.py`。
- **Edge cases:** 攻击者具有 `Ignore Superreach` 时不能忽略其限制；不会无视保护、阻挡者资格或全局规则。

## Ignore Superreach

- **Status:** 已实现并由 Superreach 测试覆盖。
- **Player-facing behavior:** 让该攻击者施加的阻挡限制继续约束 Superreach 阻挡者。
- **DSL:** `K:Ignore Superreach`。
- **Java implementation:** `Keyword.java` 注册，`CombatUtil.superreachApplies` 路径检查。
- **Tests:** `CombatUtilTest.java`。
- **Edge cases:** 只关闭 Superreach 对攻击者限制的豁免，不赋予额外不可阻挡能力。

## IgnoreDeckLimits

- **Status:** 已实现；Forge Core 和 DIY 契约测试存在。
- **Player-facing behavior:** 在非指挥官主牌组中将最低主牌数量降为 1，并覆盖普通四张及 `DeckLimit:N` 同名数量限制；不绕过卡池、禁牌、自定义卡开关等其他合法性检查。
- **DSL:** `K:IgnoreDeckLimits`。
- **Java implementation:** `forge-game/.../keyword/Keyword.java` 注册；`forge-core/.../deck/DeckFormat.java` 应用构筑政策。
- **Tests:** `forge-core/src/test/java/forge/deck/DeckFormatIgnoreDeckLimitsTest.java`；`tests/test_deck_limit_override_cards.py`。
- **Edge cases:** 只在能力牌实际位于主牌组时生效；不适用于指挥官特殊规则。

## Boarding

- **Status:** 已实现；Java 和 DIY 测试存在，客户端实测仍应与自动测试分开记录。
- **Player-facing behavior:** 本回合有至少 N 个不同友方角色受过伤害时，具有 Boarding 的牌从手牌或牌库直接进入战场。动作不使用堆叠。
- **DSL:** `K:Boarding:N`，例如 `K:Boarding:3`。
- **Java implementation:** `Keyword.java`、`keyword/Boarding.java`、`GameAction.java`。
- **Tests:** `forge-game/src/test/java/forge/game/keyword/BoardingTest.java`；`tests/test_deck_limit_override_cards.py`。
- **Edge cases:** 按实体 ID 去重；同一角色多次受伤只计一次；对象之后离场或改变操控者不撤销记录；cleanup 清空本回合记录。当前只在完整伤害批次后检查；条件已满足后牌才进入手牌或牌库时不会立即检查，必须等到下一次伤害批次。

## DeckMinimum

- **Status:** 当前源码/卡牌中已使用，历史自动测试已记录。
- **Player-facing behavior:** 改变包含该牌的牌组最低主牌数量。
- **DSL:** `K:DeckMinimum:N`。
- **Java implementation:** Forge Core 构筑合法性与关键词注册路径。
- **Tests:** `tests/test_hearthstone_card.py` 及历史计划所列 Forge Core 测试。
- **Edge cases:** 这是构筑规则，不等同于游戏开始时的区域或生命设置。

## Fatigue

- **Status:** 已实现；用于炉石传说徽记，且可由未来卡牌通过独立 API 复用。
- **Player-facing behavior:** 每位牌手各自维护对局内的疲劳次数。每次 `takeFatigue` 都先将该牌手的次数加一，再失去等同于新次数的生命；该次数永不减少。空牌库抽牌启用疲劳后，每一次逐张抽牌尝试都是一次独立疲劳事件。
- **DSL:** `DB$ TakeFatigue | Defined$ <player>` 对每个受影响牌手触发一次疲劳。`FatigueOnEmptyDraw` 是炉石传说徽记使用的内部玩家关键词，不应作为普通卡牌规则文字。
- **Java implementation:** `forge-game/.../player/Player.java` 的 `takeFatigue()` 与空牌库抽牌路径；`ApiType.TakeFatigue` 和 `ability/effects/TakeFatigueEffect.java` 提供可复用入口。
- **Edge cases:** 双方疲劳各自从 1 计算；一次抓三张会依序产生 1、2、3 点疲劳，下一次空抽为 4 点，而不是按异能或回合合并。疲劳造成生命损失，不是带来源的战斗或法术伤害。

## CardDiscover

- **Status:** 已迁移到 `D:\Forge\forge-latest` 并通过 Forge Game 针对性测试；新版桌面 JAR 已构建，尚未记录其安装或客户端对局实测。
- **Player-facing behavior:** 从指定候选池使用 Forge 现有过滤器筛选，按内部牌名去重并随机展示最多三个选项；玩家选择一张置入手牌。没有候选则不展示。
- **DSL design:** 独立 AbilityFactory API `DB$ CardDiscover`，配合 `Defined$`、`Source$`、`SourceController$`、`ValidCards$`、`OptionCount$` 和 `Destination$`。`RememberChosen$ True` 会记录实际移入目标区域的所选牌，供同一能力链的延迟触发器精确引用；省略时不会改变既有记忆状态。最终参数必须在实现测试中验证，不能因本段设计而跳过解析验证。
- **Java implementation:** `forge-game/.../ability/ApiType.java` 注册，`ability/effects/CardDiscoverEffect.java` 实现；不修改 Forge 已有 MTG `DiscoverEffect`。
- **Tests:** `forge-game/src/test/java/forge/game/ability/effects/CardDiscoverEffectTest.java`；`tests/test_airborne_bandit.py`。
- **Edge cases:** 条件必须复用 `ValidCards$`/`Card.isValid`；同名不同版本不重复；不足三个显示实际数量；数据库来源创建新牌，牌库来源移动实际对象且不洗牌。

## ReplaceCards（批量数据库牌池替换）

- **Status:** 已实现为引擎级 Ability API，供“弃暗投明”等隐藏区批量替换效果使用。
- **DSL:** `DB$ ReplaceCards`，配合 `Defined$`、`Zones$`、`ValidCards$`、`ReplacementValid$` 与必须显式写为 `True` 的 `MatchManaValue$ True`；当前 `Zones$` 只接受 `Hand`／`Library`。缺失、`False` 或其他区域会在任何区域或名称记录副作用前拒绝。只有需要记录替换名称的其他效果才使用可选的 `RememberNames$ True`。
- **Performance:** 复用 `CardDiscoverCandidateFilter` 的轻量 `PaperCard` 过滤；静态候选条件按数据库实例、数据库大小和过滤表达式缓存为法术力值桶。首次建立只遍历一次数据库，不为未选候选创建游戏内 `Card`；同一数据库上的后续结算直接复用缓存。每个玩家区域只做一次线性计划扫描并记录匹配牌的稳定位置，随后每张牌只做对应法术力值桶的常数时间随机索引；没有逐牌区域 `indexOf`、额外位置定位扫描或重复 CardDb 扫描。有序手牌只为排除“同 ID 不同对象”做身份成员校验。这里不宣称整个执行严格为 O(k)：Forge 有序区底层列表的标准移除／插入本身仍可能是 O(n)。
- **Global grants:** “弃暗投明”在替换完成后使用 `GrantSpellRule` 向施法者注册玩家级永久规则；不创建指挥区徽记，也不依赖任何牌继续存在。`RememberNames$ True` 与 `Card.sharesNameWith NamedCards` 仍作为引擎能力保留给其他需要按替换名称追踪的效果。
- **Java implementation:** `ApiType.ReplaceCards`、`ability/effects/ReplaceCardsEffect.java`、`CardDiscoverCandidateFilter.java`、`Card.java` 与 `CardProperty.java`。
- **Tests:** `ReplaceCardsEffectTest` 验证缓存只扫描一次、按法术力值分桶和牌名去重；`CardDiscoverEffectTest` 验证颜色条件可在轻量层精确过滤；`tests/test_renounce_darkness.py` 固定卡牌脚本与玩家规则契约。

## GrantSpellRule（玩家级永久施法规则）

- **Status:** 已实现为对局内玩家状态，供不应依赖永久物、徽记或区域扫描的全局施法修改使用。
- **DSL:** `DB$ GrantSpellRule`；`Defined$` 选择获得规则的牌手，`RuleKey$` 是稳定键。首版 `ValidCards$` 只接受来源无关的安全子集（`Card`/牌类别加颜色或 `nonColorless` 等颜色条件），`ValidSA$` 只接受 `Spell`、`Instant` 或 `Sorcery`；依赖 `Self`、记忆对象、SVar 或来源牌的既有过滤器会在登记时拒绝。`ReduceGeneric$` 只减少通用费用，`ManaConversion$` 复用并预校验现有颜色转换表达式。`Duration$ Permanent` 必须显式填写，表示持续到本局结束；缺失或其他值会在结算前拒绝，绝不静默创建永久规则。`Stacking$ True` 为每次结算登记独立实例；省略时相同稳定键与相同内容幂等，冲突内容会被拒绝。多玩家效果会先去重并预检所有目标，只有全部可登记时才统一提交，避免部分玩家生效后中断。
- **Hot paths:** 减费直接接入 `CostAdjustment.adjust`，调和直接接入 `StaticAbilityManaConvert.manaConvert`，包括 AI 估算路径。注册表查询自身每次只遍历该牌手已登记的少量规则，不新增或依赖战场、指挥区、其他区域或卡牌数据库扫描；为兼容其他卡牌，原有静态异能来源区域扫描保持不变。
- **AI safety:** `GrantSpellRule` 与 `ReplaceCards` 使用专用保守决策；AI 只接受当前已证明安全、且确实新增规则的自用“弃暗投明”链及正向自用规则，拒绝 `Opponent`／`AllPlayers`、动态或非法规则、幂等无新增规则以及没有正向后续的随机替换。纯调和的 `Stacking$ True` 会用真实 `ManaConversionMatrix` 比较已有支付用途是否覆盖请求，并只采用可证明的牌／法术范围蕴含；首次使用允许，已有更宽范围和用途时拒绝，无法证明覆盖时保守允许。带正数通用减费的叠加规则仍可继续累积。`AILogic$ PlayForSub`／`Always` 不能绕过这两类 API 的受益者检查，但强制触发仍遵循引擎原有语义。决策只遍历该玩家的小型规则表，不扫描区域或全局卡牌数据库。
- **Lifecycle:** 回合清理不删除；普通游戏快照和开发者 `GameState` 导出／恢复会复制规则；重启游戏时清空；真正的子游戏创建新牌手注册表且不影响主游戏规则，避免跨局或跨子游戏泄漏。
- **Java implementation:** `PlayerSpellRule`、`PlayerSpellRuleRegistry`、`GrantSpellRuleEffect`、`ApiType.GrantSpellRule`，以及 `Player`、`CostAdjustment`、`StaticAbilityManaConvert` 的接入点。
- **Tests:** `PlayerSpellRuleRegistryTest` 覆盖玩家隔离、稳定键幂等、显式叠加、无承载牌、颜色支付、只减通用费、无色排除、清理与复制生命周期。
