package com.slideindex.app.ui

import android.app.Activity
import android.graphics.Bitmap
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slideindex.app.R
import com.slideindex.app.data.AppInfo
import com.slideindex.app.freezer.FreezerAppState
import com.slideindex.app.freezer.FreezerOperations
import com.slideindex.app.launcher.AppTarget
import com.slideindex.app.overlay.CloneAppsIndex
import com.slideindex.app.launcher.QuickLauncherItemCodec
import com.slideindex.app.ui.compose.rememberAppRepository
import com.slideindex.app.ui.picker.AppPickerTabRow
import com.slideindex.app.ui.miuix.MiuixExpandableSearchIconAction
import com.slideindex.app.ui.miuix.MiuixScaffoldSearchTabBottomContent
import com.slideindex.app.ui.miuix.consumeExpandableSearchBack
import com.slideindex.app.ui.picker.ActivityShortcutPickActivityScreen
import com.slideindex.app.ui.picker.ActivityShortcutPickAppScreen
import com.slideindex.app.ui.picker.MyShortcutsFolderScreen
import com.slideindex.app.ui.picker.PresetShortcutsFolderScreen
import com.slideindex.app.ui.picker.filterShortcutCatalog
import com.slideindex.app.ui.picker.rememberLoadedShortcutCatalog
import com.slideindex.app.ui.picker.shortcutFolderCardsSection
import com.slideindex.app.ui.picker.systemShortcutCatalogItems
import com.slideindex.app.ui.settings.components.LazySettingsItem
import com.slideindex.app.ui.settings.components.SettingsLazyScreenScaffold
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.picker.appPickerGridItems
import com.slideindex.app.util.AppShortcutLoader
import com.slideindex.app.util.PinyinHelper
import com.slideindex.app.widget.InstalledAppEntry
import com.slideindex.app.widget.ShortcutEntry
import com.slideindex.app.widget.WidgetAppGroup
import com.slideindex.app.widget.WidgetCatalog
import com.slideindex.app.widget.WidgetPreviewLoader
import com.slideindex.app.widget.WidgetProviderEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton

sealed interface WidgetPickerSubScreen {
  data object Main : WidgetPickerSubScreen
  data class WidgetAppDetail(val group: WidgetAppGroup) : WidgetPickerSubScreen
  data object MyShortcuts : WidgetPickerSubScreen
  data object PresetShortcuts : WidgetPickerSubScreen
  data object PickApp : WidgetPickerSubScreen
  data class PickActivity(val packageName: String) : WidgetPickerSubScreen
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WidgetPickerScreen(
  onBack: () -> Unit,
  onWidgetSelected: ((WidgetProviderEntry) -> Unit)? = null,
  configuredAppPackages: Set<String> = emptySet(),
  configuredShortcutKeys: Set<String> = emptySet(),
  onToggleApp: ((InstalledAppEntry, Boolean) -> Unit)? = null,
  onToggleShortcut: ((ShortcutEntry, Boolean) -> Unit)? = null,
  onAppSelected: ((InstalledAppEntry) -> Unit)? = null,
  onShortcutSelected: ((ShortcutEntry) -> Unit)? = null,
  launchCreateShortcut: ((AppShortcutLoader.CreateShortcutHost) -> Unit)? = null,
  enableBackHandler: Boolean = true,
  overlayMode: Boolean = false,
) {
  val context = LocalContext.current
  var selectedTab by remember { mutableIntStateOf(0) }
  // 勾选态按目标（包名 + user）记：主实例与分身在面板里是两个条目，互相独立。
  var addedAppPackages by remember(configuredAppPackages) { mutableStateOf(configuredAppPackages) }
  var addedShortcutKeys by remember(configuredShortcutKeys) { mutableStateOf(configuredShortcutKeys) }
  val initialGroups = WidgetCatalog.cachedGroups ?: emptyList()
  val initialInstalledApps = WidgetCatalog.cachedInstalledApps ?: emptyList()
  var groups by remember { mutableStateOf(initialGroups) }
  var installedApps by remember { mutableStateOf(initialInstalledApps) }
  var allApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
  // 冰箱成员的实时状态（按目标）；挑应用时也要像冰箱 / 索引一样显示冻结 / 暂停角标。
  var appStates by remember { mutableStateOf<Map<String, FreezerAppState>>(emptyMap()) }
  var loading by remember { mutableStateOf(initialGroups.isEmpty() && initialInstalledApps.isEmpty()) }
  var searchQuery by remember { mutableStateOf("") }
  var searchExpanded by remember { mutableStateOf(false) }
  val searchFocusRequester = remember { FocusRequester() }
  var subScreen by remember { mutableStateOf<WidgetPickerSubScreen>(WidgetPickerSubScreen.Main) }

  val settingsRepository = com.slideindex.app.ui.compose.rememberSettingsRepository()
  val appSettings by settingsRepository.settings.collectAsStateWithLifecycle(initialValue = com.slideindex.app.settings.AppSettings())
  val activityShortcuts = appSettings.activityShortcuts
  val appRepository = rememberAppRepository()

  LaunchedEffect(Unit) {
    if (groups.isEmpty() || installedApps.isEmpty()) {
      loading = true
    }
    withContext(Dispatchers.IO) {
      val loadedGroups = WidgetCatalog.loadGroups(context)
      // force：缓存里可能是上一版「按包名去重」的结果（没有分身），必须重新枚举一次。
      val loadedApps = WidgetCatalog.loadInstalledApps(context, force = true)
      // 先落地「小组件 + 应用」两页的数据：后面 `loadApps` 是另一次全量枚举，
      // 面板被关掉/重建时协程会在那个挂起点被取消——如果赋值放在它后面，
      // 这一屏就永远停在旧缓存上（旧缓存没有分身）。
      groups = loadedGroups
      installedApps = loadedApps
      android.util.Log.i(
        "WidgetPicker",
        "installed=${loadedApps.size} clones=${loadedApps.count { it.cloneUserId > 0 }}",
      )
      val loadedAll = appRepository.loadApps(force = false)
      allApps = loadedAll
    }
    loading = false
  }

  val activityResultRegistryOwner = LocalActivityResultRegistryOwner.current
  LaunchedEffect(installedApps, appSettings.freezerAppPackages) {
    val targets = appSettings.freezerAppPackages
    appStates = if (targets.isEmpty()) {
      emptyMap()
    } else {
      withContext(Dispatchers.IO) {
        buildMap {
          installedApps.forEach { entry ->
            val key = AppTarget.encode(entry.packageName, entry.cloneUserId)
            if (key in targets) {
              put(
                key,
                FreezerOperations.stateOf(
                  context,
                  AppTarget(entry.packageName, entry.cloneUserId),
                ),
              )
            }
          }
        }
      }
    }
  }

  var pendingCreateHost by remember { mutableStateOf<AppShortcutLoader.CreateShortcutHost?>(null) }
  val createLauncher = if (activityResultRegistryOwner != null) {
    rememberLauncherForActivityResult(
      ActivityResultContracts.StartActivityForResult(),
    ) { result ->
      val host = pendingCreateHost
      pendingCreateHost = null
      if (result.resultCode != Activity.RESULT_OK || host == null) return@rememberLauncherForActivityResult
      val created = AppShortcutLoader.parseCreateShortcutResult(host.packageName, result.data)
        ?: return@rememberLauncherForActivityResult
      val entry = ShortcutEntry(
        packageName = created.hostPackageName,
        shortcutId = "created_${created.label.hashCode()}",
        label = created.label,
        sortKey = created.label,
        initialKey = "",
        iconBitmap = null,
        intentUri = created.intentUri.orEmpty(),
      )
      addedShortcutKeys = addedShortcutKeys + (created.hostPackageName + "/" + created.intentUri.orEmpty())
      if (onToggleShortcut != null) {
        onToggleShortcut(entry, false)
      } else {
        onShortcutSelected?.invoke(entry)
      }
    }
  } else null

  AnimatedContent(
    targetState = subScreen,
    transitionSpec = {
      if (targetState != WidgetPickerSubScreen.Main && initialState == WidgetPickerSubScreen.Main) {
        (slideInHorizontally(animationSpec = tween(240)) { it } + fadeIn(animationSpec = tween(240))) togetherWith
          (slideOutHorizontally(animationSpec = tween(200)) { -it / 3 } + fadeOut(animationSpec = tween(200)))
      } else if (targetState == WidgetPickerSubScreen.Main && initialState != WidgetPickerSubScreen.Main) {
        (slideInHorizontally(animationSpec = tween(240)) { -it / 3 } + fadeIn(animationSpec = tween(240))) togetherWith
          (slideOutHorizontally(animationSpec = tween(200)) { it } + fadeOut(animationSpec = tween(200)))
      } else {
        (slideInHorizontally(animationSpec = tween(240)) { it } + fadeIn(animationSpec = tween(240))) togetherWith
          (slideOutHorizontally(animationSpec = tween(200)) { -it } + fadeOut(animationSpec = tween(200)))
      }
    },
    label = "WidgetPickerSubScreenTransition",
  ) { currentSub ->
    when (currentSub) {
      is WidgetPickerSubScreen.WidgetAppDetail -> {
        WidgetAppDetailScreen(
          group = currentSub.group,
          onBack = { subScreen = WidgetPickerSubScreen.Main },
          onWidgetSelected = onWidgetSelected,
          enableBackHandler = enableBackHandler,
          overlayMode = overlayMode,
        )
      }
      WidgetPickerSubScreen.MyShortcuts -> {
        MyShortcutsFolderScreen(
          activityShortcuts = activityShortcuts,
          onBack = { subScreen = WidgetPickerSubScreen.Main },
          onBrowseNewShortcut = { subScreen = WidgetPickerSubScreen.PickApp },
          configuredShortcutKeys = addedShortcutKeys,
          onToggle = { item, added ->
            val key = QuickLauncherItemCodec.shortcutItemKey(item).orEmpty()
            addedShortcutKeys = if (added) addedShortcutKeys - key else addedShortcutKeys + key
            val entry = ShortcutEntry(
              packageName = item.payload.substringBefore('/'),
              shortcutId = item.payload,
              label = item.label,
              sortKey = item.label,
              initialKey = "",
              iconBitmap = null,
              intentUri = item.payload,
            )
            if (onToggleShortcut != null) {
              onToggleShortcut(entry, added)
            } else if (onShortcutSelected != null) {
              onShortcutSelected(entry)
            }
          },
          onSelectShortcutEntry = { entry ->
            onShortcutSelected?.invoke(entry)
          },
          enableBackHandler = enableBackHandler,
          overlayMode = overlayMode,
        )
      }
      WidgetPickerSubScreen.PresetShortcuts -> {
        PresetShortcutsFolderScreen(
          onBack = { subScreen = WidgetPickerSubScreen.Main },
          configuredShortcutKeys = addedShortcutKeys,
          onToggle = { item, added ->
            val key = QuickLauncherItemCodec.shortcutItemKey(item).orEmpty()
            addedShortcutKeys = if (added) addedShortcutKeys - key else addedShortcutKeys + key
            val entry = ShortcutEntry(
              packageName = item.payload.substringBefore('/'),
              shortcutId = item.payload,
              label = item.label,
              sortKey = item.label,
              initialKey = "",
              iconBitmap = null,
              intentUri = item.payload,
            )
            if (onToggleShortcut != null) {
              onToggleShortcut(entry, added)
            } else if (onShortcutSelected != null) {
              onShortcutSelected(entry)
            }
          },
          onSelectShortcutEntry = { entry ->
            onShortcutSelected?.invoke(entry)
          },
          enableBackHandler = enableBackHandler,
          overlayMode = overlayMode,
        )
      }
      WidgetPickerSubScreen.PickApp -> {
        ActivityShortcutPickAppScreen(
          onBack = { subScreen = WidgetPickerSubScreen.MyShortcuts },
          onSelectApp = { app ->
            subScreen = WidgetPickerSubScreen.PickActivity(app.packageName)
          },
          enableBackHandler = enableBackHandler,
        )
      }
      is WidgetPickerSubScreen.PickActivity -> {
        ActivityShortcutPickActivityScreen(
          packageName = currentSub.packageName,
          onBack = { subScreen = WidgetPickerSubScreen.PickApp },
          onSelectActivity = { activity ->
            val entry = ShortcutEntry(
              packageName = activity.packageName,
              shortcutId = "${activity.packageName}/${activity.className}",
              label = activity.label,
              sortKey = activity.label,
              initialKey = "",
              iconBitmap = null,
              intentUri = "",
            )
            val key = "${activity.packageName}/${activity.className}"
            addedShortcutKeys = addedShortcutKeys + key
            if (onToggleShortcut != null) {
              onToggleShortcut(entry, false)
            } else {
              onShortcutSelected?.invoke(entry)
            }
          },
          enableBackHandler = enableBackHandler,
        )
      }
      WidgetPickerSubScreen.Main -> {

  val filteredGroups = remember(groups, searchQuery) {
    val query = searchQuery.trim().lowercase()
    if (query.isEmpty()) return@remember groups
    groups.mapNotNull { group ->
      val appMatches = group.appLabel.lowercase().contains(query) ||
        PinyinHelper.sortKey(group.appLabel).contains(query) ||
        PinyinHelper.initialKey(group.appLabel).contains(query) ||
        group.packageName.lowercase().contains(query)
      val widgets = group.widgets.filter { widget ->
        appMatches ||
          widget.widgetLabel.lowercase().contains(query) ||
          PinyinHelper.sortKey(widget.widgetLabel).contains(query) ||
          PinyinHelper.initialKey(widget.widgetLabel).contains(query) ||
          widget.packageName.lowercase().contains(query)
      }
      if (widgets.isEmpty()) null else group.copy(widgets = widgets)
    }
  }

  val filteredApps = remember(installedApps, searchQuery) {
    val query = searchQuery.trim().lowercase()
    if (query.isEmpty()) {
      installedApps
    } else {
      installedApps.filter {
        it.appLabel.lowercase().contains(query) ||
          it.sortKey.contains(query) ||
          it.initialKey.contains(query) ||
          it.packageName.lowercase().contains(query)
      }
    }
    // 不要按包名去重：主实例与分身同包名，去重会把分身整条吞掉
    // （列表 key 已经带 userId，重复 key 的问题不存在了）。
  }

  val loadedCatalog = rememberLoadedShortcutCatalog(allApps)
  val filteredCatalog = remember(loadedCatalog.catalog, searchQuery) {
    filterShortcutCatalog(loadedCatalog.catalog, searchQuery)
  }
  val appsByPackage = remember(allApps) { allApps.associateBy { it.packageName } }

  val handleBack: () -> Unit = {
    if (
      !consumeExpandableSearchBack(
        expanded = searchExpanded,
        query = searchQuery,
        onExpandedChange = { searchExpanded = it },
        onQueryChange = { searchQuery = it },
      )
    ) {
      onBack()
    }
  }

  val listHPadding = if (overlayMode) PickerListOverlayHorizontalPadding else PickerListHorizontalPadding

  SettingsLazyScreenScaffold(
    title = stringResource(R.string.widget_picker_title),
    onBack = handleBack,
    enableBackHandler = enableBackHandler,
    modifier = Modifier.fillMaxSize(),
    actions = {
      MiuixExpandableSearchIconAction(
        expanded = searchExpanded,
        query = searchQuery,
        onExpandedChange = { searchExpanded = it },
        onQueryChange = { searchQuery = it },
      )
    },
    bottomContent = {
      MiuixScaffoldSearchTabBottomContent(
        searchExpanded = searchExpanded,
        searchQuery = searchQuery,
        onSearchQueryChange = { searchQuery = it },
        focusRequester = searchFocusRequester,
        hintResId = R.string.widget_picker_search_hint,
        tabContent = {
          AppPickerTabRow(
            tabs = listOf(
              stringResource(R.string.widget_picker_title),
              stringResource(R.string.widget_tab_apps),
              stringResource(R.string.widget_tab_shortcuts),
            ),
            selectedTabIndex = selectedTab,
            onTabSelected = { selectedTab = it },
          )
        },
      )
    },
  ) {
    when {
      loading -> {
        item(key = "widget_loading") {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(240.dp),
            contentAlignment = Alignment.Center,
          ) {
            CircularProgressIndicator()
          }
        }
      }
      selectedTab == 0 && filteredGroups.isEmpty() -> {
        item(key = "widget_empty_groups") {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(240.dp),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = stringResource(R.string.widget_picker_empty),
              style = MaterialTheme.typography.bodyLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
      selectedTab == 1 && filteredApps.isEmpty() -> {
        item(key = "widget_empty_apps") {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(240.dp),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = stringResource(R.string.widget_picker_empty),
              style = MaterialTheme.typography.bodyLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
      selectedTab == 0 -> {
        items(filteredGroups, key = { it.packageName }) { group ->
          WidgetAppGroupSection(
            group = group,
            onOpenApp = { subScreen = WidgetPickerSubScreen.WidgetAppDetail(group) },
            onSelect = onWidgetSelected,
            horizontalPadding = listHPadding,
          )
          Spacer(modifier = Modifier.height(16.dp))
        }
      }
      selectedTab == 1 -> {
        // 注意：这里在 LazyListScope 里，不能用 remember（不是 Composable 上下文）。
        val gridApps = filteredApps.map { entry ->
          val key = AppTarget.encode(entry.packageName, entry.cloneUserId)
          val state = appStates[key]
          AppInfo(
            packageName = entry.packageName,
            label = if (entry.cloneUserId > 0) {
              CloneAppsIndex.labelForUser(context, entry.cloneUserId, entry.appLabel)
            } else {
              entry.appLabel
            },
            letter = entry.initialKey.firstOrNull() ?: '#',
            pinyinKey = entry.sortKey,
            cloneUserId = entry.cloneUserId,
            isFrozen = state?.isFrozen ?: false,
            isPaused = state?.isPaused ?: false,
          )
        }
        appPickerGridItems(
          apps = gridApps,
          selectedKeys = addedAppPackages,
          onAppClick = { app ->
            val entry = filteredApps.firstOrNull {
              it.packageName == app.packageName && it.cloneUserId == app.cloneUserId
            }
            if (entry != null) {
              val appKey = AppTarget.encode(entry.packageName, entry.cloneUserId)
              val wasAdded = appKey in addedAppPackages
              addedAppPackages = if (wasAdded) addedAppPackages - appKey else addedAppPackages + appKey
              if (onToggleApp != null) {
                onToggleApp(entry, wasAdded)
              } else if (onAppSelected != null) {
                onAppSelected(entry)
              }
            }
          },
        )
      }      selectedTab == 2 -> {
        if (searchQuery.isBlank()) {
          shortcutFolderCardsSection(
            activityShortcutsCount = activityShortcuts.size,
            onOpenMyShortcuts = { subScreen = WidgetPickerSubScreen.MyShortcuts },
            onOpenPresetShortcuts = { subScreen = WidgetPickerSubScreen.PresetShortcuts },
            horizontalPadding = listHPadding,
          )
        }
        systemShortcutCatalogItems(
          filtered = filteredCatalog,
          appsByPackage = appsByPackage,
          loading = loadedCatalog.loading,
          scanProgress = loadedCatalog.scanProgress,
          searchQuery = searchQuery,
          loadingItemKey = "widget-shortcut-loading",
          emptyItemKey = "widget-shortcut-empty",
          onCreateHostClick = { host ->
            if (launchCreateShortcut != null) {
              launchCreateShortcut(host)
            } else if (createLauncher != null) {
              pendingCreateHost = host
              runCatching { createLauncher.launch(host.createIntent()) }
                .onFailure { pendingCreateHost = null }
            } else {
              com.slideindex.app.service.CreateShortcutTrampoline.launch(
                context = context,
                host = host,
                onPrepare = {
                  onBack()
                },
                onResult = { created ->
                  if (created != null) {
                    val entry = ShortcutEntry(
                      packageName = created.hostPackageName,
                      shortcutId = "created_${created.label.hashCode()}",
                      label = created.label,
                      sortKey = created.label,
                      initialKey = "",
                      iconBitmap = null,
                      intentUri = created.intentUri.orEmpty(),
                    )
                    val scKey = created.hostPackageName + "/" + created.intentUri.orEmpty()
                    addedShortcutKeys = addedShortcutKeys + scKey
                    if (onToggleShortcut != null) {
                      onToggleShortcut(entry, false)
                    } else {
                      onShortcutSelected?.invoke(entry)
                    }
                  }
                },
              )
            }
          },
          shortcutRowContent = { group, shortcut, segmentIndex, segmentCount ->
            val intentUri = shortcut.intentUris?.firstOrNull().orEmpty()
            val shortcutId = shortcut.shortcutId ?: shortcut.label
            val scKey = group.app.packageName + "/" + (shortcut.shortcutId.takeUnless { it.isNullOrBlank() } ?: intentUri)
            val entry = ShortcutEntry(
              packageName = group.app.packageName,
              shortcutId = shortcutId,
              label = shortcut.label,
              sortKey = shortcut.label,
              initialKey = "",
              iconBitmap = null,
              intentUri = intentUri,
            )
            val isAdded = scKey in addedShortcutKeys || addedShortcutKeys.any { it.startsWith(group.app.packageName) && (it.contains(shortcutId) || (intentUri.isNotBlank() && it.contains(intentUri))) }
            Md3PickerListRow(
              segmentIndex = segmentIndex,
              segmentCount = segmentCount,
              title = shortcut.label,
              subtitle = shortcut.targetComponent?.takeIf { it.isNotBlank() },
              selected = isAdded,
              trailingMode = PickerTrailingMode.Toggle,
              onClick = {
                val nextAdded = !isAdded
                addedShortcutKeys = if (nextAdded) addedShortcutKeys + scKey else addedShortcutKeys - scKey
                if (onToggleShortcut != null) {
                  onToggleShortcut(entry, isAdded)
                } else if (onShortcutSelected != null) {
                  onShortcutSelected(entry)
                }
              },
              leadingContent = { Md3PickerAppLeading(group.app) },
              trailingChip = shortcutKindChip(shortcut.kind),
            )
          },
        )
      }
    }
  }
    }
  }
}
}

@Composable
private fun WidgetAppGroupSection(
  group: WidgetAppGroup,
  onOpenApp: () -> Unit,
  onSelect: ((WidgetProviderEntry) -> Unit)? = null,
  horizontalPadding: androidx.compose.ui.unit.Dp = PickerListHorizontalPadding,
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = horizontalPadding),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(12.dp))
        .clickable(onClick = onOpenApp)
        .padding(vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (group.appIcon != null) {
        Md3PickerDrawableLeading(
          drawable = group.appIcon,
          contentDescription = group.appLabel,
          cacheKey = group.packageName,
        )
      } else {
        Md3PickerPackageLeading(
          packageName = group.packageName,
          contentDescription = group.appLabel,
        )
      }

      Spacer(modifier = Modifier.width(12.dp))

      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = group.appLabel,
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
          text = pluralStringResource(R.plurals.widget_group_count, group.widgets.size, group.widgets.size),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = stringResource(R.string.cd_view_all),
        modifier = Modifier.size(20.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    Spacer(modifier = Modifier.height(8.dp))

    LazyRow(
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      contentPadding = PaddingValues(horizontal = 4.dp),
    ) {
      items(
        count = group.widgets.size,
        key = { index -> "${group.widgets[index].provider.provider.flattenToString()}_${group.widgets[index].widgetLabel}_${group.widgets[index].spanX}x${group.widgets[index].spanY}_$index" },
      ) { index ->
        val widget = group.widgets[index]
        WidgetPreviewCard(
          widget = widget,
          onClick = { onSelect?.invoke(widget) },
        )
      }
    }
  }
}

@Composable
private fun WidgetPreviewCard(
  widget: WidgetProviderEntry,
  onClick: () -> Unit,
) {
  val context = LocalContext.current
  var previewBitmap by remember(widget.provider) { mutableStateOf<Bitmap?>(null) }
  LaunchedEffect(widget.provider) {
    withContext(Dispatchers.IO) {
      previewBitmap = WidgetPreviewLoader.loadPreviewBitmap(context, widget.provider, 200)
    }
  }

  Surface(
    modifier = Modifier
      .width(160.dp)
      .clip(RoundedCornerShape(16.dp))
      .clickable(onClick = onClick),
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surfaceContainerLow,
  ) {
    Column(
      modifier = Modifier.padding(12.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(100.dp)
          .clip(RoundedCornerShape(8.dp))
          .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
      ) {
        val b = previewBitmap
        if (b != null) {
          Image(
            bitmap = b.asImageBitmap(),
            contentDescription = widget.widgetLabel,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
          )
        } else {
          Icon(
            imageVector = Icons.Default.Widgets,
            contentDescription = null,
            modifier = Modifier.size(36.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      Text(
        text = widget.widgetLabel,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
      )

      Text(
        text = "${widget.spanX}×${widget.spanY}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WidgetAppDetailScreen(
  group: WidgetAppGroup,
  onBack: () -> Unit,
  onWidgetSelected: ((WidgetProviderEntry) -> Unit)? = null,
  enableBackHandler: Boolean = true,
  overlayMode: Boolean = false,
) {
  val listHPadding = if (overlayMode) PickerListOverlayHorizontalPadding else PickerListHorizontalPadding

  SettingsScreenScaffold(
    title = group.appLabel,
    subtitle = pluralStringResource(R.plurals.widget_group_count, group.widgets.size, group.widgets.size),
    onBack = onBack,
    enableBackHandler = enableBackHandler,
    overlayMode = overlayMode,
    scrollContent = false,
    modifier = Modifier.fillMaxSize(),
  ) {
    LazySettingsItem(key = "app-detail-body", fillParentMaxSize = true) {
      LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
          start = listHPadding,
          end = listHPadding,
          top = 8.dp,
          bottom = 24.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        itemsIndexed(
          items = group.widgets,
          key = { index, item -> "${item.provider.provider.flattenToString()}_${item.widgetLabel}_${item.spanX}x${item.spanY}_$index" },
        ) { _, widget ->
          WidgetDetailCard(
            widget = widget,
            onClick = { onWidgetSelected?.invoke(widget) },
          )
        }
      }
    }
  }
}

@Composable
private fun WidgetDetailCard(
  widget: WidgetProviderEntry,
  onClick: () -> Unit,
) {
  val context = LocalContext.current
  var previewBitmap by remember(widget.provider) { mutableStateOf<Bitmap?>(null) }
  LaunchedEffect(widget.provider) {
    withContext(Dispatchers.IO) {
      previewBitmap = WidgetPreviewLoader.loadPreviewBitmap(context, widget.provider, 300)
    }
  }

  Surface(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(16.dp))
      .clickable(onClick = onClick),
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surfaceContainerLow,
  ) {
    Column(
      modifier = Modifier.padding(12.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(110.dp)
          .clip(RoundedCornerShape(8.dp))
          .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
      ) {
        val b = previewBitmap
        if (b != null) {
          Image(
            bitmap = b.asImageBitmap(),
            contentDescription = widget.widgetLabel,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
          )
        } else {
          Icon(
            imageVector = Icons.Default.Widgets,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      Text(
        text = widget.widgetLabel,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
      )

      Text(
        text = "${widget.spanX}×${widget.spanY}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
