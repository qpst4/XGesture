package com.slideindex.app.xposed.bridge

/**
 * app 进程与 system_server 内 LSPosed 模块之间的桥接契约。
 *
 * 机制对齐 EdgeX：配置以 JSON 快照文件 + 变更广播下发，模块侧带 TTL 缓存读取，
 * 读不到时反向请求快照；模块侧同时把最近一次配置持久化到 `/data/system` 供重启后恢复。
 */
object ModuleHookBridgeContract {
  const val MODULE_PACKAGE = "com.slideindex.app"

  const val ACTION_CONFIG_CHANGED = "com.slideindex.app.xposed.action.CONFIG_CHANGED"
  const val ACTION_CONFIG_SNAPSHOT_REQUEST = "com.slideindex.app.xposed.action.CONFIG_SNAPSHOT_REQUEST"
  const val ACTION_MODULE_STATUS_REQUEST = "com.slideindex.app.xposed.action.MODULE_STATUS_REQUEST"
  const val ACTION_MODULE_STATUS_RESPONSE = "com.slideindex.app.xposed.action.MODULE_STATUS_RESPONSE"
  /**
   * app 侧边缘 overlay 宿主就绪状态变化（app → system_server 模块）。
   *
   * 模块据此立刻重绑事件桥，或主动放弃正在进行的接管会话；不依赖 2.5s 冷却轮询。
   */
  const val ACTION_HOST_STATE_CHANGED = "com.slideindex.app.xposed.action.HOST_STATE_CHANGED"

  /**
   * App → 电话 / 短信存储进程：请求进程自我重启，让覆盖安装后的新模块代码立即生效。
   *
   * [EXTRA_RESTART_CONFIRM] 只为防误触发（本广播不是安全边界：配置快照全局可读，
   * 被滥用的后果仅是电话进程重启，不涉及数据）。
   */
  const val ACTION_RESTART_PROCESS = "com.slideindex.app.xposed.action.RESTART_PROCESS"
  const val EXTRA_RESTART_CONFIRM = "restart_confirm"
  const val RESTART_CONFIRM_VALUE = "slideindex-otp-restart"

  const val EXTRA_CONFIG_JSON = "config_json"
  const val EXTRA_STATUS_ACTIVE = "status_active"
  const val EXTRA_STATUS_STATE = "status_state"
  const val EXTRA_STATUS_DETAIL = "status_detail"
  const val EXTRA_HOST_READY = "host_ready"

  /**
   * 模块状态三态（随 [EXTRA_STATUS_STATE] 回传，[EXTRA_STATUS_ACTIVE] 仅表示是否 [STATUS_STATE_READY]）。
   *
   * - [STATUS_STATE_READY]：接管真的会生效（hook 装齐 + 输入过滤器可用 + 控制器已连上 app 事件桥 + app 宿主就绪）。
   * - [STATUS_STATE_ARMED]：模块本身装好了，但接管此刻不会生效（事件桥未连、app 宿主未就绪、或开关全关）。
   * - [STATUS_STATE_NOT_READY]：模块没装齐（hook / 状态通道缺失）。
   */
  const val STATUS_STATE_READY = "ready"
  const val STATUS_STATE_ARMED = "armed"
  const val STATUS_STATE_NOT_READY = "not-ready"

  /** 模块侧绑定的 app 服务；服务声明为 exported 并在入口校验调用方 uid。 */
  const val BRIDGE_SERVICE_ACTION = "com.slideindex.app.xposed.bridge.ModuleGestureBridgeService"

  const val SNAPSHOT_FILE_NAME = "hook_config.json"

  /** 模块侧持久化目录（system_server 自身可读写）。 */
  const val SYSTEM_SNAPSHOT_DIR = "/data/system/slideindex"

  /** 系统侧读取 app 快照的兜底路径（`/data/user_de/0/<pkg>/files/`）；多数机型上电话进程读不到。 */
  const val APP_SNAPSHOT_PATH = "/data/user_de/0/$MODULE_PACKAGE/files/$SNAPSHOT_FILE_NAME"

  /**
   * app 对外导出的快照（外部存储私有目录 `/storage/emulated/0/Android/data/<pkg>/files/`）。
   *
   * app 写自己的外部文件目录不需要任何权限；这份文件属于 media_rw_data_file，
   * 电话进程 / system_server 侧可以读取，是模块冷启动时恢复策略的主要来源。
   *
   * 之前模块侧往 [SYSTEM_SNAPSHOT_DIR] 写盘（持久化）在电话进程里恒失败：
   * `hook_config.json.tmp: open failed: EACCES`，所以持久化改成由 app 负责导出。
   */
  const val APP_EXTERNAL_SNAPSHOT_PATH =
    "/storage/emulated/0/Android/data/$MODULE_PACKAGE/files/$SNAPSHOT_FILE_NAME"

  /**
   * 3 起新增 `otp` 段（短信黑名单 / 屏蔽 / 标记已读 / 提取后删除策略）。
   *
   * 2 起新增 `extra_rects`（悬浮球线条 / 边角轮盘）。旧模块忽略未知键，兼容。
   */
  const val SNAPSHOT_VERSION = 3

  /**
   * 模块代码版本：`xposed/` 目录下的模块代码有实质改动时 +1。
   *
   * app 侧把自己的这个常量与模块回传的 `code=` 比对：不一致说明 system_server 里跑的还是
   * 覆盖安装前的旧模块代码，需要完成热重载；框架不支持热重载时才需要重启。
   * 只是普通构建、模块代码没改时这个值不变，所以不会每次安装都误报。
   */
  const val MODULE_CODE_VERSION = 7

  /** 模块所属 APK 的安装路径：区分同代码版本的不同安装代，支持热重载后免重启。 */
  const val STATUS_DETAIL_APK_PREFIX = "apk="

  /** 状态串里模块代码版本字段：`code=<int>`。 */
  const val STATUS_DETAIL_CODE_PREFIX = "code="

  /** 状态串里剪贴板白名单 hook 字段：`clip=<ClipboardWhitelistHook.installStatus>`。 */
  const val STATUS_DETAIL_CLIPBOARD_PREFIX = "clip="

  /**
   * 状态串里**模块当前生效**的白名单大小：`wl=<int>`。
   *
   * app 侧拿它和本地设置比对：hook 装上了（`clip=ok`）但大小对不上，就说明配置没下发到
   * system_server（实测 Flyme/Android 16 上磁盘快照读不到、只能靠广播），设置页会直接提示
   * 「白名单未同步」，不用再去翻 LSPosed 日志。
   */
  const val STATUS_DETAIL_WHITELIST_PREFIX = "wl="

  /**
   * 状态回执所属通道：`system`（系统框架）/ `phone`（电话进程）。
   *
   * 电话进程与系统框架各注册一个状态通道，App 侧按通道分槽缓存；
   * 旧模块不带该字段，一律按 [CHANNEL_SYSTEM] 处理。
   */
  const val EXTRA_STATUS_CHANNEL = "status_channel"
  const val CHANNEL_SYSTEM = "system"
  const val CHANNEL_PHONE = "phone"
  const val CHANNEL_TELEPHONY = "telephony"

  /** 接管分组位掩码：顶部。 */
  const val GROUP_TOP = 1 shl 0

  /** 接管分组位掩码：左右两侧。 */
  const val GROUP_SIDES = 1 shl 1

  /** 接管分组位掩码：底部。 */
  const val GROUP_BOTTOM = 1 shl 2

  const val SIDE_LEFT = 0
  const val SIDE_RIGHT = 1
  const val SIDE_BOTTOM = 2
  const val SIDE_TOP = 3

  /**
   * 触钮之外的接管目标（16 起，和 [SIDE_LEFT] 等边号不重叠）。
   *
   * 这些目标由 app 侧下发屏幕比例矩形，模块只负责命中判断与转发，
   * 真正的命中复核由 app 侧 `canAcceptTouchAt` 完成。
   */
  const val TARGET_FLOAT_BALL = 16
  const val TARGET_FLOAT_LINE = 17
  const val TARGET_CORNER_LEFT = 18
  const val TARGET_CORNER_RIGHT = 19

  /** 边角轮盘两条触发带的编号，用于区分同一角落的竖条与横条。 */
  const val CORNER_STRIP_VERTICAL = 0
  const val CORNER_STRIP_HORIZONTAL = 1

  /** 全面屏手势导航（`Settings.Secure.navigation_mode == 2`）。 */
  const val NAVIGATION_MODE_GESTURAL = 2

  /** 模块侧配置缓存 TTL。 */
  const val SNAPSHOT_TTL_MS = 2_000L

  /** 模块侧反向请求快照的最小间隔。被旧配置顶掉/广播丢失后靠它自愈，别设太长。 */
  const val SNAPSHOT_REQUEST_THROTTLE_MS = 5_000L
}
