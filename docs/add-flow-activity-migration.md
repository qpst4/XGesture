# 「添加条目」Activity 化：现状、约定与四入口建议

> 2026-11-12。记录一次实际改造：四个启动器的「添加条目」统一成**透明 Activity 的整页**，
> 浮层只作兜底；以及小组件面板闪退修复。相关提交：`ea864008`（闪退）、
> `527bd77a`（小组件面板/快速启动器）、`f303c7da`（圆环）、后续「整页 + 挂起」两笔。

## 1. 为什么不再用浮层

| 原因 | 说明 |
| :- | :- |
| z-order | `TYPE_APPLICATION_OVERLAY`（2038）永远在 Activity 之上；浮层里“再弹一层”只能画在同一个 overlay 窗口里 |
| 触摸管线 | 浮层里的 ComposeView + LazyColumn 滚动不可靠（`QuickLauncherAddTrampoline` 的原始注释即为此） |
| 输入/返回栈 | 搜索、输入法、返回键、任务栈在普通 Activity 里都是系统默认行为，不用自己补 |
| 生命周期 | 浮层随手势会话生灭；Activity 有自己的返回栈与 `onSaveInstanceState` |

代价：Activity 打开期间宿主浮层必须让位（否则盖住页面），关闭后再恢复 —— 见 §3。

## 2. 四入口现状（改造后）

| 入口 | 宿主页面（整页） | 让位/恢复 | 兜底 |
| :- | :- | :- | :- |
| 小组件面板 | `WidgetPickerTrampolineActivity`（`WidgetPickerScreen`） | `WidgetPopupOverlayWindow.setWidgetAddFlowActive(true/false)`（hide/resume） | `WidgetPickerOverlayWindow` |
| 快速启动器 | `QuickLauncherAddTrampolineActivity` + `QuickLauncherAddFlowScreen`（**与设置入口同一套页面**：`QuickLauncherAddPickerScreen` / `QuickLauncherCreateFolderScreen` / 快捷键、应用、Shell、链接、按键子页，持久化走 `ExtensionSettingsViewModel`） | `QuickLauncherOverlayController`：进页前 `dismissFromBack()` 正常结束会话并记下页索引；Activity 关闭后按原面板/钉住态/锚点重开，重开派发的会话恢复该页。进页期间不抢焦点，触钮/悬浮球按普通手势工作 | 同一个浮层 sheet（不参与整页；Activity 起不来时回退） |
| 圆环启动器 | `RingLauncherSlotConfigActivity` + `RingLauncherSlotConfigSheet(pageMode = true)`，页面壳用 `MiuixContentScaffold`（顶栏/毛玻璃/insets 与设置页一致，内容区不强制 LazyColumn）；自定义图标内联子页 | `RingLauncherOverlayWindow.suspendForSlotIconEditor()` / `resumeAfterSlotIconEditor()`（+ 前台自愈） | 原浮层 sheet（`pageMode = false`） |
| 边角轮盘 | `CornerGestureSlotPickTrampolineActivity`（`CornerGestureSlotEditorHost`） | `CornerGestureController.suspendOverlayForExternalActivity()` / `resumeAfterSlotPicker()`（+ 前台自愈） | 无（改造前就是 Activity） |

共同点：

- 都是 `Theme.SlideIndex.Transparent` + `taskAffinity=""` + `excludeFromRecents` 的透明壳；
- 整页 UI 与设置页共用同一套 Miuix 页面（`SettingsLazyScreenScaffold` / `MiuixContentScaffold` +
  `Card`/`SmallTitle` + `MiuixTheme` 色彩/字号），不再各自手写 M3 `Surface` 顶栏；
- 启动成功与否都有明确结果：失败回退浮层，成功则在退出时恢复宿主面板。

## 3. 让位/恢复的两种实现（不要混用）

| 宿主 | 做法 | 为什么 |
| :- | :- | :- |
| 圆环 / 边角（独立浮层窗） | 窗口 `visibility=GONE` + `FLAG_NOT_TOUCHABLE`，退出时恢复 | 窗口只画自己，隐藏很干净 |
| 快速启动器（与触钮共享 edge overlay 窗） | **不挂起**：进页前 `dismissFromBack()` 结束会话并记下页索引，Activity 关闭后 dispatch 重开并恢复该页；进页后不再 `setPanelPresentationFocus(true)` | 挂起（保留会话）会让 presentation 窗/触钮/悬浮球以为自己还“开着”：新起的手势被全局压制，且浮层抢走 IME 焦点，搜索框弹不出键盘（真机 dumpsys 已证实）。结束会话最干净 |
| 小组件面板（独立浮层窗） | `root.visibility = GONE` + 不可触摸，退出时恢复 | 同上 |

快速启动器这条的教训：不要用“保留会话 + 全局挂起旗标”。挂起期间
`EdgeGestureLayoutCoordinator.needsPresentationDirectTouch()` 会被迫返回 false 来让出触摸，
但同一个旗标也会把触钮/悬浮球新起的手势一起压掉；而只要浮层窗还挂着，
`setPanelPresentationFocus(true)` 就会把 IME 焦点从 Activity 抢走（搜索框弹不出键盘）。
现在的做法是**结束会话 + 重开恢复页索引**，`OverlayTrampolineGuard` 也不再把快速启动器加进去。

## 4. 新增一个入口时必须做的四件事

1. **透明 Activity 壳**：`ComponentActivity` + `enableEdgeToEdge(auto)` + 依赖/主题包裹
   （`CompositionLocalProvider(LocalAppDependencies provides deps)` + `ModuleTheme(settings)`）。
2. **让宿主浮层让位**：优先复用各宿主已有的 suspend/hide 能力；如果选择
   `dismiss()`/`endSession()`（overlay 状态最干净），**必须**在退出时重开并恢复页索引/
   钉住态/锚点，否则会丢页索引。不要用“保留会话 + 全局挂起旗标”——它会同时压掉
   触钮/悬浮球新起的手势，还会让浮层抢走 Activity 的 IME 焦点。
3. **结果一次性交付**：`finishPicker()` 用 `finished/delivered` 布尔守卫；`onDestroy` 兜底
   （没交付就补取消/恢复），否则面板会卡在隐藏态。
4. **失败回退**：`startActivity` 用 `runCatching`；失败时回到浮层实现或至少恢复面板，
   不要“点了没反应”。

## 5. 四入口要不要合成一个 Activity？

建议：**不要合并成一个**，保留四个薄壳，共用页面与行为约定。

理由：

- 启动参数与结果语义不同：小组件面板要“页索引 + 继续去绑定系统小组件”；快速启动器要
  “多选增删 + 返回后原样恢复”；圆环/边角要“槽位轴 + 索引 + 让位/恢复”；
- 合并只能省 3 个 manifest 条目，却把所有分支塞进一个类；
- 真正值得统一的是 §4 的四件事和共用 Composable，这部分已经统一。

## 6. 已知边界

- **后台启动 Activity**：Android 10+ 与 OEM 策略可能拦截；现有代码已用 `NEW_TASK` + 透明主题，
  并在失败时回退浮层。
- **首帧主题**：Activity 初始 settings 用 `readSnapshot()` 同步读，避免先画默认主题再跳。
- **进程死亡**：结果回调是进程内对象，进程被杀后无法恢复。
- **添加页留在后台**：快速启动器 Activity 未 destroy 时，其挂起态会一直保持（
  `QuickLauncherAddTrampoline.isActive()` 也仍然挡住浮层恢复）；目前靠 Activity 的 `onDestroy`
  兜底，后续可像圆环/边角那样补前台自愈。
- **圆环图标编辑**已内联进 `RingLauncherSlotConfigActivity` 子页，避免 suspend/resume 嵌套。

## 7. 闪退修复（同一轮）

`ScalableFrameLayout` 的 companion `handler` 被 `View.getHandler()` 遮蔽：Kotlin 把 Java getter
当合成属性且类成员优先，字节码实际调 `this.getHandler()`；视图 detach 后返回 null →
`scheduleOptionsPush` 的 `removeCallbacks/post` NPE（滑到第 2 页长按必现，因为进编辑态会
`notifyItemRangeChanged` 重绑所有页，旧页 canvas 已 detach）。

修复：companion 改名 `optionsPushHandler`；`scheduleOptionsPush` 加 attached 守卫；
`onDetachedFromWindow` 取消待执行任务；`WidgetCanvasLayout` 的排队刷新统一走
`postRefreshAllWidgetLayouts()`（detach 后不跑）。
