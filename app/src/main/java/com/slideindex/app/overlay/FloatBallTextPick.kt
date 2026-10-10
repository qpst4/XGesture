package com.slideindex.app.overlay

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.slideindex.app.clipboard.ClipboardAccess
import com.slideindex.app.clipboard.ClipboardPayload
import com.slideindex.app.clipboard.ClipboardReader
import com.slideindex.app.R
import com.slideindex.app.imageeditor.ImageEditorPickReturnContext
import com.slideindex.app.search.SearchEngineLauncher
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.translate.TranslateAppCapability
import com.slideindex.app.translate.TranslateLanguageCatalog
import com.slideindex.app.translate.TranslateLaunchChannel
import com.slideindex.app.translate.TranslateLaunchPlanner
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FloatBallTextPick {
    private const val SHARE_CACHE_DIR = "float_ball_share"

    private val mainHandler = Handler(Looper.getMainLooper())

    private fun shareCacheDir(context: Context): File =
        File(context.cacheDir, SHARE_CACHE_DIR).apply { mkdirs() }

    private fun deleteShareImageUri(context: Context, uri: Uri) {
        val name = uri.lastPathSegment ?: return
        File(shareCacheDir(context), name).delete()
    }

    fun deliverResult(context: Context?, text: String?, showEmptyToast: Boolean = true) {
        val appContext = context?.applicationContext ?: return
        mainHandler.post {
            if (text.isNullOrBlank()) {
                if (showEmptyToast) {
                    Toast.makeText(
                        appContext,
                        appContext.getString(R.string.float_ball_text_not_found),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                return@post
            }
            copyText(appContext, text)
            Toast.makeText(
                appContext,
                appContext.getString(R.string.float_ball_text_copied),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun copyText(context: Context, text: String) {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText("float_ball_text", text))
    }

    fun copyImage(context: Context, bitmap: Bitmap) {
        val uri = createShareImageUri(context, bitmap) ?: run {
            Toast.makeText(context, R.string.float_ball_action_failed, Toast.LENGTH_SHORT).show()
            return
        }
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(ClipData.newUri(context.contentResolver, "image", uri))
        Toast.makeText(context, R.string.float_ball_image_copied, Toast.LENGTH_SHORT).show()
    }

    /**
     * 异步复制图片到剪贴板，[onDone] 回主线程，true 表示已写入剪贴板。
     *
     * 取词面板长按图片走这条路径：[bitmap] 是面板持有的位图，面板 dismiss 后会被
     * `recycleOwnedPanelImages` 回收，所以先在调用线程（主线程）拷一份快照再交给后台 ——
     * PNG 无损压缩在一张 1440p 截图上要几百毫秒，留在主线程会卡住与无障碍同进程的 `:overlay`。
     */
    fun copyImageAsync(context: Context, bitmap: Bitmap, onDone: (Boolean) -> Unit) {
        val appContext = context.applicationContext
        val snapshot = if (bitmap.isRecycled) {
            null
        } else {
            runCatching { bitmap.copy(Bitmap.Config.ARGB_8888, false) }.getOrNull()
        }
        if (snapshot == null) {
            mainHandler.post { onDone(false) }
            return
        }
        Thread({
            // 浮层与无障碍同进程：后台线程漏出的异常会连无障碍一起带走，这里全部兜住。
            val uri = try {
                createShareImageUri(appContext, snapshot)
            } catch (_: Throwable) {
                null
            } finally {
                snapshot.recycle()
            }
            mainHandler.post {
                val clipboard = appContext.getSystemService(ClipboardManager::class.java)
                if (uri == null || clipboard == null) {
                    onDone(false)
                } else {
                    val written = runCatching { clipboard.setPrimaryClip(imageUriClip(uri)) }.isSuccess
                    onDone(written)
                }
            }
        }, "pick-copy-image").start()
    }

    /**
     * 显式声明 mime 的图片剪贴项。
     *
     * `ClipData.newUri` 的第二参只是 label，mime 靠 resolver 反查：取词面板交出去的是
     * FileProvider 的 cache URI，一旦反查不到就退化成 `text/plain`，接收方会把图片当纯文本。
     * 与 `ClipboardWriter.uriClip` 同一处理。
     */
    private fun imageUriClip(uri: Uri): ClipData = ClipData(
        ClipDescription("image", arrayOf("image/*")),
        ClipData.Item(uri),
    )

    fun readClipboardText(context: Context): String? = ClipboardReader.read(context)?.text

    fun readClipboardPayload(context: Context): ClipboardPayload? = ClipboardReader.read(context)

    private const val WEB_TRANSLATE_URL = "https://translate.google.com/"
    private const val FALLBACK_WEB_TARGET_LANG = "zh-CN"

    /**
     * 取词面板的「跳转翻译」（关闭即时翻译时的行为）。
     *
     * 优先把文本**直接交给 Google 翻译 App**：`ACTION_PROCESS_TEXT` 会让文本进它的输入框，
     * 这是唯一"跳过去还带着文本"的方式；`ACTION_SEND` 作为兜底（部分版本只注册了分享入口）。
     * 没装、或两个通道都拉不起来时，才回落到网页翻译。
     *
     * 以前这里是发一个裸的 `https://translate.google.com/?...&text=` 链接：Google 翻译注册了该
     * 域名的 App Links，系统会把这种隐式 Intent 优先交给 App，而 App 不消费 URL 里的 `text`，
     * 于是只会"打开一个空白输入框"，选中的文本还丢了。
     *
     * [targetLang] 由调用方用 `TranslateTargetResolver` 解析好；网页翻译不再写死成中文。
     */
    fun translateText(context: Context, text: String, targetLang: String) {
        if (launchToTranslateApp(context, text, TranslateLaunchPlanner.GOOGLE_TRANSLATE_PACKAGE)) return
        if (openTranslateWeb(context, text, targetLang)) return
        // 连浏览器都拉不起来：退回原来的"搜索一下"，别让用户点了没反应。
        searchText(context, "translate $text")
    }

    /**
     * 引擎选「本地 App」时：只走用户在设置里指定的那个 App。
     *
     * 用户主动选过目标，所以失败时给提示、不静默改道：没选 App 提示「未设置翻译应用」并回落网页，
     * 选了却拉不起来提示「未找到翻译应用」并把文本复制到剪贴板，免得白点一下。
     */
    fun translateToApp(context: Context, text: String, targetPackage: String, targetLang: String) {
        val pkg = targetPackage.trim()
        if (pkg.isNotEmpty() && launchToTranslateApp(context, text, pkg)) return

        if (pkg.isEmpty()) {
            Toast.makeText(context, R.string.float_ball_translate_app_not_set, Toast.LENGTH_SHORT).show()
            if (openTranslateWeb(context, text, targetLang)) return
            searchText(context, "translate $text")
            return
        }

        copyText(context, text)
        Toast.makeText(context, R.string.float_ball_translate_app_not_found_copied, Toast.LENGTH_SHORT).show()
    }

    /** 按 [TranslateLaunchPlanner] 给出的顺序尝试把文本交给 [targetPackage]；全都失败返回 false。 */
    private fun launchToTranslateApp(context: Context, text: String, targetPackage: String): Boolean {
        val channels = TranslateLaunchPlanner.plan(
            processTextPackages = TranslateAppCapability.processTextPackages(context),
            sendPackages = TranslateAppCapability.sendPackages(context),
            targetPackage = targetPackage,
        )
        for (channel in channels) {
            val launched = when (channel) {
                TranslateLaunchChannel.PROCESS_TEXT -> startTranslateApp(
                    context,
                    translateAppIntent(Intent.ACTION_PROCESS_TEXT, text, targetPackage),
                )

                TranslateLaunchChannel.SEND -> startTranslateApp(
                    context,
                    translateAppIntent(Intent.ACTION_SEND, text, targetPackage),
                )

                // 末尾的 WEB 由调用方决定是"静默回落"还是"提示 + 复制"。
                TranslateLaunchChannel.WEB -> return false
            }
            if (launched) return true
        }
        return false
    }

    private fun translateAppIntent(action: String, text: String, targetPackage: String): Intent =
        Intent(action).apply {
            type = "text/plain"
            setPackage(targetPackage)
            if (action == Intent.ACTION_PROCESS_TEXT) {
                putExtra(Intent.EXTRA_PROCESS_TEXT, text)
                // 只读：翻译用不上"把改写后的文本回填到编辑器"的语义。
                putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
            } else {
                putExtra(Intent.EXTRA_TEXT, text)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    private fun startTranslateApp(context: Context, intent: Intent): Boolean =
        runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)

    private fun openTranslateWeb(context: Context, text: String, targetLang: String): Boolean {
        val lang = TranslateLanguageCatalog.find(targetLang)?.code ?: FALLBACK_WEB_TARGET_LANG
        val intent = Intent(
            Intent.ACTION_VIEW,
            "$WEB_TRANSLATE_URL?sl=auto&tl=$lang&text=${Uri.encode(text)}".toUri()
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    fun searchText(context: Context, text: String) {
        val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
            putExtra("query", text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
            .onFailure {
                Toast.makeText(context, R.string.float_ball_action_failed, Toast.LENGTH_SHORT).show()
            }
    }

    fun openUrl(
        context: Context,
        url: String,
        settings: AppSettings,
        longPressTriggered: Boolean = false
    ) {
        SearchEngineLauncher.launchOpenableUri(context, url, settings, longPressTriggered)
    }

    fun shareText(context: Context, text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(intent, context.getString(R.string.float_ball_action_share))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(chooser) }
            .onFailure {
                Toast.makeText(context, R.string.float_ball_action_failed, Toast.LENGTH_SHORT).show()
            }
    }

    fun shareTextTo(context: Context, text: String, target: ComponentName): Boolean {
        return runCatching {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                component = target
            }
            context.startActivity(intent)
            true
        }.getOrElse {
            Toast.makeText(context, R.string.float_ball_action_failed, Toast.LENGTH_SHORT).show()
            false
        }
    }

    fun saveScreenshot(context: Context, bitmap: Bitmap): Boolean =
        saveScreenshotReturningUri(context, bitmap) != null

    fun saveScreenshotReturningUri(
        context: Context,
        bitmap: Bitmap,
        ingestToHistory: Boolean = true,
    ): Uri? {
        val fileName = screenshotFileName()
        val values = android.content.ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_DCIM}/Screenshots")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return null
        return runCatching {
            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            } ?: error("no stream")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            if (ingestToHistory) {
                ClipboardAccess.repository?.ingestScreenshot(uri, fileName)
            }
            uri
        }.onFailure {
            resolver.delete(uri, null, null)
        }.getOrNull()
    }

    private fun screenshotFileName(): String {
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        return "Screenshot_$timestamp.png"
    }

    fun createShareImageUri(context: Context, bitmap: Bitmap): Uri? {
        val fileName = "float_ball_share_${System.currentTimeMillis()}.png"
        val file = File(shareCacheDir(context), fileName)
        return runCatching {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
        }.onFailure {
            file.delete()
        }.getOrNull()
    }

    fun shareScreenshot(context: Context, bitmap: Bitmap) {
        val uri = createShareImageUri(context, bitmap)
            ?: run {
                Toast.makeText(context, R.string.float_ball_action_failed, Toast.LENGTH_SHORT).show()
                return
            }
        runCatching {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newUri(context.contentResolver, "image", uri)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, context.getString(R.string.float_ball_action_share))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        }.onFailure {
            deleteShareImageUri(context, uri)
            Toast.makeText(context, R.string.float_ball_action_failed, Toast.LENGTH_SHORT).show()
        }
    }

    fun viewScreenshot(
        context: Context,
        bitmap: Bitmap,
        targetPackage: String? = null,
        screenRect: Rect? = null,
        layoutMeta: ScreenshotLayoutMeta? = null,
        pickReturnContext: ImageEditorPickReturnContext? = null,
    ): Boolean {
        if (targetPackage == com.slideindex.app.imageeditor.ImageEditorOpenTargets.BUILTIN_PACKAGE) {
            return openBuiltinImageEditor(context, bitmap, screenRect, layoutMeta, pickReturnContext)
        }
        if (targetPackage.isNullOrBlank()) {
            // "每次都询问" = 交给系统弹"打开方式"，不再自己画列表。
            return openSystemImageChooser(context, bitmap, screenRect, layoutMeta, pickReturnContext)
        }
        return viewScreenshotWithPackage(context, bitmap, targetPackage)
    }

    /**
     * "每次都询问"：用系统 chooser（ACTION_VIEW + createChooser）来问。
     *
     * 以前这里是自己画一个列表对话框，用的是 AppCompat 的 AlertDialog 配浮层那套主题
     * （`android:Theme.Material.Light.NoActionBar`）。那套主题里没有 AppCompat 的
     * `alertDialogTheme/listLayout`，列表布局解析成资源 id 0 → `inflate(0)` 抛
     * `Resources$NotFoundException`；而浮层和无障碍服务同在 `:overlay` 进程，
     * 等于点一下图片就把无障碍服务一起带走（真机 crash_20260927_003149 已确认）。
     *
     * 系统 chooser 由系统绘制，不受我们的主题影响，而且自带"仅此一次 / 始终"——
     * 这才是"每次都询问"本来的语义。
     */
    private fun openSystemImageChooser(
        context: Context,
        bitmap: Bitmap,
        screenRect: Rect? = null,
        layoutMeta: ScreenshotLayoutMeta? = null,
        pickReturnContext: ImageEditorPickReturnContext? = null,
    ): Boolean {
        val uri = createShareImageUri(context, bitmap)
            ?: run {
                Toast.makeText(context, R.string.float_ball_action_failed, Toast.LENGTH_SHORT).show()
                return false
            }
        val target = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "image/*")
            clipData = ClipData.newUri(context.contentResolver, "image", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        // 系统里一个能看图的 App 都没有时，chooser 会是空的：回退到内置编辑器，别让用户点了没反应。
        val hasExternalViewer = runCatching {
            context.packageManager.queryIntentActivities(target, 0).isNotEmpty()
        }.getOrDefault(true)
        if (!hasExternalViewer) {
            deleteShareImageUri(context, uri)
            return openBuiltinImageEditor(context, bitmap, screenRect, layoutMeta, pickReturnContext)
        }
        return runCatching {
            val chooser = Intent.createChooser(
                target,
                context.getString(R.string.float_ball_action_open_image),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
            true
        }.getOrElse {
            deleteShareImageUri(context, uri)
            Toast.makeText(context, R.string.float_ball_action_failed, Toast.LENGTH_SHORT).show()
            false
        }
    }

    private fun viewScreenshotWithPackage(context: Context, bitmap: Bitmap, targetPackage: String): Boolean {
        val uri = createShareImageUri(context, bitmap)
            ?: run {
                Toast.makeText(context, R.string.float_ball_action_failed, Toast.LENGTH_SHORT).show()
                return false
            }
        return runCatching {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "image/*")
                clipData = ClipData.newUri(context.contentResolver, "image", uri)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                setPackage(targetPackage)
            }
            context.startActivity(intent)
            true
        }.getOrElse {
            deleteShareImageUri(context, uri)
            Toast.makeText(context, R.string.float_ball_action_failed, Toast.LENGTH_SHORT).show()
            false
        }
    }

    fun openBuiltinImageEditor(
        context: Context,
        bitmap: Bitmap,
        screenRect: Rect? = null,
        layoutMeta: ScreenshotLayoutMeta? = null,
        pickReturnContext: ImageEditorPickReturnContext? = null,
    ): Boolean {
        return runCatching {
            val copy = bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: return false
            val editorPath = com.slideindex.app.imageeditor.ImageEditorLaunchCache.put(
                context,
                copy,
                screenRect,
                layoutMeta,
                pickReturnContext,
            )
            com.slideindex.app.imageeditor.SlideIndexImageEditorActivity.launch(context, editorPath)
            true
        }.getOrElse {
            Toast.makeText(context, R.string.float_ball_action_failed, Toast.LENGTH_SHORT).show()
            false
        }
    }

    fun shareScreenshotTo(context: Context, bitmap: Bitmap, target: ComponentName): Boolean {
        val uri = createShareImageUri(context, bitmap)
            ?: run {
                Toast.makeText(context, R.string.float_ball_action_failed, Toast.LENGTH_SHORT).show()
                return false
            }
        return runCatching {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newUri(context.contentResolver, "image", uri)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                component = target
            }
            context.startActivity(intent)
            true
        }.getOrElse {
            deleteShareImageUri(context, uri)
            Toast.makeText(context, R.string.float_ball_action_failed, Toast.LENGTH_SHORT).show()
            false
        }
    }
}
