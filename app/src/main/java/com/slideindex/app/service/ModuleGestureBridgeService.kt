package com.slideindex.app.service

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.Process
import android.util.Log
import com.slideindex.app.xposed.bridge.IModuleGestureBridge

/**
 * 供 system_server 内的 LSPosed 模块绑定的事件桥服务。
 *
 * 只接受系统 UID（1000，模块注入在 system_server）与自身进程的调用，
 * 因此必须声明为 exported；实际数据只包含触摸坐标与时间戳。
 */
class ModuleGestureBridgeService : Service() {

  private val binder = object : IModuleGestureBridge.Stub() {
    override fun isHostReady(): Boolean {
      if (!enforceTrustedCaller()) return false
      return SlideIndexAccessibilityService.isOverlayReady()
    }

    override fun canAcceptTouch(sideId: Int): Boolean {
      if (!enforceTrustedCaller()) return false
      return SlideIndexAccessibilityService.canHandleForwardedSide(sideId)
    }

    override fun canAcceptTouchAt(target: Int, x: Float, y: Float): Boolean {
      if (!enforceTrustedCaller()) return false
      return SlideIndexAccessibilityService.canHandleForwardedTargetAt(target, x, y)
    }

    override fun onTouchEvent(
      sessionId: Long,
      sideId: Int,
      action: Int,
      x: Float,
      y: Float,
      eventTime: Long,
      downTime: Long,
      metaState: Int,
    ) {
      if (!enforceTrustedCaller()) return
      SlideIndexAccessibilityService.handleModuleGestureTouch(
        sessionId = sessionId,
        sideId = sideId,
        action = action,
        x = x,
        y = y,
        eventTime = eventTime,
        downTime = downTime,
        metaState = metaState,
      )
    }

    override fun onSessionEnd(sessionId: Long, reason: Int) {
      if (!enforceTrustedCaller()) return
      SlideIndexAccessibilityService.handleModuleGestureSessionEnd(sessionId, reason)
    }

    override fun cancelRegionalPickSelection(sessionId: Long, sideId: Int): Boolean =
      enforceTrustedCaller() && SlideIndexAccessibilityService.cancelModuleRegionalPickSelection(sessionId, sideId)

    private fun enforceTrustedCaller(): Boolean {
      val callingUid = Binder.getCallingUid()
      if (!isTrustedCaller(callingUid)) {
        Log.w(TAG, "Rejected module bridge call from uid=$callingUid")
        return false
      }
      return true
    }
  }

  /**
   * 永远返回 binder。
   *
   * 曾经在宿主未就绪时返回 null，结果模块收到 onNullBinding 后只能每 2.5s 重试，
   * 而宿主稍后就绪也不会被感知——状态显示"已就绪"、实际一次接管都没发生。
   * 现在改为：绑定恒成立，`isHostReady()` / `canAcceptTouch*` 如实返回 false，
   * 模块据此完全放行（fail-open），宿主就绪后由 [SlideIndexAccessibilityService] 广播唤醒重绑。
   */
  override fun onBind(intent: Intent?): IBinder? {
    Log.i(
      TAG,
      "Module bridge bound, hostReady=${SlideIndexAccessibilityService.isOverlayReady()}",
    )
    return binder
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

  private fun isTrustedCaller(uid: Int): Boolean =
    uid == Process.SYSTEM_UID || uid == applicationInfo.uid

  private companion object {
    const val TAG = "ModuleGestureBridge"
  }
}
