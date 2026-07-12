Update the context below whenever this project's conversation context changes.

# Current Context

- The workspace currently contains the test cards `test1` through `test5` and the `Placeholder_Set` edition.
- The requested deck is `测试牌组1-海盗战.txt`, which defines 破链灾星霍格, 海盗帕奇斯, and 突牙.
- Required project documentation has been read: `AGENTS.md`, `DIY-README.md`, and `test_cards.md`.
- 破链灾星霍格 has been implemented as a 10/10 legendary red Gnoll for {4}{R}{R}{R}{R}. Its `Superreach` keyword is implemented in Forge Java and ignores restrictions created by the attacking creature while preserving blocker-side and global restrictions; `Ignore Superreach` opts an attacker out. The patched desktop runtime JAR and focused/full tests have been verified. Its script also uses `CanBlockAny`, disables the legend rule for its controller, and duplicates starting-deck legendary permanents into the library at game start.
- 马克扎尔的小鬼 has been added as a 1/3 black Demon for {B}{B}; its `Discarded` trigger draws a card whenever its controller discards one. It is listed in `PH01`, and its picture is synchronized through `cards/pictures/PH01/马克扎尔的小鬼.full.jpg`.
- `test_解除构筑限制`、海盗帕奇斯和突牙已加入 `PH01`，并通过 DIY 契约测试与脚本 linter。`IgnoreDeckLimits` 在非指挥官主牌组中把最低牌数设为1，并覆盖普通及 `DeckLimit:N` 同名数量限制，但保留其他合法性检查。
- 突牙已从普通 `DamageDoneOnce` 触发器改为引擎关键词 `Boarding:3`。Forge Game 按实体ID记录本回合受伤的不同友方角色，在完整伤害批次后或 Boarding 牌进入手牌/牌库后直接检查并用 `moveToPlay` 入场；动作不使用堆叠，记录在 cleanup 清空。Forge Core、Forge Game 和 DIY 自动测试已通过；实际客户端构筑与对局验证仍待完成。
