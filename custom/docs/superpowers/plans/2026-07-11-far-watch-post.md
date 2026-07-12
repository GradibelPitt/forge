# 前沿哨所 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新增前沿哨所，并保证战场限定与多来源永久费用叠加。

**Architecture:** ChangesZone 触发器捕获进入对手手牌的非地牌；Animate + Duration Perpetual 为触发牌添加独立 RaiseCost 静态能力。图片使用纯原画标准裁图流程。

**Tech Stack:** Forge card DSL、Python unittest、Pillow、PowerShell

---

### Task 1: 失败测试
- [ ] 新建 `test_far_watch_post.py`，覆盖费用、身材、守军、战场触发区域、任意来源进入手牌、非地对手牌、perpetual RaiseCost 及无唯一限制。
- [ ] 运行并确认资源缺失失败。

### Task 2: 卡牌与图片
- [ ] 新建脚本、PH01 #26、中文资源与 CARDS 条目。
- [ ] 备份原图并裁为 800×584 RGB art crop。

### Task 3: 验证部署
- [ ] 运行 lint、目标测试和全量测试。
- [ ] 同步卡牌和图片、构建桌面 JAR、核对哈希。
- [ ] 重启 Forge 后验证显示与实际叠加。

