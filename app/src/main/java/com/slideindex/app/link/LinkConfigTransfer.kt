package com.slideindex.app.link

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

/**
 * 通过系统剪贴板导入与导出分发规则配置。
 */
object LinkConfigTransfer {

    suspend fun exportToClipboard(context: Context): Boolean {
        val snapshot = LinkRuleRepository.snapshot(context)
        val json = LinkConfigCodec.export(snapshot.rules, snapshot.exempts, snapshot.extracts)
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return false
        cm.setPrimaryClip(ClipData.newPlainText("LinkConfig", json))
        return true
    }

    fun parseClipboardBackup(context: Context): LinkConfigBackup? {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
        val clip = cm.primaryClip
        val text = clip?.getItemAt(0)?.text?.toString()?.trim().orEmpty()
        if (text.isEmpty()) return null
        return parseText(text)
    }

    /** 统一解析：本应用格式、一墨格式、LinkGo 格式。 */
    fun parseText(text: String): LinkConfigBackup? {
        val cleaned = text.trim().removePrefix("\uFEFF")
        if (cleaned.isBlank()) return null
        LinkConfigCodec.decode(cleaned)?.takeIf { it.hasContent() }?.let { return it }
        YimoLinkConfigAdapter.parse(cleaned)?.takeIf { it.hasContent() }?.let { return it }
        return LinkGoRuleConverter.parse(cleaned).takeIf { it.isNotEmpty() }?.let {
            LinkConfigBackup(rules = it)
        }
    }

    private fun LinkConfigBackup.hasContent(): Boolean =
        rules.isNotEmpty() || exempts.isNotEmpty() || extracts.isNotEmpty()

    suspend fun importText(context: Context, text: String): LinkImportResult {
        val backup = parseText(text) ?: return LinkImportResult(0, 0, 0, error = "invalid_json")
        return LinkRuleRepository.importConfig(context, backup)
    }

    suspend fun importFromClipboard(context: Context): LinkImportResult {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return LinkImportResult(0, 0, 0, error = "clipboard_unavailable")
        val clip = cm.primaryClip
        val text = clip?.getItemAt(0)?.text?.toString()?.trim().orEmpty()
        if (text.isEmpty()) {
            return LinkImportResult(0, 0, 0, error = "clipboard_empty")
        }
        val backup = parseText(text) ?: return LinkImportResult(0, 0, 0, error = "invalid_json")
        return LinkRuleRepository.importConfig(context, backup)
    }

    suspend fun importLinkGoFromClipboard(context: Context): LinkImportResult {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return LinkImportResult(0, 0, 0, error = "clipboard_unavailable")
        val clip = cm.primaryClip
        val text = clip?.getItemAt(0)?.text?.toString()?.trim().orEmpty()
        if (text.isEmpty()) {
            return LinkImportResult(0, 0, 0, error = "clipboard_empty")
        }
        val rules = LinkGoRuleConverter.parse(text)
        if (rules.isEmpty()) {
            return LinkImportResult(0, 0, 0, error = "invalid_linkgo_json")
        }
        val backup = LinkConfigBackup(rules = rules)
        return LinkRuleRepository.importConfig(context, backup)
    }
}
