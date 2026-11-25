/*
 * 移植自 GestureEVO（https://github.com/LumiaGG/GestureEVO，GPL-3.0-only）
 *   源文件：utils/process/ProcessOutputLines.kt
 * 已按本项目的包结构、设置体系与流转栏集成层改写（部分逻辑为本项目新增）；
 * 版权与许可声明见 THIRD_PARTY_NOTICES.md，文件级映射见 docs/ATTRIBUTIONS.md。
 */

package com.slideindex.app.dragpress

import java.io.InputStream

internal object ProcessOutputLines {
    /** 逐行读取输入流，防止超大单行导致内存溢出。 */
    fun read(
        input: InputStream,
        maxLength: Int = 8192,
        onDiscard: () -> Unit = {},
        onLine: (String) -> Boolean,
    ) {
        val reader = input.reader(Charsets.UTF_8)
        val buffer = CharArray(2048)
        val line = StringBuilder()
        var oversized = false
        while (true) {
            val count = reader.read(buffer)
            if (count < 0) break
            for (index in 0 until count) {
                val char = buffer[index]
                if (char == '\n') {
                    if (oversized) {
                        onDiscard()
                    } else if (!onLine(line.toString().trimEnd('\r'))) {
                        return
                    }
                    line.setLength(0)
                    oversized = false
                } else if (!oversized) {
                    if (line.length >= maxLength) {
                        oversized = true
                        line.setLength(0)
                    } else {
                        line.append(char)
                    }
                }
            }
        }
        if (oversized) {
            onDiscard()
        } else if (line.isNotEmpty()) {
            onLine(line.toString())
        }
    }
}
