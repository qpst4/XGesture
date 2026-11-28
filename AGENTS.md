# AGENTS.md — 本仓库协作红线（每次开工先读）

> 这份文件是给所有 AI 工具 / 协作者看的**硬规则**，不是建议。
> 起因：曾发生过"一次推送把计划外的提交一并发布"的情况，因此把提交范围与推送授权写成硬约束。

## 一、Git 红线（最重要）

1. **提交范围只许是用户点名的那几个文件。**
   - `git add` 必须逐个写出路径；**禁止** `git add -A`、`git add .`、`git commit -a`。
2. **禁止自行 push。**
   - 默认只 `commit`，不 `push`。push 必须由用户**当次明确**要求推送（说"推"、"push"，或"commit&push"这类组合指令都算）。
   - 仓库装了 pre-push 门锁（`scripts/git-hooks/pre-push`）：它**只拦 AI 进程**（靠 `DSH_SHELL` / `DSH_SESSION_ID` 判定）——
     AI 发起的推送必须带 `XG_ALLOW_PUSH=1`，否则一律拒绝；**用户自己在终端 / IDE 里推送不受影响**（只打印一份待推清单）。
   - 新克隆 / 换机器后执行一次：`git config core.hooksPath scripts/git-hooks`（这是本地 `.git/config` 配置，不进版本库）。
   - **AI 不得自行设置 `XG_ALLOW_PUSH=1`**，除非用户在当次对话里明确授权推送。
3. **动手前先看范围，越界就停下报告。**
   - 工作区里经常有用户自己的未提交改动（`git status` 里非本次任务的文件一律不动）。
   - 若本地领先 `origin/main`（含用户自己的提交），**先把"将要推上去的清单"报给用户**，由用户决定推什么；不要替用户判断"反正迟早要推"。
4. **禁止整文件还原。**
   - 用户常在同一个文件里有未提交改动，`git checkout -- <file>` / `git restore <file>` 会把他的活儿一起清掉。撤销只能撤销自己改的那几行。
5. **行尾保持 LF，并当场自检（AI 工具尤其容易踩）。**
   - 本仓库存在"HEAD 存 LF、工作区是 CRLF"的文件（`core.autocrlf=false`）；Windows 上部分 AI 编辑工具还会把**整个文件**重写成 CRLF，"只改了几行"也会产出整文件假 diff。
   - 工具：改完立即跑 `python scripts/normalize-eol.py --check <改过的文件>`，报"需要规范化"就用 `--write` 修回来（只删多余 CR，不动编码 / BOM / 逻辑）。
   - 判据：再跑 `git diff --numstat -- <文件>`，**删除行数必须等于实际改动行数**；出现"整文件行数"即说明被翻了行尾。
   - 还原行尾只能删 CR：**禁止** `git checkout -- <file>` / `git restore <file>` 从 HEAD 恢复（会丢掉用户改动）。
   - 汇报时附上该文件的行尾自检结果（改动行数 vs `git diff` 行数）。

## 二、构建与验证

- 用 `.\gradlew.bat`（Wrapper）；**禁止**直接调用系统 `gradle`。
- 轻量编译检查：`.\gradlew.bat :app:compileFullDebugKotlin --offline`。
- **先确认没有别人的 Gradle 构建在跑**（用户常在 IDE 里构建）。并发构建会让 KSP 互相踩生成目录，报
  `NoSuchFileException: app\build\generated\ksp\...` —— 那不是代码错误，别乱改代码。
- 只改一两个文件时，可绕开 Gradle：用 Kotlin 编译器 + SDK 的 `android.jar` 单独编译该文件（`.tmp/volcheck/` 里有现成做法与桩；该目录未入库，只在本机有效，换机器需自备）。

## 三、沟通

- 强制中文。
- 用户标注"**仅问答**"时，只回答，不要顺手动代码。
- 不确定就停下来问，**不要替用户决定范围**。

## 四、架构红线（2026-11-09 起冻结）

overlay 迁移已完成到"可维护"的程度，**不再启动项目级重构**。此后只做增量。
（曾列过的阶段 C 收根目录、`:app` 压到 20 万行等指标，经实测已判定不值得继续，不要把它们当待办。）

冻结不等于不改代码：bug 修复、功能开发、单测补充照常；只是不再有"第 N 阶段迁移"这种工程。
理由与证据见 `docs/overlay-migration-final-status.md`。

### 4.1 新代码禁止新增静态钩子

`PanelRuntimeHooks` / `*Hooks` / `*Access` 这类 `@Volatile var` 槽位的问题是**隐藏依赖 + 生命周期靠人脑 + 测试无法替换**。
需要跨模块能力时走构造注入或 Hilt。

**唯一例外**：跨进程 / 跨服务边界（无障碍服务 ↔ 浮层 ↔ 独立进程）Hilt 图走不过去时，允许静态入口，
但必须是**不可变接口引用 + 显式 attach/detach 生命周期**，不能是可随意覆盖的 `@Volatile var`。

临时过渡必须在注释里写明"迁移到哪个接口、何时删"。

### 4.2 新增模块默认落 `:core:*` / `:feature:*`

`:app` 只放宿主、装配与 manifest 组件。

但模块化有成本（每模块一份 build 脚本、一套 KSP、一层边界摩擦），**新模块必须能说清三件事**：

1. 谁会复用它；
2. 它是否需要独立编译；
3. 它要禁止依赖谁。

说不清就放 `:app` 的独立包，不要为"看起来分层"建模块。

### 4.3 顺手改小，但必须独立提交

碰到 3k 行级文件里的函数需要改动时，优先搬到已拆出的职责类，而不是继续往主文件里加。

**前提**：该重构提交**不改行为**（编译通过、测试结果不变），且与 bug 修复 / 功能改动**分开提交**。
混在一起的后果：review 看不出行为变化、`revert` 会把修复一起退掉、`git bisect` 失效。

### 4.4 端口退役必须有机制

原则：**随改动进行，禁止集中清理**（集中改上百个调用点必炸）。
但"随改动"没有度量就等于"永远不动"，因此加两条硬约束：

1. **数量只许减不许增**：`PanelRuntimeHooks` 的 `@Volatile` 槽位数不得增加；新增即违规。
   零引用的槽位发现即删（例：2026-11-09 删掉 `showPickResultPanel`）。
2. **季度盘点**：每季度扫一次"零引用钩子"并删除。

---

## 五、UI 卡片体系与边距对齐（硬规则）

> 起因：
> 1. 2026-11-15 在"点击与长按"卡片里加长按滑条，复用了 `MiuixInsetCardComponentMargin`（横向 0），文字贴边。
> 2. 2026-11-20 全工程卡片重构发现大量老旧手拼 `Card { ... }` 破坏 Lazy 虚拟化；且存在 BottomSheet 内部双重缩进、页面表单 Column 写死 16dp 导致与 12dp 卡片边缘上下错位。

### 5.1 卡片容器选型（严禁业务层直接调用 `Card(`）
1. **常规设置列表项**：必须使用 `groupedCardItems` / `MiuixListSettingsCard`。自动按索引将每一行拆分成独立虚拟化 item，首段自动切顶圆角，尾段切底圆角。
2. **带 Tab 切换的分组卡片**：必须使用 `MiuixGroupedTabSettingsCard`。TabRow 自动作为首段大圆角，各子项挂为后续 CardSegment，严禁把几十个槽位硬塞在单一 `item { Card }` 里。
3. **弹窗选项与特殊独立表单**：使用 `CardSegment`（`isFirst = true, isLast = true`）。
4. **禁止**在 `:app` / `:feature` 业务界面直接 `import top.yukonga.miuix.kmp.basic.Card` 或手写 `Card(...)` 构造。

### 5.2 垂直基准线与边距对齐（全页统一 12dp 基线）
1. **页面级统一外边距**：设置页所有卡片以及外部表单 Column 的左右外边距基线必须统一为 **12dp**（`padding(horizontal = 12.dp)`）。**严禁**在外层手写 16dp 导致上下卡片呈现阶梯状错位。
2. **弹窗脱外边距**：在 `MiuixBottomSheet` / 底部弹窗内使用 `CardSegment` 时，由于 Sheet 自身已内置 `insideMargin`，必须显式指定 `outerHorizontalPadding = 0.dp`，**严禁双重缩进**。
3. **卡片行内部边距**：卡片行自身的 `insideMargin` 必须用 `BasicComponentDefaults.InsideMargin`（左右 16dp，水波纹撑满切角），**严禁**使用横向为 0 的 `MiuixInsetCardComponentMargin`（父容器已提供横向内边距的除外，如弹窗、浮层面板内部；此时必须确认不会出现双重缩进）。

## 六、多入口功能排查（硬规则）

> 起因：修小组件面板图标时，只凭 grep 命中的 `launchCreateShortcut` 就当成入口改了两轮，实际执行的是页面内 `createLauncher`，改动一次都没跑过。

1. **入口先枚举，再动手**：同一功能在多个入口表现不一致时，先把所有入口列成清单（每个调用点、触发条件、哪条实际会执行），写不出清单不许改代码。**禁止**把 grep 命中的某个调用点直接当成入口。改完按清单逐项确认：入口要么共用了被改代码，要么单独修复并验证；共用组件的改动要列出受益入口，不能默认“应该都好了”。
2. **先证明“别的入口为什么能行”，再谈系统限制**：判断某能力“系统不允许 / 无解”之前，必须先解释其它入口是怎么做到的；如果别的入口正常，那就是本入口没接上，不是限制。
3. **新增可配置动作必须接全入口**：新增需要二次配置弹窗的动作（打开链接、运行宏、组合动作、Shell 命令等）时，必须接全所有“可添加动作”的入口。**入口以 `grep -rl 'GestureActionPickerScreen\|GestureActionPickerLazyItems'` 的实际结果为准**——不要依赖一份写死的名单（名单一定会过期，照它接必漏）；凡复用共享选择器的入口自动受益，独立实现的入口逐个接并验证。优先放共享组件。
