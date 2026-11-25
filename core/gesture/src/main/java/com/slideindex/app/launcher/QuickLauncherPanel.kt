package com.slideindex.app.launcher

import java.util.UUID

data class QuickLauncherPanel(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val columnsPerPage: Int = 3,
    val rowsPerPage: Int = 4,
    val items: List<QuickLauncherItem> = emptyList(),
)

object QuickLauncherPanelDefaults {
    /**
     * 面板总数上限（快速启动器与小组件面板分页共用）。
     *
     * 2026-11 用户反馈上限 12 不够用，放宽到 30；数据结构是 List + 运行时索引，
     * 没有按 12 定长的数组或位掩码，因此只需改这个常量（新增/复制入口都读它）。
     */
    const val MAX_PANELS = 30
    const val DEFAULT_PANEL_ID = "default"

    fun defaultPanel(
        name: String = "",
        columnsPerPage: Int = 3,
        rowsPerPage: Int = 4,
        items: List<QuickLauncherItem> = emptyList(),
        id: String = DEFAULT_PANEL_ID,
    ): QuickLauncherPanel = QuickLauncherPanel(
        id = id,
        name = name,
        columnsPerPage = columnsPerPage,
        rowsPerPage = rowsPerPage,
        items = items,
    )

    fun effectivePanels(panels: List<QuickLauncherPanel>): List<QuickLauncherPanel> =
        panels.ifEmpty { listOf(defaultPanel()) }

    fun resolvePanel(panels: List<QuickLauncherPanel>, panelId: String): QuickLauncherPanel {
        val effective = effectivePanels(panels)
        if (panelId.isNotBlank()) {
            effective.firstOrNull { it.id == panelId }?.let { return it }
        }
        return effective.first()
    }

    fun resolvePanelId(panels: List<QuickLauncherPanel>, panelId: String): String =
        resolvePanel(panels, panelId).id

    fun migrateFromLegacyItems(
        items: List<QuickLauncherItem>,
        columnsPerPage: Int = 3,
        rowsPerPage: Int = 4,
        name: String = "",
    ): List<QuickLauncherPanel> = listOf(
        defaultPanel(
            name = name,
            columnsPerPage = columnsPerPage,
            rowsPerPage = rowsPerPage,
            items = items,
        ),
    )

    fun nextPanelName(existingCount: Int): String = "Panel ${existingCount + 1}"
}

object QuickLauncherPanelCodec {
    private const val FIELD_SEP = "\u001E"

    fun encodePanel(panel: QuickLauncherPanel): String {
        val sanitized = panel.copy(items = QuickLauncherItemCodec.sanitizeQuickLauncherItems(panel.items))
        val header = listOf(
            sanitized.id,
            sanitized.name,
            sanitized.columnsPerPage.toString(),
            sanitized.rowsPerPage.toString(),
        ).joinToString(FIELD_SEP)
        val itemsBlob = QuickLauncherItemCodec.encodeAll(sanitized.items).singleOrNull().orEmpty()
        return if (itemsBlob.isEmpty()) header else "$header$FIELD_SEP$itemsBlob"
    }

    fun decodePanel(raw: String): QuickLauncherPanel? {
        var cursor = 0
        fun nextField(): String? {
            if (cursor > raw.length) return null
            val sep = raw.indexOf(FIELD_SEP, cursor)
            return if (sep < 0) {
                raw.substring(cursor).also { cursor = raw.length }
            } else {
                raw.substring(cursor, sep).also { cursor = sep + 1 }
            }
        }

        val id = nextField()?.takeIf { it.isNotBlank() } ?: return null
        val name = nextField() ?: return null
        val columns = nextField()?.toIntOrNull()?.coerceIn(2, 6) ?: return null
        val rows = nextField()?.toIntOrNull()?.coerceIn(2, 9) ?: return null
        val itemsRaw = if (cursor < raw.length) raw.substring(cursor) else ""
        val items = QuickLauncherItemCodec.sanitizeQuickLauncherItems(
            QuickLauncherItemCodec.decodeAll(
                if (itemsRaw.isBlank()) emptySet() else setOf(itemsRaw),
            ),
        )
        return QuickLauncherPanel(id, name, columns, rows, items)
    }

    fun encodeAll(panels: List<QuickLauncherPanel>): Set<String> =
        panels.map { encodePanel(it) }.toSet()

    fun decodeAll(raw: Set<String>): List<QuickLauncherPanel> =
        raw.mapNotNull { decodePanel(it) }
}
