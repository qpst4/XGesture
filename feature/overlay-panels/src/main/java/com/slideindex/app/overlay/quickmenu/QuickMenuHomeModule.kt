package com.slideindex.app.overlay.quickmenu

/**
 * 快捷菜单首页的一个可排序模块。
 *
 * 最近使用、文件，以及每个工具分组各自是一张卡；这样工具不会堆成一整块，
 * 拖拽时也只移动一张小卡。顺序由用户长按卡片决定。
 */
sealed interface QuickMenuHomeModule {
    val id: String

    data object Recents : QuickMenuHomeModule {
        override val id: String = "RECENTS"
    }

    data object Files : QuickMenuHomeModule {
        override val id: String = "FILES"
    }

    data class ToolGroup(val group: QuickMenuGroup) : QuickMenuHomeModule {
        override val id: String = "GROUP_${group.name}"
    }

    companion object {
        val defaultOrder: List<QuickMenuHomeModule> =
            listOf(Recents, Files) + QuickMenuGroup.entries.map(::ToolGroup)

        /**
         * 只接受已知模块 id；缺失的模块按默认顺序补齐。
         * 旧版本只存了 "TOOLS"，这里展开成所有工具分组，避免升级后工具消失。
         */
        fun sanitize(names: List<String>): List<QuickMenuHomeModule> {
            val parsed = names.flatMap { name ->
                when (name) {
                    Recents.id -> listOf(Recents)
                    Files.id -> listOf(Files)
                    "TOOLS" -> QuickMenuGroup.entries.map(::ToolGroup)
                    else -> {
                        val group = QuickMenuGroup.entries.firstOrNull {
                            "GROUP_${it.name}" == name
                        }
                        group?.let { listOf(ToolGroup(it)) }.orEmpty()
                    }
                }
            }
            return (parsed + defaultOrder).distinctBy { it.id }
        }
    }
}
