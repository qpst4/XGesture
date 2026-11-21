package com.slideindex.app.shortcuts

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Build
import android.os.UserHandle
import android.os.UserManager
import android.provider.Settings
import com.slideindex.app.activity.ActivityShortcut
import com.slideindex.app.privilege.PrivilegeGateway
import com.slideindex.app.settings.PrivilegeMode
import com.slideindex.app.ui.ShortcutTestMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 动态快捷方式与全局快捷方式数据源模式核心管理器。
 *
 * 1. 维护全局快捷方式数据源模式（免特权静态 / 特权抓取 / 桌面固化）与特权子模式（Shizuku / Root）的持久化；
 * 2. 负责桌面角色权限检查、动态快捷方式扫描、系统级 [LauncherApps.pinShortcuts] 锁定；
 * 3. 将已 Pin 固化的动态快捷方式持久化保存在本地，使切回原默认桌面后依然可长期枚举与通过 [LauncherApps.startShortcut] 启动。
 */
object DynamicShortcutsManager {

    private const val PREFS_NAME = "slide_index_shortcut_source_prefs"
    private const val KEY_TEST_MODE = "key_shortcut_test_mode"
    private const val KEY_PRIVILEGE_SUB_MODE = "key_shortcut_privilege_sub_mode"
    private const val KEY_PINNED_RECORDS_JSON = "key_pinned_dynamic_shortcuts_json"

    private val jsonCodec = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Volatile
    private var initialized = false

    private val _testModeFlow = MutableStateFlow(ShortcutTestMode.PRIVILEGE)
    val testModeFlow: StateFlow<ShortcutTestMode> = _testModeFlow.asStateFlow()

    private val _privilegeSubModeFlow = MutableStateFlow(PrivilegeGateway.mode)
    val privilegeSubModeFlow: StateFlow<PrivilegeMode> = _privilegeSubModeFlow.asStateFlow()

    private val _pinnedRecordsFlow = MutableStateFlow<List<PinnedDynamicShortcutRecord>>(emptyList())
    val pinnedRecordsFlow: StateFlow<List<PinnedDynamicShortcutRecord>> = _pinnedRecordsFlow.asStateFlow()

    @Serializable
    data class PinnedDynamicShortcutRecord(
        val packageName: String,
        val shortcutId: String,
        val label: String,
        val appLabel: String,
        val userSerial: Long = 0L,
        val intentUris: List<String> = emptyList(),
    )

    data class DiscoveredDynamicShortcut(
        val packageName: String,
        val shortcutId: String,
        val label: String,
        val appLabel: String,
        val isPinned: Boolean,
        val userHandle: UserHandle,
        val userSerial: Long = 0L,
        val intentUris: List<String> = emptyList(),
    )

    fun ensureInitialized(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val savedModeName = prefs.getString(KEY_TEST_MODE, null)
            _testModeFlow.value = savedModeName?.let {
                runCatching { ShortcutTestMode.valueOf(it) }.getOrNull()
            } ?: ShortcutTestMode.PRIVILEGE

            val savedPrivName = prefs.getString(KEY_PRIVILEGE_SUB_MODE, null)
            _privilegeSubModeFlow.value = savedPrivName?.let {
                runCatching { PrivilegeMode.valueOf(it) }.getOrNull()
            } ?: PrivilegeGateway.mode

            val rawJson = prefs.getString(KEY_PINNED_RECORDS_JSON, null).orEmpty()
            if (rawJson.isNotBlank()) {
                _pinnedRecordsFlow.value = runCatching {
                    jsonCodec.decodeFromString<List<PinnedDynamicShortcutRecord>>(rawJson)
                }.getOrDefault(emptyList())
            }
            initialized = true
        }
    }

    fun getTestMode(context: Context): ShortcutTestMode {
        ensureInitialized(context)
        return _testModeFlow.value
    }

    fun setTestMode(context: Context, mode: ShortcutTestMode) {
        ensureInitialized(context)
        _testModeFlow.value = mode
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TEST_MODE, mode.name)
            .apply()
    }

    fun getPrivilegeSubMode(context: Context): PrivilegeMode {
        ensureInitialized(context)
        return _privilegeSubModeFlow.value
    }

    fun setPrivilegeSubMode(context: Context, mode: PrivilegeMode) {
        ensureInitialized(context)
        _privilegeSubModeFlow.value = mode
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PRIVILEGE_SUB_MODE, mode.name)
            .apply()
    }

    fun getPinnedRecords(context: Context): List<PinnedDynamicShortcutRecord> {
        ensureInitialized(context)
        return _pinnedRecordsFlow.value
    }

    fun isPinnedDynamicShortcut(context: Context, packageName: String, shortcutId: String?): Boolean {
        if (packageName.isBlank() || shortcutId.isNullOrBlank()) return false
        ensureInitialized(context)
        return _pinnedRecordsFlow.value.any { it.packageName == packageName && it.shortcutId == shortcutId }
    }

    private fun savePinnedRecords(
        context: Context,
        records: List<PinnedDynamicShortcutRecord>,
        preserveOrder: Boolean = false,
    ) {
        val deduplicated = records
            .distinctBy { "${it.packageName}/${it.userSerial}/${it.shortcutId}" }
            .let { list ->
                if (preserveOrder) list else list.sortedWith(compareBy({ it.appLabel }, { it.label }))
            }
        _pinnedRecordsFlow.value = deduplicated
        val encoded = runCatching { jsonCodec.encodeToString(deduplicated) }.getOrDefault("[]")
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PINNED_RECORDS_JSON, encoded)
            .apply()
    }

    fun removePinnedRecord(context: Context, packageName: String, shortcutId: String) {
        ensureInitialized(context)
        val filtered = _pinnedRecordsFlow.value.filterNot {
            it.packageName == packageName && it.shortcutId == shortcutId
        }
        savePinnedRecords(context, filtered, preserveOrder = true)
    }

    /**
     * 批量删除已固化的快捷方式，并在当前具备桌面宿主权限时同步解除系统级 Pin。
     */
    suspend fun removePinnedRecords(
        context: Context,
        keysToRemove: Set<String>,
    ) = withContext(Dispatchers.IO) {
        ensureInitialized(context)
        if (keysToRemove.isEmpty()) return@withContext
        val current = _pinnedRecordsFlow.value
        val remaining = current.filterNot { "${it.packageName}/${it.userSerial}/${it.shortcutId}" in keysToRemove }
        savePinnedRecords(context, remaining, preserveOrder = true)

        if (hasPermission(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            val launcher = context.getSystemService(LauncherApps::class.java)
            val userManager = context.getSystemService(UserManager::class.java)
            if (launcher != null && userManager != null) {
                val removedByPkg = current
                    .filter { "${it.packageName}/${it.userSerial}/${it.shortcutId}" in keysToRemove }
                    .map { it.packageName to it.userSerial }
                    .distinct()
                for ((pkg, serial) in removedByPkg) {
                    val user = if (serial > 0L) {
                        runCatching { userManager.getUserForSerialNumber(serial) }.getOrNull()
                    } else {
                        android.os.Process.myUserHandle()
                    } ?: android.os.Process.myUserHandle()
                    val keepIds = remaining
                        .filter { it.packageName == pkg && it.userSerial == serial }
                        .map { it.shortcutId }
                    runCatching {
                        launcher.pinShortcuts(pkg, keepIds, user)
                    }
                }
            }
        }
    }

    /**
     * 更新已固化快捷方式的顺序（用于长按编辑模式拖拽排序、置顶/上移/下移）。
     */
    fun reorderPinnedRecords(
        context: Context,
        orderedRecords: List<PinnedDynamicShortcutRecord>,
    ) {
        ensureInitialized(context)
        savePinnedRecords(context, orderedRecords, preserveOrder = true)
    }

    /**
     * 当前应用是否已被授予默认桌面（Launcher Host）权限。
     */
    fun hasPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return false
        val launcher = context.getSystemService(LauncherApps::class.java) ?: return false
        return runCatching { launcher.hasShortcutHostPermission() }.getOrDefault(false)
    }

    /**
     * 构造申请系统桌面角色的 Intent。
     */
    fun createRequestHomeIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
            } else {
                Intent(Settings.ACTION_HOME_SETTINGS)
            }
        } else {
            Intent(Settings.ACTION_HOME_SETTINGS)
        }
    }

    /**
     * 构造打开系统“默认桌面设置”页面的 Intent。
     */
    fun openHomeSettingsIntent(): Intent = Intent(Settings.ACTION_HOME_SETTINGS)

    /**
     * 枚举所有可访问用户 Profile 下各应用发布的动态快捷方式与已固定快捷方式。
     */
    suspend fun queryDynamicShortcuts(context: Context): List<DiscoveredDynamicShortcut> = withContext(Dispatchers.IO) {
        ensureInitialized(context)
        if (!hasPermission(context) || Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) {
            return@withContext emptyList()
        }
        val launcher = context.getSystemService(LauncherApps::class.java) ?: return@withContext emptyList()
        val userManager = context.getSystemService(UserManager::class.java) ?: return@withContext emptyList()
        val profiles = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            launcher.profiles
        } else {
            userManager.userProfiles
        }

        val result = mutableListOf<DiscoveredDynamicShortcut>()
        for (user in profiles) {
            if (!userManager.isUserUnlocked(user)) continue
            val serial = runCatching { userManager.getSerialNumberForUser(user) }.getOrDefault(0L)
            val query = LauncherApps.ShortcutQuery().apply {
                setQueryFlags(
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST
                )
            }
            val shortcuts = runCatching { launcher.getShortcuts(query, user) }.getOrNull() ?: continue
            for (info in shortcuts) {
                if (!info.isEnabled) continue
                val appLabel = runCatching {
                    val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        launcher.getApplicationInfo(info.`package`, 0, user)
                    } else {
                        launcher.getActivityList(info.`package`, user).firstOrNull()?.applicationInfo
                    }
                    appInfo?.let { context.packageManager.getApplicationLabel(it).toString() }
                }.getOrNull() ?: info.`package`

                val title = (info.shortLabel ?: info.longLabel ?: info.id).toString()
                val intentUris = runCatching {
                    info.intents?.mapNotNull { it.toUri(Intent.URI_INTENT_SCHEME) }
                }.getOrNull() ?: emptyList()

                result += DiscoveredDynamicShortcut(
                    packageName = info.`package`,
                    shortcutId = info.id,
                    label = title,
                    appLabel = appLabel,
                    isPinned = info.isPinned,
                    userHandle = user,
                    userSerial = serial,
                    intentUris = intentUris,
                )
            }
        }
        // 自动恢复已在系统中处于 isPinned 的快捷方式到本地持久化记录中
        val alreadyPinnedInSystem = result.filter { it.isPinned }.map { it.toPinnedRecord() }
        if (alreadyPinnedInSystem.isNotEmpty()) {
            val merged = (_pinnedRecordsFlow.value + alreadyPinnedInSystem)
                .distinctBy { "${it.packageName}/${it.userSerial}/${it.shortcutId}" }
            savePinnedRecords(context, merged)
        }
        result.sortedWith(compareBy({ it.appLabel }, { it.label }))
    }

    /**
     * 将选中的快捷方式在系统底层 Pin（固定）到本应用下，并持久化写入本地记录。
     *
     * 关键原理：一旦 Pin 成功，即使之后用户将默认桌面切回系统原生桌面，
     * 本应用依然保留长期合法的 [LauncherApps.startShortcut] 启动权限！
     */
    suspend fun pinShortcuts(
        context: Context,
        shortcuts: List<DiscoveredDynamicShortcut>,
    ) = withContext(Dispatchers.IO) {
        ensureInitialized(context)
        if (shortcuts.isEmpty()) return@withContext
        if (hasPermission(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            val launcher = context.getSystemService(LauncherApps::class.java)
            if (launcher != null) {
                val existingPinnedByPkg = queryDynamicShortcuts(context)
                    .filter { it.isPinned }
                    .groupBy { it.packageName to it.userHandle }
                    .mapValues { (_, list) -> list.map { it.shortcutId }.toSet() }

                val grouped = shortcuts.groupBy { it.packageName to it.userHandle }
                for ((key, items) in grouped) {
                    val (pkg, user) = key
                    // 保留该应用原先已 Pin 的 ID，避免覆盖式清空该包下早先已固定的其它快捷方式
                    val existingIds = existingPinnedByPkg[key].orEmpty()
                    val ids = (existingIds + items.map { it.shortcutId }).toList()
                    runCatching {
                        launcher.pinShortcuts(pkg, ids, user)
                    }
                }
            }
        }
        val newRecords = shortcuts.map { it.toPinnedRecord() }
        val merged = (_pinnedRecordsFlow.value + newRecords)
            .distinctBy { "${it.packageName}/${it.userSerial}/${it.shortcutId}" }
        savePinnedRecords(context, merged)
    }

    fun DiscoveredDynamicShortcut.toPinnedRecord(): PinnedDynamicShortcutRecord =
        PinnedDynamicShortcutRecord(
            packageName = packageName,
            shortcutId = shortcutId,
            label = label,
            appLabel = appLabel,
            userSerial = userSerial,
            intentUris = intentUris,
        )

    /**
     * 转换为工程通用的 [ActivityShortcut] 实体。
     */
    fun toActivityShortcut(item: DiscoveredDynamicShortcut): ActivityShortcut {
        return ActivityShortcut(
            label = item.label,
            packageName = item.packageName,
            shortcutId = item.shortcutId,
            intentUris = item.intentUris,
        )
    }

    fun toActivityShortcut(record: PinnedDynamicShortcutRecord): ActivityShortcut {
        return ActivityShortcut(
            label = record.label,
            packageName = record.packageName,
            shortcutId = record.shortcutId,
            intentUris = record.intentUris,
        )
    }

    /**
     * 启动动态快捷方式。
     */
    fun launchShortcut(
        context: Context,
        packageName: String,
        shortcutId: String,
        userHandle: UserHandle? = null,
        userSerial: Long? = null,
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return false
        val launcher = context.getSystemService(LauncherApps::class.java) ?: return false
        val userManager = context.getSystemService(UserManager::class.java)
        val resolvedUser = userHandle
            ?: userSerial?.takeIf { it > 0L }?.let { serial ->
                runCatching { userManager?.getUserForSerialNumber(serial) }.getOrNull()
            }
            ?: android.os.Process.myUserHandle()
        return runCatching {
            launcher.startShortcut(packageName, shortcutId, null, null, resolvedUser)
            true
        }.getOrDefault(false)
    }
}
