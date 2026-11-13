# 用户反馈分析（2026-11-13）

> 本轮 1 条反馈；**已按拍板做成开关**（§1 是代码现状，§2 起是落地）。
> 仓库 1.64.1（versionCode 87）；行号以当前工作区为准。

## 0. 结论速览

| # | 反馈 | 性质 | 代码现状（证据） | 本次落地 |
| :- | :- | :- | :- | :- |
| 1 | 取词面板里有链接的时候，分词时自动合并 | 行为开关，不是崩溃类 bug | 分词器在 `:` `/` `.` `?` `=` `&` 等硬定界符处把链接**拆开**，全仓库没有「按链接合并 token」的分支 | 新增「**分词时合并链接**」开关，默认开：整条链接一个词块；关掉回到按标点拆开（拍板：只限「分词」模式，「按字」不生效） |

## 1. 先把「现状」说清楚

**分词 token 怎么来的**：`PickResultWordTokenizer.tokenizeSelectableWords`（`feature/overlay-panels/src/main/java/com/slideindex/app/overlay/pickresult/PickResultWordTokenizer.kt:52`）。
它先按 `DELIMITERS`（`:8-14`，含 `:` `/` `.` `?` `=` `&` `#` `-` 等 40 多个字符）切开，再把连续的拉丁字符攒成一个词。所以：

- `https://a.com/x` → `https | : | / | / | a | . | com | / | x`（9 个词块）
- `www.b.com` → `www | . | b | . | com`

**链接识别只有一套**：`PickResultUrl`（`PickResultUrl.kt:72`）给「打开链接」按钮和智能 chips 用，它只产出候选 URL，从不改分词结果。

因此「有链接就自动合并」在当前版本不是既有行为 —— 不管这条反馈是想要合并（链接被拆碎了不好选中），还是在别处见过合并（FV 一类工具的习惯），结论一样：**这件事得能开能关**。
另有一项现成能力正好当兜底：**长按词块拆成单字**（`PickResultWordTokenizer.splitTokenAtIndex`，`:157`），本次保留。

## 2. 开关的位置与语义

- 路径：悬浮球 → 取词 → 布局与行为 → 「选择与复制」段 → **分词时合并链接**（紧跟在「点词粒度」下面）。
- 开（默认）：`https://a.com/x?q=1` 整条一个词块，点一下即整条选中，复制 / 打开都是一整条；长按仍可拆成单字。
- 关：回到按标点拆开的老行为，只想取链接里某一段时用。
- **只作用于「分词」**；「按字」本来就是逐字选（没有「炸词」这回事），切过去时该行置灰。

## 3. 实现

### 3.1 链接区间（不改 URL 语义，只补位置）

`PickResultUrl.findOpenableUrlRanges`（`PickResultUrl.kt:111`）：

- 与 `extractOpenableUrls` 同一套正则，但返回**原文下标区间**——`extractOpenableUrls` 返回的是规范化后的网址（`www.x.com` 会被补成 `https://www.x.com`），拿去原文反查对不上；
- intent URI 先整段挖空，避免再从里面截出半条链接；
- 同一条链接会被多条正则同时命中（`https://` 也满足「自定义协议」），只留最外层；
- 句尾 `. , ) 。` 等标点留在链接外，跟后面的词块走；
- 逐条过 `normalizeOpenableUrl` 校验，`javascript:` 之类照旧不认。

### 3.2 分词

`tokenizeSelectableWords(text, mergeLinks)`（`PickResultWordTokenizer.kt:52`）：命中区间整段收口成一个 token，其余规则原样不动，`tokens.joinToString("") == text` 这个不变量保持（自动全选、划选、复制都依赖它）。
「按字」仍走 `tokenizeSelectableChars(text)`（逐 code point），不改。

### 3.3 面板与设置链路

- 面板：`PickResultInteractiveTextSection` 读 `floatBallPickMergeLinks`，进 token 缓存 key；开关或粒度变化后清选中集与「自动全选」锁存（`PickResultInteractiveText.kt:209-250`、`:278-290`）。
- 设置：照 `floatBallPickHapticEnabled` 那条线补齐 —— `AppSettingsSlices.kt:339`、`AppSettings.kt`、`OverlaySettings.kt`（`from(AppSettings)` + `toMinimalAppSettings`）、`SettingsPreferenceKeys.kt:667`（`float_ball_pick_merge_links`，落 FLOAT_BALL 模块）、`SettingsSnapshotReader.kt`、`SettingsSliceBridges.kt`、`OverlaySettingsMutator.kt`、`SettingsRepository.kt`、`ExtensionSettingsViewModel.kt`、设置页（`FloatBallPickPanelLayoutBehaviorSettingsScreen.kt:409`）+ 导航。
- 文案：`values/strings.xml`、`values-zh/strings.xml`（其余语言走 Weblate，回退英文）。

### 3.4 测试

- `PickResultWordTokenizerTest` +4：开时整条链接一个 token、关时按标点拆、句尾标点不并进链接、tel/www 同样合并。
- `PickResultUrlTest` +4：返回原文子串而非规范化网址、http 不重复命中、句尾标点留在区间外、intent 内层链接不另出一段。
- `SettingsModuleRegistryTest`：键数快照 696 → 697，FLOAT_BALL 77 → 78。

## 4. 验证

| 项 | 结果 |
| :- | :- |
| `.\gradlew.bat :app:compileFullDebugKotlin --offline` | BUILD SUCCESSFUL（6m55s） |
| `:app:testFullDebugUnitTest --tests "com.slideindex.app.overlay.pickresult.*"` | BUILD SUCCESSFUL；`PickResultUrlTest` 36 / `PickResultWordTokenizerTest` 16，0 失败 |
| `:feature:settings:testDebugUnitTest --tests "...SettingsModuleRegistryTest"` | BUILD SUCCESSFUL，6/6 |
| 行尾自检 | 19 个改动文件全部 LF（`scripts/normalize-eol.py --check`） |

> 未提交。默认值目前取「开」（按反馈：有链接就整条选中）；要改成「关」只需动 `AppSettingsSlices.kt:339` 与 `OverlaySettings.kt` 两处默认值。
