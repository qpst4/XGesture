package com.slideindex.app.freezer

import android.content.Context
import android.widget.Toast
import com.slideindex.app.R
import com.slideindex.app.data.AppRepository
import com.slideindex.app.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object FreezerListOperations {
    suspend fun removeFromList(
        context: Context,
        settingsRepository: SettingsRepository,
        packageName: String,
        appRepository: AppRepository? = null
    ): Boolean {
        when (FreezerOperations.stateOf(context, packageName)) {
            FreezerAppState.FROZEN -> {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, R.string.freezer_remove_while_frozen, Toast.LENGTH_SHORT).show()
                }
                return false
            }
            FreezerAppState.PAUSED -> {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, R.string.freezer_remove_while_paused, Toast.LENGTH_SHORT).show()
                }
                return false
            }
            FreezerAppState.ACTIVE -> Unit
        }
        settingsRepository.removeFreezerApp(packageName)
        appRepository?.invalidate()
        return true
    }

    /** 解冻或取消暂停之后再移出列表（对应菜单里的「解冻并移除」/「取消暂停并移除」）。 */
    suspend fun restoreAndRemoveFromList(
        context: Context,
        settingsRepository: SettingsRepository,
        packageName: String,
        appRepository: AppRepository? = null
    ): Boolean {
        when (FreezerOperations.stateOf(context, packageName)) {
            FreezerAppState.FROZEN ->
                if (!FreezerOperations.setFrozen(context, packageName, frozen = false)) return false
            FreezerAppState.PAUSED ->
                if (!FreezerOperations.setPaused(context, packageName, paused = false)) return false
            FreezerAppState.ACTIVE -> Unit
        }
        settingsRepository.removeFreezerApp(packageName)
        appRepository?.invalidate()
        return true
    }

    suspend fun importFrozenApps(
        context: Context,
        settingsRepository: SettingsRepository
    ): Int {
        val scanned = FreezerBootstrap.scanImportableLauncherPackages(context)
        if (scanned.isEmpty()) return 0
        val current = settingsRepository.readFreshSnapshot().freezerAppPackages
        val toAdd = scanned - current
        toAdd.forEach { packageName ->
            settingsRepository.addFreezerApp(packageName)
            // 导入的包本来就带着冻结 / 暂停状态：补一条意图，否则手势会按全局模式猜一个档位。
            val intent = FreezerBootstrap.intentForState(FreezerOperations.stateOf(context, packageName))
                ?: return@forEach
            settingsRepository.setFreezerAppIntent(packageName, intent)
        }
        return toAdd.size
    }
}
