package com.prettyj.driveroid.assistant

import android.content.Context
import android.database.Cursor
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

/**
 * Resolves a contact name to a phone number using the device's contacts provider.
 *
 * Requires [android.Manifest.permission#READ_CONTACTS].
 */
class ContactManager(private val context: Context) {

    /**
     * Returns the first phone number for [contactName], or `null` when the contact cannot be
     * found or has no phone number. The match is case-insensitive on the display name.
     */
    fun lookupNumber(contactName: String): String? {
        if (!hasContactsPermission()) return null

        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
        )
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} = ? COLLATE NOCASE"
        val selectionArgs = arrayOf(contactName)
        val sortOrder = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"

        return context.contentResolver.query(uri, projection, selection, selectionArgs, sortOrder).use { cursor ->
            if (cursor != null && cursor.moveToFirst()) {
                cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER))
            } else {
                null
            }
        }
    }

    private fun hasContactsPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
}
