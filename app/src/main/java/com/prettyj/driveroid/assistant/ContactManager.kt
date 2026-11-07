package com.prettyj.driveroid.assistant

import android.content.Context
import android.database.Cursor
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import java.util.Locale

/**
 * Resolves a contact name to a phone number using the device's contacts provider.
 *
 * Requires [android.Manifest.permission#READ_CONTACTS].
 *
 * Matching is case-insensitive and ignores spaces, so a spelled-out name like "johndoe" (from
 * "call john d o e") matches a contact stored as "John Doe".
 */
class ContactManager(private val context: Context) {

    /** Normalises a contact name for comparison: lowercase and strip all spaces. */
    private fun normalizeName(name: String): String =
        name.lowercase(Locale.ROOT).replace(" ", "")

    /**
     * Returns the first phone number for [contactName], or `null` when the contact cannot be
     * found or has no phone number.
     *
     * First tries an exact (case-insensitive) match on the display name. If that fails, falls back
     * to comparing normalised names (spaces removed, lowercased) so that a spelled-out name such as
     * "johndoe" matches a contact stored as "John Doe".
     */
    fun lookupNumber(contactName: String): String? {
        if (!hasContactsPermission()) return null

        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
        )
        val sortOrder = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"

        // 1) Fast path: exact case-insensitive match.
        val exactSelection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} = ? COLLATE NOCASE"
        return context.contentResolver.query(
            uri, projection, exactSelection, arrayOf(contactName), sortOrder
        ).use { cursor ->
            if (cursor != null && cursor.moveToFirst()) {
                cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER))
            } else {
                // 2) Fallback: compare normalised names (spaces stripped, lowercased).
                val normalizedInput = normalizeName(contactName)
                context.contentResolver.query(uri, projection, null, null, sortOrder).use { allCursor ->
                    if (allCursor != null) {
                        val numberCol = allCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                        val nameCol = allCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                        while (allCursor.moveToNext()) {
                            val displayName = allCursor.getString(nameCol)
                            if (displayName != null && normalizeName(displayName) == normalizedInput) {
                                return@use allCursor.getString(numberCol)
                            }
                        }
                    }
                    null
                }
            }
        }
    }

    private fun hasContactsPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
}
