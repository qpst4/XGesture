package com.slideindex.app.overlay.browser

import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.widget.FrameLayout
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat

/**
 * 真正的系统画中画：把嗅探到的视频直链放进一个真实 Activity 播放，
 * 然后进入 PiP 模式（浮层窗口本身无法进入系统 PiP）。
 */
class VideoPipActivity : ComponentActivity() {

    private var videoView: VideoView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra(EXTRA_URL).orEmpty()
        if (url.isBlank()) {
            finish()
            return
        }

        val container = FrameLayout(this)
        val video = VideoView(this)
        container.addView(
            video,
            FrameLayout.LayoutParams(-1, -1),
        )
        video.setVideoPath(url)
        video.setOnPreparedListener { player ->
            player.isLooping = true
            video.start()
        }
        videoView = video
        setContentView(container)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching {
                enterPictureInPictureMode(
                    PictureInPictureParams.Builder()
                        .setAspectRatio(Rational(16, 9))
                        .build(),
                )
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !isInPictureInPictureMode) {
            runCatching {
                enterPictureInPictureMode(
                    PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build(),
                )
            }
        }
    }

    override fun onDestroy() {
        runCatching {
            videoView?.stopPlayback()
            videoView = null
        }
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_URL = "url"

        /** 用免转场的透明 Activity 直接进 PiP。 */
        fun start(context: Context, url: String) {
            val intent = Intent(context, VideoPipActivity::class.java)
                .putExtra(EXTRA_URL, url)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ContextCompat.startActivity(context, intent, null)
        }
    }
}
