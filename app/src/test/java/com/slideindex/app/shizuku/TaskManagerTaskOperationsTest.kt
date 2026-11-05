package com.slideindex.app.shizuku

import com.slideindex.app.util.TaskManagerTaskQueries
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class TaskManagerTaskOperationsTest {

    @Test
    fun removeTaskById_returnsFalseForNull() {
        assertFalse(operations().removeTaskById(null))
    }

    @Test
    fun removeTaskById_returnsFalseForZero() {
        assertFalse(operations().removeTaskById("0"))
    }

    @Test
    fun removeTaskById_returnsFalseForNonNumeric() {
        assertFalse(operations().removeTaskById("not-a-task"))
    }

    @Test
    fun removeTaskById_fallsBackToShellWhenRecentsRemovalFails() {
        val shell = RecordingShellRunner(commandsToSucceed = 1)
        val operations = TaskManagerTaskOperations(
            shell = shell,
            recents = FakeRecentsReader(removeTaskResult = false),
        )

        assertTrue(operations.removeTaskById("42"))
        assertEquals(
            listOf(listOf("cmd", "activity", "task", "remove", "42")),
            shell.commands,
        )
    }

    @Test
    fun getRecentTasks_formatsRowsForBinder() {
        val operations = TaskManagerTaskOperations(
            shell = RecordingShellRunner(),
            recents = FakeRecentsReader(
                tasks = listOf(
                    SystemRecentsAccess.Task(
                        taskId = 7,
                        packageName = "com.example",
                        component = "com.example/.MainActivity",
                        title = "Example",
                    ),
                ),
            ),
        )

        assertArrayEquals(
            arrayOf("7\tcom.example/.MainActivity\tExample\tcom.example/.MainActivity"),
            operations.getRecentTasks(),
        )
    }

    @Test
    fun getRecentTasks_parsesThroughFakeBinderService() {
        val operations = TaskManagerTaskOperations(
            shell = RecordingShellRunner(),
            recents = FakeRecentsReader(
                tasks = listOf(
                    SystemRecentsAccess.Task(
                        taskId = 9,
                        packageName = "com.demo",
                        component = "com.demo/.Home",
                        title = "Demo",
                    ),
                ),
            ),
        )
        val service = BinderBackedTaskManagerService(operations)

        val parsed = TaskManagerTaskQueries.fetchRecentTasksFromService(service)

        assertEquals(1, parsed.size)
        assertEquals(9, parsed.first().taskId)
        assertEquals("com.demo/.Home", parsed.first().identifier)
        assertEquals("Demo", parsed.first().title)
    }

    @Test
    fun removeTaskById_invokesBinderService() {
        val shell = RecordingShellRunner(commandsToSucceed = 1)
        val operations = TaskManagerTaskOperations(
            shell = shell,
            recents = FakeRecentsReader(removeTaskResult = false),
        )
        val service = BinderBackedTaskManagerService(operations)

        assertTrue(service.removeTaskById("15"))
    }

    @Test
    fun switchToTask_returnsFalseWhenUnresolved() {
        assertFalse(operations().switchToTask(null, null, null))
        assertFalse(operations().switchToTask("", "", null))
    }

    @Test
    fun forceStopPackage_returnsFalseForBlank() {
        assertFalse(operations().forceStopPackage(null))
        assertFalse(operations().forceStopPackage("  "))
    }

    @Test
    fun startPublishedShortcut_returnsFalseForBlankArgs() {
        assertFalse(operations().startPublishedShortcut(null, "id"))
        assertFalse(operations().startPublishedShortcut("com.test", null))
        assertFalse(operations().startPublishedShortcut("", ""))
    }

    private fun operations(): TaskManagerTaskOperations =
        TaskManagerTaskOperations(
            shell = RecordingShellRunner(),
            recents = FakeRecentsReader(),
        )

    private class RecordingShellRunner(
        private val commandsToSucceed: Int = 0,
    ) : ShellCommandRunner {
        val commands = mutableListOf<List<String>>()
        private var successCount = 0

        override fun shellCommand(vararg cmd: String): Boolean {
            commands += cmd.toList()
            successCount += 1
            return successCount <= commandsToSucceed
        }
    }

    private class FakeRecentsReader(
        private val tasks: List<SystemRecentsAccess.Task> = emptyList(),
        private val removeTaskResult: Boolean = false,
        private val switchToTaskResult: Boolean = true,
    ) : RecentsReader {
        var removedTaskId: Int? = null

        override fun listTasks(): List<SystemRecentsAccess.Task> = tasks

        override fun removeTask(taskId: Int): Boolean {
            removedTaskId = taskId
            return removeTaskResult
        }

        override fun frontTask(): SystemRecentsAccess.Task? = tasks.firstOrNull()

        override fun findTaskId(identifier: String): Int? =
            tasks.firstOrNull { it.component == identifier }?.taskId

        override fun switchToTask(taskId: Int): Boolean = switchToTaskResult

        override fun matchesPackage(task: SystemRecentsAccess.Task, packageName: String): Boolean =
            task.packageName == packageName
    }

    private class BinderBackedTaskManagerService(
        private val operations: TaskManagerTaskOperations,
    ) : ITaskManagerService.Stub() {
        override fun destroy() = Unit

        override fun removeTaskById(taskIdStr: String?): Boolean =
            operations.removeTaskById(taskIdStr)

        override fun getRecentTasks(): Array<String> =
            operations.getRecentTasks()

        override fun getFrontTaskId(): String = operations.getFrontTaskId()

        override fun getTaskIdsForPackage(packageName: String?): Array<String> =
            operations.getTaskIdsForPackage(packageName)

        override fun getRecentTaskPackages(): Array<String> =
            operations.getRecentTaskPackages()

        override fun moveTaskToFreeWindow(
            taskIdStr: String?,
            windowingMode: Int,
            left: Int,
            top: Int,
            right: Int,
            bottom: Int,
        ): Boolean = false

        override fun getApiVersion(): Int = 0

        override fun getFrontTaskPackage(): String = operations.getFrontTaskPackage()

        override fun forceStopPackage(packageName: String?): Boolean =
            operations.forceStopPackage(packageName)

        override fun getPublishedShortcuts(packageName: String?): Array<String> = emptyArray()

        override fun startPublishedShortcut(packageName: String?, shortcutId: String?): Boolean =
            operations.startPublishedShortcut(packageName, shortcutId)

        override fun getAllPublishedShortcuts(): Array<String> = emptyArray()

        override fun switchToTask(
            taskIdStr: String?,
            identifier: String?,
            topComponentStr: String?,
        ): Boolean = operations.switchToTask(taskIdStr, identifier, topComponentStr)

        override fun showVoiceAssistant(): Boolean = operations.showVoiceAssistant()

        override fun runShellCommand(cmd: Array<out String>?): Boolean = false

        override fun runShellCommandOutput(cmd: Array<out String>?): String = ""

        override fun runShellCommandLine(command: String?, useRoot: Boolean, forceAdb: Boolean): String = ""

        override fun probeRootAvailable(): Boolean = false

        override fun getShortcutIconBytes(
            packageName: String?,
            shortcutId: String?,
            userId: Int,
        ): ByteArray? = null

        // 多触点观测 / root 轮询：这个测试只覆盖任务操作，桩里一律当"未运行"处理。
        override fun startMultiTouchObservation(
            callback: IOnMultiTouchGesture,
            pathPriority: Boolean,
        ): Boolean = false

        override fun stopMultiTouchObservation() = Unit

        override fun isMultiTouchObservationRunning(): Boolean = false

        override fun startMultiTouchPolling(pathPriority: Boolean): Boolean = false

        override fun pollMultiTouchEvents(): Array<String> = emptyArray()

        override fun stopMultiTouchPolling() = Unit
    }
}
