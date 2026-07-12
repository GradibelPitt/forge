# 决战瞬间与图片键修复 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将决战改为瞬间，并用无标点内部名修复 Forge 卡图查找。

**Architecture:** 脚本、版本登记和图片文件共享内部键 `决战`；中文资源独立显示“决战！”。不修改 token 与卡牌效果。

**Tech Stack:** Forge card DSL、Python unittest、PowerShell

---

### Task 1: 失败测试

- [ ] 更新 `custom/tests/test_showdown.py`，要求 `Name:决战`、`Types:Instant`、PH01 `21 R 决战 @Custom`、中文显示“决战！”且类别为“瞬间”。
- [ ] 运行目标测试并确认因当前旧脚本失败。

### Task 2: 最小实现

- [ ] 将卡牌脚本重命名为 `决战.txt` 并修改内部名称与类别。
- [ ] 更新 PH01 和 `cardnames-zh-CN.txt`。
- [ ] 删除带全角叹号和 `.full` 的冗余图片，只保留 `决战.artcrop.jpg`。

### Task 3: 验证部署

- [ ] 运行单卡 lint、目标测试和 DIY 全量测试。
- [ ] 运行安装脚本并移除部署目录中的旧脚本与旧冗余图片。
- [ ] 核对源/部署哈希，重启或重新加载客户端后检查实际卡图。
