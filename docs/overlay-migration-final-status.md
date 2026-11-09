# overlay 迁移与工程治理：最终状态与修订计划

> 来源：本次会话从「资深 Android 评审报告 → 第 1 周止血 → W1 结构重构（overlay 迁移）→ W3 工程卫生」的完整执行记录。
> 本文替代此前计划中已经过时或被证伪的判断；与 `overlay-panels-migration-plan.md`（施工细节）互补。
> 生成时间：2026-11-09。基线提交：`2d85c1b2`。

---

## 0. 一句话结论

**面板迁移（原 W1 阶段 B）已完成，`FloatBallOverlay` 按职责拆成 10 个类；阶段 A/C 的剩余部分与 P1-1 指标经实测判定「不值得继续」，建议锁定当前状态、发版前重采 profile、推送后收尾。**

---

## 1. 关键数字

| 指标 | 起点 | 现在 | 变化 |
| :--- | ---: | ---: | ---: |
| `:app/overlay` 行数 | 136,402 | **50,675** | **−63%** |
| `:app/overlay` 根目录文件 | 220 | **129** | −91 |
| `:app` 总行数 | ≈286,000 | **241,964** | −44,000 |
| `:feature:overlay-panels` | 0 | **90,799** | 新增模块 |
| `FloatBallOverlay` | 4,302 | **3,313** | −23% |
| 跳板 Activity | 33 | **30** | −3 |
| 面板组迁出 | 0 | **19 组** | 组目录清零 |
| 本地未推提交 | 0 | **171** | ⚠️ |

---

## 2. 原计划逐条对账

### 第 1 周（止血）

| 条目 | 状态 | 证据 |
| :--- | :--- | :--- |
| CI 加 test job | ✅ | `.github/workflows/ci.yml` 的 `unit-tests` job |
| 修红测试（实际 12 条） | ✅ | `unitTests` 全量绿 |
| 合并两个 `lint {}`、恢复 `checkReleaseBuilds = true` | ✅ | 根 `build.gradle.kts` |
| baselineprofile / macrobenchmark 二选一 | ✅ 选接线 | `:baselineprofile` 在；`macrobenchmark` 已删 |

### 第 2–4 周（骨架）

| 条目 | 状态 | 说明 |
| :--- | :--- | :--- |
| 抽 `:core:overlay-host` | ✅ | 3.8k 行；另有 `:core:design` 4.8k |
| 设置仓库单快照 + 切片派生（P1-5） | ✅（此前误报为未做） | `SettingsPreferencesEditor.snapshotFlow` 已 shareIn 单一解析，切片全部派生 |
| 启动 `runBlocking` → 同步镜像（P1-7） | 🟡 部分 | 语言读取已改同步镜像；其余启动读取仍在 IO |
| 签名校验 + `update.json` sha256（P1-8） | ⏸ 按计划暂缓 | 未动 |
| Roborazzi 截图基线（P1-6） | ⏸ 按计划推迟 | 未动 |
| 更新弹窗精简版说明 | ⏸ 按计划取消 | 未动 |

### W1 结构重构

| 阶段 | 状态 | 实测 |
| :--- | :--- | :--- |
| **A 拆底座** | 🟡 约 70% | 主题层 ✅、宿主 ✅、`overlayHostContext` 去 DI ✅；根包单例只拆了 `FloatBallOverlay`（10 类） |
| **B 搬面板组** | ✅ 完成 | 19 组全部迁出；`filebrowser` 提前完成 |
| **C 收根目录** | 🟡 约 5% | 已搬 `CNotice` 簇 6 文件 / 2,551 行；根目录仍 129 文件 / 50,675 行 |
| P1-1（`:app` ≤ 20 万行） | ❌ 未达成 | 241,964 行（差 4.2 万） |
| P1-2（`FloatBallOverlay` 拆 4 类） | ✅ 超额 | 拆出 **10 类** |
| P1-3（85 个静态入口收敛） | 🟡 部分 | 面板侧已端口化；根包单例静态入口未收敛 |

### W2 性能

| 条目 | 状态 |
| :--- | :--- |
| 生成 app 级 Baseline Profile | 🟡 工程接线完成（Generator + StartupBenchmark），**项目 profile 产物未见提交** |
| 启动路径改动后重采 | ❌ 未做（发版前必做） |
| CI 跑 StartupBenchmark | ✅ 按计划不做 |

### W3 工程卫生

| 条目 | 状态 | 实测 |
| :--- | :--- | :--- |
| W3-1 trampoline 合并 | 🟡 部分（33 → 30），**主动停止** | 见 §5.1 |
| W3-2 设置 schema 版本 + 迁移表 | ✅ **完成** | `SettingsSchemaMigrations` + `settings_schema_version` + 4 条单测 |

---

## 3. A3 成果：`FloatBallOverlay` 拆出的 10 个类

| 类 | 行数 | 职责 |
| :--- | ---: | :--- |
| `FloatBallCaptureChrome` | 104 | 截图/取词/启动器期间的 chrome 抑制与恢复 |
| `FloatBallPositionPreview` | 85 | 位置预览与回滚 |
| `FloatBallAppearancePreview` | 126 | 大小/透明度/线条预览与回滚 |
| `FloatBallLifecycle` | 46 | 语言切换重建、布局重算、IME 变化 |
| `FloatBallDismiss` | 90 | 销毁路径（窗口/触摸层/空闲层/scope） |
| `FloatBallShow` | 120 | 创建/更新入口（含重建确认） |
| `FloatBallIdleVisuals` | 155 | 空闲显隐与 alpha 动画 |
| `FloatBallTouchWindowLayout` | 171 | 扩窗/收缩/穿透 |
| `FloatBallDragChromeLayout` | 100 | 拖拽单帧布局提交 |
| `FloatBallDragFinish` | 320 | 拖拽收尾/松手取词/贴边 |

**剩余 3,313 行 = 交互状态机**（`handleForwardedTouch` 主循环 + 取词预览），与 `dragSession` / `cursorPreview*` / `regionalPick*` / `selection*` 十余个状态互相咬合。
拆分前提是先把状态收进 `FloatBallInteractionState` 对象——属设计重构，非文件搬迁。

---

## 4. 我此前判断的更正（重要）

| 原判断 | 实测结论 | 影响 |
| :--- | :--- | :--- |
| 「39 个 trampoline 拖慢冷启动」 | **错误**。跳板不参与启动，只在触发时实例化 | W3-1 收益≈0，已停止 |
| 「阶段 C 收完根目录即可达成 P1-1」 | **不成立**。根目录相互引用密度极高（`FloatBallOverlay` 被引用 267 次、`FloatBallLayout` 93、`OverlayManager` 53），单文件搬走平均连锁 2–5 个未定义符号 | 阶段 C 需先做宿主接口化，成本 > 收益 |
| 「P1-1（`:app` ≤20 万行）值得追求」 | **价值低**。为指标搬宿主单例属"为重构而重构" | 建议放弃该指标 |
| 「拆出的类可立即补大量单测」 | **多数不可测**。10 个类里 9 个依赖单例状态/Looper | 单测收益取决于状态对象化 |
| 「设置仓库单快照未做」 | **误报**，`snapshotFlow` 已实现 | P1-5 应标记完成 |

---

## 5. 主动停止的两件事与理由

### 5.1 W3-1（trampoline 合并）

- 已完成：`Assist` / `PinNotificationRestore` / `WallpaperPermission` 三个并入 `LaunchTrampolineActivity`（按 extra 分派）
- 剩余 30 个分三类：
  | 类别 | 数量 | 处理 |
  | :--- | ---: | :--- |
  | 外部调用契约（adb/Tasker，`ExternalInvocationCatalog`） | 6 | **不能删**（公开组件名） |
  | 需要 `startActivityForResult` 的选图/分享 | ~12 | 只能共基类，不能合并 |
  | 依赖 Hilt 的业务型 | ~8 | 可并，但需先给 `LaunchTrampolineActivity` 加 Hilt |
- **停止理由**：收益≈0、风险集中在只有真机能验证的路径（通知恢复/助手拉起/壁纸权限）
- **替代方案（若要继续）**：加一个 `BaseTrampolineActivity` 统一 finish/无动画/noHistory，**不删类**——约 0.5 天，零契约风险

### 5.2 阶段 C（收根目录 129 个文件）

- 已试 6 次批量搬迁，成功 2 次（`CNotice` 簇、`CNoticeDetail`），失败 4 次全部回退
- 结论：**宿主单例接口化是前置条件**（`FloatBallPort` / `OverlayPort` / `RegionalPickPort`），属独立项目
- 建议：不做

---

## 6. 遗留风险

| 风险 | 说明 | 建议 |
| :--- | :--- | :--- |
| **171 个提交仅本地** | 从 `2d85c1b2` 累计至今，未 push | ⚠️ 最高优先级：完整验证后推送 |
| 3 个跳板合并未真机验证 | 助手拉起 / 通知恢复钉图 / 壁纸权限跳转 | 发版前各验一次；出问题 `git revert` 单个提交 |
| Baseline Profile 产物缺失 | profile 记录的是旧启动路径 | 发版前重采一次并提交产物 |

---

## 7. 修订后的优先级

| 优先级 | 事项 | 工作量 | 说明 |
| :--- | :--- | :--- | :--- |
| **P0** | 完整验证（`assembleFullDebug` + `unitTests` + 关键 lint）→ 推送 171 个提交 | 1–2 小时 | 消除最大风险 |
| **P1** | 发版前重采 Baseline Profile 并提交产物 | ~40 分钟 | profile 已过期 |
| **P1** | 真机验证 3 个合并的跳板入口 | ~20 分钟 | 见 §6 |
| **P2（可选）** | `BaseTrampolineActivity` 统一跳板收尾行为（不删类） | ~0.5 天 | 零契约风险的可维护性收益 |
| **不做** | 阶段 C 剩余 5 万行 | — | 收益 < 成本 |
| **不做** | P1-1 的 20 万行指标 | — | 指标导向，非价值导向 |
| **不做** | `FloatBallInteractionState` 状态对象化 | — | 除非将来要给 overlay 写大量单测 |
| **不做** | W3-1 继续合并剩余跳板 | — | 收益≈0，契约风险高 |

---

## 8. 交付物索引

- 模块：`:core:design`、`:core:overlay-host`、`:core:shortcuts`、`:core:clipboard`、`:core:stash`、`:core:search`、`:core:translate`、`:feature:overlay-panels`（90,799 行 / 342 文件）
- 施工细节：`docs/overlay-panels-migration-plan.md`（§5 完成表、§12.1–12.15 历史与结论）
- 原始评审：`docs/code-review-2026-11-06.md`（W1/W2/W3 定义）
- 本文：最终状态 + 修订计划

---

## 9. 一句话给未来的自己

**模块边界应该按"变化频率 × 复用度"划，不该按行数指标划。**
这次真正带来价值的不是"搬走 8.6 万行"，而是 `FloatBallOverlay` 从 4.3k 行单例拆成 10 个能单独读懂、单独修改的类。
