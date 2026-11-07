package com.slideindex.app.overlay.filebrowser

/**
 * 文件浏览器可向 UI 暴露的失败原因。
 *
 * 解析层只产生这里的枚举，不产生任何用户文案；文案统一在 UI 层用字符串资源映射，
 * 这样 shell 输出的英文错误、直读异常与权限缺失都能落到同一套提示上。
 */
sealed interface FileBrowserError {
    /** 目录存在但没有读权限。 */
    data object PermissionDenied : FileBrowserError

    /** Shizuku（shell uid）无法访问，需要切换到 Root 模式。 */
    data object NeedsRoot : FileBrowserError

    /** 当前权限模式尚未授权（Root 未授权 / Shizuku 未授权）。 */
    data object PrivilegeUnavailable : FileBrowserError

    data object NotFound : FileBrowserError

    data object NotDirectory : FileBrowserError

    data object Timeout : FileBrowserError

    data class Unknown(val detail: String?) : FileBrowserError
}
