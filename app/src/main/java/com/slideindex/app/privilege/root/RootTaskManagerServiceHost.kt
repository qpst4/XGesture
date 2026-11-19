package com.slideindex.app.privilege.root



import android.content.Context

import android.os.SystemClock

import android.util.Log

import com.slideindex.app.shizuku.ITaskManagerService

import com.slideindex.app.shizuku.TaskManagerShellExecutor

import java.io.File

import java.util.concurrent.CountDownLatch

import java.util.concurrent.TimeUnit



/**

 * Root-only counterpart to [ShizukuUserServiceHost]: shell [TaskManagerUserService] via RPC

 * (and ServiceManager when the ROM allows lookup).

 */

object RootTaskManagerServiceHost {



    private const val TAG = "RootTaskManagerHost"

    private const val SPAWN_TIMEOUT_MS = 8_000L

    private const val ENSURE_TIMEOUT_MS = 12_000L



    private const val PRE_SPAWN_LOOKUP_TRIES = 3

    private const val POST_SPAWN_LOOKUP_TRIES_FULL = 12

    private const val POST_SPAWN_LOOKUP_TRIES_QUICK = 6

    private const val RETRY_DELAY_MS = 120L



    private const val DAEMON_BACKOFF_MS = 15_000L

    private const val MIN_SPAWN_INTERVAL_MS = 5_000L

    private const val LIVE_PORT_FILE_MS = 60_000L



    private val bindLock = Any()

    private val spawnLock = Any()



    @Volatile

    private var service: ITaskManagerService? = null



    @Volatile

    private var ensureLatch: CountDownLatch? = null



    @Volatile

    private var daemonBackoffUntilMs: Long = 0L



    @Volatile

    private var lastSpawnAtMs: Long = 0L



    @Volatile

    private var cachedRpcPort: Int = 0



    fun peek(): ITaskManagerService? =

        service?.takeIf { it.asBinder().pingBinder() }



    fun readApi(taskService: ITaskManagerService?): Int =

        taskService?.let { runCatching { it.apiVersion }.getOrDefault(0) } ?: 0



    fun ensureQuick(context: Context, minApi: Int = 0): ITaskManagerService? {
        peek()?.let { live ->
            val api = readApi(live)
            if (api > 0 && api >= minApi) return live
            drop(context)
        }
        if (SystemClock.elapsedRealtime() < daemonBackoffUntilMs) return null
        synchronized(bindLock) {
            ensureLatch?.let { latch ->
                runCatching { latch.await(2_500L, TimeUnit.MILLISECONDS) }
                return peek()?.takeIf {
                    val api = readApi(it)
                    api > 0 && api >= minApi
                }
            }
        }
        return connectOrSpawn(context, minApi, quick = true)
    }

    fun ensure(context: Context, minApi: Int = 0): ITaskManagerService? {
        peek()?.let { live ->
            val api = readApi(live)
            if (api > 0 && api >= minApi) return live
            Log.w(TAG, "stale api=$api need=$minApi, dropping")
            drop(context)
        }



        var latch: CountDownLatch

        var isLeader = false

        synchronized(bindLock) {

            val inFlight = ensureLatch

            if (inFlight != null) {

                latch = inFlight

            } else {

                latch = CountDownLatch(1)

                ensureLatch = latch

                isLeader = true

            }

        }



        if (!isLeader) {
            runCatching { latch.await(ENSURE_TIMEOUT_MS, TimeUnit.MILLISECONDS) }
            return synchronized(bindLock) {
                peek()?.takeIf {
                    val api = readApi(it)
                    api > 0 && api >= minApi
                }
            }
        }



        return try {

            if (SystemClock.elapsedRealtime() < daemonBackoffUntilMs) {

                null

            } else {

                connectOrSpawn(context, minApi, quick = false)

            }

        } finally {

            synchronized(bindLock) {

                ensureLatch = null

                latch.countDown()

            }

        }

    }



    fun restart(context: Context): Int {

        daemonBackoffUntilMs = 0L

        drop(context)

        killStaleDaemons(context)

        val bound = ensure(context, minApi = 0)

        return readApi(bound).takeIf { it > 0 } ?: -1

    }



    fun drop(context: Context) {

        synchronized(bindLock) {

            runCatching { service?.destroy() }

            service = null

            cachedRpcPort = 0

        }

    }



    internal fun daemonServiceName(context: Context): String =

        RootBinderPublish.serviceName(context.packageName)



    internal fun portFilePath(packageName: String): String {

        val hash = packageName.hashCode().toUInt().toString(16)

        return "/data/local/tmp/slideindex_${hash}_taskmgr.port"

    }



    private fun portFilePath(context: Context): String = portFilePath(context.packageName)



    private fun connectOrSpawn(context: Context, minApi: Int, quick: Boolean): ITaskManagerService? {

        repeat(PRE_SPAWN_LOOKUP_TRIES) {

            bindFromRemote(context, minApi)?.let { return it }

            Thread.sleep(RETRY_DELAY_MS)

        }



        val spawned = maybeSpawnDaemon(context, minApi)

        val postTries = if (quick) POST_SPAWN_LOOKUP_TRIES_QUICK else POST_SPAWN_LOOKUP_TRIES_FULL

        repeat(postTries) {

            bindFromRemote(context, minApi)?.let { return it }

            Thread.sleep(RETRY_DELAY_MS)

        }



        if (quick) {

            daemonBackoffUntilMs = SystemClock.elapsedRealtime() + DAEMON_BACKOFF_MS

        }

        Log.w(

            TAG,

            "ensure failed service=${daemonServiceName(context)} minApi=$minApi quick=$quick spawned=$spawned",

        )

        return null

    }



    private fun bindFromRemote(context: Context, minApi: Int): ITaskManagerService? {
        val iface = resolveRemoteService(context) ?: return null
        val api = readApi(iface)
        if (api <= 0 || api < minApi) {
            drop(context)
            return null
        }

        val rooted = runCatching { iface.probeRootAvailable() }.getOrDefault(false)

        if (!rooted) {

            Log.w(TAG, "rejecting daemon: not running as root (api=$api)")

            drop(context)

            killStaleDaemons(context)

            return null

        }

        synchronized(bindLock) {

            service = iface

        }

        daemonBackoffUntilMs = 0L

        Log.i(TAG, "connected api=$api via=${connectionLabel(iface)}")

        return iface

    }



    private fun connectionLabel(iface: ITaskManagerService): String =

        if (iface is TaskManagerRpcClient) "rpc" else "binder"



    private fun resolveRemoteService(context: Context): ITaskManagerService? {

        val serviceName = daemonServiceName(context)

        RootBinderPublish.lookup(serviceName)?.let { binder ->

            return ITaskManagerService.Stub.asInterface(binder)

        }

        val port = cachedRpcPort.takeIf { it > 0 } ?: readDaemonPort(portFilePath(context)) ?: return null

        cachedRpcPort = port

        val rpc = TaskManagerRpcClient(port)

        val api = runCatching { rpc.apiVersion }.getOrDefault(0)

        if (api <= 0) {

            cachedRpcPort = 0

            return null

        }

        return rpc

    }



    private fun readDaemonPort(portFile: String): Int? {

        return runCatching {

            val file = File(portFile)

            if (!file.exists()) return null

            file.readText().trim().toIntOrNull()?.takeIf { it in 1..65535 }

        }.getOrNull()

    }



    private fun daemonPortFileLooksLive(context: Context): Boolean {
        val file = File(portFilePath(context))
        if (!file.exists()) return false
        val port = readDaemonPort(file.path) ?: run {
            runCatching { file.delete() }
            return false
        }
        val isReachable = runCatching {
            java.net.Socket().use { s ->
                s.connect(java.net.InetSocketAddress("127.0.0.1", port), 200)
                true
            }
        }.getOrDefault(false)
        if (!isReachable) {
            runCatching { file.delete() }
            return false
        }
        return true
    }



    private fun maybeSpawnDaemon(context: Context, minApi: Int = 0): Boolean {

        if (daemonPortFileLooksLive(context)) {

            return false

        }

        synchronized(spawnLock) {

            val now = SystemClock.elapsedRealtime()

            if (now - lastSpawnAtMs < MIN_SPAWN_INTERVAL_MS) {

                return false

            }

            lastSpawnAtMs = now

        }

        spawnDaemon(context, minApi)

        return true

    }



    private fun killStaleDaemons(context: Context) {
        cachedRpcPort = 0
        val marker = TaskManagerRootDaemonMain::class.java.name
        val q = TaskManagerShellExecutor.shellQuote(marker)
        val pkg = TaskManagerShellExecutor.shellQuote(context.packageName)
        val portFile = TaskManagerShellExecutor.shellQuote(portFilePath(context))
        TaskManagerShellExecutor.runAsRootUser(
            "pkill -f $q 2>/dev/null; pkill -f ${pkg}:task_manager 2>/dev/null; rm -f $portFile; true",
            SPAWN_TIMEOUT_MS,
        )
    }



    private fun spawnDaemon(context: Context, minApi: Int = 0) {

        val existing = bindFromRemote(context, minApi = 0)
        if (existing != null) {
            val api = readApi(existing)
            if (api > 0 && api >= minApi) {
                return
            }
            Log.w(TAG, "spawnDaemon: killing stale daemon with api=$api (need $minApi)")
            killStaleDaemons(context)
            drop(context)
        } else if (!daemonPortFileLooksLive(context)) {

            killStaleDaemons(context)

        }

        val apk = context.applicationInfo.sourceDir

        val mainClass = TaskManagerRootDaemonMain::class.java.name

        val pkg = context.packageName

        val processLine = buildString {

            if (RootNamespace.isUsable()) {

                // 必须从 init 的 mount namespace 启动：否则 /data/data 只剩本 App 的 tmpfs 桩。
                append("CLASSPATH=").append(apk).append(' ')

                append("exec ").append(RootNamespace.NSENTER_PATH).append(" -t 1 -m -- ")

                append("/system/bin/app_process /system/bin ").append(mainClass).append(' ')

                append(TaskManagerShellExecutor.shellQuote(pkg))

            } else if (RootNamespace.mountMasterUsable()) {

                // 没有 nsenter：用 su --mount-master 在全局 namespace 里起 daemon。
                val inner = "CLASSPATH=" + apk + " exec app_process /system/bin " +
                    mainClass + " " + TaskManagerShellExecutor.shellQuote(pkg)

                append("exec ").append(TaskManagerShellExecutor.resolveSuInvocation())

                append(" --mount-master -c ")

                append(TaskManagerShellExecutor.shellQuote(inner))

            } else {

                append("CLASSPATH=").append(apk).append(' ')

                append("exec app_process /system/bin ").append(mainClass).append(' ')

                append(TaskManagerShellExecutor.shellQuote(pkg))

            }

        }

        val launch = "nohup sh -c ${TaskManagerShellExecutor.shellQuote(processLine)} >/dev/null 2>&1 &"

        val result = TaskManagerShellExecutor.runAsRootUser(launch, SPAWN_TIMEOUT_MS)

        if (result.exitCode != 0) {

            Log.w(TAG, "root spawn failed exit=${result.exitCode} preview=${result.output.take(80)}")

        }

    }

}


