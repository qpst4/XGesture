package com.slideindex.app.dragpress

/**
 * 远程 getevent 触摸流进程脚本与参数构建。
 */
internal object TouchStreamProcess {
    const val HEARTBEAT_MS = 10_000L

    fun arguments(devicePath: String): List<String> {
        require(devicePath.matches(Regex("/dev/input/event\\d+"))) { "Invalid device path: $devicePath" }
        return listOf("/system/bin/sh", "-c", leaseScript, "slide-touch-stream", devicePath)
    }

    internal val leaseScript = """
        child=
        cleanup() {
            trap - EXIT HUP INT TERM
            if [ -n "${'$'}child" ]; then
                kill -KILL "${'$'}child" 2>/dev/null
                wait "${'$'}child" 2>/dev/null
            fi
        }
        trap cleanup EXIT
        trap 'exit 0' HUP INT TERM
        /system/bin/getevent -t "${'$'}1" </dev/null &
        child=${'$'}!
        while IFS= read -r -t 60 lease; do
            kill -0 "${'$'}child" 2>/dev/null || exit 1
        done
        exit 0
    """.trimIndent()
}
