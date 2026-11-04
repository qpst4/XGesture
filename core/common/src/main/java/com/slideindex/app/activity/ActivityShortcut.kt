package com.slideindex.app.activity

import android.content.Context
import androidx.annotation.StringRes
import com.slideindex.app.common.R
import java.util.UUID

enum class ActivityShortcutKind {
    COMPONENT,
    DYNAMIC,
    INTENT,
}

data class ActivityShortcut(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val packageName: String,
    val activityClassName: String = "",
    val shortcutId: String = "",
    val intentUris: List<String> = emptyList(),
    val iconPath: String? = null,
) {
    val kind: ActivityShortcutKind
        get() = when {
            intentUris.isNotEmpty() -> ActivityShortcutKind.INTENT
            shortcutId.isNotBlank() -> ActivityShortcutKind.DYNAMIC
            else -> ActivityShortcutKind.COMPONENT
        }

    /**
     * 是否为「应用快捷方式」（而不是普通 Intent 或 Shell）。
     *
     * 动态/固定快捷方式可能同时带 intent URI（启动兜底），所以不能只看 `kind == DYNAMIC`；
     * Shell 也带 `shortcutId`，用 `cebianshell:` 前缀排除。
     */
    val isAppShortcut: Boolean
        get() = shortcutId.isNotBlank() && !ActivityShortcutShellSupport.isShellShortcut(this)

    fun componentFlat(): String = "$packageName/$activityClassName"

    /** 与 QuickLauncher / GestureShortcut 对齐的稳定身份键。 */
    fun identityKey(): String = when (kind) {
        ActivityShortcutKind.COMPONENT -> componentFlat()
        ActivityShortcutKind.DYNAMIC -> "$packageName$SHORTCUT_ID_SEP$shortcutId"
        ActivityShortcutKind.INTENT -> {
            if (intentUris.size == 1) {
                "intent:${intentUris[0]}"
            } else {
                "intents:${intentUris.joinToString(INTENT_LIST_SEP)}"
            }
        }
    }

    companion object {
        const val SHORTCUT_ID_SEP = '\u001C'
        const val INTENT_LIST_SEP = "\u001F"

        fun component(
            label: String,
            packageName: String,
            activityClassName: String,
            iconPath: String? = null,
            id: String = UUID.randomUUID().toString(),
        ) = ActivityShortcut(
            id = id,
            label = label,
            packageName = packageName,
            activityClassName = activityClassName,
            iconPath = iconPath,
        )

        fun dynamic(
            label: String,
            packageName: String,
            shortcutId: String,
            iconPath: String? = null,
            id: String = UUID.randomUUID().toString(),
        ) = ActivityShortcut(
            id = id,
            label = label,
            packageName = packageName,
            shortcutId = shortcutId,
            iconPath = iconPath,
        )

        fun intent(
            label: String,
            packageName: String,
            intentUris: List<String>,
            iconPath: String? = null,
            id: String = UUID.randomUUID().toString(),
        ) = ActivityShortcut(
            id = id,
            label = label,
            packageName = packageName,
            intentUris = intentUris.filter { it.isNotBlank() },
            iconPath = iconPath,
        )
    }
}

fun List<ActivityShortcut>.findByIdentityKey(key: String): ActivityShortcut? {
    if (key.isBlank()) return null
    return firstOrNull { it.identityKey() == key }
}

data class ActivityShortcutPreset(
    @StringRes val labelRes: Int,
    val packageName: String,
    val activityClassName: String,
) {
    fun label(context: Context): String = context.getString(labelRes)

    fun toShortcut(context: Context): ActivityShortcut = ActivityShortcut.component(
        label = label(context),
        packageName = packageName,
        activityClassName = activityClassName,
    )
}

object ActivityShortcutCodec {
    private const val SEP = "\u001E"
    private const val LIST_SEP = "\u001F"
    private const val INTENT_URI_SEP = "\u001D"

    fun encode(item: ActivityShortcut): String =
        listOf(
            item.id,
            item.label,
            item.packageName,
            item.activityClassName,
            item.shortcutId,
            item.intentUris.joinToString(INTENT_URI_SEP),
            item.iconPath.orEmpty(),
        ).joinToString(SEP)

    fun decode(raw: String): ActivityShortcut? {
        val parts = raw.split(SEP)
        if (parts.size < 4) return null
        val id = parts[0]
        val label = parts[1]
        val packageName = parts[2]
        val activityClassName = parts.getOrElse(3) { "" }
        val shortcutId = parts.getOrElse(4) { "" }
        val intentUris = parts.getOrElse(5) { "" }
            .split(INTENT_URI_SEP)
            .map { it.trim() }
            .filter { it.isNotBlank() }
        val iconPath = parts.getOrElse(6) { "" }.ifBlank { null }
        if (label.isBlank() || packageName.isBlank()) return null
        if (activityClassName.isBlank() && shortcutId.isBlank() && intentUris.isEmpty()) return null
        return ActivityShortcut(
            id = id.ifBlank { UUID.randomUUID().toString() },
            label = label,
            packageName = packageName,
            activityClassName = activityClassName,
            shortcutId = shortcutId,
            intentUris = intentUris,
            iconPath = iconPath,
        )
    }

    fun encodeAll(items: List<ActivityShortcut>): Set<String> =
        if (items.isEmpty()) {
            emptySet()
        } else {
            setOf(items.joinToString(LIST_SEP) { encode(it) })
        }

    fun decodeAll(raw: Set<String>): List<ActivityShortcut> {
        if (raw.isEmpty()) return emptyList()
        val decoded = if (raw.size == 1) {
            val only = raw.first()
            if (LIST_SEP in only) {
                only.split(LIST_SEP).mapNotNull { decode(it) }
            } else {
                listOfNotNull(decode(only))
            }
        } else {
            raw.mapNotNull { decode(it) }
        }
        return decoded
    }
}

object ActivityShortcutCatalog {
    private const val WECHAT = "com.tencent.mm"

    fun presets(): List<ActivityShortcutPreset> = listOf(
        ActivityShortcutPreset(
            labelRes = R.string.activity_shortcut_coupons,
            packageName = WECHAT,
            activityClassName = "com.tencent.mm.plugin.card.ui.v4.CouponAndGiftCardListV4UI",
        ),
        ActivityShortcutPreset(
            labelRes = R.string.activity_shortcut_favorites,
            packageName = WECHAT,
            activityClassName = "com.tencent.mm.plugin.fav.ui.FavoriteIndexUI",
        ),
        ActivityShortcutPreset(
            labelRes = R.string.activity_shortcut_search,
            packageName = WECHAT,
            activityClassName = "com.tencent.mm.plugin.fts.ui.FTSMainUI",
        ),
        ActivityShortcutPreset(
            labelRes = R.string.activity_shortcut_scan,
            packageName = WECHAT,
            activityClassName = "com.tencent.mm.plugin.scanner.ui.BaseScanUI",
        ),
    )
}
