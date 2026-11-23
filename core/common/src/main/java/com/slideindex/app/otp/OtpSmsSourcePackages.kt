package com.slideindex.app.otp

/** Packages that surface carrier SMS as system notifications. */
object OtpSmsSourcePackages {
    private val KNOWN_PACKAGES = setOf(
        "com.android.phone",
        "com.android.providers.telephony",
        "com.android.mms",
        "com.android.messaging",
        "com.google.android.apps.messaging",
        "com.samsung.android.messaging",
        "com.textra",
        "com.huawei.message",
        "com.coloros.mms",
        "com.vivo.mms",
        "com.meizu.flyme.mms",
        "com.meizu.mms",
        // MIUI / HyperOS may surface carrier SMS under a vendor-specific package.
        "com.miui.mms",
        "com.miui.sms",
        "com.xiaomi.mms",
    )

    fun isSystemSmsPackage(packageName: String): Boolean {
        val base = packageName.substringBefore(':')
        return base in KNOWN_PACKAGES
    }
}
