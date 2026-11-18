package com.slideindex.app.service

import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import com.slideindex.app.ui.miuix.MiuixTopLayerRoot
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.slideindex.app.di.AppDependencies
import com.slideindex.app.ui.IconLibraryPickerPage
import com.slideindex.app.ui.compose.LocalAppDependencies
import com.slideindex.app.ui.miuix.theme.ModuleTheme
import dagger.hilt.android.AndroidEntryPoint
import top.yukonga.miuix.kmp.theme.MiuixTheme
import javax.inject.Inject

/**
 * 图标库选择页（**独立 Activity**，不是弹窗）。
 *
 * 为什么用一个 trampoline Activity 而不是 Compose 的 Dialog：
 * - 「从图标库选择」在交互上是一级子页面：要有系统转场、返回键、被系统回收后能恢复；
 * - Dialog 承载时会带遮罩/居中动画，看起来永远是"弹窗型中间页"；
 * - 仓库里已有同类先例（`RingLauncherSlotIconEditorTrampolineActivity`）。
 *
 * 结果通过 `setResult` 回传（选中的图标 key，或 RESULT_CANCELED）。
 */
@AndroidEntryPoint
class IconLibraryPickerTrampolineActivity : ComponentActivity() {

    @Inject lateinit var deps: AppDependencies

    override fun onCreate(savedInstanceState: Bundle?) {
        // auto：浅色页面用深色系统栏图标（与设置页一致），深色模式自动反转
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        val initialKey = intent.getStringExtra(EXTRA_INITIAL_KEY)

        setContent {
            MiuixTopLayerRoot {
                CompositionLocalProvider(LocalAppDependencies provides deps) {
                    ModuleTheme {
                        // 不加 statusBarsPadding：内容要延伸到系统栏后面，
                        // 具体 inset 由 SettingsScreenScaffold（Miuix Scaffold）处理。
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MiuixTheme.colorScheme.background,
                        ) {
                            IconLibraryPickerPage(
                                initialKey = initialKey,
                                onPick = { picked ->
                                    setResult(
                                        RESULT_OK,
                                        Intent().putExtra(EXTRA_PICKED_KEY, picked),
                                    )
                                    finish()
                                },
                                onDismiss = {
                                    setResult(RESULT_CANCELED)
                                    finish()
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_INITIAL_KEY = "initial_key"
        const val EXTRA_PICKED_KEY = "picked_key"

        fun createIntent(context: Context, initialKey: String?): Intent =
            Intent(context, IconLibraryPickerTrampolineActivity::class.java)
                .putExtra(EXTRA_INITIAL_KEY, initialKey)
    }
}
