# 无障碍链路回归清单

> 用途：每次动到「无障碍服务 / 浮层窗口 / 设置回流 / 自愈」中的任何一处，按本表逐条过一遍。
> 背景：ColorOS 等 ROM 上最常见的故障不是"功能坏了"，而是**系统把无障碍连接摘掉但 App 不知道**
> （`instance` 还在、旧窗口还在，但 `performGlobalAction` 恒 false、新建无障碍窗口恒 `token null`）。
> 本清单把"能发现 / 能恢复 / 不误伤"分开验。

## 0. 前置

- 装 debug 包（`app-full-debug.apk`）。
- 抓日志：`adb logcat -v threadtime > field.log`
- 一键诊断入口：App 内「设置 → 关于/诊断 → 复制诊断报告」，确认新增的「无障碍链路」小节有数据。

## 1. 安装 / 覆盖安装

| 步骤 | 期望 |
| --- | --- |
| `adb install -r` 覆盖安装 | 进程重启后 10s 内：触钮、悬浮球、角轮盘全部出现，且**不需要用户改任何设置** |
| 同上 | 日志出现 `settings emission` / `applySettings serviceEnabled=true`，随后 `heal nothing-missing` |
| 同上 | 不应出现大量 `heal SKIP serviceEnabled=false`（说明设置没应用上） |

## 2. 强停后重新打开

| 步骤 | 期望 |
| --- | --- |
| 设置里强行停止 App，再从桌面打开 | 无障碍若被系统标记为"出现故障"，应出现可点击通知「无障碍已断开，点此重新开启」；点击直达无障碍设置页 |
| 手动关/开一次无障碍 | 3s 内球/触钮/轮盘全部回来，**且不出现左右各两组触钮窗口** |

## 3. 连接被摘掉（核心场景）

| 步骤 | 期望 |
| --- | --- |
| 用 `adb shell am force-stop` 之外的任何方式让服务连接失效（或等待 ROM 自己摘） | 15~30s 内日志出现 `AccessibilityLink: accessibility link broken` |
| 同上 | 随后出现 `accessibility link broken ... → teardown + rebind`，浮层被拆（避免僵尸窗口吞触摸） |
| 有 Shizuku / `WRITE_SECURE_SETTINGS` | 自动改写条目重绑，成功后 `recover` → 浮层自动回来 |
| 无上述权限 | 只发通知，不反复重试（60s 节流） |

## 4. 功能面（每条都要同时验"正常"与"掉线时"）

| 功能 | 正常 | 掉线时 |
| --- | --- | --- |
| 触钮滑动 | 手势动画出现、松手执行动作 | 不静默：日志有 `performGlobalAction(...)` 失败证据 + 通知 |
| 悬浮球手势返回 / Home / 最近任务 | 立即生效 | 同上 |
| 快速启动器（圆环 / 角轮盘槽位 / 触钮） | 面板弹出、加号可点 | 建窗失败会记账，不出现"点了没反应却没日志" |
| 角轮盘 | 四角窗口挂上、可按 | 同上 |
| 取词悬停红框 / 区域截图结果面板 | 正常显示 | 同上 |

## 5. 长时间与极端条件

| 条件 | 期望 |
| --- | --- |
| 后台 24h（不充电、不打开 App） | 记录掉线次数；要么自己恢复，要么有通知（不能静默） |
| 锁屏 / 解锁、切换横竖屏 | 不误判掉线（探活需连续两次空窗口才判） |
| 低内存（开大量应用） | 进程被杀后，重新打开 App 能恢复；诊断报告里能看到 `brokenCount/recoveryAttempts` |
| 无 Shizuku、纯手动开启 | 全程不崩、不刷屏，通知可点、能直达设置 |

## 6. 诊断报告必看字段

- **链路状态**：可用 / 不可用（原因）—— 不可用时看原因是不是 `window-add:*` 或 `performGlobalAction(*)`。
- **掉线次数 / 恢复尝试 / 最近结果**：次数多但成功率高 = 自愈在工作；次数多且失败 = 需要用户介入。
- **设置应用次数**：启动后应为 1 次以上；长期为 0 说明设置回流又丢了（触钮会缺席）。
- **触钮自愈巡检/跳过**：跳过数远大于命中数时，检查 `serviceEnabled / suppressed / trampolineGuard` 哪个条件长期成立。

## 7. 提交前自检

- `.\gradlew.bat :app:compileFullDebugKotlin --offline` 通过。
- 改动文件 `python scripts/normalize-eol.py --check <files>` 全 LF。
- `git diff --numstat` 无整文件改写。
- 本清单 1–4 至少各过一条，并把结果记在 PR 描述里。
