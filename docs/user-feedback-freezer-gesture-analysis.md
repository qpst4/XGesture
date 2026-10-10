# 用户反馈分析：重冻手势语义 / 熄屏自动触发 / 杀应用手势

> 只做分析，不含代码改动。证据标注 `路径:行号`。分析时工作区干净。
> 反馈：① 重冻手势把该暂停的应用冻结了；② 希望熄屏/锁屏自动冻结与暂停；③ 希望有杀应用手势。

## 0. 结论

| 反馈 | 结论 | 处理 |
| :- | :- | :- |
| ① | **成立**。重冻手势写死调 `freezeAll`，不读冰箱「工作模式」 | 定案 A：手势跟随工作模式（§2） |
| ② | **现状为零**。Shizuku 模式下只能挂在常驻进程上，冷启动执行做不到 | 需先定 4 个参数再动手（§3） |
| ③ | **动作已有**，只是名字不叫「杀应用」，搜索别名也能搜到 | 补别名 + 回复入口位置 |

---

## 1. 定位

- 「打开冻结+暂停合一窗口」= 手势「冰箱面板」`FREEZER_PANEL`（`GestureAction.kt:834` → `ActionExecutor.kt:448-452`）。
- 「冻结应用」= 手势「重冻应用」`REFREEZE`（`GestureAction.kt:839` → `ActionExecutor.kt:453-458`）。它是唯一的冰箱批量动作（`GestureActionCatalog.kt:188`、`GestureSessionActionDispatch.kt:452`）。

## 2. 第 ① 条

### 成因

`FreezerOperations.kt:182-205`：`refreezeAll()` = `freezeAll()`，只处理 `stateOf(pkg).isActive` 的成员，走 `setFrozen()`（停用 + force-stop，`FreezerPrivilegedOps.kt:64-79`），**没有 isPause 分支**。工作模式只被面板悬浮按钮消费（`FreezerPanelContent.kt:307-327`）。

两个容易被搞错的点：

- 它冻结的不是「打开过的应用」，而是**冰箱列表里所有 `enabled` 的成员**（状态来自系统标志，`FreezerPrivilegedOps.kt:59-62`，不查使用记录）。点开应用时 `launchAndRestore` 已把它恢复成 ACTIVE（`FreezerOperations.kt:128-147`），所以感觉像「冻结刚用过的」。
- 暂停意图没有地方存：只有包名集合，无成员级目标状态（`AppSettingsSlices.kt:94-97`），所以批量只能按当前状态猜。
- 冻结与暂停强制互斥（`FreezerOperations.kt:51-55`、`:97-100`），护栏本身是对的（disable 会让图标消失），副作用是暂停态被冻结覆盖。

### 修法（已定 A）

`ActionExecutor.kt:453-458` 改为读 `settings.freezerWorkMode`：暂停模式走 `pauseAll`（已存在，语义正确，`FreezerOperations.kt:207-226`），否则走 `refreezeAll`。Toast 复数文案两条都已有，无需新增字符串。

一并要做的：改 `gesture_action_refreeze_desc`（现文案「冻结冰箱列表中已启用的应用」会继续误导，`values-zh/strings.xml:1042-1043`）；CHANGELOG Changed 里写明语义变更。

未采纳：B 成员级意图记忆（波及存储/picker/管理页）、C 拆两个动作（同功能两入口，选择成本转给用户）、D 面向「正在运行的应用」（无法可靠枚举，误杀面大，且单应用已有现成动作覆盖）。

## 3. 第 ② 条

### 现状（已核实）

无逻辑（grep `autoFreeze|autoPause` 0 命中）、无设置 key（`SettingsPreferenceKeys.kt:423-426`）、无 trigger（冰箱没有自己的 receiver）、无调度层（`AndroidManifest.xml:665-674` 移除了 WorkManager 初始化）。

### 硬边界：为什么 Shizuku 模式下自动执行不灵

冻结/暂停本身是系统持久状态，系统执行拦截不需要我们的进程活着；**难的是「在某一时刻触发」**。Android 12+ 后台广播不能拉起前台服务，进程已死就没人能执行特权操作；固定时间靠 AlarmManager 也受 Doze 与进程冻结影响。

- Hail / Essentials 的 Shizuku 通道本质依赖 ADB 连接，进程或 Shizuku 一断就没有任何一端能替它执行；它们能可靠自动化的形态是 Device Owner。
- 本仓库的 Shizuku UserService 绑在**本应用进程内**（`shizuku/ShizukuUserServiceHost.kt`），进程一死 binder 立即失效，需重新绑定 → **冷启动执行做不到**。

| 通道 | 能否自动执行 | 前提 |
| :- | :- | :- |
| Shizuku | 仅熄屏那刻进程活着时 | 无障碍/前台服务常驻（`service/OverlayService.kt` 已是 specialUse）+ 电池白名单（`util/PermissionHelper.kt:45-50`） |
| Root | 可行（守护独立进程） | root 常驻 |
| Xposed | 唯一能脱离进程 | 需模块启用；属跨进程重构，列为后置可选项 |

### 第一版建议形态

1. 挂点复用 `SlideIndexAccessibilityWatchdog.kt:25-71`（已收 SCREEN_OFF/ON/USER_PRESENT），不新开裸 receiver。
2. 熄屏后**延迟 10–30 s** 执行，执行前复查 `isInteractive`，亮屏整批取消。
3. **冻结/暂停分成两个独立开关**（暂停门槛低，纯 Shizuku 可用；冻结需 root）。
4. 豁免：媒体播放、导航、通话、录音、下载、前台应用。暂停路径会先 force-stop 一次（`FreezerPrivilegedOps.kt:145-152`），放歌时执行等于直接断电。
5. pending 落盘 + 与面板手动操作互斥（避免状态漂移）。
6. 留痕：失败发通知（Toast 在熄屏时等于没提示），binder 重连或下次亮屏补跑。
7. 可用性门禁：常驻条件不满足时开关置灰并写明原因。

必测失败模式：熄屏 3 秒又亮屏（应整批取消）、放歌时熄屏（应跳过）、进程刚被杀过（应留痕不动作）、执行到一半被杀（pending 续跑/回滚）、ADB 被拔（停止 + 明示）。

## 4. 第 ③ 条

已有：「结束当前应用」（移除最近任务卡，`ActionExecutorLaunch.kt:133-136`）、「强行停止当前应用」（真杀进程，`:138-142`）。选择器搜索别名已含「强行停止 / 杀掉应用 / 结束进程」（`values-zh/strings.xml:3946-3948`），需 Shizuku/root 授权。

没有：「一键杀全部后台」（`killBackgroundProcesses` 只在权限清单 `PermissionGranterHook.kt:90` 出现过）。独立需求，误杀与保活重建风险不小 —— 建议先补口语别名（「杀进程」「清理后台」）并回复用户入口位置。

## 5. 待确认

1. 自动动作范围：冰箱全体启用成员，还是另建专用子集？
2. 是否做亮屏自动恢复（离线暂停式 vs 单向用完即冻）？
3. 延迟档位：固定还是可配（立即 / 30s / 2min / 5min）？
4. 媒体豁免判定口径：`AudioManager` 活跃播放、媒体通知、还是用户白名单？（倾向两者叠加）
5. Shizuku 且无 root 时，冻结开关是否直接置灰？
6. 第 ① 条是否连动作名一起改（「重冻应用」→ 更中性的名字）？

## 6. 落地状态

①**已实现**（方案 B：成员级意图记忆，取代本文前面评估的方案 A）。实现要点：

- 意图表：`FeatureSettings` 新增 `FREEZER_APP_INTENTS` key + `FreezerAppIntent`（FROZEN / PAUSE）+ `FreezerAppIntentCodec`（按行编解码，容错空值/坏行），落到 `freezerAppIntents: Map<包名, 意图>`。
- 写意图：`setFrozen(true)` 记 FROZEN；`setPaused(true)` 记 PAUSE；**取消暂停 / 解冻不改意图**（用户要的还是暂停，只是临时放出来用）。只对列表内成员记录。
- 关键一环：`launchAndRestore` 在把状态清成启用**之前**，先把「它原本是冻结还是暂停」补进意图表——系统那边状态一清就再也看不出原档位了。
- 批量统一入口：`FreezerOperations.restoreIntents` 按 `FreezerIntentResolution.decide`（纯函数，可单测）逐包决策：有记录按记录、无记录按工作模式兜底、已是目标态则跳过。**面板底部按钮、「重冻应用」手势、面板「按档位收回全部」三项共用它**（原 `refreezeAll` / `freezeAll` 已删除，避免再出现「按钮一律冻结」这条歧路）；「全部暂停 / 全部取消暂停 / 全部解冻」保留为显式动词，不改语义。
- 工作模式定位随之改变：从「底部按钮执行哪种动作」变为「无记录成员的兜底档位」，设置项描述已同步改写。
- 导入：`FreezerBootstrap.intentForState` 按导入时的状态补记记录；成员被移出列表时记录一并删除。「全部取消暂停」「全部解冻」**都不动记录**——它们是让成员现在能跑，用户给的档位仍然有效，下次按档位收回要把原状态还回去（最初实现里「全部取消暂停」顺手清了记录，导致这些成员退回兜底档位、被一律冻结，已修）。
- 可观测性：`FreezerOperations` 打点（意图写入、`setFrozen/setPaused -> ok|failed`、逐包 `state/intent -> decision`、收尾 `changed=N`）；成员全在目标态时给一句「无需处理」提示，不再静默。
- 文案四语言：改写 `gesture_action_refreeze_desc`、`freezer_work_mode_pause_desc`，新增 `freezer_restore_intents`、`freezer_restore_intents_done`、`freezer_restore_intents_noop`。
- 验证：`:app:compileFullDebugKotlin` 通过；新增 `FreezerIntentResolutionTest`(6)、`FreezerAppIntentCodecTest`(3)、`FreezerBootstrapTest` 补 1 例、`SettingsMutatorsTest` 补 2 例，相关单测全绿。
- 真机实测（MEIZU 21 / Android 16）：手势一次把两个活跃成员冻结、已暂停成员按记录保持暂停；随后发现「成员被冻结后记录被改写为冻结」的现象，日志缓冲已滚掉无法回溯，因此补了上面的打点以便下次直接定位。

② 未实现（本文只做可行性分析）；③ 无需新增动作。
