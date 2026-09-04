# Forge DIY Keywords and Engine APIs

## Mystery（奥秘）

- **Status:** 已实现为共用背面的隐藏结界机制。
- **Front-face contract:** 每张奥秘的真实卡面必须写成 `Types:Enchantment Mystery` 并具有 `K:Mystery`。在其操控者手中时，牌名、费用、插画和规则文字均正常显示；展示手牌的效应也会向被展示者显示这些真实信息。奥秘本身始终是永久物牌，因此可被“从手牌将一张永久物牌放进战场”一类效应正常选择。
- **Casting and entry:** 奥秘不能使用正面永久物咒语施放。`K:Mystery` 统一生成 `{1}{U}{U}` 的牌面朝下施放方式；其进入堆叠后只公开为蓝色的 `蓝色奥秘`、`结界～奥秘`，且没有规则异能。无论从手牌、牌库、坟墓场、放逐区或其他路径进入战场，只要真实卡面是奥秘，通用换区路径都会令其牌面朝下进入。
- **Shared face:** 共用背面映射到 `TOKEN_HS` #8 的真实卡牌 `蓝色奥秘`：费用 `{1}{U}{U}`、蓝色、`结界～奥秘`，Oracle 只有 `你的对手隐藏了一些秘密。`；统一图片键为 `c:蓝色奥秘|TOKEN_HS|[8]`，使用 `cards/pictures/TOKEN_HS/蓝色奥秘.artcrop.jpg` 动态绘制完整卡面。其他奥秘在战场的牌框中对所有玩家始终显示该卡面，但底层保留各自真实正面的全部信息与 `MysteryEffect`；操控者可通过悬停／详情预览查看真实正面，对手不能查看真实正面。
- **Reveal and resolution:** 牌面朝下的奥秘具有费用为 `{0}` 的原生 `TurnFaceUp` 特殊动作，并以 `OpponentTurn$ True` 限制为只能在对手回合使用。若 `MysteryEffect` 任一需要至少一个目标的能力节点没有足够合法候选，该特殊动作不可使用；完全不使用目标或目标数下限为零的奥秘不受此限制。翻回正面会产生关键词自带的 `TurnFaceUp` 触发，并先让 `MysteryEffect` 正常使用堆叠、接受响应和结算。该触发仍在堆叠或等待进入堆叠时，正面奥秘保留在战场；触发结算、因目标失效而失效、或被反击并离开堆叠后，下一次状态检查才令它牺牲。这样效果可安全读取其来源，同时正面不会在触发处理完毕后留场。
- **DSL:** 每张牌必须提供 `SVar:MysteryEffect:DB$ ...`；该 SVar 必须以 `DB$` 开头，目标、选择与后续子异能均按普通 Forge AbilityFactory 语法书写。例如：

```text
Name:Example Mystery
ManaCost:2 U
Types:Enchantment Mystery
K:Mystery
SVar:MysteryEffect:DB$ Counter | TargetType$ Spell | ValidTgts$ Instant,Sorcery | TgtPrompt$ Select target instant or sorcery spell | SpellDescription$ Counter target instant or sorcery spell.
Oracle:Counter target instant or sorcery spell.
```

- **Java implementation:** `Keyword` 注册；`CardFactoryUtil` 生成隐藏施放、翻面动作、目标预检所用的 `MysteryEffect` 副本、实际效果触发和共用背面；`AbilityStatic` 在翻面可用性检查中验证全部强制目标候选；`SpellAbilityRestriction` 禁止正面施放；`GameAction` 在所有进战场路径统一准备背面，并在奥秘翻面触发离开堆叠后的状态检查中令其退场；`Card` 与 `SetStateEffect` 负责身份、统一图片键和翻面日志；桌面端 `CardPanel`／`CachedCardImage` 固定使用牌面朝下图片键绘制战场牌框，悬停详情仍沿用操控者可查看正面的既有权限。
- **Tests:** `MysteryTest` 覆盖手牌真实卡面与永久物资格、正面施放禁令、共用施放费用和背面特征、任意进战场自动背面、双方可见性、对手回合限制、无合法强制目标时禁止翻面、无目标效果仍可翻面，以及“效果在堆叠时留场、触发离开堆叠后退场”的顺序。

## Quest（任务牌）

- **Status:** 已实现为备牌构筑规则与开局选择机制。
- **DSL:** 牌张类别写作 `Types:<主类别> Quest`；构筑限制以 `K:Quest:<需求1>;<需求2>:<牌面描述>` 声明，每项需求使用普通 `Card.isValid` 过滤器并且须在起始主牌中至少找到一张，例如 `K:Quest:Pirate;Equipment;Card.Historic:...`。任务牌只能放在备牌中，且一副牌至多携带一张；该数量限制是构筑规则，不写在牌面上。
- **Player-facing behavior:** 若备牌中有任务且起始主牌满足它列出的每项构筑条件，游戏开始时牌手可以选择让它正面朝上进入自己的徽记区。同一张牌可以同时满足多项条件，例如武具作为神器也属于史迹。任务上未显式指定生效区域的静止式异能与未显式指定起动区域的起动式异能会改为从徽记区生效；任务本身的咒语异能仍只从手牌施放。
- **Explicit zones:** 任务上的触发式异能仍须写 `TriggerZones$ Command`；若某个静止式或起动式能力显式填写 `EffectZone$`／`ActivationZone$`，引擎保留该显式区域。
- **Rendering:** `Quest` 登记为合法结界子类别，避免卡牌类型清理时被移除；桌面 `FCardImageRenderer` 将其使用 Saga 的纵向分栏布局（规则文字在左、插画在右），但不把任务类型改为 `Saga` 或 `Class`，因此不会获得 Saga 的章节／传记指示物规则或 Class 的等级规则。
- **Java implementation:** `DeckFormat` 执行备牌与单张限制；`Quest` 关键字解析逐项构筑条件；`Match` 与 `Player.assignQuest` 检查起始主牌、处理开局选择、移至 `Command` 及默认异能区域。
- **Tests:** `QuestDeckRuleTest`、`QuestCardTest` 与 `tests/test_raid_the_docks.py`。

## Windfury（风怒）

- **Status:** 已实现为共享额外战斗关键词。
- **DSL:** `K:Windfury`。
- **Semantics:** 主动牌手的第一次战斗阶段结束后，若其操控至少一个具有风怒的生物，则在该回合共同执行恰好一次额外战斗阶段。该额外战斗开始时重置由主动牌手操控的全部风怒生物；只有具有风怒的生物能在其中攻击。多个风怒生物不会各自创建额外战斗，对手的风怒生物也不会为主动牌手创建额外战斗。
- **Java implementation:** `Keyword.java` 注册；`PhaseHandler` 安排并标记完整额外战斗阶段组；`ExtraPhase` 携带风怒战斗元数据；`CombatUtil.canAttack` 执行攻击者限制。
- **Tests:** `WindfuryTest` 覆盖关键词注册、多个风怒生物只创建一次额外战斗、仅重置风怒生物，以及额外战斗的攻击资格。

## CanBeAttacked（牌张可作为攻击目标）

- **Status:** 已实现为通用静止式异能，复用鹏洛客／战役的 `Card` defender 战斗管线。
- **DSL:** `S:Mode$ CanBeAttacked | ValidDefender$ Card.Self | ValidAttacker$ Creature.OppCtrl`。
- **Semantics:** 符合 `ValidAttacker$` 的生物可在宣告攻击者时选择符合 `ValidDefender$` 的牌张作为攻击目标；攻击箭头、阻挡、战斗伤害和防御牌手解析均继续使用现有 `Combat` 逻辑。它不是强制阻挡，也不改变召唤失调。炉石模式下，每个生物都由模式规则自动获得相同的攻击目标资格，不要求牌张脚本逐张写此静止式异能；但具体攻击者只有在普通成对阻挡规则下能够阻挡该目标时才可选择它。
- **Java implementation:** `StaticAbilityCanBeAttacked` 将显式合格牌张及炉石模式下的所有对方生物加入 `CombatUtil.getAllPossibleDefenders`，并由 `CombatUtil.canAttack` 对具体攻击者再次校验。炉石分支反向调用 `CombatUtil.canBlockByRestrictions(target, attacker)`，统一继承 `CantBlockBy`、影遁和 Superreach 等成对规则，而不把攻击者是否横置或一般“不能阻挡”状态误作目标限制。
- **Tests:** `AttackableCreatureTest` 覆盖显式词条的对手生物可攻击、己方生物不可攻击、普通模式下普通生物不进入 defender 列表，以及 `Combat` 保存牌张攻击目标；`HearthstoneModeTest` 覆盖模式自动资格、普通模式隔离、飞行／延势、马术、影遁与攻击者一般“不能阻挡”状态的边界。

## printedNamed（原始牌名筛选）

- **Status:** 已实现为通用 `Card.isValid` 属性，用于复制状态下仍需按物理牌原始身份筛选的咒语或牌。
- **DSL:** `Card.printedNamed<name>`；可与既有否定语法组合为 `Card.!printedNamed<name>`。含逗号的牌名继续用分号代替逗号，名称中的下划线会按既有 `named` 规则还原为空格。
- **Semantics:** 读取 `Card` 关联的 `PaperCard` 名称，不受复制状态改变当前牌名影响；没有关联 `PaperCard` 的临时对象不会命中。普通 `named<name>` 仍按当前可复制牌名判断，两者不可混用。
- **Use:** `殒命暗影` 的手牌追踪触发以此排除所有物理身份为 `殒命暗影` 的咒语，因此连续施放时保留更早的非同名瞬间／法术。
- **Java implementation:** `forge-game/.../card/CardProperty.java`。
- **Tests:** `ShadowOfDemiseTest` 覆盖复制后当前名称改变但原始牌名仍可识别、连续同名咒语跳过及空历史无效果结算。

## Miracle 施放费用标记

- **Status:** 在 Forge 既有 `Miracle` 关键字上补充施放方式标记，不改变抽到、展示、时机或替代法术力费用规则。
- **DSL:** `K:Miracle:<cost>` 生成的 `Play` 效果会把待施放的咒语标记为 `AlternativeCost.Miracle`。咒语异能过滤器可用 `Spell.Miracle` 或 `Spell.!Miracle` 区分是否实际支付奇迹费用。
- **Semantics:** 标记只属于本次咒语异能；普通施放、免费施放、返照及其他替代费用不会被误判为奇迹。`矿车难题` 以 `ValidSpell$ Spell.!Miracle` 添加弃牌费用，因此只有实际支付奇迹费用时免除弃牌。
- **Java implementation:** `CardFactoryUtil`、`PlayEffect`、`AlternativeCost` 与 `SpellAbilityProperty`。
- **Tests:** `MiracleAlternativeCostTest` 覆盖生成标记、咒语过滤与额外费用分支；`tests/test_trolley_problem.py` 固定卡牌契约。

## Durability

- **Status:** 已实现为引擎级数值关键字。
- **Player-facing behavior:** 具有耐久 N 的永久物进战场时上面有 N 个耐久指示物；当最后一个耐久指示物从其上移去时，将它牺牲。
- **DSL:** `K:Durability:N`，例如 `K:Durability:2`。
- **Java implementation:** `Keyword.java` 注册数值关键字；`CardFactoryUtil.java` 建立进场放置耐久指示物的替代式效应与最后一个耐久指示物被移去时的牺牲触发器；`CounterEnumType.java` 注册耐久与秘银指示物。
- **Edge cases:** 耐久本身不会按回合自动移去指示物；只有其他费用或效应移去最后一个耐久指示物时才会触发牺牲。

## LifeReduced 来源筛选

- **Status:** 已实现为现有替代事件的可选来源过滤能力，供“防止所选来源的失去生命效应”使用。
- **DSL:** `Event$ LifeReduced | ValidPlayer$ <player> | ValidSource$ <card filter> | IsDamage$ False`。一次性防护可将此替代式效应与 `DamageDone` 来源防护放在同一个临时 `Effect` 上，并让两者的 `ReplaceWith$` 都放逐同一效果牌。
- **Java implementation:** `Player.loseLife(..., Card source)` 把可选来源写入替代事件；`ReplaceLifeReduced` 执行 `ValidSource$`；`LifeLoseEffect` 与 `InternalRadiationEffect` 传入其宿主牌。
- **Edge cases:** 支付生命、疲劳、法力灼烧与其他没有牌张来源的生命损失不会命中 `ValidSource$`。伤害批次可能同时包含多个来源，因此仍由 `DamageDone` 在伤害转化为失去生命之前按来源拦截，不能把聚合后的 `LifeReduced` 当作单一伤害来源。

## DrawnAll（批次抓牌触发）

- **Status:** 已实现；供“抓一张或数张牌时”这类每个抓牌批次只触发一次的异能使用。
- **DSL:** `T:Mode$ DrawnAll | ValidPlayer$ <player>`；本批次实际抓到的牌张数通过 `TriggerCount$Amount` 取得，实际牌集合为 `TriggeredCards`。
- **Java implementation:** `TriggerType.DrawnAll`、`TriggerDrawnAll.java` 与 `Player.drawCards(...)` 的批次完成路径。原有 `Drawn` 仍按每张牌单独触发，未改变其他卡牌语义。
- **Edge cases:** 只统计本次 `drawCards` 实际抓到的牌；被替代、被禁止或因牌库不足而未抓到的部分不计入，实际为零时不触发。

## 跨牌库抓牌与抓牌步骤首次替代

- **Status:** 已实现为通用抓牌与 `Draw` 替代参数。
- **DSL:** `DB$ Draw | Defined$ <drawing player> | FromLibrary$ <library owner>` 令前者从后者的牌库顶抓牌；这是抓牌而非单纯换区。`R:Event$ Draw | FirstCardInDrawStep$ True` 只匹配回合牌手在自己抓牌步骤尚未抓过牌时的逐张抓牌事件。
- **Semantics:** `Defined$` 牌手接受牌、累计本回合／本抓牌步骤抓牌次数并触发 `Drawn`／`DrawnAll`；`FromLibrary$` 只改变取牌的牌库，且明确从顶抓，不会借用抓牌者或牌库拥有者的“从牌库底抓牌”关键字。空牌库抓牌失败与不能抓牌仍归于实际抓牌者。
- **Java implementation:** `DrawEffect`、`Player.drawCardsFromLibrary(...)` 与 `ReplaceDraw`。
- **Tests:** `DrawFromLibraryTest` 与 `ReplaceDrawFirstCardInDrawStepTest`。

## SpellCast 本回合共享类别计数

- **Status:** 已实现为 `SpellCast` 的通用条件参数。
- **DSL:** `ActivatorThisTurnCastSharedCardType$ <comparison>` 配合 `ActivatorThisTurnCastSharedCardTypeValid$ <card filter>`。它统计同一施法者本回合较早施放、符合比较池过滤器且与当前咒语共享至少一个牌张类别的咒语，再用 `EQ0` 等表达式比较。
- **Semantics:** 当前咒语不计入“其他咒语”；神器生物等多类别牌只要共享任一类别便计一次。比较池可写 `Permanent` 只看永久物咒语，或写 `Card` 查看所有较早咒语。
- **Java implementation:** `TriggerSpellAbilityCastOrCopy`。
- **Tests:** `TriggerSpellAbilityCastOrCopySharedTypeTest`。

## ChangeZone 指定目标牌手区域

- **Status:** 已实现为已知来源换区的可选目的牌手参数。
- **DSL:** `DB$ ChangeZone | ... | Destination$ Library | DestinationPlayer$ <defined player> | LibraryPosition$ 0`。
- **Semantics:** 省略时继续使用既有按拥有者决定区域的行为；指定时移动至该牌手对应区域，但不改变牌的拥有者。可用于把正常结算的己方咒语置于目标牌手牌库顶。
- **Java implementation:** `ChangeZoneEffect`。
- **Tests:** `ChangeZoneDestinationPlayerTest`。

## ColorChoice（有色费用择一减免）

- **Status:** 已实现，供 `ReduceCost` 静态异能使用。
- **DSL:** `ColorChoice$ U B`。对每一个 `Amount`，操控者从费用中仍存在的指定有色法术力符号中选择一个，将其减少；不会同时减少多个颜色，也不会减少无色费用。
- **Java implementation:** `forge-game/.../cost/CostAdjustment.java`。
- **Tests:** `forge-game/src/test/java/forge/game/cost/CostAdjustmentColorChoiceTest.java`。

## StartingDeckDuplicateNonlandNames（起始套牌非地重名计数）

- **Status:** 已实现为通用数值 SVar，供施放时检查起始构筑的高地条件。
- **DSL:** `SVar:<name>:Count$StartingDeckDuplicateNonlandNames`；配合 `ConditionCheckSVar$ <name> | ConditionSVarCompare$ EQ0` 表示起始套牌中每张非地牌的名称均不相同。
- **Java implementation:** `AbilityUtils.xCount` 读取异能起动牌手注册的主牌 `CardPool`；按内部牌名合并不同版本，忽略所有地牌，并返回出现至少两份的非地牌名数量。
- **Tests:** `forge-game/src/test/java/forge/game/ability/AbilityUtilsStartingDeckTest.java`；DIY 契约测试 `tests/test_elise_the_enlightened.py`。
- **Edge cases:** 读取的是注册的起始构筑，不会因对局中抓牌、磨牌、化生、洗牌或换区而改变；同名不同版本仍视为同一牌名，基本地和非基本地均不计入。

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
- **Tests:** Forge Core 构筑测试及使用该关键词的目标卡牌契约；炉石模式已改用独立 `DeckFormat.Hearthstone`，不再依赖此关键词。
- **Edge cases:** 这是构筑规则，不等同于游戏开始时的区域或生命设置。

## GameRule

- **Status:** 已实现；作为通用规则牌能力保留，当前炉石模式不再使用。
- **Player-facing behavior:** 这类牌只承载整局规则，不是可获得或使用的普通牌。引擎在任何起手牌或调度手牌产生前将它从牌库放逐；若异常路径仍把它留在牌库顶或牌库底，抓牌会先将其放逐并继续抓下一张普通牌。
- **DSL:** `K:GameRule`。
- **Java implementation:** `GameAction` 负责调度前放逐并禁止其离开放逐区；`Player.drawCards` 提供顶部／底部抽牌兜底；`GameRuleCard` 统一识别规则牌；`CardDiscoverEffect`、`MakeCardEffect`、`ReplaceCardsEffect`、`DraftEffect`、`PlayEffect`、`CopyPermanentEffect` 与 `CloneEffect` 从发现、化生／制造、随机替换、选牌、直接打出及复制入口排除规则牌。
- **Tests:** `GameRuleCardTest` 覆盖调度前放逐、顶部／底部抽牌兜底与区域锁；`DeckPolicyKeywordTest` 覆盖关键词解析；`CardDiscoverEffectTest`、`MakeCardEffectTest` 与 `ReplaceCardsEffectTest` 覆盖三类生成入口。
- **Edge cases:** 构筑检查仍会把该牌计入主牌并应用 `DeckMinimum`／`DeckLimit`；进入对局后它只能位于放逐区，不能成为发现选项、化生结果、制造结果、批量替换结果、选牌结果、直接打出结果或复制来源。其 `NewGame` 触发必须以 `TriggerZones$ Exile` 运行。

## Fatigue

- **Status:** 已实现；炉石模式直接启用，且可由未来卡牌通过独立 API 复用。
- **Player-facing behavior:** 每位牌手各自维护对局内的疲劳次数。每次 `takeFatigue` 都先将该牌手的次数加一，再失去等同于新次数的生命；该次数永不减少。空牌库抽牌启用疲劳后，每一次逐张抽牌尝试都是一次独立疲劳事件。
- **DSL:** `DB$ TakeFatigue | Defined$ <player>` 对每个受影响牌手触发一次疲劳。`FatigueOnEmptyDraw` 仍可供其他内部规则使用；炉石模式无需向玩家添加该关键词。
- **Java implementation:** `forge-game/.../player/Player.java` 的 `takeFatigue()` 与空牌库抽牌路径；`ApiType.TakeFatigue` 和 `ability/effects/TakeFatigueEffect.java` 提供可复用入口。
- **Edge cases:** 双方疲劳各自从 1 计算；一次抓三张会依序产生 1、2、3 点疲劳，下一次空抽为 4 点，而不是按异能或回合合并。疲劳造成生命损失，不是带来源的战斗或法术伤害。

## CardDiscover

- **Status:** 已迁移到 `D:\Forge\forge-latest` 并通过 Forge Game 针对性测试；新版桌面 JAR 已构建，尚未记录其安装或客户端对局实测。
- **Player-facing behavior:** 从指定候选池使用 Forge 现有过滤器筛选，按内部牌名去重并随机展示最多三个选项；玩家选择一张置入手牌。没有候选则不展示。候选池可来自卡牌数据库、指定牌手的牌库或备牌。
- **DSL design:** 独立 AbilityFactory API `DB$ CardDiscover`，配合 `Defined$`、`Source$ CardDatabase|Library|Sideboard`、`SourceController$`、`ValidCards$`、`OptionCount$` 和 `Destination$`。`RememberChosen$ True` 只在选择完成且牌实际移入目标区域后记录所选牌，不记录仅展示而未选择的选项；配合 `ValidCards$ Card+doesNotShareNameWith Remembered` 可在后续发现中排除所有与真正选过的牌同名的候选。省略 `RememberChosen$ True` 时不会改变既有记忆状态。最终参数必须在实现测试中验证，不能因本段设计而跳过解析验证。
- **Java implementation:** `forge-game/.../ability/ApiType.java` 注册，`ability/effects/CardDiscoverEffect.java` 实现；不修改 Forge 已有 MTG `DiscoverEffect`。
- **Tests:** `forge-game/src/test/java/forge/game/ability/effects/CardDiscoverEffectTest.java`；`tests/test_airborne_bandit.py`；`tests/test_band_manager_elite_tauren_chieftain.py`。
- **Edge cases:** 条件必须复用 `ValidCards$`/`Card.isValid`；同名不同版本不重复；不足三个显示实际数量；数据库来源创建新牌，牌库与备牌来源移动实际对象且不洗牌。`GameRule` 牌在数据库和区域候选层都会被排除。`doesNotShareNameWith Remembered` 按已选牌名排除同名候选，但不能把只出现过而未选择的发现选项之牌名加入排除集合。

## ReplaceCards（批量数据库牌池替换）

- **Status:** 已实现为引擎级 Ability API，供“弃暗投明”等隐藏区批量替换效果使用。
- **DSL:** `DB$ ReplaceCards`，配合 `Defined$`、`Zones$`、`ValidCards$`、`ReplacementValid$` 与必须显式写为 `True` 的 `MatchManaValue$ True`；当前 `Zones$` 只接受 `Hand`／`Library`。缺失、`False` 或其他区域会在任何区域或名称记录副作用前拒绝。只有需要记录替换名称的其他效果才使用可选的 `RememberNames$ True`。
- **Performance:** 复用 `CardDiscoverCandidateFilter` 的轻量 `PaperCard` 过滤，并在建立候选桶时排除 `GameRule`；静态候选条件按数据库实例、数据库大小和过滤表达式缓存为法术力值桶。首次建立只遍历一次数据库，不为未选候选创建游戏内 `Card`；同一数据库上的后续结算直接复用缓存。每个玩家区域只做一次线性计划扫描并记录匹配牌的稳定位置，随后每张牌只做对应法术力值桶的常数时间随机索引；没有逐牌区域 `indexOf`、额外位置定位扫描或重复 CardDb 扫描。有序手牌只为排除“同 ID 不同对象”做身份成员校验。这里不宣称整个执行严格为 O(k)：Forge 有序区底层列表的标准移除／插入本身仍可能是 O(n)。
- **Global grants:** “弃暗投明”在替换完成后使用 `GrantSpellRule` 向施法者注册玩家级永久规则；不创建指挥区徽记，也不依赖任何牌继续存在。`RememberNames$ True` 与 `Card.sharesNameWith NamedCards` 仍作为引擎能力保留给其他需要按替换名称追踪的效果。
- **Java implementation:** `ApiType.ReplaceCards`、`ability/effects/ReplaceCardsEffect.java`、`CardDiscoverCandidateFilter.java`、`Card.java` 与 `CardProperty.java`。
- **Tests:** `ReplaceCardsEffectTest` 验证缓存只扫描一次、按法术力值分桶和牌名去重；`CardDiscoverEffectTest` 验证颜色条件可在轻量层精确过滤；`tests/test_renounce_darkness.py` 固定卡牌脚本与玩家规则契约。

## GrantSpellRule（玩家级永久施法规则）

- **Status:** 已实现为对局内玩家状态，供不应依赖永久物、徽记或区域扫描的全局施法修改使用。
- **DSL:** `DB$ GrantSpellRule`；`Defined$` 选择获得规则的牌手，`RuleKey$` 是稳定键。首版 `ValidCards$` 只接受来源无关的安全子集（`Card`/牌类别加颜色或 `nonColorless` 等颜色条件），`ValidSA$` 只接受 `Spell`、`Instant` 或 `Sorcery`；依赖 `Self`、记忆对象、SVar 或来源牌的既有过滤器会在登记时拒绝。既有 `ReduceGeneric$` 只减少通用费用，`ManaConversion$` 复用并预校验现有颜色转换表达式。新 `Harmony$ True` 授予独立于官方坟场替代费用 `Harmonize` 的可见“调和”关键字，并自动提供 `AnyType->AnyColor`，因此禁止再同时填写 `ManaConversion$` 或 `ReduceGeneric$`；`HarmonyReduction$ N` 只允许与 Harmony 同用，先以每点一个符号移除具有有色支付选项的单色、混血、2/有色、C/有色或非瑞符号，再让剩余点数走 Forge 既有通用减费路径。尚未选定的 X／COLORED_X、雪境与纯 `{C}` 不会被直接删除；X 选定并展开为实际应付数额后可以照常减少（包括限定颜色的已选 X）。`Duration$ Permanent` 必须显式填写，表示持续到本局结束；缺失或其他值会在结算前拒绝，绝不静默创建永久规则。`Stacking$ True` 为每次结算登记独立实例；省略时相同稳定键与相同内容幂等，冲突内容会在写入前被拒绝。此类已证明无副作用的稳定键冲突会标记为可恢复，只跳过当前效果并写入 stderr／对局日志；其他异常不在这里吞掉。多玩家效果会先去重并预检所有目标，只有全部可登记时才统一提交，避免部分玩家生效后中断。Harmony 的标签、专用减费和颜色支付都按牌的拥有者注册表判定；即使由另一牌手施放也沿用拥有者规则，施放者自己的 Harmony 不会借给不属于他的牌。非 Harmony 的 `ReduceGeneric$`／`ManaConversion$` 仍按实际施放者判定。
- **Name snapshots:** `NameSnapshot$ OpponentCards` 只可用于 `GrantSpellRule`，并在结算时快照每名获得规则牌手的任一对手当前全部牌名。该名称集合成为来源无关的永久规则数据，用来限制 `ValidCards$`；不会在费用、支付或关键字热路径扫描对手区域。与 `Stacking$ True` 同用时，每次结算保存自己的名称集合；同一牌名出现在两次快照中就获得两层减费，只在后一次出现则只获得后一层。名称集合也参与 Harmony 覆盖判断，新增名称会推进 epoch／刷新可见标签，空快照仍匹配零张牌。它供“神秘访客”将与对手同名的己方牌接入与“弃暗投明”完全相同的 Harmony 付款和减费语义。
- **Hot paths:** 减费直接接入 `CostAdjustment.adjust`，调和支付直接接入 `StaticAbilityManaConvert.manaConvert`，包括 AI 估算路径。调和标签由游戏内 `Card` 关键字缓存和 `CardView` 动态投射，不修改共享 `CardRules`／`PaperCard`；玩家规则投射位于牌层关键字禁止之后，因此不能被受影响牌自身的“不能获得关键字”状态删掉。登记、清空或恢复只差分刷新手牌和公开区域中实际改变标签的牌；牌库、备牌及其他隐藏牌堆只推进玩家级 epoch 并在实时查询或进入可见区域时惰性同步，20,000 张隐藏牌也不会被逐张重建 AbilityText。费用与支付始终直接查询实时规则。注册表热查询每次只遍历该牌拥有者或实际施放者已登记的少量规则（按上述语义选择），不扫描卡牌数据库；为兼容其他卡牌，原有静态异能来源区域扫描保持原行为。
- **AI safety:** `GrantSpellRule` 与 `ReplaceCards` 使用专用保守决策；AI 只接受当前已证明安全、且确实新增规则的自用“弃暗投明”链及正向自用规则，拒绝 `Opponent`／`AllPlayers`、动态或非法规则、幂等无新增规则以及没有正向后续的随机替换。纯调和的 `Stacking$ True` 会用真实 `ManaConversionMatrix` 比较已有支付用途是否覆盖请求，并只采用可证明的牌／法术范围蕴含；首次使用允许，已有更宽范围和用途时拒绝，无法证明覆盖时保守允许。带正数通用减费的叠加规则仍可继续累积。`AILogic$ PlayForSub`／`Always` 不能绕过这两类 API 的受益者检查，但强制触发仍遵循引擎原有语义。决策只遍历该玩家的小型规则表，不扫描区域或全局卡牌数据库。
- **Lifecycle:** 回合清理不删除；普通游戏快照和开发者 `GameState` 导出／恢复会复制规则；重启游戏时清空；真正的子游戏创建新牌手注册表且不影响主游戏规则，避免跨局或跨子游戏泄漏。
- **Java implementation:** `PlayerSpellRule`、`PlayerSpellRuleRegistry`、`GrantSpellRuleEffect`、`ApiType.GrantSpellRule`，以及 `Player`、`CostAdjustment`、`StaticAbilityManaConvert` 的接入点。
- **Tests:** `PlayerSpellRuleRegistryTest` 覆盖玩家隔离、稳定键幂等、显式叠加、无承载牌、颜色支付、旧通用减费兼容、调和先减有色符号、可见关键字、无色排除、清理与复制生命周期；`PlayerSpellRuleNameSnapshotTest` 覆盖独立名称快照、重叠名称累计减费、新名称单层减费、无关名称隔离与稳定键冲突的安全跳过。
