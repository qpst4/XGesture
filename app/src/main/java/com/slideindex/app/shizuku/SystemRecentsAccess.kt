package com.slideindex.app.shizuku

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.os.Process
import android.os.SystemClock
import android.util.Log
import com.slideindex.app.util.TaskExclusions

/**
 * Outcome of a "move this task into a free window" attempt.
 *
 * [UNKNOWN] means the platform did not expose the task's windowingMode, so the move
 * cannot be judged either way; callers must not treat it as a confirmed failure.
 */
internal enum class FreeWindowMoveStatus {
    MOVED,
    NOT_MOVED,
    UNKNOWN,
}

/**
 * Generic system recents access through IActivityTaskManager / IActivityManager.
 * Intended to run inside the Shizuku UserService (adb/shell uid).
 *
 * ## 为什么要“探测一次、记住赢家”
 *
 * 不同 Android 版本 / ROM 上，同一件事有 2 个接口、多种方法签名和 flags 组合；
 * 旧实现每次取最近任务都把 2 个接口 × 8 种调用**全部试一遍**（4 个代理对象重复问同一个服务），
 * 每次成功都跨进程传回最多 40 条 TaskInfo 并逐条反射解析，命中后也不早退 —— 这是任务切换器
 * 打开慢的主要来源。
 *
 * 现在改成：
 * 1. **探测**（每次进程内第一次、或结果连续异常时）：把所有候选跑一遍，按「覆盖的任务集合」
 *    选一个最小覆盖集（1～2 条），记在内存里；
 * 2. **稳定态**：只跑记住的那 1～2 条；
 * 3. 连续空结果 → 清掉记忆，冷却后重新探测，避免“记错了就永远空白”。
 */
internal object SystemRecentsAccess {

    private const val TAG = "SystemRecentsAccess"
    private const val MAX_TASKS = 40

    /** 一次探测最多记住几条查询：覆盖全 + 冗余小。 */
    private const val MAX_PLAN_QUERIES = 2

    /** 连续多少次“一条任务都没有”就认为记忆可能失效。 */
    private const val REPROBE_AFTER_EMPTY_STREAK = 3

    /** 探测有结果后，多短的间隔内不再重复探测（防抖）。 */
    private const val MIN_PROBE_INTERVAL_MS = 5_000L

    /** 探测完全没结果后的冷却：这期间直接返回空，不再反复探测。 */
    private const val EMPTY_PROBE_COOLDOWN_MS = 60_000L

    private const val RECENT_WITH_EXCLUDED = 0x0001
    private const val RECENT_IGNORE_UNAVAILABLE = 0x0002
    private const val RECENT_INCLUDE_PROFILES = 0x0004
    private const val RECENT_FLAGS =
        RECENT_WITH_EXCLUDED or RECENT_IGNORE_UNAVAILABLE or RECENT_INCLUDE_PROFILES

    private const val START_SUCCESS = 0
    private const val START_DELIVERED_TO_TOP = 3

    /** Window modes that mean "this task already is a free/small window". */
    private val FREE_WINDOW_MODES = setOf(5, 11, 100, 102, 106)

    data class Task(
        val taskId: Int,
        val packageName: String,
        val component: String,
        val title: String?,
        val windowingMode: Int? = null,
        /** 任务所属用户；-1 = 平台没暴露。多开/分身（如 user 999）靠它区分。 */
        val userId: Int = -1
    )

    // ---------------------------------------------------------------------------------------------
    // 查询候选与记忆
    // ---------------------------------------------------------------------------------------------

    /**
     * 两个真实存在的系统服务。同接口的“另一个代理”与这里等价，不再重复问。
     */
    private enum class ManagerKind(val label: String) {
        ACTIVITY_TASK("at"),
        ACTIVITY("am"),
    }

    private class QueryCandidate(
        val key: String,
        val kind: ManagerKind,
        val run: (Any) -> List<Any>?,
    )

    private class CandidateRun(
        val candidate: QueryCandidate,
        val tasks: List<Task>,
        val durationMs: Long,
    )

    private data class QueryPlan(val keys: List<String>)

    private class ProbeOutcome(val plan: QueryPlan, val tasks: Map<Int, Task>)

    /**
     * 候选顺序 = 探测顺序，也是“全都失败”时的兜底顺序。
     *
     * 每种接口都试同一组方法/参数组合：不同版本上只有一部分存在，失败的会在探测时被记成
     * “不可用”，稳定态不会再试。
     */
    private val queryCandidates: List<QueryCandidate> = buildList {
        for (kind in ManagerKind.entries) {
            val prefix = kind.label
            add(QueryCandidate("$prefix.tasks.3.false.false", kind) { m -> getTasks3(m, MAX_TASKS, false, false) })
            add(QueryCandidate("$prefix.tasks.3.false.true", kind) { m -> getTasks3(m, MAX_TASKS, false, true) })
            add(QueryCandidate("$prefix.tasks.2.1", kind) { m -> getTasks2(m, MAX_TASKS, 1) })
            add(QueryCandidate("$prefix.tasks.2.0", kind) { m -> getTasks2(m, MAX_TASKS, 0) })
            add(QueryCandidate("$prefix.tasks.1", kind) { m -> getTasks1(m, MAX_TASKS) })
            add(QueryCandidate("$prefix.recent.flags.user", kind) { m -> getRecentTasks(m, MAX_TASKS, RECENT_FLAGS, currentUserId()) })
            add(QueryCandidate("$prefix.recent.flags.0", kind) { m -> getRecentTasks(m, MAX_TASKS, RECENT_FLAGS, 0) })
            add(QueryCandidate("$prefix.recent.0.user", kind) { m -> getRecentTasks(m, MAX_TASKS, 0, currentUserId()) })
        }
    }

    private val candidatesByKey: Map<String, QueryCandidate> = queryCandidates.associateBy { it.key }

    /** 全部候选都失败时，下一轮先试这两条（不同接口各一条），避免反复空跑。 */
    private val defaultPlan: QueryPlan = QueryPlan(
        listOf(
            "${ManagerKind.ACTIVITY_TASK.label}.tasks.3.false.false",
            "${ManagerKind.ACTIVITY.label}.recent.flags.user",
        )
    )

    @Volatile
    private var plan: QueryPlan? = null

    @Volatile
    private var lastProbeAtMs = 0L

    @Volatile
    private var nextProbeAllowedAtMs = 0L

    @Volatile
    private var consecutiveEmpty = 0

    private val probeLock = Any()

    // ---------------------------------------------------------------------------------------------
    // 对外查询
    // ---------------------------------------------------------------------------------------------

    /**
     * Reads the live windowingMode of one task without shelling out. Returns null when the
     * platform does not expose the field, so callers can tell "not a free window" from "unknown".
     */
    fun windowingModeOf(taskId: Int): Int? {
        if (taskId <= 0) return null
        // 取一次代理就够了：每次现取都要重新走 ServiceManager + asInterface。
        val manager = primaryTaskManager() ?: return null
        val queries = listOf(
            { getTasks3(manager, MAX_TASKS, false, true) },
            { getTasks3(manager, MAX_TASKS, false, false) },
            { getTasks2(manager, MAX_TASKS, 1) }
        )
        for (query in queries) {
            val raw = runCatching { query() }.getOrNull() ?: continue
            for (item in raw) {
                val id = SystemReflect.readInt(item, "getTaskId", "taskId", "id") ?: continue
                if (id != taskId) continue
                val mode = readWindowingMode(item) ?: continue
                Log.i(TAG, "windowingModeOf($taskId) -> $mode")
                return mode
            }
        }
        return null
    }

    fun isFreeWindowMode(mode: Int?): Boolean = mode != null && mode in FREE_WINDOW_MODES

    /** True only when the task really reports a free window mode; unknown counts as not moved. */
    fun isTaskInFreeWindow(taskId: Int): Boolean = isFreeWindowMode(windowingModeOf(taskId))

    /** Tri-state check used to verify a free window move without guessing. */
    fun freeWindowMoveStatus(taskId: Int): FreeWindowMoveStatus =
        when (val mode = windowingModeOf(taskId)) {
            null -> FreeWindowMoveStatus.UNKNOWN
            else -> if (isFreeWindowMode(mode)) {
                FreeWindowMoveStatus.MOVED
            } else {
                FreeWindowMoveStatus.NOT_MOVED
            }
        }

    fun listTasks(): List<Task> {
        val startedAt = SystemClock.elapsedRealtime()
        var activePlan = plan
        if (activePlan == null) {
            if (startedAt < nextProbeAllowedAtMs) {
                Log.i(TAG, "listTasks: probe cooling down, skip")
                return emptyList()
            }
            val outcome = probeOrNull()
            if (outcome != null) {
                val filtered = outcome.tasks.values
                    .filter { it.packageName !in TaskExclusions.LAUNCHER_AND_SYSTEM }
                noteResultSize(filtered.size)
                Log.i(
                    TAG,
                    "listTasks(probe) size=${filtered.size} plan=${outcome.plan.keys} " +
                        "ms=${SystemClock.elapsedRealtime() - startedAt}"
                )
                return filtered
            }
            // 另一个线程刚好探测完了：直接用它记住的方案。
            activePlan = plan ?: return emptyList()
        }

        // 走到这里 activePlan 一定非空（上面两个分支要么返回、要么重新取到了方案）。
        val planToUse = activePlan
        val merged = runPlan(planToUse)
        if (merged == null) {
            plan = null
            Log.w(TAG, "listTasks: all planned queries failed, re-probe next time")
            return emptyList()
        }
        val filtered = merged.values.filter { it.packageName !in TaskExclusions.LAUNCHER_AND_SYSTEM }
        noteResultSize(filtered.size)
        Log.i(
            TAG,
            "listTasks size=${filtered.size} plan=${planToUse.keys} " +
                "ms=${SystemClock.elapsedRealtime() - startedAt}"
        )
        return filtered
    }

    fun frontTask(): Task? = listTasks().firstOrNull()

    fun findTaskId(identifier: String): Int? {
        val needle = identifier.trim()
        if (needle.isEmpty()) return null
        return listTasks().firstOrNull { task ->
            task.component == needle ||
                task.packageName == needle ||
                task.component.startsWith("$needle/") ||
                task.component.startsWith("$needle.")
        }?.taskId
    }

    fun matchesPackage(task: Task, packageName: String): Boolean {
        val target = packageName.trim()
        if (target.isEmpty()) return false
        return task.packageName == target ||
            task.component.startsWith("$target/") ||
            task.component.startsWith("$target.")
    }

    fun switchToTask(taskId: Int): Boolean {
        if (taskId <= 0) return false
        val atm = primaryTaskManager() ?: return false
        if (startActivityFromRecents(atm, taskId)) {
            Log.i(TAG, "switchToTask($taskId) via startActivityFromRecents")
            return true
        }
        if (moveTaskToFront(atm, taskId)) {
            Log.i(TAG, "switchToTask($taskId) via moveTaskToFront")
            return true
        }
        Log.w(TAG, "switchToTask($taskId) failed")
        return false
    }

    fun removeTask(taskId: Int): Boolean {
        if (taskId <= 0) return false
        val atm = primaryTaskManager() ?: return false
        val removed = runCatching {
            SystemReflect.invoke(atm, "removeTask", taskId) as? Boolean
        }.getOrNull() == true ||
            runCatching {
                SystemReflect.invoke(atm, "removeTask", taskId, true) as? Boolean
            }.getOrNull() == true
        Log.i(TAG, "removeTask($taskId) -> $removed")
        return removed
    }

    fun toShellEntry(task: Task): ShellTaskEntry =
        ShellTaskEntry(
            taskId = task.taskId,
            packageName = task.packageName,
            rawIdentifier = task.component,
            taskTitle = task.title
        )

    // ---------------------------------------------------------------------------------------------
    // 探测 / 执行 / 记忆维护
    // ---------------------------------------------------------------------------------------------

    /**
     * 跑一次全量探测并记住方案。
     *
     * 返回 null 表示「进来时别的线程刚好已经探测完」，调用方应改用 [plan]。
     */
    private fun probeOrNull(): ProbeOutcome? {
        synchronized(probeLock) {
            if (plan != null) return null

            val probeStartedAt = SystemClock.elapsedRealtime()
            val runs = ArrayList<CandidateRun>(queryCandidates.size)
            for (candidate in queryCandidates) {
                val manager = managerFor(candidate.kind) ?: continue
                val queryStartedAt = SystemClock.elapsedRealtime()
                val raw = runCatching { candidate.run(manager) }.getOrNull() ?: continue
                val tasks = raw.mapNotNull(::parseTask).filter { it.taskId > 0 }
                runs += CandidateRun(candidate, tasks, SystemClock.elapsedRealtime() - queryStartedAt)
            }

            val union = LinkedHashMap<Int, Task>()
            for (run in runs) {
                for (task in run.tasks) union.putIfAbsent(task.taskId, task)
            }

            val picked = selectPlan(runs, union.keys)
            val selected = if (picked.isEmpty()) defaultPlan else QueryPlan(picked.map { it.candidate.key })
            plan = selected
            lastProbeAtMs = SystemClock.elapsedRealtime()
            nextProbeAllowedAtMs = lastProbeAtMs +
                if (union.isEmpty()) EMPTY_PROBE_COOLDOWN_MS else MIN_PROBE_INTERVAL_MS
            consecutiveEmpty = 0

            Log.i(
                TAG,
                "probe: candidates=${runs.size}/${queryCandidates.size} " +
                    "union=${union.size} plan=${selected.keys} " +
                    "ms=${SystemClock.elapsedRealtime() - probeStartedAt}"
            )
            // 探测已经把每条候选的任务解析过一遍；如果选中的候选里已经有结果，直接复用，
            // 不再为当前这次调用重查一遍。
            val reused = LinkedHashMap<Int, Task>()
            val pickedKeys = picked.map { it.candidate.key }.toSet()
            for (run in runs) {
                if (run.candidate.key !in pickedKeys) continue
                for (task in run.tasks) reused.putIfAbsent(task.taskId, task)
            }
            return ProbeOutcome(selected, reused)
        }
    }

    /**
     * 选出「最小覆盖集」：先取覆盖任务最多的候选；还缺任务再补一条；最多 [MAX_PLAN_QUERIES] 条。
     * 打平时依次比字段完整率、耗时。
     */
    private fun selectPlan(runs: List<CandidateRun>, union: Set<Int>): List<CandidateRun> {
        if (union.isEmpty()) return emptyList()
        val usable = runs.filter { it.tasks.isNotEmpty() }
        if (usable.isEmpty()) return emptyList()

        val picked = ArrayList<CandidateRun>(MAX_PLAN_QUERIES)
        val covered = HashSet<Int>()

        while (picked.size < MAX_PLAN_QUERIES && covered.size < union.size) {
            var best: CandidateRun? = null
            var bestGain = 0
            var bestCompleteness = -1f
            var bestDuration = Long.MAX_VALUE
            for (run in usable) {
                if (picked.any { it.candidate.key == run.candidate.key }) continue
                val gain = run.tasks.count { it.taskId !in covered }
                if (gain <= 0) continue
                val completeness = completenessOf(run.tasks)
                val better = gain > bestGain ||
                    (gain == bestGain && completeness > bestCompleteness) ||
                    (gain == bestGain && completeness == bestCompleteness && run.durationMs < bestDuration)
                if (better) {
                    best = run
                    bestGain = gain
                    bestCompleteness = completeness
                    bestDuration = run.durationMs
                }
            }
            val chosen = best ?: break
            picked += chosen
            chosen.tasks.forEach { covered += it.taskId }
        }
        return picked
    }

    private fun completenessOf(tasks: List<Task>): Float {
        if (tasks.isEmpty()) return 0f
        var score = 0
        for (task in tasks) {
            if (task.component.contains('/')) score++
            if (!task.title.isNullOrBlank()) score++
            if (task.userId >= 0) score++
        }
        return score / (tasks.size * 3f)
    }

    /** 按记住的方案跑；全部查询都失败时返回 null（由调用方清掉记忆并重探）。 */
    private fun runPlan(activePlan: QueryPlan): Map<Int, Task>? {
        val merged = LinkedHashMap<Int, Task>()
        var failures = 0
        for (key in activePlan.keys) {
            val candidate = candidatesByKey[key] ?: continue
            val manager = managerFor(candidate.kind)
            val raw = if (manager == null) {
                null
            } else {
                runCatching { candidate.run(manager) }.getOrNull()
            }
            if (raw == null) {
                failures++
                continue
            }
            for (item in raw) {
                parseTask(item)?.let { task ->
                    if (task.taskId > 0) merged.putIfAbsent(task.taskId, task)
                }
            }
        }
        return if (failures == activePlan.keys.size) null else merged
    }

    private fun noteResultSize(size: Int) {
        if (size > 0) {
            consecutiveEmpty = 0
            return
        }
        consecutiveEmpty++
        if (consecutiveEmpty >= REPROBE_AFTER_EMPTY_STREAK && plan != null) {
            plan = null
            Log.i(TAG, "empty streak=$consecutiveEmpty → will re-probe")
        }
    }

    // ---------------------------------------------------------------------------------------------
    // 解析
    // ---------------------------------------------------------------------------------------------

    private fun parseTask(raw: Any): Task? {
        val taskId = SystemReflect.readInt(raw, "getTaskId", "getId", "taskId", "id")
            ?: SystemReflect.readField(raw, "key")?.let { key ->
                SystemReflect.readInt(key, "id", "taskId")
            }
            ?: return null
        if (taskId <= 0) return null

        val component = SystemReflect.readComponent(
            raw,
            "getTopActivity",
            "getRealActivity",
            "getBaseActivity",
            "getOrigActivity",
            "topActivity",
            "realActivity",
            "baseActivity",
            "origActivity"
        )
            ?: SystemReflect.readComponentFromInfo(raw, "topActivityInfo", "baseActivityInfo")
            ?: (SystemReflect.readField(raw, "baseIntent") as? Intent)?.component

        val packageName = component?.packageName
            ?: SystemReflect.readString(raw, "packageName", "processName")
            ?: (SystemReflect.readField(raw, "baseIntent") as? Intent)?.`package`
            ?: return null

        val componentName = component?.flattenToShortString() ?: packageName
        val title = readTitle(raw)
        val userId = SystemReflect.readInt(raw, "userId", "getUserId") ?: -1
        return Task(taskId, packageName, componentName, title, readWindowingMode(raw), userId)
    }

    private fun readWindowingMode(raw: Any): Int? =
        SystemReflect.readInt(raw, "windowingMode", "getWindowingMode")

    private fun readTitle(raw: Any): String? {
        val description = SystemReflect.invoke(raw, "getTaskDescription")
            ?: SystemReflect.readField(raw, "taskDescription")
        return description?.let {
            SystemReflect.readCharSequence(it, "getLabel", "label")?.toString()?.trim()
        }?.takeIf { it.isNotEmpty() }
    }

    private fun getRecentTasks(manager: Any, maxNum: Int, flags: Int, userId: Int): List<Any>? =
        SystemReflect.unwrapList(
            SystemReflect.invoke(manager, "getRecentTasks", maxNum, flags, userId)
        )

    private fun getTasks3(
        manager: Any,
        maxNum: Int,
        filterOnlyVisibleRecents: Boolean,
        keepIntentExtra: Boolean
    ): List<Any>? =
        SystemReflect.unwrapList(
            SystemReflect.invoke(
                manager,
                "getTasks",
                maxNum,
                filterOnlyVisibleRecents,
                keepIntentExtra
            )
        )

    private fun getTasks2(manager: Any, maxNum: Int, flags: Int): List<Any>? =
        SystemReflect.unwrapList(SystemReflect.invoke(manager, "getTasks", maxNum, flags))

    private fun getTasks1(manager: Any, maxNum: Int): List<Any>? =
        SystemReflect.unwrapList(SystemReflect.invoke(manager, "getTasks", maxNum))

    private fun startActivityFromRecents(atm: Any, taskId: Int): Boolean {
        val options = ActivityOptions.makeBasic().toBundle() ?: Bundle()
        val result = SystemReflect.invoke(atm, "startActivityFromRecents", taskId, options) as? Number
            ?: return false
        val code = result.toInt()
        return code in START_SUCCESS..START_DELIVERED_TO_TOP
    }

    private fun moveTaskToFront(atm: Any, taskId: Int): Boolean =
        runCatching {
            SystemReflect.invoke(atm, "moveTaskToFront", taskId, 0, null)
            true
        }.getOrDefault(false)

    // ---------------------------------------------------------------------------------------------
    // 系统服务代理（每个接口只取一个）
    // ---------------------------------------------------------------------------------------------

    private fun managerFor(kind: ManagerKind): Any? = when (kind) {
        ManagerKind.ACTIVITY_TASK -> activityTaskManager()
        ManagerKind.ACTIVITY -> activityManager()
    }

    private fun primaryTaskManager(): Any? =
        activityTaskManager() ?: activityManager()

    private fun activityTaskManager(): Any? =
        runCatching { bindActivityTaskManager() }.getOrNull()
            ?: runCatching {
                val clazz = Class.forName("android.app.ActivityTaskManager")
                clazz.getMethod("getService").invoke(null)
            }.getOrNull()

    private fun activityManager(): Any? =
        runCatching { bindActivityManager() }.getOrNull()
            ?: runCatching {
                val clazz = Class.forName("android.app.ActivityManager")
                clazz.getMethod("getService").invoke(null)
            }.getOrNull()

    // 同接口的重复代理已经去掉：每个接口只取一个，避免同一份数据被问两遍。
    // （windowingModeOf / 旧调用点仍按“两个接口各一个”的顺序尝试。）

    @SuppressLint("PrivateApi") // IActivityTaskManager via ServiceManager
    private fun bindActivityTaskManager(): Any {
        val serviceManager = Class.forName("android.os.ServiceManager")
        val binder = serviceManager.getMethod("getService", String::class.java)
            .invoke(null, "activity_task") as IBinder
        val stubClass = Class.forName("android.app.IActivityTaskManager\$Stub")
        return stubClass.getMethod("asInterface", IBinder::class.java).invoke(null, binder)
            ?: error("IActivityTaskManager unavailable")
    }

    @SuppressLint("PrivateApi") // IActivityManager via ServiceManager
    private fun bindActivityManager(): Any {
        val serviceManager = Class.forName("android.os.ServiceManager")
        val binder = serviceManager.getMethod("getService", String::class.java)
            .invoke(null, "activity") as IBinder
        val stubClass = Class.forName("android.app.IActivityManager\$Stub")
        return stubClass.getMethod("asInterface", IBinder::class.java).invoke(null, binder)
            ?: error("IActivityManager unavailable")
    }

    private fun currentUserId(): Int = runCatching {
        Process.myUserHandle().javaClass.getMethod("getIdentifier")
            .invoke(Process.myUserHandle()) as Int
    }.getOrDefault(0)
}
