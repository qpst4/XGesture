package com.slideindex.app.shortcut.port

import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import com.slideindex.app.BuildConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import rikka.shizuku.Shizuku

/**
 * Shizuku user-service shell used by the shortcut diagnostics page.
 */
class ShizukuShell {

    private var serviceReady = CompletableDeferred<IUserService?>()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            if (binder == null || !binder.pingBinder()) {
                serviceReady.complete(null)
                return
            }
            val service = IUserService.Stub.asInterface(binder)
            mUserService = service
            serviceReady.complete(service)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            mUserService = null
            serviceReady = CompletableDeferred()
        }
    }

    suspend fun ensureUserService(timeoutMillis: Long = 5_000L): IUserService? {
        mUserService?.let { return it }
        if (!serviceReady.isCompleted) {
            val args = Shizuku.UserServiceArgs(
                ComponentName(BuildConfig.APPLICATION_ID, UserService::class.java.name),
            )
                .daemon(false)
                .processNameSuffix("service")
                .debuggable(false)
                .version(121)
            Shizuku.bindUserService(args, serviceConnection)
        }
        return try {
            withTimeout(timeoutMillis) { serviceReady.await() }
        } catch (error: Exception) {
            Log.e("ShizukuShell", "Service binding timeout", error)
            null
        }
    }

    suspend fun runAndGetOutput(command: String): String {
        val service = ensureUserService() ?: return ""
        return try {
            service.runShellCommand(command).orEmpty()
        } catch (error: Exception) {
            Log.e("ShizukuShell", "Command execution failed", error)
            ""
        }
    }

    suspend fun runCommand(command: String) {
        val service = ensureUserService() ?: return
        try {
            service.runShellCommand(command)
        } catch (error: Exception) {
            Log.e("ShizukuShell", "Command execution failed", error)
        }
    }

    fun isSupported(): Boolean =
        Shizuku.pingBinder() &&
            Shizuku.getVersion() >= 11 &&
            !Shizuku.isPreV11()

    fun isPermissionDenied(): Boolean =
        Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED

    fun requestPermission() {
        Shizuku.requestPermission(0)
    }

    companion object {
        @Volatile
        private var mUserService: IUserService? = null
    }
}
