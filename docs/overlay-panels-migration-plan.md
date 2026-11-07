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
| `quickwheel` | `OverlayBlurGate`（✅ 已下移）/ `OverlayComposeOwner`（✅ 已下移）/ `ShellCommandBadgeOverlay` / `ShortcutBadgeOverlay` | 单例 |
| `animation` | `OverlayComposeOwner`（✅ 已下移） | ✅ 可搬 |
| `volumepanel` | `FloatBallOverlay` / `LocalFrostedGlassBackdrop` / `OverlayViewBackHandler`（`OverlayComposeOwner` ✅ 已下移） | 单例 |
| `fingertip` | `ShellCommandBadgeRenderer` | 单例 |
| `share` | `OverlayToastWindow` / `OverlayViewBackHandler`（`OverlayComposeOwner` ✅ 已下移） | 单例 |
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

### A3. 单例接口化（1–2 天）——🟡 进行中

给这批根包单例各开一个窄接口 / 回调，`SlideIndexApp` 里接线（模式同 `OverlayZOrderHooks`）：

- ✅ `OverlayViewBackHandler` → 直接迁入 `:core:overlay-host`（`internal` 改 public）；它依赖的
  `PredictiveBackHelper` 一起下沉到 `:core:common`。
- ✅ `FloatBallOverlay`：`isShowing` / `notifyPanelAttachedAboveChrome` 已有 `OverlayZOrderHooks` 钩子。
- ⬜ `OverlayComposeDialogHost` / `ScreenPinManager` / `OverlayToastWindow`（`share` / `screenshot` 用）。
- ⬜ `LocalFrostedGlassBackdrop`（`volumepanel` / `filebrowser` 用）——它读设置，要拆成"纯背板 + 设置门面"两层。
- ⬜ `ShellCommandBadgeRenderer` / `ShellCommandBadgeOverlay` / `ShortcutBadgeOverlay`（`fingertip` / `quickwheel` 用）。

**验收**：`screenshot` / `backpanel` / `quickwheel` / `animation` / `volumepanel` / `fingertip` / `share` 的外部根包依赖全部归零。

> 2026-11-07 的实测补充：**逐组搬在依赖图上远比预期贵**。`filebrowser` 虽然 A 层文件多，但横向依赖
> clipboard / ocr / history / searchpanel / share / privilege / R / service 等 13 个包；`animation` 依赖
> settings / ui / perf。这两组都**不是**"搬走就行"的叶子。真正能整组搬的，是那些只依赖
> `:core:*` + 少量 settings 枚举的面板；在此之前，A3 的接口化仍是前置。

## 3. 阶段 B：按组搬面板（5–7 天，顺序自由）

底座拆完后顺序不再受依赖限制，按"收益 / 风险"排即可：

| 顺序 | 内容 | 文件 / 行 | 备注 |
| :-- | :--- | ---: | :--- |
| B1 | `filebrowser` | 35 / 9,405 | **不依赖底座，可以提前到任何时间做** |
| B2 | `screenshot` + `backpanel` | 13 / 3,231 | 依赖 A1 + A3 |
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

`:core:overlay-host` 现有 15 个文件 / 1,020 行。

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
| 建议 | **有连续 3–5 天再做阶段 A**；`filebrowser`（B1）可以单独挑出来先做，半天到一天。没有整块时间就放着——它不会自己变好，但也不会坏。 |
