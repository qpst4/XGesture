# AGENTS.md — 本仓库协作红线（每次开工先读）

> 这份文件是给所有 AI 工具 / 协作者看的**硬规则**，不是建议。
> 起因：曾发生过"一次推送把计划外的提交一并发布"的情况，因此把提交范围与推送授权写成硬约束。

## 一、Git 红线（最重要）

1. **提交范围只许是用户点名的那几个文件。**
   - `git add` 必须逐个写出路径；**禁止** `git add -A`、`git add .`、`git commit -a`。
2. **禁止自行 push。**
   - 默认只 `commit`，不 `push`。push 必须由用户**当次明确**说"推"。
   - 仓库装了 pre-push 门锁（`scripts/git-hooks/pre-push`）：它**只拦 AI 进程**（靠 `DSH_SHELL` / `DSH_SESSION_ID` 判定）——
     AI 发起的推送必须带 `XG_ALLOW_PUSH=1`，否则一律拒绝；**用户自己在终端 / IDE 里推送不受影响**（只打印一份待推清单）。
   - 新克隆 / 换机器后执行一次：`git config core.hooksPath scripts/git-hooks`（这是本地 `.git/config` 配置，不进版本库）。
   - **AI 不得自行设置 `XG_ALLOW_PUSH=1`**，除非用户在当次对话里明确授权推送。
3. **动手前先看范围，越界就停下报告。**
   - 工作区里经常有用户自己的未提交改动（`git status` 里非本次任务的文件一律不动）。
   - 若本地领先 `origin/main`（含用户自己的提交），**先把"将要推上去的清单"报给用户**，由用户决定推什么；不要替用户判断"反正迟早要推"。
4. **禁止整文件还原。**
   - 用户常在同一个文件里有未提交改动，`git checkout -- <file>` / `git restore <file>` 会把他的活儿一起清掉。撤销只能撤销自己改的那几行。
5. **提交前把该文件行尾规范成 LF。**
   - 本仓库存在"HEAD 存 LF、工作区是 CRLF"的文件（`core.autocrlf=false`），直接 `git add` 会产出"整文件改写"的假 diff。
   - 规范方式：只删掉多余 CR（不动编码 / BOM），使 diff 只含真实改动。
   - 工具：`python scripts/normalize-eol.py --check <文件...>` 查，`--write` 改。
6. **每次编辑文件后必须当场自检行尾（AI 工具尤其容易踩）。**
   - 部分 AI 编辑工具在 Windows 上会把**整个文件**重写成 CRLF（不只是改动的行），因此"我只加了几行"也会产出整文件假 diff。
   - 每次改完文件，立即跑：`python scripts/normalize-eol.py --check <改过的文件>`；报 `需要规范化` 就立刻 `--write` 修回来。
   - 修改前后各跑一次 `git diff --numstat -- <文件>`：**删除行数必须等于实际改动行数**，出现"整文件行数"即说明被翻了行尾。
   - 还原行尾只能删 CR：**禁止** `git checkout -- <file>` / `git restore <file>` 从 HEAD 恢复（会丢掉用户改动），也不要借机改编码 / BOM / 代码逻辑。
   - 汇报时附上该文件的行尾自检结果（改动行数 vs `git diff` 行数）。

## 二、构建与验证

- 用 `.\gradlew.bat`（Wrapper）；**禁止**直接调用系统 `gradle`。
- 轻量编译检查：`.\gradlew.bat :app:compileFullDebugKotlin --offline`。
- **先确认没有别人的 Gradle 构建在跑**（用户常在 IDE 里构建）。并发构建会让 KSP 互相踩生成目录，报
  `NoSuchFileException: app\build\generated\ksp\...` —— 那不是代码错误，别乱改代码。
- 只改一两个文件时，可绕开 Gradle：用 Kotlin 编译器 + SDK 的 `android.jar` 单独编译该文件（`.tmp/volcheck/` 里有现成做法与桩）。

## 三、沟通

- 强制中文。
- 用户标注"**仅问答**"时，只回答，不要顺手动代码。
- 不确定就停下来问，**不要替用户决定范围**。

## 四、架构红线（2026-11-09 起冻结）

overlay 迁移已完成到"可维护"的程度，**不再启动项目级重构**（阶段 C 收根目录、继续合并 trampoline、
:app 压到 20 万行、FloatBallInteractionState 状态对象化）。此后只做增量：

1. **新代码禁止再加静态钩子**（PanelRuntimeHooks / *Hooks / *Access 这类 @Volatile 槽位）。
   需要跨模块能力时走构造注入或 Hilt；确需临时过渡，必须在 PR 里写明"迁移到哪个接口、何时删"。
2. **新增模块默认落 `:core:*` 或 `:feature:*`**，`:app` 只放宿主、装配与 manifest 组件。
3. **改动经过的旧代码顺手改小**：碰到 3k 行级文件里的函数，优先搬到已拆出的职责类，而不是继续往主文件里加。
4. **端口退役随改动进行**：每次改到某个钩子，评估能否改注入；禁止集中清理（集中改会一次动几十个调用点）。

冻结不等于不改代码：bug 修复、功能开发、单测补充照常；只是不再有"第 N 阶段迁移"这种工程。
理由与证据见 docs/overlay-migration-final-status.md。