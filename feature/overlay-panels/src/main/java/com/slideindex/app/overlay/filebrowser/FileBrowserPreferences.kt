package com.slideindex.app.overlay.filebrowser

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.fileBrowserPrefsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "slide_index_file_browser_prefs",
)

@Serializable
data class FileBrowserPrefs(
    val pinnedPaths: List<String> = emptyList(),
    val lastPath: String? = null,
    val sortOrder: String = FileSortOrder.NAME_ASC.name,
    val showHidden: Boolean = false,
    val tabs: List<String> = emptyList(),
    val activeTabIndex: Int = 0,
    val hiddenShortcuts: List<String> = emptyList(),
    val shortcutOrder: List<String> = emptyList(),
)

object FileBrowserPreferences {
    private const val KEY_NAME = "file_browser_prefs"
    private const val MAX_PINS = 8
    private val key = stringPreferencesKey(KEY_NAME)
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    var current by mutableStateOf(FileBrowserPrefs())
        private set

    private var appContext: Context? = null
    private var loaded = false

    fun attach(
        context: Context,
        onLoaded: ((FileBrowserPrefs) -> Unit)? = null,
    ) {
        appContext = context.applicationContext
        if (loaded) {
            onLoaded?.invoke(current)
            return
        }
        loaded = true
        val ctx = context.applicationContext
        scope.launch {
            val prefs = read(ctx)
            current = prefs
            onLoaded?.invoke(prefs)
        }
    }

    fun pinnedOrDefault(internalRoot: String): List<String> {
        val pins = current.pinnedPaths
            .map { FileBrowserPaths.normalize(it, internalRoot) }
            .distinct()
            .take(MAX_PINS)
        return if (pins.isNotEmpty()) pins else defaultPins(internalRoot)
    }

    fun defaultPins(internalRoot: String): List<String> {
        val root = FileBrowserPaths.normalize(internalRoot, internalRoot)
        return listOf(
            "/",
            root,
            "$root/Android/data",
            "$root/Download",
        )
    }

    fun defaultShortcuts(internalRoot: String): List<String> {
        val root = FileBrowserPaths.normalize(internalRoot, internalRoot)
        return (defaultPins(root) + "$root/Android/obb")
            .map { FileBrowserPaths.normalize(it, root) }
    }

    private fun allShortcuts(internalRoot: String): List<String> {
        val root = FileBrowserPaths.normalize(internalRoot, internalRoot)
        val valid = (defaultShortcuts(root) + userPinned(root)).distinct()
        val stored = current.shortcutOrder
            .map { FileBrowserPaths.normalize(it, root) }
            .filter { it in valid }
        val missingDefaults = defaultShortcuts(root).filterNot { it in stored }
        val missingUser = userPinned(root).filterNot { it in stored }
        return (stored + missingDefaults + missingUser).distinct()
    }

    fun togglePin(path: String) {
        val root = FileBrowserStorage.internalStoragePath()
        val normalized = FileBrowserPaths.normalize(path, root)
        if (isSystemShortcut(normalized, root)) {
            if (normalized in hiddenSystemShortcuts()) {
                unhideShortcut(normalized)
            } else {
                hideShortcut(normalized)
            }
            return
        }
        val existing = userPinned(root).toMutableList()
        val order = allShortcuts(root).toMutableList()
        if (existing.contains(normalized)) {
            existing.remove(normalized)
            order.remove(normalized)
        } else {
            existing.add(normalized)
            if (normalized !in order) order.add(normalized)
        }
        update {
            it.copy(
                pinnedPaths = existing.distinct().take(MAX_PINS),
                shortcutOrder = order.distinct(),
            )
        }
    }

    fun isPinned(path: String): Boolean {
        val normalized = FileBrowserPaths.normalize(path)
        val root = FileBrowserStorage.internalStoragePath()
        if (isSystemShortcut(normalized, root)) {
            return normalized !in hiddenSystemShortcuts()
        }
        return userPinned(root).contains(normalized)
    }

    fun removeShortcut(path: String) {
        val root = FileBrowserStorage.internalStoragePath()
        val normalized = FileBrowserPaths.normalize(path, root)
        if (isSystemShortcut(normalized, root)) {
            hideShortcut(normalized)
        } else {
            update {
                it.copy(
                    pinnedPaths = userPinned(root)
                        .filterNot { item -> item == normalized },
                    shortcutOrder = allShortcuts(root)
                        .filterNot { item -> item == normalized },
                )
            }
        }
    }

    fun userPinned(internalRoot: String): List<String> {
        val root = FileBrowserPaths.normalize(internalRoot, internalRoot)
        return current.pinnedPaths
            .map { FileBrowserPaths.normalize(it, root) }
            .filterNot { isSystemShortcut(it, root) }
            .distinct()
            .take(MAX_PINS)
    }

    fun rememberTabs(paths: List<String>, activeIndex: Int) {
        val normalized = paths.map(::normalizeStored).distinct().take(6)
        if (normalized.isEmpty()) return
        update {
            it.copy(
                tabs = normalized,
                activeTabIndex = activeIndex.coerceIn(0, normalized.lastIndex),
            )
        }
    }

    private fun normalizeStored(path: String): String =
        FileBrowserMedia.normalizeLocation(path, FileBrowserStorage.internalStoragePath())

    fun movePin(from: Int, to: Int, internalRoot: String) {
        val base = userPinned(internalRoot).toMutableList()
        if (from !in base.indices || to !in base.indices || from == to) return
        val item = base.removeAt(from)
        base.add(to, item)
        update { it.copy(pinnedPaths = base.take(MAX_PINS)) }
    }

    fun hiddenSystemShortcuts(): List<String> =
        current.hiddenShortcuts.map { FileBrowserPaths.normalize(it) }.distinct()

    fun isSystemShortcut(path: String, internalRoot: String): Boolean {
        val normalized = FileBrowserPaths.normalize(path, internalRoot)
        return defaultPins(internalRoot).any {
            FileBrowserPaths.normalize(it, internalRoot) == normalized
        } || normalized == FileBrowserPaths.normalize("$internalRoot/Android/obb", internalRoot)
    }

    fun hideShortcut(path: String) {
        val normalized = FileBrowserPaths.normalize(path)
        update {
            it.copy(hiddenShortcuts = (it.hiddenShortcuts + normalized).distinct())
        }
    }

    fun unhideShortcut(path: String) {
        val normalized = FileBrowserPaths.normalize(path)
        update {
            it.copy(hiddenShortcuts = it.hiddenShortcuts.filterNot { item ->
                FileBrowserPaths.normalize(item) == normalized
            })
        }
    }

    fun resetHiddenShortcuts() {
        update { it.copy(hiddenShortcuts = emptyList()) }
    }

    fun quickShortcuts(internalRoot: String): List<String> {
        val hidden = hiddenSystemShortcuts()
        return allShortcuts(internalRoot)
            .filterNot { it in hidden }
            .take(MAX_PINS)
    }

    /**
     * 按“可见列表”的 from/to 移动，内部映射回完整顺序，
     * 这样隐藏项不会破坏排序索引。
     */
    fun moveShortcut(fromVisible: Int, toVisible: Int, internalRoot: String) {
        val order = allShortcuts(internalRoot).toMutableList()
        val hidden = hiddenSystemShortcuts()
        val visible = order.filterNot { it in hidden }
        if (fromVisible !in visible.indices || toVisible !in visible.indices) return
        if (fromVisible == toVisible) return
        val fromPath = visible[fromVisible]
        val toPath = visible[toVisible]
        val fromIndex = order.indexOf(fromPath)
        val toIndex = order.indexOf(toPath)
        if (fromIndex < 0 || toIndex < 0) return
        val item = order.removeAt(fromIndex)
        order.add(toIndex, item)
        update { it.copy(shortcutOrder = order) }
    }

    fun rememberLastPath(path: String) {
        val normalized = normalizeStored(path)
        if (current.lastPath == normalized) return
        update { it.copy(lastPath = normalized) }
    }

    fun setSort(order: FileSortOrder) {
        update { it.copy(sortOrder = order.name) }
    }

    fun setShowHidden(show: Boolean) {
        update { it.copy(showHidden = show) }
    }

    private fun update(transform: (FileBrowserPrefs) -> FileBrowserPrefs) {
        current = sanitize(transform(current))
        val ctx = appContext ?: return
        val snapshot = current
        scope.launch { write(ctx, snapshot) }
    }

    private fun sanitize(prefs: FileBrowserPrefs): FileBrowserPrefs = prefs.copy(
        pinnedPaths = prefs.pinnedPaths
            .map { FileBrowserPaths.normalize(it) }
            .filter { it.startsWith("/") }
            .distinct()
            .take(MAX_PINS),
        lastPath = prefs.lastPath
            ?.takeIf { it.startsWith("/") || FileBrowserMediaCategory.isMediaPath(it) },
        tabs = prefs.tabs
            .map(::normalizeStored)
            .distinct()
            .take(6),
        activeTabIndex = prefs.activeTabIndex.coerceAtLeast(0),
        shortcutOrder = prefs.shortcutOrder
            .map { FileBrowserPaths.normalize(it) }
            .filter { it.startsWith("/") }
            .distinct(),
    )

    private suspend fun read(context: Context): FileBrowserPrefs {
        val raw = runCatching { context.fileBrowserPrefsDataStore.data.first()[key] }
            .getOrNull()
            ?: return FileBrowserPrefs()
        val decoded = runCatching { json.decodeFromString<FileBrowserPrefs>(raw) }
            .getOrDefault(FileBrowserPrefs())
        return sanitize(decoded)
    }

    private suspend fun write(context: Context, prefs: FileBrowserPrefs) {
        runCatching {
            context.fileBrowserPrefsDataStore.edit { mutable ->
                mutable[key] = json.encodeToString(sanitize(prefs))
            }
        }
    }

    /** 备份用：DataStore 里的原始 JSON；没有存过时返回空串。 */
    suspend fun exportRawJson(context: Context): String =
        runCatching { context.fileBrowserPrefsDataStore.data.first()[key] }
            .getOrNull()
            .orEmpty()

    /** 恢复用：整份写回并同步内存态（面板下次打开就是恢复后的值）。 */
    suspend fun importRawJson(context: Context, raw: String) {
        if (raw.isBlank()) return
        val decoded = runCatching { json.decodeFromString<FileBrowserPrefs>(raw) }
            .getOrNull() ?: return
        appContext = context.applicationContext
        loaded = true
        current = sanitize(decoded)
        write(context, current)
    }
}

fun FileBrowserPrefs.sortOrderOrDefault(): FileSortOrder =
    FileSortOrder.entries.firstOrNull { it.name == sortOrder } ?: FileSortOrder.NAME_ASC
