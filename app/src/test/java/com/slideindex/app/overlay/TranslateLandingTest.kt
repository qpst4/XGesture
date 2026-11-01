package com.slideindex.app.overlay

import com.slideindex.app.settings.FloatBallTranslateEngine
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 翻译落地方式的判定顺序。
 *
 * 锁住三件事（顺序改了就等于改产品行为）：
 * 1. 「本地 App」永远交给外部 App，与「即时翻译」开关无关；
 * 2. 关闭「即时翻译」永远是跳转（Google / ML Kit / 云端都一样），引擎在这一档不参与；
 * 3. 只有「即时翻译」开着时，才在取词结果面板里用所选引擎翻译。
 */
class TranslateLandingTest {

  private val inAppEngines = listOf(
    FloatBallTranslateEngine.GOOGLE,
    FloatBallTranslateEngine.ML_KIT,
    FloatBallTranslateEngine.CLOUD_LLM,
  )

  @Test
  fun local_app_goes_external_regardless_of_instant_toggle() {
    assertEquals(
      TranslateLanding.EXTERNAL_APP,
      resolveTranslateLanding(FloatBallTranslateEngine.LOCAL_APP, instantTranslate = true),
    )
    assertEquals(
      TranslateLanding.EXTERNAL_APP,
      resolveTranslateLanding(FloatBallTranslateEngine.LOCAL_APP, instantTranslate = false),
    )
  }

  @Test
  fun instant_translate_off_always_jumps_to_web() {
    for (engine in inAppEngines) {
      assertEquals(
        "engine=$engine",
        TranslateLanding.JUMP_TO_WEB,
        resolveTranslateLanding(engine, instantTranslate = false),
      )
    }
  }

  @Test
  fun instant_translate_on_translates_in_panel() {
    for (engine in inAppEngines) {
      assertEquals(
        "engine=$engine",
        TranslateLanding.IN_PANEL,
        resolveTranslateLanding(engine, instantTranslate = true),
      )
    }
  }
}
