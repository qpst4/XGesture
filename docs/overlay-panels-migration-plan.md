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