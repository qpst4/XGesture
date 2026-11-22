package com.slideindex.app.xposed.bridge

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class ModuleGestureBridgeClientLifecycleTest {
    @Test
    fun retiringGeneration_unbindsConnectedBridge_andDoesNotReconnect() {
        val context = BindingContext()
        val client = ModuleGestureBridgeClient(context)
        client.ensureBound(force = true)
        context.connect()
        assertTrue(client.isConnected)

        client.close()
        client.close()
        client.ensureBound(force = true)

        assertFalse(client.isConnected)
        assertEquals(1, context.binds)
        assertEquals(1, context.unbinds)
    }

    @Test
    fun retiringWhileBinding_ignoresLateConnectionCallback() {
        val context = BindingContext()
        val client = ModuleGestureBridgeClient(context)
        client.ensureBound(force = true)
        client.close()
        context.connect()

        assertFalse(client.isConnected)
        assertEquals(1, context.binds)
    }

    @Test
    fun retiringUnusedGeneration_doesNotUnbindAnotherConnection() {
        val context = BindingContext()
        ModuleGestureBridgeClient(context).close()
        assertEquals(0, context.unbinds)
    }

    private class BindingContext : ContextWrapper(RuntimeEnvironment.getApplication()) {
        var binds = 0
        var unbinds = 0
        private lateinit var connection: ServiceConnection
        override fun bindService(intent: Intent, conn: ServiceConnection, flags: Int): Boolean {
            binds++
            connection = conn
            return true
        }
        override fun unbindService(conn: ServiceConnection) { unbinds++ }
        fun connect() = connection.onServiceConnected(ComponentName(packageName, "bridge"), Binder())
    }
}
