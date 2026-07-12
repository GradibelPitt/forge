# 自定义测试卡牌参考指南

此文件记录了在此 DIY 工作空间中创建的自定义测试卡牌（`test1` 至 `test5`）。这些卡牌用于验证 Forge 引擎的规则机制与脚本引擎的各项高级特性。

---

## 🃏 test1: 基础加载与套牌数量限制破除

一张用于验证自定义卡牌加载和破除套牌携带数量限制（同名卡最多带4张）的最简测试卡。

### 属性
- **法术力费用**: `0`
- **类别**: `Artifact`（神器）

### 脚本代码 (`cards/colorless/test1.txt`)
```text
Name:test1
ManaCost:0
Types:Artifact
K:A deck can have any number of cards named CARDNAME.
Oracle:A deck can have any number of cards named test1.
```

### 机制解析
- **`K:A deck can have any number of cards named CARDNAME.`**：告诉套牌构建器与游戏引擎，该卡不受标准赛制中单张同名卡最多携带 4 张的限制，套牌内可以携带任意数量。

---

## 🃏 test2: 连携入场（拉出所有同名卡）

当你施放此牌时，它会把手牌和牌库里剩余的其他所有 `test2` 连带直接放入战场。

### 属性
- **法术力费用**: `0`
- **类别**: `Artifact`（神器）

### 脚本代码 (`cards/colorless/test2.txt`)
```text
Name:test2
ManaCost:0
Types:Artifact
K:A deck can have any number of cards named CARDNAME.
T:Mode$ ChangesZone | ValidCard$ Card.wasCastFromYourHandByYou+Self | Destination$ Battlefield | Execute$ TrigHand | TriggerDescription$ When CARDNAME enters, if you cast it from your hand, put all cards named test2 from your hand and library onto the battlefield.
SVar:TrigHand:DB$ ChangeZoneAll | Origin$ Hand | Destination$ Battlefield | ChangeType$ Card.namedtest2 | SubAbility$ TrigLibrary
SVar:TrigLibrary:DB$ ChangeZoneAll | Origin$ Library | Destination$ Battlefield | ChangeType$ Card.namedtest2 | Shuffle$ True
Oracle:A deck can have any number of cards named test2.\nWhen test2 enters, if you cast it from your hand, put all cards named test2 from your hand and library onto the battlefield.
```

### 机制解析
- **`Card.wasCastFromYourHandByYou+Self`**：确保入场触发器只有在你从手牌中**真正施放（打出）** `test2` 时才会生效。这能完美避开由效果拉进场的其他 `test2` 再次触发其入场能力，从而防止死循环（无限递归）崩溃。
- **`TrigHand` 与 `TrigLibrary` 连携**：通过子能力链条（SubAbility），分步执行两次 `ChangeZoneAll` 效果，首先将手牌中的所有 `test2` 移入战场，接着将牌库中的所有 `test2` 移入战场，最后执行洗牌操作 (`Shuffle$ True`)。

---

## 🃏 test3: 全局忽略传奇规则

一张传奇神器，只要它存在于对局中（即使还在你的牌库深处），你操控的所有传奇永久物就会永久忽略传奇规则，不需要因为同名而做出牺牲。

### 属性
- **法术力费用**: `0`
- **类别**: `Legendary Artifact`（传奇神器）

### 脚本代码 (`cards/colorless/test3.txt`)
```text
Name:test3
ManaCost:0
Types:Legendary Artifact
K:A deck can have any number of cards named CARDNAME.
S:Mode$ IgnoreLegendRule | ValidCard$ Permanent.YouCtrl | EffectZone$ All | Description$ The "legend rule" doesn't apply to permanents you control.
Oracle:A deck can have any number of cards named test3.\nThe "legend rule" doesn't apply to permanents you control.
```

### 机制解析
- **`S:Mode$ IgnoreLegendRule`**：挂载静止式能力（Static Effect），用以豁免传奇规则判定。
- **`EffectZone$ All`**：至关重要。将静止式能力的有效生效区域设置为 `All`（所有区域，如牌库、手牌、坟墓场、战场、指挥官区）。这代表只要它在你的卡组里，游戏在任何区域都会执行这一修改，无需等它进场。

---

## 🃏 test4: 自动上手与特殊胜利条件

如果牌库没有其他牌，该牌会自动从牌库上手（若已在手牌中则不检测）。一旦在手牌和牌库都没有其他牌时将它打入战场，你将直接获得游戏胜利。

### 属性
- **法术力费用**: `0`
- **类别**: `Artifact`（神器）

### 脚本代码 (`cards/colorless/test4.txt`)
```text
Name:test4
ManaCost:0
Types:Artifact
K:A deck can have any number of cards named CARDNAME.

# 效果 1：当牌库没有其他牌时，自动从牌库将自己移入手牌
T:Mode$ ChangesZone | Origin$ Library | Destination$ Any | TriggerZones$ Library | CheckSVar$ OtherCardsInLib | SVarCompare$ EQ0 | Execute$ TrigDrawSelf | TriggerDescription$ Whenever a card leaves your library, if there are no other cards in your library, put CARDNAME into your hand.
SVar:TrigDrawSelf:DB$ ChangeZone | Defined$ Self | Origin$ Library | Destination$ Hand
SVar:OtherCardsInLib:Count$ValidLibrary Card.notnamedtest4+YouOwn

# 效果 2：当进场时，如果手牌 and 牌库均没有其他牌，获得胜利
T:Mode$ ChangesZone | Origin$ Any | Destination$ Battlefield | ValidCard$ Card.Self | CheckSVar$ NoOtherCards | SVarCompare$ EQ0 | Execute$ TrigWin | TriggerDescription$ When CARDNAME enters the battlefield, if you have no other cards in hand and library, you win the game.
SVar:TrigWin:DB$ WinsGame | Defined$ You
SVar:NoOtherCards:Count$ValidHand Card.notnamedtest4+YouOwn/Plus.Count$ValidLibrary Card.notnamedtest4+YouOwn

Oracle:A deck can have any number of cards named test4.\nWhenever a card leaves your library, if there are no other cards in your library and test4 is in your library, put test4 into your hand.\nWhen test4 enters the battlefield, if you have no other cards in hand and library, you win the game.
```

### 机制解析
- **`TriggerZones$ Library`**：设定该触发器只在卡牌身处牌库（Library）中时才处于激活状态，进入手牌后自动失效，实现了“已经在手上则不用检测”的需求。
- **`Card.notnamedtest4+YouOwn` 过滤器**：关键性地加入了 `+YouOwn` 限制，确保只对**你自己拥有**的牌库/手牌进行非 `test4` 卡牌计数，而不会把对手的卡牌包含进来。
- **`/Plus.` 逻辑求和**：在 SVar 数学表达式中使用 `/Plus.` 运算符，将手牌和牌库里的非 `test4` 数量求和，确保总数为 `0` 时触发 `WinsGame`。

---

## 🃏 test5: 起手保底与高额七彩法术力生成

0费神器，保证你开局起手（或者进行调度洗牌后）的手牌里必然会有这一张卡；横置它能获得 100 点任意颜色组合的法术力。

### 属性
- **法术力费用**: `0`
- **类别**: `Artifact`（神器）

### 脚本代码 (`cards/colorless/test5.txt`)
```text
Name:test5
ManaCost:0
Types:Artifact
K:StartInHand
A:AB$ Mana | Cost$ T | Produced$ Combo Any | Amount$ 100 | SpellDescription$ Add 100 mana in any combination of colors.
Oracle:{T}: Add 100 mana in any combination of colors.\nStartInHand (You begin the game with this card in your opening hand.)
```

### 机制解析
- **`K:StartInHand` 引擎级机制**：由底层的 Java 引擎（`Player.java` 中的 `drawCards` 方法）在调度（Mulligan）阶段每次玩家抽取起手或调度手牌后进行检测。如果玩家手牌中不存在 `test5`，会自动用牌库中的 `test5` 替换手牌中一张非保底牌并洗牌，从而保证其始终在起手包含且完美兼容调度逻辑。
- **`Produced$ Combo Any | Amount$ 100`**：提供高额法术力的同时，产生 `Combo Any` 复合型分配逻辑，会在横置时由游戏自动弹出一个色彩分配面板，允许你在生成的 100 点法术力中自由配比五色（WUBRG）和无色（C）。

---

## 🃏 test_解除构筑限制: 全局解除主牌数量限制

0费神器。只要它实际位于非指挥官格式的主牌组中，该主牌组最低可以只有1张牌，并忽略普通四张限制以及其他卡牌的 `DeckLimit:N`。

```text
Name:test_解除构筑限制
ManaCost:0
Types:Artifact
K:IgnoreDeckLimits
```

该能力由 `DeckFormat` 引擎级实现，不会绕过卡池、禁牌、自定义卡开关或指挥官等非数量合法性规则。

---

## 🃏 海盗帕奇斯: 海盗登船

`{R}`的1/1传奇海盗／恶魔，具有敏捷和 `DeckLimit:1`。它在手牌或牌库中监听己方海盗进场，并自动把手牌和牌库中的全部同名牌放进战场，不展示且不洗牌。

脚本在结算时使用 `DB$ Branch` 重新统计剩余同名牌；没有剩余副本时不再执行区域移动。

---

## 🃏 突牙: 即时条件动作登船

`{2}{R}{R}`的3/3传奇野兽，具有敏捷、`DeckLimit:1`和：

```text
K:Boarding:3
```

引擎按实体ID记录本回合受到过伤害的不同友方角色。友方角色包括玩家本人以及受伤当时由其操控的所有可受伤害对象。对象之后死亡、离场或改变操控者不会从本回合记录中移除，同一对象多次受伤只计一次。

完整伤害批次结束后，若数量达到3，突牙会直接从手牌或牌库进入战场。这个动作不创建异能、不进入堆叠且不能响应。若条件已经成立，之后突牙才进入手牌或牌库，也会立即登场。记录在回合清理时清空。
