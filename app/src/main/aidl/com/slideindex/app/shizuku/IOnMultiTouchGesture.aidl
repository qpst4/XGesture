package com.slideindex.app.shizuku;

/**
 * Shizuku 用户服务（shell 进程）向 app 回调的多指手势事件。
 *
 * 事件很稀疏（一次手势才一条），并且进程间只传枚举序号与时间戳，不传坐标轨迹。
 */
oneway interface IOnMultiTouchGesture {
    /**
     * @param gesture [com.slideindex.app.gesture.multitouch.MultiPointerGesture] 的 ordinal
     * @param fingerCount 触发时的触点数（3 / 4）
     * @param eventTimeMs 观察进程的单调时钟（elapsedRealtime）
     */
    void onGesture(int gesture, int fingerCount, long eventTimeMs);

    /** 观察器状态变化（启动 / 停止 / 找不到触摸屏 / 读取出错）。 */
    void onObserverState(boolean running, String detail);

    /**
     * 一次多指路径手势（抬手时回调）。
     *
     * @param points 归一化坐标，x/y 交替排列
     */
    void onPathGesture(int fingerCount, in float[] points, long eventTimeMs);

    /**
     * 检测到的虚拟/中继触摸设备（其它触控工具创建的），null 表示没有。
     */
    void onInputConflict(String devices);
}
