package com.slideindex.app.message

import android.content.Context

/**
 * 阅读消息后的「返回上一应用」胶囊；由 `:app` 实现。
 *
 * 实现方在目标应用真正到前台后，于屏幕左边缘浮出胶囊；点击胶囊回到打开消息前的应用。
 */
interface MessageReturnPort {

    /**
     * 打开消息成功后调用。
     *
     * @param sourcePackage 打开消息前的前台应用，可能为空。
     * @param targetPackage 通知所属应用，即用户刚跳转过去的应用。
     * @param smallWindowRead 这次是否通过「小窗阅读」打开：小窗真的开出来时来源应用仍可见，
     *   此时不应显示返回胶囊；小窗失败退化成全屏时照常显示。
     */
    fun arm(
        context: Context,
        sourcePackage: String?,
        targetPackage: String,
        settings: MessageSettings,
        smallWindowRead: Boolean,
    )

    /** 立即收起胶囊（例如消息提醒被关闭时）。 */
    fun dismiss()
}
