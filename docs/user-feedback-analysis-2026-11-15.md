# 用户反馈分析（2026-11-15，共 13 条 / 14 个问题）

> 面向版本：1.66.0（versionCode 88，`app/build.gradle.kts:42-43`）。
> 本文只做分析：给判定、根因线索、改动落点、体量与风险；不含代码改动。
> 体量口径：**S** ≤ 0.5 天、**M** 0.5–2 天、**L** 3–5 天、**XL** > 1 周。

---

## 0. 一句话结论

14 个问题里，**3 个是明确缺陷/回归**（#13a 亮度回弹、#13b 图片拖拽无效、#6 宏录制多录一下），
**4 个是"这条链路上本来就没有"**（#3 创建快捷方式、#4 滤盒开关、#7 录屏自身入镜、#9 锁屏显示），
其余 7 个是参数可配 / 能力扩展。建议先把 5 个小改动（#6、#13b、#4、#8、#10）打包成 1.66.1，
大件（#5、#12、#9）单独排期。

---

## 1. 总表

| # | 反馈摘要 | 判定 | 主要落点 | 体量 | 优先级 |
|---|---|---|---|---|---|
| 1 | 悬浮球能否靠"显示百分比 >100%"实现不贴边 | 产品回退诉求；用户方案技术上成立 | `FloatBallLayout.kt:228`、`FloatBallPositionMode.kt:14` | S | P2 |
| 2 | 任务切换器太宽，希望能自定义 | 宽度硬编码 226dp | `TaskSwitcherLayoutEngine.kt:43` | S | P1 |
| 3 | 应用内直达的"添加快捷方式"页没有"创建快捷方式" | 该页显式清空了创建入口（非本版回归） | `ActivityShortcutPickAppShortcutScreen.kt:56` | S | P1 |
| 4 | 通知滤盒希望可关闭（停止记录） | 无开关；记录无门控 | `NotificationHistoryRecorder.kt:162` | S | P1 |
| 5 | 摇杆松手即点、摇杆不消失、可循环拖动 | 新开关 + 状态机改造 | `FloatingPointerInputHandler.kt:550` | M | P2 |
| 6 | 宏录制把"保存"那一下也录进去 | 捕获层装回后压在控制条之上 | `MacroRecordingTouchCapture.kt:365` | S | **P0** |
| 7 | 录屏把自己的按钮录进画面 | 整屏合成必然包含自家浮层 | `ScreenRecordService.kt:131` | S~M | P1 |
| 8 | 触钮长按时间希望能自定义 | 硬编码 450ms，是唯一的例外 | `SwipePathRecognizer.kt:992` | S | P1 |
| 9 | 侧边气泡希望支持锁屏显示、解锁后稳定 | 熄屏即关 + 无锁屏标志 + 5s 自动关 | `SideBubbleOverlayWindow.kt:79/288` | M | P1 |
| 10 | 翻译按钮长按切换语言 | 按钮无长按；语言包已可枚举 | `PickResultPanelChrome.kt:991/1620` | S | P1 |
| 11 | 图片区加编辑按钮、单击执行指定功能、全屏可缩放旋转 | 后两项部分能力已存在 | `PickResultFullscreenImage.kt:40`、`FloatBallTextPick.kt:531` | S | P1 |
| 12 | 自定义区域放大（区别于系统方案） | 当前无此能力，需新建子系统 | — | XL | P2 |
| 13a | 亮度滑块升上去又退回原亮度（1.50 正常） | 1.66 的修复未覆盖该 ROM | `BrightnessControlHelper.kt:200` | S（定位后） | **P0** |
| 13b | 图片全局拖放对任何 App 都没反应（文字正常） | 起拖前同步镜像：失败则不起拖，成功也可能卡住 | `ClipboardDragClipHelper.kt:36` | S~M | **P0** |

---

## 2. 逐条分析

### #1 悬浮球"不贴边 + 调水平位置"

**用户猜测是对的**：把"显示百分比"继续加大，确实能实现水平内缩。

- 布局公式本身就是通用的：`FloatBallLayout.dockedBallLeftPx()` 里
  `LEFT = -w*(1-f)`、`RIGHT = W - w*f`；当 `f > 1` 时球会整体向内偏，数学上等价于"距边缘内缩"。
  （`app/src/main/java/com/slideindex/app/overlay/FloatBallLayout.kt:228-236`）
- 唯一的闸门是取值范围：`MIN_VISIBLE = 0.5f`、`MAX_VISIBLE = 1f`，
  滑条范围也直接用这两个常量（`feature/settings/.../FloatBallPositionFractions.kt:4-7`、
  `app/.../ui/FloatBallAppearanceSettingsScreen.kt:31-32,247-269`）。

**但这不是新需求，是删掉的能力**：2026-07-19 的提交 `778af3a9` 明确写了
"Replace horizontal position with a visible-fraction slider"，
同一提交把 `CUSTOM` 从 `selectable` 里摘掉，并让 `fromStorageKey(CUSTOM)` 回落成 `RIGHT`
（`FloatBallPositionMode.kt:14,18`）。所以老用户的"水平位置"是被这个滑条替换掉的，现在用户想拿回来。

**两条实现路线：**

| 方案 | 做法 | 优点 | 代价/风险 |
|---|---|---|---|
| A（用户建议） | `MAX_VISIBLE` 放宽到 1.5–2.5 + 滑条 + 文案 | 改动最小，只碰 2 处布局 + 滑条 + 文案 | "百分比"语义被复用（>100% 时不再是"露出比例"）；双贴边模式下球会离线，观感要另调 |
| B（推荐） | 新增"距边缘距离（dp）"，`dockedBallLeftPx` 加 `insetPx` 参数 | 语义清晰、和"露出比例"解耦、易做预览 | 新增设置项 + 预览 + 迁移，工作量比 A 大一点 |

两个方案共同要处理的：`FloatBallAppearancePreview` 的 clamp 走的是同一个 `coerceVisibleFraction`，
放宽后要确认预览不会把值夹回去；球离开边缘后，"贴边隐藏/闲置隐身"的语义要重新说明。

**体量**：S。**建议**：先跟用户确认要 A 还是 B（这是产品语义，不适合替他决定）。

---

### #2 任务切换器宽度可自定义

- 现状：面板宽度写死 `host.dp(226f)`，只做了"不超过整屏减去左右边距"的保护
  （`core/overlay-layout/.../TaskSwitcherLayoutEngine.kt:43-49`）。
- 好消息：行高、关闭列、图标列、命中矩形**全部从 `panelRect` 派生**
  （`TaskSwitcherLayoutEngine.kt:110-133,137-208`），所以只要改这一个宽度，整块会等比跟着走，不需要改渲染。
- 落点：
  1. `OverlayPanelLayoutHost` 加 `fun panelWidthDp(): Float = 226f`（接口默认值，避免动全部实现，
     `core/overlay-layout/.../OverlayPanelLayoutHost.kt:7-13`）；
  2. TaskSwitcher 的 host 实现从设置读；
  3. 设置页放在"扩展 / 浮层面板"一类入口（现在没有任务切换器的独立设置页），滑条 180–340dp 带实时预览；
  4. 现有先例可抄：暂存面板的 `StashPanelWidthMode`（档位 + 拖动改宽）已经是成熟做法。
- 要一起改的测试：`TaskSwitcherLayoutEngineTest` 里写死的 `rightHost.dp(226f)` 断言要参数化。
- **体量**：S。

---

### #3 应用内直达缺"创建快捷方式"

**不是这一版弄丢的，是这条路径从来没做**：

- `ActivityShortcutPickAppShortcutScreen` 把 catalog 的创建入口**显式清空**：
  `FilteredShortcutCatalog(createHosts = emptyList(), ...)`（`:56-58`），
  并且 `onCreateHostClick = {}`（`:86`、`:107`）。提交 `637f6362` 重构时也是"只列启动类快捷方式"。
- 而"创建快捷方式"在其他三个挑选器里都有：`systemShortcutCatalogItems` 的
  `ShortcutCreateSection`（`app/.../ui/picker/SystemShortcutCatalogPicker.kt:126-186,256-265`），
  入口是手势动作、快速启动器、圆环。
- 能力已经齐了：`CreateShortcutTrampoline.launch()` 负责隐藏浮层 → 起宿主 Activity → 回传结果
  （`app/.../service/CreateShortcutTrampolineActivity.kt:140-159`）；
  回传的 `CreatedShortcut` 能直接映射成 `ActivityShortcut.dynamic/intent`
  （`core/common/.../ActivityShortcut.kt:14-52`、`core/shortcuts/.../ActivityShortcutMappings.kt:120-152`）。
- 实现就是把 `createHosts` 传下去 + 回调走 trampoline + 去重（`identityKey()`）+ 失败提示。
- **体量**：S（顺手做；不给用户"这页少了什么"的错觉，最好同时给这页加一句空态说明）。

---

### #4 通知滤盒支持关闭（停止记录）

- 现状：`NotificationHistoryRecorder.onPosted()` 无条件写库（`:162` `historyRepository.record(item)`），
  设置里只有"按应用分组 / 最大条数 50–2000"（`app/.../ui/NotificationSettingsLazyItems.kt:120-148`），
  没有"记录开关"。
- **关键约束（最容易踩的坑）**：这个 recorder 同时承担
  **通知过滤规则**（`findMatchingRules` / `hideFromShadeOnMain`）和**验证码提取**（OTP，`:103-144`）。
  如果在 `onPosted` 开头直接 return，会连带把过滤和 OTP 一起停掉。
  正确做法是只门控记录本身：
  1. `historyRepository.record(item)`；
  2. `onRemoved()` 里的 capture 回填（`:173-191`）。
- 建议：`notificationHistoryEnabled`（默认 true）；
  关闭时把"最大条数"滑条置灰并说明"过滤规则仍然生效，只是不再留记录"；
  可选"暂停 1 小时"满足"临时不想记"的诉求（比一刀切更贴合"停止记录"的语义）。
- **体量**：S（settings slice + prefs 读取 + recorder 两处 + UI + 一个单测）。

---

### #5 摇杆"松手即点、摇杆不消失、可继续拖"

用户描述得没错，现在的开关语义确实不同：

- 现有 `floatingPointerReleaseClickAndDismiss` 只在**边缘触钮接手的会话**里生效：
  `handleContinuedEdgeUp()` → `performPointerClickAndDismiss()`（点完就 `onDismiss`），
  摇杆随之消失（`app/.../overlay/FloatingPointerInputHandler.kt:550-581,599-609`）。
- 用户要的是"点完留在原地、下一次按下继续拖、循环"。

**实现要点（不是加一行判断就能完成的）：**

1. 新增独立开关（不要改现有 `releaseClickAndDismiss` 的语义，两个模式要能共存）；
2. `handleContinuedEdgeUp`：改走 `performPointerClick()`（`:583-597`，不 dismiss），
   保持 `continuedEdgeSessionActive` / `joystickActive` / 指针可见；
3. **普通路径的 ACTION_UP**（`:391-495`）也要支持：`movedBeyondTap()` 时在指针处点击，
   但不 reset 到 rest 位置、不清会话——否则"第二次拖动"就没有入口；
4. 下一次 DOWN 的锚点要用"当前摇杆位置"而不是 rest，否则第二次拖动会跳；
5. 该模式下要屏蔽 `floatingPointerHideWhenJoystickReleased` 与 quick-swipe dismiss；
6. **必须定义退出条件**（点摇杆外 / 返回键 / 闲置超时），否则摇杆会永远挂在屏幕上。

**风险**：`FloatingPointerInputHandler` 是 688 行的状态机，牵扯边缘交接、全屏触摸捕获窗、注入回声；
建议先给它补"UP → 保持 → 再 DOWN"的状态机单测，再动实现。
**体量**：M。**优先级**：P2（放 1.67，不要和热修混）。

---

### #6 宏录制把"保存"那一下录进去（P0，根因已定位）

**根因（高置信）**：捕获层的窗口 z-order 在第一次录制动作之后被反转了。

- 装配顺序：先 `captureLayer.show()`（全屏捕获层），再 `recordOverlay.show()`（196dp 小控制条）——
  同类型窗口后加者在上，所以**一开始**控制条在捕获层之上
  （`app/.../macro/MacroRecordingInstaller.kt:144-147`、`MacroRecordOverlay.kt:145-166`）。
- 但捕获层为了注入回放，每次都会 `removeView` 摘掉、注入完再 `addView` 装回
  （`MacroRecordingTouchCapture.kt:355-376`）。**重新 add 之后它回到了最上层**，
  从此压在控制条上面。
- 结果：之后点"暂停 / 停止 / 保存"，DOWN 先被全屏捕获层吃掉 → 被录成一个 tap
  （`ACTION_UP` 里 `requestForward`，`:209-215`）→ 注入时捕获层已摘掉，
  注入的那一下正好点中按钮 → **按钮照样生效，但动作被录进去了**。
- 这解释了为什么总是"保存的最后一下"：保存前必然已经录过至少一个动作，
  而第一次录制动作之后 z-order 就已经反了。

**修复：**

1. 主修：注入结束装回捕获层后，把控制条重新置顶（remove+add，或由 installer 提供 `bringToFront()` 回调）；
   或让控制条用更高一层的窗口类型（注意注释里写过的 Flyme 给无障碍浮层叠 80% alpha 的坑）。
2. 兜底：保存前丢弃"落在控制条矩形内"的末尾 tap（按坐标判定比按时间可靠）。
3. 顺手：`MacroRecordOverlay.active` / `activeRecorder`（`:485-489`）目前零引用，
   是上一代无障碍捕获方案留下的死钩子，可一并删。
4. 单测：给 `MacroRecorder` 加"末尾 tap 落在 bar 矩形内时被丢弃"的 case。

**体量**：S（主修 10 行以内 + 兜底 + 测试）。

---

### #7 录屏把自己的按钮录进去

- 事实：`createVirtualDisplay(..., AUTO_MIRROR, ...)` 抓的是整屏合成
  （`app/.../service/ScreenRecordService.kt:131-140`），`RecordingDock` 是普通 overlay
  （`app/.../screencapture/RecordingDock.kt:85-98`）——**必然入镜**。
- Android 没有"从全屏录制里排除某个窗口"的公开 API。可选手段与代价：

| 手段 | 效果 | 代价 |
|---|---|---|
| **录制时收起 dock**（推荐默认） | 画面干净 | 停止要另找入口（通知按钮 / 磁贴 / 手势） |
| 14+ 单应用投屏（`MediaProjectionConfig.createConfigForUserChoice()`） | 只录目标 App，浮层天然不入镜 | 不是全屏录制；仅 14+ |
| `FLAG_SECURE` 给 dock | 录制里变成黑块 | 观感更差，还可能影响截图 |
| 区域录制（避开 dock 区域） | 画面干净 | 等于换功能 |
| 延迟启动（倒计时藏 dock） | 治标 | 之后仍然入镜 |

- 建议：默认"开始录制 1.2s 后自动收起 dock"，停止入口用前台通知按钮（通知本身不在屏幕上，不会被录），
  设置里保留"常显 dock"的旧行为；14+ 再补单应用模式。
- **体量**：S（收起 + 停止入口）/ M（单应用模式）。

---

### #8 触钮长按时间可自定义

- 现状：触钮（边缘触发条）的长按判定是硬编码 `SwipePathRecognizer.LONG_PRESS_MS = 450L`
  （`core/gesture/.../SwipePathRecognizer.kt:992`），
  同时用于 `GestureSession.onTouchDown` 的定时器（`GestureSession.kt:378`）和
  `refreshLongPress()` 的内部判定（`SwipePathRecognizer.kt:251-260`）。
- 项目里其它长按**都已经可配**：`effectiveLongPressDurationMs()`（250–900）、
  摇杆径向（200–2000）、快捷轮盘（200–1500），只有触钮没有——用户提这个很合理。
- 实现：新增 `edgeLongPressMs`（200–1200，默认 450），注入 recognizer，两处共用同一个值；
  无迁移（新 key，默认值等于现值）。
- 粒度选择：
  - **全局**（推荐先做）：一个 key，放"手势 / 触钮"设置页；
  - **按触钮**（和现状一致）：`TriggerHandle` 已经有 per-handle 的 `shortSwipeDistanceDp` /
    `longSwipeDistanceDp`，设置页在 `TriggerAppearanceSettingsScreen`；
    但加字段要动 `TriggerHandle` 编解码 + `GestureProfile` 存档 + 备份兼容
    （`GestureProfileCodecTest` 里有"编解码逐字节一致"的断言），成本和回归面都大不少。
  建议 1.66.1 先上全局，按触钮的粒度等有人提再说。
- 注意：`GestureSessionThresholdTrackerTest` 直接用常量推进时钟，测试要改成注入值。
- **体量**：S（全局）/ M（按触钮）。

---

### #9 侧边气泡锁屏显示 + 解锁后稳定

- 现状三连：
  1. 窗口 flags 里没有任何锁屏相关位（`MessageOverlayLayout.buildSideBubbleLayoutParams`，`:90-105`）；
  2. **熄屏即 dismiss**：`screenOffDismissReceiver = ScreenOffDismissReceiver { dismiss() }`
     （`SideBubbleOverlayWindow.kt:79`，在 `ensureWindow` 注册，`:265`）——锁屏的第一步就是熄屏，
     所以"锁屏显示"必然不成立；
  3. 默认 5 秒自动关闭（`MessageSettings.sideBubbleAutoDismissSeconds`，`scheduleAutoDismiss()` `:288-295`）——
     就算窗口留着，解锁后气泡也早没了。
- 需求拆成三件事：
  1. 开关"锁屏时也显示气泡"；
  2. 开启时：不响应 screen off dismiss；窗口补 `FLAG_SHOW_WHEN_LOCKED`
     （对 overlay 是否生效依 ROM，需真机验证；不生效就退到"监听 `ACTION_USER_PRESENT` 重挂 + 重排"）；
  3. **锁屏期间暂停自动关闭计时，解锁后重新计时**（否则等于没显示）。
     解锁后再跑一次 `updateWindowPlacement`，避免位置/宽度按锁屏坐标系算错。
- 可对齐的现成能力：`pendingUnlockMessage` + `replayFloatIconAfterUnlock()`
  （`feature/message/.../MessageReminderOrchestrator.kt:161-188`）——
  现在只有"悬浮球图标"有解锁后补显，气泡没有；建议把"补显"做成通用能力。
- **体量**：M（跨 screen-off / user-present 生命周期 + 计时暂停 + 真机兼容）。

---

### #10 翻译按钮长按切换语言

- 现状：翻译按钮只有 `onClick`，没有长按：
  图标底栏 `PickResultPanelChrome.kt:991-1004`；紧凑底栏用的是裸 `IconButton`（`:1620-1634`）。
  而"分享"按钮已经用了 `onLongClick`（`:1013`），说明组件层支持，改造成本低。
- 数据已就绪：
  - 目标语言：`floatBallTranslateTargetLang`（默认 `FOLLOW_APP`，`AppSettingsSlices.kt:413`）；
  - 已下载语言包可枚举：`TranslateModelRepository.installedLanguageCodes()`
    （`core/translate/.../TranslateModelRepository.kt:29-34`）；
  - 语言名有现成映射：`translateTargetAutonym()`。
- 建议：长按在"中⇄英"或"已下载语言包"里轮换，并给一次轻提示（toast / 面板内提示）；
  设置两项：启用长按切换、切换范围（双语对 / 已装语言包）。
  切换只改设置、不触发翻译；若当前是 `FOLLOW_APP`，第一次长按先落到一个具体语言。
- **体量**：S。

---

### #11 取词面板图片区：编辑按钮 / 单击指定功能 / 全屏缩放旋转

拆成三件事，其中两件的一半已经存在：

1. **加"编辑"按钮**：`PickResultImageBarButton` 目前只有图搜/保存/分享/钉住/暂存
   （`feature/settings/.../PickResultBarButtons.kt:51-66,95-101`）。
   加 `EDIT` 需要同步：默认顺序、底栏渲染 `when` 分支
   （`PickResultImageSearchBar.kt:959-1021`）、设置页排序/隐藏
   （`app/.../ui/FloatBallPickBarButtonSection.kt:147-171`）、`PickResultBarButtonsTest`。
   能力现成：`FloatBallTextPick.openBuiltinImageEditor()`（`app/.../overlay/FloatBallTextPick.kt:531-553`）
   已经封好"落盘 + 启动编辑器"。
2. **单击图片执行指定功能**：现在单击固定走 `onImageClick` → `FloatBallTextPick.viewScreenshot()`
   （`FloatBallPickResultPanel.kt:1369-1396`），而 `viewScreenshot` 的目标就是
   `defaultImageViewerPackage`——它已经支持"每次都询问 / 内置编辑器 / 指定看图 App"
   （`FloatBallTextPick.kt:440-456`）。
   所以用户要的"指定功能"其实**部分已经能做**：把默认看图应用设成内置编辑器即可。
   真要扩展，就是把"看图目标"扩成动作枚举（查看 / 编辑 / 保存 / 分享 / 图搜），
   而不是另建一套"单击动作"。
3. **全屏查看器加双指缩放、旋转**：`PickResultFullscreenImage` 明确写着
   "不做缩放 / 旋转 / 1:1"（`:40-41`），当前用 `detectTapGestures` 点空白关闭（`:54`）。
   改成 `detectTransformGestures` + `graphicsLayer(scale/rotation/translation)`：
   双击回正、放大后平移要夹边、空白点击关闭要让位给"已放大"状态。
- **体量**：1+2 = S；3 = S~M（手势冲突）。
- **可复用点**：#11 的缩放/旋转与 #12 的放大镜是同一套 scale/rotate/translate + 边界夹取，
  建议抽一个共享的 `ZoomPanState`，别写两遍。

---

### #12 自定义区域放大（XL，需产品定义）

- 现状：**没有这个能力**。代码里的"放大镜"只有取色器内部的像素放大
  （`QuickMenuColorPicker*`），不是屏幕放大；和系统"三击放大"也互不相干，要自建。
- 仓库里已有两条可复用但都不够用的链路：
  - `RegionalScreenshotOcr`（`app/.../service/RegionalScreenshotOcr.kt:41`）——区域截图 + OCR，
    能当"冻结放大"的一期底座；
  - `QuickMenuScreenshot` 的注释把限制写死了："一次一张、被系统限流，做不了连续取帧"
    （`app/.../overlay/quickmenu/QuickMenuScreenshot.kt:21`、`QuickMenuColorPickerScreen.kt:103`）——
    所以"实时放大"**必须**换 MediaProjection 或特权通道，不能指望 takeScreenshot。
- 三条技术路线：

| 路线 | 体验 | 代价 |
|---|---|---|
| MediaProjection 持续抓屏 + 裁剪放大 | 可跟手（30fps） | 常驻 CPU/GPU、每次授权弹窗、会把自己录进去（浮窗压在源区域上会"镜中镜"） |
| 无障碍 `takeScreenshot` 定时抓帧 | 只适合"冻结后手动刷新" | 系统限流（一次一张，做不了连续取帧），无需新权限，可复用现成截图链路 |
| Shizuku/root `SurfaceControl` 抓屏 | 最流畅 | 门槛高、机型兼容差，复用项目已有特权通道 |

- 建议分两期：
  - 一期"冻结放大镜"：悬浮窗 + 可拖动 / 可调倍率 / 手动刷新，复用截图链路（M~L）；
  - 二期"实时放大"：MediaProjection + 自捕获处理 + 省电策略（L）。
- 还要先定的产品参数：放大区域（跟随手指 / 固定框选）、倍率（1.5×–8×）、是否旋转、
  边缘吸附、是否全屏放大。
- **体量**：XL。

---

### #13a 亮度滑块"升上去又退回原亮度"（P0，回归未修完）

**这是已知回归，不是新问题**：

- 提交 `df9e6dde`（1.66.0）的说明写得很明确：
  "1.50.5 每帧写 float + int 时正常，1.52 为了避开同步器互相回写把 float 删了，回弹也是从那一版开始的"；
  1.66 恢复了 `screen_brightness_float` 并把量程改成"三源取最大"。
  `update.json` 的 1.66 更新日志也写了"修复亮度滑块调高或调低后立即回到原亮度"。
- 用户在 1.66 上仍复现 → **这次修复没有覆盖他的 ROM/机型**，或者还有第二条回写路径。

**与"1.50 正常"的具体差异**（对照 `a5325b92` 的实现与当前 `BrightnessControlHelper.kt:200-320`）：

| | 1.50（正常） | 1.66（仍回弹） |
|---|---|---|
| 写入顺序 | 先 float、再 int | 先 int、再 float |
| 通道 | 只 Settings；手势中不写 shell | Settings 每次都写，另每 100ms 用 shell 再写一遍 |
| 量程上限 | config → BRIGHTNESS_ON 降级 | config / BRIGHTNESS_ON / 观测值**取最大** |
| 量程下限 | `config_screenBrightnessSettingMinimum` | `writableMin()`（按官方口径另算） |
| 读取 | int/float 一致时返回 float | **只读 int**（`:95-103`） |

**三个最可能的原因（按概率）：**

1. **自动亮度开着**：系统自动亮度控制器会在我们写完后再算一次并推回去，
   "升上去又回来"的节奏正好是这个；在自动模式下 int/float 都不是受支持的"用户覆盖"通道，只有 `adj` 是。
   → 对策：auto 模式下只写 `screen_auto_brightness_adj`（或先切 MANUAL 再写，代价是关掉用户的自动亮度）。
2. **int/float 量纲不一致**：max 取三源最大后，若真实量程比这个 max 小，
   同一 fraction 算出的 int 就偏，同步器把 float 换算覆盖回来 → 视觉回弹。
   → 对策：写入前用 `describeScale()` 校准，或恢复"先 float、后 int"的顺序并按真实量程反算 int。
3. **shell 与 Settings 双通道互相覆盖**：shell 是百毫秒级异步，若它带着中间值比 Settings 的终值晚落地，
   屏幕就"到位又退回去"。
   → 对策：终值只走一条通道，或三条 key 一次 shell 写完并带代次/序号丢弃过期写入。

**下一步（不要盲改）**：先要一份现场数据——
设备/ROM、自动亮度开关状态、`BrightnessControlHelper.describeScale()` 输出、
logcat 过滤 `BrightnessControlHelper|EdgeDiag|brightness`（现成日志就在 `writeManualBrightnessFraction` 里）。
**体量**：定位后修复很小（S），但必须先拿到这份数据。

---

### #13b 图片"全局拖放"对任何 App 都没反应（文字正常）（P0）

**范围很明确**：文字拖拽正常、图片拖拽无反应 → 问题在"带 URI 的拖拽"这条分支。

**关键事实**：图片起拖前会先做一次 **URI 重映射**：

- `HistoryEntryDragHelper.startDrag()` 调 `ClipboardDragClipHelper.remapForHostIfNeeded()`
  （`feature/overlay-panels/.../history/HistoryEntryDragHelper.kt:207-211`）；
- 只要前台包不是自己、且 URI 不是 MediaStore（我们给的是 FileProvider `cache/pick_drag/*.png`），
  就会在 **UI 线程同步**做一次 MediaStore 镜像：
  `insert` + 整份 PNG 拷贝 + `querySize` + `update` + `grantUriPermission`
  （`core/clipboard/.../ClipboardDragClipHelper.kt:54-109` → `DragFileMirror.kt:63-123`）；
- 文字 clip 没有 URI，`requiresMirror = false`，直接起拖——**这就是两条路径唯一的差别**。

**后果**：截图 PNG 动辄数 MB～十几 MB，这段"insert + 整份拷贝 + 回读校验"发生在
`startDragAndDrop` **之前**、且在主线程上。可能断在三处：

1. **镜像失败就直接不起拖**：`prepareForHost()` 返回 null（insert 被拒 / 回读 size 为 0 被回滚），
   `remapUrisForHost()` 里 `deliverableCount == 0` → 返回 null → `startDrag` 直接 `return false`
   （`ClipboardDragClipHelper.kt:103`、`HistoryEntryDragHelper.kt:211`）。
   **最符合"什么都不发生"的描述**，而且失败提示只有面板内一条，面板隐藏/被挡时看不到。
2. **同步拷贝把主线程卡住**：几十到几百毫秒的阻塞落在长按回调里，拖拽的建立被推迟甚至取消
   （这段时间里手指还在动，系统侧的拖拽建立窗口是有限的）。
3. **授权只给了"起拖时的前台包"**：`grantUriPermission(hostPackage, ...)`
   （`DragFileMirror.kt:108-112`）针对的是起拖瞬间的前台应用，真正落点的应用靠系统的
   `DRAG_FLAG_GLOBAL_URI_READ`；若某个环节丢了 flag（例如重映射后重建了 ClipData），
   落点应用读不到镜像。

**怎么快速确认是哪一处**：让用户抓一份 logcat（现成 tag：`ClipboardDragClip` / `DragFileMirror` /
`HistoryEntryDrag`）。能看到 `mirror created uri=...` 就说明镜像成功、问题在拖拽/授权；
看不到就是断在第 1 步。再对比"拖到记事本的输入框"和"拖到微信"，能区分"完全没起拖"和"起了但没人接"。

**修复方向（按性价比）：**

1. 只在**真的需要镜像**的宿主上镜像（微信 / QQ 白名单），其它 App 直接用 FileProvider URI +
   `DRAG_FLAG_GLOBAL_URI_READ`（已经设了，`buildDragFlags()` `:282-291`）；
2. 起拖前必须完成的工作**提前做**：面板打开 / 图片就绪时就把 MediaStore 镜像准备好，起拖时只取 URI；
3. 起拖失败要有**可见**反馈（Toast，而不是面板内提示）；
4. 加日志让用户能自证：现成 tag `ClipboardDragClip` / `DragFileMirror` / `HistoryEntryDrag`，
   一份 logcat 就能区分"没起拖"还是"起了但没人接"。

**体量**：S~M。

---

## 3. 批次建议

**1.66.1 热修（1–2 天，都是小改动、可验证）**
- #6 宏录制多录保存点击（根因已定位，改动 < 10 行）
- #13b 图片拖拽（先做 1/3/4：白名单镜像 + 可见失败提示 + 日志）
- #4 通知滤盒记录开关
- #8 触钮长按时长
- #10 翻译长按切换语言

**1.67（约 1 周）**
- #2 任务切换器宽度
- #3 应用内直达"创建快捷方式"
- #11 图片编辑按钮 + 看图目标扩展 + 全屏缩放旋转
- #1 悬浮球水平位置（需产品确认方案 A/B）
- #9 侧边气泡锁屏显示（含真机验证与计时暂停）

**1.68+（大件）**
- #5 摇杆"松手连点"（状态机 + 单测先行）
- #12 区域放大（一期冻结 → 二期实时）
- #13a 亮度（等现场数据，按 1→3 顺序逐条 A/B）
- #7 录屏 dock（收起 + 停止入口；14+ 单应用模式）

**风险提示**
- #1、#3 涉及产品语义（要不要恢复自由位置、要不要在这条链路开放"创建"），建议先确认再动手。
- #6、#7、#9 都卡在"浮层窗口的 z-order / 生命周期"这同一类问题上；
  这次不建议做项目级重构，但可以在 `docs/` 补一份《浮层窗口 z-order 与锁屏生命周期约定》，
  把这次踩到的三条规则写下来，避免下次再犯。

---

## 4. 需要拍板的 5 个问题

1. **#1**：用"距边缘 dp 偏移"（语义清晰，推荐）还是直接放宽"显示百分比"到 >100%（改动最小）？
2. **#4**：关闭记录时，"过滤规则 / 验证码提取"继续工作（推荐）还是整体停用？
3. **#7**：默认"录屏时收起 dock"能否接受？停止入口用通知按钮、磁贴还是已绑定手势？
4. **#9**：锁屏期间是否暂停自动关闭、解锁后重新计时（推荐要）？锁屏可见性要不要按 ROM 做兼容分支？
5. **#12**：先做"冻结放大"一期，还是直接做实时放大（工期与耗电都更大）？

---

## 5. 给英文反馈者的回复草稿（可直接用）

> Thanks for the detailed report — both items are confirmed and tracked.
>
> **Brightness slider**: this is a regression we already tried to fix in 1.66 (the float/int dual write),
> but it clearly doesn't cover your ROM. On 1.50 the write order and the value range were different;
> we're narrowing it down. Could you send your device model / ROM version, whether auto-brightness is on,
> and a logcat filtered by `BrightnessControlHelper` while dragging the slider? That single log tells us
> which channel (Settings vs shell) actually reaches the display.
>
> **Image drag & drop**: text drags work because they carry no URI; image drags currently do a synchronous
> MediaStore mirror copy on the UI thread *before* `startDragAndDrop`, which can miss the drag-start window
> and end up doing nothing. We're moving the mirror to a whitelist + pre-warming it before the drag starts,
> and adding a visible failure toast. Text drag is unaffected by the change.
