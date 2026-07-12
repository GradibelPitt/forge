# 炉石传说与农夫运行时修复 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复炉石传说最大手牌与首回合递增持久法力，并限制农夫只在战场触发。

**Architecture:** 卡牌区域错误在 DIY 脚本中修复；最大手牌失效在 Forge 静态能力分层源头修复。徽记法力通过真实游戏测试确认其计数与清理生命周期，仅在证据表明脚本解析错误时调整炉石脚本。

**Tech Stack:** Forge Java/TestNG、Forge 卡牌 DSL、Python unittest、Maven、PowerShell

---

### Task 1: 建立失败回归测试

**Files:**
- Modify: `custom/tests/test_peasant.py`
- Modify: `custom/tests/test_hearthstone_card.py`
- Create or modify: `forge-game/src/test/java/forge/game/staticability/StaticAbilityLayersTest.java`
- Create or modify: `forge-game/src/test/java/forge/game/trigger/HearthstoneEmblemTest.java`

- [ ] 给农夫契约加入 `TriggerZones$ Battlefield` 断言并运行，确认因当前脚本缺失而失败。
- [ ] 构造同时含 `AddKeyword` 与 `SetMaxHandSize` 的静态能力，断言层集合含 `ABILITIES` 和 `RULES`，确认当前仅有前者。
- [ ] 构造第一位牌手的第一次维持，断言徽记计数与法术力为 1；继续验证第二、第三次为 2、3。
- [ ] 在法力测试中推进步骤，断言法力跨步骤保留；推进到回合结束清理后断言清零。

### Task 2: 实施最小修复

**Files:**
- Modify: `custom/cards/blue/农夫.txt`
- Modify: `forge-game/src/main/java/forge/game/staticability/StaticAbility.java`
- Modify only if the failing runtime test proves necessary: `custom/cards/colorless/炉石传说.txt`

- [ ] 给农夫触发器加入 `TriggerZones$ Battlefield`。
- [ ] 将 `SetMaxHandSize`、`RaiseMaxHandSize` 加入静态能力 `RULES` 层判定。
- [ ] 若徽记测试证明 `Amount$ X` 未继承，改为由徽记自身可解析的现有 Forge 计数表达式；保留 `PersistentMana$ True`。

### Task 3: 验证、构建与部署

**Files:**
- Modify: `custom/VERIFICATION.md`
- Deploy: `%APPDATA%/Forge/custom`
- Build/deploy: `forge-gui-desktop/target/forge-gui-desktop-2.0.14-SNAPSHOT-jar-with-dependencies.jar`

- [ ] 运行目标 Python 测试与两张卡的 lint。
- [ ] 运行目标 Java 测试及相关回归测试。
- [ ] 运行 DIY 全量测试。
- [ ] 构建桌面聚合 JAR，核对产物时间与相关类字节码。
- [ ] 同步 DIY 内容与客户端 JAR。
- [ ] 重启 Forge 后实际验证最大手牌为 10、先手第一次维持得到 1 点持久法力、农夫在手牌不触发。
- [ ] 将本次实际命令与结果写入 `custom/VERIFICATION.md`。
