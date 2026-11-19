package com.slideindex.app.ui

import android.content.Context
import androidx.compose.runtime.Composable
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.settings.AppCarouselListSource
import com.slideindex.app.settings.AppSettings

@Composable
fun gestureActionLabel(action: GestureAction, settings: AppSettings? = null): String =
    com.slideindex.app.ui.gesturepicker.gestureActionLabel(action, settings)

@Composable
fun gestureActionSettingSubtitle(action: GestureAction): String =
    com.slideindex.app.ui.gesturepicker.gestureActionSettingSubtitle(action)

@Composable
fun gestureActionDescription(action: GestureAction): String? =
    com.slideindex.app.ui.gesturepicker.gestureActionDescription(action)

@Composable
fun gestureActionPermissionHint(
    action: GestureAction,
    context: Context,
    appCarouselListSource: AppCarouselListSource? = null,
): String? =
    com.slideindex.app.ui.gesturepicker.gestureActionPermissionHint(
        action,
        context,
        appCarouselListSource,
    )

@Composable
fun gestureActionRequirementHint(action: GestureAction): String? =
    com.slideindex.app.ui.gesturepicker.gestureActionRequirementHint(action)

fun filterGestureActions(
    context: Context,
    actions: List<GestureAction>,
    query: String
): List<GestureAction> = com.slideindex.app.ui.gesturepicker.filterGestureActions(context, actions, query)

fun requestPermissionForAdjustAction(context: Context, action: GestureAction) =
    com.slideindex.app.ui.gesturepicker.requestPermissionForAdjustAction(context, action)
