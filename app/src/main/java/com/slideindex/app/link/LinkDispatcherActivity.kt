package com.slideindex.app.link

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.slideindex.app.R
import com.slideindex.app.overlay.pickresult.PickResultUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 链接分发跳板 Activity（透明主题，接收系统 VIEW 意图进行规则匹配、直达跳转与浏览器兜底）。
 */
class LinkDispatcherActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(incoming: Intent?) {
        if (incoming == null) {
            finish()
            return
        }

        val action = incoming.action
        val rawTarget = if (action == Intent.ACTION_VIEW) {
            incoming.dataString
        } else if (action == Intent.ACTION_SEND && incoming.type == "text/plain") {
            incoming.getStringExtra(Intent.EXTRA_TEXT) ?: incoming.dataString
        } else {
            incoming.dataString
        }

        if (rawTarget.isNullOrBlank()) {
            finish()
            return
        }

        lifecycleScope.launch {
            val url = PickResultUrl.normalizeOpenableUrl(rawTarget) ?: rawTarget.trim()
            val result = LinkDispatcher.route(this@LinkDispatcherActivity, url)

            when (result) {
                is LinkDispatchResult.Routed -> {
                    LinkLauncher.launch(
                        context = this@LinkDispatcherActivity,
                        url = result.url,
                        targetPackage = result.rule.targetPackage,
                        excludeFromRecents = result.rule.excludeFromRecents,
                        component = result.rule.component,
                    )
                    LinkDispatchHistoryRepository.record(
                        context = this@LinkDispatcherActivity,
                        rawUrl = url,
                        finalUrl = result.url,
                        matchedRuleName = result.rule.name,
                        targetPackage = result.rule.targetPackage,
                        isSuccess = true,
                        isReDispatch = result.rule.resolveStrategy == LinkResolveStrategy.REDISPATCH,
                    )
                    finish()
                }
                is LinkDispatchResult.Exempted -> {
                    handleFallbackOrSelector(url, "豁免域名: ${result.domain}")
                }
                is LinkDispatchResult.SystemDefault -> {
                    handleFallbackOrSelector(result.url, "重分发未匹配")
                }
                LinkDispatchResult.NoRule -> {
                    handleFallbackOrSelector(url, null)
                }
                LinkDispatchResult.Failed -> {
                    Toast.makeText(this@LinkDispatcherActivity, R.string.link_dispatch_no_app, Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    private suspend fun handleFallbackOrSelector(url: String, reason: String?) {
        val fallbackPkg = LinkRuleRepository.getFallbackBrowser(this@LinkDispatcherActivity)
        if (!fallbackPkg.isNullOrBlank()) {
            LinkLauncher.launch(
                context = this@LinkDispatcherActivity,
                url = url,
                targetPackage = fallbackPkg,
                excludeFromRecents = false,
            )
            LinkDispatchHistoryRepository.record(
                context = this@LinkDispatcherActivity,
                rawUrl = url,
                finalUrl = url,
                matchedRuleName = reason ?: "兜底备选浏览器",
                targetPackage = fallbackPkg,
                isSuccess = true,
            )
            finish()
        } else {
            val browsers = withContext(Dispatchers.IO) {
                BrowserSelectorActivity.queryBrowsers(this@LinkDispatcherActivity)
            }
            if (browsers.size == 1) {
                val singleBrowser = browsers[0].packageName
                LinkLauncher.launch(
                    context = this@LinkDispatcherActivity,
                    url = url,
                    targetPackage = singleBrowser,
                    excludeFromRecents = false,
                )
                LinkDispatchHistoryRepository.record(
                    context = this@LinkDispatcherActivity,
                    rawUrl = url,
                    finalUrl = url,
                    matchedRuleName = reason ?: "单浏览器直达",
                    targetPackage = singleBrowser,
                    isSuccess = true,
                )
                finish()
            } else {
                val selectorIntent = Intent(this@LinkDispatcherActivity, BrowserSelectorActivity::class.java).apply {
                    putExtra("EXTRA_URL", url)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(selectorIntent)
                finish()
            }
        }
    }
}
