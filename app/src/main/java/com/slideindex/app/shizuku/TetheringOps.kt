package com.slideindex.app.shizuku

import android.net.ITetheringConnector
import android.net.ITetheringEventCallback
import android.net.Network
import android.net.TetheredClient
import android.net.TetheringCallbackStartedParcel
import android.net.TetheringConfigurationParcel
import android.net.TetheringRequestParcel
import android.net.TetherStatesParcel
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import com.slideindex.app.privilege.PrivilegedCallerIdentity
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Wi-Fi 热点（网络共享）的开关与状态读取。
 *
 * **为什么必须在特权进程里跑**：`tethering` 服务的 `startTethering` / `stopTethering` 要
 * signature 级权限（`TETHER_PRIVILEGED` 一类），普通应用拿不到；而且 `android.net.ITetheringConnector`
 * 是 `@hide`。本对象只由 [TaskManagerUserService] 调用，那个类同时跑在 root 守护进程（uid 0）
 * 和 Shizuku 用户服务（uid 2000）里，两个身份都满足。
 *
 * **没有 shell 命令等价物**：`svc` / `cmd connectivity` 都没有 tether 子命令，`cmd wifi start-softap`
 * 要显式 SSID/密码且不走正常共享上网，所以这里只能调 binder。
 *
 * 编译期的 `android.net.*` 类来自 `app/src/main/aidl/android/net/` 下的垫片；运行期这些类由
 * 系统（boot classpath）提供，事务号与 parcel 布局以系统实现为准，本对象只依赖字段名/方法签名。
 */
internal object TetheringOps {

    private const val TAG = "TetheringOps"

    /** 与 [ITaskManagerService.getTetheringState] 约定一致：-1 = 读不到 / 不支持。 */
    const val UNKNOWN = -1

    private const val TETHERED = 1
    private const val NOT_TETHERED = 0

    /** `TetheringManager.TETHERING_WIFI` */
    private const val TETHERING_WIFI = 0

    /** `TetheringRequest.CONNECTIVITY_SCOPE_GLOBAL` */
    private const val CONNECTIVITY_SCOPE_GLOBAL = 1

    /** 等首次回调的上限；正常几十毫秒就回来。 */
    private const val STATE_TIMEOUT_MS = 2_500L

    /** 刚开/关过就沿用缓存，避免切换动作连点两次都去问系统。 */
    private const val STATE_CACHE_TTL_MS = 15_000L

    private val lock = Any()

    @Volatile
    private var cachedState = UNKNOWN

    @Volatile
    private var cachedAtMs = 0L

    /**
     * 调用方包名：`TetheringService` 会校验「包名必须属于当前 uid」，不匹配就整条请求丢弃。
     * 身份统一由 [PrivilegedCallerIdentity] 按当前进程 uid 计算（root → `root`，
     * shell → `com.android.shell`），不能传应用自己的 applicationId。
     */
    private val callerPackage: String get() = PrivilegedCallerIdentity.require()

    /**
     * @return 1 = 已开启，0 = 已关闭，[UNKNOWN] = 读不到（无服务 / 无权限 / 超时）。
     */
    fun getState(): Int {
        val now = SystemClock.elapsedRealtime()
        val cached = cachedState
        if (cached != UNKNOWN && now - cachedAtMs < STATE_CACHE_TTL_MS) return cached

        val connector = connector()
        if (connector == null) {
            Log.i(TAG, "state: tethering service unavailable")
            return UNKNOWN
        }
        val receiver = StateReceiver()
        return try {
            connector.registerTetheringEventCallback(receiver, callerPackage)
            val state = receiver.await(STATE_TIMEOUT_MS)
            cache(state)
            Log.i(TAG, "state=$state")
            state
        } catch (e: Throwable) {
            Log.w(TAG, "read tethering state failed", e)
            UNKNOWN
        } finally {
            runCatching { connector.unregisterTetheringEventCallback(receiver, callerPackage) }
        }
    }

    /**
     * 打开 / 关闭 Wi-Fi 热点。
     *
     * 调用是 `oneway` 的：返回 true 只代表请求已发给系统，运营商校验 / 无上游网络等失败
     * 不会在这里体现；失败后下一次 [getState] 会读回真实状态。
     */
    fun setEnabled(enable: Boolean): Boolean {
        val connector = connector() ?: return false
        return try {
            if (enable) {
                // 字段用反射写：`connectivityScope` 这类字段是后续版本才加的，
                // 直接 putfield 在老系统上会 NoSuchFieldError；少一个字段也应能开热点。
                val request = TetheringRequestParcel()
                setField(request, "tetheringType", TETHERING_WIFI)
                setField(request, "localIPv4Address", null)
                setField(request, "staticClientAddress", null)
                setField(request, "exemptFromEntitlementCheck", false)
                setField(request, "showProvisioningUi", true)
                setField(request, "connectivityScope", CONNECTIVITY_SCOPE_GLOBAL)

                connector.startTethering(request, callerPackage, null, null)
                cache(TETHERED)
                Log.i(TAG, "setTethering(true)")
            } else {
                connector.stopTethering(TETHERING_WIFI, callerPackage, null, null)
                cache(NOT_TETHERED)
                Log.i(TAG, "setTethering(false)")
            }
            true
        } catch (e: Throwable) {
            Log.w(TAG, "set tethering=$enable failed", e)
            false
        }
    }

    private fun setField(target: Any, name: String, value: Any?) {
        runCatching {
            target.javaClass.getField(name).set(target, value)
        }.onFailure {
            Log.w(TAG, "set TetheringRequestParcel.$name failed (ignored)", it)
        }
    }

    private fun cache(state: Int) {
        synchronized(lock) {
            cachedState = state
            cachedAtMs = SystemClock.elapsedRealtime()
        }
    }

    private fun connector(): ITetheringConnector? = runCatching {
        val serviceManager = Class.forName("android.os.ServiceManager")
        val binder = serviceManager
            .getMethod("getService", String::class.java)
            .invoke(null, "tethering") as? IBinder
            ?: return null
        ITetheringConnector.Stub.asInterface(binder)
    }.onFailure { Log.w(TAG, "tethering service unavailable", it) }
        .getOrNull()

    /**
     * 只为了拿一次初始状态：注册 → 等 `onCallbackStarted` → 立刻反注册。
     *
     * 回调里只认 `tetheredList` 是否为空（有任一共享接口就算开着），不解析接口细节。
     */
    private class StateReceiver : ITetheringEventCallback.Stub() {

        private val latch = CountDownLatch(1)

        @Volatile
        private var state = UNKNOWN

        override fun onCallbackStarted(parcel: TetheringCallbackStartedParcel?) {
            state = if (parcel?.states?.tetheredList?.isNotEmpty() == true) {
                TETHERED
            } else {
                NOT_TETHERED
            }
            latch.countDown()
        }

        override fun onTetherStatesChanged(states: TetherStatesParcel?) {
            // 初始回调没到就先用这次变化兜底；已经到了就不再改，避免和查询语义混在一起。
            if (latch.count > 0) {
                state = if (states?.tetheredList?.isNotEmpty() == true) TETHERED else NOT_TETHERED
                latch.countDown()
            }
        }

        override fun onCallbackStopped(errorCode: Int) {
            latch.countDown()
        }

        override fun onUpstreamChanged(network: Network?) = Unit

        override fun onConfigurationChanged(config: TetheringConfigurationParcel?) = Unit

        override fun onTetherClientsChanged(clients: List<TetheredClient?>?) = Unit

        override fun onOffloadStatusChanged(status: Int) = Unit

        override fun onSupportedTetheringTypes(supportedBitmap: Long) = Unit

        fun await(timeoutMs: Long): Int {
            runCatching { latch.await(timeoutMs, TimeUnit.MILLISECONDS) }
            return state
        }
    }
}
