package com.slideindex.app.settings.macro

/**
 * 新建 / 编辑宏时的表单状态与校验（纯逻辑，不依赖 Android）。
 *
 * 界面层把输入框 / 选择器的值同步进来，用它算"能不能保存"、"能不能开始录制"。
 */
data class MacroEditState(
    /** 正在编辑的宏 id（界面层已经有 id，这里只是留着做展示用）。 */
    val macroId: String = "",
    val name: String = "",
    val targetPackage: String = "",
    /** 是否处于录制中（由录制器回调驱动，本阶段界面只负责按它切文案）。 */
    val recording: Boolean = false,
) {

    /** 名字是否已填（保存的前置条件）。 */
    val hasName: Boolean get() = MacroValidation.isNameValid(name)

    /** 是否已选目标 App；没选 = 全局宏（不拉起任何 App，从当前界面开始录 / 放）。 */
    val hasTargetPackage: Boolean get() = targetPackage.isNotBlank()

    /** 点"保存"该不该被拦住：只有名字是硬性的。 */
    val canSave: Boolean get() = hasName

    /**
     * 点"开始录制"该不该被拦住：只有名字是硬性的。
     *
     * 目标应用是**可选**的（未选 = 全局宏，从当前界面开始录），所以不参与这里。
     */
    val canStartRecording: Boolean get() = hasName
}

/** 宏名的校验规则。 */
object MacroValidation {

    /** 宏名必填：去掉首尾空白后不能为空。 */
    fun isNameValid(name: String): Boolean = name.isNotBlank()

    /** 落盘前把名字收成最终值（首尾空白只在判断时忽略，写回时也一并去掉）。 */
    fun normalizeName(name: String): String = name.trim()
}
