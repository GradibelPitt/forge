# 异教低阶牧师 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新增使用 Forge 动态牌框的异教低阶牧师，并实现只在战场生效、可叠加的对手瞬间与法术费用增加。

**Architecture:** 使用卡牌脚本的 `RaiseCost` 静止异能，不修改 Java。图片走纯原画 `.artcrop.jpg` 与 `@Custom` 分支，安装脚本负责同步脚本、版本、本地化和图片。

**Tech Stack:** Forge card DSL、Python unittest/Pillow、PowerShell 安装脚本。

---

### Task 1: 契约测试

**Files:**
- Create: `custom/tests/test_cult_neophyte.py`

- [ ] 写入契约测试，断言普通蓝黑费用脚本 `ManaCost:U B`、人类／牧师、3/2、`RaiseCost` 的 `ValidCard$ Instant,Sorcery`、`Activator$ Opponent`、`Type$ Spell`、`Amount$ 1`，且不存在永久持续时间或唯一性限制。
- [ ] 断言 PH01 #27、中文条目、原画备份和横向 RGB 裁图。
- [ ] 运行 `python -m unittest tests.test_cult_neophyte -v`，确认因卡牌尚不存在而失败。

### Task 2: 最小卡牌与资源实现

**Files:**
- Create: `custom/cards/multicolor/异教低阶牧师.txt`
- Modify: `custom/editions/Placeholder_Set.txt`
- Modify: `custom/CARDS.md`
- Modify: `forge-gui/res/languages/cardnames-zh-CN.txt`

- [ ] 创建卡牌脚本：`ManaCost:U B`、`Types:Creature Human Cleric`、`PT:3/2`，静止能力使用 `Mode$ RaiseCost | ValidCard$ Instant,Sorcery | Activator$ Opponent | Type$ Spell | Amount$ 1`。
- [ ] 登记 `27 R 异教低阶牧师 @Custom`，补充 CARDS 清单和中文条目 `异教低阶牧师|异教低阶牧师|生物～人类／牧师|对手施放的瞬间和法术咒语增加{1}来施放。`。
- [ ] 运行目标契约测试，确认脚本与文本部分通过，仅图片部分仍失败。

### Task 3: 图片处理与安装

**Files:**
- Create: `custom/tools/card-artwork/Cult_Neophyte_full.jpg`
- Create: `custom/cards/pictures/PH01/异教低阶牧师.artcrop.jpg`

- [ ] 保留桌面原图副本；按人物面部和施法动作居中裁成约 1.37:1 的 RGB JPEG。
- [ ] 运行目标契约测试和 `python tools/lint_card.py cards/multicolor/异教低阶牧师.txt`。
- [ ] 运行 `powershell -ExecutionPolicy Bypass -File .\tools\install_to_forge.ps1`。
- [ ] 比较源图与 `%LOCALAPPDATA%\Forge\Cache\pics\cards\PH01\异教低阶牧师.artcrop.jpg` 的 SHA-256，要求一致。

### Task 4: 回归验证

**Files:**
- Modify: `custom/VERIFICATION.md`

- [ ] 运行 `python -m unittest discover -s tests -p "test_*.py"`。
- [ ] 检查部署脚本、PH01、中文资源和图片缓存均存在且与源文件一致。
- [ ] 在 VERIFICATION.md 记录测试数量、裁图尺寸与哈希；不把未完成的客户端实测写成已完成。
