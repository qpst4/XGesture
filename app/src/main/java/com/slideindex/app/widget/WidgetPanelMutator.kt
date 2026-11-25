package com.slideindex.app.widget

import android.content.Context
import com.slideindex.app.R

object WidgetPanelMutator {
  fun addPage(pages: List<WidgetPanelPage>): List<WidgetPanelPage> {
    val effective = WidgetPanelDefaults.effectivePages(pages).toMutableList()
    val newId = (effective.maxOfOrNull { it.id } ?: 0L) + 1L
    effective.add(WidgetPanelPage(id = newId))
    return effective
  }

  fun removePage(pages: List<WidgetPanelPage>, pageIndex: Int): List<WidgetPanelPage>? {
    val effective = WidgetPanelDefaults.effectivePages(pages).toMutableList()
    if (effective.size <= 1) return null
    val index = pageIndex.coerceIn(0, effective.lastIndex)
    effective.removeAt(index)
    return effective
  }

  fun addWidgetToPage(
    context: Context,
    pages: List<WidgetPanelPage>,
    pageIndex: Int,
    appWidgetId: Int,
  ): List<WidgetPanelPage>? {
    val effective = WidgetPanelDefaults.effectivePages(pages)
    val index = pageIndex.coerceIn(0, effective.lastIndex)
    val page = effective[index]
    val info = WidgetPopupHost.providerInfo(context, appWidgetId)
    if (info == null) {
      android.util.Log.e("WidgetPanelMutator", "addWidgetToPage failed: providerInfo is null for id $appWidgetId")
      android.os.Handler(android.os.Looper.getMainLooper()).post {
        android.widget.Toast.makeText(
          context,
          context.getString(R.string.widget_panel_add_widget_failed),
          android.widget.Toast.LENGTH_LONG,
        ).show()
      }
      return null
    }
    val (rawSpanX, rawSpanY) = WidgetSpanUtil.spanFromProviderInfo(info)
    val spanX = rawSpanX.coerceIn(1, page.columnCount)
    val spanY = rawSpanY.coerceAtLeast(1)
    val slot = WidgetPanelGridLogic.findFirstFreeSlot(page, spanX, spanY)
    if (slot == null) {
      android.util.Log.e("WidgetPanelMutator", "addWidgetToPage failed: no slot for span $spanX x $spanY")
      return null
    }
    val label = WidgetPopupHost.labelFor(context, appWidgetId)
    val item = WidgetPanelItem(
      appWidgetId = appWidgetId,
      x = slot.first,
      y = slot.second,
      spanX = spanX,
      spanY = spanY,
      label = label,
    )
    val updatedPage = WidgetPanelGridLogic.upsertItem(page, item)
    return effective.toMutableList().also { it[index] = updatedPage }
  }

  fun addAppToPage(
    context: Context,
    pages: List<WidgetPanelPage>,
    pageIndex: Int,
    packageName: String,
    className: String,
    label: String,
    cloneUserId: Int = 0,
  ): List<WidgetPanelPage>? {
    val effective = WidgetPanelDefaults.effectivePages(pages)
    val index = pageIndex.coerceIn(0, effective.lastIndex)
    val page = effective[index]
    val spanX = 1
    val spanY = 1
    val slot = WidgetPanelGridLogic.findFirstFreeSlot(page, spanX, spanY)
      ?: return null
    val syntheticId = -kotlin.math.abs(
      (System.currentTimeMillis() xor packageName.hashCode().toLong() xor cloneUserId.toLong()).toInt(),
    )
    val item = WidgetPanelItem(
      appWidgetId = syntheticId,
      x = slot.first,
      y = slot.second,
      spanX = spanX,
      spanY = spanY,
      label = label,
      itemType = ITEM_TYPE_APP,
      packageName = packageName,
      className = className,
      cloneUserId = cloneUserId,
    )
    val updatedPage = WidgetPanelGridLogic.upsertItem(page, item)
    return effective.toMutableList().also { it[index] = updatedPage }
  }

  fun addShortcutToPage(
    context: Context,
    pages: List<WidgetPanelPage>,
    pageIndex: Int,
    packageName: String,
    shortcutId: String,
    label: String,
    intentUri: String,
  ): List<WidgetPanelPage>? {
    val effective = WidgetPanelDefaults.effectivePages(pages)
    val index = pageIndex.coerceIn(0, effective.lastIndex)
    val page = effective[index]
    val spanX = 1
    val spanY = 1
    val slot = WidgetPanelGridLogic.findFirstFreeSlot(page, spanX, spanY)
      ?: return null
    val syntheticId = -kotlin.math.abs((System.currentTimeMillis() xor (packageName + shortcutId).hashCode().toLong()).toInt())
    val item = WidgetPanelItem(
      appWidgetId = syntheticId,
      x = slot.first,
      y = slot.second,
      spanX = spanX,
      spanY = spanY,
      label = label,
      itemType = ITEM_TYPE_SHORTCUT,
      packageName = packageName,
      shortcutId = shortcutId,
      intentUri = intentUri,
    )
    val updatedPage = WidgetPanelGridLogic.upsertItem(page, item)
    return effective.toMutableList().also { it[index] = updatedPage }
  }

  fun addActionToPage(
    context: Context,
    pages: List<WidgetPanelPage>,
    pageIndex: Int,
    actionPayload: String,
    label: String,
  ): List<WidgetPanelPage>? {
    val effective = WidgetPanelDefaults.effectivePages(pages)
    val index = pageIndex.coerceIn(0, effective.lastIndex)
    val page = effective[index]
    val spanX = 1
    val spanY = 1
    val slot = WidgetPanelGridLogic.findFirstFreeSlot(page, spanX, spanY)
      ?: return null
    val syntheticId = -kotlin.math.abs((System.currentTimeMillis() xor actionPayload.hashCode().toLong()).toInt())
    val item = WidgetPanelItem(
      appWidgetId = syntheticId,
      x = slot.first,
      y = slot.second,
      spanX = spanX,
      spanY = spanY,
      label = label,
      itemType = ITEM_TYPE_ACTION,
      intentUri = actionPayload,
    )
    val updatedPage = WidgetPanelGridLogic.upsertItem(page, item)
    return effective.toMutableList().also { it[index] = updatedPage }
  }

  fun removeActionFromPage(
    pages: List<WidgetPanelPage>,
    pageIndex: Int,
    actionPayload: String,
  ): List<WidgetPanelPage> {
    val effective = WidgetPanelDefaults.effectivePages(pages).toMutableList()
    val index = pageIndex.coerceIn(0, effective.lastIndex)
    val page = effective[index]
    val updatedItems = page.items.filterNot {
      it.itemType == ITEM_TYPE_ACTION && it.intentUri == actionPayload
    }
    val updatedPage = page.copy(items = updatedItems)
    effective[index] = updatedPage.copy(rowCount = WidgetPanelGridLogic.computeContentRowCount(updatedPage))
    return effective
  }

  fun removeAppFromPage(
    pages: List<WidgetPanelPage>,
    pageIndex: Int,
    packageName: String,
    cloneUserId: Int = 0,
  ): List<WidgetPanelPage> {
    val effective = WidgetPanelDefaults.effectivePages(pages).toMutableList()
    val index = pageIndex.coerceIn(0, effective.lastIndex)
    val page = effective[index]
    val updatedItems = page.items.filterNot {
      it.itemType == ITEM_TYPE_APP && it.packageName == packageName && it.cloneUserId == cloneUserId
    }
    val updatedPage = page.copy(items = updatedItems)
    effective[index] = updatedPage.copy(rowCount = WidgetPanelGridLogic.computeContentRowCount(updatedPage))
    return effective
  }

  fun removeShortcutFromPage(
    pages: List<WidgetPanelPage>,
    pageIndex: Int,
    packageName: String,
    shortcutId: String,
    intentUri: String = "",
  ): List<WidgetPanelPage> {
    val effective = WidgetPanelDefaults.effectivePages(pages).toMutableList()
    val index = pageIndex.coerceIn(0, effective.lastIndex)
    val page = effective[index]
    val updatedItems = page.items.filterNot {
      it.itemType == ITEM_TYPE_SHORTCUT &&
        it.packageName == packageName &&
        (it.shortcutId == shortcutId || (intentUri.isNotBlank() && it.intentUri == intentUri))
    }
    val updatedPage = page.copy(items = updatedItems)
    effective[index] = updatedPage.copy(rowCount = WidgetPanelGridLogic.computeContentRowCount(updatedPage))
    return effective
  }

  fun removeWidgetFromPage(
    context: Context,
    pages: List<WidgetPanelPage>,
    pageIndex: Int,
    appWidgetId: Int,
  ): List<WidgetPanelPage> {
    val effective = WidgetPanelDefaults.effectivePages(pages).toMutableList()
    val index = pageIndex.coerceIn(0, effective.lastIndex)
    val page = effective[index]
    effective[index] = WidgetPanelGridLogic.removeItem(page, appWidgetId)
    if (appWidgetId > 0) {
      WidgetPopupHost.deleteAppWidgetId(context, appWidgetId)
    }
    return effective
  }

  fun replacePage(
    pages: List<WidgetPanelPage>,
    pageIndex: Int,
    page: WidgetPanelPage,
  ): List<WidgetPanelPage> {
    val effective = WidgetPanelDefaults.effectivePages(pages).toMutableList()
    val index = pageIndex.coerceIn(0, effective.lastIndex)
    effective[index] = page.copy(rowCount = WidgetPanelGridLogic.computeContentRowCount(page))
    return effective
  }

  fun replaceItemOnPage(
    pages: List<WidgetPanelPage>,
    pageIndex: Int,
    item: WidgetPanelItem,
  ): List<WidgetPanelPage> {
    val effective = WidgetPanelDefaults.effectivePages(pages).toMutableList()
    val index = pageIndex.coerceIn(0, effective.lastIndex)
    effective[index] = WidgetPanelGridLogic.upsertItem(effective[index], item)
    return effective
  }

  fun updateItemOnPage(
    pages: List<WidgetPanelPage>,
    pageIndex: Int,
    item: WidgetPanelItem,
  ): List<WidgetPanelPage>? {
    val effective = WidgetPanelDefaults.effectivePages(pages).toMutableList()
    val index = pageIndex.coerceIn(0, effective.lastIndex)
    val page = effective[index]
    if (!WidgetPanelGridLogic.isAreaFree(page, item.x, item.y, item.spanX, item.spanY, item.appWidgetId)) {
      return null
    }
    effective[index] = WidgetPanelGridLogic.upsertItem(page, item)
    return effective
  }
}
