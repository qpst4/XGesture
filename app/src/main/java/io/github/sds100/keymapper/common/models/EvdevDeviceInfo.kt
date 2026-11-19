/*
 * Derived from Key Mapper (https://github.com/keymapperorg/KeyMapper)
 * Copyright (C) sds100 and contributors.
 * Licensed under GNU General Public License v3.0.
 */

package io.github.sds100.keymapper.common.models

data class EvdevDeviceInfo(
    @JvmField val name: String,
    @JvmField val bus: Int,
    @JvmField val vendor: Int,
    @JvmField val product: Int,
)
