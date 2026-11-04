package com.slideindex.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/** Activity 级共享：编辑页与挑应用/挑 Activity 页之间传递选择结果。 */
data class ActivityShortcutPickerResult(
    val packageName: String? = null,
    val activityClassName: String? = null,
    val iconPackageName: String? = null,
)

@HiltViewModel
class ActivityShortcutPickerViewModel @Inject constructor() : ViewModel() {
    private val _result = MutableStateFlow<ActivityShortcutPickerResult?>(null)
    val result: StateFlow<ActivityShortcutPickerResult?> = _result.asStateFlow()

    fun pickPackage(packageName: String) {
        _result.value = ActivityShortcutPickerResult(packageName = packageName)
    }

    fun pickActivity(packageName: String, activityClassName: String) {
        _result.value = ActivityShortcutPickerResult(
            packageName = packageName,
            activityClassName = activityClassName,
        )
    }

    fun pickIconPackage(packageName: String) {
        _result.value = ActivityShortcutPickerResult(iconPackageName = packageName)
    }

    fun consume() {
        _result.value = null
    }
}
