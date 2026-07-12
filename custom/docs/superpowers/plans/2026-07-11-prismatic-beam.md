# 棱彩光束 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新增棱彩光束的卡牌脚本、标准裁图、中文资源与部署内容。

**Architecture:** 使用 Forge 现有 DamageAll 和 ability-level ReduceCost，不修改 Java。目标对手同时驱动伤害对象集合与费用减少计数。

**Tech Stack:** Forge card DSL、Python unittest、Pillow/System.Drawing、PowerShell

---

### Task 1: 写失败契约测试
- [ ] 新建 `custom/tests/test_prismatic_beam.py`，断言费用、瞬间类别、目标伤害、目标对手计数费用减少、PH01 #25、中文原文和图片路径。
- [ ] 运行测试，确认因资源尚不存在而失败。

### Task 2: 实现卡牌与流程规则
- [ ] 新建 `custom/cards/colorless/棱彩光束.txt`。
- [ ] 在 `Placeholder_Set.txt` 登记 `25 R 棱彩光束 @Custom`。
- [ ] 补充 `cardnames-zh-CN.txt` 与 `CARDS.md`。
- [ ] 在图片流程中记录错字／wording 修正规则。

### Task 3: 图片、验证与部署
- [ ] 备份原图并生成 `PH01/棱彩光束.artcrop.jpg`。
- [ ] 运行单卡 lint、目标测试和 DIY 全量测试。
- [ ] 运行安装脚本，核对脚本与图片源/部署 SHA-256。
- [ ] 重启或重载 Forge 后完成客户端检查。

