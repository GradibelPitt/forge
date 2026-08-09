# Forge DIY Agent Guide

这是所有 agent 的唯一强制入口。`D:\Forge\forge-latest\custom` 是 DIY 内容的 source of truth；`D:\Forge\forge-latest` 是同一 Git 工作树中的 Forge 引擎源码。不要把安装目录或构建产物当作首选编辑位置。

## Required reading by task

先读本文件，然后只加载任务需要的主文档：

| Task | Required documents |
|---|---|
| 新增或修改卡牌脚本 | [PROJECT.md](PROJECT.md)、[CARDS.md](CARDS.md)、相关 `reference/` 文档 |
| 新增关键词或 Java 机制 | [PROJECT.md](PROJECT.md)、[ARCHITECTURE.md](ARCHITECTURE.md)、[KEYWORDS.md](KEYWORDS.md)、[docs/DESIGN.md](docs/DESIGN.md) |
| 卡图 | [ARCHITECTURE.md](ARCHITECTURE.md)、[VERIFICATION.md](VERIFICATION.md)、[图片导入流程.md](图片导入流程.md)、[简洁版图片工作流.md](简洁版图片工作流.md) |
| 汉化或部署 | [ARCHITECTURE.md](ARCHITECTURE.md)、[VERIFICATION.md](VERIFICATION.md) |
| 判断功能是否完成 | [VERIFICATION.md](VERIFICATION.md) 并检查实际源码/运行时 |
| 追溯旧决策或命令 | [docs/archive/README.md](docs/archive/README.md)，按需读取单个归档文件 |

历史文档不属于默认阅读路径，也不是当前事实的权威来源。

## Non-negotiable rules

1. **不得发明参数。** 只使用当前 Forge 源码、`reference/` 或已验证官方卡牌中存在的 API、触发器、过滤条件和关键词写法。
2. **复杂效果先找官方范例。** 使用 `python tools/find_similar.py` 或搜索 `D:\Forge\forge-latest\forge-gui\res\cardsfolder\`。
3. **Oracle 不实现逻辑。** `Oracle:` 和描述字段只负责显示；实际行为必须由 `K:`、`A:`、`T:`、`S:`、`R:`、`SVar:` 或 Java 引擎实现。
4. **脚本优先。** 能用现有 DSL 准确实现时不要修改 Java；需要新引擎能力时必须有明确授权和测试。
5. **引用必须闭合。** `Execute$`、`SubAbility$`、`ReplaceWith$`、`AdditionalAbility$` 等名称必须有对应定义且类型正确。
6. **目标与定义不可混用。** 规则写“目标”时使用目标字段；非目标选择和已解析对象使用 `Defined$` 等机制。
7. **状态必须分层。** 设计批准、代码存在、自动测试通过、部署完成和客户端实测是五种不同状态。
8. **保护用户改动。** 不覆盖无关文件，不使用破坏性 Git 命令，不因缺少 Git 仓库自行初始化。
9. **每次制卡完成后立即发布。** 新增或修改卡牌脚本、图片、Token、中文文本或仅使用现有 DSL 的效果时，完成与改动相称的验证和本机部署后，必须立即创建范围明确的源码 commit 并 push 到 `GradibelPitt/forge` 的 `diy` 分支；随后用 `publish_git_payload.ps1 -SyncCustom` 更新运行仓库，完成运行时门禁后立即 commit 并 push `GradibelPitt/forge-diy-runtime` 的 `main` 分支。每张卡、每次修改都执行，不等待后续批次。
10. **精准注入补丁完成后立即发布。** 任何 Java 引擎、Ability API、规则执行路径或引擎级 keyword 的改动，在目标测试和必要回归测试通过后，优先只构建受影响模块，并用 `publish_git_payload.ps1 -Module <module> -SyncCustom` 发布 overlay JAR；只有跨模块/API、依赖、资源打包边界或明确的新基线才重建桌面聚合 JAR。随后必须立即 push 源码与运行仓库，并核对两个远端 ref，避免联机双方加载不同引擎。
11. **新卡与简中翻译是同一发布单元。** 每张新卡都必须在 `forge-gui/res/languages/cardnames-zh-CN.txt` 有完整四字段记录；发布运行仓库时用 `publish_git_payload.ps1 -SyncCustom` 自动同步简中资源。`CardTranslation` 只在启动时预载，未重启的客户端不得记为中文显示已验证。
12. **发布脚本不等于 Git 发布。** `publish_git_payload.ps1` 只生成或同步 payload 与发布元数据，不会代替测试、暂存、commit 或 push。脚本成功后仍必须审查差异、只暂存本次目标文件、运行运行时门禁、提交、推送并确认远端提交；不得用 `git add -A` 混入无关工作。

## Change workflow

1. 检查当前脚本、Java 源码、测试和相关主文档。
2. 新功能或修复遵循测试驱动：先写失败测试并确认失败原因正确。
3. 实现最小改动，通过目标测试后再整理代码。
4. 卡牌必须登记到正确版本，内部名称必须与版本条目和图片查找一致。
5. 更新对应的权威主文档；不要把新状态只写入历史 plan/design。
6. 源内容验证完成后，才运行 `tools/install_to_forge.ps1`；每次完成卡牌后都使用 `publish_git_payload.ps1 -SyncCustom` 发布运行 payload，由脚本一并同步简中资源。
7. 每次卡牌/图片/本地化/纯 DSL 改动完成后，立即分别提交并 push 源码仓库和运行仓库，再核对两个远端 ref；中文显示的客户端验收必须发生在资源同步并重启之后。
8. Java/API/规则路径/引擎级 keyword 改动完成验证后，立即构建并精准注入受影响模块的 overlay，更新源码仓库与运行仓库并 push；只有确有打包边界时才改用桌面聚合 JAR，不得留到后续卡牌批次。

## Validation before handoff

至少完成与改动相称的验证：

```powershell
python tools\lint_card.py <card-script>
python -m unittest discover -s tests -p "test_*.py"
```

涉及 Java 时运行对应模块的针对性测试和必要回归测试。交付时在 [VERIFICATION.md](VERIFICATION.md) 中准确记录本次实际运行的验证，不用历史结果冒充新结果。

## Historical research

历史 design、plan 和被替代旧文档全部保存在 `docs/archive/`。先查看 [归档索引](docs/archive/README.md)，只读取与当前问题相关的文件。若归档内容与当前源码或主文档冲突，以当前源码、用户需求和主文档为准。
