package com.slideindex.app.shizuku;

/**
 * Shizuku 用户服务（shell 进程）向 app 回调的多指手势事件。
 *
 * 事件很稀疏（一次手势才一条），进程间只传枚举序号、时间戳与一个触摸锚点。
 *
 * 锚点 `anchorU / anchorV` 是**触摸面板自然坐标**里的位置（0..1，u 沿面板自然 X 轴、v 沿自然 Y 轴），
 * 取「这次触摸手指摆好时」的质心。面板（快速启动器这类贴边浮层）靠它决定从哪一侧、
 * 哪个高度出来；换算成屏幕坐标要用 app 侧当前的 Display 旋转 —— 服务进程只知道原始坐标，
 * 不知道旋转，所以坐标在这里保持「面板自然方向」。
 */
oneway interface IOnMultiTouchGesture {
    /**
     * @param gesture [com.slideindex.app.gesture.multitouch.MultiPointerGesture] 的 ordinal
     * @param fingerCount 触发时的触点数（3 / 4）
     * @param eventTimeMs 观察进程的单调时钟（elapsedRealtime）
     */
    void onGesture(int gesture, int fingerCount, long eventTimeMs);

    /**
     * 一次触摸的多个候选（每种类型独立判定，可能同时命中捏合和滑动等）。
     *
     * @param kinds [com.slideindex.app.gesture.multitouch.MultiPointerGesture] 的 ordinal
     * @param fingerCounts 与 [kinds] 一一对应的触点数
     * @param eventTimeMs 观察进程的单调时钟（elapsedRealtime）
     * @param anchorU 面板自然坐标里的横向位置（0..1）
     * @param anchorV 面板自然坐标里的纵向位置（0..1）
     */
    void onGestureBatch(
        in int[] kinds,
        in int[] fingerCounts,
        long eventTimeMs,
        float anchorU,
        float anchorV
    );

    /** 观察器状态变化（启动 / 停止 / 找不到触摸屏 / 读取出错）。 */
    void onObserverState(boolean running, String detail);

    /**
     * 一次多指路径手势（全部手指抬起时回调）。
     *
     * @param points 归一化坐标，x/y 交替排列
     * @param strokeLengths 每条轨迹的点数（按顺序切分 [points]）
     * @param anchorU 面板自然坐标里的横向位置（0..1），整条路径的质心
     * @param anchorV 面板自然坐标里的纵向位置（0..1）
     */
    void onPathGesture(
        int fingerCount,
        in float[] points,
        in int[] strokeLengths,
        long eventTimeMs,
        float anchorU,
        float anchorV
    );

    /**
     * 检测到的虚拟/中继触摸设备（其它触控工具创建的），null 表示没有。
     */
    void onInputConflict(String devices);
}
