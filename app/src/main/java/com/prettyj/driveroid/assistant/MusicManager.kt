package com.prettyj.driveroid.assistant

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import com.prettyj.driveroid.util.DriveroidLog

class MusicManager(private val context: Context) {

    private companion object {
        const val TAG = "MusicManager"
        const val YOUTUBE_MUSIC_PACKAGE = "com.google.android.apps.youtube.music"
        const val SPOTIFY_PACKAGE = "com.spotify.music"
    }

    fun openMusicApp(appName: String): Boolean {
        val packageName = when (appName.lowercase()) {
            "youtube music" -> YOUTUBE_MUSIC_PACKAGE
            "spotify" -> SPOTIFY_PACKAGE
            else -> {
                DriveroidLog.w(TAG, "Unknown music app: $appName")
                return false
            }
        }

        val pm = context.packageManager

        val isInstalled = try {
            pm.getPackageInfo(packageName, PackageManager.GET_META_DATA)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            DriveroidLog.w(TAG, "$appName package not installed: $packageName")
            false
        }

        if (!isInstalled) {
            val storeIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            return try {
                context.startActivity(storeIntent)
                DriveroidLog.w(TAG, "$appName not installed; opened Play Store page")
                true
            } catch (e: Exception) {
                DriveroidLog.e(TAG, "Could not open Play Store for $appName", e)
                false
            }
        }

        val launchIntent = pm.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        }

        if (launchIntent != null) {
            try {
                context.startActivity(launchIntent)
                DriveroidLog.i(TAG, "Opened $appName via launch intent")
                return true
            } catch (e: Exception) {
                DriveroidLog.w(TAG, "Launch intent failed for $appName, trying ACTION_MAIN", e)
            }
        }

        val mainIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setPackage(packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        }

        return if (pm.resolveActivity(mainIntent, 0) != null) {
            try {
                context.startActivity(mainIntent)
                true
            } catch (e: Exception) {
                DriveroidLog.e(TAG, "Could not open $appName via ACTION_MAIN", e)
                false
            }
        } else {
            DriveroidLog.e(TAG, "Could not find any way to launch $appName")
            false
        }
    }
    /** Searches for [query] in [appName] and shows search results. */
    fun searchMusicInApp(query: String, appName: String): Boolean {
        val cleanedQuery = query.replace(Regex("[^\\p{L}\\p{N}\\s]"), "").trim()
        if (cleanedQuery.isEmpty()) {
            DriveroidLog.w(TAG, "Empty search query; cannot search music")
            return false
        }

        return when (appName.lowercase()) {
            "youtube music" -> searchInYouTubeMusic(cleanedQuery)
            "spotify" -> searchInSpotify(cleanedQuery)
            else -> {
                DriveroidLog.w(TAG, "Unknown music app: $appName")
                false
            }
        }
    }

    /** Searches for [query] in [appName] and plays the first result. */
    fun playMusicInApp(query: String, appName: String): Boolean {
        val cleanedQuery = query.replace(Regex("[^\\p{L}\\p{N}\\s]"), "").trim()
        if (cleanedQuery.isEmpty()) {
            DriveroidLog.w(TAG, "Empty search query; cannot play music")
            return false
        }

        return when (appName.lowercase()) {
            "youtube music" -> playInYouTubeMusic(cleanedQuery)
            "spotify" -> playInSpotify(cleanedQuery)
            else -> {
                DriveroidLog.w(TAG, "Unknown music app: $appName")
                false
            }
        }
    }
    /** Searches YouTube Music for [query] using its deep link or web URL fallback. */
    private fun searchInYouTubeMusic(query: String): Boolean {
        val encoded = Uri.encode(query)

        val ytMusicIntent = Intent(Intent.ACTION_VIEW, Uri.parse("youtube-music://search?q=$encoded")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        }

        val pm = context.packageManager
        if (pm.resolveActivity(ytMusicIntent, 0) != null) {
            try {
                context.startActivity(ytMusicIntent)
                DriveroidLog.i(TAG, "Searching YouTube Music via deep link for: $query")
                return true
            } catch (e: Exception) {
                DriveroidLog.w(TAG, "YouTube Music deep link failed, trying web URL fallback", e)
            }
        } else {
            DriveroidLog.w(TAG, "No app handles youtube-music:// deep link, trying web URL fallback")
        }

        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://music.youtube.com/search?q=$encoded")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        }
        if (pm.resolveActivity(webIntent, 0) != null) {
            try {
                context.startActivity(webIntent)
                DriveroidLog.i(TAG, "Searching YouTube Music via web URL for: $query")
                return true
            } catch (e: Exception) {
                DriveroidLog.w(TAG, "YouTube Music web URL failed, just opening app", e)
                return openMusicApp("youtube music")
            }
        }

        DriveroidLog.w(TAG, "No way to search YouTube Music, just opening app")
        return openMusicApp("youtube music")
    }

    /** Searches Spotify for [query] using web URL first (Android App Link), then deep link fallback. */
    private fun searchInSpotify(query: String): Boolean {
        val encoded = Uri.encode(query)

        // Web URL first - Android App Links are more reliable than custom schemes for Spotify.
        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://open.spotify.com/search/$encoded")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        }

        val pm = context.packageManager
        if (pm.resolveActivity(webIntent, 0) != null) {
            try {
                context.startActivity(webIntent)
                DriveroidLog.i(TAG, "Searching Spotify via web URL for: $query")
                return true
            } catch (e: Exception) {
                DriveroidLog.w(TAG, "Spotify web URL failed, trying deep link fallback", e)
            }
        } else {
            DriveroidLog.w(TAG, "No app handles Spotify web URL, trying deep link")
        }

        // Fall back: Spotify deep link using correct URI scheme: spotify:search:QUERY
        val spotifyIntent = Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:$encoded")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        }
        if (pm.resolveActivity(spotifyIntent, 0) != null) {
            try {
                context.startActivity(spotifyIntent)
                DriveroidLog.i(TAG, "Searching Spotify via deep link for: $query")
                return true
            } catch (e: Exception) {
                DriveroidLog.w(TAG, "Spotify deep link failed, just opening app", e)
                return openMusicApp("spotify")
            }
        }

        DriveroidLog.w(TAG, "No way to search Spotify, just opening app")
        return openMusicApp("spotify")
    }
    /** Plays YouTube Music for [query] - tries web URL first, then deep link fallback. */
    private fun playInYouTubeMusic(query: String): Boolean {
        val encoded = Uri.encode(query)

        // Web URL first - Android App Links are more reliable than custom schemes.
        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://music.youtube.com/search?q=$encoded")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        }

        val pm = context.packageManager
        if (pm.resolveActivity(webIntent, 0) != null) {
            try {
                context.startActivity(webIntent)
                DriveroidLog.i(TAG, "Playing YouTube Music via web URL for: $query")
                return true
            } catch (e: Exception) {
                DriveroidLog.w(TAG, "YouTube Music web URL failed, trying deep link", e)
            }
        } else {
            DriveroidLog.w(TAG, "No app handles YouTube Music web URL, trying deep link")
        }

        // Fall back: deep link with autoplay parameter.
        val ytMusicIntent = Intent(Intent.ACTION_VIEW, Uri.parse("youtube-music://search?q=$encoded&autoplay=1")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        }
        if (pm.resolveActivity(ytMusicIntent, 0) != null) {
            try {
                context.startActivity(ytMusicIntent)
                DriveroidLog.i(TAG, "Playing YouTube Music via deep link for: $query")
                return true
            } catch (e: Exception) {
                DriveroidLog.w(TAG, "YouTube Music deep link failed, just opening app", e)
                return openMusicApp("youtube music")
            }
        }

        DriveroidLog.w(TAG, "No way to play YouTube Music, just opening app")
        return openMusicApp("youtube music")
    }

    /** Plays Spotify for [query] - tries web URL first, then deep link fallback. */
    private fun playInSpotify(query: String): Boolean {
        val encoded = Uri.encode(query)

        // Web URL first - Android App Links are more reliable for Spotify.
        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://open.spotify.com/search/$encoded")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        }

        val pm = context.packageManager
        if (pm.resolveActivity(webIntent, 0) != null) {
            try {
                context.startActivity(webIntent)
                DriveroidLog.i(TAG, "Playing Spotify via web URL for: $query")
                return true
            } catch (e: Exception) {
                DriveroidLog.w(TAG, "Spotify web URL failed, trying deep link fallback", e)
            }
        } else {
            DriveroidLog.w(TAG, "No app handles Spotify web URL, trying deep link")
        }

        // Fall back: Spotify deep link using correct URI scheme: spotify:search:QUERY
        val spotifyIntent = Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:$encoded")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        }
        if (pm.resolveActivity(spotifyIntent, 0) != null) {
            try {
                context.startActivity(spotifyIntent)
                DriveroidLog.i(TAG, "Playing Spotify via deep link for: $query")
                return true
            } catch (e: Exception) {
                DriveroidLog.w(TAG, "Spotify deep link failed, just opening app", e)
                return openMusicApp("spotify")
            }
        }

        DriveroidLog.w(TAG, "No way to play Spotify, just opening app")
        return openMusicApp("spotify")
    }
}