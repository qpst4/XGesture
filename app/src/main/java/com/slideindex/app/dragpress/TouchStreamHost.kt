/*
 * 移植自 GestureEVO（https://github.com/LumiaGG/GestureEVO，GPL-3.0-only）
 *   源文件：drop/TouchStreamHost.kt
 * 已按本项目的包结构、设置体系与流转栏集成层改写（部分逻辑为本项目新增）；
 * 版权与许可声明见 THIRD_PARTY_NOTICES.md，文件级映射见 docs/ATTRIBUTIONS.md。
 */

package com.slideindex.app.dragpress

import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import com.slideindex.app.shizuku.ITouchInterruptionGuard
import java.io.Closeable

/**
 * 特权触摸工作器：管理触摸设备枚举、流 Token 租赁、客户端 Binder 死亡监听与资源释放。
 */
internal class TouchStreamHost : Closeable {
    private var touchStream: ProcessStreamRelay? = null
    private var touchToken: String? = null
    private var touchOwner: IBinder? = null
    private var touchDeath: IBinder.DeathRecipient? = null
    private val cancelledTouchStreams = LinkedHashSet<String>()
    private val interrupter = ShizukuTouchInterrupter()

    @Synchronized
    fun open(token: String, owner: IBinder): ParcelFileDescriptor {
        require(token.matches(Regex("[a-f0-9-]{36}"))) { "Invalid token format: $token" }
        check(token !in cancelledTouchStreams) { "Token has been cancelled: $token" }
        close(touchToken.orEmpty())

        val inventory = runCatching {
            val (code, output) = ProcessCommandRunner.run(listOf("/system/bin/cat", "/proc/bus/input/devices"))
            if (code == 0) output else ""
        }.getOrDefault("")

        val (rangeCode, ranges) = ProcessCommandRunner.run(listOf("/system/bin/getevent", "-lp"))
        check(rangeCode == 0) { "Touch device discovery failed: getevent exit=$rangeCode" }

        val device = checkNotNull(TouchInputProtocol.discover(inventory, ranges)) { "No readable multitouch screen found" }
        val pipe = ParcelFileDescriptor.createPipe()
        val output = ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])
        try {
            val death = IBinder.DeathRecipient { close(token) }
            owner.linkToDeath(death, 0)
            touchOwner = owner
            touchDeath = death
            touchToken = token
            output.write("SLIDE_TOUCH ${device.minX} ${device.maxX} ${device.minY} ${device.maxY}\n".toByteArray())
            touchStream = ProcessStreamRelay(
                TouchStreamProcess.arguments(device.path),
                output,
                durationMs = null,
                byteLimit = null,
                stdinLease = true,
                leaseHeartbeatMs = TouchStreamProcess.HEARTBEAT_MS,
                receiverClosed = {
                    val descriptor = StructPollfd().apply {
                        fd = pipe[1].fileDescriptor
                        events = 0
                    }
                    Os.poll(arrayOf(descriptor), 0)
                    descriptor.revents.toInt() and (OsConstants.POLLERR or OsConstants.POLLHUP or OsConstants.POLLNVAL) != 0
                }
            )
            return pipe[0]
        } catch (error: Exception) {
            pipe.forEach { runCatching { it.close() } }
            close(token)
            throw error
        }
    }

    @Synchronized
    fun close(token: String) {
        cancelledTouchStreams.add(token)
        while (cancelledTouchStreams.size > 16) cancelledTouchStreams.remove(cancelledTouchStreams.first())
        if (token != touchToken) return
        touchStream?.close()
        touchStream = null
        touchToken = null
        touchDeath?.let { death -> runCatching { touchOwner?.unlinkToDeath(death, 0) } }
        touchDeath = null
        touchOwner = null
    }

    @Synchronized
    fun interrupt(
        token: String,
        sourcePackage: String,
        x: Float,
        y: Float,
        displayId: Int,
        deadlineUptimeMs: Long,
        guard: ITouchInterruptionGuard,
    ): Boolean {
        fun current(): Boolean = token == touchToken &&
            touchOwner?.isBinderAlive == true &&
            touchStream?.isProcessAlive() == true &&
            guard.asBinder().isBinderAlive &&
            deadlineUptimeMs - SystemClock.uptimeMillis() in 1L..500L &&
            runCatching { guard.isCurrent }.getOrDefault(false)

        if (!current()) return false
        return interrupter.interrupt(sourcePackage, x, y, displayId, ::current)
    }

    @Synchronized
    override fun close() = close(touchToken.orEmpty())
}
