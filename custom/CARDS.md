# Forge DIY Cards

本文件列出现有卡牌与已批准卡牌。脚本本身是属性和逻辑的权威来源；机制含义见 [KEYWORDS.md](KEYWORDS.md)。

## Test cards

| Card | Cost / type / stats | Script | PH01 | Purpose |
|---|---|---|---:|---|
| Forge Test Goblin | `{1}{R}`，2/1 鬼怪／战士 | `cards/red/forge_test_goblin.txt` | 1 | 基础加载、敏捷和进场抓牌 |
| test1 | `{1}` 神器 | `cards/colorless/test1.txt` | 2 | 同名牌数量豁免测试 |
| test2 | `{0}` 神器 | `cards/colorless/test2.txt` | 3 | 从手牌与牌库拉出同名牌 |
| test3 | `{0}` 传奇神器 | `cards/colorless/test3.txt` | 4 | 全区域传奇规则豁免脚本测试 |
| test4 | `{0}` 神器 | `cards/colorless/test4.txt` | 5 | 牌库末张自动上手与胜利条件 |
| test5 | `{0}` 神器 | `cards/colorless/test5.txt` | 6 | `StartInHand` 与组合任意色法力 |
| test_解除构筑限制 | `{0}` 神器 | `cards/colorless/test_解除构筑限制.txt` | 9 | `IgnoreDeckLimits` 测试 |
| Test Superreach 1 | `{0}` 10/10 生物 | `cards/colorless/test_superreach_1.txt` | 12 | Superreach 阻挡者 |
| Test Superreach 2 | `{0}` 20/20 生物 | `cards/colorless/test_superreach_2.txt` | 13 | 多种攻击者阻挡限制组合 |

## Gameplay cards

| Card | Cost / type / stats | Script | PH01 | Behavior summary |
|---|---|---|---:|---|
| 马克扎尔的小鬼 | `{B}{B}`，1/3 恶魔 | `cards/black/markzul_imp.txt` | 7 | 每当你弃一张牌，抓一张牌 |
| 破链灾星霍格 | `{4}{R}{R}{R}{R}`，10/10 传奇豺狼人 | `cards/red/chainbreaker_hogger.txt` | 8、8a | Superreach；开局复制其他传奇永久物并授予传奇规则徽记；画面 1 为标准牌框，画面 2 为原始竖版扩画 |
| 海盗帕奇斯 | `{R}`，1/1 传奇海盗／恶魔 | `cards/red/海盗帕奇斯.txt` | 10 | 敏捷；己方海盗进场时从手牌/牌库登场 |
| 突牙 | `{2}{R}{R}`，3/3 传奇野兽 | `cards/red/突牙.txt` | 11 | 敏捷；`Boarding:3` |
| 炉石传说 | `{0}` 神器 | `cards/colorless/炉石传说.txt` | 14 | `DeckMinimum:31`；开局设置生命、建立成长法力徽记并放逐自身 |
| 空中悍匪 | `{R}`，1/2 海盗 | `cards/red/空中悍匪.txt` | 15 | 进战场时从数据库发现一张海盗生物牌 |
| 空降歹徒 | `{2}`，2/2 海盗 | `cards/red/空降歹徒.txt` | 16 | 己方海盗进场时可从手牌免费施放 |
| Gigantic Spright | `{U}{R}`，2/2 传奇神器生物／元素／构装体 | `cards/colorless/gigantic_spright.txt` | 17 | 可额外横置两名法术力值为 2 的未横置己方生物施放；支付此额外费用时进场获得素材指示物（若其中有神器生物、结界生物或传奇生物则改为两个并获得敏捷至回合结束）；横置并移除一个素材指示物可检索一张力量与防御力均为 2 的生物进战场 |
| 战斗号角 | `{1}{W}{U}{G}` 法术 | `cards/white/战斗号角.txt` | 18 | 从牌堆搜寻至多三张法术力消耗小于等于 2 的生物，以任意顺序放进战场，然后洗牌 |
| 尼鲁巴蛛网领主 | `{1}{B}`，1/4 生物～蜘蛛／亡灵 | `cards/black/尼鲁巴蛛网领主.txt` | 19 | 对手的生物咒语施放费用增加 `{2}` |
| 农夫 | `{U}`，2/1 生物～人类／平民 | `cards/blue/农夫.txt` | 20 | 在你的维持开始时，抓一张牌。 |
| 决战！ | `{W}{R}` 瞬间 | `cards/multicolor/决战.txt` | 21 | 每位牌手各派出三个名为“歹徒”的 3/3 无色生物衍生物，且它们具有敏捷异能。 |
| 援军光环 | `{1}{W}{U}` 结界 | `cards/multicolor/援军光环.txt` | 22 | 消逝 3；在你的结束步骤开始时，从牌库搜寻一张法术力值等于或小于 2 的生物牌放进战场，然后洗牌。 |
| 十字军光环 | `{2}{W}{R}` 结界 | `cards/multicolor/十字军光环.txt` | 23 | 你操控的生物获得攻击触发；每次攻击后永久 +2/+1。 |
| 水晶学 | `{U}` 法术 | `cards/blue/水晶学.txt` | 24 | 从牌库随机选择至多两张力量为 1 的生物牌，将它们置于手上，然后洗牌。 |
| 棱彩光束 | `{7}` 瞬间 | `cards/colorless/棱彩光束.txt` | 25 | 对目标对手以及他操控的每个生物和鹏洛客各造成 3 点伤害；目标对手每操控一个生物或鹏洛客，施放费用便减少 `{1}`。 |
| 前沿哨所 | `{2}`，2/3 生物～墙 | `cards/colorless/前沿哨所.txt` | 26 | 守军；每当一张非地牌从任何区域置入对手的手牌时，该牌永久获得“施放此牌的费用增加 `{1}`。”多个哨所分别触发并叠加。 |
| 异教低阶牧师 | `{U}{B}`，3/2 生物～人类／牧师 | `cards/multicolor/异教低阶牧师.txt` | 27 | 对手施放的瞬间和法术咒语增加 `{1}` 来施放；只在战场生效，多个副本可以叠加。 |
| 虚触侍从 | `{R}`，1/3 生物～人类／术士 | `cards/red/虚触侍从.txt` | 28 | 每位牌手受到的所有伤害增加 1 点；只在战场生效，多个副本可叠加。 |

| 古尔丹之手 | `{4}{U}{U}` 法术 | `cards/blue/古尔丹之手.txt` | 29 | 抓三张牌；疯魔 `{0}`。 |
| 咒怨之墓 | `{0}` 黑色法术 | `cards/black/咒怨之墓.txt` | 30 | 从你的牌库发现一张牌；在你的回合结束时弃掉以此法获得且仍在手牌中的该牌。 |

## Approved cards awaiting implementation

当前没有已批准但尚未创建脚本的卡牌。
