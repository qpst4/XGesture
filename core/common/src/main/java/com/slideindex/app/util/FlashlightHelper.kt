package com.slideindex.app.util

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper

object FlashlightHelper {
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var torchOn = false
    private var cameraManager: CameraManager? = null
    private var torchCameraId: String? = null
    private var observeCount = 0
    private val listeners = LinkedHashSet<(Boolean) -> Unit>()

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId != torchCameraId) return
            setTorchState(enabled)
        }

        override fun onTorchModeUnavailable(cameraId: String) {
            if (cameraId != torchCameraId) return
            setTorchState(false)
        }
    }

    fun isOn(): Boolean = torchOn

    fun addListener(listener: (Boolean) -> Unit) {
        listeners.add(listener)
        listener(torchOn)
    }

    fun removeListener(listener: (Boolean) -> Unit) {
        listeners.remove(listener)
    }

    fun startObserving(context: Context) {
        val app = context.applicationContext
        if (observeCount == 0) {
            val manager = app.getSystemService(CameraManager::class.java) ?: return
            val cameraId = findFlashCameraId(manager) ?: return
            cameraManager = manager
            torchCameraId = cameraId
            manager.registerTorchCallback(torchCallback, mainHandler)
        }
        observeCount++
    }

    fun stopObserving() {
        if (observeCount <= 0) return
        observeCount--
        if (observeCount == 0) {
            runCatching { cameraManager?.unregisterTorchCallback(torchCallback) }
            cameraManager = null
            torchCameraId = null
        }
    }

    fun toggle(context: Context): Boolean {
        val app = context.applicationContext
        if (observeCount == 0) {
            startObserving(app)
        }
        val manager = cameraManager
            ?: app.getSystemService(CameraManager::class.java)?.also { cameraManager = it }
            ?: return false
        val cameraId = torchCameraId ?: findFlashCameraId(manager).also { torchCameraId = it } ?: return false
        val target = !torchOn
        return runCatching {
            manager.setTorchMode(cameraId, target)
            setTorchState(target)
            true
        }.getOrDefault(false)
    }

    private fun setTorchState(enabled: Boolean) {
        if (torchOn == enabled) return
        torchOn = enabled
        if (Looper.myLooper() == Looper.getMainLooper()) {
            notifyListeners(enabled)
        } else {
            mainHandler.post { notifyListeners(enabled) }
        }
    }

    private fun notifyListeners(enabled: Boolean) {
        listeners.forEach { listener -> listener(enabled) }
    }

    private fun findFlashCameraId(manager: CameraManager): String? =
        manager.cameraIdList.firstOrNull { id ->
            manager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
}
