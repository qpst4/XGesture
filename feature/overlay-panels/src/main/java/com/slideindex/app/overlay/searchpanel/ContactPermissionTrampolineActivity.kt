package com.slideindex.app.overlay.searchpanel

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.slideindex.app.search.contacts.ContactSearchIndex
import com.slideindex.app.util.finishWithoutTransition

class ContactPermissionTrampolineActivity : ComponentActivity() {

    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            ContactSearchIndex.invalidateCache()
        }
        onPermissionResult?.invoke(isGranted)
        onPermissionResult = null
        finishWithoutTransition()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermission.launch(Manifest.permission.READ_CONTACTS)
    }

    companion object {
        var onPermissionResult: ((Boolean) -> Unit)? = null

        fun launch(context: Context, onResult: (Boolean) -> Unit) {
            onPermissionResult = onResult
            val intent = Intent(context, ContactPermissionTrampolineActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
