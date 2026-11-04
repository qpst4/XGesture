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
}
