package com.slideindex.app.search

import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.slideindex.app.R
import com.slideindex.app.settings.SearchEngineConfig
import com.slideindex.app.settings.SearchEngineType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class SearchEngineShareEnvelope(
    val schema: String = SearchEngineShareCodec.SCHEMA,
    val version: Int = 1,
    val engines: List<SearchEngineConfig> = emptyList(),
)

/**
 * 本应用自己的引擎/搜索组交换格式。
 *
 * 组分享时 members 会作为普通引擎一起放进 [SearchEngineShareEnvelope.engines]；
 * 导入方先给所有引擎生成新 id，再 remap 组的 groupMemberIds。
 */
object SearchEngineShareCodec {
    const val SCHEMA = "xgesture.search-engine"

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun encode(engines: List<SearchEngineConfig>): String =
        json.encodeToString(
            SearchEngineShareEnvelope.serializer(),
            SearchEngineShareEnvelope(engines = engines),
        )

    fun decode(raw: String): List<SearchEngineConfig>? {
        val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()
            ?: return null
        if (root["schema"]?.jsonPrimitive?.content != SCHEMA) return null
        return runCatching {
            json.decodeFromJsonElement(
                SearchEngineShareEnvelope.serializer(),
                root,
            ).engines
        }.getOrNull()
    }
}

object SearchEngineShare {
    fun share(
        context: Context,
        engine: SearchEngineConfig,
        allEngines: List<SearchEngineConfig>,
    ): Boolean {
        val members = if (engine.engineType == SearchEngineType.SEARCH_GROUP) {
            val byId = allEngines.associateBy { it.id }
            engine.groupMemberIds.mapNotNull { id ->
                byId[id]?.takeIf { it.engineType != SearchEngineType.SEARCH_GROUP }
            }
        } else {
            emptyList()
        }
        if (engine.engineType == SearchEngineType.SEARCH_GROUP &&
            members.size != engine.groupMemberIds.size
        ) {
            Toast.makeText(
                context,
                R.string.search_group_share_missing_members,
                Toast.LENGTH_SHORT,
            ).show()
            return false
        }
        val payload = buildList {
            add(engine)
            addAll(members.filter { it.id != engine.id })
        }
        val text = SearchEngineShareCodec.encode(payload)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.search_engine_share_subject))
            putExtra(Intent.EXTRA_TEXT, text)
        }
        return runCatching {
            context.startActivity(
                Intent.createChooser(
                    send,
                    context.getString(R.string.search_engine_share_chooser),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            true
        }.getOrElse {
            Toast.makeText(
                context,
                R.string.search_engine_share_failed,
                Toast.LENGTH_SHORT,
            ).show()
            false
        }
    }
}
