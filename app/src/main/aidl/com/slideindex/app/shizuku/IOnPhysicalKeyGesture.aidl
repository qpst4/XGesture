package com.slideindex.app.shizuku;

/**
 * Shizuku / Root 用户服务（shell 进程）向 app 回调的底层硬件物理按键手势。
 */
oneway interface IOnPhysicalKeyGesture {
    /**
     * @param gestureOrdinal [com.slideindex.app.physicalkey.PhysicalKeyGesture] 的 ordinal
     * @param eventTimeMs 观察进程的单调时钟（elapsedRealtime）
     */
    void onKeyGesture(int gestureOrdinal, long eventTimeMs);

    /** 观察器状态变化（启动 / 停止 / 找不到硬件按键设备 / 读取出错）。 */
    void onObserverState(boolean running, String detail);
}
