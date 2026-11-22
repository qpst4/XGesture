package com.slideindex.app.ui.navigation

import android.app.Application
import com.slideindex.app.ui.MainBottomNavDestination
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import sun.misc.Unsafe
import top.yukonga.miuix.kmp.nav.core.NavEntryBuilder

/** Resolve the actual tab graph, without composing screens or starting their dependencies. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = Application::class)
class LinkDispatchNavigationAcceptanceTest {
    @Suppress("UNCHECKED_CAST")
    private fun provider(tab: MainBottomNavDestination): (AppNavKey) -> Any {
        val builder = NavEntryBuilder()
        // Registration only captures ctx in screen lambdas; the host is never accessed here.
        val unsafe = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null) as Unsafe
        val context = unsafe.allocateInstance(MainNavContext::class.java) as MainNavContext
        Class.forName("com.slideindex.app.ui.navigation.MainNavHostKt")
            .getDeclaredMethod("registerMainTabNavEntries", NavEntryBuilder::class.java,
                MainBottomNavDestination::class.java, MainNavContext::class.java)
            .apply { isAccessible = true }
            .invoke(null, builder, tab, context)
        // NavEntry is Kotlin-internal in miuix; only resolution success matters here.
        return NavEntryBuilder::class.java.getMethod("build").invoke(builder) as (AppNavKey) -> Any
    }

    @Test fun linkDispatchCanOpenFromHomePickSettings() {
        assertNotNull(provider(MainBottomNavDestination.Home)(AppNavKey.LinkDispatchSettings))
    }

    @Test fun linkDispatchStillOpensFromExtensionHub() {
        assertNotNull(provider(MainBottomNavDestination.Extension)(AppNavKey.LinkDispatchSettings))
    }
}
