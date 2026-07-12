# 破链灾星霍格：开局传奇规则豁免

## 目标

只要玩家以破链灾星霍格开始游戏，该玩家整局游戏的传奇永久物不受传奇规则限制；该效果不依赖霍格随后是否在战场、墓地、放逐区或任何其他区域。

## 根因

霍格当前直接使用 `S:Mode$ IgnoreLegendRule | ... | EffectZone$ All`。虽然 `EffectZone$ All` 允许该静止式能力在隐藏区域生效，但 Forge 的传奇规则查询只扫描 Battlefield、Graveyard、Exile、Command 和 Stack，不扫描 Library 或 Hand。因此霍格在起始牌库或手牌中时不会成为传奇规则豁免的来源。

## 设计

保留霍格的 `NewGame` 触发器。该触发器在完成起始牌组中其他传奇永久物的复制后，创建一个永久的 Command 区徽记。

该徽记的静止式能力为：

```text
Mode$ IgnoreLegendRule | ValidCard$ Permanent.YouCtrl
```

Forge 的 `DB$ Effect` 会将永久效果放入 Command 区，并强制其静止式能力以 Command 区为有效来源。传奇规则本来就扫描 Command 区，因此每张受该玩家操控的传奇永久物都会通过 `ignoreLegendRule()` 检查，`handleLegendRule` 不会显示保留一张的选择窗口。

## 范围与约束

- 仅修改 `cards/red/chainbreaker_hogger.txt`；不修改 Forge Java。
- 删除霍格本体上的直接 `IgnoreLegendRule` 静止式能力，避免它在墓地、放逐区或堆叠时意外提供额外来源。
- 徽记使用 `Duration$ Permanent`，作用到该局游戏结束。
- 徽记只影响其控制者的永久物（`Permanent.YouCtrl`），不会影响对手或队友。
- 以 `Unique$ True` 防止同一玩家在异常重复执行 NewGame 时获得重复徽记。

## 验证

1. 先建立一个回归测试，验证 Command 区的 `IgnoreLegendRule` 来源可让同一玩家的同名传奇永久物均通过 `ignoreLegendRule()`。
2. 修改脚本后运行该测试及相关 Forge Game 测试。
3. 运行 `python tools/lint_card.py cards/red/chainbreaker_hogger.txt`。
4. 同步到 Forge，并在客户端以含霍格与至少两张同名传奇永久物的套牌开始对局，确认 Command 区出现徽记且不再弹出传奇规则选择窗口。
