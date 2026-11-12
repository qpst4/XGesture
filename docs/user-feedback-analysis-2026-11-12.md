# 用户反馈分析（2026-11-12）

> 本轮 3 条反馈，**仅分析，未改任何代码**。
> 仓库 1.64.1（versionCode 87，`app/build.gradle.kts:42-43`）；HEAD `060c2864`（分析过程中仓库新增了一条 i18n 提交，工作区干净）。
> 行号以**当前工作区**为准；设置的显示名以 `app/src/main/res/values-zh/strings.xml` 为准。

## 0. 结论速览

| # | 反馈 | 性质 | 现状是否成立 | 改动面 | 优先级 |
| :- | :- | :- | :- | :- | :- |
| 1 | 单元网格宽度调小后 app 名称被下面的图标压住；建议加「是否显示 app 名字」 | 布局缺陷（是真的被裁）+ 缺开关 | **成立**：默认 62dp 就已经卡线，滑块 30dp 起必然坏 | 小（卡片容器 + 一个全局设置项 + 文案，约 6~8 文件；不动 codec） | 高 |
| 2 | 有多个小组件面板时，选动作时能选具体某一页 | 功能缺口（动作无参数，运行期永远第 1 页） | **成立** | 中（core 动作类 + 选择器 + 浮窗/卡片 + 删页重映射 + 单测，约 10~14 文件） | 中高 |
| 3 | 启动面板 ⋮ 里加「从图标库自定义图标」，并支持圆环等其它启动器调用 | 新能力（面板没有图标这一维） | **成立**（面板确实没有 icon；圆环槽位只能画通用图标） | 中（存储 + 4 处图标解析收敛 + 选择器 + 文案） | 中 |

**一句话**：三条都不是「设置藏得深」，而是**数据模型里少了一维**——
#1 是单元格高度没给文字留预算；#2 是 `WidgetPopupOverlay` 这个无参 `data object`，打开时硬编码从第 0 页开始；
#3 是 `QuickLauncherPanel` 根本没有 icon 字段，图标解析链里也没有「面板」这一维。

---

## 1. 反馈 1：单元网格调小后，名字被下面的图标压住

### 1.1 对应哪里（已证实）

- 设置项：设置 → 扩展中心 → 小组件面板 → 页签下方「**单元网格宽度: %1$d dp**」（`widget_panel_cell_width_title`），滑块 `30f..120f`，写 `page.cellWidthDp`：`app/src/main/java/com/slideindex/app/ui/WidgetPanelSettingsScreen.kt:392-402`。
- 面板里的「应用 / 快捷方式 / 动作」条目都由同一个卡片容器画：**44dp 图标 + 4dp 间距 + 11sp 单行名称**（末行省略号）：`app/src/main/java/com/slideindex/app/widget/WidgetCardContainer.kt:140-157`。
- 网格是**正方形**：列宽和行高都取 `cellWidthDp`，`spanY=1` 的卡片就是 `step × step`：
  - `WidgetCanvasLayout.onMeasure`：`contentH = pageRowCount * step`（`app/src/main/java/com/slideindex/app/widget/WidgetCanvasLayout.kt:392-395`）；
  - `onLayout`：`top = item.y * step`（`:420-428`）；
  - `WidgetCardContainer.previewLayoutHeightPx` = `spanY * step`（`:439-442`）；
  - `WidgetPanelLayoutMetrics.compute`：`viewportInnerPx = visibleRows * gridStepPx`（`core/common/src/main/java/com/slideindex/app/widget/WidgetPanelLayoutMetrics.kt:52-53`）。

### 1.2 根因（证据链）

1. 内容需要的高度 ≈ `44 + 4 + 11sp 行高`。11sp 的一行（`includeFontPadding` 默认 true）约 15~16dp，所以**需要 63~64dp**。
2. 默认 `cellWidthDp = 62`（`core/common/src/main/java/com/slideindex/app/widget/WidgetPanelModels.kt:30`），格子高也是 62 —— **默认就已经差 1~2dp**；系统字体放大后（大字号/无障碍）差得更多。
3. 卡片内部那个竖向 `LinearLayout`（`WidgetCardContainer.kt:80-158`）尺寸是 `MATCH_PARENT`（= 格子），默认裁剪；`WidgetCardContainer` 自己虽然 `clipChildren=false`（`:69-72`，为缩放把手/删除角标让路），但**内部这一层仍然裁**。
4. 于是名称要么被 `AT_MOST 剩余高度` 量成不足一行（字被拦腰截断），要么被摆到边界外再裁掉；而下一行格子的图标正好从这条边界开始（格间距为 0，卡片按 `x*step / y*step` 紧挨着排，`WidgetCanvasLayout.kt:425-427`）——肉眼就是「被下面的图标挡住」。

### 1.3 数值（估算，按 11sp 行高 15dp）

| `cellWidthDp` | 文字可用高度 | 观感 |
| :- | :- | :- |
| 62（默认） | ≈ -1 ~ 0 | 刚好卡线，字体稍大就裁掉 1~2dp |
| 55 | ≈ 7 | 只露字的上半截 |
| 48 | ≈ 0 | 名称整行看不见 |
| 30（滑块下限） | 负 | 图标本身都可能顶出格子 |

> 注：这是「一格 = 一个图标 + 一行字」的必然结果，不是某个 OEM 的字体差异。

### 1.4 方案对比

| 方案 | 做法 | 优点 | 代价 / 风险 |
| :- | :- | :- | :- |
| **A 推荐** | ① 加全局开关「显示图标名称」；② 显示名称时按格子实测高度**动态缩图标**（`icon = step - 4 - 行高`，下限 24dp），算不下就自动隐藏文字 | 一处同时治「裁切」和「只想看图标」；默认观感几乎不变（62dp 时图标仍约 43~44dp）；不动网格几何、不动 codec | 卡片要在尺寸确定后重排一次（`onSizeChanged`/`refreshWidgetLayout`）；新设置项要过 6 处管线 |
| B 只加开关 | 关掉文字就不裁，开着照旧裁 | 最小 | 用户把开关打开仍然坏 —— 等于把缺陷包装成设置 |
| C 行高独立 | 让格子「宽 × 更高」，行高 = 列宽 + 文字区 | 根治 | 动 `WidgetPanelLayoutMetrics` / `WidgetCanvasLayout` / 卡片尺寸 / 现有 `WidgetPanelLayoutMetricsTest`，牵一发动全身 |
| D 只改默认值 | 默认 `cellWidthDp` 提到 66 | 一行 | 滑块 30~120 照样能滑坏，治标 |

### 1.5 推荐 A 的落地细节

**设置侧**（照 `quick_launcher_show_labels` 那条链抄，`feature/settings`）：

1. `WidgetPanelSettings`（`feature/settings/src/main/java/com/slideindex/app/settings/AppSettingsSlices.kt:644-654`）加 `widgetPanelShowLabels: Boolean = true`。
2. `SettingsPreferenceKeys` 加 `booleanPreferencesKey("widget_panel_show_labels")`（与 `:320-326` 的 `widget_panel_*` 放一起，备份前缀自动归类）。
3. `SettingsSnapshotReader`（`:319-326` 附近）读入；`OverlaySettingsMutator` 加 `setWidgetPanelShowLabels`；`SettingsRepository` 透传。
4. `WidgetPanelUiState`（`app/src/main/java/com/slideindex/app/ui/viewmodel/WidgetPanelEditorViewModel.kt:19-30`；另有一处构造在 `WidgetPanelSettingsScreen.kt:120-126`，编译器会帮你找全）加字段；设置页在「模糊」卡与「点击行为」卡之间插一张 `groupedCardItems`（`WidgetPanelSettingsScreen.kt:194-236` 同款），用 `SettingExpandableSwitchRow`。
5. 文案照 `quick_launcher_show_labels` / `quick_launcher_show_labels_desc`（中文：*显示图标名称 / 关闭后面板上只显示图标；应用名称仍用于无障碍朗读*）。

**渲染侧**（`WidgetCardContainer`）：

1. 新增 `applyCellMetrics(stepPx: Int, showLabels: Boolean)`：由 `WidgetCanvasLayout.onMeasure/onLayout` 把 `currentGridStepPx` 传进来（或让卡片从自身 `measuredWidth / spanX` 反推），在 `onSizeChanged` 与 `refreshWidgetLayout` 里调用。
2. 用 `labelView.paint.fontMetrics` 实测行高（**不要写死 15dp**，系统字号会变）：
   `icon = (step - gap - lineHeight).coerceIn(24dp, 44dp)`；`icon < 28dp` 或 `!showLabels` → `labelView.visibility = GONE`，图标按 `min(44dp, step - 4dp)` 垂直居中。
3. 隐藏文字时给图标/`appView` 设 `contentDescription = item.label`，否则无障碍就只剩一个无名图标（快速启动器那边也是这么处理的，见 `QuickLauncherDisplaySettings.showLabels` 的注释，`feature/settings/.../QuickLauncherDisplaySettings.kt:10-17`）。
4. 图标位图缓存尺寸（`WidgetAppIconCache`，132px）不用改，缩的只是绘制尺寸。

### 1.6 风险与注意点

- **默认观感**：62dp 会从「44dp 图标 + 被裁 1dp 的字」变成「43dp 图标 + 完整字」，肉眼几乎无差。
- **不要在 `onMeasure` 里改子 View 的 LayoutParams**（会触发二次布局甚至抖动）；放在 `onSizeChanged` + `post` 或 `refreshWidgetLayout` 里。
- 卡片容器本身 `clipChildren=false`（缩放把手要在格外画），别顺手改成 true，否则缩放手势会坏。
- 该文件 2 空格缩进、`WidgetPanelSettingsScreen.kt` 也是 2 空格；仓库其它 UI 多为 4 空格 —— 按文件现有风格写。
- 编辑态缩放手势只读 `spanX/spanY` 与 `step`，本次不动这两个契约。

---

## 2. 反馈 2：多个小组件面板时，动作里能选具体某一页

### 2.1 现状（已证实）

- 「小组件面板」动作 `GestureAction.WidgetPopupOverlay` 是 **`data object`**，`payload` 恒为 `""`：`core/gesture/src/main/java/com/slideindex/app/gesture/GestureAction.kt:725-729`；解码同样是一个单例：`:1299`。
- 打开入口 `WidgetPopupOverlayWindow.show(context, settings, side, anchorRawY)` **没有页参数**（`app/src/main/java/com/slideindex/app/overlay/WidgetPopupOverlayWindow.kt:80-134`）。
- 卡片 `currentPageIndex` 从 0 起（`app/src/main/java/com/slideindex/app/widget/WidgetPopupView.kt:77`），初始尺寸也按第 0 页算（`WidgetPopupOverlayWindow.kt:127-134` 的 `effectivePages.firstOrNull()`）。
- 结果：同一个动作绑在侧滑、悬浮球、圆环、伸展面板、快速启动器条目里，**全都落在第 1 页**，用户只能横滑翻页（`PagerSnapHelper` + 圆点指示器，`WidgetPopupView.kt:150-185`）。

### 2.2 仓库里已经有一套同构的先例（可照抄）

- 动作带面板 id：`GestureAction.QuickLauncher(panelId, pinned, pinCurrentApp)`，`panelId` 空串 = 第一个面板（`GestureAction.kt:405-444`）。
- 选择页：`GestureQuickLauncherActionScreen`（面板单选列表 + 对勾），从动作网格点进去（`app/src/main/java/com/slideindex/app/ui/GestureActionPickerScreen.kt:416-440`，入口在 `GestureActionPickerLazyItems.kt:264-266`）。
- 已绑动作改面板：侧滑 / 悬浮球 / 圆环槽位各有「选择面板」行（`SideGestureSlotConfigScreen.kt:236-284`、`FloatBallGestureSettingsScreen.kt:119-127`、`RingLauncherSlotConfigSheet.kt:446-488`）。
- 归一化与删面板重映射：`QuickLauncherPanelMutator`（`normalizeQuickLauncherAction` / `sanitize*`）+ `OverlaySettingsMutator.setQuickLauncherPanels` 在删除时对**全库 `String` / `Set<String>`** 做精确改写（`feature/settings/.../OverlaySettingsMutator.kt:449-466`、`:475-506`），并有 `QuickLauncherPanelNormalizeTest` / `QuickLauncherPanelRemovalRemapTest` 两个单测兜底。
- 为什么必须重映射：动作里存的是面板 id，面板删了以后如果只是「运行期回落到第一个」，用户改顺序/删面板后行为会静默漂移 —— 这条注释写在 `QuickLauncherPanelMutator.kt:97-102`，小组件面板是同一个坑。

### 2.3 方案对比

| 方案 | 做法 | 优点 | 代价 / 风险 |
| :- | :- | :- | :- |
| **A 推荐** | `WidgetPopupOverlay` 改成 `data class`（`panelId: String = ""`，payload = 页 id）；绑动作时弹页列表 | 与快速启动器**完全同构**；每个绑定独立；已有归一化/重映射/单测模板 | object→class 牵动 5 处引用（+ 声明本身）；要补删页重映射 |
| B 全局「默认页」设置 | 设置里选「每次打开停在哪页」 | 改动最小 | 所有绑定共用一页 —— 满足不了「这个手势开第 2 页、那个开第 3 页」 |
| C 记住上次页 | 运行期记忆最后停留页 | 不用配置 | 不可预测，两个手势会互相串 |

### 2.4 推荐 A 的落地细节

1. **动作**（`core/gesture`）：
   - 改成 `data class WidgetPopupOverlay(val panelId: String = "")`，`override val payload = panelId`；
   - `from(type, payload)` 分支 `:1299` 改为 `WidgetPopupOverlay(payload)`；
   - 5 处使用点改用 `is` / 构造（另加声明本身）：`GestureAction.kt:1299`（decode）、`ActionExecutor.kt:317`、`GestureSessionActionDispatch.kt:473`、`GestureActionPickerSearch.kt:961`、`GestureActionCatalog.kt:363`；`GestureActionPickerLazyItems` 的点击分支按 `type` 路由，不用改。
   - 顺手加 `actionHasDetailParams(WIDGET_POPUP_OVERLAY)`（`GestureActionPickerLazyItems.kt:60-71`），让格子显示「有参数」小圆点。
2. **选择器**（`:app`）：页数 ≤ 1 直接选中（保持现在一键完成的体验）；> 1 弹一个页列表（照 `MacroPickDialog` 的弹窗形态，或新建 `WidgetPanelPagePickDialog`，每行：页名（空则「第 N 页」）+ `列×行 / N 个条目`）。动作列表里的副标题用 `gesture_action_widget_popup_overlay_named`（新增：「小组件面板 · %1$s」），照 `gesture_action_quick_launcher_named`（`GestureActionPickerSearch.kt:505-514`）。
3. **已绑后改页**：侧滑 / 悬浮球 / 圆环 / 伸展面板的槽位配置里加一行「打开页」，与 QuickLauncher 的面板行同位置、同交互。
4. **运行期**（`:app`）：
   - `WidgetPopupOverlayWindow.show(..., initialPageId: String? = null)`；用 `effectivePages.indexOfFirst { it.id.toString() == initialPageId }`，取不到就 0；
   - **初始卡片尺寸与上边距必须按目标页算**（现在 `initialBox` 取 `firstOrNull()`，`:127-134`），否则会先按第 1 页开、再跳过去；
   - `WidgetPopupCardLayout` 增加 `initialPageIndex`：`init` 里先把 `currentPageIndex` 置好、`updateDots()`、`applyPageMetrics(pages[index])`，再 `recyclerView.scrollToPosition(index)`（`WidgetPopupView.kt:161-177`、`:216-219`）。
5. **删页重映射**（`feature/settings`）：`OverlaySettingsMutator.setWidgetPanelPages`（`:838-842`）里比较「写前 / 写后」的 id 集合，删掉的 id → 存活第一页；沿用 QL 的做法遍历所有 `String` / `Set<String>` 精确改写，needle 用 `<typeId>\u001C<pageId>` 与 `<typeId>\u001F<pageId>` 两种形态（动作负载 / 手势规则相邻字段），并做「id 后一位不能还是数字」的边界判断（页 id 是 Long，`"1"` 撞 `"12"` 的概率比 UUID 高）。`removedIds` 为空要早退 —— 这个 mutator 在拖滑块、改列数时也会被调用。
6. **历史数据**：payload 为空 = 第一页（与现状一致）；首次写入时归一化成具体 id（照 `QuickLauncherPanelMutator.normalizeQuickLauncherAction`）。
7. **单测**：`WidgetPanelPageNormalizeTest`、`WidgetPanelPageRemovalRemapTest`（照 `core/gesture/src/test/.../QuickLauncherPanelNormalizeTest.kt`、`feature/settings/src/test/.../QuickLauncherPanelRemovalRemapTest.kt`），外加 codec 往返（空 payload → 第 1 页）。

### 2.5 风险与注意点

- **object → class 是行为变化**：所有 `== GestureAction.WidgetPopupOverlay` / `in setOf(...)` 的等价比较都会失效（编译器能抓到大部分，抓不到的是字符串/反射路径）。仓库里没有针对该动作的既有单测，需要新加 codec 往返。
- **首帧**：RecyclerView 的 `scrollToPosition` 必须发生在卡片尺寸按目标页算好之后，否则会看到「先按第 1 页开、再跳」的闪动。
- **page id 会复用**：`WidgetPanelMutator.addPage` 用 `max(id)+1`（`app/src/main/java/com/slideindex/app/widget/WidgetPanelMutator.kt:7-12`），删掉最大页再新增会拿到同一个 id。删页时已把引用改写掉，所以只有「旧备份 + 新页」才会撞上；要接受这个极小概率，或在页 id 上加单调计数器（会动 codec，不建议本轮做）。
- **全库字符串改写**：QL 那套是按「动作类型 id + 分隔符 + 面板 id」精确匹配（`QuickLauncherItemCodec.remapPanelReferences`，`core/gesture/.../QuickLauncherItem.kt:253-314`），小组件面板要写一份同构的，不要直接复用（类型 id 不同）。
- 打开目标页后仍然可以横滑到其它页 —— 如果用户期望「锁定」，那是另一个需求（见 §5）。

---

## 3. 反馈 3：启动面板 ⋮ 增加「从图标库自定义图标」，供圆环等启动器调用

### 3.1 先定位「三点菜单」（已证实）

设置 → 扩展中心 → 快速启动器 → 卡片标题行（页签 + 面板名 + 「+」+ 「⋮」）右侧的 ⋮：
`app/src/main/java/com/slideindex/app/ui/quicklauncher/QuickLauncherPanelManagementSection.kt:170-194`；
菜单项目前是 **重命名 / 复制 / 删除**（`:80-128`）。中文节标题 `quick_launcher_panels_section` = **「启动面板」**，与反馈用词完全对上。

### 3.2 现状（已证实）

- `QuickLauncherPanel(id, name, columnsPerPage, rowsPerPage, items)` —— **没有图标字段**：`core/gesture/src/main/java/com/slideindex/app/launcher/QuickLauncherPanel.kt:5-11`。
- 圆环槽位绑「打开快速启动器」时，槽位图标走：
  `HoneycombTargetResolver`（`feature/overlay-panels/.../HoneycombTargetResolver.kt:35-54`）
  → `PanelRuntimeHooks.quickLauncherIconDrawable`（`:app` 实现见 `SlideIndexApp.kt:983-992`）
  → `QuickLauncherIconResolver.iconDrawable`（`app/src/main/java/com/slideindex/app/util/QuickLauncherIconResolver.kt:141-161`）
  → `ActionIconBitmap.load`（`app/src/main/java/com/slideindex/app/ui/ActionIconBitmap.kt:22-36`）
  → 没有覆盖时落到「快速启动器」这个**动作类型**的默认矢量图标。
- 图标库（扩展 → 图标库）现在只有两维：**动作类型**、**应用包名**（`app/src/main/java/com/slideindex/app/ui/GlobalIconStore.kt:61-100`），没有「某个面板」。
- 所以最多 12 个面板在圆环 / 蜂窝 / 角落轮盘 / 快速启动器条目里看起来完全一样，只能靠名字区分 —— 这正是反馈说的痛点。

### 3.3 方案对比

| 方案 | 存储 | 优点 | 代价 / 风险 |
| :- | :- | :- | :- |
| **A 推荐** | `GlobalIconStore` 加 `panel:<panelId>` 前缀（同一个 prefs） | 不动面板载荷/codec；`revision` + 监听链（重绘）现成；整个 prefs 已在备份范围内 | 解析链要认「面板」这一维（见下） |
| B 塞进 `QuickLauncherPanel.iconKey` | 面板 codec | 语义上「面板自己的属性」 | `QuickLauncherPanelCodec` 是 4 个 header 字段之后**直接是 items blob**（`QuickLauncherPanel.kt:62-100`），加第 5 个字段会让老数据把第一个 item 当 header 解析 —— 得加判别式，风险明显大于收益 |
| C 只给动作类型设图标 | 已有 | 零改动 | 所有面板共用一个图标，等于没做 |

### 3.4 推荐 A 的落地细节

1. **存储**（`GlobalIconStore`，`:61-100`）：
   - `panelIcon(context, panelId)` / `setPanelIcon(context, panelId, key)`，前缀 `panel:`；
   - `notifyIconChanged()`（`:38-46`）已经会 `AppIconOverride.clear()` + 清 `QuickLauncherIconResolver` / `RingLauncherSlotIconBitmap` / `CornerSlotIconBitmap` / `QuickWheelIconResolver` 并发监听，面板图标可以直接享受；
   - 相册/应用图标落盘用现成的 `ActionIconStorage.saveFromUriForGlobalKey(context, "global:panel:<id>", uri)`（`app/src/main/java/com/slideindex/app/ui/ActionIconStorage.kt:79-115`）。
2. **入口**（`QuickLauncherPanelManagementSection`）：
   - ⋮ 里加 `DropdownItem("自定义图标")`；
   - 弹一个 `PanelIconPickerDialog`：图标预览 + 「从图标库选择 / 文字图标 / 应用图标 / 相册 / 恢复默认」。菜单结构可直接复用 `ActionIconPickerHost`（`app/src/main/java/com/slideindex/app/ui/ActionIconPickers.kt:150-285`）—— 前提是给它加一个可选 `storageKey` 参数（现在相册落盘按 `action` 身份算文件名，`:179`），这个泛化对两边都有好处。
   - 图标库整页选择器已经是独立 Activity（`IconLibraryPickerTrampolineActivity`，`:163-173`），不用新写页面。
3. **解析**：把「面板图标 → 动作类型图标 → 应用图标」收敛成**一个入口**，而不是复制 4 份。现在同一段 `actionIcon ?: appIcon` 至少出现在：
   - `ActionIconBitmap.load`（Canvas：圆环 / 蜂窝 / 角落轮盘 / 快速启动器条目，`ActionIconBitmap.kt:30-34`）；
   - `ActionIconSlot`（Compose：快速轮盘、动作列表、槽位预览，`app/src/main/java/com/slideindex/app/ui/ActionIconSlot.kt:44-49`）；
   - `GestureSlotActionIcon`（Compose：侧滑 / 悬浮球 / FV 槽位行，`app/src/main/java/com/slideindex/app/ui/GestureSlotActionIcon.kt:34-40`）；
   - `SlideIndexApp.globalIconKey`（面板模块拿不到 action 时的兜底，`SlideIndexApp.kt:928-932`）。
   新增 `ActionIconKeyResolver.resolve(context, action): String?`（面板 → 动作类型 → 应用），前 3 个（Compose/Canvas）改成调它；`globalIconKey` 拿不到 action，保持「只有动作类型/应用」不动。
4. **显示面**：改完上面这一处，圆环 / 蜂窝 / 角落轮盘 / 快速启动器条目（比如面板里放一个「打开另一个面板」的动作）都会自动显示面板图标；面板页签/名称行要不要也画图标是另一个可选点（用 `GlobalIconRow` 同款渲染，`GlobalIconLibraryScreen.kt:198-491`）。
5. **优先级要明确**：目前角落轮盘**先**查 `globalIconKey(action.type.name)`（动作类型级），没命中才退到 `actionIconBitmapFull`（`CornerSlotIconBitmap.kt:42-62`、`:118-126`）。也就是说：如果用户在图标库里给「快速启动器」这个类型设了图标，它会盖住面板图标。要么把面板判断提前（改 `feature/overlay-panels` 的顺序），要么接受「类型图标优先」并写进说明 —— **必须选一个**，否则同一个人在不同入口看到不同图标。
6. **缓存**：`QuickLauncherIconResolver` 的 key 已带 `GlobalIconStore.revision`（`:62`）；`RingLauncherSlotIconBitmap` 带 `iconCacheRevision`（`:35`）；`CornerSlotIconBitmap` 靠 `notifyIconChanged` 清（`:40-46`）。**不要**把面板图标塞进 `GestureActionIconBitmap.get`：它的缓存 key 只有 `type/payload/尺寸/…`（`app/src/main/java/com/slideindex/app/util/GestureActionIconBitmap.kt:89-97`），换图标不会失效。
7. **删除 / 复制面板**：删面板时顺手清 `panel:<id>`（或留着，靠 id 不复用也无害）；复制面板建议不复制图标（新 id 自然没有图标）。

### 3.5 风险与注意点

- **覆盖面**：圆环 / 蜂窝 / 角落轮盘 / 快速启动器条目能覆盖；快速轮盘槽位如果显式选了图标来源（图标库 / 应用图标 / 相册），会按**槽位自己的来源**优先，面板图标不参与（`QuickWheelIconResolver` + `QuickWheelContainer.kt:65` 的解析顺序注释）—— 需确认是否符合预期。
- **备份**：`global_icon_overrides` 整个 prefs 会导出/导入（`app/src/main/java/com/slideindex/app/cloudbackup/SettingsBackupExternalStores.kt:46, 87`），`panel:` 前缀自动包含，无需新写编解码。
- **面板 id 稳定性**：默认面板 id 是 `"default"`，新建/复制是 UUID（`QuickLauncherPanel.kt:15`、`QuickLauncherPanelMutator.kt:20`）；删了再建是新 id，旧图标成孤儿（可接受）。
- **不新增静态钩子**：本方案不需要动 `PanelRuntimeHooks`（那里已经是 121 个 `@Volatile` 槽位的红线，AGENTS §4.1/§4.4），所有解析都在 `:app` 侧完成 —— 这也是推荐 A 而不是「在面板模块里查」的原因。

### 3.6 容易歧义的地方（需你确认）

「从图标库中自定义图标」有两种读法：

1. **面板级**（本文按这个写）：给这个「启动面板」一个图标，圆环/蜂窝等调用它时显示这个图标 —— 与「支持其它启动器调用」这句对得上。
2. **条目级**：给面板里某个 app 换图标。这个能力**已经存在**（扩展 → 图标库 → 应用：按包名全局覆盖；`WidgetAppIconCache` 也走 `AppIconOverride`，`app/src/main/java/com/slideindex/app/widget/WidgetAppIconCache.kt:48-53`），只是没在 ⋮ 里给入口。若你指的是这个，那只需要在 ⋮ 里加一个「跳到图标库」的入口（或按当前面板里出现的应用列出可改项），不需要新存储。

---

## 4. 三条放一起看

- **共同点**：都是「模型缺一维」，而不是 UI 找不到入口。这也解释了为什么用户会一次提三条 —— 三个功能都停在「差一点」的位置。
- **可以合并收益的地方**：
  - #2 与 #3 都要碰「动作/面板身份」，但相互独立，可以并行；
  - #3 的「图标解析收敛成一个 resolver」本身能消掉现有 3~4 处重复的 `actionIcon ?: appIcon`，减少四处口径漂移（例如角落轮盘用「类型图标优先」、快速启动器用「覆盖优先」），属于低风险的结构性收益；
  - 三条都要新增中文文案，建议一轮补齐（仓库有 `Translation Check`，新增 key 要同步 ar/ja/ru，见最近一条提交 `060c2864`）。
- **建议顺序**：
  1. **#1**（改动最小、痛感最直接，且能顺带把「文字自动隐藏」做成 #2/#3 的基础能力）；
  2. **#2**（用户操作路径上的硬阻塞，但改动最大，先做能留出足够的回归时间）；
  3. **#3**（纯增量，且需要先定「优先级」和「覆盖面」两个口径）。
  - 若想先出一个「马上能感觉到」的版本，也可以 #1 → #3 → #2（#3 不依赖 #2，且能让 12 个面板立刻可区分）。

---

## 5. 待你拍板

1. **#1 开关粒度**：全局一个（推荐，和快速启动器一致，且不动 codec），还是逐页各一个？（逐页要动 `WidgetPanelCodec` 的 10 字段 header —— 后面直接是 items blob，加字段要写判别式，属于自找麻烦。）
2. **#1 自动隐藏**：是否接受「格子小到放不下文字时自动只显示图标」（即使开关是开的）？我建议接受，否则开关开着仍然会被裁。
3. **#2 选页后是否锁定**：打开第 N 页后还允许横滑到其它页，还是临时锁在这一页？
4. **#2 单页时**：页数 = 1 时是否还弹选择（我建议直接选中，不加一步）。
5. **#3 是面板级还是条目级图标**（见 §3.6）？若是面板级：面板图标要不要也画在页签/名称行？角落轮盘里「动作类型图标」和「面板图标」谁优先？

---

## 6. 本轮边界

- 只读代码与文档，**没有改任何源码、没有跑构建**；工作区仍为干净状态。
- 文中所有行号基于 HEAD `060c2864`；`values-zh` 的文案名可能因 i18n 提交而移动，但 key 不变。
