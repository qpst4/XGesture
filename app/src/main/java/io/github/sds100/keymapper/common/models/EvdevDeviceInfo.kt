package io.github.sds100.keymapper.common.models

data class EvdevDeviceInfo(
    @JvmField val name: String,
    @JvmField val bus: Int,
    @JvmField val vendor: Int,
    @JvmField val product: Int,
)
