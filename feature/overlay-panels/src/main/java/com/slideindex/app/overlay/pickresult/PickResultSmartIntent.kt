package com.slideindex.app.overlay.pickresult

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.ui.graphics.vector.ImageVector
import com.slideindex.app.overlay.panels.R

sealed class PickResultSmartEntity {
    abstract val label: String
    /** 去重与 Compose key 用的稳定标识，默认与展示标签一致。 */
    open val entityKey: String get() = label
    abstract val actionPrefixResId: Int
    abstract val icon: ImageVector
    abstract fun execute(context: Context, onCopyText: (String) -> Unit)

    data class UrlEntity(
        val url: String,
        val host: String
    ) : PickResultSmartEntity() {
        override val icon: ImageVector get() = Icons.Outlined.Language
        override val actionPrefixResId: Int get() = R.string.float_ball_pick_smart_open_url
        override val label: String get() = host
        override val entityKey: String get() = url
        override fun execute(context: Context, onCopyText: (String) -> Unit) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                onCopyText(url)
            }
        }
    }

    data class EcommerceEntity(
        val rawToken: String,
        val appName: String,
        val packageName: String?
    ) : PickResultSmartEntity() {
        override val icon: ImageVector get() = Icons.Outlined.ShoppingCart
        override val actionPrefixResId: Int get() = R.string.float_ball_pick_smart_open_ecommerce
        override val label: String get() = appName
        override fun execute(context: Context, onCopyText: (String) -> Unit) {
            onCopyText(rawToken)
            if (!packageName.isNullOrBlank()) {
                try {
                    val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(launchIntent)
                        return
                    }
                } catch (_: Exception) {
                }
            }
            Toast.makeText(context, context.getString(R.string.float_ball_pick_smart_copied_toast), Toast.LENGTH_SHORT).show()
        }
    }

    data class PhoneEntity(
        val phoneNumber: String
    ) : PickResultSmartEntity() {
        override val icon: ImageVector get() = Icons.Outlined.Phone
        override val actionPrefixResId: Int get() = R.string.float_ball_pick_smart_call_phone
        override val label: String get() = phoneNumber
        override fun execute(context: Context, onCopyText: (String) -> Unit) {
            try {
                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                onCopyText(phoneNumber)
            }
        }
    }

    data class ExpressEntity(
        val trackingNumber: String,
        val companyName: String?
    ) : PickResultSmartEntity() {
        override val icon: ImageVector get() = Icons.Outlined.LocalShipping
        override val actionPrefixResId: Int get() = R.string.float_ball_pick_smart_track_express
        override val label: String get() = companyName?.let { "$it $trackingNumber" } ?: trackingNumber
        override fun execute(context: Context, onCopyText: (String) -> Unit) {
            try {
                val query = companyName?.let { "$it $trackingNumber" } ?: trackingNumber
                val searchUrl = "https://www.baidu.com/s?wd=${Uri.encode(query)}"
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(searchUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                onCopyText(trackingNumber)
            }
        }
    }

    data class VerifyCodeEntity(
        val code: String
    ) : PickResultSmartEntity() {
        override val icon: ImageVector get() = Icons.Outlined.Password
        override val actionPrefixResId: Int get() = R.string.float_ball_pick_smart_copy_code
        override val label: String get() = code
        override fun execute(context: Context, onCopyText: (String) -> Unit) {
            onCopyText(code)
            Toast.makeText(context, context.getString(R.string.float_ball_pick_smart_copied_toast), Toast.LENGTH_SHORT).show()
        }
    }
}

object PickResultSmartParser {
    private val phoneRegex = Regex("""(?<!\d)(1[3-9]\d{9}|0\d{2,3}-?\d{7,8})(?!\d)""")
    private val verifyCodeRegex = Regex("""(?i)(?:验证码|校验码|动态码|code)[:：\s]{0,6}([0-9]{4,6})(?!\d)""")
    private val tbTokenRegex = Regex("""(￥[a-zA-Z0-9]{8,18}￥|[a-zA-Z0-9]{11}:/)""")
    private val sfExpressRegex = Regex("""(?i)(?:SF\d{10,15})""")
    private val expressKeywordRegex = Regex("""(?i)(?:快递|单号|运单号|运单|包裹)[:：\s]{0,4}([a-zA-Z0-9]{10,20})""")

    fun parseSmartEntities(text: String?): List<PickResultSmartEntity> {
        if (text.isNullOrBlank()) return emptyList()
        val entities = mutableListOf<PickResultSmartEntity>()

        // 1. 验证码优先检测（短消息中最迫切）
        verifyCodeRegex.find(text)?.let { match ->
            val code = match.groupValues.getOrNull(1)
            if (!code.isNullOrBlank()) {
                entities.add(PickResultSmartEntity.VerifyCodeEntity(code))
            }
        }

        // 2. 电商口令 / 淘口令
        if (tbTokenRegex.containsMatchIn(text) || text.contains("tb.cn") || text.contains("taobao.com")) {
            val token = tbTokenRegex.find(text)?.value ?: text.trim()
            entities.add(
                PickResultSmartEntity.EcommerceEntity(
                    rawToken = token,
                    appName = "淘宝",
                    packageName = "com.taobao.taobao"
                )
            )
        } else if (text.contains("jd.com") || text.contains("jingdong.com")) {
            entities.add(
                PickResultSmartEntity.EcommerceEntity(
                    rawToken = text.trim(),
                    appName = "京东",
                    packageName = "com.jingdong.app.mall"
                )
            )
        }

        // 3. 电话号码
        phoneRegex.findAll(text).take(2).forEach { match ->
            val number = match.value
            if (entities.none { it is PickResultSmartEntity.PhoneEntity && it.phoneNumber == number }) {
                entities.add(PickResultSmartEntity.PhoneEntity(number))
            }
        }

        // 4. 快递单号
        sfExpressRegex.find(text)?.let { match ->
            entities.add(PickResultSmartEntity.ExpressEntity(match.value, "顺丰"))
        } ?: run {
            expressKeywordRegex.find(text)?.let { match ->
                val num = match.groupValues.getOrNull(1)
                if (!num.isNullOrBlank()) {
                    entities.add(PickResultSmartEntity.ExpressEntity(num, null))
                }
            }
        }

        // 5. 网页 URL
        val urls = PickResultUrl.extractOpenableUrls(text).take(MAX_URL_ENTITIES)
        val labels = PickResultUrl.linkDisplayLabels(urls)
        urls.forEachIndexed { index, url ->
            entities.add(PickResultSmartEntity.UrlEntity(url = url, host = labels[index]))
        }

        return entities.distinctBy { it.entityKey }
    }

    private const val MAX_URL_ENTITIES = 5
}
