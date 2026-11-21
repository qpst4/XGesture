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
        return LinkConfigCodec.decode(text)?.takeIf {
            it.rules.isNotEmpty() || it.exempts.isNotEmpty() || it.extracts.isNotEmpty()
        } ?: LinkGoRuleConverter.parse(text).takeIf { it.isNotEmpty() }?.let {
            LinkConfigBackup(rules = it)
        }
    }

    suspend fun importFromClipboard(context: Context): LinkImportResult {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return LinkImportResult(0, 0, 0, error = "clipboard_unavailable")
        val clip = cm.primaryClip
        val text = clip?.getItemAt(0)?.text?.toString()?.trim().orEmpty()
        if (text.isEmpty()) {
            return LinkImportResult(0, 0, 0, error = "clipboard_empty")
        }
        val backup = LinkConfigCodec.decode(text)?.takeIf {
            it.rules.isNotEmpty() || it.exempts.isNotEmpty() || it.extracts.isNotEmpty()
        } ?: LinkGoRuleConverter.parse(text).takeIf { it.isNotEmpty() }?.let {
            LinkConfigBackup(rules = it)
        } ?: return LinkImportResult(0, 0, 0, error = "invalid_json")

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
