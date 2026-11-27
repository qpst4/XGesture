package com.slideindex.app.xposed.bridge;

/**
 * system_server 模块 → app 的单向事件桥。
 *
 * 模块在输入层吞掉接管区域内的触摸流后，逐条把事件回传给 app，
 * 由 app 现有手势引擎（面板跟手、Pie、连续调节、子手势）继续处理。
 */
interface IModuleGestureBridge {
    /**
     * app 侧边缘 overlay 宿主是否就绪（无障碍服务已连上且 overlay 宿主已建立）。
     *
     * 宿主未就绪时 `canAcceptTouch` / `canAcceptTouchAt` 一律返回 false，模块应完全放行；
     * 这个方法只用于让模块侧的状态显示如实反映「桥已连但接管仍不生效」。
     */
    boolean isHostReady();

    /**
     * 同步询问 app 当前是否真的能处理该边的接管触摸（宿主未就绪、边被隐藏时返回 false）。
     * 模块据此在吞掉 DOWN 之前决定放行，避免事件被吞掉却无人处理。
     */
    boolean canAcceptTouch(int sideId);

    /**
     * 触钮之外的目标（角轮盘、悬浮球线条）用：带屏幕坐标实时复核命中区。
     *
     * 这类目标的命中区随位置设置、键盘弹出、悬浮球停靠变化，模块侧缓存的矩形只能用来缩小询问范围，
     * 真正的判定由 app 当次现场几何给出——返回 false 时模块必须放行，避免吞掉却无人处理。
     */
    boolean canAcceptTouchAt(int target, float x, float y);

    oneway void onTouchEvent(
        long sessionId,
        int sideId,
        int action,
        float x,
        float y,
        long eventTime,
        long downTime,
        int metaState
    );

    oneway void onSessionEnd(long sessionId, int reason);
    boolean cancelRegionalPickSelection(long sessionId, int sideId);
}
