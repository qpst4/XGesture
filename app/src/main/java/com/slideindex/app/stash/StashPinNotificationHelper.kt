package com.slideindex.app.stash

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.slideindex.app.R
import com.slideindex.app.overlay.ScreenPinManager
import com.slideindex.app.service.LaunchTrampolineActivity
import java.io.File
import java.io.FileOutputStream
import kotlinx.serialization.json.Json

internal object StashPinNotificationHelper {
    private const val CHANNEL_ID = "stash_pin_image"
    private const val NOTIFICATION_ID = 23001
    private const val RESTORE_REQUEST_CODE = 23002
    private const val CACHE_DIR_NAME = "pin_notification"
    private const val SNAPSHOT_FILE_NAME = "snapshot.json"
    private const val IMAGE_FILE_NAME = "pin_image.png"
    private const val RICH_IMAGE_PREFIX = "pin_rich_image_"

    const val ACTION_RESTORE_PIN = "com.slideindex.app.action.RESTORE_PIN_FROM_NOTIFICATION"

    private val json = Json { ignoreUnknownKeys = true }

    fun postPinSnapshot(
        context: Context,
        snapshot: PinNotificationSnapshot,
        displayBitmap: Bitmap,
        title: String,
        richImageBitmaps: List<Bitmap> = emptyList()
    ) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        saveSnapshot(context, snapshot, displayBitmap, richImageBitmaps)
        ensureChannel(context)
        NotificationManagerCompat.from(context).notify(
            NOTIFICATION_ID,
            buildNotification(context, displayBitmap, title)
        )
    }

    /**
     * 只换标题：内容 OCR 回来原地刷新用。
     * 图片直接沿用 [saveSnapshot] 落下的 PNG（不重写缓存）；渠道本身是静默的，所以不会二次提醒。
     */
    fun updatePinNotificationTitle(context: Context, title: String) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val image = pinImageFile(context)
            ?.let { file -> runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull() }
            ?: return
        ensureChannel(context)
        NotificationManagerCompat.from(context).notify(
            NOTIFICATION_ID,
            buildNotification(context, image, title)
        )
    }

    private fun buildNotification(
        context: Context,
        displayBitmap: Bitmap,
        title: String
    ): android.app.Notification {
        val contentIntent = PendingIntent.getActivity(
            context,
            RESTORE_REQUEST_CODE,
            LaunchTrampolineActivity.createRestorePinIntent(context).apply {
                action = ACTION_RESTORE_PIN
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.stash_pin_notification_text))
            .setStyle(NotificationCompat.BigPictureStyle().bigPicture(displayBitmap))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setSilent(true)
            .build()
    }

    fun restoreToScreenPin(context: Context) {
        val snapshot = loadSnapshot(context) ?: return
        val imageBitmap = when (snapshot.type) {
            PinNotificationSnapshot.TYPE_IMAGE -> {
                pinImageFile(context)?.let { BitmapFactory.decodeFile(it.absolutePath) } ?: return
            }
            else -> null
        }
        val richImageLoader = when (snapshot.type) {
            PinNotificationSnapshot.TYPE_RICH -> { fileName: String ->
                richImageFile(context, fileName)?.let { BitmapFactory.decodeFile(it.absolutePath) }
            }
            else -> null
        }
        ScreenPinManager.restoreFromNotification(
            context = context,
            snapshot = snapshot,
            imageBitmap = imageBitmap,
            richImageLoader = richImageLoader
        ) { success ->
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
            if (success) {
                clearSnapshotFiles(context)
                Toast.makeText(context, R.string.stash_pin_notification_restored, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, R.string.stash_pin_notification_restore_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun cancelNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        clearSnapshotFiles(context)
    }

    private fun saveSnapshot(
        context: Context,
        snapshot: PinNotificationSnapshot,
        displayBitmap: Bitmap,
        richImageBitmaps: List<Bitmap>
    ) {
        val dir = cacheDir(context)
        clearSnapshotFiles(context)
        runCatching {
            FileOutputStream(File(dir, IMAGE_FILE_NAME)).use { output ->
                displayBitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
            if (snapshot.type == PinNotificationSnapshot.TYPE_RICH) {
                var imageIndex = 0
                richImageBitmaps.forEach { bitmap ->
                    val fileName = "$RICH_IMAGE_PREFIX$imageIndex.png"
                    FileOutputStream(File(dir, fileName)).use { output ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                    }
                    imageIndex++
                }
            }
            File(dir, SNAPSHOT_FILE_NAME).writeText(
                json.encodeToString(PinNotificationSnapshot.serializer(), snapshot)
            )
        }
    }

    private fun loadSnapshot(context: Context): PinNotificationSnapshot? {
        val file = File(cacheDir(context), SNAPSHOT_FILE_NAME)
        if (!file.exists()) return null
        return runCatching {
            json.decodeFromString(PinNotificationSnapshot.serializer(), file.readText())
        }.getOrNull()
    }

    private fun clearSnapshotFiles(context: Context) {
        val dir = cacheDir(context)
        File(dir, SNAPSHOT_FILE_NAME).delete()
        File(dir, IMAGE_FILE_NAME).delete()
        dir.listFiles()
            ?.filter { it.name.startsWith(RICH_IMAGE_PREFIX) }
            ?.forEach { it.delete() }
    }

    private fun cacheDir(context: Context): File {
        return File(context.cacheDir, CACHE_DIR_NAME).apply { mkdirs() }
    }

    private fun pinImageFile(context: Context): File? {
        val file = File(cacheDir(context), IMAGE_FILE_NAME)
        return file.takeIf { it.exists() }
    }

    private fun richImageFile(context: Context, fileName: String): File? {
        if (fileName.isBlank() || !fileName.startsWith(RICH_IMAGE_PREFIX)) return null
        val file = File(cacheDir(context), fileName)
        return file.takeIf { it.exists() }
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.stash_pin_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.stash_pin_notification_channel_desc)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }
}

internal fun savePinShareImage(context: Context, bitmap: Bitmap): File? {
    val dir = File(context.cacheDir, "pin_share").apply { mkdirs() }
    val file = File(dir, "pin_${System.currentTimeMillis()}.png")
    return runCatching {
        FileOutputStream(file).use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
        file
    }.getOrNull()
}
