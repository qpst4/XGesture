/*
 * Derived from Key Mapper (https://github.com/keymapperorg/KeyMapper)
 * Copyright (C) sds100 and contributors.
 * Licensed under GNU General Public License v3.0.
 */

package io.github.sds100.keymapper.sysbridge.service

import android.util.Log
import io.github.sds100.keymapper.common.models.EvdevDeviceInfo
import io.github.sds100.keymapper.common.models.GrabTargetKeyCode
import io.github.sds100.keymapper.common.models.GrabbedDeviceHandle

/**
 * 原生 KeyMapper evdev_manager 引擎 JNI 桥接器。
 * 绑定至 libevdev_manager.so，实现 Linux 内核级 EVIOCGRAB 独占抓取与 /dev/uinput 虚拟回写。
 */
class SystemBridge {
    interface Listener {
        fun onEvdevEvent(
            deviceId: Int,
            timeSec: Long,
            timeUsec: Long,
            type: Int,
            code: Int,
            value: Int,
            androidCode: Int,
        ): Boolean

        fun onGrabbedDevicesChanged(devices: Array<GrabbedDeviceHandle>) {}
        fun onEvdevDevicesChanged(devices: Array<EvdevDeviceInfo>) {}
        fun onEmergencyKill() {}
        fun onLog(level: Int, message: String) {}
    }

    @Volatile
    var listener: Listener? = null

    external fun initEvdevManager()
    external fun destroyEvdevManager()
    external fun setGrabTargetsNative(devices: Array<GrabTargetKeyCode>): Array<GrabbedDeviceHandle>
    external fun writeEvdevEventNative(deviceId: Int, type: Int, code: Int, value: Int): Boolean
    external fun writeEvdevEventKeyCodeNative(deviceId: Int, keyCode: Int, value: Int): Boolean
    external fun getEvdevDevicesNative(): Array<EvdevDeviceInfo>
    external fun setLogLevelNative(level: Int)
    external fun setEmergencyStopEnabledNative(enabled: Boolean)

    /**
     * 由 Rust 底层在事件到来时通过 JNI 调用。
     * @return true 消费并丢弃事件（彻底阻断）；false 由底层 uinput 自动回写给系统。
     */
    fun onEvdevEvent(
        deviceId: Int,
        timeSec: Long,
        timeUsec: Long,
        type: Int,
        code: Int,
        value: Int,
        androidCode: Int,
    ): Boolean {
        return listener?.onEvdevEvent(deviceId, timeSec, timeUsec, type, code, value, androidCode) ?: false
    }

    fun onGrabbedDevicesChanged(devices: Array<GrabbedDeviceHandle>) {
        listener?.onGrabbedDevicesChanged(devices)
    }

    fun onEvdevDevicesChanged(devices: Array<EvdevDeviceInfo>) {
        listener?.onEvdevDevicesChanged(devices)
    }

    fun onEmergencyKillSystemBridge() {
        Log.w(TAG, "onEmergencyKillSystemBridge received from Rust")
        listener?.onEmergencyKill()
    }

    fun onLogMessage(level: Int, message: String) {
        listener?.onLog(level, message)
    }

    companion object {
        private const val TAG = "KeyMapperSystemBridge"

        @Volatile
        private var isLoaded = false

        private val instanceLock = Any()

        @Volatile
        private var sharedBridge: SystemBridge? = null

        fun getOrCreate(): SystemBridge? {
            if (!loadLibrary()) return null
            synchronized(instanceLock) {
                var bridge = sharedBridge
                if (bridge == null) {
                    val newBridge = SystemBridge()
                    val initOk = runCatching {
                        newBridge.initEvdevManager()
                        newBridge.setLogLevelNative(3)
                        newBridge.setEmergencyStopEnabledNative(true)
                        true
                    }.onFailure {
                        Log.w(TAG, "failed to initEvdevManager: ${it.message}")
                    }.getOrDefault(false)

                    if (initOk) {
                        Log.i(TAG, "evdev_manager initialized successfully for process")
                        sharedBridge = newBridge
                        bridge = newBridge
                    }
                }
                return bridge
            }
        }

        fun loadLibrary(): Boolean {
            if (isLoaded) return true

            // 1. 优先尝试系统常规加载
            if (runCatching { System.loadLibrary("evdev_manager"); true }.getOrDefault(false)) {
                isLoaded = true
                Log.i(TAG, "libevdev_manager loaded via System.loadLibrary")
                return true
            }

            // 2. 检查常见已存在路径
            val candidatePaths = listOf(
                "/data/local/tmp/libevdev_manager.so",
                "/data/local/tmp/libevdev_manager_extracted.so",
                "/data/data/com.slideindex.app/cache/libevdev_manager.so",
            )
            for (p in candidatePaths) {
                val f = java.io.File(p)
                if (f.exists() && f.canRead()) {
                    if (runCatching { System.load(f.absolutePath); true }.getOrDefault(false)) {
                        isLoaded = true
                        Log.i(TAG, "libevdev_manager loaded from candidate $p")
                        return true
                    }
                }
            }

            // 3. 从独立进程启动时的 classpath APK 中自动解压 native 库
            return runCatching {
                val cacheFile = java.io.File("/data/local/tmp/libevdev_manager_extracted.so")
                if (!cacheFile.exists() || cacheFile.length() == 0L) {
                    val classPath = System.getProperty("java.class.path") ?: ""
                    for (part in classPath.split(':')) {
                        val partFile = java.io.File(part)
                        if (part.endsWith(".apk") && partFile.exists()) {
                            val zip = java.util.zip.ZipFile(partFile)
                            val entry = zip.getEntry("lib/arm64-v8a/libevdev_manager.so")
                                ?: zip.getEntry("lib/armeabi-v7a/libevdev_manager.so")
                            if (entry != null) {
                                zip.getInputStream(entry).use { input ->
                                    java.io.FileOutputStream(cacheFile).use { output ->
                                        input.copyTo(output)
                                    }
                                }
                                cacheFile.setReadable(true, false)
                                cacheFile.setExecutable(true, false)
                                break
                            }
                        }
                    }
                }
                if (cacheFile.exists() && cacheFile.length() > 0L) {
                    System.load(cacheFile.absolutePath)
                    isLoaded = true
                    Log.i(TAG, "libevdev_manager loaded successfully from ${cacheFile.absolutePath}")
                    true
                } else {
                    Log.w(TAG, "unable to locate or extract libevdev_manager.so from classpath")
                    false
                }
            }.onFailure {
                Log.w(TAG, "failed to load libevdev_manager: ${it.message}")
            }.getOrDefault(false)
        }
    }
}
