# Forge DIY 文档整合设计

## 目标

将面向维护者和 agent 的零散 Markdown 整理为职责单一、入口明确的主文档体系。历史设计与实施计划必须保留并归档，供后续追溯原始决策、命令和验证过程；归档文件不再属于默认阅读路径。

`reference/` 下的 Forge API、触发器、替代效应和卡牌脚本参考资料不属于本次整合范围。

## 目标结构

```text
forge-diy/
├─ AGENTS.md
├─ PROJECT.md
├─ ARCHITECTURE.md
├─ KEYWORDS.md
├─ CARDS.md
├─ VERIFICATION.md
└─ docs/
   ├─ DESIGN.md
   └─ archive/
      ├─ designs/
      └─ plans/
```

## 主文档职责

### `AGENTS.md`

作为所有 agent 的唯一入口，保持简短并包含：

- 强制阅读顺序；
- 不得发明 Forge 参数、必须验证官方实现等不可违反的规则；
- 修改卡牌、引擎、汉化、卡图时应查阅的对应文档；
- lint、测试和部署前后的最低验证要求；
- 历史材料位于 `docs/archive/`，仅在追溯特定功能时按需阅读。

它不再重复完整 DSL 教程、项目状态或每张卡牌说明。

### `PROJECT.md`

融合现有 `memory.md` 和 `instruction.md`，并吸收 `DIY-README.md` 中仍适用的项目目标与日常工作流：

- 项目定位和 source-of-truth；
- 当前功能状态；
- 通用开发流程；
- Forge 规则、引擎源码、官方脚本和 DIY 文件的权威顺序；
- 当前已知约束与未完成事项。

### `ARCHITECTURE.md`

记录实际文件布局和数据流，至少覆盖：

- DIY 卡牌脚本、版本表、token 与测试；
- Forge Java 引擎模块及自定义机制入口；
- 卡图源文件与同步后的游戏内卡图目录；
- 中文名称、类别、规则文字的汉化文件；
- 构建产物、桌面运行时 JAR 与部署目标；
- 工具脚本以及卡牌从源码到游戏客户端的同步路径。

所有路径必须通过当前文件系统检查后记录，不能仅从旧文档推测。

### `KEYWORDS.md`

独立记录自定义关键词和类似关键词的引擎级 API。每项包括：

- 玩家可见语义；
- 卡牌 DSL 写法；
- Java 注册点和核心实现文件；
- 状态记录、触发时机和边界行为；
- 相关自动测试；
- 与 Forge 已有同名机制冲突时的处理方式。

首批至少包含 `Superreach`、`Ignore Superreach`、`IgnoreDeckLimits`、`Boarding`，以及经实现后加入的炉石式 `CardDiscover`。尚未实现的机制必须明确标记为“已批准设计”或“待实现”，不能写成已验证状态。

### `CARDS.md`

吸收 `test_cards.md` 并汇总现有 DIY 卡牌。每张卡只记录稳定信息：

- 费用、类型、身材；
- 脚本路径和版本编号；
- 效果摘要及依赖的关键词/API；
- 必要的特殊实现说明。

不复制大段可直接从 `.txt` 读取的脚本，除非片段对于解释机制必不可少。

### `VERIFICATION.md`

吸收 `update.md` 和现有状态记录中的验证信息，按功能而非聊天时间组织：

- 已运行的自动测试与结果；
- linter 状态；
- 构建和运行时部署状态；
- 已完成的客户端验证；
- 尚待手工验证的项目。

状态必须区分“设计批准”“代码实现”“自动测试通过”“已部署”和“客户端实测”。

### `docs/DESIGN.md`

合并现有 design 文档中仍有效的最终设计决策，包括卡牌规则、引擎边界、关键词行为和本次文档治理结构。只保留当前结论和必要理由，不复制逐任务实施步骤。

新功能在设计批准后先更新 `docs/DESIGN.md`；复杂或高风险功能可以在开发期间使用临时独立规格，完成整合后再归档。

## 历史归档

现有文件按原内容完整移动：

- `docs/superpowers/specs/*.md` → `docs/archive/designs/`
- `docs/superpowers/plans/*.md` → `docs/archive/plans/`

历史 plan 和 design 均不得删除或覆盖。归档保留原文件名，使日期、主题和搜索关键词不变。

在 `docs/archive/README.md` 建立索引，说明：

- 归档只用于历史追溯；
- 当前事实以根目录主文档和 `docs/DESIGN.md` 为准；
- 每份历史文档的主题、日期及当前状态；
- 对应功能的当前主文档入口。

## 旧文件处理

完成内容迁移与核对后：

- `memory.md`、`instruction.md` 的职责由 `PROJECT.md` 接替；
- `test_cards.md` 的职责由 `CARDS.md` 接替；
- `update.md` 的职责由 `VERIFICATION.md` 接替；
- `DIY-README.md` 的有效内容分别进入 `PROJECT.md`、`ARCHITECTURE.md` 和 `AGENTS.md`。

上述旧文件在内容核对完成后移入 `docs/archive/legacy/`，不直接删除，以避免外部引用立即失效并保留原始材料。`AGENTS.md` 原地重写，因为它必须继续作为约定入口。

## 一致性规则

- 同一事实只在一个主文档中详细定义，其他文档使用相对链接。
- 路径与运行时状态必须实地检查。
- 已批准但未实现的功能不得写入“当前实现”章节。
- 历史文档不得作为当前状态的权威来源。
- 所有主文档顶部说明其职责，并链接 `AGENTS.md` 或相邻主文档。
- agent 默认只需读取 `AGENTS.md`，再根据任务类型读取两到三个相关主文档。

## 验收

1. 根目录只有六个面向日常维护的主 Markdown 文档。
2. 所有历史 design、plan 和被替代旧文档仍可在 `docs/archive/` 找到。
3. `AGENTS.md` 给出明确且最小的按任务阅读路径。
4. 自定义关键词、卡牌、架构和验证状态不再混写。
5. 所有主文档内部链接有效。
6. 文档中记录的关键路径均存在，或被清楚标记为构建/部署时生成。
7. 搜索旧功能名称仍能找到历史设计和当前入口。
