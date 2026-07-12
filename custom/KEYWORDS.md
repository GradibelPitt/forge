# Forge DIY Keywords and Engine APIs

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
- **Edge cases:** 按实体 ID 去重；同一角色多次受伤只计一次；对象之后离场或改变操控者不撤销记录；cleanup 清空本回合记录；条件已满足后牌才进入手牌或牌库时仍立即检查。

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
- **DSL design:** 独立 AbilityFactory API `DB$ CardDiscover`，配合 `Defined$`、`Source$`、`SourceController$`、`ValidCards$`、`OptionCount$` 和 `Destination$`。最终参数必须在实现测试中验证，不能因本段设计而跳过解析验证。
- **Java implementation:** `forge-game/.../ability/ApiType.java` 注册，`ability/effects/CardDiscoverEffect.java` 实现；不修改 Forge 已有 MTG `DiscoverEffect`。
- **Tests:** `forge-game/src/test/java/forge/game/ability/effects/CardDiscoverEffectTest.java`；`tests/test_airborne_bandit.py`。
- **Edge cases:** 条件必须复用 `ValidCards$`/`Card.isValid`；同名不同版本不重复；不足三个显示实际数量；数据库来源创建新牌，牌库来源移动实际对象且不洗牌。
