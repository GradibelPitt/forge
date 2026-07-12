# Forge DIY Verification Status

- 2026-07-12 实现 `混乱触须`：作为无色神器 token，具有“`{T}`，牺牲此神器：发现一张法术力值为 X+1 的法术牌，然后不支付其法术力费用施放；若该法术有目标，将其改为随机合法目标”，其中 X 为你坟墓场中混乱触须牌的数量（因先支付牺牲费用，包含本次牺牲的触须）。发现复用 `CardDiscover` 的全卡池三选一流程，筛选为 `Sorcery.cmcEQY`，随后从放逐区免费施放并以 `ChangeTargets | RandomTarget$ True` 随机改目标。`test_chaos_tentacle.py` 4 项契约测试、token lint 和 DIY 全量 129 项测试本次均通过。用户原图备份为 `tools/card-artwork/Chaos_Tentacle_original.jpg`，SHA-256 为 `EDC145828C149F3F5DB0EF3ED2B040FDE116C738AA7CB1DB3E9F21EBD8CA957D`；token 图 `tokens/pictures/c_chaos_tentacle.jpg` 为 960×701 RGB JPEG，源/缓存 SHA-256 均为 `3E9B7AE568DA09EDB9FA3EA6B35CE83D6C4664B0186B1BBA8422F96B1AF45DE4`。token 脚本源/AppData SHA-256 均为 `8F82729736735960E74868890EFB49C1DF0628B3C0648337492523B2B81EB62B`；重启 Forge 或重新加载资源后可进行客户端实测。

- 2026-07-12 修订 `脱困古神尤格萨隆`：新增恐惧与敏捷；其 `AbilityCast | ValidCard$ Card.Self | ValidSA$ Activated.Exhaust` 触发器只在尤格自身的竭绝被启动时造一个 `c_chaos_tentacle`（混乱触须）神器衍生物，当前不预置数值或效果，待后续需求补充。第二项竭绝由随机互伤替换为原生 `Goad | Defined$ Valid Creature.OppCtrl+nonArtifact`，煽惑所有对手操控的非神器生物。`test_yogg_saron_unbound.py` 6 项契约测试、尤格与触须 token 的单脚本 lint、DIY 全量 125 项测试本次均通过。尤格脚本源/AppData SHA-256 均为 `AE30C69719C024B48B58CC4037088C1BEFAD2667F01F0FFB21056DB9A17B73AF`；混乱触须 token 源/AppData SHA-256 均为 `DF8DC326BF7FE527EDB0C5C48189BFC54E9D84B640E87C0DF35C25BF64BB39CC`。需重启 Forge 或重新加载资源后完成客户端实测。

- 2026-07-12 新增 `脱困古神尤格萨隆`：`{15}` 7/5 传奇生物～古神。开局从手牌或牌库建立唯一的指挥区徽记；该徽记在你每次施放非生物咒语时获得一个 spell 指示物，并使你拥有的同名咒语减少等量无色费用。三项竭绝均为 `{T}`：获得目标非神器生物的操控权；复用 Forge 官方 `Season's Beatings` 的 `RepeatEach` / `ChooseCard AtRandom` 模式，使每个敌方生物随机对另一个敌方生物造成自身力量的伤害；以及按你坟墓场的瞬间/法术牌数量，重复“从全卡池随机命名瞬间或法术→化生→免费直接施放”。`test_yogg_saron_unbound.py` 4 项契约测试、单卡 lint 和 DIY 全量 123 项测试本次均通过。用户原图备份为 `tools/card-artwork/1-照片-1.jpg`，SHA-256 为 `75D3D116184DE12687D17AFFFDF4D34EAAB00608918040C94C592EE1CD24D235`；横向裁图 `PH01/脱困古神尤格萨隆.artcrop.jpg` 为 960×701 RGB JPEG，源/缓存 SHA-256 均为 `311279AF5EB7E5B2932277BEE77BADC4423E004512C672EF54275037CC3A0CD3`。脚本源/AppData SHA-256 均为 `18FF64EBFB445D74F617B0A98C6295B0BB9DFC58896526B4C19A225B89AF57AE`；已加入 PH01 #36 与桌面 zh-CN 显示文本。重启 Forge 或重新加载资源后可进行客户端实测。

- 2026-07-12 新增 `灵魂弹幕`：`{3}{B}{R}` 法术，复用 Forge 原生 `Repeat | MaxRepeat$ 6`，主异能锁定一个任意目标，子异能以 `Defined$ Targeted` 对同一目标逐次造成 1 点伤害，共 6 次；具有疯魔 `{0}`。`test_soul_barrage.py` 4 项契约测试、单卡 lint 和 DIY 全量 119 项测试通过。用户原图备份为 `tools/card-artwork/Soul_Barrage_full.jpg`，SHA-256 为 `2DDE88AB5BB4240D825E4A5F8649F58057F851CE556D38C406058437D01307E8`；标准裁切图 `PH01/灵魂弹幕.artcrop.jpg` 为 1280×934 RGB JPEG，源/缓存 SHA-256 均为 `4E656331617A43EF0538AF0C62A85490D394136B9D36368885CC0F1BC1445504`。脚本源/AppData SHA-256 均为 `6D1224AC0A8803F8D745E3B7916E0A05971CFD2EFBB32642117ABA9B31C74417`；PH01 #35 使用 `@Custom`，中文显示文本已追加。重启 Forge 或重新加载资源后可完成同一目标六次伤害与疯魔的客户端实测。

- 2026-07-12 修复 `矿坑老板雷斯卡` 的 `{U}`／`{B}` 减费：新增 Forge 引擎 `ColorChoice$ U B`。每一个坟墓场生物牌只会让操控者从费用中仍存在的 `{U}` 或 `{B}` 选择一个减少，绝不同时扣减两种颜色，也不减少无色费用。`CostAdjustmentColorChoiceTest` 覆盖 `{6}{U}{U}{B}{B}` 先选蓝、再选黑后的剩余费用和可选颜色；Forge Core/Game 全量 34 项、DIY 全量 115 项、单卡 lint 与 5 项契约测试均通过。桌面聚合 JAR 已构建；运行库 `BUILD-ID` 为 `20260712-172325`，JAR SHA-256 为 `9801ED3CFBB33DDC6B70115DC1575D7AD2532C0D122C2E6F5630310EF1BBBFD4`，雷斯卡脚本源/AppData/运行库 SHA-256 均为 `FF878728DE4BE5222ED5139552C0C5324BDD59DB1F0F9116473E50E7D46B108F`。运行库关键清单校验完成；需重启 Forge 后在施放时选择每张生物牌要支付的 `{U}` 或 `{B}`。

- 2026-07-12 新增并修订 `矿坑老板雷斯卡`：`{6}{B}{B}{U}{U}` 6/3 传奇生物～僵尸，敏捷。以 Forge 现有 `ReduceCost` 静态能力并使用 `Color$ U B`，使其每因你坟墓场中的一张生物牌而减少 `{U}{B}` 的施放费用；从任何区域进入坟墓场时，以 `GainControl | ValidTgts$ Permanent.nonLand` 获得目标非地永久物的操控权。`test_reska_the_pit_boss.py` 5 项契约测试、单卡 lint 与 DIY 全量 115 项测试通过。用户原图备份为 `tools/card-artwork/800px-Reska,_the_Pit_Boss_full.jpg`；标准裁切图 `PH01/矿坑老板雷斯卡.artcrop.jpg` 为 800×583 RGB JPEG，源/缓存 SHA-256 均为 `151EEE2D287F657DB8A98DB2C0D1539288351A600F093558EA3A5A6B9C7BB4E2`。修订后脚本源/部署 SHA-256 均为 `6529A004A00E7CCAD6A47815A5B1075861751FFF2C697761D22C347AF5245168`。PH01 #34 使用 `@Custom`，中文资源已追加；需重启 Forge 或重新加载资源后完成 `{U}{B}` 减费、坟墓场触发、永久操控权与动态牌框的客户端实测。

- 2026-07-12 新增 `镀银魔像`：`{3}` 3/3 神器生物～魔像，使用 Forge 既有 `Madness:0` 关键词实现疯魔 `{0}`。`test_silverware_golem.py` 3 项契约测试、单卡 lint 与 DIY 全量 110 项测试通过。用户原图备份为 `tools/card-artwork/Silverware_Golem_full.jpg`；标准裁切图 `PH01/镀银魔像.artcrop.jpg` 为 564×411 RGB JPEG，源/缓存 SHA-256 均为 `B96F84979384EECCACCA0CC1851CFAA8258EFB0B1681957262002BB483AD3932`。脚本源/部署 SHA-256 均为 `F90BBB82BA9DC4B3DDBA73F2328AE5B366D1E4F957B71E047430DC9CBC471053`。PH01 #33 使用 `@Custom`，中文资源已追加；需重启 Forge 或重新加载资源后完成疯魔与动态牌框的客户端实测。

- 2026-07-12 新增 `灵魂之火`：先检索官方卡牌库、自定义卡池及简体中文资源，确认不存在精确同名牌（`Soulfire`／`灵魂之火`）；仅有不同名称的 `Soulfire Eruption` 与 `Soulfire Grand Master`。本卡为 `{0}` 红黑双色法术，复用官方 `DealDamage | ValidTgts$ Any` 对任意目标造成 2 点伤害，再以 `Discard | Mode$ Random` 从你的手牌随机弃一张牌。`test_soulfire.py` 4 项契约测试、单卡 lint 与 DIY 全量 107 项测试通过。用户原图备份为 `tools/card-artwork/1024px-Soulfire_full.jpg`；标准裁切图 `PH01/灵魂之火.artcrop.jpg` 为 1024×747 RGB JPEG，源/缓存 SHA-256 均为 `E1B99E060013FE96B64EDC77FC501FC5DD4A58D0ADCCD95E3213862A7064DCE6`。脚本源/部署 SHA-256 均为 `F70C765A9520482A44792A061053502544F9A8B7ACB61C493E880D6481C67698`。PH01 #32 使用 `@Custom`，中文资源已追加；需重启 Forge 或重新加载资源后完成目标伤害、随机弃牌和动态牌框的客户端实测。

- 2026-07-12 新增 `栉龙`：`{U/B}` 1/2 生物～恐龙。进战场时以 Forge 既有 `Draw | RememberDrawn$ True` 抓一张牌并记住该牌；死去时只弃掉仍在手牌中的已记住牌，随后清理记忆；疯魔为 `{0}`。`test_compsognathus.py` 4 项契约测试、单卡 lint 与 DIY 全量 103 项测试通过。用户原图备份为 `tools/card-artwork/163557.png`；标准裁切图 `PH01/栉龙.artcrop.jpg` 为 376×274 RGB JPEG，源/缓存 SHA-256 均为 `08E6DAC5754EA9809BFB82713B0DDD20D939AFC9676A7D33AD6A21B647F059B3`。脚本源/部署 SHA-256 均为 `5E76636C01B82C019905E45724E2F0EACE4F2AE103A7D5A1980D01977FB49E82`。PH01 #31 使用 `@Custom`，中文资源已追加；需重启 Forge 或重新加载资源后完成进场抓牌、死亡弃牌和动态牌框的客户端实测。

- 2026-07-12 新增 `咒怨之墓`：`{0}` 黑色法术，以 `CardDiscover` 的 `Source$ Library` 从你的牌库发现一张牌；回合结束时，延迟触发仅弃掉以此法获得且仍在手牌中的该牌。为实现精确追踪，`CardDiscoverEffect` 新增可选 `RememberChosen$ True`：记录实际移动到目标区域的所选牌，省略该参数时不改变既有记忆状态；`CardDiscoverEffectTest` 新增回归覆盖。`test_cursed_catacombs.py` 5 项契约测试、单卡 lint、DIY 全量 99 项测试与 Forge Game 4 项针对性测试通过。用户原图备份为 `tools/card-artwork/1024px-Cursed_Catacombs_full.jpg`；标准裁切图 `PH01/咒怨之墓.artcrop.jpg` 为 1024×748 RGB JPEG，源/缓存 SHA-256 均为 `DC873930A13C35DF603F2208A71C20D78B2939F33564B3784CE9C4F58A9799F8`。脚本源/部署 SHA-256 均为 `A6222E3523FCFEFCC779B1963C8505666B98EE81614E01E8D059EE3387476AE4`。桌面聚合 JAR 已重建并写入运行库，`BUILD-ID` 为 `20260712-164300`、JAR SHA-256 为 `0A5621D46A685DE4FB242DD94FF1B0EFD4629CC31DAFFDD2E9088410A2B4720F`；运行库清单哈希已验证。客户端需重启后完成玩法实测。

- 2026-07-12 新增 `古尔丹之手`：`{4}{U}{U}` 法术，使用 Forge 已有 `Draw` 与 `Madness:0` DSL 实现“抓三张牌。\n疯魔{0}”。新增 `test_hand_of_guldan.py` 的 4 项契约测试通过，单卡 lint 无错误，DIY 全量 94 项测试通过。用户原图已备份为 `tools/card-artwork/1920px-Hand_of_Gul'dan_full.jpg`；标准裁切图 `PH01/古尔丹之手.artcrop.jpg` 为 1920×1401 RGB JPEG，源/缓存 SHA-256 均为 `851F2D4661816BE6DCD291FB0BD7D04E79EB8A04C7C684249A055602B47FDA08`。脚本源/部署 SHA-256 均为 `07C3C23FC8C1CB719B05D502210B0C3B433BFC2D8438A76CCE29EF614E6E9054`。客户端中文资源已追加名称、类别与规则文字；需重启 Forge 或重新加载资源后完成客户端实测。

- 2026-07-12 新增 `虚触侍从`：`{R}` 1/3 生物～人类／术士。复用官方 `Embermaw Hellion` 的 `DamageDone` 替代效应语法；在战场上时，任何牌手将要受到的伤害均增加 1 点。原画备份为 `tools/card-artwork/Voidtouched_Attendant_full.jpg`，裁图 `PH01/虚触侍从.artcrop.jpg` 为 640×467 RGB JPEG。新增契约测试 3 项、单卡 lint 与 DIY 全量 86 项测试通过；脚本与图片源/部署 SHA-256 分别为 `98DAD78D1B0CC98802BABFA37C544CF5F64ECFB4ABF2C68237B0641B5318A257`、`4748E140A2D95F547FE0BFDC20BF818AA4A84B8E59E93EF502D4E5F2BCEEE97D`，均一致。PH01 #28 使用 `@Custom`；需重启 Forge 或重新加载资源后完成客户端伤害递增与动态牌框复测。

- 2026-07-11 新增 `异教低阶牧师`：`{U}{B}` 3/2 生物～人类／牧师。脚本费用使用 Forge 普通多色格式 `ManaCost:U B`，而非代表蓝／黑混血费用的紧凑写法 `UB`。使用 Forge 现有 `RaiseCost` 静止异能，使对手施放的瞬间和法术咒语增加 `{1}` 来施放；能力没有 `Duration$` 或 `Unique$`，因此仅随来源在战场存在并可由多个副本叠加。原画备份为 `tools/card-artwork/Cult_Neophyte_full.jpg`，裁图 `PH01/异教低阶牧师.artcrop.jpg` 为 566×413 RGB JPEG。目标测试 3 项、单卡 lint 与 DIY 全量 83 项通过；脚本与图片源/部署 SHA-256 分别为 `671CBEC7900CD8B92E8F7F11E2EF952E04C66782D303B94BEB62C16B7E5A3163`、`A827E99067D314FC83E9D57C7A2CE2863277B3763B42322A46C2CDA5A1D280B3`，均一致。PH01 #27 使用 `@Custom`；需重启 Forge 或重新加载资源后完成客户端牌框与实际费用叠加复测。

- 2026-07-11 新增 `前沿哨所`：`{2}` 2/3 无色生物～墙，守军。战场限定的 `ChangesZone` 触发器捕获从任何区域进入对手手牌的非地牌，并通过 `Animate | Duration$ Perpetual | staticAbilities$ RaiseCost` 使该牌永久获得“施放此牌的费用增加{1}”。能力没有 `Unique$`；每座哨所和每次区域变化各自结算并产生独立 perpetual 时间戳，因此可以叠加。原图备份为 `tools/card-artwork/800px-Far_Watch_Post_full.jpg`，裁图 `PH01/前沿哨所.artcrop.jpg` 为 800×584 RGB JPEG。目标测试 4 项、单卡 lint 和 DIY 全量 80 项通过；脚本与图片源/部署 SHA-256 分别为 `829B8C8D21C47E27E84CE2BFDA7F2103DA1B4DD7D6CAF71D19F9375CD0B3D114`、`90388639B2C6D513A56A6AE45DCFE6122305554C883CF8B3E732439EF24F6078`，均一致。PH01 #26 使用 `@Custom`，桌面聚合 JAR 于 20:48:13 构建成功；需重启后完成客户端叠加实测。

- 2026-07-11 新增 `棱彩光束`：`{7}` 无色瞬间，使用 Forge 现有 `DamageAll` 同时对目标对手及其操控的每个生物和鹏洛客各造成 3 点伤害；ability-level `ReduceCost$ X` 以 `Count$Valid Creature.TargetedPlayerCtrl,Planeswalker.TargetedPlayerCtrl` 计算目标对手的对应永久物总数。中文文字仅纠正用户误写的“碰洛克”为“鹏洛客”，其他内容保持原文。原图确认为纯原画，备份到 `tools/card-artwork/1920px-Prismatic_Beam_full.jpg`，居中裁为 1920×1401 RGB JPEG `PH01/棱彩光束.artcrop.jpg`。单卡 lint、目标测试 3 项及 DIY 全量 76 项通过；脚本与图片源/部署 SHA-256 分别为 `E01DEC4417AD9D57FE5A36999D30A5D438F4C756D772113DCDC05A21EB576042`、`E7D3F8D4EDB4ADB14C771E200F75B701BE7CEDF22758E95BB8FBE78658FBAE5D`，均一致。PH01 #25 使用 `@Custom`；桌面聚合 JAR 于 20:36:57 构建成功。当前 Forge 进程早于本次构建，需重启后完成客户端实测。

- 2026-07-11 将“决战！”由法术改为瞬间，并修复客户端卡图键：内部名、脚本文件、PH01 条目和裁图统一规范化为无标点的 `决战`，`cardnames-zh-CN.txt` 仍显示“决战！”且类别为“瞬间”。已删除源目录和部署目录中带全角叹号及 `.full` 的旧兼容资源，避免同名歧义；新脚本与 `PH01/决战.artcrop.jpg` 的源/部署 SHA-256 均一致。单卡 lint、`test_showdown.py` 6 项和 DIY 全量 73 项测试通过；桌面聚合 JAR 于 20:15:41 构建成功。当前运行中的旧进程仍在对局，需重启后完成新 JAR 与新图片键的客户端显示复测。

- 2026-07-11 修复“炉石传说”与“农夫”的客户端运行时问题：`StaticAbility.getLayers()` 现会把 `SetMaxHandSize` / `RaiseMaxHandSize` 明确加入规则层，避免与 `AddKeyword` 共存时最大手牌仍回落到 7；“炉石传说”的 `NewGame` 触发使用 `ResolveBeforeFirstTurn$ True`，在首个阶段开始前建立徽记，因此先手第一次维持即可获得 1 点法术力；普通 `NewGame` 触发仍使用堆叠。“农夫”触发器新增 `TriggerZones$ Battlefield`，不会再从手牌触发。持久法力仍使用 `PersistentMana$ True`，`ManaPoolPersistentManaTest` 验证其跨阶段保留并在 cleanup 清空。目标 Java 测试 6 项、两张卡 Python 测试 11 项、DIY 全量 73 项均通过，两张卡 lint 无错误；桌面聚合 JAR 于 19:50:38 构建成功，`javap` 确认包含三个新参数路径，卡牌源/部署哈希一致。新客户端进程 PID 39324 于 19:51:11 从该 JAR 启动；对局内人工复测仍需在此新进程中完成。

本文件只记录验证层级和证据。功能语义见 [KEYWORDS.md](KEYWORDS.md)，项目状态见 [PROJECT.md](PROJECT.md)。迁移文档时未重新运行的结果均标为历史证据。

## Status definitions

- **Design approved:** 规则与架构已确认，但可能没有代码。
- **Implemented:** 当前源码或脚本中存在实现。
- **Automated tests passed:** 有记录表明相关自动测试通过；必须注明是本次还是历史运行。
- **Deployed:** 源内容已复制/构建到实际 Forge 目标。
- **Client verified:** 已在真实 Forge 客户端中完成对应玩法验证。

## Mechanism status

| Feature | Design | Implemented | Automated tests | Deployed | Client verified |
|---|---|---|---|---|---|
| 基础 DIY 卡牌与 PH01 | Yes | Yes | 历史通过 | 历史已同步 | 部分历史验证 |
| Superreach / Ignore Superreach | Yes | Yes | 历史 Java 与 DIY 测试通过 | 历史已构建部署 | 历史记录为已验证 |
| IgnoreDeckLimits | Yes | Yes | 历史 Forge Core 与 DIY 测试通过 | 历史已构建部署 | 未在本次迁移复验 |
| Boarding | Yes | Yes | 历史 Java 与 DIY 测试通过 | 历史已构建部署 | 仍待明确完整客户端对局验证 |
| DeckMinimum / 炉石传说 | Yes | Yes | 存在 DIY 测试与历史实施记录 | 构建产物存在 | 未在本次迁移复验 |
| DIY 中文本地化安装 | Yes | No | No | No | No |
| CardDiscover | Yes | Yes | 2026-07-11 迁移后 3 项针对性测试通过；同次 Forge Game 目标集 11 项通过 | 新版桌面 JAR 已构建，待安装 | No |
| 空中悍匪 | Yes | Yes | DIY 28 项测试及单卡 lint 通过 | PH01 与卡牌已同步 | No |

## Historical automated evidence

- 2026-07-11 新增 `水晶学`：实现为 `{U}` 法术，使用 Forge 既有 `ChangeZone` 的 `Creature.powerEQ1` 过滤与 `AtRandom$ True` 随机选择语法，从牌库中随机选择至多两张力量为 1 的生物牌置入手上，然后洗牌。`test_crystology.py` 4 项契约测试、单卡 lint 与 DIY 全量 72 项测试本次均通过。原画备份为 `tools/card-artwork/Crystology.jpg`；标准裁图 `PH01/水晶学.artcrop.jpg` 为 1024×747 RGB JPEG，源/缓存 SHA-256 均为 `E37B9F2CBDC8F4F331305226D3B2DE564F84453BEDC0D75697B295C6155DC51A`。脚本源/部署 SHA-256 均为 `B93532F8AF62E24991EB8C5ACD475CBB8900E3A810B17F2918A5FDDF57FC3157`；PH01 #24 使用 `@Custom`，中文资源已补全。需重启 Forge 或重新加载资源后完成客户端实测。

- 2026-07-11 新增 `十字军光环`：实现为 `{2}{W}{R}` 结界；你操控的每个生物获得“每当该生物攻击时，它永久获得+2/+1。”攻击触发，使用现有 `AddTrigger` 与 `Pump Duration$ Permanent` 语法。`test_crusader_aura.py` 4 项契约测试、单卡 lint 与 DIY 全量 68 项测试本次均通过。原画备份为 `tools/card-artwork/十字军光环.jpg`；标准裁图 `PH01/十字军光环.artcrop.jpg` 为 1280×934 RGB JPEG，源/缓存 SHA-256 均为 `502200C3EA835E311F53BD70C957E438CFFF2C2FE02DE0CE98302AF7C1A9D518`。脚本源/部署 SHA-256 均为 `174922FBA614728FAE41A7D965BBB77A1DDFFEAD9FC66706FCA41780595D1098`；PH01 #23 使用 `@Custom`，中文资源已补全。需重启 Forge 或重新加载资源后完成客户端实测。

- 2026-07-11 新增 `援军光环`：实现为 `{1}{W}{U}` 结界，具有 `Vanishing:3`；在你的结束步骤开始时，使用 `Creature.cmcLE2` 从牌库搜寻一张法术力值等于或小于 2 的生物牌放进战场，然后洗牌。规则为强制触发，未加入 `OptionalDecider$`。`test_reinforcement_aura.py` 4 项契约测试与单卡 lint 本次通过，DIY 全量 64 项测试通过。原画备份为 `tools/card-artwork/1920px-Reinforcement_Aura_full.jpg`；标准裁图 `PH01/援军光环.artcrop.jpg` 为 1920×1401 RGB JPEG，源/缓存 SHA-256 均为 `3A5CD7D52C91C0DF2B3EDE708B0F5930E8EDD64AD8EB2AAE6B2063CEDDEBCD50`。脚本源/部署 SHA-256 均为 `38B157FA0E881C6B0B56634CFCD8E9EC3FFAE876AE788A6765A9DC3E26C1D6D2`；PH01 #22 使用 `@Custom`，中文资源已补全标准措辞。需重启 Forge 或重新加载资源后完成客户端实测。

- 2026-07-11 新增 `决战！`：实现为 `{W}{R}` 法术，使用 Forge 官方 `RepeatEach | RepeatPlayers$ Player` 与 `TokenOwner$ Player.IsRemembered` 模式，使每位牌手各派出三个名为“歹徒”的 3/3 无色生物衍生物，且它们具有敏捷异能。新增 `c_3_3_outlaw_haste` token 脚本及自定义 token 图片；安装脚本现会把 `tokens/pictures/` 同步到 Forge token 图片缓存。`test_showdown.py` 6 项契约测试、主牌与 token 单文件 lint 本次通过，DIY 全量 60 项测试通过。主牌裁图为 1024×747 RGB JPEG，源/缓存 SHA-256 均为 `F439C1423FA63D489F320390AA5C381AE93538AB9CEC78AD202ABEDD3D077EA5`；token 裁图为 423×309 RGB JPEG，源/缓存 SHA-256 均为 `F9A748CEA44111B59BDB25EEDC30D1E578247501EE5D01E0E05226C63739FB49`。主牌脚本与 token 脚本的源/部署哈希也分别一致；PH01 #21 使用 `@Custom`，中文资源采用标准化后的规则文字。需重启 Forge 或重新加载资源后完成客户端实测。

- 2026-07-11 新增 `农夫`：实现为 `{U}` 2/1 人类／平民；在你的维持开始时抓一张牌。`test_peasant.py` 的 4 项契约测试与单卡 lint 本次通过，DIY 全量 54 项测试通过。原画保留为 `tools/card-artwork/800px-Peasant_full.jpg`；标准裁切图 `cards/pictures/PH01/农夫.artcrop.jpg` 为 800×584 RGB JPEG，并已由安装脚本同步到 Forge 缓存。PH01 #20 使用 `@Custom`，让 Forge 在 `Crop` 模式中自动绘制牌框与文本。`forge-gui/res/languages/cardnames-zh-CN.txt` 已追加名称、类别和“在你的维持开始时，抓一张牌。”的中文显示文本；需重启 Forge 或重新加载资源后完成客户端实测。

- 2026-07-11 新增 `尼鲁巴蛛网领主`：按用户提供的完整成品卡图实现 `{1}{B}` 1/4 蜘蛛／亡灵，以及“对手的生物咒语施放费用增加 `{2}`”的 `RaiseCost` 静态规则。`test_nerubian_weblord.py` 3 项契约测试通过，单卡 lint 无错误，DIY 全量 50 项测试通过。完整卡图原件保留为 `tools/card-artwork/282017.jpg`，并原样复制为 `PH01/尼鲁巴蛛网领主.full.jpg`，源/缓存 SHA-256 均为 `52EBCA0049551412AD0CC40CF7BA906579D1F0DC0A9213C0D46FA9896DC34E20`。为使全局 `Crop` 模式下仍直接显示完整卡图，PH01 #19 不含 `@Custom`，已确认部署版本表也是该无画师条目。脚本源/部署 SHA-256 均为 `B1128F1B29045BC1652242D6B94FAE4B1F8DF1951ABEA6163B1BD35A55145A81`；需重启 Forge 或重新加载资源后完成客户端实测。

- 2026-07-11 新增 `战斗号角`：脚本使用 Forge 官方 `ChangeZone` 牌库搜索模式，选择至多三张 `Creature.cmcLE2` 并直接进入战场。`test_battle_horn.py` 4 项契约测试通过，单卡 lint 无错误，DIY 全量 47 项测试通过。原图备份为 `tools/card-artwork/Call_to_Arms_full.jpg`；标准裁切图 `PH01/战斗号角.artcrop.jpg` 为 1920×1401 RGB JPEG（约 1.37:1），源/缓存 SHA-256 均为 `378CA8CBA05FA094D2504F5332B6E7EB84385DAD7BAF0E4CEAD7EEFCAA872BD9`。脚本源/部署 SHA-256 均为 `15CAE36069D4EE85640F1A11C3DAF44307E897E42841E46DFE47957FF4A29FDF`。客户端中文资源已追加用户指定的名称、类别与规则文字；需重启 Forge 或重新加载资源后完成客户端实测。

- 2026-07-11 炉石传说疲劳规则：新增 `PlayerFatigueTest` 的两项 TestNG 回归测试通过。空牌库中一次 `drawCards(3)` 在启用疲劳后使同一牌手依次承受 1、2、3 点生命损失，后续一次空抽承受 4 点；第二位牌手独立从 1 开始。`mvn -s .mvn/local-settings.xml -pl forge-game -am -Dtest=PlayerFatigueTest -Dsurefire.failIfNoSpecifiedTests=false test` 为 `BUILD SUCCESS`。DIY 全量 43 项测试通过，炉石传说单卡 lint 无错误。桌面聚合 JAR 已重新构建，`javap` 确认其中含有 `Player.takeFatigue()`、`Player.getFatigueCount()`、`TakeFatigueEffect` 和 `ApiType.TakeFatigue`。安装脚本已同步炉石传说脚本到 `%APPDATA%\Forge\custom`，源/部署 SHA-256 一致；客户端玩法验证仍需重启后完成。

- `forge-core/target/surefire-reports/forge.deck.DeckFormatIgnoreDeckLimitsTest.txt` 记录 `DeckFormatIgnoreDeckLimitsTest` 10 项通过。
- `forge-game/src/test/java/forge/game/keyword/BoardingTest.java` 和 `forge-game/src/test/java/forge/game/combat/CombatUtilTest.java` 是当前源码中的针对性测试。
- DIY 测试包括 `test_chainbreaker_hogger.py`、`test_deck_limit_override_cards.py`、`test_hearthstone_card.py`、`test_markzul_imp.py`、`test_superreach_cards.py` 和 `test_lint_card.py`。
- 2026-07-10 本次运行：Forge Game 19 项测试全部通过；DIY 28 项测试全部通过；`空中悍匪.txt` linter 无错误。
- 2026-07-10 解除构筑限制排障：`DeckFormatIgnoreDeckLimitsTest` 10 项通过。确认旧桌面聚合 JAR 内仍是未包含 `IgnoreDeckLimits`/`DeckMinimum` 的旧 `forge-core`；重新安装 `forge-core` 后构建并部署聚合 JAR，使用 `javap` 确认部署产物包含 `IGNORE_DECK_LIMITS`、`DECK_MINIMUM`、`hasDeckLimitOverride` 与 `effectiveMainDeckMinimum`。客户端已重启，仍待点击“开始对战”复验。
- 原始逐任务命令与结果保留在 [docs/archive/README.md](docs/archive/README.md) 所索引的历史计划和旧验证日志中。

## Standard verification commands

```powershell
python -m unittest discover -s tests -p "test_*.py"
python tools\lint_card.py cards\<color>\<card>.txt
```

Java 测试从 `D:\Forge\forge-latest` 执行，并使用实际模块和测试类，例如：

```powershell
.\mvnw.cmd -pl forge-game -am -Dtest=BoardingTest,CombatUtilTest test
.\mvnw.cmd -pl forge-core -am -Dtest=DeckFormatIgnoreDeckLimitsTest test
```

具体 Maven 参数应以当前 wrapper 和模块行为验证，不能只依据历史文档假定。

## Deployment evidence

- `tools/install_to_forge.ps1` 当前会同步卡牌、版本、token 和卡图。
- `%APPDATA%\Forge\custom` 与 `%LOCALAPPDATA%\Forge\Cache\pics\cards` 当前存在实际部署内容。
- 2026-07-11 `Gigantic Spright` 特殊素材分支修复：已将“支付踢增时横置的生物是否包含神器生物、结界生物或传奇生物”的判定从进场后的 `ChangesZone` 触发移入 `ETBReplacement` 分支，避免堆叠到战场的区域变更使支付对象记录失效。普通素材分支以一个 `COMPONENT` 指示物进场；特殊素材分支以两个指示物进场，并复用 Forge ETB/Riot 类的 `Animate` 敏捷模式直到回合结束。目标回归测试与单卡 lint 本次通过；全量 42 项仍仅有既有的霍格第二画面尺寸断言失败。安装脚本已同步，源码与部署脚本 SHA-256 同为 `21A190271039C13F458F282E8CD23FE4F82BF81B42481DACE8DAC2C9027AA96A`。当前已运行客户端早于同步启动，仍需重启或资源重载后验证普通素材与特殊素材两条分支。
- 2026-07-11 `Gigantic Spright` 客户端排障：发现 `%APPDATA%\Forge\custom\cards\colorless` 同时残留旧的 `Gigantic Spright.txt`（仅有 Oracle、无实际异能）与当前 `gigantic_spright.txt`，Forge 因同名定义加载旧脚本，导致施放时没有横置两个生物的踢增选项，且牌上不存在 `{T}` 起动式异能。已删除旧文件并增加部署唯一性回归测试；当前部署只保留 `gigantic_spright.txt`，其 SHA-256 与源码同为 `9A01DF3702B4AD9532B7C0F9B1EF08AF549651169723FD43602FD19EAB8643EB`。目标测试 4 项和单卡 lint 本次通过；全量 42 项中仅有既有的霍格图片尺寸断言失败。由于两个 Forge 客户端进程均早于修复启动，仍需重启客户端后完成玩法复验。
- 2026-07-11 本次运行：`Gigantic Spright` 已按参考 DSL 实装追加横置费用、素材指示物、特殊素材的第二枚指示物与敏捷，以及移除素材检索印刷身材 2/2 生物的起动式异能。根据最终需求，未保留“非从手牌施放”的回合锁、替代效应或 `!wasCast` 近似逻辑。目标契约测试 3 项与单卡 lint 通过；脚本源/部署 SHA-256 均为 `9A01DF3702B4AD9532B7C0F9B1EF08AF549651169723FD43602FD19EAB8643EB`。桌面 zh-CN 资源保留英文名 `Gigantic Spright`，并采用用户确认的最终中文文本。原画备份为 `tools/card-artwork/gigantic_spright_original.webp`；标准裁切图 `PH01/Gigantic Spright.artcrop.jpg` 为 1264×923 RGB JPEG，已同步缓存，源/缓存 SHA-256 均为 `7139BA7A9DC06CC189B07747572AD59D94269BA13145947B02ACA874FE61DBC4`。
- 2026-07-10 本次运行：新增 `空降歹徒`（{2} 2/2 海盗）。卡牌契约测试 3 项通过，单卡 lint 通过；客户端桌面中文资源已加入“当一个海盗在你的操控下进战场时，你可以从手牌中免费施放这张牌”。原画备份为 `tools/card-artwork/Parachute_Brigand_original.jpg`；标准裁切图 `PH01/空降歹徒.artcrop.jpg` 为 1000×730 RGB JPEG，已同步到 Forge 缓存，源/缓存 SHA-256 均为 `65CAC0D04756538693B088FF35BF8DD81B4AFDF32D0ED9B19DC5E048FD89585F`。单画面版本不会在图片名中包含 `1`；Forge 只在同一版本存在多画面记录时才查找编号文件。
- 2026-07-10 本次运行：`海盗帕奇斯` 已通过 Card Conjurer 的 `Borderless` + `zh-CN` 任务生成完整卡图；使用客户端权威中文条目，规则文字左对齐，字号为 `0.023`、每行 25 个中文字符，以尽量放大文字并保持全文和句末标点不被裁切。成品写入 `cards/pictures/PH01/海盗帕奇斯.full.jpg` 并同步到 Forge 缓存；源文件与缓存文件的 SHA-256 均为 `3269003A72A0999590F98762706B649A1F81EE33E392E2141C4B55D2DF6CE69E`，两处均可读取为 2010×2814 RGB JPEG。单卡 lint 通过；Card Conjurer 自动化测试 15 项通过。DIY 全量 Python 测试为 37 项通过、1 项既有失败：`test_chainbreaker_hogger_alt_art` 仍期望霍格第二画面为 1500×2092，但当前未改动文件实际为 2010×2814。
- 2026-07-11 引擎迁移：Java 源切换到 `D:\Forge\forge-latest`，上游基线为 `ebf900109c882d7027b0651ddcff65a57519237a`（`ebf9001`）。`CardDiscover` 已在 `ApiType` 注册并恢复 `CardDiscoverEffect`；`BranchEffect` 增加了将父能力 `ReplacingObjects` 传递给选中分支的修复，使需要替代效应上下文的子能力不会丢失对象。
- 同次串行 Forge Game 目标测试报告为 11 项通过、0 失败、0 错误、0 跳过；其中 `CardDiscoverEffectTest` 的三项覆盖“最多三种不同名称”“候选不足上限”“空池或非正上限”，`BranchEffectTest` 覆盖被选分支继承替代效应对象。
- 新桌面聚合 JAR 已在补迁 `NewGame` 路由后重新构建为 `D:\Forge\forge-latest\forge-gui-desktop\target\forge-gui-desktop-2.0.14-SNAPSHOT-jar-with-dependencies.jar`（39,325,385 字节，SHA-256 `CF499A80DE72FA5390CF62DB7585BA5E0148985F01AC954ADE49767D8ED392FC`）。`javap` 已确认聚合 JAR 包含 `TriggerHandler.shouldUseStack` 及 `TriggerType.NewGame` 判断；桌面快捷方式引用该固定 JAR 路径，但已经运行的客户端必须重启后才会载入本次重建产物。
- 2026-07-11 custom 工作区迁入 `D:\Forge\forge-latest\custom`：旧 `forge-diy` 的 123 个文件全部有对应文件，0 个缺失；另增加 `custom/README.md` 与 `reference/MagicCompRules 20260619.txt`。当前文档和工具路径已切换到 `forge-latest`，历史归档保留旧路径作为原始记录。
- 同次迁移补回旧快照的 `NewGame` 静态触发堆栈路由与两份测试。TDD 红阶段因 `TriggerHandler.shouldUseStack` 不存在而编译失败；实现最小路由判断后，Forge Core 10 项与 Forge Game 14 项目标测试均为 0 失败、0 错误，桌面模块 Maven package 为 `BUILD SUCCESS`。
- 从新 `custom` 目录首次运行 Python 全量测试共 42 项：41 项通过，唯一失败为既有霍格第二画面尺寸断言（期望 1500×2092，实际 2010×2814）。当前图片流程已明确 Card Conjurer 成品为 2010×2814，因此迁移时同步更新了该过期断言；最终复验结果记录在下方。从新目录运行 `tools/install_to_forge.ps1` 成功同步 17 张卡牌、PH01 版本与 9 个图片文件。
- 删除旧 `forge-master` 与旧独立 `forge-diy` 目录后，从 `D:\Forge\forge-latest\custom` 重新运行 42 项 Python 测试，结果为全部通过；`Gigantic Spright` 与海盗帕奇斯的单卡 lint 也均无错误。这证明当前 custom 源、测试和工具不依赖已删除的旧路径。
- 构建产物存在不等于当前所有源码改动都已部署，也不等于客户端行为已经验证。

## Manual verification backlog

- 在完整对局中验证 Boarding 的多次伤害、离场和晚进入手牌/牌库边界。
- 确认当前桌面客户端确实加载最新聚合 JAR。
- 中文本地化实现后，在中文界面逐卡检查名称、类别和规则文字。
- 在客户端验证 `CardDiscover` 的 0、1、2、3 个选项、同名去重、数据库来源及双方牌库来源。
