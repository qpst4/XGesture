# 「添加条目」Activity 化：现状、约定与四入口建议

> 2026-11-12。本文记录一次实际改造：把四个启动器的「添加条目」浮层统一成
> **透明 Activity 页面**（浮层只作兜底），以及闪退修复的根因。
> 相关提交：`ea864008`（闪退）、`527bd77a`（小组件面板/快速启动器）、`f303c7da`（圆环）。

## 1. 为什么不再用浮层

| 原因 | 说明 |
| :- | :- |
| z-order | `TYPE_APPLICATION_OVERLAY`（2038）永远在 Activity 之上；浮层里“再弹一层”只能画在同一个 overlay 窗口里 |
| 触摸管线 | 浮层里的 ComposeView + LazyColumn 滚动不可靠（`QuickLauncherAddTrampoline` 的原始注释即为此） |
| 输入/返回栈 | 搜索、输入法、返回键、任务栈在普通 Activity 里都是系统默认行为，不用自己补 |
| 生命周期 | 浮层随手势会话生灭；Activity 有自己的返回栈与 `onSaveInstanceState` |

代价：Activity 打开期间宿主浮层必须让位（否则盖住页面），关闭后再恢复 —— 见 §3。

## 2. 四入口现状（改造后）

| 入口 | 宿主页面 | 让位/恢复 | 兜底 |
| :- | :- | :- | :- |
| 小组件面板 | `WidgetPickerTrampolineActivity`（`WidgetPickerScreen`） | `WidgetPopupOverlayWindow.setWidgetAddFlowActive(true/false)`（hide/resume） | `WidgetPickerOverlayWindow` |
| 快速启动器 | `QuickLauncherAddTrampolineActivity`（`QuickLauncherAddOverlaySheet`） | 关闭面板（`dismissFromBack`）→ 结束后 `dispatchExternalGestureAction(QuickLauncher(panelId, pinned, pinCurrentApp))` 重开，失败重试 2 次 | 同一个 sheet 在浮层里弹（`showAddSheet`） |
| 圆环启动器 | `RingLauncherSlotConfigActivity`（整页 `RingLauncherSlotConfigSheet`，自定义图标内联子页） | `RingLauncherOverlayWindow.suspendForSlotIconEditor()` / `resumeAfterSlotIconEditor()`（+ 前台自愈） | 原来的浮层 sheet |
| 边角轮盘 | `CornerGestureSlotPickTrampolineActivity`（`CornerGestureSlotEditorHost`） | `CornerGestureController.suspendOverlayForExternalActivity()` / `resumeAfterSlotPicker()`（+ 前台自愈） | 无（改造前就是 Activity） |

共同点：

- 都是 `Theme.SlideIndex.Transparent` + `taskAffinity=""` + `excludeFromRecents` 的透明壳；
- 页面本身与浮层共用同一套 Composable（`WidgetPickerScreen` / `QuickLauncherAddOverlaySheet` / `RingLauncherSlotConfigSheet`），不维护两份 UI；
- 启动成功与否都要有明确结果：失败回退浮层，成功则在退出时恢复宿主面板。

## 3. 新增一个入口时必须做的四件事

1. **透明 Activity 壳**：`ComponentActivity` + `enableEdgeToEdge(transparent)` + 依赖/主题包裹
   （`CompositionLocalProvider(LocalAppDependencies provides deps)` + `ModuleTheme(settings)`）。
2. **让宿主浮层让位**：复用各宿主已有的 suspend/hide 能力，不要直接 `dismiss()` 一了百了
   （会丢面板位置/钉住状态）。
3. **结果一次性交付**：`finishPicker()` 用 `finished/delivered` 布尔守卫；`onDestroy` 兜底
   （没交付就补取消/恢复），否则面板会卡在隐藏态。
4. **失败回退**：`startActivity` 用 `runCatching`；失败时回到浮层实现或至少恢复面板，
   不要“点了没反应”。

## 4. 四入口要不要合成一个 Activity？

建议：**不要合并成一个**，保留四个薄壳，共用页面与行为约定。

理由：

- 启动参数与结果语义不同：小组件面板要“页索引 + 继续去绑定系统小组件”（`WidgetBindTrampolineActivity`）；
  快速启动器要“多选增删 + 返回后按原面板重开”；圆环/边角要“槽位轴 + 索引 + 让位/恢复”；
- 合并只能省掉 3 个 manifest 条目，却把所有分支塞进一个类，和现在的“不同实现”风险正好相反；
- 真正值得统一的是**上面 §3 的四件事**和**共用 Composable**，这部分已经统一。

如果要进一步收敛，优先抽一个 `AddFlowActivityBase`（只放：edge-to-edge、`finishOnce()`、
`onDestroy` 兜底），四个子类只保留各自的参数/结果差异；不建议动 widget 的“绑定继续”链路。

## 5. 已知边界

- **后台启动 Activity**：Android 10+ 与 OEM 策略可能拦截；现有代码已用 `NEW_TASK` + 透明主题，
  并在失败时回退浮层。若后续再遇到“点了没反应”，先查这里。
- **首帧主题**：Activity 初始 settings 用 `readSnapshot()` 同步读，避免先画默认主题再跳。
- **进程死亡**：结果回调是进程内对象，进程被杀后无法恢复；面板的 `onDestroy` 兜底会恢复浮层，
  但“添加了一半”的状态不会恢复（与所有 trampoline 一致）。
- **圆环图标编辑**已内联进 `RingLauncherSlotConfigActivity` 子页，避免再起一个 Activity
  造成 suspend/resume 嵌套。

## 6. 闪退修复（同一轮）

`ScalableFrameLayout` 的 companion `handler` 被 `View.getHandler()` 遮蔽：Kotlin 把 Java getter
当合成属性且类成员优先，字节码实际调 `this.getHandler()`；视图 detach 后返回 null →
`scheduleOptionsPush` 的 `removeCallbacks/post` NPE（滑到第 2 页长按必现，因为进编辑态会
`notifyItemRangeChanged` 重绑所有页，旧页 canvas 已 detach）。

修复：companion 改名 `optionsPushHandler`；`scheduleOptionsPush` 加 attached 守卫；
`onDetachedFromWindow` 取消待执行任务；`WidgetCanvasLayout` 的排队刷新统一走
`postRefreshAllWidgetLayouts()`（detach 后不跑）。
