/*
 * 移植自 GestureEVO（https://github.com/LumiaGG/GestureEVO，GPL-3.0-only）
 *   源文件：drop/TouchStreamProcess.kt
 * 已按本项目的包结构、设置体系与流转栏集成层改写（部分逻辑为本项目新增）；
 * 版权与许可声明见 THIRD_PARTY_NOTICES.md，文件级映射见 docs/ATTRIBUTIONS.md。
 */

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
