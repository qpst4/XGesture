package com.slideindex.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.physicalkey.PhysicalKeyGesture

internal fun physicalKeyGestureLabelRes(gesture: PhysicalKeyGesture): Int = when (gesture) {
    PhysicalKeyGesture.POWER_SINGLE_PRESS -> R.string.physical_key_gesture_power_single
    PhysicalKeyGesture.POWER_LONG_PRESS -> R.string.physical_key_gesture_power_long
    PhysicalKeyGesture.POWER_DOUBLE_PRESS -> R.string.physical_key_gesture_power_double
    PhysicalKeyGesture.VOLUME_UP_SINGLE_PRESS -> R.string.physical_key_gesture_vol_up_single
    PhysicalKeyGesture.VOLUME_UP_LONG_PRESS -> R.string.physical_key_gesture_vol_up_long
    PhysicalKeyGesture.VOLUME_UP_DOUBLE_PRESS -> R.string.physical_key_gesture_vol_up_double
    PhysicalKeyGesture.VOLUME_DOWN_SINGLE_PRESS -> R.string.physical_key_gesture_vol_down_single
    PhysicalKeyGesture.VOLUME_DOWN_LONG_PRESS -> R.string.physical_key_gesture_vol_down_long
    PhysicalKeyGesture.VOLUME_DOWN_DOUBLE_PRESS -> R.string.physical_key_gesture_vol_down_double
    PhysicalKeyGesture.VOLUME_BOTH_PRESS -> R.string.physical_key_gesture_vol_both_press
}

@Composable
internal fun PhysicalKeyGesture.displayName(): String = stringResource(physicalKeyGestureLabelRes(this))
