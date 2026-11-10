package com.slideindex.app.settings

/**
 * 取词面板底栏「纯图标模式」下可用的按钮。
 *
 * [id] 会落盘（顺序 + 隐藏集合），**改名等于丢用户配置**，只许新增。
 * 这里只放 id 与顺序归一化；图标、文案、动作由各自的 UI 层按 id 映射
 * （overlay-panels 负责底栏渲染，app 负责设置页）。
 *
 * 没有「更多」：原来收在三点菜单里的钉住 / 暂存 / 二维码 / 用其他应用处理
 * 现在都是底栏按钮本身。
 */
enum class PickResultTextBarButton(val id: String) {
    /** 复制当前选中（经典样式里的「复制选中」主按钮）。 */
    COPY("copy"),

    /** 打开链接（只在正文里识别到链接时出现）。 */
    OPEN_LINK("open_link"),

    /** 搜索：单击展开/收起搜索网格，长按滑选窗口形态直搜。 */
    SEARCH("search"),

    /** 翻译当前选中。 */
    TRANSLATE("translate"),

    /** 分享当前选中（长按 = 起系统拖拽）。 */
    SHARE("share"),

    /** 钉在屏幕。 */
    PIN("pin"),

    /** 暂存（保存到想法）。 */
    STASH("stash"),

    /** 生成二维码（只在有文字时出现）。 */
    QR("qr"),

    /** 用其他应用处理（列出系统文本菜单里的 PROCESS_TEXT 入口）。 */
    PROCESS_TEXT("process_text"),
    ;

    companion object {
        fun fromId(id: String): PickResultTextBarButton? = entries.firstOrNull { it.id == id }
    }
}

/** 取词面板图片区底栏「纯图标模式」下可用的按钮。规则同 [PickResultTextBarButton]。 */
enum class PickResultImageBarButton(val id: String) {
    /** 以图搜图：单击把当前图片送进搜索面板的图片模式；长按滑选分享引擎。 */
    IMAGE_SEARCH("image_search"),

    /** 保存当前图片。 */
    SAVE("save"),

    /** 分享当前图片（长按 = 起系统拖拽）。 */
    SHARE("share"),

    /** 钉在屏幕。 */
    PIN("pin"),

    /** 暂存（保存到想法）。 */
    STASH("stash"),
    ;

    companion object {
        fun fromId(id: String): PickResultImageBarButton? = entries.firstOrNull { it.id == id }
    }
}

/**
 * 底栏按钮顺序 / 隐藏集合的纯逻辑：默认顺序、旧配置归一化、落盘编解码。
 *
 * 归一化语义与 `LauncherShortcutMenu.normalizedOrder` 一致：
 * 丢弃未知 id、去掉重复、新版本新增的按钮补到末尾；顺序即底栏从左到右的顺序。
 */
object PickResultBarButtons {

    /** 默认顺序 = 视觉顺序（复制在最左；钉住/暂存/二维码/其他应用处理排在分享之后）。 */
    val defaultTextOrder: List<String> = listOf(
        PickResultTextBarButton.COPY.id,
        PickResultTextBarButton.OPEN_LINK.id,
        PickResultTextBarButton.SEARCH.id,
        PickResultTextBarButton.TRANSLATE.id,
        PickResultTextBarButton.SHARE.id,
        PickResultTextBarButton.PIN.id,
        PickResultTextBarButton.STASH.id,
        PickResultTextBarButton.QR.id,
        PickResultTextBarButton.PROCESS_TEXT.id,
    )

    val defaultImageOrder: List<String> = listOf(
        PickResultImageBarButton.IMAGE_SEARCH.id,
        PickResultImageBarButton.SAVE.id,
        PickResultImageBarButton.SHARE.id,
        PickResultImageBarButton.PIN.id,
        PickResultImageBarButton.STASH.id,
    )

    fun normalizeText(saved: List<String>): List<PickResultTextBarButton> =
        normalized(saved, PickResultTextBarButton.entries.map { it.id })
            .mapNotNull(PickResultTextBarButton::fromId)

    fun normalizeImage(saved: List<String>): List<PickResultImageBarButton> =
        normalized(saved, PickResultImageBarButton.entries.map { it.id })
            .mapNotNull(PickResultImageBarButton::fromId)

    /** 过一遍已知 id：用户配置里残留的旧 id 不该让隐藏判断变成"永远隐藏/永远显示"。 */
    fun sanitizeHiddenText(saved: Set<String>): Set<String> =
        saved.filterTo(mutableSetOf()) { PickResultTextBarButton.fromId(it) != null }

    fun sanitizeHiddenImage(saved: Set<String>): Set<String> =
        saved.filterTo(mutableSetOf()) { PickResultImageBarButton.fromId(it) != null }

    private fun normalized(saved: List<String>, known: List<String>): List<String> {
        val kept = saved.filter { it in known }.distinct()
        return kept + known.filterNot { it in kept }
    }

    /** 顺序落盘：条目 id 只含 `[a-z_]`，用单元分隔符拼接（stringSet 无序，不能直接用）。 */
    fun encodeOrder(ids: List<String>): String = ids.joinToString(ORDER_SEP)

    fun decodeOrder(raw: String?): List<String> =
        raw?.split(ORDER_SEP)
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()

    private const val ORDER_SEP = "\u001F"
}
