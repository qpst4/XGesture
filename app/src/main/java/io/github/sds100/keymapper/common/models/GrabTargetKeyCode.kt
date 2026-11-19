/*
 * Derived from Key Mapper (https://github.com/keymapperorg/KeyMapper)
 * Copyright (C) sds100 and contributors.
 * Licensed under GNU General Public License v3.0.
 */

package io.github.sds100.keymapper.common.models

data class GrabTargetKeyCode(
    @JvmField val name: String,
    @JvmField val bus: Int,
    @JvmField val vendor: Int,
    @JvmField val product: Int,
    @JvmField val extraKeyCodes: IntArray = IntArray(0),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GrabTargetKeyCode) return false
        if (name != other.name) return false
        if (bus != other.bus) return false
        if (vendor != other.vendor) return false
        if (product != other.product) return false
        if (!extraKeyCodes.contentEquals(other.extraKeyCodes)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + bus
        result = 31 * result + vendor
        result = 31 * result + product
        result = 31 * result + extraKeyCodes.contentHashCode()
        return result
    }
}
