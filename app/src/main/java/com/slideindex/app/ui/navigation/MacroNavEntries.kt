package com.slideindex.app.ui.navigation

import android.content.Context
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.slideindex.app.R
import com.slideindex.app.macro.Macro
import com.slideindex.app.macro.MacroEngine
import com.slideindex.app.macro.MacroStore
import com.slideindex.app.settings.macro.MacroRecordingLauncher
import com.slideindex.app.settings.macro.MacroRecordingStart
import com.slideindex.app.settings.macro.MacroTargetApp
import com.slideindex.app.settings.macro.MacroValidation
import com.slideindex.app.settings.macro.ui.MacroEditScreen
import com.slideindex.app.settings.macro.ui.MacroListScreen
import com.slideindex.app.settings.macro.ui.MacroTargetAppPickScreen
import top.yukonga.miuix.kmp.nav.core.NavEntryBuilder

/**
 * 列表页的"脏标记"：`MacroStore` 不是可观察流，界面靠改这个计数来重新读盘。
 */
private var macroListRevision by mutableIntStateOf(0)

/** 是否正在录制：录制器回调驱动，编辑页据此切换帮助文案。 */
private var macroRecording by mutableStateOf(false)

/**
 * 编辑页的**草稿**。
 *
 * 关键约定：编辑页只改内存（改名 / 选目标 App 都不落盘），
 * **只有「保存」和「开始录制」才写盘**；「取消」直接丢掉草稿 —— 磁盘上什么都没发生。
 *
 * （例外说明：录制器跑在无障碍服务里、按宏 id 读磁盘回填步骤，所以"开始录制"那一下必须先落盘。）
 */
private class MacroDraft(
    val id: String,
    val createdAtMs: Long,
    name: String,
    targetPackage: String,
    /** 磁盘上原本的值（用于判断"到底改了没有"）；新建草稿传空串。 */
    private val initialName: String,
    private val initialTargetPackage: String,
    /** 是不是「+」新建出来的（磁盘上还没有这条宏）。 */
    val isNew: Boolean,
) {
    var name by mutableStateOf(name)
    var targetPackage by mutableStateOf(targetPackage)

    /** 是否已经真的写进过磁盘（保存过 / 开始录制过）。 */
    var persisted by mutableStateOf(false)

    /**
     * 用户到底动过没有。
     *
     * 没动过的时候「保存」应该是灰的 —— 否则点一下就会在列表里多出一条什么都没填的空宏，
     * 或者把 `updatedAtMs` 白白刷新一遍。
     */
    fun hasEdits(): Boolean = if (isNew) {
        targetPackage.isNotBlank() || (name.isNotBlank() && name != Macro.FALLBACK_NAME)
    } else {
        name != initialName || targetPackage != initialTargetPackage
    }

    fun toMacro(): Macro = Macro(
        id = id,
        name = MacroValidation.normalizeName(name).ifBlank { Macro.FALLBACK_NAME },
        targetPackage = targetPackage,
        createdAtMs = createdAtMs,
        updatedAtMs = System.currentTimeMillis(),
    )
}

/** 当前编辑中的草稿（同一时刻只可能有一个编辑页）。 */
private var macroDraft by mutableStateOf<MacroDraft?>(null)

/** 录制器把结果回传给界面用的 listener（同一时刻只可能有一次录制）。 */
private val macroRecordingListener = object : MacroRecordingStart.Listener {
    override fun onRecordingStarted() {
        macroRecording = true
    }

    override fun onRecordingFinished(savedSteps: Int) {
        macroRecording = false
        // 录制器自己已经落盘，这里只要让列表重新读一遍。
        if (savedSteps > 0) {
            macroDraft?.persisted = true
            macroListRevision++
        }
    }
}

/**
 * 「手势宏」：列表页 → 编辑页 →（目标 App 选择页）。
 *
 * 编辑页是**草稿**语义：改名 / 选目标 App 都只改内存，点「保存」才落盘，「取消」等于什么都没做。
 * 唯一例外是「开始录制」—— 录制器按 id 读磁盘，所以那一步会先把草稿写进磁盘。
 */
fun NavEntryBuilder.macroNavEntries(ctx: MainNavContext) {
    hiltEntry<AppNavKey.MacroList> {
        val context = LocalContext.current
        var macros by remember { mutableStateOf(emptyList<Macro>()) }
        // 读盘放在 LaunchedEffect 里：磁盘访问不放在组合阶段。
        LaunchedEffect(macroListRevision) {
            macros = MacroStore.all(context)
        }
        MacroListScreen(
            macros = macros,
            onBack = { ctx.navigateBack() },
            onCreate = { createMacroDraftAndOpen(ctx) },
            onOpenMacro = { macroId -> ctx.navigate(AppNavKey.MacroEdit(macroId)) },
            onDeleteMacro = { macroId ->
                MacroStore.delete(context, macroId)
                // 删掉的正好是当前草稿就一起丢掉，避免编辑页停在一条已不存在的宏上。
                if (macroDraft?.id == macroId) macroDraft = null
                macroListRevision++
            },
        )
    }

    hiltEntry<AppNavKey.MacroEdit> { key ->
        val context = LocalContext.current
        var draft by remember { mutableStateOf(macroDraft?.takeIf { it.id == key.macroId }) }
        LaunchedEffect(key.macroId) {
            if (draft == null) {
                val existing = MacroStore.byId(context, key.macroId)
                draft = if (existing != null) {
                    MacroDraft(
                        id = existing.id,
                        createdAtMs = existing.createdAtMs,
                        name = existing.name,
                        targetPackage = existing.targetPackage,
                        initialName = existing.name,
                        initialTargetPackage = existing.targetPackage,
                        isNew = false,
                    ).also { it.persisted = true }
                } else {
                    // 编辑一条 id 存在但磁盘上还没有的宏：当新草稿处理（正常只在新建流程里出现）。
                    MacroDraft(
                        id = key.macroId,
                        createdAtMs = System.currentTimeMillis(),
                        name = Macro.FALLBACK_NAME,
                        targetPackage = "",
                        initialName = "",
                        initialTargetPackage = "",
                        isNew = true,
                    )
                }
                macroDraft = draft
            }
        }
        val current = draft
        if (current == null) return@hiltEntry

        MacroEditScreen(
            context = context,
            titleResId = R.string.macro_editor_title,
            name = current.name,
            targetPackage = current.targetPackage,
            recording = macroRecording,
            saveEnabled = current.hasEdits(),
            onNameChange = { text -> current.name = text },
            onPickTargetApp = { ctx.navigate(AppNavKey.MacroTargetApp(current.id)) },
            onStartRecording = { startMacroRecording(ctx, context, current) },
            onTestRun = {
                MacroEngine(context.applicationContext)
                    .run(MacroStore.byId(context, current.id) ?: current.toMacro())
            },
            onSave = { saveDraftAndBack(ctx, context, current) },
            onBack = {
                // 「取消」= 丢掉草稿：没保存过的东西磁盘上本来就没有，什么都不用删。
                macroDraft = null
                ctx.navigateBackTo(AppNavKey.MacroList)
            },
        )
    }

    hiltEntry<AppNavKey.MacroTargetApp> { key ->
        val context = LocalContext.current
        val draft = macroDraft?.takeIf { it.id == key.macroId }
        MacroTargetAppPickScreen(
            selectedTarget = draft?.targetPackage
                ?: MacroStore.byId(context, key.macroId)?.targetPackage.orEmpty(),
            onBack = { ctx.navigateBackTo(AppNavKey.MacroEdit(key.macroId)) },
            onSelect = { app -> applyMacroTargetApp(ctx, context, key.macroId, app) },
        )
    }
}

/** 新建：**只在内存里**开一份草稿（磁盘上暂时什么都没有），进编辑页。 */
private fun createMacroDraftAndOpen(ctx: MainNavContext) {
    macroDraft = MacroDraft(
        id = MacroStore.newId(),
        createdAtMs = System.currentTimeMillis(),
        name = Macro.FALLBACK_NAME,
        targetPackage = "",
        initialName = "",
        initialTargetPackage = "",
        isNew = true,
    )
    ctx.navigate(AppNavKey.MacroEdit(macroDraft!!.id))
}

/** 选目标 App：写回**草稿**（不落盘），然后回编辑页。 */
private fun applyMacroTargetApp(
    ctx: MainNavContext,
    context: Context,
    macroId: String,
    app: com.slideindex.app.data.AppInfo,
) {
    val draft = macroDraft?.takeIf { it.id == macroId }
    if (draft != null) {
        draft.targetPackage = com.slideindex.app.launcher.AppTarget.encode(app.packageName, app.cloneUserId)
    } else {
        // 兜底（没有草稿时，例如异常路径）：直接按旧行为写盘，别让选择丢掉。
        val current = MacroStore.byId(context, macroId) ?: return
        MacroStore.upsert(
            context,
            current.copy(
                targetPackage = com.slideindex.app.launcher.AppTarget.encode(app.packageName, app.cloneUserId),
                updatedAtMs = System.currentTimeMillis(),
            ),
        )
        macroListRevision++
    }
    ctx.navigateBackTo(AppNavKey.MacroEdit(macroId))
}

/** 把草稿写进磁盘（保存 / 开始录制时调用）。 */
private fun persistDraft(context: Context, draft: MacroDraft) {
    val existing = MacroStore.byId(context, draft.id)
    val base = existing ?: draft.toMacro()
    MacroStore.upsert(
        context,
        base.copy(
            name = MacroValidation.normalizeName(draft.name).ifBlank { Macro.FALLBACK_NAME },
            targetPackage = draft.targetPackage,
            updatedAtMs = System.currentTimeMillis(),
        ),
    )
    draft.persisted = true
    macroListRevision++
}

/** 保存：名字必填（编辑页已拦一次，这里再兜一层）→ 落盘 → 丢掉草稿 → 回列表。 */
private fun saveDraftAndBack(
    ctx: MainNavContext,
    context: Context,
    draft: MacroDraft,
) {
    // 什么都没改：不写盘、不刷新列表，直接回列表（按钮本来也是灰的，这里再兜一层）。
    if (!draft.hasEdits()) {
        macroDraft = null
        ctx.navigateBackTo(AppNavKey.MacroList)
        return
    }
    if (!MacroValidation.isNameValid(draft.name)) {
        ctx.deps.userMessageBus.showError(
            context.getString(R.string.macro_error_name_required),
        )
        return
    }
    persistDraft(context, draft)
    macroDraft = null
    ctx.navigateBackTo(AppNavKey.MacroList)
}

/**
 * 「开始录制」的**调用点**。
 *
 * 这里只依赖窄接口 [MacroRecordingStart]（见 `feature/settings` 的
 * `MacroRecordingLauncher`）：P2 的录制器落地后把实现塞进 `MacroRecordingLauncher.impl`
 * 就接通了。没接线时给用户一句明确的提示，而不是静默失败。
 *
 * 注意：录制器按宏 id 读磁盘，所以录制前**必须**先把草稿落盘（这是"不点保存就不落盘"的唯一例外）。
 */
private fun startMacroRecording(
    ctx: MainNavContext,
    context: Context,
    draft: MacroDraft,
) {
    val launcher = MacroRecordingLauncher.impl
    if (launcher == null) {
        ctx.deps.userMessageBus.showError(
            context.getString(R.string.macro_recording_unavailable),
        )
        return
    }
    persistDraft(context, draft)
    val started = launcher.start(
        context = context,
        macroId = draft.id,
        targetPackage = draft.targetPackage,
        macroName = MacroValidation.normalizeName(draft.name).ifBlank { Macro.FALLBACK_NAME },
        listener = macroRecordingListener,
    )
    if (started) {
        macroRecording = true
    } else {
        ctx.deps.userMessageBus.showError(
            context.getString(R.string.macro_recording_start_failed),
        )
    }
}
