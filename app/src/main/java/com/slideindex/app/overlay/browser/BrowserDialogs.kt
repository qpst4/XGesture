package com.slideindex.app.overlay.browser

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.slideindex.app.overlay.OverlayWindowTypes

/**
 * 浮层浏览器用到的自绘对话框。
 *
 * 为什么必须自绘：服务上下文没有 Activity 令牌，`AlertDialog` / 系统 Dialog 会抛
 * `WindowManager$BadTokenException`（P0 实测）。所有对话框都用 `Dialog` + overlay 窗口类型。
 */
internal object BrowserDialogs {

    private fun baseColumn(context: Context): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFFFFFFF.toInt())
            setPadding(24, 20, 24, 16)
        }

    private fun dialogOf(context: Context): android.app.Dialog =
        android.app.Dialog(context, android.R.style.Theme_Material_Light_Dialog_NoActionBar).apply {
            window?.setType(OverlayWindowTypes.contentPanelWindowType(context))
        }

    private fun title(context: Context, text: String): TextView =
        TextView(context).apply {
            this.text = text
            textSize = 16f
            setTextColor(0xFF111111.toInt())
            setPadding(0, 0, 0, 12)
        }

    private fun body(context: Context, text: String): TextView =
        TextView(context).apply {
            this.text = text
            textSize = 14f
            setTextColor(0xFF333333.toInt())
            setPadding(0, 0, 0, 16)
        }

    private fun buttons(
        context: Context,
        dialog: android.app.Dialog,
        okText: String = "确定",
        cancelText: String = "取消",
        onOk: () -> Unit,
        onCancel: (() -> Unit)? = null,
    ): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            addView(Button(context).apply {
                text = cancelText
                setOnClickListener {
                    dialog.dismiss()
                    onCancel?.invoke()
                }
            })
            addView(Button(context).apply {
                text = okText
                setOnClickListener {
                    dialog.dismiss()
                    onOk()
                }
            })
        }

    fun alert(context: Context, message: String, onDone: () -> Unit) {
        val dialog = dialogOf(context)
        val column = baseColumn(context)
        column.addView(title(context, "提示"))
        column.addView(body(context, message))
        column.addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END
                addView(Button(context).apply {
                    text = "知道了"
                    setOnClickListener {
                        dialog.dismiss()
                        onDone()
                    }
                })
            },
        )
        dialog.setContentView(column)
        runCatching { dialog.show() }
    }

    fun confirm(
        context: Context,
        message: String,
        onOk: () -> Unit,
        onCancel: () -> Unit,
    ) {
        val dialog = dialogOf(context)
        val column = baseColumn(context)
        column.addView(title(context, "确认"))
        column.addView(body(context, message))
        column.addView(buttons(context, dialog, onOk = onOk, onCancel = onCancel))
        dialog.setContentView(column)
        dialog.setOnCancelListener { onCancel() }
        runCatching { dialog.show() }
    }

    fun prompt(
        context: Context,
        message: String,
        defaultValue: String,
        onOk: (String) -> Unit,
        onCancel: () -> Unit,
    ) {
        val dialog = dialogOf(context)
        val column = baseColumn(context)
        column.addView(title(context, "输入"))
        column.addView(body(context, message))
        val input = EditText(context).apply {
            setText(defaultValue)
            setTextColor(0xFF111111.toInt())
        }
        column.addView(input, LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        column.addView(buttons(context, dialog, onOk = { onOk(input.text?.toString().orEmpty()) }, onCancel = onCancel))
        dialog.setContentView(column)
        dialog.setOnCancelListener { onCancel() }
        runCatching { dialog.show() }
    }

    /** 列表选择（select 接管）。 */
    fun choose(
        context: Context,
        titleText: String,
        items: List<String>,
        selected: Int,
        onPick: (Int) -> Unit,
    ) {
        val dialog = dialogOf(context)
        val column = baseColumn(context)
        column.addView(title(context, titleText))
        items.forEachIndexed { index, label ->
            column.addView(
                TextView(context).apply {
                    text = if (index == selected) "\u2713 $label" else label
                    textSize = 15f
                    setTextColor(if (index == selected) 0xFF0A7A0A.toInt() else 0xFF222222.toInt())
                    setPadding(8, 16, 8, 16)
                    setOnClickListener {
                        dialog.dismiss()
                        onPick(index)
                    }
                },
            )
        }
        dialog.setContentView(ScrollView(context).apply { addView(column) })
        runCatching { dialog.show() }
    }

    /** 日期 / 时间选择（input[type=date/time] 接管）。 */
    fun pickDateOrTime(
        context: Context,
        type: String,
        current: String,
        onPicked: (String) -> Unit,
    ) {
        val dialog = dialogOf(context)
        val column = baseColumn(context)
        val calendar = java.util.Calendar.getInstance()
        if (type == "time") {
            val picker = android.widget.TimePicker(context).apply {
                setIs24HourView(true)
                runCatching {
                    val parts = current.split(":")
                    hour = parts.getOrNull(0)?.toIntOrNull() ?: calendar.get(java.util.Calendar.HOUR_OF_DAY)
                    minute = parts.getOrNull(1)?.toIntOrNull() ?: calendar.get(java.util.Calendar.MINUTE)
                }
            }
            column.addView(picker)
            column.addView(
                buttons(context, dialog, onOk = {
                    onPicked(String.format("%02d:%02d", picker.hour, picker.minute))
                }),
            )
        } else {
            val picker = android.widget.DatePicker(context)
            runCatching {
                val parts = current.split("-")
                if (parts.size == 3) {
                    val y = parts[0].toIntOrNull()
                    val m = parts[1].toIntOrNull()
                    val d = parts[2].toIntOrNull()
                    if (y != null && m != null && d != null) picker.updateDate(y, m - 1, d)
                }
            }
            column.addView(picker)
            column.addView(
                buttons(context, dialog, onOk = {
                    onPicked(
                        String.format(
                            "%04d-%02d-%02d",
                            picker.year,
                            picker.month + 1,
                            picker.dayOfMonth,
                        ),
                    )
                }),
            )
        }
        dialog.setContentView(ScrollView(context).apply { addView(column) })
        runCatching { dialog.show() }
    }

    @SuppressLint("SetTextI18n")
    fun info(context: Context, message: String) {
        val dialog = dialogOf(context)
        val column = baseColumn(context)
        column.addView(body(context, message))
        dialog.setContentView(column)
        runCatching { dialog.show() }
    }
}
