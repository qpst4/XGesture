package com.slideindex.app.macro

import android.content.Context
import java.util.UUID

/**
 * 宏列表的持久化。
 *
 * 刻意用一个**独立的小 prefs 文件**，而不是塞进主设置：宏是"用户数据"而不是"配置"，
 * 独立存放的好处是清空设置不会顺手清掉用户的宏，导入导出也更干净。
 *
 * 任何持有 Context 的模块都能直接用（界面层、无障碍服务、测试），不需要 DI 接线。
 */
object MacroStore {

    const val PREFS_NAME = "xgesture_macros"

    /**
     * 宏数据代次：任何写操作后 +1。
     *
     * 宏本身没有可观察的数据流，而图标 / 名字这些派生显示都带缓存（面板格子、编辑器预览）。
     * 缓存键必须带上它，否则改了宏（目标 App、名字）之后，已经画出来的格子还是旧值。
     * 进程内计数即可：宏表只在本进程读写。
     */
    @Volatile
    var revision: Int = 0
        private set

    /** 读出全部宏（坏数据自动退化成空列表，见 [MacroJson.decode]）。 */
    fun all(context: Context): List<Macro> =
        MacroJson.decode(prefs(context).getString(MacroJson.PREF_KEY, null))

    /** 整份覆盖写。 */
    fun save(context: Context, macros: List<Macro>) {
        prefs(context).edit()
            .putString(MacroJson.PREF_KEY, MacroJson.encode(macros))
            .apply()
        revision++
    }

    /** 新增或按 id 覆盖一条，返回写回后的完整列表。 */
    fun upsert(context: Context, macro: Macro): List<Macro> {
        val current = all(context)
        val index = current.indexOfFirst { it.id == macro.id }
        val next = if (index >= 0) {
            current.toMutableList().also { it[index] = macro }
        } else {
            current + macro
        }
        save(context, next)
        return next
    }

    /** 按 id 删除，返回剩下的列表。 */
    fun delete(context: Context, id: String): List<Macro> {
        val next = all(context).filterNot { it.id == id }
        save(context, next)
        return next
    }

    fun byId(context: Context, id: String): Macro? = all(context).firstOrNull { it.id == id }

    /** 新宏的 id。 */
    fun newId(): String = UUID.randomUUID().toString()

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
