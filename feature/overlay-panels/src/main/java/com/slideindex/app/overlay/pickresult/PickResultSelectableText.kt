package com.slideindex.app.overlay.pickresult

import android.content.Context
import android.text.Selection
import android.text.Spannable
import android.text.SpannableString
import android.text.method.ArrowKeyMovementMethod
import android.util.AttributeSet
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.AppCompatTextView
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.widget.NestedScrollView

private class SelectionReportingTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : AppCompatTextView(context, attrs) {
    var onSelectionRangeChanged: ((start: Int, end: Int) -> Unit)? = null

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        onSelectionRangeChanged?.invoke(selStart.coerceAtLeast(0), selEnd.coerceAtLeast(0))
    }
}

@Composable
fun PickResultSelectableText(
    text: String,
    maxHeight: Dp,
    modifier: Modifier = Modifier,
    textSizeSp: Float = 15f,
    selectAllRequest: Int = 0,
    deselectAllRequest: Int = 0,
    onSelectionChanged: (start: Int, end: Int) -> Unit,
) {
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val textSizeSpValue = textSizeSp
    var lastSelectAllRequest by remember { mutableIntStateOf(0) }
    var lastDeselectAllRequest by remember { mutableIntStateOf(0) }

    fun applySelection(textView: SelectionReportingTextView, start: Int, end: Int) {
        val spannable = textView.text as? Spannable ?: return
        val safeStart = start.coerceIn(0, spannable.length)
        val safeEnd = end.coerceIn(0, spannable.length)
        Selection.setSelection(spannable, safeStart, safeEnd)
        textView.onSelectionRangeChanged?.invoke(safeStart, safeEnd)
    }

    AndroidView(
        modifier = modifier.heightIn(max = maxHeight),
        factory = { context ->
            NestedScrollView(context).apply {
                isFillViewport = false
                isVerticalScrollBarEnabled = true
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                val textView = SelectionReportingTextView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                    setTextIsSelectable(true)
                    isFocusable = true
                    isFocusableInTouchMode = true
                    movementMethod = ArrowKeyMovementMethod.getInstance()
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSpValue)
                    setLineSpacing(0f, 1.35f)
                    setTextColor(textColor)
                    setPadding(0, 0, 0, 0)
                    onSelectionRangeChanged = { start, end ->
                        onSelectionChanged(start, end)
                    }
                }
                addView(textView)
                tag = textView
            }
        },
        update = { scrollView ->
            val textView = scrollView.tag as SelectionReportingTextView
            if (textView.text.toString() != text) {
                textView.setText(SpannableString(text), TextView.BufferType.SPANNABLE)
            }
            textView.setTextColor(textColor)
            textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSpValue)
            textView.onSelectionRangeChanged = { start, end ->
                onSelectionChanged(start, end)
            }
            if (selectAllRequest > lastSelectAllRequest) {
                lastSelectAllRequest = selectAllRequest
                textView.post {
                    textView.requestFocus()
                    applySelection(textView, 0, textView.text.length)
                }
            }
            if (deselectAllRequest > lastDeselectAllRequest) {
                lastDeselectAllRequest = deselectAllRequest
                textView.post {
                    applySelection(textView, 0, 0)
                }
            }
        },
    )
}
