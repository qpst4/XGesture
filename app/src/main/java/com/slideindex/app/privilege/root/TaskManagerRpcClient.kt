package com.slideindex.app.privilege.root



import com.slideindex.app.shizuku.IOnMultiTouchGesture
import com.slideindex.app.shizuku.ITaskManagerService



internal class TaskManagerRpcClient(

    private val port: Int,

) : ITaskManagerService.Stub() {



    override fun destroy() {

        runCatching { TaskManagerDaemonRpc.execute(port, 16777114, emptyList()) }

    }



    override fun removeTaskById(taskId: String?): Boolean =

        rpcBool(1, listOf(taskId))



    override fun getFrontTaskId(): String? =

        rpcString(2, emptyList())



    override fun getTaskIdsForPackage(packageName: String?): Array<String> =

        rpcStringArray(3, listOf(packageName))



    override fun getRecentTaskPackages(): Array<String> =

        rpcStringArray(4, emptyList())



    override fun moveTaskToFreeWindow(

        taskId: String?,

        windowingMode: Int,

        left: Int,

        top: Int,

        right: Int,

        bottom: Int,

    ): Boolean = rpcBool(

        5,

        listOf(

            taskId,

            windowingMode.toString(),

            left.toString(),

            top.toString(),

            right.toString(),

            bottom.toString(),

        ),

    )



    override fun getApiVersion(): Int =

        (TaskManagerDaemonRpc.execute(port, 6, emptyList()) as RpcValue.I32).value



    override fun getFrontTaskPackage(): String? =

        rpcString(7, emptyList())



    override fun forceStopPackage(packageName: String?): Boolean =

        rpcBool(8, listOf(packageName))



    override fun getPublishedShortcuts(packageName: String?): Array<String> =

        rpcStringArray(9, listOf(packageName))



    override fun startPublishedShortcut(packageName: String?, shortcutId: String?): Boolean =

        rpcBool(10, listOf(packageName, shortcutId))



    override fun getRecentTasks(): Array<String> =

        rpcStringArray(11, emptyList())



    override fun switchToTask(taskId: String?, identifier: String?, topComponent: String?): Boolean =

        rpcBool(12, listOf(taskId, identifier, topComponent))



    override fun showVoiceAssistant(): Boolean =

        rpcBool(13, emptyList())



    override fun runShellCommand(cmd: Array<out String>?): Boolean =

        rpcBool(14, cmd?.map { it } ?: emptyList())



    override fun runShellCommandOutput(cmd: Array<out String>?): String? =

        rpcString(15, cmd?.map { it } ?: emptyList())



    override fun runShellCommandLine(command: String?, useRoot: Boolean, forceAdb: Boolean): String? =

        rpcString(16, listOf(command, useRoot.toString(), forceAdb.toString()))



    override fun probeRootAvailable(): Boolean =

        rpcBool(17, emptyList())



    override fun getAllPublishedShortcuts(): Array<String> =

        rpcStringArray(18, emptyList())



    override fun getShortcutIconBytes(packageName: String?, shortcutId: String?, userId: Int): ByteArray? =

        (TaskManagerDaemonRpc.execute(port, 19, listOf(packageName, shortcutId, userId.toString())) as RpcValue.Bytes).value



    private fun rpcBool(op: Int, args: List<String?>): Boolean =

        (TaskManagerDaemonRpc.execute(port, op, args) as RpcValue.Bool).value



    private fun rpcString(op: Int, args: List<String?>): String? =

        (TaskManagerDaemonRpc.execute(port, op, args) as RpcValue.Str).value



    private fun rpcStringArray(op: Int, args: List<String?>): Array<String> =

        (TaskManagerDaemonRpc.execute(port, op, args) as RpcValue.StrArr).value

    // 多指手势观察只由 Shizuku 用户服务实现；root 模式走下面的轮询接口。
    override fun startMultiTouchObservation(
        callback: IOnMultiTouchGesture,
        pathPriority: Boolean,
    ): Boolean = false

    override fun stopMultiTouchObservation() = Unit

    override fun isMultiTouchObservationRunning(): Boolean = false

    /** root 模式：服务端入队观察事件，app 轮询取走。 */
    override fun startMultiTouchPolling(pathPriority: Boolean): Boolean =
        rpcBool(100, listOf(if (pathPriority) "1" else "0"))

    override fun pollMultiTouchEvents(): Array<String> =
        rpcStringArray(102, emptyList())

    override fun stopMultiTouchPolling() {
        runCatching { TaskManagerDaemonRpc.execute(port, 101, emptyList()) }
    }

}


