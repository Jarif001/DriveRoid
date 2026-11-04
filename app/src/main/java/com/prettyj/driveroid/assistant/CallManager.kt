package com.prettyj.driveroid.assistant

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import com.prettyj.driveroid.util.DriveroidLog

/**
 * Places calls: a regular phone call (SIM 1) via the dialer, a Messenger call by launching
 * Messenger and searching for the contact by name, or a WhatsApp call by opening a WhatsApp
 * chat resolved from the contact's phone number.
 *
 * Requires [android.Manifest.permission#CALL_PHONE] for phone calls and
 * [android.Manifest.permission#READ_CONTACTS] for looking up contact numbers.
 */
class CallManager(private val context: Context) {

    private companion object {
        const val TAG = "CallManager"
    }

    /** Places a phone call to [number] using SIM 1. */
    fun callPhone(number: String): Boolean {
        if (!hasCallPermission()) {
            DriveroidLog.e(TAG, "CALL_PHONE permission missing; cannot place call")
            return false
        }

        val cleanNumber = number.replace(Regex("[^\\d+]"), "")
        if (cleanNumber.isEmpty()) {
            DriveroidLog.w(TAG, "Empty phone number; refusing to call")
            return false
        }

        val uri = Uri.fromParts("tel", cleanNumber, null)
        val intent = Intent(Intent.ACTION_CALL).apply {
            setData(uri)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            context.startActivity(intent)
            DriveroidLog.i(TAG, "Placing phone call to $cleanNumber (SIM 1)")
            true
        } catch (e: Exception) {
            DriveroidLog.e(TAG, "Could not place phone call", e)
            false
        }
    }

    /** Opens Messenger and searches for [contactName] so the user can place a call there. */
    fun callMessenger(contactName: String): Boolean {
        val pm = context.packageManager

        // 1) Try Messenger's search deep link so the contact is searched by name.
        val searchIntent = Intent(Intent.ACTION_VIEW, Uri.parse("fb-messenger://search/$contactName")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (pm.resolveActivity(searchIntent, 0) != null) {
            return try {
                context.startActivity(searchIntent)
                DriveroidLog.i(TAG, "Opening Messenger and searching for $contactName")
                true
            } catch (e: Exception) {
                DriveroidLog.w(TAG, "Messenger search deep link failed, falling back to app launch", e)
                launchMessengerApp(contactName)
            }
        }

        // 2) Fall back: just open Messenger so the user can search manually.
        return launchMessengerApp(contactName)
    }

    /** Opens a WhatsApp chat with [phoneNumber] so the user can place a WhatsApp call. */
    fun callWhatsApp(phoneNumber: String): Boolean {
        val cleanNumber = phoneNumber.replace(Regex("[^\\d+]"), "")
        if (cleanNumber.isEmpty()) {
            DriveroidLog.w(TAG, "Empty phone number; cannot open WhatsApp")
            return false
        }

        val pm = context.packageManager

        // 1) Try WhatsApp's deep link: whatsapp://send?phone=<number>
        val waIntent = Intent(Intent.ACTION_VIEW, Uri.parse("whatsapp://send?phone=$cleanNumber")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (pm.resolveActivity(waIntent, 0) != null) {
            return try {
                context.startActivity(waIntent)
                DriveroidLog.i(TAG, "Opening WhatsApp chat with $cleanNumber")
                true
            } catch (e: Exception) {
                DriveroidLog.w(TAG, "WhatsApp deep link failed, falling back to wa.me link", e)
                openWhatsAppWeb(cleanNumber)
            }
        }

        // 2) Fall back: https://wa.me/<number> which redirects to WhatsApp.
        return openWhatsAppWeb(cleanNumber)
    }

    /** Opens https://wa.me/<number> which the system routes to WhatsApp. */
    private fun openWhatsAppWeb(cleanNumber: String): Boolean {
        val waMeIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$cleanNumber")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(waMeIntent)
            DriveroidLog.i(TAG, "Opening wa.me link for $cleanNumber")
            true
        } catch (e: Exception) {
            DriveroidLog.e(TAG, "Could not open WhatsApp", e)
            false
        }
    }

    private fun launchMessengerApp(contactName: String): Boolean {
        val pm = context.packageManager
        val launchIntent = pm.getLaunchIntentForPackage("com.facebook.orca")?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("search_query", contactName)
            putExtra("query", contactName)
        }

        if (launchIntent == null) {
            // Messenger not installed: offer the Play Store page.
            val storeIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.facebook.orca")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            return try {
                context.startActivity(storeIntent)
                DriveroidLog.w(TAG, "Messenger not installed; opened Play Store page")
                true
            } catch (e: Exception) {
                DriveroidLog.e(TAG, "Could not open Play Store", e)
                false
            }
        }

        return try {
            context.startActivity(launchIntent)
            DriveroidLog.i(TAG, "Opened Messenger app (search for: $contactName)")
            true
        } catch (e: Exception) {
            DriveroidLog.e(TAG, "Could not open Messenger", e)
            false
        }
    }

    private fun hasCallPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.CALL_PHONE) ==
            PackageManager.PERMISSION_GRANTED
}
