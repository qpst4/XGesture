# 用户反馈分析（2026-11-01）

> 本轮只有 1 条反馈。**§1–§9 是改动前的分析与方案对比；§10 记录本轮实际落地**（用户已拍板：复用现有翻译设置 / 与取词面板完全一致 / 顺带修拖拽流转栏）。
> 仓库 1.55.0（versionCode 82，`app/build.gradle.kts:41-42`），HEAD `e690811b`。
> 工作区原有用户未提交的 `SlideIndexAccessibilityService.kt` 与两个未跟踪文件，本次一律未触碰。
> 行号均以**当前工作区**为准；§1–§9 的行号是改动前的，§10 落地后部分行号已漂移。

## 0. 结论速览

| 反馈 | 性质 | 现状是否成立 | 改动面 | 优先级 |
| :- | :- | :- | :- | :- |
| 全屏复制的翻译键应可自定义引擎；现在只跳 Google 网页翻译 | 功能缺口：入口**漏接**既有「翻译引擎」设置 | **成立** | 小（3 个代码文件 + 1 个单测；无新设置项、无新文案） | 高 |

**一句话**：App 里早就有 4 档「翻译引擎 + 目标语言 + 本地 App + ML Kit / 云端配置」，取词面板、历史、剪贴板智能翻译、屏幕翻译、快速菜单翻译工具都接了；只有「全局复制」（用户口中的全屏复制）这个入口写死走 Google。代码不是难点，真正要定的是「Google 这一档要不要也在面板里翻」和「即时翻译开关算不算数」（§4 / §8）。

## 1. 反馈对应哪个功能（已证实）

- 全仓代码与资源里没有「全屏复制」这个字符串（只有本文出现）。语义吻合的只有手势动作「**全局复制**」（`app/src/main/res/values-zh/strings.xml:1037`，`GestureAction.UniversalCopy` → `SlideIndexAccessibilityService.performUniversalCopy()` → `UniversalCopyOverlay`）：高亮框选全屏文本，底部 Dock 一排操作。
- 该 Dock 第 5 个图标就是「翻译」（tooltip `universal_copy_translate`=翻译）：`app/src/main/java/com/slideindex/app/copy/UniversalCopyOverlay.kt:518-534`。

## 2. 现状：证据链（已证实）

1. 点击翻译 → `performTranslate(context, text)` → `dismiss()`：`UniversalCopyOverlay.kt:530-531`。
2. `performTranslate` **只读了目标语言，完全没读引擎 / 本地 App / 即时翻译**（`:776-789`）：
   ```kotlin
   val targetLang = if (settings != null) {
       TranslateTargetResolver.resolve(settings.floatBallTranslateTargetLang, settings.appUiLanguageTag)
   } else "zh-CN"
   FloatBallTextPick.translateText(context, text, targetLang)
   ```
3. `FloatBallTextPick.translateText` 的落地顺序（`app/src/main/java/com/slideindex/app/overlay/FloatBallTextPick.kt:159-164`）：
   1. `launchToTranslateApp(...GOOGLE_TRANSLATE_PACKAGE)`：先 `ACTION_PROCESS_TEXT`、再 `ACTION_SEND`，包名写死 `com.google.android.apps.translate`（`:188-212`；通道探测见 `TranslateAppCapability.kt:18-37`、`TranslateLaunchPlanner.kt:36-44`）；
   2. 都拉不起来 → `openTranslateWeb`：`https://translate.google.com/?sl=auto&tl=<targetLang>&text=<encoded>`（`:234-244`）；
   3. 连浏览器都没有 → `searchText("translate …")`（`:162-163`）。
4. 所以「只跳 Google 网页翻译」的说法是准确的：机器上没装 Google 翻译 App 时（`PROCESS_TEXT`/`SEND` 探测不到它，国产 ROM 很常见），前两条必然失败，直接落网页。**目标语言倒是已经跟随设置**（不是写死中文），缺的只是「引擎」这一维。

## 3. 已有引擎能力盘点（可直接复用，不必新造）

### 3.1 设置与数据

- 4 档枚举：`FloatBallTranslateEngine`（`feature/settings/src/main/java/com/slideindex/app/settings/FloatBallTranslateEngine.kt:3-16`）——`GOOGLE` / `ML_KIT` / `CLOUD_LLM` / `LOCAL_APP`。
- 偏好项：`floatBallInstantTranslate`（默认 **true**）、`floatBallTranslateEngine`（默认 GOOGLE）、`floatBallTranslateTargetLang`（默认跟随 App 语言）、`floatBallTranslateAppPackage`（`AppSettingsSlices.kt:386-393`、`SettingsSnapshotReader.kt:567-574`）。
- 设置页：`app/src/main/java/com/slideindex/app/ui/FloatBallTranslationSettingsScreen.kt` —— 引擎下拉 `:62-70`、目标语言 `:73-91`、即时翻译开关 `:92-106`、本地 App 选择 `:109-126`、ML Kit 语言包入口 `:127-144`、云端翻译配置入口 `:145-162`。
- 入口位置：悬浮球 → 取词 → 翻译（`FloatBallPickSettingsScreen.kt:299-315`），副标题显示「引擎 · 即时翻译/跳转翻译」（`FloatBallSettingsScreen.kt:152-171`）。
- 「即时翻译」的官方语义（`values-zh/strings.xml:2204`）：*开启后在取词面板上方用浮窗显示译文；关闭则跳转翻译，装了翻译 App 就用它，否则打开网页*。

### 3.2 现有消费方（各自决定 LOCAL_APP 的兜底，口径**并不统一**）

| 入口 | 代码 | 怎么用引擎 |
| :- | :- | :- |
| 取词面板 / 历史 / 剪贴板智能翻译 | `FloatBallTranslateCoordinator.kt:18-83` | LOCAL_APP → 交外部 App；即时翻译关 → 跳 Google；否则面板内联翻译（`showTranslateLoading/Result/Error`） |
| 剪贴板浮层的智能「翻译」 | `ClipboardOverlayController.kt:207-214` → `PickResultFromHistoryCoordinator.openFromClipboardPayload(autoTranslate=true)`（`:85-113`） | **先开取词结果面板，再调上面那个 coordinator** —— 这就是「别的入口点翻译」的现成模板 |
| 屏幕翻译 | `ScreenTranslationOverlayManager.kt:162-170` | LOCAL_APP 退回 **Google**（要原位画译文，不能交给外部 App，代码注释已写明） |
| 快速菜单「翻译」工具 | `QuickMenuToolScreens.kt:1004-1012` | LOCAL_APP 退回 **ML Kit**（面板里无法与外部 App 交互） |
| **全局复制** | `UniversalCopyOverlay.kt:776-789` | **不读引擎，永远 Google** |

> `floatBallTranslateEngine` 在 app 侧的消费点里没有 `copy/UniversalCopyOverlay.kt` —— 这就是本次反馈的全部根因（该入口的翻译路径没有第二个隐藏分支）。

### 3.3 结果展示面已经现成

- 取词结果面板有完整的内联翻译态：`showTranslateLoading` / `showTranslateResult` / `showTranslateError` / `restoreFromTranslation`（`FloatBallPickResultPanel.kt:575-627`）；面板内的翻译键再点一下会还原原文（`isShowingTranslation`）。
- 错误码 → 文案：`pickresult/PickResultPanelMessages.kt:6`；coordinator 里还有一层 `mapErrorMessage`（`FloatBallTranslateCoordinator.kt:85-94`）。
- 全局复制自己的「分词」已经在用这个面板：`UniversalCopyOverlay.kt:506-514` + `openPickPanel` `:928-942`（先 `showResult`，再 `dismiss()` 自己的浮层）—— 所以「翻译也开这个面板」对用户不是新东西。
- 反例：`FloatBallTranslatePanel.kt`（独立译文浮窗）现在是**死代码** —— `showLoading/showResult/showError` 全仓已无调用点（`git grep HEAD "FloatBallTranslatePanel.show"` 为空，只剩 `isShowing`/`dismiss` 被面板关闭链引用）。**不建议**为这个需求复活它。

## 4. 方案对比

| 方案 | 做法 | 优点 | 代价 / 风险 |
| :- | :- | :- | :- |
| **A（推荐）复用** | 全局复制翻译 = 读「翻译设置」；LOCAL_APP → 外部 App；即时翻译关 → 跳 Google；否则**开取词结果面板 + 自动翻译**（与剪贴板智能翻译同一条链路） | 改动最小；与取词面板/剪贴板完全同语义；加载/错误/复制/分享/搜索全部白送；无新设置项与文案 | 默认设置（GOOGLE + 即时翻译开）下行为**从「跳 Google」变成「面板内 Google 翻译」** |
| **A' 只按引擎** | 忽略「即时翻译」开关：GOOGLE/ML_KIT/CLOUD_LLM 都在面板里翻，只有 LOCAL_APP 跳出去 | 显式点翻译就一定有译文，不会因开关被甩去网页 | 与浮球/剪贴板语义不一致；用户关掉即时翻译的意图在这个入口失效 |
| **B 引擎决定落地** | GOOGLE 保持现在的「App→网页」跳转；ML_KIT / CLOUD_LLM 面板内翻；LOCAL_APP 外部 App | **默认行为完全不变**，只给想换引擎的人新路径 | 同一个 GOOGLE 在取词面板（即时翻译开）是内联、在全局复制是跳转，两处语义不一致；仍要新增「绕过开关、按引擎翻」的入口 |
| **C 独立设置** | 给全局复制单开一套引擎设置 | 完全隔离 | 新 key + 快照/桥接/mutator/repository + 设置页/导航 + 四套文案 + 备份编解码，约 10–15 文件；概念与「翻译设置」重复 |
| **D 长按快选（增强）** | 长按翻译键弹 4 档引擎快选 | 就地切换，不用进设置 | 当前长按只 toast 显示 tooltip（`UniversalCopyOverlay.kt:654-658`）；改的是全局设置，会连带改取词面板；建议作为 A 之后的增强 |

**为什么推荐 A**：仓库里已经有一条被验证过的「外部入口点翻译」链路——剪贴板浮层的智能「翻译」就是「开取词结果面板 + `FloatBallTranslateCoordinator.translate`」。全局复制照抄它，改动面最小、行为最一致；而且 `LOCAL_APP` 的失败提示（未设置/未安装时复制文本）在 `FloatBallTextPick.translateToApp`（`:172-185`）里已经写好。

## 5. 推荐方案 A 的落地细节

### 5.1 改动点（约 15–30 行）

1. `app/src/main/java/com/slideindex/app/copy/UniversalCopyOverlay.kt`
   - `performTranslate` 读 `floatBallTranslateEngine`，分三支：
     ```kotlin
     when {
         settings != null && engine == FloatBallTranslateEngine.LOCAL_APP ->
             FloatBallTextPick.translateToApp(context, text, settings.floatBallTranslateAppPackage, targetLang)
         settings == null || !settings.floatBallInstantTranslate ->
             FloatBallTextPick.translateText(context, text, targetLang)   // 与浮球一致：关掉即时翻译就跳转
         else -> {
             openPickPanel(context, text)                                // 现成的 :928-942
             FloatBallTranslateCoordinator.translate(context, text)      // 现成的引擎/错误/加载链路
         }
     }
     ```
   - 调用方 `:530-531` 的 `dismiss()` 不变（分词已经是「先开面板再关自己」的顺序，`:512-513`）。
2. `app/src/main/java/com/slideindex/app/overlay/FloatBallTranslateCoordinator.kt`（可选，但建议）
   - 把「读设置 → 选落地方式」抽成一个入口，例如 `translateFromExternalEntry(context, text, openTextPanel: (String) -> Unit)`，`translate()` 与全局复制共用，避免上面那三支判断在 coordinator 和 overlay 里各写一遍、日后漂移。
   - 若选 A'，就在抽出时去掉 `!floatBallInstantTranslate` 分支。
3. 无需新增偏好键、设置页、导航、文案。

### 5.2 行为变化（默认设置下）

- 改前：选一段文字 → 翻译 → 离开本 App，去 Google 翻译 App / 网页（没装 App 就是网页）。
- 改后：选一段文字 → 翻译 → 原地弹出取词结果面板，先转圈、再显示译文；面板内可复制/分享/搜索，翻译键再点一下可切回原文。引擎换成 ML Kit / 云端就换引擎翻译；换成「本地 App」就交给所选 App。

## 6. 风险与注意点

- **LOCAL_APP 不要先开面板再跳外部 App**：剪贴板那条路现在就是「面板留着、人已跳到别的 App」（`PickResultFromHistoryCoordinator.kt:102-111` + coordinator 的 LOCAL_APP 分支），全局复制应提前判掉，别把这个小毛病复制过来。
- **即时翻译关掉时也别开面板**：否则 coordinator 直接跳网页，面板会留在屏上显示原文（同样的「空面板」问题）。
- 第一帧不会闪原文：`showResult` 同步把 `pickPanelVisible` 置 true（`FloatBallPickResultPanel.kt:353`），coordinator 紧接着 `showTranslateLoading`，同一次调用栈内就进了 loading 态。
- 多行文本按 `\n` 拼接后整段送（`:511`/`:529`）。三个引擎的接口都是「整段文本进、整段文本出」，保持现状即可；不要改成一行动一次翻译（ML Kit 每次调用要过一遍语言识别与模型，多段会明显变慢）。
- ML Kit 语言包没下载 / 云端没配 API Key：面板会 toast 现成的错误文案（`PickResultPanelMessages.kt:6`），不会静默失败。
- LOCAL_APP 档目标语言由目标 App 决定（设置页已隐藏该行，`FloatBallTranslationSettingsScreen.kt:72-73`），传给 `translateToApp` 的 targetLang 只服务「没选 App → 网页兜底」。
- 面板翻译态下窗口默认不抢焦点（`WORD_TAP`），与全局复制浮层抢焦点无关；`dismiss()` 顺序沿用分词。
- 无障碍：翻译键 `contentDescription` 不变；面板自带读屏节点。

## 7. 顺带发现（同类问题，本次可不做）

- **拖拽流转栏的「翻译」同样没接引擎，而且目标语言写死 `zh-CN`**：`app/src/main/java/com/slideindex/app/overlay/dragdrop/DragDropFlowOverlay.kt:773-775` `FloatBallTextPick.translateText(context, text, "zh-CN")`。与本次是同一类缺口；如果抽了 §5.1 的公共入口，可以顺带接上（但流转栏没有现成文本面板可开，要么也开取词面板，要么维持跳转）。
- **`FloatBallTranslatePanel.kt` 整文件是死代码**（§3.3），可以单独提一个清理提交。
- **「翻译设置」藏在悬浮球 → 取词 → 翻译**：只用全局复制、不用悬浮球的用户可能找不到引擎入口。若希望「翻译键」本身可配，长按快选（方案 D）比再开一页设置更直接。
- **LOCAL_APP 的兜底口径三处不同**（外部 App / 退回 Google / 退回 ML Kit）：不是 bug，但建议在文档或设置页副标题里写清楚，免得下一条反馈还是它。

## 8. 待确认（3 个决策，已由用户拍板 → 见 §10）

> 用户的选择：**① 复用「取词 → 翻译」现有设置；② 与取词面板 / 剪贴板智能翻译完全一致（「即时翻译」开关也生效）；③ 顺带修拖拽流转栏的「翻译」。**

1. **引擎设置复用还是独立？**
   推荐复用「取词 → 翻译」那套（引擎 / 目标语言 / 本地 App / ML Kit / 云端配置全共享）；独立设置要 10–15 个文件且概念重复。
2. **点了翻译之后怎么落地？**
   - A（推荐）：与取词面板 / 剪贴板智能翻译完全一致——引擎 + 即时翻译开关都生效；默认在取词结果面板里出译文，关掉开关仍跳 Google。
   - A'：只按引擎，忽略即时翻译开关；Google / ML Kit / 云端都在面板里翻，只有本地 App 跳出去。
   - B：引擎决定落地——Google 保持跳转，ML Kit / 云端面板内翻，本地 App 跳出去（默认行为完全不变）。
3. **范围**：这次只修全局复制，还是顺带把拖拽流转栏的「翻译」也接上引擎（并去掉写死的 `zh-CN`）？

## 9. 验证方式（落地后）

- 编译：`.\gradlew.bat :app:compileFullDebugKotlin --offline`（先确认没有别人的 Gradle 构建在跑）。
- 手测矩阵：4 档引擎 × 即时翻译开/关 ×（未装 Google 翻译 / 未下载 ML Kit 语言包 / 未配云端 API Key / 未选本地 App）。
- 行尾：`python scripts/normalize-eol.py --check <改过的文件>`；`git diff --numstat` 的删除行数应等于实际改动行数。

## 10. 本轮落地（已实现）

### 10.1 改动

1. **`app/src/main/java/com/slideindex/app/overlay/TranslateLanding.kt`（新增，纯逻辑）**
   - `TranslateLanding`（`EXTERNAL_APP` / `JUMP_TO_WEB` / `IN_PANEL`）+ `resolveTranslateLanding(engine, instantTranslate)`。
   - 判定顺序集中在这里：**本地 App > 即时翻译开关 > 引擎**。三处入口（取词面板、全局复制、拖拽流转栏）共用，改顺序等于改行为。
2. **`app/src/main/java/com/slideindex/app/overlay/FloatBallTranslateCoordinator.kt`**
   - 新增 `translateFromExternalEntry(context, text, openTextPanel)`：给「取词面板以外」的入口用；`openTextPanel` **只在确实要在面板里翻时才回调**，所以不会出现「面板开着、人已跳到别的 App」的空面板。
   - `translate()`（取词面板的翻译键 / 历史 / 剪贴板）行为不变，只把「面板内翻译」抽成 `translateInPanel()` 供两处复用。
3. **`app/src/main/java/com/slideindex/app/copy/UniversalCopyOverlay.kt`**
   - `performTranslate()` 删掉写死的 `FloatBallTextPick.translateText(...)`（这是用户反馈的根因），改调 `translateFromExternalEntry`；结果面板复用「分词」已经在用的 `openPickPanel()`。
   - 附带：`UniversalCopyOverlay` 不再直接读 `floatBallTranslateTargetLang`，目标语言由统一链路解析。
4. **`app/src/main/java/com/slideindex/app/overlay/dragdrop/DragDropFlowOverlay.kt`**
   - `translate_text` 同样改走 `translateFromExternalEntry`，**去掉写死的 `"zh-CN"`**（目标语言回到设置）。
   - `openWordSegmentation()` 抽出 `openTextPickPanel()`，分词与翻译共用；翻译在面板显示后再灌 loading/译文。
5. **`app/src/test/java/com/slideindex/app/overlay/TranslateLandingTest.kt`（新增）**
   - 锁住三件事：本地 App 无视开关恒外部；关即时翻译恒跳转；开即时翻译恒面板内。

### 10.2 落地后的行为

| 引擎 | 即时翻译开（默认） | 即时翻译关 |
| :- | :- | :- |
| Google / ML Kit / 云端 | 取词结果面板内翻译：loading → 译文；可复制 / 分享 / 搜索；面板翻译键再点一下还原原文 | 跳转翻译：装了 Google 翻译就交给它，否则打开 `translate.google.com` 网页 |
| 本地 App | 交给所选 App；未选 → 提示「未设置翻译应用」并按网页兜底；已选但拉不起 → 提示并复制原文 | 同左（开关不影响这一档） |

- **全局复制**：点翻译后原浮层照旧 `dismiss()`，译文在取词结果面板里；默认设置下的体验从「离开 App 去 Google」变成「原地出译文」。
- **拖拽流转栏**：点翻译同样进面板；流转栏按原逻辑淡出并收成 1px 锚点。
- 设置项、文案、导航一律未新增 —— 引擎 / 目标语言 / 本地 App / ML Kit / 云端配置仍走「悬浮球 → 取词 → 翻译」那一页。

### 10.3 验证

- 主代码编译：`.\gradlew.bat :app:compileFullDebugKotlin --offline` **BUILD SUCCESSFUL**（4m47s）；把落地逻辑拆到独立的 `TranslateLanding.kt` 之后再跑一次，仍 **BUILD SUCCESSFUL**（4m12s）。
- 单测：`:app:testFullDebugUnitTest --tests com.slideindex.app.overlay.TranslateLandingTest` **无法跑**——`app` 的 unit test 源集有**既有的、与本次无关的**编译错误：
  - `app/src/test/java/com/slideindex/app/overlay/FloatBallGestureDetectorTest.kt:419/698/904`：`No value passed for parameter 'systemTouchSlopPx'`；
  - `app/src/test/java/com/slideindex/app/ui/viewmodel/ViewModelsTest.kt:455/459/621`：`No value passed for parameter 'applicationScope'` / 类型不匹配。
  - 因此改用手边的 Kotlin 编译器 + JUnit 4.13.2 单独编译并运行新测试：`OK (3 tests)`（`-Xfriend-paths` 模拟 Gradle 的 test→main internal 可见性）。
- 行尾：改动 / 新增文件经 `scripts/normalize-eol.py --check` 全部 **LF**；`git diff --numstat` 无整文件改写。
- **未做真机手测**：4 档引擎 × 即时翻译开/关矩阵（§9）仍需在设备上过一遍，重点看「本地 App 未安装」「ML Kit 语言包未下载」「云端未配 Key」三种失败提示，以及拖拽流转栏翻译时面板与流转栏的层叠顺序。
