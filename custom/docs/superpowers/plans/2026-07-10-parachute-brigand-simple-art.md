# 空降歹徒与简洁卡图工作流 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新增 2/2、费用 {2} 的海盗“空降歹徒”，并用原画裁切出的 Forge 标准文字牌框插画部署到客户端。

**Architecture:** 卡牌规则使用 Forge 现有 `Play` + `WithoutManaCost` DSL，从手牌可选施放该卡。原画保留在工作区工具素材目录，生成的横向 `1.artcrop.jpg` 作为唯一游戏图资源，由安装脚本同步到缓存。简洁流程以独立文档记录，不改变完整扩画流程。

**Tech Stack:** Forge card DSL、Python unittest、PowerShell System.Drawing、Forge 图片缓存同步脚本。

---

### Task 1: 定义新卡与中文资源

**Files:**
- Create: `cards/red/空降歹徒.txt`
- Modify: `editions/Placeholder_Set.txt`
- Modify: `D:/Forge/forge-latest/forge-gui/res/languages/cardnames-zh-CN.txt`
- Test: `tests/test_parachute_brigand.py`

- [ ] **Step 1: 写入失败契约测试**

```python
self.assertIn("Name:空降歹徒", text)
self.assertIn("ManaCost:2", text)
self.assertIn("Types:Creature Pirate", text)
self.assertIn("PT:2/2", text)
self.assertIn("WithoutManaCost$ True", text)
self.assertIn("ValidZone$ Hand", text)
self.assertIn("16 C 空降歹徒 @Custom", edition)
```

- [ ] **Step 2: 运行测试并确认其因卡牌尚不存在而失败**

Run: `python -m unittest tests.test_parachute_brigand`

- [ ] **Step 3: 添加最小卡牌脚本、PH01 登记和中文条目**

```text
Name:空降歹徒
ManaCost:2
Types:Creature Pirate
PT:2/2
T:Mode$ ChangesZone | Origin$ Any | Destination$ Battlefield | ValidCard$ Pirate.YouCtrl | TriggerZones$ Hand | Execute$ TrigCast | TriggerDescription$ Whenever a Pirate enters under your control, you may cast CARDNAME from your hand without paying its mana cost.
SVar:TrigCast:DB$ Play | Defined$ Self | ValidSA$ Spell | ValidZone$ Hand | Controller$ You | WithoutManaCost$ True | Optional$ True | Amount$ 1
Oracle:Whenever a Pirate enters under your control, you may cast 空降歹徒 from your hand without paying its mana cost.
```

- [ ] **Step 4: 运行新增测试与 linter**

Run: `python -m unittest tests.test_parachute_brigand; python tools/lint_card.py cards/red/空降歹徒.txt`

### Task 2: 生成并部署标准插画裁切图

**Files:**
- Create: `tools/card-artwork/Parachute_Brigand_original.jpg`
- Create: `cards/pictures/PH01/空降歹徒1.artcrop.jpg`

- [ ] **Step 1: 保留原图副本**

Copy `C:/Users/Marsh/Desktop/Parachute_Brigand_full.jpg` 到 `tools/card-artwork/Parachute_Brigand_original.jpg`。

- [ ] **Step 2: 从原图制作横向 RGB JPEG**

使用固定裁剪框 `(x=0, y=100, width=1000, height=730)`；输出必须为 1000×730 的 RGB JPEG，保留人物面部、双手和伞降动作。

- [ ] **Step 3: 同步并核对**

Run: `powershell -ExecutionPolicy Bypass -File tools/install_to_forge.ps1`

Compare SHA-256 for `cards/pictures/PH01/空降歹徒1.artcrop.jpg` and `%LOCALAPPDATA%/Forge/Cache/pics/cards/PH01/空降歹徒1.artcrop.jpg`.

### Task 3: 记录简洁标准流程

**Files:**
- Create: `简洁版图片工作流.md`
- Modify: `VERIFICATION.md`

- [ ] **Step 1: 写入简洁工作流**

记录输入、原图保留、约 1.37:1 横向裁切、主体默认居中或略偏上、`1.artcrop.jpg` 命名、`@Custom` 版本登记、安装与哈希验证；明确默认不使用 AI 扩画或 Card Conjurer。

- [ ] **Step 2: 更新实际验证证据**

记录本次卡牌 lint、裁切图尺寸、源/缓存 SHA-256 及测试结果。
