# Forge DIY Current Design Decisions

本文件汇总当前有效设计结论。实施历史和原始讨论见 [archive/README.md](archive/README.md)。

## Document governance

日常维护只使用根目录六份主文档及本文件。每个事实只有一个详细权威位置，其他文档通过链接引用。历史 design、plan 和旧根文档完整归档，不删除、不覆盖，但可能已经过时。

## Card scripting boundaries

- 卡牌效果优先用当前 Forge DSL 实现。
- 不得从规则文字推测不存在的 Forge 参数。
- `ValidCards$`、玩家定义、区域移动和现有 AbilityFactory API 应优先复用。
- 只有现有脚本系统无法准确表达且用户明确要求时，才扩展 Java。
- 引擎新增能力必须有独立边界、测试和不会破坏同名官方机制的内部名称。

## Spell categories

法术派系／类别复用 Forge 原生卡牌子类别，不新增平行字段或 Java 关键字。脚本采用 `Types:Sorcery Shadow` 或 `Types:Instant Shadow` 一类写法；`Shadow` 与官方的 `Arcane`、`Lesson`、`Trap` 一样进入 `CardType` 的 subtype 集合，可由现有类型与有效性筛选路径识别。中文类别栏显示为“法术～暗影”或“瞬间～暗影”。

## Deck construction overrides

`IgnoreDeckLimits` 只覆盖非指挥官主牌组的最低数量与同名数量限制，保留卡池、禁牌、自定义卡设置等其他合法性检查。`DeckMinimum:N` 用于声明包含该牌时的最低主牌数量；两者均为构筑政策，不应通过对局内脚本模拟。

## New-game and legend-rule behavior

需要开局改变规则的牌使用 `NewGame` 静态触发和永久徽记。破链灾星霍格只复制注册牌手当前主牌构筑清单中的其他传奇永久物，并通过唯一永久徽记让其控制者忽略传奇规则；“起始牌组”不按异能结算时的手牌或牌库内容重新计算，因此海盗帕奇斯等牌先离开隐藏区也不会漏掉复制。效果不能依赖霍格之后是否仍在某一区域。

“炉石传说”通过 `DeckMinimum:31` 建立牌组下限，在新游戏流程中展示、设置玩家生命、为每位玩家建立成长法力徽记并放逐自身。徽记按 upkeep 增加法力指示物并产生相应组合任意色法力。

炉石传说徽记也将双方最大手牌数量设为 10，并为双方开启空牌库疲劳。每位牌手独立维护单调递增的疲劳次数；空牌库中的每一张抽牌尝试都立即产生一次疲劳，而不是将同一抽牌异能合并为一次。疲劳通过通用 `Player.takeFatigue()` 和 `DB$ TakeFatigue` 暴露给未来的引擎或卡牌机制。

## Superreach

Superreach 允许阻挡任意数量的攻击者，并忽略由相应攻击者自身施加的阻挡限制。它不忽略阻挡者自身条件或全局规则。攻击者的 `Ignore Superreach` 会保留该攻击者的限制。详细入口见 [../KEYWORDS.md](../KEYWORDS.md)。

## Boarding

Boarding 按实体 ID 记录本回合受过伤害的不同友方角色。在完整伤害批次后检查阈值；满足时直接进入战场，不创建可响应的堆叠对象。记录在 cleanup 清空。当前区域移动路径不会在 Boarding 牌之后才进入手牌或牌库时立即重查已经满足的阈值；必须等到下一次伤害批次，这一边界仍待单独修复。

## Chinese localization

内部卡名、脚本文件和能力引用保持不变；显示层使用 `内部名称|中文显示名|中文类别|中文规则文字`。未来以工作区翻译文件作为源，安装时对 Forge 中文资源先备份、按内部名称删除旧 DIY 条目、再幂等追加。该设计尚未实现。

## CardDiscover

新增炉石式发现时使用独立 `CardDiscover` API，不改变 Forge 已有 MTG `DiscoverEffect`。

- 候选来源首期支持卡牌数据库和指定玩家牌库。
- 所有费用、颜色、类型和类别约束复用现有 `ValidCards$`/`Card.isValid`，条件按现有语法叠加取交集。
- 候选先按内部名称去重，再随机提供最多三个；不足三个提供实际数量；零候选不弹窗。
- 数据库来源创建选择牌；牌库来源移动选择的实际对象，不复制、不洗牌、不改变未选牌顺序。
- 从对手牌库选择时保留原所有者，进入发现玩家手牌后遵循 Forge 既有区域规则。

当前状态为已实现、自动测试通过并部署，尚待客户端对局实测。

## Batch database replacement and permanent colored-spell grants

“弃暗投明”使用独立 `ReplaceCards` API 一次性处理手牌与牌库。替换过程先快照符合条件的牌，再从缓存的同法术力值候选桶中随机选择；原牌直接停止存在，新牌进入原区域，牌库中的相对位置保持不变。候选缓存只保存 `PaperCard`，避免循环调用发现、重复扫描全数据库或批量实例化游戏对象。

替换完成后创建一个永久指挥区徽记。客户端实测发现动态 `NamedCards` 条件没有进入施放费用计算后，按用户批准的稳定方案改为直接匹配 `Card.nonColorless`：你的所有有色咒语获得 `ManaConversion$ AnyType->AnyColor`，并由 `ReduceCost Amount$ 2` 减少通用费用。徽记是实现细节，用户给定的卡面描述保持不变。

## Card-specific decisions

- **破链灾星霍格：** `{4}{R}{R}{R}{R}` 10/10 传奇豺狼人，Superreach，按注册主牌构筑清单复制其他传奇永久物并授予传奇规则徽记。
- **海盗帕奇斯：** `{R}` 1/1 传奇海盗／恶魔，敏捷；己方海盗进场时从手牌和牌库登场。
- **突牙：** `{2}{R}{R}` 3/3 传奇野兽，敏捷，`Boarding:3`。
- **空中悍匪：** `{R}` 1/2 海盗生物；进战场时从数据库发现一张海盗生物牌。已登记为 PH01 第15号牌。
