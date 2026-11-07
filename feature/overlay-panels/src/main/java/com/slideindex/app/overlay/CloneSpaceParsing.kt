package com.slideindex.app.overlay

/**
 * 分身空间枚举里那些「解析 shell 输出」的纯函数。
 *
 * 放在独立文件里，不碰 Android API，方便 JVM 单测直接覆盖：
 * `pm list users` / `pm list packages --user N` 的格式一旦被 ROM 改掉，
 * 这里是最先崩的地方，也是最该有测试的地方。
 */
object CloneSpaceParsing {
    /** `UserInfo{999:FlymeParallelSpace:1010} running` 里的 user id。 */
    private val USER_INFO = Regex("""UserInfo\{(\d+):""")

    /** 当前用户之外的 user id（工作资料 / 平行空间 / 第二空间），按出现顺序去重。 */
    fun parseUserIds(output: String, currentUserId: Int): List<Int> =
        USER_INFO.findAll(output)
            .map { it.groupValues[1].toIntOrNull() }
            .filterNotNull()
            .filter { it != currentUserId }
            .distinct()
            .toList()

    /** `pm list packages --user N` 的 `package:com.foo` 行。 */
    fun parsePackageNames(output: String): Set<String> =
        output.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("package:") }
            .map { it.removePrefix("package:") }
            .filter { it.isNotBlank() }
            .toCollection(LinkedHashSet())

    /**
     * `dumpsys user` 里每个 user 的 type：
     *
     * ```
     * UserInfo{999:FlymeParallelSpace:1010} serialNo=10 ...
     *   Type: android.os.usertype.profile.CLONE
     * ```
     *
     * 返回 `userId -> type`。`UserInfo` 本身不是公开 SDK 类，这是不依赖系统 API 的唯一办法。
     */
    fun parseUserTypes(output: String): Map<Int, String> {
        val types = LinkedHashMap<Int, String>()
        var currentUserId: Int? = null
        output.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            val userMatch = USER_INFO.find(line)
            if (userMatch != null) {
                currentUserId = userMatch.groupValues[1].toIntOrNull()
                return@forEach
            }
            val pendingUserId = currentUserId
            if (line.startsWith("Type:") && pendingUserId != null) {
                val type = line.removePrefix("Type:").trim()
                if (type.isNotEmpty()) types[pendingUserId] = type
                currentUserId = null
            }
        }
        return types
    }
}
