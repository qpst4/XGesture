package com.slideindex.app.search.contacts

/**
 * Portions derived from Quick Search (https://github.com/teja2495/quick-search)
 * Licensed under MIT. Modified for com.slideindex.app.
 */

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.net.toUri

object ContactSearchLauncher {
    /**
     * Opens contact via [Intent.ACTION_VIEW] on lookup URI — same as Quick Search
     * ([CallSmsActions.openContact]). Do not use [Intent.createChooser]; MIUI/HyperOS shows
     * its own resolver for contact links.
     */
    fun openContact(context: Context, contact: ContactSearchEntry): Boolean {
        if (contact.lookupKey.isBlank()) return false
        val lookupUri = ContactsContract.Contacts.getLookupUri(contact.id, contact.lookupKey)
            ?: return false
        val intent = Intent(Intent.ACTION_VIEW, lookupUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    fun dial(context: Context, phoneNumber: String): Boolean {
        if (phoneNumber.isBlank()) return false
        val intent = Intent(Intent.ACTION_DIAL, "tel:$phoneNumber".toUri()).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { context.startActivity(intent); true }.getOrDefault(false)
    }

    fun sms(context: Context, phoneNumber: String): Boolean {
        if (phoneNumber.isBlank()) return false
        val intent = Intent(Intent.ACTION_SENDTO, "smsto:$phoneNumber".toUri()).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { context.startActivity(intent); true }.getOrDefault(false)
    }
}
