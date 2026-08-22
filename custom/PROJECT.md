# Forge DIY Project

本文件说明项目目标、当前状态与通用工作流。Agent 的强制入口见 [AGENTS.md](AGENTS.md)，路径布局见 [ARCHITECTURE.md](ARCHITECTURE.md)。

## Document map

- [AGENTS.md](AGENTS.md)：agent 必读入口与强制规则。
- [ARCHITECTURE.md](ARCHITECTURE.md)：代码、卡牌、图片、汉化、构建和部署位置。
- [KEYWORDS.md](KEYWORDS.md)：自定义关键词与引擎 API。
- [CARDS.md](CARDS.md)：现有及已批准卡牌清单。
- [VERIFICATION.md](VERIFICATION.md)：测试、构建、部署与客户端验证状态。
- [docs/DESIGN.md](docs/DESIGN.md)：当前有效设计决策。
- [docs/HANDOFF.md](docs/HANDOFF.md)：卡牌制作、发布、Windows 启动链与历史产物交接简报。
- [docs/archive/README.md](docs/archive/README.md)：历史设计、计划和旧文档索引。

## Purpose and source of truth

`D:\Forge\forge-latest\custom` 是自定义卡牌、版本、图片、音乐、文档和相关契约测试的权威来源。`D:\Forge\forge-latest` 是同一 Git 工作树中的 Forge 引擎源码来源；其上游基线为 `ebf900109c882d7027b0651ddcff65a57519237a`（`ebf9001`），本项目的迁移改动与 custom 内容都保留在该工作树中。复制到 Forge 用户目录的内容和构建出的 JAR 都是部署产物，不应作为首选编辑位置。

项目同时覆盖两层：Magic/自定义规则定义“效果是什么”，当前 Forge 源码、官方脚本和已验证 DSL 定义“如何执行”。

## Authority hierarchy

处理规则与实现时按以下顺序核查：

1. 用户本次明确需求与已批准设计；
2. Magic 规则或项目自定义规则语义；
3. 当前 Forge Java 源码；
4. 当前 Forge 脚本文档和 `reference/`；
5. 当前官方卡牌脚本；
6. 本项目历史设计与计划。

历史文档可能过时，不能覆盖当前源码和主文档。

## Current implemented state

- 桌面主页已退役独立的 Quest、Puzzle 与 Gauntlet 模式：不再注册其侧栏入口、首页文档、成就集合或启动期 Quest 存档加载，普通牌组保存也不再刷新 Gauntlet 单例；旧 `EDocID` 名称仅以空文档保留给源码和既有布局兼容。单人构筑、轮抓、现开、多人联机、普通 Commander/EDH、牌组编辑器及其共享预组牌数据保持可用；Bazaar 的 Quest 状态改为仅在显式打开该旧视图时延迟创建。
- 测试牌已从 `PH01` 拆分到独立 `TEST` 版本；凡内部名称含 `test`（不区分大小写）的牌只登记在 `editions/Test_Set.txt`。`PH01` 保留正式 DIY 卡牌及其原收藏编号。
- `Superreach`、`Ignore Superreach`、`Boarding`、`IgnoreDeckLimits` 和 `DeckMinimum` 已在当前 Forge 源码中找到实现或使用点。
- 构筑大厅以独立“炉石传说”模式替代原“魔王／魔王混斗”复选框；模式位于“时空竞逐”之后。规则完全由引擎持有，不再登记或加载同名神器、徽记或指挥区对象。
- 炉石模式使用 30 点初始生命、至少 30 张主牌、7 张起手、10 张手牌上限与递增疲劳；每个维持阶段选择一种基本地加入手牌。生物的已标记伤害跨 cleanup 保留，攻击牌手还可为每个攻击生物选择一个目标生物并强制该阻挡。飞行与威慑按反向层级筛选：具备异能的攻击者可选择不具备者，不具备异能的攻击者不能选择具备者。
- 通用 `GameRule` 规则牌保护机制仍保留给其他未来规则牌：在起手与调度之前放逐并锁定在放逐区，同时从发现、化生／制造、批量数据库替换、选牌、直接打出和复制中排除。
- 破链灾星霍格通过 `StartingDeckLegendaryPermanents` 直接枚举注册牌手当前主牌 `CardPool`，开局复制其中其他传奇永久物，并提供传奇规则徽记与超级延势；复制结果不依赖结算时的手牌/牌库状态，也不会像数据库发现那样物化全卡池候选。
- 启迪者伊利斯通过 `Count$StartingDeckDuplicateNonlandNames` 检查注册的起始主牌构筑；地牌不参与重名判定，同名不同版本会合并计数，满足条件时在施放触发结算时化生当前手牌中每张牌的一个复制。
- 海盗帕奇斯通过牌张脚本从手牌/牌库登场；突牙使用引擎级 `Boarding:3`。
- 卡图同步工具已支持从 `cards/pictures/` 安装到 Forge 本地图片缓存。
- 简中卡牌资源以 `forge-gui/res/languages/cardnames-zh-CN.txt` 为唯一源码；开发客户端在启动时预载该文件，运行仓库的 `publish_git_payload.ps1 -SyncCustom` 会同时同步到 `app/res/languages/cardnames-zh-CN.txt` 并校验哈希，避免只发布卡牌而漏发中文类别或规则文字。
- 最新引擎迁移已恢复 `CardDiscover`，并修复 `BranchEffect` 在分支解析时传递替代效应对象；新桌面聚合 JAR 已构建，安装到实际客户端与客户端对局验证仍待单独记录。
- 旧快照中的 `NewGame` 静态触发堆栈路由和持久法术力清理回归测试已适配到最新版；普通静态触发仍保持立即结算。
- `Card.isValid` 支持 `printedNamed<name>`，在牌因复制状态改变当前名称后仍可按关联 `PaperCard` 的原始牌名筛选；`殒命暗影` 用它跳过连续施放的其他殒命暗影，并在没有更早合格瞬间／法术时以无效果咒语正常结算。
- `LifeReduced` 替代事件可用 `ValidSource$` 精确筛选造成非伤害生命损失的牌张来源；`LifeLoseEffect` 与辐射结算会传递其实际来源，费用、疲劳和法力灼烧等无来源生命损失保持不匹配。伤害来源仍由 `DamageDone` 替代事件筛选。
- 玩家级 `GrantSpellRule` 已接入真实费用与法术力转换热路径；规则在本局内独立于来源牌持续存在，支持稳定键幂等或显式叠加。叠加规则会保留各次独立的名称快照，重叠牌名累计减费而新增牌名只获得包含它的层数；其独立 `Harmony` 模式按牌的拥有者向游戏内卡牌与 `CardView` 投射可见“调和”关键字，并以专用减费语义先减有色符号、再减非 X 通用费用，不改变旧 `ReduceGeneric`。“神秘访客”每次进场登记一层独立快照规则。
- 结算异常分为显式可恢复与未知异常：只有能证明在当前效果写入前失败的 `RecoverableEffectException` 会跳过该效果、写入 stderr／对局日志并继续；其他 `RuntimeException`／`Error` 仍终止比赛。终止路径会结束所有控制器并绕过认输确认强制关闭比赛页，再明确弹出“比赛因意外错误退出”；某个非致命清理步骤再次失败也不会阻断后续关页和弹窗。本地、移动端默认实现与联机客户端共用同一失败回调。

详细状态与证据见 [VERIFICATION.md](VERIFICATION.md)。

## Development workflow

1. 阅读 [AGENTS.md](AGENTS.md) 并按任务类型加载相关主文档。
2. 从当前源码和官方卡牌中验证 DSL 或 Java 扩展点。
3. 新功能或修复先写能正确失败的测试。
4. 只实现使测试通过的最小改动。
5. 运行相关 Java/Python 测试与 `python tools/lint_card.py <card>`。
6. 审查差异和状态表述；源文件正确后再运行 `tools/install_to_forge.ps1`。每次完成卡牌后使用 `forge-diy-runtime/tools/publish_git_payload.ps1 -SyncCustom`，让卡牌、音乐与简中资源作为同一 payload 发布。
7. 立即分别提交并 push 源码仓库和运行仓库，确认 `forge:diy` 与 `forge-diy-runtime:main` 的远端 ref 指向本次提交。
8. 将“自动测试”“已部署”“Git 已发布”“客户端重启后实测”分别记录，不互相替代。

## Git 保存与发布策略

### 每次卡牌和数据层改动

以下改动完成相称验证和本机部署后，必须立即发布，不再等待卡牌批次：

- 卡牌或 Token 脚本；
- 卡图、Token 图片和图片流程；
- `cardnames-zh-CN.txt` 等显示文本；
- 只复用 Forge 现有 DSL、无需修改 Java 的卡牌效果；
- 与制卡/机制任务一起更新的项目文档和测试数据；纯文档维护只提交并 push 源码仓库，不生成无变化的运行 payload。

每次卡牌内容执行顺序为：

1. 只暂存本次目标源码，创建范围明确的 commit，并 push 到 `https://github.com/GradibelPitt/forge` 的 `diy` 分支；
2. 在运行仓库执行 `publish_git_payload.ps1 -SyncCustom`，使用唯一 `BUILD-ID` 同步卡牌、版本、图片、Token 和简中资源；
3. 审查运行仓库差异，只暂存本次 payload 与发布元数据，运行 `tests/test_scripts.ps1`；
4. 创建运行仓库 commit，并 push 到 `https://github.com/GradibelPitt/forge-diy-runtime` 的 `main` 分支；
5. 核对两个远端 ref。`publish_git_payload.ps1` 本身不会测试、commit 或 push，不能把脚本成功当成 Git 已发布。

### 引擎层改动

以下改动不得只保存在本地，也不得等待后续卡牌批次：

- Forge Java 引擎源码；
- 新增或修改 Ability API；
- 规则执行、触发、替代效应、战斗、费用、牌手状态等核心路径；
- 需要 Java 注册、解析或执行的新 keyword；
- 会造成两台客户端运行语义不同的任何改动。

完成相应 Java 测试和必要回归测试后，立即执行精准注入发布：

1. 只构建受影响模块并检查目标类/方法进入模块 JAR；用 `publish_git_payload.ps1 -Module <module> -SyncCustom` 更新 `app/overlays/<module>.jar`；
2. commit 并 push 到 `https://github.com/GradibelPitt/forge` 的 `diy` 分支；
3. 更新 `https://github.com/GradibelPitt/forge-diy-runtime` 的 `app/`、关键哈希清单和 `BUILD-ID`；
4. push 运行仓库，并做一次公开无凭据 clone/更新验证；
5. 告知联机双方先运行一键启动脚本，确认显示相同 `BUILD-ID` 后再对战。

只有跨模块/API、依赖或资源打包边界，或者明确建立新基线时，才重建桌面聚合 JAR。普通卡牌和精准注入引擎补丁都必须在每次完成后立即发布源码与运行仓库。

## Constraints and open work

- 不得发明 Forge DSL 参数、关键词写法或过滤表达式。
- 除非用户明确要求引擎实现，否则卡牌逻辑优先留在 `cards/`。
- 新机制若与 Forge 现有同名机制含义冲突，必须使用独立内部名称。
- 中文本地化仍待实现；`CardDiscover` 和“空中悍匪”已实现并通过自动测试。旧部署有历史记录，但迁移后的新桌面 JAR 仍待安装与客户端对局实测。
- `custom/` 已并入 `forge-latest` Git 工作树；不要在 `custom/` 内再次初始化嵌套仓库。
