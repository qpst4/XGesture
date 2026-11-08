package com.slideindex.app.search.contacts

/**
 * Portions derived from Quick Search (https://github.com/teja2495/quick-search)
 * Licensed under MIT. Modified for com.slideindex.app.
 */

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.slideindex.app.util.PinyinHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ContactSearchIndex {

    @Volatile
    private var cachedContacts: List<ContactSearchEntry>? = null
    @Volatile
    private var lastLoadTime: Long = 0L

    private const val CACHE_EXPIRATION_MS = 60_000L

    fun hasPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun loadContacts(context: Context, force: Boolean = false): List<ContactSearchEntry> {
        if (!hasPermission(context)) return emptyList()

        val currentTime = System.currentTimeMillis()
        val existing = cachedContacts
        if (!force && existing != null && (currentTime - lastLoadTime < CACHE_EXPIRATION_MS)) {
            return existing
        }

        return withContext(Dispatchers.IO) {
            val contacts = queryContacts(context)
            cachedContacts = contacts
            lastLoadTime = System.currentTimeMillis()
            contacts
        }
    }

    suspend fun search(
        context: Context,
        query: String,
        limit: Int = 5
    ): List<ContactSearchEntry> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        val contacts = loadContacts(context)
        if (contacts.isEmpty()) return emptyList()

        val normalizedQuery = trimmed.lowercase()
        val digitsQuery = trimmed.filter { it.isDigit() }

        return withContext(Dispatchers.Default) {
            contacts
                .mapNotNull { entry ->
                    score(entry, normalizedQuery, digitsQuery)?.let { entry to it }
                }
                .sortedWith(
                    compareByDescending<Pair<ContactSearchEntry, Int>> { it.second }
                        .thenBy { it.first.name.length }
                )
                .map { it.first }
                .take(limit)
        }
    }

    /**
     * 拼音按前缀匹配：全拼前缀、或各字首字母缩写前缀。
     * 不对全拼/首字母做任意位置 substring，避免输入 `u` 误匹配 `songguang`。
     * 显示名仍允许 contains（便于中文名中间字、英文名片段）。
     */
    internal fun score(
        entry: ContactSearchEntry,
        normalizedQuery: String,
        digitsQuery: String
    ): Int? {
        val nameLower = entry.name.lowercase()
        var score = 0

        if (nameLower == normalizedQuery ||
            entry.initialPinyin == normalizedQuery ||
            entry.fullPinyin == normalizedQuery
        ) {
            score = maxOf(score, 140)
        } else if (
            nameLower.startsWith(normalizedQuery) ||
            entry.initialPinyin.startsWith(normalizedQuery) ||
            entry.fullPinyin.startsWith(normalizedQuery)
        ) {
            score = maxOf(score, 120)
        } else if (nameLower.contains(normalizedQuery)) {
            score = maxOf(score, 80)
        }

        if (digitsQuery.isNotEmpty() && entry.phoneNumber.isNotEmpty()) {
            if (entry.phoneNumber == digitsQuery) {
                score = maxOf(score, 150)
            } else if (entry.phoneNumber.startsWith(digitsQuery)) {
                score = maxOf(score, 110)
            } else if (entry.phoneNumber.contains(digitsQuery)) {
                score = maxOf(score, 70)
            }
        }

        return if (score > 0) score else null
    }

    private fun queryContacts(context: Context): List<ContactSearchEntry> {
        val results = mutableListOf<ContactSearchEntry>()
        val photoByContactId = loadContactPhotoUris(context)
        val lookupKeyByContactId = loadContactLookupKeys(context)
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val seen = mutableSetOf<String>()

        runCatching {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                while (cursor.moveToNext()) {
                    val id = if (idIdx >= 0) cursor.getLong(idIdx) else 0L
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx).orEmpty().trim() else ""
                    val rawNumber = if (numberIdx >= 0) cursor.getString(numberIdx).orEmpty().trim() else ""
                    if (name.isEmpty() || rawNumber.isEmpty()) continue

                    val cleanNumber = rawNumber.filter { it.isDigit() }
                    val uniqueKey = "$name-$cleanNumber"
                    if (!seen.add(uniqueKey)) continue

                    val fullPinyin = PinyinHelper.sortKey(name)
                    val initialPinyin = PinyinHelper.initialKey(name)

                    results.add(
                        ContactSearchEntry(
                            id = id,
                            lookupKey = lookupKeyByContactId[id].orEmpty(),
                            name = name,
                            phoneNumber = cleanNumber,
                            formattedPhone = rawNumber,
                            fullPinyin = fullPinyin,
                            initialPinyin = initialPinyin,
                            photoUri = photoByContactId[id],
                        )
                    )
                }
            }
        }

        return results
    }

    private fun loadContactPhotoUris(context: Context): Map<Long, String> {
        val map = HashMap<Long, String>()
        runCatching {
            context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(
                    ContactsContract.Contacts._ID,
                    ContactsContract.Contacts.PHOTO_URI,
                ),
                null,
                null,
                null,
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(ContactsContract.Contacts._ID)
                val photoIdx = cursor.getColumnIndex(ContactsContract.Contacts.PHOTO_URI)
                while (cursor.moveToNext()) {
                    if (idIdx < 0) continue
                    val id = cursor.getLong(idIdx)
                    val photo = if (photoIdx >= 0) cursor.getString(photoIdx)?.trim().orEmpty() else ""
                    if (photo.isNotEmpty()) {
                        map[id] = photo
                    }
                }
            }
        }
        return map
    }

    private fun loadContactLookupKeys(context: Context): Map<Long, String> {
        val map = HashMap<Long, String>()
        runCatching {
            context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(
                    ContactsContract.Contacts._ID,
                    ContactsContract.Contacts.LOOKUP_KEY,
                ),
                null,
                null,
                null,
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(ContactsContract.Contacts._ID)
                val lookupIdx = cursor.getColumnIndex(ContactsContract.Contacts.LOOKUP_KEY)
                while (cursor.moveToNext()) {
                    if (idIdx < 0 || lookupIdx < 0) continue
                    val id = cursor.getLong(idIdx)
                    val lookupKey = cursor.getString(lookupIdx)?.trim().orEmpty()
                    if (lookupKey.isNotEmpty()) {
                        map[id] = lookupKey
                    }
                }
            }
        }
        return map
    }

    fun invalidateCache() {
        cachedContacts = null
        lastLoadTime = 0L
    }
}
