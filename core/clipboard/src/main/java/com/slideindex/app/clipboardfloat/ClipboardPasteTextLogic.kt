package com.slideindex.app.clipboardfloat

/**
 * 无障碍粘贴时的文本归一化与合并逻辑。
 * 部分输入框在无障碍树里会用纯换行占位表示「空」。
 */
object ClipboardPasteTextLogic {

    data class EditableSnapshot(
        val content: String,
        val leadingPlaceholderLength: Int
    )

    fun effectiveInputText(
        rawText: CharSequence?,
        hintText: CharSequence? = null
    ): String {
        return snapshotEditableText(rawText, hintText).content
    }

    fun snapshotEditableText(
        rawText: CharSequence?,
        hintText: CharSequence? = null
    ): EditableSnapshot {
        val text = rawText?.toString().orEmpty()
        if (text.isEmpty()) {
            return EditableSnapshot(content = "", leadingPlaceholderLength = 0)
        }
        val hint = hintText?.toString().orEmpty()
        if (hint.isNotEmpty() && text == hint) {
            return EditableSnapshot(content = "", leadingPlaceholderLength = 0)
        }
        if (text.all { it == '\n' }) {
            return EditableSnapshot(content = "", leadingPlaceholderLength = text.length)
        }
        if (text.isBlank()) {
            return EditableSnapshot(content = "", leadingPlaceholderLength = 0)
        }
        val leadingPlaceholderLength = text.indexOfFirst { it != '\n' }.let { index ->
            if (index < 0) text.length else index
        }
        val content = text.substring(leadingPlaceholderLength)
        return EditableSnapshot(
            content = content,
            leadingPlaceholderLength = leadingPlaceholderLength
        )
    }

    fun mergeClipAtSelection(
        currentText: String,
        clipText: String,
        selectionStart: Int,
        selectionEnd: Int
    ): String {
        if (currentText.isEmpty()) return clipText
        val start = selectionStart.coerceIn(0, currentText.length)
        val end = selectionEnd.coerceIn(start, currentText.length)
        return buildString {
            append(currentText.substring(0, start))
            append(clipText)
            append(currentText.substring(end))
        }
    }
}
