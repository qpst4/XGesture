package com.slideindex.app.service

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.slideindex.app.R
import com.slideindex.app.overlay.StashPanelExternalUi
import com.slideindex.app.util.TrampolineResultPort
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 「记一条」里加图片的**中转 Activity**（§0.16.12）。
 *
 * 为什么要中转：**overlay 窗里不能直接拉起系统选图** —— `rememberLauncherForActivityResult` 需要一个
 * `ActivityResultRegistryOwner`，而 overlay 的 Compose 树里没有（App 里所有这类调用都在 `ui` 包下的
 * Activity 里，overlay 侧一个都没有）。所以 overlay 侧的选图一律走 trampoline。
 *
 * 选图用 `PickMultipleVisualMedia`（**系统相册选择器，不需要任何读图权限**，设备没有照片选择器时会自动
 * 回退到 `ACTION_OPEN_DOCUMENT`）。
 *
 * ⚠️ 关键细节：**在 trampoline 里就把图解码并落到 App 自己的 cache**，只回传文件路径。
 * 相册选择器给的 URI 读权限是**绑在发起 Activity 的生命周期**上的，trampoline `finish()` 之后
 * 再让面板去读那个 URI 可能就 EACCES 了；落成自己的文件则永远读得到，也绕开了 Bundle 不能塞大图的问题。
 */
class StashComposerImageTrampolineActivity : ComponentActivity() {

    private val maxItems: Int
        get() = intent.getIntExtra(EXTRA_MAX_ITEMS, DefaultMaxItems).coerceIn(2, 20)

    /** 是否已经交付过结果（`onResume` 兜底与 launcher 回调都可能在同一次里跑，必须幂等）。 */
    private var delivered = false

    /** 选择器是否真的压过我们（`onPause`）—— `onResume` 兜底的判据。 */
    private var pausedWithPicker = false

    /**
     * 选择器**已经交回结果**（回调进来了）。
     *
     * ⚠️ 与 [delivered] 分开：回调里还要把 URI 解码落盘（`Dispatchers.IO`，有耗时），
     * 那段时间 [delivered] 仍是 false，若 `onResume` 兜底按 [delivered] 判，就会在落盘途中
     * 抢先交付一个空列表 —— 用户选的图全丢（比"面板卡住"严重得多）。
     */
    private var resultReceived = false

    /* ---------- §0.16.23：面板挂起租约（防止"结果丢了 → 面板冻死 / 看门狗误伤"） ---------- */

    private val leaseHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var leaseRunning = false
    private val leaseTick = object : Runnable {
        override fun run() {
            if (!leaseRunning) return
            StashPanelExternalUi.renewExternalUiLease(owner = LeaseOwner)
            leaseHandler.postDelayed(this, LeaseIntervalMs)
        }
    }

    private val pickLauncher = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(DefaultMaxItems)
    ) { uris ->
        resultReceived = true
        if (uris.isEmpty()) {
            deliver(emptyList())
        } else {
            lifecycleScope.launch {
                val paths = withContext(Dispatchers.IO) {
                    uris.take(maxItems).mapNotNull { uri -> copyToCache(uri) }
                }
                if (paths.isEmpty()) {
                    Toast.makeText(
                        this@StashComposerImageTrampolineActivity,
                        R.string.stash_pin_add_failed,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                deliver(paths)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // §0.16.23：把"面板为外部 UI 挂起"的租约往后推一次 —— 相册选择器起来之前（onPause 之前）
        // 也归看门狗管；随后每次 onPause 开始续租，onResume 停止（见下面三个回调）。
        StashPanelExternalUi.renewExternalUiLease(owner = LeaseOwner)
        runCatching { pickLauncher.launch(imageOnlyRequest) }.onFailure {
            Toast.makeText(this, R.string.stash_pin_add_failed, Toast.LENGTH_SHORT).show()
            deliver(emptyList())
        }
    }

    /**
     * 相册选择器（外部 UI）压上来了：**开始续租**。
     *
     * ⚠️ 没有这一步，`StashPanelExternalUi` 的 5s 看门狗会在用户翻相册翻到一半时把面板恢复出来 ——
     * 面板是比相册更高的无障碍覆盖层，那一瞬间用户就点不着相册了。续租 = "外部 UI 还活着"。
     */
    override fun onPause() {
        super.onPause()
        startLease()
    }

    /**
     * 回到前台 = 外部 UI 已经不在我们上面了：停止续租。
     *
     * 顺手兜底"结果/生命周期丢了"：已经压过我们（[pausedWithPicker]）却还没交付结果，
     * 就按取消交付一次 —— 面板挂起态不会因为回调丢失而永久卡死（§0.16.23）。
     */
    override fun onResume() {
        super.onResume()
        stopLease()
        if (pausedWithPicker && !resultReceived && !delivered) {
            Log.w(TAG, "onResume 兜底：选择器已退出但没有任何结果 → 按取消交付（面板恢复由发起方做）")
            deliver(emptyList())
        }
    }

    override fun onDestroy() {
        stopLease()
        super.onDestroy()
    }

    private fun startLease() {
        pausedWithPicker = true
        if (leaseRunning) return
        leaseRunning = true
        leaseHandler.removeCallbacks(leaseTick)
        leaseHandler.postDelayed(leaseTick, LeaseIntervalMs)
    }

    private fun stopLease() {
        if (!leaseRunning) return
        leaseRunning = false
        leaseHandler.removeCallbacks(leaseTick)
    }

    private fun deliver(paths: List<String>) {
        if (delivered) {
            Log.i(TAG, "deliver 重复调用（幂等忽略）")
            return
        }
        delivered = true
        val token = intent.getStringExtra(TrampolineResultPort.EXTRA_TOKEN).orEmpty()
        if (token.isNotEmpty()) {
            TrampolineResultPort.deliver(
                this,
                token,
                Bundle().apply {
                    putStringArrayList(EXTRA_PATHS, ArrayList(paths))
                    putBoolean(TrampolineResultPort.EXTRA_CANCELLED, paths.isEmpty())
                },
            )
        } else {
            Log.w(TAG, "deliver：intent 里没有 token → 发起方收不到结果")
        }
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    /** 解码（长边 ≤ 2048）后写成 App cache 里的 JPEG，返回绝对路径。 */
    private fun copyToCache(uri: Uri): String? = runCatching {
        val bitmap = decodeSampled(uri) ?: return null
        val dir = File(cacheDir, CacheDirName).apply { mkdirs() }
        val file = File(dir, "${UUID.randomUUID()}.jpg")
        file.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out) }
        bitmap.recycle()
        file.absolutePath
    }.getOrNull()

    private fun decodeSampled(uri: Uri): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > MaxDimension || bounds.outHeight / sample > MaxDimension) {
            sample *= 2
        }
        contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(
                stream,
                null,
                BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                },
            )
        }
    }.getOrNull()

    companion object {
        const val EXTRA_PATHS = "stash_composer_image_paths"
        private const val EXTRA_MAX_ITEMS = "stash_composer_image_max"
        private const val DefaultMaxItems = 9
        private const val CacheDirName = "stash_composer_images"
        private const val MaxDimension = 2048

        /** 日志 tag（与图片编辑链路同一个 tag，`adb logcat -s StashImageEdit` 能看到全部外部 UI 挂起/恢复）。 */
        private const val TAG = com.slideindex.app.service.StashEditImageTrampolineActivity.LOG_TAG
        private const val LeaseOwner = "composer-add-image"
        private const val LeaseIntervalMs = 2_000L

        /** 照片选择器请求（`PickVisualMediaRequest(ImageOnly)`，避免把视频也选进来）。 */
        private val imageOnlyRequest =
            androidx.activity.result.PickVisualMediaRequest(
                ActivityResultContracts.PickVisualMedia.ImageOnly,
            )

        /**
         * 从 overlay 侧发起选图：注册一次性回调 → 启动中转 Activity → 结果回来时回调路径列表
         * （空列表 = 用户取消或全部解码失败）。
         */
        fun launch(context: Context, onPicked: (List<String>) -> Unit) {
            val appContext = context.applicationContext
            val token = UUID.randomUUID().toString()
            TrampolineResultPort.register(appContext, token) { payload ->
                onPicked(payload.getStringArrayList(EXTRA_PATHS).orEmpty())
            }
            val intent = Intent(appContext, StashComposerImageTrampolineActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(TrampolineResultPort.EXTRA_TOKEN, token)
            }
            runCatching { appContext.startActivity(intent) }.onFailure {
                TrampolineResultPort.clearToken(token)
                onPicked(emptyList())
            }
        }
    }
}

