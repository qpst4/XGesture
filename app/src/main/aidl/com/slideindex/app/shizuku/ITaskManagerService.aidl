package com.slideindex.app.shizuku;

import com.slideindex.app.shizuku.IOnMultiTouchGesture;

interface ITaskManagerService {
    void destroy() = 16777114;

    boolean removeTaskById(String taskId) = 1;
    String getFrontTaskId() = 2;
    String[] getTaskIdsForPackage(String packageName) = 3;
    String[] getRecentTaskPackages() = 4;
    boolean moveTaskToFreeWindow(String taskId, int windowingMode, int left, int top, int right, int bottom) = 5;
    int getApiVersion() = 6;
    String getFrontTaskPackage() = 7;
    boolean forceStopPackage(String packageName) = 8;
    String[] getPublishedShortcuts(String packageName) = 9;
    boolean startPublishedShortcut(String packageName, String shortcutId) = 10;
    /** Each row: package<TAB>shortcutId<TAB>label */
    String[] getAllPublishedShortcuts() = 18;
    /** Each row: taskId<TAB>identifier<TAB>title<TAB>topComponent */
    String[] getRecentTasks() = 11;
    boolean switchToTask(String taskId, String identifier, String topComponent) = 12;
    boolean showVoiceAssistant() = 13;
    boolean runShellCommand(in String[] cmd) = 14;
    /** Returns "exitCode\\n---\\noutput" */
    String runShellCommandOutput(in String[] cmd) = 15;
    /** Returns "exitCode\\n---\\noutput" */
    String runShellCommandLine(String command, boolean useRoot, boolean forceAdb) = 16;
    /** Whether root execution is available from the current Shizuku service. */
    boolean probeRootAvailable() = 17;
    /** PNG bytes for a published shortcut icon, or null when unavailable. */
    byte[] getShortcutIconBytes(String packageName, String shortcutId, int userId) = 19;

    /**
     * 开始多指手势观察（读 /dev/input/event*，只读，不拦截）。
     *
     * 观察线程跑在 shell 用户服务里；识别到一次手势就通过 [IOnMultiTouchGesture] 回传一条。
     * 重复调用会先停掉旧的观察器。返回 false 表示当前服务拿不到触摸设备（权限/设备异常）。
     */
    boolean startMultiTouchObservation(in IOnMultiTouchGesture callback, boolean pathPriority) = 20;

    void stopMultiTouchObservation() = 21;

    boolean isMultiTouchObservationRunning() = 22;

    /**
     * root 模式：RPC 无法传 binder callback，改为服务端入队、app 轮询取事件。
     *
     * 事件格式：`g:手势 ordinal:触点数` 或 `p:触点数:x,y;x,y|x,y`。
     */
    boolean startMultiTouchPolling(boolean pathPriority) = 23;

    String[] pollMultiTouchEvents() = 24;

    void stopMultiTouchPolling() = 25;

    /**
     * 横屏方向补偿：把触摸坐标在 service 侧统一旋转成「当前屏幕坐标」再识别。
     *
     * 对齐 GMD：旋转发生在入口，后面的滑动方向、路径录制/匹配、锚点就都自动一致，
     * 不需要在 app 侧逐个手势再翻译一次。rotation 为 Display.getRotation()；
     * 屏幕旋转变化时 app 会再次调用。旧版 service 没有这个接口，调用会失败，
     * app 侧回退到「panel 坐标 + 方向重映射」的旧行为。
     */
    void setMultiTouchDisplay(int rotation, int screenWidthPx, int screenHeightPx) = 26;

    /**
     * 手动覆盖触摸协议 / 设备。protocol = auto|type_a|type_b；devicePath 空串 = 自动。
     * 自动模式下观测器还会用真实事件流嗅探协议，这里的手动值优先级最高。
     */
    void setMultiTouchConfig(String protocol, String devicePath) = 27;

    /** 多指观察诊断（选中设备/协议/候选列表），设置页「诊断」展示用。 */
    String getMultiTouchDiagnostics() = 28;

    /**
     * 触摸设备候选列表，每行 `eventPath|name|protocol|width|height`。
     * 设置页「触摸设备」选择用；空数组表示还没枚举到。
     */
    String[] getTouchDevices() = 29;
}
