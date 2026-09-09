# 游戏内更新保护策略 2

入口仍为 `CSubmenuDownloaders → AutoUpdater → DiyUpdateBridge → diy-updater.ps1`。保护器 `DiyProtection.java`、历史保护目录与脚本内嵌在同一 GUI JAR；脚本按实际 overlay 顺序提取保护资源。缺失任一资源即停止。

## 牌名模糊搜索是明确的 DIY 保护合同

- `CardNameSearchIndex`：中英文别名、Unicode 规范化、精确/前缀/子串/编辑距离排序、稳定候选顺序；只索引原候选对象并复用索引。
- `LatestSearchGeneration`：新查询或窗口关闭后，旧结果不能覆盖当前列表。
- 桌面 `ListChooser`：后台索引和搜索、180 毫秒防抖、中文输入法组合态、按钮/回车、查询中禁用旧选择、空结果提示、取消和释放、大列表固定单元尺寸避免 UI 线程遍历全部译名。
- `GuiChoose`、`CardFaceView`、`PlayerControllerHuman.chooseCardName` 两个入口、`chooseCardNameFromCandidates` 和 `chooseOptionalCardNameFace`：显示中文与英文，最终返回合法候选的内部牌名；取消和普通强制选牌流程各自保持原义。
- 原测试 `CardNameSearchIndexTest`、`PlayerControllerHumanCardNameTest`、`ListChooserTest` 必须保留。

搜索索引、异步代次、ListChooser、GuiChoose、CardFaceView 不从官方覆盖；目录再次登记这些完整类和上述玩家方法，绑定门禁继续检查调用方及依赖。不能用“搜索类仍存在”替代完整保护。

历史提交 `0eabbb306884283c99d552ab3adba524769a6d15` 确认双语模糊搜索来自 DIY 分支。`SFilterUtil.memoizeTextFilter(Predicate<PaperCard>,boolean)` 和 `buildTextFilter(String,boolean,boolean,boolean,boolean,boolean)` 的缓存接入亦纳入历史保护合同，不只保留窗口类。

## 历史归属下限

按照 2026-09-09 提供的《代码块历史归属》《历史归属全量清单》《明确代码块与历史依据》逐项核对：56 个自有文件、451 个新增成员、382 个共享增补成员和 16 个结构边界项均已直接覆盖，未发现遗漏或签名定位失败。发布资源 `diy-protection-history.tsv` 固定这 905 条历史合同及 2 条当前适配合同；生成未来目录时必须先核验，缺失/改名/当前实现变化需要审核，不能从新差异中悄悄消失。另核验 Miracle 与 LifeReduced 当前接口适配，以及 TypeLists 中 Quest/Mystery 登记和 Warmwood 资源。

该历史清单中的“共享增补”证明局部代码的来源；16 个结构项只是需要审核的边界，不能当作旧实现自动回填脚本。官方 `ComparableOp`、`ImageKeys.ENDURING_STORY_IMAGE`、LobbyPlayer sleeves 接口和旧 `AscendEffect` 不列为 DIY 自有代码。当前差异及依赖门禁可能仍要求对它们的变动进行兼容审核，此类保守限制不等于原创归属。完整方法保护是当前执行器的保守边界，并非声称历史清单中的整段官方方法都是 DIY。

## 目录与门禁

当前 DIY 源码相对记录的官方基线，所有桌面 main Java 的差异都会进入目录，不限于几个关键词名字。新增 DIY Java 整文件保护；共享 Java 保护字段、构造器、方法、初始化块、类型头和字段初始化顺序；DIY 有意删除的官方成员不得被自动恢复。定位不使用行号，注释和排版不改变共享成员摘要。独有文件仍要求字节不变。

包含 DIY 逻辑的共享方法按**整个方法**保留，包括控制流、调用顺序、参数和匿名类。当前实现不自动拼接同一方法中的官方语句块，比评估文档的最细粒度块合并更保守。

JDK 类型分析解析参数类型、局部变量作用域、符号引用、继承和虚方法覆盖。合并前记录、构建后复核；受保护成员的调用方及递归被调依赖发生改变即停止。公共依赖较多，所以较大比例的引擎变更可能需要人工兼容。不会在绑定失败、歧义或不支持语法时退化成文本匹配。此门禁不宣称任意 Java 程序语义等价；反射行为和实际像素仍需行为/客户端验证。

自动差异目录包含 KEYWORDS.md 对应的实现、注册、API 和接入：Harmony 注册表及视图生命周期、CardDiscover 与官方 Discover 的独立注册、Boarding、Mystery、Quest、Windfury、Durability、Fatigue、LifeReduced、DrawnAll、DrawFromLibrary、Superreach、构筑账本、Miracle 原生费用来源、Warmwood 和共享绘制代码。`custom/**`、Warmwood 皮肤、DIY 资源/翻译/测试及更新策略通过独立文件清单保护。中文大文件和自定义 overlay 均跳过官方资源覆盖。

后续版本继承目录和文件清单，不能随官方基线推进而缩小保护范围。`.dck` 在读取/复制/哈希前排除，不读取、备份或改动用户牌组。

## 获取与执行

1. 官方基线优先取上一代 update-state，其次显式开发参数或 release.json 的 `upstreamCommit`。已审核源码 `0d87c2c71c269d645188ece26413ef89f4b9519a` 可兼容映射到 `4bee0abda5277ad8b8def2ed1229458bb7121fc0`；未知版本缺元数据就停止，不再默认 `ebf900...`。
2. `blob:none` 获取配合桌面稀疏检出；包含 parent/build 元数据和 core、game、ai、gui、gui-desktop、custom。移动平台等目录不检出。官方保护基线仅提取五模块 main Java。隔离根 POM 移除非桌面 reactor 条目，当前安装不删目录。
3. cardsfolder 只接收新增，edition 接收新增/修改，但已有运行资源若含本地修改则停止。五模块 Java 新增/修改参与三方合并与保护检查；删除/重命名、构建依赖改动和冲突仍要求审核。
4. 当前执行的策略资源带入候选，防止旧源码重新打包出旧更新器。执行桌面生产/测试源码编译及测试，验证保护成员、绑定依赖、文件清单、搜索相关类和内嵌策略一致。
5. 本地候选使用单一新聚合 JAR，不复制旧 overlay。资源复制排除牌组和 junction。全部通过后才原子切换 `updates/active.json`，保留旧版。

状态记录官方/本地源码提交、策略版本、保护目录/文件清单/保护器摘要、JDK、验证门禁和产物摘要。job 中有 `plan.json`、`result.txt`、`update.log`、`baseline-bindings.tsv`；版本目录保留 `protection.tsv`、`protected-files.json`、`update-state.json`。失败不激活候选，也不推送到 GitHub。

替换运行模块前关闭 Forge；重新启动后的真实按钮和搜索交互是独立验收项。最新实际验证见 `../VERIFICATION.md`。
