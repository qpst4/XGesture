package com.slideindex.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/** 首页设置用的自绘图标（24dp；多数为 1.75 描边，App保活为填充路径）。 */
internal object HomeCustomIcons {
  /**
   * App保活：盾牌 + 锁（设计稿 SVG，24 网格，填充路径）。
   *
   * 线宽约 1.5，比首页 1.75 的自绘略细；用白色填充，由 `Icon` 的 tint 着色。
   */
  val AppKeepAlive: ImageVector by lazy {
    ImageVector.Builder(
      name = "AppKeepAlive",
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f
    ).apply {
      addPath(
        pathData = PathParser().parsePathString(APP_KEEP_ALIVE_PATH).toNodes(),
        fill = SolidColor(Color.White),
      )
    }.build()
  }

  private const val APP_KEEP_ALIVE_PATH =
    "M12,21.48Q8.76,20.6 6.63,17.66T4.5,11.1V5.35L12,2.54l7.5,2.81V11.1q0,3.63-2.13,6.56T12," +
    "21.48Zm0-1.58q2.6-0.82 4.3-3.3T18,11.1V6.38L12,4.13L6,6.38V11.1q0,3.03 1.7,5.5T12," +
    "19.9Zm0-7.89Zm-1.98,3.82h3.96q0.38,0 0.64-0.26t0.26-0.64V11.96q0-0.38-0.26-0.64t-0.64-0.26H13.89v-1q0" +
    "-0.79-0.55-1.34T12.01,8.17T10.67,8.72t-0.55,1.34v1H10.02q-0.38,0-0.64,0.26T9.12,11.96v2.96q0,0.38 " +
    "0.26,0.64t0.64,0.26Zm0.99-4.77v-1q0-0.43 0.29-0.71t0.71-0.29t0.71,0.29t0.29,0.71v1h-2Z"

  /**
   * 边角轮盘：左下等长 L 边 + 以直边为半径的 1/4 圆弧上（不含两端点）三等分的三个描边圆槽位。
   */
  val CornerWheel: ImageVector by lazy {
    ImageVector.Builder(
      name = "CornerWheel",
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f
    ).apply {
      val stroke = SolidColor(Color.Black)
      val w = 1.75f
      val transparent = SolidColor(Color.Transparent)
      val cornerX = 3.75f
      val cornerY = 20.25f
      val leg = 14.5f
      val slotCircleR = 1.85f

      fun slotCenter(degreesFromEast: Float): Pair<Float, Float> {
        val rad = Math.toRadians(degreesFromEast.toDouble())
        val x = cornerX + leg * cos(rad).toFloat()
        val y = cornerY - leg * sin(rad).toFloat()
        return x to y
      }

      fun strokedCircle(cx: Float, cy: Float, r: Float) {
        path(
          fill = transparent,
          stroke = stroke,
          strokeLineWidth = w,
          strokeLineCap = StrokeCap.Round
        ) {
          moveTo(cx + r, cy)
          arcTo(r, r, 0f, false, true, cx - r, cy)
          arcTo(r, r, 0f, false, true, cx + r, cy)
        }
      }

      path(
        fill = transparent,
        stroke = stroke,
        strokeLineWidth = w,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
      ) {
        moveTo(cornerX, cornerY - leg)
        verticalLineTo(cornerY)
        horizontalLineTo(cornerX + leg)
      }

      // 90° 扇形四等分，取中间三段的分点（避开竖/横边端点 90° 与 0°）
      val slotAngles = floatArrayOf(67.5f, 45f, 22.5f)
      for (deg in slotAngles) {
        val (cx, cy) = slotCenter(deg)
        strokedCircle(cx, cy, slotCircleR)
      }
    }.build()
  }

  /** 动作选择器用描边录屏（相机机身 + 镜头 + 空心播放三角）。 */
  val ScreenRecordOutlined: ImageVector by lazy {
    ImageVector.Builder(
      name = "ScreenRecordOutlined",
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f
    ).apply {
      val stroke = SolidColor(Color.Black)
      val w = 1.5f
      val transparent = SolidColor(Color.Transparent)

      path(
        fill = transparent,
        stroke = stroke,
        strokeLineWidth = w,
        strokeLineJoin = StrokeJoin.Round
      ) {
        moveTo(7f, 8.25f)
        horizontalLineTo(12.5f)
        curveTo(13.6f, 8.25f, 14.5f, 9.15f, 14.5f, 10.25f)
        verticalLineTo(14.75f)
        curveTo(14.5f, 15.85f, 13.6f, 16.75f, 12.5f, 16.75f)
        horizontalLineTo(7f)
        curveTo(5.9f, 16.75f, 5f, 15.85f, 5f, 14.75f)
        verticalLineTo(10.25f)
        curveTo(5f, 9.15f, 5.9f, 8.25f, 7f, 8.25f)
        close()
      }
      path(
        fill = transparent,
        stroke = stroke,
        strokeLineWidth = w,
        strokeLineJoin = StrokeJoin.Round
      ) {
        moveTo(14.5f, 10.5f)
        lineTo(18.25f, 9.25f)
        lineTo(18.25f, 15.75f)
        lineTo(14.5f, 14.5f)
        close()
      }
      path(
        fill = transparent,
        stroke = stroke,
        strokeLineWidth = w,
        strokeLineJoin = StrokeJoin.Round,
        strokeLineCap = StrokeCap.Round
      ) {
        moveTo(8.15f, 10.4f)
        lineTo(10.85f, 12.5f)
        lineTo(8.15f, 14.6f)
        close()
      }
    }.build()
  }

  /** Material Symbols Outlined: screenshot_region（区域截图&取词） */
  val ScreenshotRegionOutlined: ImageVector by lazy {
    ImageVector.Builder(
      name = "ScreenshotRegionOutlined",
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f
    ).apply {
      val fill = SolidColor(Color.Black)
      path(fill = fill) {
        moveTo(5f, 9f)
        lineTo(5f, 5f)
        lineTo(9f, 5f)
        lineTo(9f, 7f)
        lineTo(7f, 7f)
        lineTo(7f, 9f)
        close()
      }
      path(fill = fill) {
        moveTo(15f, 5f)
        lineTo(19f, 5f)
        lineTo(19f, 9f)
        lineTo(17f, 9f)
        lineTo(17f, 7f)
        lineTo(15f, 7f)
        close()
      }
      path(fill = fill) {
        moveTo(5f, 15f)
        lineTo(5f, 19f)
        lineTo(9f, 19f)
        lineTo(9f, 17f)
        lineTo(7f, 17f)
        lineTo(7f, 15f)
        close()
      }
      path(fill = fill) {
        moveTo(16.5f, 15f)
        lineTo(17.5f, 15f)
        lineTo(17.5f, 19f)
        lineTo(16.5f, 19f)
        close()
        moveTo(15f, 16.5f)
        lineTo(19f, 16.5f)
        lineTo(19f, 17.5f)
        lineTo(15f, 17.5f)
        close()
      }
    }.build()
  }

  /** 描边电池 + 闪电（保活），无实心填充。 */
  val BatteryKeepAliveOutlined: ImageVector by lazy {
    ImageVector.Builder(
      name = "BatteryKeepAliveOutlined",
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f
    ).apply {
      val stroke = SolidColor(Color.Black)
      val w = 1.5f
      path(
        fill = SolidColor(Color.Transparent),
        stroke = stroke,
        strokeLineWidth = w,
        strokeLineJoin = StrokeJoin.Round
      ) {
        moveTo(6.5f, 7.5f)
        horizontalLineTo(15.5f)
        curveTo(16.33f, 7.5f, 17f, 8.17f, 17f, 9f)
        verticalLineTo(15f)
        curveTo(17f, 15.83f, 16.33f, 16.5f, 15.5f, 16.5f)
        horizontalLineTo(6.5f)
        curveTo(5.67f, 16.5f, 5f, 15.83f, 5f, 15f)
        verticalLineTo(9f)
        curveTo(5f, 8.17f, 5.67f, 7.5f, 6.5f, 7.5f)
        close()
      }
      path(
        fill = SolidColor(Color.Transparent),
        stroke = stroke,
        strokeLineWidth = w,
        strokeLineCap = StrokeCap.Round
      ) {
        moveTo(17.5f, 10.5f)
        horizontalLineTo(18.5f)
        verticalLineTo(13.5f)
        horizontalLineTo(17.5f)
      }
      path(
        fill = SolidColor(Color.Black),
        pathFillType = PathFillType.NonZero
      ) {
        moveTo(10.2f, 9.8f)
        lineTo(8.6f, 12.4f)
        horizontalLineTo(10.8f)
        lineTo(9.9f, 15.2f)
        lineTo(13.4f, 11.6f)
        horizontalLineTo(11.2f)
        lineTo(12.1f, 9.8f)
        close()
      }
    }.build()
  }

  /** Material Symbols Outlined: play_pause */
  val PlayPauseOutlined: ImageVector by lazy {
    ImageVector.Builder(
      name = "PlayPauseOutlined",
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f
    ).apply {
      val stroke = SolidColor(Color.Black)
      val w = 1.5f
      val transparent = SolidColor(Color.Transparent)
      path(
        fill = transparent,
        stroke = stroke,
        strokeLineWidth = w,
        strokeLineJoin = StrokeJoin.Round,
        strokeLineCap = StrokeCap.Round
      ) {
        moveTo(8f, 7.5f)
        verticalLineTo(16.5f)
        lineTo(15f, 12f)
        close()
      }
      path(
        fill = transparent,
        stroke = stroke,
        strokeLineWidth = w,
        strokeLineCap = StrokeCap.Round
      ) {
        moveTo(17f, 7.5f)
        verticalLineTo(16.5f)
        moveTo(20f, 7.5f)
        verticalLineTo(16.5f)
      }
    }.build()
  }

  /** 锁屏并静音响铃：锁 + 小铃铛斜杠 */
  val LockScreenSilenceRingOutlined: ImageVector by lazy {
    ImageVector.Builder(
      name = "LockScreenSilenceRingOutlined",
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f
    ).apply {
      val stroke = SolidColor(Color.Black)
      val w = 1.5f
      val transparent = SolidColor(Color.Transparent)
      path(
        fill = transparent,
        stroke = stroke,
        strokeLineWidth = w,
        strokeLineJoin = StrokeJoin.Round,
        strokeLineCap = StrokeCap.Round
      ) {
        moveTo(6.5f, 10.5f)
        horizontalLineTo(17.5f)
        curveTo(18.6f, 10.5f, 19.5f, 11.4f, 19.5f, 12.5f)
        verticalLineTo(19.5f)
        curveTo(19.5f, 20.6f, 18.6f, 21.5f, 17.5f, 21.5f)
        horizontalLineTo(6.5f)
        curveTo(5.4f, 21.5f, 4.5f, 20.6f, 4.5f, 19.5f)
        verticalLineTo(12.5f)
        curveTo(4.5f, 11.4f, 5.4f, 10.5f, 6.5f, 10.5f)
        close()
        moveTo(8f, 10.5f)
        verticalLineTo(6.5f)
        curveTo(8f, 4.3f, 9.8f, 2.5f, 12f, 2.5f)
        curveTo(14.2f, 2.5f, 16f, 4.3f, 16f, 6.5f)
        verticalLineTo(10.5f)
      }
      path(
        fill = transparent,
        stroke = stroke,
        strokeLineWidth = w,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
      ) {
        moveTo(15.5f, 14.5f)
        curveTo(15.5f, 13.4f, 16.4f, 12.5f, 17.5f, 12.5f)
        curveTo(18.6f, 12.5f, 19.5f, 13.4f, 19.5f, 14.5f)
        verticalLineTo(15.5f)
        horizontalLineTo(15.5f)
        verticalLineTo(14.5f)
        moveTo(16.75f, 17.75f)
        curveTo(16.75f, 18.44f, 17.19f, 19f, 17.75f, 19f)
        curveTo(18.31f, 19f, 18.75f, 18.44f, 18.75f, 17.75f)
        moveTo(14.25f, 12.25f)
        lineTo(21.25f, 19.25f)
      }
    }.build()
  }

  /** 锁屏并全部静音：锁 + 扬声器斜杠 */
  val LockScreenMuteAllOutlined: ImageVector by lazy {
    ImageVector.Builder(
      name = "LockScreenMuteAllOutlined",
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f
    ).apply {
      val stroke = SolidColor(Color.Black)
      val w = 1.5f
      val transparent = SolidColor(Color.Transparent)
      path(
        fill = transparent,
        stroke = stroke,
        strokeLineWidth = w,
        strokeLineJoin = StrokeJoin.Round,
        strokeLineCap = StrokeCap.Round
      ) {
        moveTo(6.5f, 10.5f)
        horizontalLineTo(17.5f)
        curveTo(18.6f, 10.5f, 19.5f, 11.4f, 19.5f, 12.5f)
        verticalLineTo(19.5f)
        curveTo(19.5f, 20.6f, 18.6f, 21.5f, 17.5f, 21.5f)
        horizontalLineTo(6.5f)
        curveTo(5.4f, 21.5f, 4.5f, 20.6f, 4.5f, 19.5f)
        verticalLineTo(12.5f)
        curveTo(4.5f, 11.4f, 5.4f, 10.5f, 6.5f, 10.5f)
        close()
        moveTo(8f, 10.5f)
        verticalLineTo(6.5f)
        curveTo(8f, 4.3f, 9.8f, 2.5f, 12f, 2.5f)
        curveTo(14.2f, 2.5f, 16f, 4.3f, 16f, 6.5f)
        verticalLineTo(10.5f)
      }
      path(
        fill = transparent,
        stroke = stroke,
        strokeLineWidth = w,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
      ) {
        moveTo(14.5f, 12f)
        lineTo(12.25f, 14.25f)
        verticalLineTo(17.75f)
        lineTo(9.75f, 15.25f)
        horizontalLineTo(7.5f)
        verticalLineTo(11.75f)
        horizontalLineTo(9.75f)
        lineTo(12.25f, 9.25f)
        verticalLineTo(12f)
        moveTo(15.75f, 10.75f)
        lineTo(20.25f, 15.25f)
        moveTo(20.25f, 10.75f)
        lineTo(15.75f, 15.25f)
      }
    }.build()
  }

  /** 点击穿透：手指 + 虚线穿过圆环 */
  val ClickPassthroughOutlined: ImageVector by lazy {
    ImageVector.Builder(
      name = "ClickPassthroughOutlined",
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f
    ).apply {
      val stroke = SolidColor(Color.Black)
      val w = 1.5f
      val transparent = SolidColor(Color.Transparent)
      path(
        fill = transparent,
        stroke = stroke,
        strokeLineWidth = w,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
      ) {
        moveTo(9.5f, 5.5f)
        verticalLineTo(12.5f)
        moveTo(9.5f, 9.5f)
        curveTo(7.8f, 9.5f, 6.5f, 10.8f, 6.5f, 12.5f)
        verticalLineTo(16.5f)
        curveTo(6.5f, 18.7f, 8.3f, 20.5f, 10.5f, 20.5f)
        horizontalLineTo(13.5f)
        curveTo(16.3f, 20.5f, 18.5f, 18.3f, 18.5f, 15.5f)
        verticalLineTo(11.5f)
        moveTo(13.5f, 4.5f)
        curveTo(15.2f, 5.3f, 16.5f, 7f, 16.5f, 9f)
      }
      path(
        fill = transparent,
        stroke = stroke,
        strokeLineWidth = w,
        strokeLineCap = StrokeCap.Round
      ) {
        moveTo(17.5f, 3.5f)
        curveTo(20.5f, 3.5f, 22.5f, 5.5f, 22.5f, 8.5f)
        curveTo(22.5f, 11.5f, 20.5f, 13.5f, 17.5f, 13.5f)
        curveTo(14.5f, 13.5f, 12.5f, 11.5f, 12.5f, 8.5f)
      }
      path(
        fill = transparent,
        stroke = stroke,
        strokeLineWidth = w,
        strokeLineCap = StrokeCap.Round,
        pathFillType = PathFillType.NonZero
      ) {
        moveTo(11f, 8.5f)
        lineTo(19f, 8.5f)
        moveTo(11f, 6.5f)
        lineTo(11f, 10.5f)
        moveTo(19f, 6.5f)
        lineTo(19f, 10.5f)
      }
    }.build()
  }

  /**
   * Material **Symbols** 版 `dashboard_customize`（九宫格 + 铅笔）——首页「按应用分档」用。
   *
   * ⚠️ 为什么自绘：androidx `material-icons-extended` 属于**旧 Material Icons** 体系，
   * 里面同名的 `DashboardCustomize` 画的是"九宫格 + **加号**"；Google 迁移到 Symbols 时把这个
   * 图标重画成了铅笔版。这里按 Symbols 的 960×960 原始路径搬过来。
   *
   * ⚠️ 坐标：Symbols 的 `viewBox="0 -960 960 960"` **只是把视口原点移到了 y=-960，y 方向仍然向下**，
   * 所以这里只需把整条路径**平移 +960**（`group(translationY = 960f)`）。**不要**用 `scaleY = -1`
   * 去"翻转 y 轴"——那会把图标整个上下镜像（踩过这个坑）。
   * `Z` 之后的相对 `m` 已换算成绝对坐标，免得依赖 close() 之后当前点的语义差异。
   */
  val DashboardCustomizeOutlined: ImageVector by lazy {
    ImageVector.Builder(
      name = "DashboardCustomizeOutlined",
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 960f,
      viewportHeight = 960f
    ).apply {
      group(translationY = 960f) {
        path(fill = SolidColor(Color.Black)) {
          // 九个圆点（这里只画 6 个，右下三个位置让给铅笔）
          moveTo(183.5f, -183.5f)
          quadTo(160f, -207f, 160f, -240f)
          reflectiveQuadToRelative(23.5f, -56.5f)
          quadTo(207f, -320f, 240f, -320f)
          reflectiveQuadToRelative(56.5f, 23.5f)
          quadTo(320f, -273f, 320f, -240f)
          reflectiveQuadToRelative(-23.5f, 56.5f)
          quadTo(273f, -160f, 240f, -160f)
          reflectiveQuadToRelative(-56.5f, -23.5f)
          close()
          moveTo(183.5f, -423.5f)
          quadTo(160f, -447f, 160f, -480f)
          reflectiveQuadToRelative(23.5f, -56.5f)
          quadTo(207f, -560f, 240f, -560f)
          reflectiveQuadToRelative(56.5f, 23.5f)
          quadTo(320f, -513f, 320f, -480f)
          reflectiveQuadToRelative(-23.5f, 56.5f)
          quadTo(273f, -400f, 240f, -400f)
          reflectiveQuadToRelative(-56.5f, -23.5f)
          close()
          moveTo(183.5f, -663.5f)
          quadTo(160f, -687f, 160f, -720f)
          reflectiveQuadToRelative(23.5f, -56.5f)
          quadTo(207f, -800f, 240f, -800f)
          reflectiveQuadToRelative(56.5f, 23.5f)
          quadTo(320f, -753f, 320f, -720f)
          reflectiveQuadToRelative(-23.5f, 56.5f)
          quadTo(273f, -640f, 240f, -640f)
          reflectiveQuadToRelative(-56.5f, -23.5f)
          close()
          moveTo(423.5f, -663.5f)
          quadTo(400f, -687f, 400f, -720f)
          reflectiveQuadToRelative(23.5f, -56.5f)
          quadTo(447f, -800f, 480f, -800f)
          reflectiveQuadToRelative(56.5f, 23.5f)
          quadTo(560f, -753f, 560f, -720f)
          reflectiveQuadToRelative(-23.5f, 56.5f)
          quadTo(513f, -640f, 480f, -640f)
          reflectiveQuadToRelative(-56.5f, -23.5f)
          close()
          moveTo(663.5f, -663.5f)
          quadTo(640f, -687f, 640f, -720f)
          reflectiveQuadToRelative(23.5f, -56.5f)
          quadTo(687f, -800f, 720f, -800f)
          reflectiveQuadToRelative(56.5f, 23.5f)
          quadTo(800f, -753f, 800f, -720f)
          reflectiveQuadToRelative(-23.5f, 56.5f)
          quadTo(753f, -640f, 720f, -640f)
          reflectiveQuadToRelative(-56.5f, -23.5f)
          close()
          moveTo(423.5f, -423.5f)
          quadTo(400f, -447f, 400f, -480f)
          reflectiveQuadToRelative(23.5f, -56.5f)
          quadTo(447f, -560f, 480f, -560f)
          reflectiveQuadToRelative(56.5f, 23.5f)
          quadTo(560f, -513f, 560f, -480f)
          reflectiveQuadToRelative(-23.5f, 56.5f)
          quadTo(513f, -400f, 480f, -400f)
          reflectiveQuadToRelative(-56.5f, -23.5f)
          close()
          // 铅笔（右下）
          moveTo(520f, -160f)
          verticalLineToRelative(-123f)
          lineToRelative(221f, -220f)
          quadToRelative(9f, -9f, 20f, -13f)
          reflectiveQuadToRelative(22f, -4f)
          quadToRelative(12f, 0f, 23f, 4.5f)
          reflectiveQuadToRelative(20f, 13.5f)
          lineToRelative(37f, 37f)
          quadToRelative(8f, 9f, 12.5f, 20f)
          reflectiveQuadToRelative(4.5f, 22f)
          quadToRelative(0f, 11f, -4f, 22.5f)
          reflectiveQuadTo(863f, -380f)
          lineTo(643f, -160f)
          horizontalLineTo(520f)
          close()
          moveTo(820f, -423f)
          lineToRelative(-37f, -37f)
          lineToRelative(37f, 37f)
          close()
          moveTo(580f, -220f)
          horizontalLineToRelative(38f)
          lineToRelative(121f, -122f)
          lineToRelative(-18f, -19f)
          lineToRelative(-19f, -18f)
          lineToRelative(-122f, 121f)
          verticalLineToRelative(38f)
          close()
          moveTo(721f, -361f)
          lineToRelative(-19f, -18f)
          lineToRelative(37f, 37f)
          lineToRelative(-18f, -19f)
          close()
        }
      }
    }.build()
  }
}
