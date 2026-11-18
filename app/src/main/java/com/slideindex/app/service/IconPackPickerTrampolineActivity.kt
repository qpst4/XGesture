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
import com.slideindex.app.iconpack.IconPackTarget
import com.slideindex.app.ui.IconPackPickerPage
import com.slideindex.app.ui.compose.LocalAppDependencies
import com.slideindex.app.ui.miuix.theme.ModuleTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 「从手机已有 app 图标包中选择」的宿主页（独立 Activity，不是弹窗）。
 *
 * 与 [IconLibraryPickerTrampolineActivity] 同构：图标网格 + 搜索需要系统转场、
 * 返回键和进程回收后的状态恢复，Dialog 撑不住。
 *
 * 结果：回传落盘后的 PNG 绝对路径（[EXTRA_PICKED_PATH]），调用方用
 * `ActionIconKeyCodec.file(path)` 存成 `file:` key。
 */
@AndroidEntryPoint
class IconPackPickerTrampolineActivity : ComponentActivity() {

    @Inject lateinit var deps: AppDependencies

    override fun onCreate(savedInstanceState: Bundle?) {
        // auto：浅色页面用深色系统栏图标（与设置页一致），深色模式自动反转
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        val target = IconPackTarget(
            packageName = intent.getStringExtra(EXTRA_APP_PACKAGE).orEmpty(),
            activityName = intent.getStringExtra(EXTRA_APP_ACTIVITY),
            label = intent.getStringExtra(EXTRA_APP_LABEL).orEmpty(),
        )
        val storageKey = intent.getStringExtra(EXTRA_STORAGE_KEY).orEmpty()

        setContent {
            MiuixTopLayerRoot {
                CompositionLocalProvider(LocalAppDependencies provides deps) {
                    ModuleTheme {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MiuixTheme.colorScheme.background,
                        ) {
                            IconPackPickerPage(
                                target = target,
                                storageKey = storageKey,
                                onPicked = { path ->
                                    setResult(
                                        RESULT_OK,
                                        Intent().putExtra(EXTRA_PICKED_PATH, path),
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
        const val EXTRA_APP_PACKAGE = "icon_pack_app_package"
        const val EXTRA_APP_ACTIVITY = "icon_pack_app_activity"
        const val EXTRA_APP_LABEL = "icon_pack_app_label"
        const val EXTRA_STORAGE_KEY = "icon_pack_storage_key"
        const val EXTRA_PICKED_PATH = "icon_pack_picked_path"

        fun createIntent(context: Context, target: IconPackTarget, storageKey: String): Intent =
            Intent(context, IconPackPickerTrampolineActivity::class.java)
                .putExtra(EXTRA_APP_PACKAGE, target.packageName)
                .putExtra(EXTRA_APP_ACTIVITY, target.activityName)
                .putExtra(EXTRA_APP_LABEL, target.label)
                .putExtra(EXTRA_STORAGE_KEY, storageKey)
    }
}
