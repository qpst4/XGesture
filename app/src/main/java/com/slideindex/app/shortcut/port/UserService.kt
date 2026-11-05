package com.slideindex.app.shortcut.port

import android.os.Parcel
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Shizuku user service process.
 *
 * The Shizuku-specific destroy transaction id is 0xFFFF9B. AIDL's own id range
 * ends at 0xFFFF9A, so that one transaction is handled manually here while the
 * rest of the AIDL Stub stays generated.
 */
class UserService : IUserService.Stub() {

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        if (code == TRANSACTION_DESTROY) {
            data.enforceInterface(IUserService.DESCRIPTOR)
            destroy()
            reply?.writeNoException()
            return true
        }
        return super.onTransact(code, data, reply, flags)
    }

    private fun destroy() {
        System.exit(0)
        throw RuntimeException("System.exit returned normally, while it was supposed to halt JVM.")
    }

    override fun runShellCommand(command: String?): String {
        if (command.isNullOrBlank()) return ""
        val output = StringBuilder()
        try {
            val process = ProcessBuilder("sh", "-c", command)
                .redirectErrorStream(true)
                .start()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            try {
                while (true) {
                    val line = reader.readLine() ?: break
                    output.append(line).append('\n')
                }
            } finally {
                reader.close()
            }
            process.waitFor()
        } catch (error: Exception) {
            error.printStackTrace()
        }
        return output.toString()
    }

    companion object {
        private const val TRANSACTION_DESTROY = 0xFFFF9B
    }
}
