# 混乱触须实卡化设计

## 目标

将当前的“混乱触须”神器衍生物改为 PH01 中可构筑、可正常进入坟墓场的正式卡牌，并让“脱困古神尤格萨隆”改为将该实体卡化生到战场。这样，混乱触须支付牺牲费用后会作为牌留在坟墓场，后续能力可以正确统计坟墓场中的混乱触须数量。

## 卡牌定义

“混乱触须”登记为 PH01 #39，法力费用为 `{1}`，类别为无色神器。它保留当前能力：

`{T}`，牺牲混乱触须：发现一张法术力值为 X+1 的法术牌，然后不支付其法术力费用、对随机合法目标施放之。X 为你坟墓场中名为混乱触须的牌数量。

牺牲属于起动费用，因此能力开始结算时，本次牺牲的混乱触须已经在坟墓场中并计入 X。第一次在坟墓场原本没有混乱触须时启动该能力，X 为 1，发现法术力值为 2 的法术。

## 文件与资源迁移

- 将 `tokens/c_chaos_tentacle.txt` 的能力迁移到 `cards/colorless/混乱触须.txt`，并把 `ManaCost:no cost` 改为 `ManaCost:1`。
- 在 `editions/Placeholder_Set.txt` 中登记 `39 C 混乱触须 @Custom`。
- 将现有 `tokens/pictures/c_chaos_tentacle.jpg` 迁移为 `cards/pictures/PH01/混乱触须.artcrop.jpg`。
- 保留原始图片备份 `tools/card-artwork/Chaos_Tentacle_original.jpg`。
- 删除旧 token 脚本和 token 图片，避免同名实体卡与 token 定义并存。

## 尤格萨隆修改

“脱困古神尤格萨隆”不再通过 `TokenScript$ c_chaos_tentacle` 派出衍生物，而使用 Forge 已有的 `MakeCard` 能力从卡牌数据库化生实体卡：

- 每次启动尤格萨隆的一项竭绝异能时，化生一张名为“混乱触须”的牌到你的战场。
- 第三项竭绝异能化生六张名为“混乱触须”的牌到你的战场。
- 使用 `Conjure$ True`、`Name$ 混乱触须` 和 `Zone$ Battlefield`；六张版本使用 `Amount$ 6`。
- 英文 Oracle、简体中文显示文本和 `CARDS.md` 中的描述同步由“派出神器衍生物”改为“化生实体牌到战场”。

本设计只复用当前 Forge 已存在并有官方卡牌实例的 DSL，不修改 Java 引擎。

## 测试与验证

实施遵循测试驱动：

1. 先修改 `test_chaos_tentacle.py`，要求脚本位于正式卡牌目录、费用为 `{1}`、存在 PH01 #39 登记和 PH01 卡图，并要求旧 token 文件不存在。
2. 先修改 `test_yogg_saron_unbound.py`，要求两处 Yogg 效果使用 `MakeCard` 化生一张或六张实体混乱触须，且不再引用 `c_chaos_tentacle`。
3. 运行目标测试，确认它们因当前 token 实现而按预期失败。
4. 完成最小脚本、版本、图片和文本修改，使目标测试通过。
5. 对“混乱触须”和“脱困古神尤格萨隆”运行单卡 lint，再运行 DIY 全量测试。
6. 更新 `CARDS.md` 与 `VERIFICATION.md`，运行 `tools/install_to_forge.ps1`，并比较源文件与 `%APPDATA%\Forge` 部署文件的哈希。

客户端实测与自动验证分别记录；自动测试和部署成功不等同于客户端对局已经验证。

## 非目标

- 不改变混乱触须的发现候选数量、随机目标处理或免费施放流程。
- 不修改尤格萨隆的费用、身材、其他竭绝效果或已归档的随机法术链。
- 不让 token 进入坟墓场，也不增加任何 Java 级 token 规则例外。
