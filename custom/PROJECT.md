# Forge DIY Project

本文件说明项目目标、当前状态与通用工作流。Agent 的强制入口见 [AGENTS.md](AGENTS.md)，路径布局见 [ARCHITECTURE.md](ARCHITECTURE.md)。

## Document map

- [AGENTS.md](AGENTS.md)：agent 必读入口与强制规则。
- [ARCHITECTURE.md](ARCHITECTURE.md)：代码、卡牌、图片、汉化、构建和部署位置。
- [KEYWORDS.md](KEYWORDS.md)：自定义关键词与引擎 API。
- [CARDS.md](CARDS.md)：现有及已批准卡牌清单。
- [VERIFICATION.md](VERIFICATION.md)：测试、构建、部署与客户端验证状态。
- [docs/DESIGN.md](docs/DESIGN.md)：当前有效设计决策。
- [docs/archive/README.md](docs/archive/README.md)：历史设计、计划和旧文档索引。

## Purpose and source of truth

`D:\Forge\forge-latest\custom` 是自定义卡牌、版本、图片、文档和相关契约测试的权威来源。`D:\Forge\forge-latest` 是同一 Git 工作树中的 Forge 引擎源码来源；其上游基线为 `ebf900109c882d7027b0651ddcff65a57519237a`（`ebf9001`），本项目的迁移改动与 custom 内容都保留在该工作树中。复制到 Forge 用户目录的内容和构建出的 JAR 都是部署产物，不应作为首选编辑位置。

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

- `PH01` 当前有 17 个不同卡名、18 条版本登记（`破链灾星霍格` 的 `8` 与 `8a` 为两张画面记录），包含测试牌、机制测试牌、海盗牌、`Gigantic Spright`、破链灾星霍格及“炉石传说”。
- `Superreach`、`Ignore Superreach`、`Boarding`、`IgnoreDeckLimits` 和 `DeckMinimum` 已在当前 Forge 源码中找到实现或使用点。
- 破链灾星霍格通过脚本提供开局复制传奇永久物、传奇规则徽记与超级延势。
- 海盗帕奇斯通过牌张脚本从手牌/牌库登场；突牙使用引擎级 `Boarding:3`。
- 卡图同步工具已支持从 `cards/pictures/` 安装到 Forge 本地图片缓存。
- 最新引擎迁移已恢复 `CardDiscover`，并修复 `BranchEffect` 在分支解析时传递替代效应对象；新桌面聚合 JAR 已构建，安装到实际客户端与客户端对局验证仍待单独记录。
- 旧快照中的 `NewGame` 静态触发堆栈路由和持久法术力清理回归测试已适配到最新版；普通静态触发仍保持立即结算。

详细状态与证据见 [VERIFICATION.md](VERIFICATION.md)。

## Approved but not implemented

- DIY 中文本地化源和安装脚本的幂等合并流程已经设计，但当前工作区尚无 `translations/cardnames-zh-CN.txt`，安装脚本也尚未实现该流程。

## Development workflow

1. 阅读 [AGENTS.md](AGENTS.md) 并按任务类型加载相关主文档。
2. 从当前源码和官方卡牌中验证 DSL 或 Java 扩展点。
3. 新功能或修复先写能正确失败的测试。
4. 只实现使测试通过的最小改动。
5. 运行相关 Java/Python 测试与 `python tools/lint_card.py <card>`。
6. 审查差异和状态表述；源文件正确后再运行 `tools/install_to_forge.ps1`。
7. 将“自动测试”“已部署”“客户端实测”分别记录，不互相替代。

## Constraints and open work

- 不得发明 Forge DSL 参数、关键词写法或过滤表达式。
- 除非用户明确要求引擎实现，否则卡牌逻辑优先留在 `cards/`。
- 新机制若与 Forge 现有同名机制含义冲突，必须使用独立内部名称。
- 中文本地化仍待实现；`CardDiscover` 和“空中悍匪”已实现并通过自动测试。旧部署有历史记录，但迁移后的新桌面 JAR 仍待安装与客户端对局实测。
- `custom/` 已并入 `forge-latest` Git 工作树；不要在 `custom/` 内再次初始化嵌套仓库。
