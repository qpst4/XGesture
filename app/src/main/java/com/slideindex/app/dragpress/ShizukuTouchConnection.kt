/*
 * 移植自 GestureEVO（https://github.com/LumiaGG/GestureEVO，GPL-3.0-only）
 *   源文件：drop/ShizukuTouchConnection.kt
 * 已按本项目的包结构、设置体系与流转栏集成层改写（部分逻辑为本项目新增）；
 * 版权与许可声明见 THIRD_PARTY_NOTICES.md，文件级映射见 docs/ATTRIBUTIONS.md。
 */

package com.slideindex.app.dragpress

import android.content.Context
import android.content.pm.PackageManager
import android.os.Binder
import android.os.IBinder
import android.os.ParcelFileDescriptor
import com.slideindex.app.shizuku.ITaskManagerService
import com.slideindex.app.shizuku.ITouchInterruptionGuard
import com.slideindex.app.shizuku.ShizukuUserServiceHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.Closeable
import java.util.UUID

/**
 * 管理 App 侧与 Shizuku 服务的长按触摸管道连接与远程触摸打断调用。
 */
internal class ShizukuTouchConnection(
    private val context: Context,
) : Closeable {
    private val token = UUID.randomUUID().toString()
    private val owner = Binder()
    private val death = IBinder.DeathRecipient { close() }
    private var closed = false
    private var remote: ITaskManagerService? = null
    private var descriptor: ParcelFileDescriptor? = null

    suspend fun open(): ParcelFileDescriptor = withContext(Dispatchers.IO) {
        check(Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            "Shizuku permission unavailable"
        }
        val shell = ShizukuUserServiceHost.ensure(context, minApi = ShizukuUserServiceHost.SERVICE_BUILD)
            ?: error("Shizuku service unavailable")
        currentCoroutineContext().ensureActive()
        synchronized(this@ShizukuTouchConnection) {
            check(!closed) { "Shizuku input cancelled" }
            remote = shell
            shell.asBinder().linkToDeath(death, 0)
        }
        val pipe = shell.openTouchStream(token, owner) ?: error("Failed to open touch stream from shell")
        synchronized(this@ShizukuTouchConnection) {
            if (closed) {
                pipe.close()
                error("Shizuku input cancelled")
            }
            descriptor = pipe
        }
        pipe
    }

    override fun close() {
        val shell = synchronized(this) {
            if (closed) return
            closed = true
            runCatching { descriptor?.close() }
            descriptor = null
            remote.also { remote = null }
        }
        runCatching { shell?.asBinder()?.unlinkToDeath(death, 0) }
        runCatching { shell?.closeTouchStream(token) }
    }

    suspend fun interrupt(
        sourcePackage: String,
        x: Float,
        y: Float,
        displayId: Int,
        deadlineUptimeMs: Long,
        guard: ITouchInterruptionGuard,
    ): Boolean = withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        val shell = synchronized(this@ShizukuTouchConnection) { if (closed) null else remote }
            ?: return@withContext false
        shell.interruptTouchForDrag(token, sourcePackage, x, y, displayId, deadlineUptimeMs, guard)
    }
}
