package com.slideindex.app.privilege.root



import com.slideindex.app.shizuku.ITaskManagerService

import java.io.DataInputStream

import java.io.DataOutputStream

import java.io.IOException

import java.net.InetSocketAddress

import java.net.Socket



internal object TaskManagerDaemonRpc {

    private const val HOST = "127.0.0.1"

    private const val CONNECT_TIMEOUT_MS = 800



    const val T_VOID = 0

    const val T_BOOL = 1

    const val T_INT = 2

    const val T_STR = 3

    const val T_STR_ARR = 4

    const val T_BYTES = 5



    fun execute(port: Int, op: Int, args: List<String?>): RpcValue {

        Socket().use { socket ->

            socket.tcpNoDelay = true

            socket.connect(InetSocketAddress(HOST, port), CONNECT_TIMEOUT_MS)

            val output = DataOutputStream(socket.getOutputStream())

            val input = DataInputStream(socket.getInputStream())

            writeRequest(output, op, args)

            return readValue(input)

        }

    }



    fun handleRequest(service: ITaskManagerService, op: Int, args: List<String?>): RpcValue {

        return when (op) {

            16777114 -> {

                runCatching { service.destroy() }

                RpcValue.Void

            }

            1 -> RpcValue.Bool(service.removeTaskById(args.string(0)))

            2 -> RpcValue.Str(service.getFrontTaskId())

            3 -> RpcValue.StrArr(service.getTaskIdsForPackage(args.string(0)))

            4 -> RpcValue.StrArr(service.getRecentTaskPackages())

            5 -> RpcValue.Bool(

                service.moveTaskToFreeWindow(

                    args.string(0),

                    args.int(1),

                    args.int(2),

                    args.int(3),

                    args.int(4),

                    args.int(5),

                ),

            )

            6 -> RpcValue.I32(service.getApiVersion())

            7 -> RpcValue.Str(service.getFrontTaskPackage())

            8 -> RpcValue.Bool(service.forceStopPackage(args.string(0)))

            9 -> RpcValue.StrArr(service.getPublishedShortcuts(args.string(0)))

            10 -> RpcValue.Bool(service.startPublishedShortcut(args.string(0), args.string(1)))

            11 -> RpcValue.StrArr(service.getRecentTasks())

            12 -> RpcValue.Bool(service.switchToTask(args.string(0), args.string(1), args.string(2)))

            13 -> RpcValue.Bool(service.showVoiceAssistant())

            14 -> RpcValue.Bool(service.runShellCommand(args.toStringArray()))

            15 -> RpcValue.Str(service.runShellCommandOutput(args.toStringArray()))

            16 -> RpcValue.Str(

                service.runShellCommandLine(

                    args.string(0),

                    args.boolean(1),

                    args.boolean(2),

                ),

            )

            17 -> RpcValue.Bool(service.probeRootAvailable())

            18 -> RpcValue.StrArr(service.getAllPublishedShortcuts())

            19 -> RpcValue.Bytes(service.getShortcutIconBytes(args.string(0), args.string(1), args.int(2)))

            100 -> RpcValue.Bool(service.startMultiTouchPolling(args.boolean(0)))

            101 -> {
                service.stopMultiTouchPolling()
                RpcValue.Void
            }

            102 -> RpcValue.StrArr(service.pollMultiTouchEvents())

            else -> throw IOException("unknown op=$op")

        }

    }



    fun writeRequest(output: DataOutputStream, op: Int, args: List<String?>) {

        output.writeInt(op)

        output.writeInt(args.size)

        for (arg in args) {

            if (arg == null) {

                output.writeInt(-1)

            } else {

                val bytes = arg.toByteArray(Charsets.UTF_8)

                output.writeInt(bytes.size)

                output.write(bytes)

            }

        }

        output.flush()

    }



    fun readRequest(input: DataInputStream): Pair<Int, List<String?>> {

        val op = input.readInt()

        val count = input.readInt()

        if (count < 0 || count > 64) throw IOException("bad arg count=$count")

        val args = ArrayList<String?>(count)

        repeat(count) {

            val len = input.readInt()

            if (len < 0) {

                args.add(null)

            } else {

                val bytes = ByteArray(len)

                input.readFully(bytes)

                args.add(bytes.toString(Charsets.UTF_8))

            }

        }

        return op to args

    }



    fun writeValue(output: DataOutputStream, value: RpcValue) {

        when (value) {

            RpcValue.Void -> output.writeInt(T_VOID)

            is RpcValue.Bool -> {

                output.writeInt(T_BOOL)

                output.writeInt(if (value.value) 1 else 0)

            }

            is RpcValue.I32 -> {

                output.writeInt(T_INT)

                output.writeInt(value.value)

            }

            is RpcValue.Str -> {

                output.writeInt(T_STR)

                writeUtf8(output, value.value)

            }

            is RpcValue.StrArr -> {

                output.writeInt(T_STR_ARR)

                output.writeInt(value.value.size)

                for (item in value.value) {

                    writeUtf8(output, item)

                }

            }

            is RpcValue.Bytes -> {

                output.writeInt(T_BYTES)

                val bytes = value.value

                if (bytes == null) {

                    output.writeInt(-1)

                } else {

                    output.writeInt(bytes.size)

                    output.write(bytes)

                }

            }

        }

        output.flush()

    }



    fun readValue(input: DataInputStream): RpcValue {

        return when (input.readInt()) {

            T_VOID -> RpcValue.Void

            T_BOOL -> RpcValue.Bool(input.readInt() != 0)

            T_INT -> RpcValue.I32(input.readInt())

            T_STR -> RpcValue.Str(readUtf8(input))

            T_STR_ARR -> {

                val count = input.readInt()

                if (count < 0) throw IOException("bad array count=$count")

                val items = Array(count) { readUtf8(input) ?: "" }

                RpcValue.StrArr(items)

            }

            T_BYTES -> {

                val len = input.readInt()

                if (len < 0) RpcValue.Bytes(null)

                else {

                    val bytes = ByteArray(len)

                    input.readFully(bytes)

                    RpcValue.Bytes(bytes)

                }

            }

            else -> throw IOException("bad reply type")

        }

    }



    private fun writeUtf8(output: DataOutputStream, value: String?) {

        if (value == null) {

            output.writeInt(-1)

            return

        }

        val bytes = value.toByteArray(Charsets.UTF_8)

        output.writeInt(bytes.size)

        output.write(bytes)

    }



    private fun readUtf8(input: DataInputStream): String? {

        val len = input.readInt()

        if (len < 0) return null

        val bytes = ByteArray(len)

        input.readFully(bytes)

        return bytes.toString(Charsets.UTF_8)

    }



    private fun List<String?>.string(index: Int): String = this.getOrNull(index) ?: ""



    private fun List<String?>.int(index: Int): Int = this.getOrNull(index)?.toIntOrNull() ?: 0



    private fun List<String?>.boolean(index: Int): Boolean =

        this.getOrNull(index)?.equals("true", ignoreCase = true) == true



    private fun List<String?>.toStringArray(): Array<String> =

        map { it ?: "" }.toTypedArray()

}



internal sealed class RpcValue {

    data object Void : RpcValue()

    data class Bool(val value: Boolean) : RpcValue()

    data class I32(val value: Int) : RpcValue()

    data class Str(val value: String?) : RpcValue()

    data class StrArr(val value: Array<String>) : RpcValue()

    data class Bytes(val value: ByteArray?) : RpcValue()

}


