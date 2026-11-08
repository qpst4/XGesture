package com.slideindex.app.overlay.searchpanel

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.slideindex.app.util.finishWithoutTransition

class SearchPanelImagePickerActivity : ComponentActivity() {

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        onImagePicked?.invoke(uri)
        onImagePicked = null
        finishWithoutTransition()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pickImage.launch("image/*")
    }

    companion object {
        var onImagePicked: ((Uri?) -> Unit)? = null

        fun launch(context: Context, onPicked: (Uri?) -> Unit) {
            onImagePicked = onPicked
            val intent = Intent(context, SearchPanelImagePickerActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
