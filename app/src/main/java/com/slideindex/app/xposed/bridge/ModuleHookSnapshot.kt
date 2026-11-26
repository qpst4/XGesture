package com.slideindex.app.xposed.bridge

import org.json.JSONArray
import org.json.JSONObject

/**
 * 下发到 system_server 模块的配置快照。
 *
 * 只携带模块计算接管区域所需的最小信息：分组开关、导航模式、密度与四边触钮几何。
 * 具体矩形由模块按当前窗口尺寸实时换算，因此旋转/折叠自动跟随。
 */
data class ModuleHookSnapshot(
  val version: Int = ModuleHookBridgeContract.SNAPSHOT_VERSION,
  val takeoverGroups: Int = 0,
  val interceptSystemBackGesture: Boolean = false,
  val navigationMode: Int = 0,
  val density: Float = 1f,
  val sides: List<ModuleHookSide> = emptyList(),
  /**
   * 触钮之外的接管矩形（悬浮球线条、边角轮盘），坐标为屏幕比例。
   *
   * 归属哪个开关由 [ModuleHookExtraRect.groups] 单一位决定；列表靠前的矩形优先命中。
   */
  val extraRects: List<ModuleHookExtraRect> = emptyList(),
  /** 剪贴板白名单（LSPosed 模式）：这些包会被当作默认输入法放行，可在后台读剪贴板。 */
  val clipboardWhitelist: List<String> = emptyList(),
  /** 验证码短信策略（电话进程消费）。 */
  val otp: ModuleHookOtpPolicy = ModuleHookOtpPolicy(),
  val updatedAtMs: Long = 0L,
) {
  fun hasGroup(group: Int): Boolean = (takeoverGroups and group) != 0

  fun hasReservedAreas(): Boolean = sides.any { it.reservedHandles.isNotEmpty() }

  fun side(sideId: Int): ModuleHookSide? = sides.firstOrNull { it.sideId == sideId }

  fun toJson(): String = JSONObject().apply {
    put(KEY_VERSION, version)
    put(KEY_GROUPS, takeoverGroups)
    put(KEY_INTERCEPT_BACK, interceptSystemBackGesture)
    put(KEY_NAV_MODE, navigationMode)
    put(KEY_DENSITY, density.toDouble())
    put(KEY_UPDATED_AT, updatedAtMs)
    put(
      KEY_SIDES,
      JSONArray().apply {
        sides.forEach { put(it.toJson()) }
      },
    )
    if (extraRects.isNotEmpty()) {
      put(
        KEY_EXTRA_RECTS,
        JSONArray().apply {
          extraRects.forEach { put(it.toJson()) }
        },
      )
    }
    if (clipboardWhitelist.isNotEmpty()) {
      put(
        KEY_CLIPBOARD_WHITELIST,
        JSONArray().apply {
          clipboardWhitelist.forEach { put(it) }
        },
      )
    }
    put(KEY_OTP, otp.toJson())
  }.toString()

  companion object {
    private const val KEY_VERSION = "version"
    private const val KEY_GROUPS = "groups"
    private const val KEY_INTERCEPT_BACK = "intercept_back"
    private const val KEY_NAV_MODE = "nav_mode"
    private const val KEY_DENSITY = "density"
    private const val KEY_SIDES = "sides"
    private const val KEY_EXTRA_RECTS = "extra_rects"
    private const val KEY_CLIPBOARD_WHITELIST = "clipboard_whitelist"
    private const val KEY_OTP = "otp"
    private const val KEY_UPDATED_AT = "updated_at"

    /** 解析失败的快照一律视为“未配置”，模块据此保持完全放行。 */
    fun parse(raw: String?): ModuleHookSnapshot? {
      if (raw.isNullOrBlank()) return null
      return runCatching {
        val json = JSONObject(raw)
        val sidesJson = json.optJSONArray(KEY_SIDES) ?: JSONArray()
        val sides = buildList {
          for (index in 0 until sidesJson.length()) {
            val item = sidesJson.optJSONObject(index) ?: continue
            add(ModuleHookSide.fromJson(item))
          }
        }
        val extraRectsJson = json.optJSONArray(KEY_EXTRA_RECTS) ?: JSONArray()
        val extraRects = buildList {
          for (index in 0 until extraRectsJson.length()) {
            val item = extraRectsJson.optJSONObject(index) ?: continue
            add(ModuleHookExtraRect.fromJson(item))
          }
        }
        val clipboardJson = json.optJSONArray(KEY_CLIPBOARD_WHITELIST) ?: JSONArray()
        val clipboardWhitelist = buildList {
          for (index in 0 until clipboardJson.length()) {
            val packageName = clipboardJson.optString(index).orEmpty()
            if (packageName.isNotBlank()) add(packageName)
          }
        }
        ModuleHookSnapshot(
          version = json.optInt(KEY_VERSION, ModuleHookBridgeContract.SNAPSHOT_VERSION),
          takeoverGroups = json.optInt(KEY_GROUPS, 0),
          interceptSystemBackGesture = json.optBoolean(KEY_INTERCEPT_BACK, false),
          navigationMode = json.optInt(KEY_NAV_MODE, 0),
          density = json.optDouble(KEY_DENSITY, 1.0).toFloat().coerceIn(0.5f, 6f),
          sides = sides,
          extraRects = extraRects,
          clipboardWhitelist = clipboardWhitelist,
          otp = ModuleHookOtpPolicy.fromJson(json.optJSONObject(KEY_OTP)),
          updatedAtMs = json.optLong(KEY_UPDATED_AT, 0L),
        )
      }.getOrNull()
    }
  }
}

/**
 * 触钮之外的接管矩形：屏幕比例坐标 + 所属开关位 + 目标号。
 *
 * [strip] 仅角轮盘使用（[ModuleHookBridgeContract.CORNER_STRIP_VERTICAL] /
 * [ModuleHookBridgeContract.CORNER_STRIP_HORIZONTAL]），其余目标固定 0。
 */
data class ModuleHookExtraRect(
  val target: Int,
  val groups: Int,
  val leftFraction: Float,
  val topFraction: Float,
  val rightFraction: Float,
  val bottomFraction: Float,
  val strip: Int = 0,
) {
  fun toJson(): JSONObject = JSONObject().apply {
    put(KEY_TARGET, target)
    put(KEY_GROUPS, groups)
    put(KEY_LEFT, leftFraction.toDouble())
    put(KEY_TOP, topFraction.toDouble())
    put(KEY_RIGHT, rightFraction.toDouble())
    put(KEY_BOTTOM, bottomFraction.toDouble())
    if (strip != 0) put(KEY_STRIP, strip)
  }

  companion object {
    private const val KEY_TARGET = "target"
    private const val KEY_GROUPS = "groups"
    private const val KEY_LEFT = "left"
    private const val KEY_TOP = "top"
    private const val KEY_RIGHT = "right"
    private const val KEY_BOTTOM = "bottom"
    private const val KEY_STRIP = "strip"

    fun fromJson(json: JSONObject): ModuleHookExtraRect = ModuleHookExtraRect(
      target = json.optInt(KEY_TARGET, -1),
      groups = json.optInt(KEY_GROUPS, 0),
      leftFraction = json.optDouble(KEY_LEFT, 0.0).toFloat().coerceIn(0f, 1f),
      topFraction = json.optDouble(KEY_TOP, 0.0).toFloat().coerceIn(0f, 1f),
      rightFraction = json.optDouble(KEY_RIGHT, 0.0).toFloat().coerceIn(0f, 1f),
      bottomFraction = json.optDouble(KEY_BOTTOM, 0.0).toFloat().coerceIn(0f, 1f),
      strip = json.optInt(KEY_STRIP, 0),
    )
  }
}

/** 单边触钮几何；`widthDp` 为 LEFT/RIGHT 的接管宽度（已含「拦截系统返回手势」加宽）。 */
data class ModuleHookSide(
  val sideId: Int,
  val widthDp: Float,
  val handles: List<ModuleHookHandle> = emptyList(),
  val reservedHandles: List<ModuleHookHandle> = emptyList(),
) {
  fun toJson(): JSONObject = JSONObject().apply {
    put(KEY_SIDE, sideId)
    put(KEY_WIDTH_DP, widthDp.toDouble())
    put(
      KEY_HANDLES,
      JSONArray().apply {
        handles.forEach { put(it.toJson()) }
      },
    )
    if (reservedHandles.isNotEmpty()) {
      put(KEY_RESERVED_HANDLES, JSONArray().apply { reservedHandles.forEach { put(it.toJson()) } })
    }
  }

  companion object {
    private const val KEY_SIDE = "side"
    private const val KEY_WIDTH_DP = "width_dp"
    private const val KEY_HANDLES = "handles"
    private const val KEY_RESERVED_HANDLES = "reserved_handles"

    fun fromJson(json: JSONObject): ModuleHookSide {
      fun readHandles(key: String): List<ModuleHookHandle> = buildList {
        val values = json.optJSONArray(key) ?: return@buildList
        for (index in 0 until values.length()) {
          val item = values.optJSONObject(index) ?: continue
          add(ModuleHookHandle.fromJson(item))
        }
      }
      return ModuleHookSide(
        sideId = json.optInt(KEY_SIDE, ModuleHookBridgeContract.SIDE_LEFT),
        widthDp = json.optDouble(KEY_WIDTH_DP, 0.0).toFloat(),
        handles = readHandles(KEY_HANDLES),
        reservedHandles = readHandles(KEY_RESERVED_HANDLES),
      )
    }
  }
}

/** 单个触钮在所属边上的比例区间与厚度（dp）。 */
data class ModuleHookHandle(
  val topFraction: Float,
  val heightFraction: Float,
  val widthDp: Float,
) {
  val bottomFraction: Float get() = topFraction + heightFraction

  fun toJson(): JSONObject = JSONObject().apply {
    put(KEY_TOP, topFraction.toDouble())
    put(KEY_HEIGHT, heightFraction.toDouble())
    put(KEY_WIDTH_DP, widthDp.toDouble())
  }

  companion object {
    private const val KEY_TOP = "top"
    private const val KEY_HEIGHT = "height"
    private const val KEY_WIDTH_DP = "width_dp"

    fun fromJson(json: JSONObject): ModuleHookHandle = ModuleHookHandle(
      topFraction = json.optDouble(KEY_TOP, 0.0).toFloat().coerceIn(0f, 1f),
      heightFraction = json.optDouble(KEY_HEIGHT, 0.0).toFloat().coerceIn(0f, 1f),
      widthDp = json.optDouble(KEY_WIDTH_DP, 0.0).toFloat().coerceIn(0f, 320f),
    )
  }
}
