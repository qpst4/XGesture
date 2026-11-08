package com.slideindex.app.search

import com.slideindex.app.settings.SearchEngineConfig
import com.slideindex.app.settings.SearchEngineType

object SearchEngineValidator {
    fun validate(engine: SearchEngineConfig): Boolean {
        if (engine.name.isBlank()) return false
        return when (engine.engineType) {
            SearchEngineType.DIRECT_LINK,
            SearchEngineType.EXTERN_JUMP_LINK ->
                !engine.searchLink.isNullOrBlank() || !engine.externJumpLink.isNullOrBlank()
            SearchEngineType.JUMP_TO_ACTIVITY ->
                !engine.targetPackage.isNullOrBlank()
            SearchEngineType.SHARE_TO_APP ->
                !engine.targetPackage.isNullOrBlank() && !engine.targetActivity.isNullOrBlank()
            SearchEngineType.SHARE_IMAGE_TO_APP ->
                !engine.targetPackage.isNullOrBlank() && !engine.targetActivity.isNullOrBlank()
            SearchEngineType.SHARE_FILE_TO_APP ->
                !engine.targetPackage.isNullOrBlank()
        }
    }
}
