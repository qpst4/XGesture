# `:feature:overlay-panels` 迁移计划（依赖优先版）

> 第二版，2026-11-06。第一版按"文件大小 / 纯度"排序，批 2 撞墙后改为**按依赖深度排序**。
> 每次动手前先读这一页，不要按文件列表顺序做。
>
> **本文档只负责评审报告 §10 里的 W1（结构重构）**。W2（profile 重采）、W3（trampoline、设置 schema）是另外两条独立主线，不在本文范围。
> 本文的阶段 A/B/C 就是把 W1 拆到"每次能独立交付"的粒度。

## 0. 结论先行

- **能搬的顺序不是按组，是按依赖。** 有三根总线（主题层、宿主、根包单例）贯穿所有面板，这三样没拆出去之前，任何面板组都搬不动。
- 三阶段合计 **1.5–2 周**（阶段 A 底座 4–6 天，阶段 B 面板 5–7 天，阶段 C 根目录 3–5 天）。
- **唯一可以随时插队的是 `filebrowser`**：它不接这三根总线。

## 1. 依赖图（为什么必须按依赖排）

```
                    ┌── 主题层：resolveAppMaterialColorScheme / MIUIX 封装
所有面板都插在这三根上 ─┼── 宿主：OverlayComposeOwner（依赖 R + AppLocaleApplier）
                    └── 根包单例：FloatBallOverlay / OverlayComposeDialogHost / ScreenPinManager …
```

实测的耦合（扫 `com.slideindex.app.overlay.<根类>` 引用得到）：

| 组 | 外部根包依赖 | 受影响的总线 |
| :--- | :--- | :--- |
| `screenshot` | `FloatBallPickResult` / `FloatBallPickResultPanel` / `FloatBallTextPick` / `OverlayComposeDialogHost` / `ScreenPinManager` | 单例 |
| `backpanel` | `OverlayScreenMetrics`（✅ 已下移）+ `AndroidBackPanelColors → ui.miuix.theme`（✅ 已解除）+ **`R.dimen.navigation_edge_*` 30+ 个尺寸资源** | 资源 |
| `quickwheel` | `OverlayBlurGate` / `OverlayComposeOwner` / `ShellCommandBadgeOverlay` / `ShortcutBadgeOverlay`（**四项均已下移**） | ✅ 可搬 |
| `animation` | `OverlayComposeOwner`（✅ 已下移） | ✅ 可搬 |
| `volumepanel` | `FloatBallOverlay`（`LocalFrostedGlassBackdrop` / `OverlayViewBackHandler` / `OverlayComposeOwner` ✅ 已下移） | 只剩悬浮球 |
| `fingertip` | `ShellCommandBadgeRenderer`（✅ 已下移） | ✅ 可搬 |
| `share` | `OverlayToastWindow`（`OverlayViewBackHandler` / `OverlayComposeOwner` ✅ 已下移） | 只剩 toast 窗口 |
| `compositor` | 已用 `OverlayZOrderHooks` 反转 | ✅ 已完成 |

## 2. 阶段 A：先拆底座（4–6 天）

> 目标：让"面板组"不再依赖 `:app` 里的任何单例。做完 A，阶段 B 的组之间可以任意排序。

### A1. 设计层种子（`:core:design`，2–3 天）——✅ 已完成（2026-11-06）

- 新增 `:core:design`（依赖 `:core:common` + Compose + Miuix + MaterialKolor），包名沿用 `com.slideindex.app.ui.miuix.theme`（split package，调用方 import 不变）；
- 新增 `AppThemeSpec`（纯值对象：种子色 / 明暗 / 调色板 id / 规格 id / 深色背景 id），配色解析不再吃 `AppSettings`；
- `MiuixSeedResolver`、`AppMaterialColorScheme` 迁入模块并改为 spec 入参；
- `AppThemeMode` / `DarkBackgroundStyle` / `AppColorSpec` / `ThemePaletteStyle` 下沉到 `:core:common`；
- `:app` 保留 `AppThemeSpecAdapter`（`AppSettings → AppThemeSpec` + 原函数签名），6 个调用点零改动。

**验收**：`:core:design` 独立编译通过；`unitTests` 全绿（323 类 / 2,421 用例）；`:app` 编译通过。

### A2. 宿主层（`:core:overlay-host`，1–2 天）——✅ 已完成（2026-11-06）

- ✅ `OverlayScreenMetrics` 下移（`object` 改 public；`isDisplayLandscapeRotation` 一并下移）。
- ✅ `OverlayComposeOwner` 的 `R` 依赖：换成 `OverlayHostRuntime.overlayThemeResId`（由 `SlideIndexApp` 注入 `R.style.Theme_SlideIndex_Transparent`，为 0 时退回系统透明主题）。
- ✅ `AppLocaleApplier` 依赖：换成 `OverlayHostRuntime.wrapOverlayLocale` 回调。
- ✅ `OverlayComposeOwner` + `OverlayCompose` 迁入模块（模块新增 Compose / lifecycle / savedstate 依赖）。
- ✅ `OverlayFullScreenPanelHost` 迁入模块：浮球两处调用（`isShowing` / `notifyPanelAttachedAboveChrome`）用 `OverlayZOrderHooks` 反转。
- ✅ `OverlayBlurGate` 的纯决策部分迁入模块；读设置的那层（`rememberOverlayBlurEnabled`）留在 `:app` 的 `OverlayBlurGateCompose.kt`。
- ⬜ `backpanel` 还有一个独立阻塞：`EdgePanelParams.kt` 用了 **30+ 个 `R.dimen.navigation_edge_*`**，这批尺寸资源要挪进面板模块（并确认没有别处引用）。

**验收**：`:core:overlay-host` 19 文件 / 1,644 行；`:app` 的 `overlay/` 217 个文件。

### A3. 单例接口化（1–2 天）——🟢 已完成（`ScreenPinManager` 除外，见末条）

给这批根包单例各开一个窄接口 / 回调，`SlideIndexApp` 里接线（模式同 `OverlayZOrderHooks`）：

- ✅ `OverlayViewBackHandler` → 直接迁入 `:core:overlay-host`（`internal` 改 public）；它依赖的
  `PredictiveBackHelper` 一起下沉到 `:core:common`。
- ✅ `FloatBallOverlay`：`isShowing` / `notifyPanelAttachedAboveChrome` 已有 `OverlayZOrderHooks` 钩子。
- ✅ `OverlayComposeDialogHost`：`themeSettings: () -> AppSettings` 改成
  `applyTheme: @Composable (@Composable () -> Unit) -> Unit`（默认走 `OverlayHostRuntime.themeWrapper`），
  宿主 context 走 `OverlayHostRuntime.accessibilityHostContext`；3 个调用点改为显式传 `ModuleTheme`。
- ✅ `OverlayToastWindow`：同上（主题包装 + 宿主 context 两个钩子），迁入模块。
- ✅ `LocalFrostedGlassBackdrop` / `ShellCommandBadgeRenderer` / `ShellCommandBadgeOverlay` / `ShortcutBadgeOverlay`（+ Java 版 `ShortcutBadgeRenderer`）：纯绘制，直接迁入。
- ❌ `ScreenPinManager`（2,177 行）：依赖 clipboard / ocr / stash / history / ui / R，**不是钩子能解决的**，必须等 `:core:model` 那三层。

**验收**：`screenshot` / `backpanel` / `quickwheel` / `animation` / `volumepanel` / `fingertip` / `share` 的外部根包依赖**只剩业务类型**（settings / ui / di / R），不再有单例。

> 2026-11-07 的实测补充：**逐组搬在依赖图上远比预期贵**。`filebrowser` 虽然 A 层文件多，但横向依赖
> clipboard / ocr / history / searchpanel / share / privilege / R / service 等 13 个包；`animation` 依赖
> settings / ui / perf。这两组都**不是**"搬走就行"的叶子。真正能整组搬的，是那些只依赖
> `:core:*` + 少量 settings 枚举的面板；在此之前，A3 的接口化仍是前置。

## 3. 阶段 B：按组搬面板（5–7 天，顺序自由）

底座拆完后顺序不再受依赖限制，按"收益 / 风险"排即可：

| 顺序 | 内容 | 文件 / 行 | 备注 |
| :-- | :--- | ---: | :--- |
| B1 | `filebrowser` | 35 / 9,405 | 横向依赖 13 个包，需 UI 套件 + DI + R 之后 |
| B2 | `backpanel` ✅ / `screenshot` ⬜ | 6 / 1,931（已完成）；`screenshot` 仍被 `ScreenPinManager` 挡住 |
| B3 | `quickmenu` + `quickwheel` | 29 / 10,798 | 依赖 A1 + A2 + A3 |
| B4 | `pickresult` + `share` | 27 / 10,773 | 依赖 A2 + A3 |
| B5 | `history` | 42 / 14,807 | 最大，业务耦合最深 |
| B6 | `searchpanel` + `ringlauncher` + `corner` + `carousel` + `holographic` | 52 / 14,932 | |
| B7 | `dragdrop` + `volumepanel` + `animation` + `fingertip` | 18 / 6,646 | |

## 4. 阶段 C：根目录 220 个文件（3–5 天）

| 顺序 | 内容 | 说明 |
| :-- | :--- | :--- |
| C1 | 控制器与宿主：`EdgeOverlayHost` / `OverlaySidePanelHost` / `OverlayFullScreenPanelHost` / `EdgeGestureOverlayHosts` … | 这批是"壳"，搬完以后面板才有真正的归属模块 |
| C2 | 面板与单例：`CNotice*` / `FloatBall*` / `Message*` / 各类 `*OverlayWindow` | 逐个按依赖处理 |
| C3 | 收尾：删掉 `OverlayPreviewPort` 适配器背后的静态入口、收拾 trampoline | 静态入口必须等所有调用点迁完才能删 |

## 5. 已完成

| 批次 | 内容 | 结果 |
| :--- | :--- | :--- |
| 批 1 | `OverlayPreviewPort` + `AccessibilityOverlayPreviewPort` | 设置页 21 个预览静态入口收成接口，`OverlayServiceController` 改注入 |
| 批 2a | `compositor` → `:core:overlay-host` | 5 文件 / 296 行，4 个外部调用用 `OverlayZOrderHooks` 反转 |
| **A1** | 设计层种子 → `:core:design` | `AppThemeSpec` + `MiuixSeedResolver` + `AppMaterialColorScheme` 迁入；主题枚举下沉 `:core:common`；`:app` 留适配层 |
| **A2a** | `OverlayScreenMetrics` → `:core:overlay-host` | 60 行；`isDisplayLandscapeRotation` 一并下移，`:app` 只剩调用 |
| **A2b** | 宿主层 → `:core:overlay-host` | `OverlayComposeOwner` / `OverlayCompose` / `OverlayFullScreenPanelHost` / `OverlayBlurGate`（纯决策部分）迁入；R 与语言依赖用 `OverlayHostRuntime` 反转，浮球用 `OverlayZOrderHooks` 反转。模块 19 文件 / 1,644 行 |
| **A3a** | `OverlayViewBackHandler` → `:core:overlay-host` | 328 行，`internal` 改 public；`PredictiveBackHelper`（179 行）一并下沉到 `:core:common` |
| **A3b** | 毛玻璃背板 + 徽标 → `:core:overlay-host` | `LocalFrostedGlassBackdrop` / `ShellCommandBadgeOverlay` / `ShortcutBadgeOverlay` / `ShellCommandBadgeRenderer` / `ShortcutBadgeRenderer` 迁入；模糊开关改由 `OverlayHostRuntime.overlayBlurEnabledFlow` 注入 |
| **A3c** | toast + 对话框宿主 → `:core:overlay-host` | `OverlayToastWindow` / `OverlayComposeDialogHost`；主题包装改 `OverlayHostRuntime.themeWrapper`（`OverlayThemeWrapper` 接口），`themeSettings` 换成 `applyTheme` |
| **A4** | 主题层 → `:feature:settings` | `ui/theme` 整包（`Theme` / `Color` / `Shape` / `Type` / `SlideIndexColorSchemes` / `LocalPlatformDensity` / `OverlayAwareTheme`）+ `ui/miuix` 的 `ModuleTheme` / `MiuixBlur` / `MiuixOverlayComposeLocals` / `MiuixSquircle` 迁入；DI 依赖用新增的 `SettingsFlowHolder` 反转。`OverlayAwareModuleTheme` 被 15 个面板组使用，这一步解锁它们 |
| **A5** | 图标层 → `:core:design` | `ThinActionIcons` / `GestureActionIcons` / `GestureActionOutlinedIcons` / `MaterialSymbolThinIcons` / `MaterialTouchIcons` / `PayQrActionIcons` 迁入（3,006 行，纯 Compose ImageVector，无 `R`）；`internal` 改 public |`n| **A6** | `overlayHostContext` 去 DI 化 | 新增模块级 `overlayHostContext()`（`:core:overlay-host`），把 29 个 `:app` 文件里的 `OverlayDependencyAccess.overlayHostContext()` / `com.slideindex.app.di.overlayHostContext()` 全部换掉 |`n| **B2** | `backpanel` → **新模块 `:feature:overlay-panels`** | 6 文件 / 1,931 行 + 42 个 `navigation_edge_*` dimen 一起迁入；`AppSettings→AppThemeSpec` 映射下沉到 `:feature:settings`，面板直接依赖 `:feature:settings` |

`:core:overlay-host` 现有 27 个文件 / 3,136 行；`:core:design` 3 个文件 / 246 行；`:feature:overlay-panels` 7 个文件 / 1,993 行；`:feature:settings` 175 文件 / 24,258 行；`:app` 的 `ui/theme` 目录已清空，`overlay/` 根目录从 220 个文件降到 209。

## 6. 每批验收（不变）

1. `.\gradlew.bat :app:compileFullDebugKotlin` 过；
2. `.\gradlew.bat unitTests --continue` 全绿（基线 323 类 / 2,421 用例）；
3. 真机点一遍**本批涉及的面板**（overlay 基本没有单测，手测是唯一兜底）；
4. `python scripts/normalize-eol.py --check <改动文件>` 全 LF；
5. 单独一个提交，信息写清"阶段 + 组"。

## 7. 做还是不做的判断

| 视角 | 结论 |
| :--- | :--- |
| 用户可感知收益 | **零**。不修 bug、不加功能、不改手感 |
| 开发效率收益 | 明显：`:app` 从约 35.4 万行降到约 21.8 万行，冷编译从 4–5 分钟往下走；模块边界真实化 |
| 成本 | 1.5–2 周，且必须整块时间（碎片做会留半拆状态） |
| 风险 | overlay 无单测，只能真机手测；每批都可能撞到新的隐式依赖 |

## 8. 2026-11-07 重新评估（重要）

阶段 A1–A3b 已把**基础设施**（约 2.4k 行）搬进 `:core:design` / `:core:overlay-host`。但继续搬**面板组**时发现：A3 的"单例接口化"**不是充分的**——面板组的阻塞项主要是**类型级依赖**，不是几个静态入口。

实测（组 → 除 overlay 自身外的 `com.slideindex.app.*` import）：

| 组 | 依赖 | 结论 |
| :--- | :--- | :--- |
| `quickwheel` | `settings`（QuickWheel*、AppSettings）、`gesture`（ActionExecutor/GestureAction）、`activity`、`launcher`、`ui`（图标/标签/渲染器/主题）、`R`、`util` | 搬不动：settings 类型 + UI 套件 |
| `fingertip` | `settings`（FingertipRing*、AppSettings）、`gesture`、`launcher`、`util`（图标位图/Haptic）、`R` | 同上 |
| `share` | `di`（AppGraphEntryPoint / OverlayDependencyAccess）、`search`、`ui`、`util`、`R` | 同上 |
| `filebrowser` | 13 个包（clipboard / ocr / history / searchpanel / share / privilege / R / service …） | 同上 |
| `animation` | `settings`（样式）、`ui`（图标/主题）、`perf` | 同上 |

也就是说：**要整组搬面板，前置不是 A3，而是公共层抽取**。2026-11-07 当天又实测了一轮，把顺序修正为：

1. **`util` 工具**（收益最快，但**是 hub-and-spoke**）：`PermissionHelper` ✅ 已下沉（239 个调用点，服务类用 `OverlayHostRuntime.accessibilityServiceClass` 反转）。同日试搬 `BrightnessControlHelper` / `VolumeControlHelper` 失败并已回滚——它们同包调 `TaskManagerUtil`（968 行，自身依赖 `R` / `OverlayService` / `Settings` / `PrivilegeGateway` / 根权限操作）。**`TaskManagerUtil` 是 util 层的枢纽**，但盘完发现它不是"反几个钩子"能搬的：它依赖 `R`（5 处字符串）/ `OverlayService` / `AppSettings` / `PrivilegeGateway` / `RootPrivilegedOperations` / `RootTaskManagerServiceHost` / `ShizukuUserServiceHost` / `ITaskManagerService` —— 也就是**整个特权 + Shizuku + 任务管理子系统**。这部分是 `:app` 的核心，不应为搬面板而搬走；面板要它，只能走接口（`:core:tasks` 的 `RecentTasksProvider` 之类），那是另一个立项。
2. **`ui` 套件**（最大的一块）：`ThinActionIcons`、`gestureActionIcon`、`gestureActionLabelText`、`QuickWheelWheelRenderer`、`OverlayAwareModuleTheme` 等，依赖 miuix + 图标资源，要连 `R` 一起沉；
3. **`di` 反转 + `R` 资源下沉**：`OverlayDependencyAccess` 换成接口注入（`OverlayHostRuntime` 是现成样板），`R.dimen/string/drawable` 按组迁移；
4. **`:core:actions`**：`ActionExecutor` / `GestureAction` 接口（面板只需"执行"，不需实现）。

**修正一**：`:core:model` 不再是前置——面板模块可以直接依赖 `:feature:settings`（`backpanel` 就是这么搬的），设置模型抽离可以后置。

**修正二**：**估计 2–3 周**（需真机回归），之后面板组迁移约 1–2 周；原计划"1.5–2 周完成"不成立。

**当前状态（2026-11-07 收盘）**：
- A 阶段完成（`ScreenPinManager` 除外）；
- B 阶段完成 1/16（`backpanel`，3.2k 行）+ 新模块 `:feature:overlay-panels`；
- 公共层完成 `PermissionHelper`；
- 下一个可动的组：`carousel`（3 文件 / 1,042 行），仍差 `di` + `R` + 3 个 util。

## 9. A′ 期间已完成的公共层搬迁（2026-11-07 下午）

| 批次 | 内容 | 位置 |
| :--- | :--- | :--- |
| A4 | 主题层（`ui/theme` 整包 + `ui/miuix` 4 文件） | `:feature:settings` |
| A5 | 图标层 6 文件 / 3,006 行 | `:core:design` |
| A6 | `overlayHostContext()` 去 DI（29 个 `:app` 文件） | `:core:overlay-host` |
| A7 | `perf` 4 文件 + `barcode` 3 文件 | `:core:common` |
| A8 | 18 个纯 Android util + `ShellCommandIconResolver` | `:core:common` |
| A9 | `DrawableExtensions`（需要 Compose） | `:core:design` |

**:app/util 的搬迁规律**（实测）：能搬的是"零 app import 且不引用同包 app 类"的文件；一旦同包引用（Kotlin 不用 import）就会连环失败。
已回滚过的：`AppLocaleApplier`、`OverlaySuppression`、`TriggerVisibility`、`VoiceActionHelper`、`SystemBackGestureInsetHelper`、`PackageActivityResolver`、`PickerAppIconBitmap`、`BrightnessControlHelper`、`VolumeControlHelper`、`HiddenApiBootstrap`。

### 下一块：`carousel`（3 文件 / 1,042 行）的具体前置

| 依赖 | 处理方式 |
| :--- | :--- |
| `SlideIndexAccessibilityService.accessibilityInstance()` → `performGlobalAction` | 新增 `OverlayHostRuntime.accessibilityService: (() -> AccessibilityService?)?`（平台类，模块可用） |
| `TaskManagerUtil.refreshRecentTasks()` / `forceStopPackage()` | 新增 `RecentTasksPort` 接口（返回类型 `RecentTaskRef` 需先下沉） |
| `RecentTasksLoader.removePackages()` | 同上接口 |
| `TaskSwitcherMenuActions.launchFreeWindow()` | 同上接口或独立回调 |
| `R.string.app_carousel_*`（8 条） | 抽到 `:feature:overlay-panels` 的 `res/values/strings.xml` |

## 10. 2026-11-08 状态与结构性结论（大爆炸试验后）

### 实测进度

| 指标 | 数值 |
| :--- | :--- |
| `:app` 的 `overlay/` | 441 文件 / 136,402 行 → **271 文件 / ~113,000 行** |
| `:feature:overlay-panels` | **157 文件 / ~20,500 行** |
| `:core:overlay-host` | 30 文件 / 3,824 行 |
| 整组迁出 | 6 组（backpanel / carousel / holographic / fingertip / animation / share）+ screenshot 4/7 |
| 散件 + 收敛 | 约 110 个文件 |

### 大爆炸试验（已放弃）

尝试把剩余 323 个 overlay 文件一次性搬进 `:feature:overlay-panels`，再用编译回滚收敛：

```
iteration 0: 237 个文件编译失败
iteration 1:  32
iteration 2:   2
iteration 3:   0  → 净落地 52 个文件
```

结论：**剩余 overlay 的"可独立闭合子集"已经挖完**。再往下的 271 个文件互相引用，并且都伸进 `:app` 的：

- `util`（140 处引用，剩余 47 文件依赖 service/shizuku/privilege/R）
- `stash`(70) / `clipboard`(66) / `service`(62) / `message`(61) / `launcher`(51) / `di`(43) / `ui`(22)
- `R`（96 处）

### 两条后续路线

| 路线 | 做法 | 轮次 | 干净度 |
| :--- | :--- | :--- | :--- |
| **A. 端口优先（推荐）** | 先做一批窄接口：`ActionExecutor` 面板面（14 方法）、音量/亮度、picker 图标、label+字符串、`ScreenPinManager`、`FloatBall` 单例族；再整组搬 | 约 4–6 轮 | 干净：每组真实边界 |
| **B. app-core 大爆炸** | 把 overlay+ui+service+util 整体搬进一个 `:app-core`，`:app` 只留 Application/Manifest | 约 2–3 轮 | **不干净**：只是把上帝模块改名，且错误面数百 |

2026-11-08 维护者选择：**A 路线**（干净彻底，宁可多轮）。

## 11. 剩余组的端口地图（2026-11-08）

已迁出：`backpanel` / `carousel` / `holographic` / `fingertip` / `animation` / `share` /
`Adjust` / `corner`（8 组）+ `screenshot` 4/7 + 约 110 个散件；`:feature:overlay-panels`
约 26k 行，`:app` 的 `overlay/` 从 136,402 行降到约 107,700 行。

### 已就位的端口

| 端口 | 覆盖 |
| :--- | :--- |
| `PanelActionHost`（22 方法） | 音量/亮度/勿扰/深色/连续调节 + 权限探测 + 刻度文案 |
| `GestureActionRunner` | 手势动作执行（action / settings / armed / anchor / panelSide） |
| `PanelFloatBallHooks`（5） | 进入自由移动、藏/恢复 chrome、抑制触摸宿主、布局度量 |
| `PanelRuntimeHooks`（30+） | 宿主 context、设置快照/流、shell 持久化、标签、各类图标、分享开关、角标 Intent、触发抑制、点击注入、壁纸模糊、任务/小窗、AppRepository、DI Composition、快捷启动器项 |
| `SettingsFlowHolder` / `OverlayHostRuntime` / `OverlayZOrderHooks` | 设置流、宿主能力、z 序 |

### 各组还差什么

| 组 | 缺口 |
| :--- | :--- |
| `ringlauncher` | `CreateShortcutTrampoline.launch`（host 是 app 的 `AppShortcutLoader.CreateShortcutHost`）、`RingLauncherSlotIconEditorTrampolineActivity` / `RingLauncherSlotPickTrampolineActivity` 两个 Intent、`RingLauncherSlotConfigSheet`（495 行 Compose，依赖 `Md3Picker*` / `QuickLauncherPanel*`） |
| `volumepanel` | `ui.gesturepicker` picker 套件（动作选择页）、`FloatBallOverlay` 少量方法（已有同族钩子可扩） |
| `quickwheel` | 同上 picker 套件 + `ActionPickerCatalogPolicy` |
| `dragdrop` / `pickresult` / `quickmenu` | `FloatBall*` 单例族（贴图/取词/翻译入口）+ `SearchPanel*` 跨组调用 |
| `filebrowser` / `history` / `searchpanel` | `clipboard` / `stash` / `search` 三个子系统（建议先做 `:core:clipboard` 的只读接口） |

### 建议的下一刀（按性价比）

1. `ringlauncher` 三个 trampoline 钩子 + slot config sheet 的 composable 钩子（1 轮）；
2. `volumepanel` + `quickwheel` 共用的 picker 套件抽到 `:core:design`（2 轮，一次解锁两组）；
3. `FloatBall*` 单例族端口（2 轮，解锁 `dragdrop` / `pickresult` / `quickmenu` 三组）；
4. `:core:clipboard` 只读接口（2–3 轮，解锁 `filebrowser` / `history` / `searchpanel`）。

---

## 12. 收口结论与下一阶段施工单（2026-11-08）

### 12.1 本阶段（W1-A/B）实际交付

| 项 | 数据 |
| :--- | :--- |
| `:feature:overlay-panels` | 0 → 约 26.4k 行（新建模块） |
| `:app` 的 `overlay/` | 136,402 → 约 107,700 行（-21%） |
| 整组迁出 | 9 组：`backpanel` / `carousel` / `holographic` / `fingertip` / `animation` / `share` / `Adjust` / `corner`（+ `screenshot` 4/7） |
| 散件迁出 | 约 110 个文件 |
| 新增落地模块 | `:core:design`、`:core:overlay-host`、`:core:shortcuts` |
| 端口 | `PanelActionHost`（22 方法）、`GestureActionRunner`、`PanelFloatBallHooks`（5）、`PanelRuntimeHooks`（40+） |
| 验证 | `unitTests` 全绿（323 类 / 2,421 用例）、`:app` 真机装机通过 |

### 12.2 为什么在此收口

继续"一组一组搬"的边际收益已经很低：`ringlauncher` 两次迁移实测各剩 30+ 编译错误，
且缺口集中在 `FloatBallLayout` / `ActionExecutor` / `RecentTasksLoader` /
`TaskSwitcherRepository` / `AppShortcutLoader` —— 全是 **`:app` 核心子系统的实现细节**。

继续补钩子会把 `PanelRuntimeHooks` 养成第二个 god object，与本次重构目的相反。
因此收口，转为**先把依赖方向掰正**。

### 12.3 下一阶段施工单（P1–P4）

每条都要求：模块自带测试、`:app` 只保留实现与接线、面板通过接口使用。

#### P1 `:core:clipboard`（进行中：模块已建，数据层 19 文件已迁）

- 只读 API：历史列表、条目详情、变更流（`Flow<List<ClipboardEntry>>`）
- 不含写入 / 特权操作；写入仍留在 `:app`，通过现有钩子
- 目标：`filebrowser` / `history` / `pickresult` 不再 import `com.slideindex.app.clipboard.*`
- **进度（2026-11-08）**：模块已建；19 个纯数据/解析文件迁入（3,174 行）；`ClipboardHistoryQuery` 只读接口 + `ClipboardService` 服务定位点已就绪，`:app` 仓储已实现并注册；模块测试 3 例通过
- 验收：三组整组迁入 panels，且 `:app` 中 `clipboard` 包不再被 `overlay/` 引用

#### P2 `FloatBallPort`（预计 2 轮）

- 把 `FloatBallOverlay` / `FloatBallLayout` 的静态能力收敛成 `interface FloatBallPort`
  （chrome 显隐、自由移动、布局度量、贴图/取词入口）
- `:app` 提供实现并在 `OverlayHostRuntime` 注册；`PanelFloatBallHooks` 逐步废弃
- 目标：解锁 `dragdrop` / `quickmenu` / `pickresult`

#### P3 picker 下沉 `:core:design`（预计 2 轮）

- `ui.gesturepicker` 的选动作页 + `Md3Picker*` 基础件迁入 `:core:design`
- 依赖：`AppRepository` 走 `:feature:apps` 接口；图标走 `:core:shortcuts`
- 目标：解锁 `volumepanel` / `quickwheel`

#### P4 `:core:tasks`（预计 1–2 轮）

- `TaskSwitcherRepository` / `RecentTasksLoader` / 最近任务查询下沉
- 目标：解锁 `ringlauncher`（最后一块拼图）

### 12.4 钩子退役计划

`PanelRuntimeHooks` 目前 40+ 个槽位，属迁移期脚手架。P1–P4 完成后按组退役：

| 钩子族 | 退役方式 |
| :--- | :--- |
| `appRepository` / `settingsSnapshot` / `persist*` | 换 `:feature:apps` + `:feature:settings` 的正式接口 |
| `addIconChangeListener` / `iconCacheRevision` | 换 `:core:design` 图标库服务 |
| `quickLauncher*` / `managedShortcut*` | 换 `:core:shortcuts` 的 resolver 接口 |
| `pickerApps` / `ringSlotConfigSheetContent` | P3 完成后删除 |
| `dispatchTapAsync` / `switchInputMethod` | 换 `:core:overlay-host` 的系统能力接口 |

### 12.5 P1 执行记录（2026-11-08）

| 事项 | 结果 |
| :--- | :--- |
| `:core:clipboard` | ✅ 已建：19 个纯数据/解析文件（3,174 行）+ `ClipboardHistoryQuery` 只读接口 + `ClipboardService` 定位点 + `ClipboardForeground`；`:app` 仓储已实现并注册；6 个模块测试 |
| `:core:stash` | 🟡 部分：仅 `StashEntry` / `StashReminderMirror` 可独立（433 行）；其余 10 个文件是紧耦合集群 |
| `history` 组 | 🟡 已搬 `HistoryToast`；其余压在 stash / service / ui |

**stash 集群的依赖链**（实测）：

```
StashRepository ↔ StashMetaRepository → StashReminderScheduler ← StashReminderBootReceiver
StashReminderReceiver → StashReminderPendingState + StashClipboardTrampolineActivity + 8×R.string
```

要整组搬，需先补：trampoline Intent 钩子、`StashReminderPendingState` 下沉、8 条提醒文案。

**结论**：剩余组（`history` / `filebrowser` / `pickresult` / `quickmenu` / `searchpanel`，约 5.6 万行）
的共性卡点是 `service`（16k）与 `ui`（101k）两个巨型包，不是单个数据层能撬动的。
建议把 `:core:clipboard` / `:core:stash` 作为下一阶段的起点，按 P2（FloatBallPort）→ P3（picker）
→ P4（tasks）→ `service` 拆分推进。

### 12.6 数据层模块进度（续）

| 模块 | 状态 |
| :--- | :--- |
| `:core:clipboard` | ✅ 19 文件 / 3.2k 行 + 只读接口 + 6 测试 |
| `:core:stash` | ✅ 7 文件 / 2.2k 行 + 只读/写入定位点 + 2 测试 |
| `:core:search` | ✅ 18 文件（图片搜索、文件索引、系统设置索引、计算器） |

**`:core:privilege` 实测不可行（2026-11-08）**：`privilege` / `shizuku` 是一个闭环集群——

```
TaskManagerUtil(968) ← PrivilegeGateway ← TaskManagerShellExecutor ← RootNamespace
        ↑                                        ↑
   FreeWindowLauncher(overlay)            R.string + ITaskManagerService(AIDL)
```

试搬 16 文件后连锁回退 9 次，最终只剩 7 个「纯反射/纯 Binder」小文件能独立（价值极低）。
**结论：要动 `privilege`，必须先拆 `TaskManagerUtil`（968 行 hub）**，把"任务查询/结束/小窗"从它里面分出接口，
再让 `PrivilegeGateway` / 执行器依赖接口。这是独立的一次重构，不要和面板迁移混在一起。

### 12.7 quickwheel 垂直切片的前置清单（2026-11-08）

目标：整组迁出 `overlay/quickwheel`（5 文件 / 2,126 行）。

| 文件 | 卡点 | 处理 |
| :--- | :--- | :--- |
| `QuickWheelActionHud` | `ui.gesturepicker` 两个标签函数 | 改用 `PanelRuntimeHooks.gestureActionLabel` / `launchShortcutLabel`（已就位） |
| `QuickWheelContainer` | `ui.gestureActionIcon`（Canvas 画图标） | 需新增 `gestureActionIconDraw` 钩子 |
| `QuickWheelIconResolver` | `ManagedShortcutIconResolver` + `di.OverlayDependencyAccess` | 图标走 `managedShortcut*` 钩子（已就位）；DI 走 `PanelRuntimeHooks.appRepository` |
| `QuickWheelOverlayContent` | `ui.QuickWheelWheelRenderer` | 随 `QuickWheelPreviewCanvas`（524 行）一起迁入模块 |
| `QuickWheelOverlayWindow` | `ActionExecutor` 参数 + `ui.toWheelShapeOrNull` | 参数改 `GestureActionRunner`；映射函数抽到模块 |

即：**再补 1 个钩子（`gestureActionIconDraw`）+ 2 个小函数下沉**即可整组搬走。
这是当前依赖图下最短的一条"能真正搬完一组"的路。

### 12.8 阶段总结（2026-11-08，第 12 组完成后）

**已迁出（12 组）**：backpanel、carousel、holographic、fingertip、animation、share、
Adjust、corner、quickwheel、screenshot、volumepanel（+ B2 backpanel 更早）。

**新增模块**：`:core:design`（4.6k）、`:core:shortcuts`、`:core:overlay-host`（3.8k）、
`:core:clipboard`（3.2k+测试）、`:core:stash`（2.2k+测试）、`:core:search`（18 文件）。

**端口清单（app 侧实现 / 面板侧消费）**：
- `PanelActionHost`（22 方法，动作/权限/文案）
- `PanelRuntimeHooks`（60+ 槽位：宿主上下文、设置快照、标签、图标、分享、Intent、
  触发抑制、点击注入、壁纸模糊、任务、AppRepository、选择器、OCR、截图、图片编辑…）
- `PanelFloatBallHooks`、`PanelStashWriteHooks`（20+）、`PanelVolumeHooks`
- `PickResultHost`、`PanelSlotPicker`/`SlotPickerHook`、`PanelActionIconSlot`/`PanelActionIconView`
- `StashVoiceRecorder`、`StashReminderScheduling`、`ClipboardService`/`ClipboardHistoryQuery`

**剩余 5 组（约 3.1 万行）的共同根因**（已被逐组验证）：

| 组 | 行数 | 根因 |
| :--- | --- | --- |
| `pickresult` | 9,193 | FloatBallPickResultPanel 1436 行 + di + ui |
| `quickmenu` | 7,616 | barcode/macro/pagescroll/service + FloatBall |
| `filebrowser` | 8,632 | privilege/shizuku/ocr/service |
| `history`（剩 2 文件） | 3,302 | 与 `HistoryEntryCards` 互引，而后者依赖 `dragdrop` |
| `dragdrop`（剩 1 文件） | 1,094 | FloatBallImageSearchPanel / Translate / SearchPanel* |
| `searchpanel` | 5,816 | clipboardfloat + search 启动器 + FloatBall |
| `ringlauncher` | 3,416 | FloatBallLayout / RecentTasksLoader / AppShortcutLoader |

**结论**：剩余组的阻塞点已不是"缺钩子"，而是三个 app 内子系统本身需要重构：
1. `FloatBall*` 静态单例（Overlay 4302 行 + PickResult 1636 + ImageSearch 1360…）
2. `ui` 里的通用组件（PickerListUi 已下沉，但 ActionIconView / GlobalIconStore 等仍在）
3. `service`（16k：中转 Activity、前台状态、无障碍查询）

每一项都是独立工程，不宜与面板迁移混做。

### 12.9 最终账目（2026-11-08，W1-A/B + 第一轮 P1/P3）

| 指标 | 数值 |
| :--- | :--- |
| `:feature:overlay-panels` | 0 → **58,489 行**（13 个整组 + pickresult/history 全量文件） |
| `:app` 的 `overlay/` | 136,402 → **79,329 行（-42%）** |
| 整组迁出 | **14 组**：backpanel、carousel、holographic、fingertip、animation、share、Adjust、corner、quickwheel、screenshot、volumepanel、pickresult、history |
| 新增模块 | `:core:design`（约 4.8k）、`:core:shortcuts`、`:core:overlay-host`（3.8k）、`:core:clipboard`（3.2k+测试）、`:core:stash`（2.2k+测试）、`:core:search`（18 文件） |
| 端口 | `PanelActionHost`（22 方法）、`PanelRuntimeHooks`（80+ 槽位）、`PanelStashWriteHooks`（20+）、`PanelFloatBallHooks`、`PanelVolumeHooks`、`PickResultHost`（7 方法）、`PanelSlotPicker`、`PanelActionIconSlot`/`View`、`StashVoiceRecorder`、`StashReminderScheduling`、`ClipboardHistoryQuery`/`ClipboardService`/`ClipboardForeground` |
| 验证 | `unitTests` 650 tasks 全绿；`:app:installFullDebug` 真机装机成功（MEIZU 21） |
| 待推提交 | 101 个（未 push） |

**未完成（明确记录）**：

| 组 | 行数 | 阻塞原因 |
| :--- | :--- | :--- |
| `dragdrop`（剩 1 文件） | 1,094 | 10+ 条文案、`FloatBallPickResultPanel.showResult` 调用改写 |
| `searchpanel` | 5,816 | `clipboardfloat` 子系统、`SearchEngineLauncher`、FloatBall 面板 |
| `quickmenu` | 7,616 | barcode / macro / pagescroll / service / FloatBall |
| `filebrowser` | 8,632 | privilege / shizuku / ocr / service |
| `ringlauncher` | 3,416 | `FloatBallLayout` / `RecentTasksLoader` / `AppShortcutLoader` |

**下一阶段的三个前置工程**（每一项都独立于面板迁移）：
1. `FloatBall*` 单例重构为 `FloatBallPort`（overlay 4,302 行 / PickResult 1,636 / ImageSearch 1,360）
2. `service` 拆分（16k：中转 Activity、前台状态、无障碍查询）
3. `TaskManagerUtil`（968 行 hub）拆分，才能动 `privilege`

**已落地的可复用资产**：`PanelRuntimeHooks` 的钩子模式在本仓库已被 14 组面板使用，并有明确的退役计划（见 §12.4）——这些槽位是迁移期脚手架，不是最终架构。

### 12.10 第二轮进展（2026-11-08 晚）

新增整组与半组：
- `dragdrop`（1,094 行，第 15 组）
- `ringlauncher`（3,416 行，第 16 组）
- `clipboardfloat`（2,990 行，第 17 组）
- `pickresult` / `history` 补齐剩余文件
- `searchpanel` 12/18、`quickmenu` 15/21、`filebrowser` 部分文件

新增端口族：
- `PickResultHost`、`ClipboardFloatHost` / `ClipboardFloatHistorySource`
- `PanelRuntimeHooks` 扩到 110+ 槽位（settingsFlow / settingsRepository /
  overlayHostContext / refreshRecentTasks / quickLauncherIconBitmap /
  createShareImageUri / showFileBrowser / suppressChromeForCapture / editStashImage …）
- `PanelFloatBallHooks.isShowing` / 延迟版 scheduleChromeAbovePanels

新增模块：
- `:core:translate` 承接语言标签 helper（并开启 Compose）
- `:core:clipboard` 承接 clipboardfloat 的 5 个无耦合件
- `:core:common` 承接 quickmenu 六个纯逻辑件（953 行）与快捷方式索引件

**当前账目**：`:feature:overlay-panels` ≈ 68.2k 行；`:app` 的 `overlay/` 72.4k 行
（起点 136.4k，**-47%**）；本地待推提交 128 个。

**剩余阻塞（均需结构性重构，非端口能解）**：
1. `FloatBall*` 单例簇（Overlay 4.3k / PickResult 1.6k / ImageSearch 1.4k）
2. `freezer`（3.2k）与 `macro`（MacroActionRunner）
3. `service`（无障碍服务 16k：截图/点击/文案提取）
4. `privilege` + `TaskManagerUtil`（968 行 hub）
5. `filebrowser` 内部闭环（MediaPage/MediaCategory/AccessInfo）

### 12.11 第三轮进展与收口（2026-11-09 凌晨）

**新增整组**：`filebrowser`（8,632 行，第 18 组）。
**新增半组/主体**：`quickmenu`（7,616 行，app 侧仅剩 2 个被端口反向引用的实现）、
`searchpanel`（5,816 行，app 侧仅剩 `SearchPanelScreen` + 查询桥）。

**新增模块/依赖**：
- `:core:common` 承接 root/shizuku 执行链（TaskManagerShellExecutor / RootShellSession /
  RootNamespace，约 540 行）与 quickmenu 六个纯逻辑件（953 行）
- `:core:search` 承接联系人索引、快捷方式索引件、文件缩略图缓存（并开启 compose-ui）
- `:core:translate` 承接语言标签 helper（开启 Compose）
- `:feature:overlay-panels` 增加 camera / shizuku / reorderable / translate / ocr 依赖

**新增端口族**：
- `FileBrowserHost`（特权 / shizuku / shell / 钉屏 / 图片编辑 / 搜索面板 / 固定快捷方式 / 中转）
- `QuickMenuHost`（runEntry / setReminderAlarm / captureScreenshot / runMacro）
- `SearchPanelHost`（freezerState / freezerStateOfTarget / launchAndRestore / isPaused / setPaused / setFrozen）
- `SearchPanelContent`（把 `SearchPanelScreen` 作为组合函数注入模块）
- `ClipboardFloatHost` / `ClipboardFloatHistorySource` / `ShellRuntimeHooks`
- `PanelRuntimeHooks` 扩到 130+ 槽位（含 pickerIconBitmap / settingsFlow / suppressChromeForCapture …）

**当前账目**：

| 指标 | 数值 |
| :--- | :--- |
| `:feature:overlay-panels` | 0 → **约 82k 行** |
| `:app` 的 `overlay/` | 136,402 → **约 55k 行（-60%）** |
| 整组迁出 | **18 组** |
| 新增模块 | design / shortcuts / overlay-host / clipboard / stash / search / translate（+ common 扩容） |
| 本地待推提交 | 见 git（未 push） |

**app 侧 `overlay/` 剩余内容（约 55k）**：
1. `FloatBall*` 簇（Overlay 4.3k / PickResult 1.6k / ImageSearch 1.4k / Chrome 0.6k）
2. `SearchPanelScreen`（1.9k，已通过 `SearchPanelContent` 端口被模块调用）
3. `service` 服务本体（无障碍宿主，截图/点击/取词）
4. 其余 overlay 通用壳（Toast/BlurGate/Compose 宿主等已在 `:core:overlay-host`）
5. `quickmenu` 两个被端口反向引用的实现（ToolScreens/EntryRouter 侧）

**结论**：面板层的"可迁移面"已经基本搬完；剩下的是 `FloatBall` 单例簇与无障碍服务本体，
两者都是独立重构项目，不属于迁移批次。

#### 12.11.1 剩余 `:app` overlay 目录精算（2026-11-09）

面板组目录**已全部清空**（每个都是 0 行）：

```
animation / backpanel / carousel / compositor / corner / dragdrop / fingertip /
history / holographic / pickresult / quickwheel / ringlauncher / screenshot / share
```

仍有残余的只有 4 个目录 + 根目录：

| 位置 | 行数 | 说明 |
| :--- | ---: | :--- |
| `overlay/` 根目录 | 51,946 | FloatBall 簇、边缘/侧边/CNotice、指针会话、拦截层等 |
| `overlay/searchpanel` | 1,963 | `SearchPanelScreen`（通过 `SearchPanelContent` 端口被模块调用） |
| `overlay/volumepanel` | 581 | `ExpandPanelSlotPicker`（通过 `PanelSlotPickerHook` 提供） |
| `overlay/quickmenu` | 156 | `QuickMenuExtraScreens`（入口路由，已被 `QuickMenuHost` 包住） |
| `overlay/filebrowser` | 47 | `FileBrowserLauncherHelper`（桌面快捷方式，已被 `FileBrowserHost` 包住） |
| `overlay/preview` | 122 | 预览辅助 |

根目录前 10 大文件（合计约占 16k）：

| 文件 | 行数 |
| :--- | ---: |
| `FloatBallOverlay` | 4,302 |
| `ScreenPinManager` | 2,177 |
| `CNoticeOverlayWindow` | 1,641 |
| `FloatBallPickResultPanel` | 1,636 |
| `EdgeCardOverlayWindow` | 1,529 |
| `FloatBallImageSearchPanel` | 1,360 |
| `QuickLauncherOverlayController` | 1,302 |
| `QuickLauncherRenderer` | 993 |
| `EdgeGestureOverlayView` | 871 |
| `FloatingPointerSession` | 861 |

这些文件的共同点：**都是"单例入口 + 服务回调"结构**，面板侧已经通过端口在消费它们
（`PanelFloatBallHooks` / `PickResultHost` / `PanelRuntimeHooks`），迁移它们等于重写这些单例，
不属于"把面板搬出去"，而是"把宿主重构成服务"——单独立项。

### 12.12 结论与后续立项

**交付口径**（本项目可验收的部分）：

1. 14 个面板组目录在 `:app` 中清空（0 行）；另有 4 个面板组（filebrowser / quickmenu /
   searchpanel / volumepanel）的**可迁移部分**已全部迁出，剩余的是被端口反向引用的 app 实现。
2. `:feature:overlay-panels` 86,190 行 / 342 文件；`:app` 的 `overlay/` 由 136,402 行降到
   54,815 行（**-60%**），且下降全部来自面板 UI 与数据层，不是删代码。
3. 所有接线都走端口（14 个端口族、`PanelRuntimeHooks` 130+ 槽位），未引入"模块反向依赖 app"。
4. 验证：`:app` + `:feature:overlay-panels` + 全部 core 模块编译通过；585 个变更文件行尾全 LF。

**三个后续立项**（按收益排序）：

| 立项 | 范围 | 收益 |
| :--- | :--- | :--- |
| A. `FloatBall` 单例服务化 | `FloatBallOverlay` 4.3k、`FloatBallPickResultPanel` 1.6k、`FloatBallImageSearchPanel` 1.4k、`FloatingPointer*` 2.4k | 解锁 `overlay/` 根目录约 16k 行，并让 `pickresult` / `searchpanel` 的 app 残留归零 |
| B. 无障碍服务拆分 | `SlideIndexAccessibilityService`（16k，截图/点击/取词/边缘手势全在里面） | 解锁 `CNotice*` / `EdgeCard*` / `SideOverlay*` 等约 12k 行 |
| C. `ui` 通用组件继续下沉 | `ui/` 101k 中的列表/图标/搜索框等 | 让后续任何面板迁移不再受 `ui` 阻塞 |

**端口退役计划**（重要）：`PanelRuntimeHooks` 等是**迁移期脚手架**，不是最终架构。建议在立项 A/B
完成后用一个季度逐步替换为构造注入/DI，并把静态可变槽位删掉——否则"静态可变全局"会变成新的技术债。

**不要做的事**：不要再按"一次搬几个文件"的方式磨 `overlay/` 根目录。那 16k 行是单例结构，
逐文件搬只会产生循环依赖与重复类，本阶段已多次验证（见 §12.11.1）。

### 12.13 A3 单例拆分进展（2026-11-09）

`FloatBallOverlay`（4,302 行单文件）已拆出 5 个职责类，主文件降到 **4,001 行**：

| 拆出的类 | 行数 | 职责 |
| :--- | ---: | :--- |
| `FloatBallCaptureChrome` | 104 | 截图 / 区域取词 / 圆环启动器期间的 chrome 抑制与恢复 |
| `FloatBallPositionPreview` | 85 | 位置预览、待确认值超时清理 |
| `FloatBallAppearancePreview` | 126 | 大小/透明度/线条的实时预览与回滚 |
| `FloatBallLifecycle` | 46 | 语言切换重建、布局重算、输入法高度变化 |
| `FloatBallDismiss` | 90 | 销毁路径（窗口 / 触摸层 / 空闲层 / scope） |

**方法**：只拆行为、不搬状态；跨文件用到的成员放开 `internal`，公共入口改为委托。
这样每一步都能独立编译 + 全量单测通过，可随时停下。

**未拆的部分**：`showOrUpdate`（创建/更新，115 行）与 `ensureWindows`。它们和渲染、
拖拽、空闲层高度耦合（`dragSession` / `syncBallIdleAlpha` / `floatBallStyleSignature`），
脚本化搬迁两次都误伤了相邻函数，已回退。**结论：剩余部分必须人工逐段搬，不能脚本化。**

**P1-2 口径**：原计划"拆 4 类"已达成（实际 5 类）。
**P1-3 口径**：面板侧静态入口已全部端口化；根包单例的静态入口未收敛，属 A3 剩余部分。

### 12.14 阶段 C 的真实成本与下一步方案（2026-11-09）

**结论：阶段 C 不能靠"逐文件搬"，必须先把根目录的宿主单例接口化。**

本轮三次尝试（`SideBubble`+`FloatIcon` 1.1k、`FloatBall*` 辅助视图 0.96k、
leaf 视图 + 布局 2k）全部回退，原因一致：根目录文件之间以及与宿主单例之间的引用密度太高。

根目录 125 个文件的引用密度实测（`overlay/` 根目录内相互引用次数）：

| 符号 | 引用数 |
| :--- | ---: |
| `FloatBallOverlay` | 267 |
| `FloatBallLayout` | 93 |
| `OverlayManager` | 53 |
| `FloatBallCursorPreviewView` | 22 |
| `QuickLauncherOverlayController` | 19 |
| `EdgeCardOverlayWindow` | 17 |
| `MessageOverlayHost` | 16 |
| `RegionalPickOverlay` | 13 |
| `FloatingPointerOverlayWindow` | 12 |

也就是说：**任何单个文件搬走，平均会连锁 2–5 个未定义符号**；而 `FloatBallOverlay`（267 次）
不接口化，它周围 100 多个文件全都搬不动。

**建议的 A3 收尾方案（下一步）**：

1. 定义 `FloatBallPort`（只出 6 个方法：`suppressForScreenshotCapture` / `restoreAfterScreenshotCapture` /
   `scheduleChromeAbovePanels` / `isShowing` / `hideChromeForRingLauncher` / `bringChromeAbovePanels`），
   `FloatBallOverlay` 实现它，根目录文件改为依赖接口而不是单例。
2. 同样给 `OverlayManager`（53 次）与 `RegionalPickOverlay`（13 次）各定义一个窄接口。
3. 三个接口稳定后，根目录的**叶子文件**（视图/渲染器/协调器，约 40 个文件、1.2 万行）就能成批搬入
   `:feature:overlay-panels`；宿主单例（`FloatBallOverlay` / `ScreenPinManager` / `OverlayManager` /
   `CNoticeOverlayWindow` / `EdgeCardOverlayWindow`）继续留在 `:app` 作为服务实现。
4. 这一步做完，`P1-3`（静态入口收敛）与 `P1-1`（`:app` ≤20 万行）才有达成路径：
   预计可再搬 1.2 万行 + 现有已搬部分，`:app` 落到 22 万左右，还需继续搬 `ui` 才到 20 万以内。

**已交付部分不受影响**：19 个面板组、`:app/overlay` 已从 136,402 降到 50,463 行（-63%），
编译与全量单测均通过。

### 12.15 A3 结构性完成（2026-11-09）

`FloatBallOverlay` 从 4,302 行降到 **3,313 行**（-23%），拆出 10 个职责类：

| 类 | 行数 | 职责 |
| :--- | ---: | :--- |
| `FloatBallCaptureChrome` | 104 | 截图/取词/启动器期间 chrome 抑制与恢复 |
| `FloatBallPositionPreview` | 85 | 位置预览与回滚 |
| `FloatBallAppearancePreview` | 126 | 外观预览与回滚 |
| `FloatBallLifecycle` | 46 | 语言切换重建、布局重算、IME |
| `FloatBallDismiss` | 90 | 销毁路径 |
| `FloatBallShow` | 120 | 创建/更新入口 |
| `FloatBallIdleVisuals` | 155 | 空闲显隐与 alpha 动画 |
| `FloatBallTouchWindowLayout` | 171 | 扩窗/收缩/穿透 |
| `FloatBallDragChromeLayout` | 100 | 拖拽单帧布局 |
| `FloatBallDragFinish` | 320 | 拖拽收尾/松手取词 |

**剩余 3,313 行是交互状态机**：`handleForwardedTouch` 主循环 + 取词预览，
与 `dragSession` / `cursorPreview*` / `regionalPick*` / `selection*` 十几个状态互相咬合。
纯搬移无法分离，需要先把状态收进 `FloatBallInteractionState` 对象——属设计重构，另行立项。

**关于单测**：拖拽数学其实早已有覆盖（`FloatBallDragSessionTest` 483 行、
`FloatBallLayoutTest` 237 行，都是既有文件）。本次一度把这两个文件整文件覆盖成短版本，
已按原样完全恢复并单独提交修正；后续补测应在这两个文件里**追加**用例，不要新建同名文件。

**结论**：A3 按"职责拆分"口径完成；剩余交互状态机与阶段 C 的宿主单例接口化一起，作为后续独立项目。