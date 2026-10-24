/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.BuildConfig
import moe.rukamori.archivetune.MainActivity
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AutomaticUpdateCheckKey
import moe.rukamori.archivetune.constants.EnableUpdateNotificationKey
import moe.rukamori.archivetune.constants.LastNotifiedVersionKey
import moe.rukamori.archivetune.constants.LastUpdateCheckKey
import moe.rukamori.archivetune.constants.UpdateChannel
import moe.rukamori.archivetune.constants.UpdateChannelKey
import moe.rukamori.archivetune.defaultUpdateChannel
import moe.rukamori.archivetune.updates.UpdateCheckScheduler
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

object UpdateNotificationManager {
    private const val CHANNEL_ID = "update_notification_channel"
    private const val NOTIFICATION_ID = 9999
    private const val NEWS_NOTIFICATION_ID = 9998 // ID Khusus untuk notifikasi berita
    private const val CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun createNotificationChannel(context: Context) {
        if (!BuildConfig.UPDATER_AVAILABLE) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = context.getString(R.string.update_notification_channel_name)
            val descriptionText = context.getString(R.string.update_notification_channel_desc)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel =
                NotificationChannel(CHANNEL_ID, name, importance).apply {
                    description = descriptionText
                }
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    fun schedulePeriodicUpdateCheck(context: Context) {
        UpdateCheckScheduler.schedule(context)
    }

    fun cancelPeriodicUpdateCheck(context: Context) {
        UpdateCheckScheduler.cancel(context)
    }

    suspend fun checkForUpdates(context: Context) {
        if (!BuildConfig.UPDATER_AVAILABLE) {
            cancelPeriodicUpdateCheck(context)
            cancelUpdateNotification(context)
            return
        }

        // =====================================================================
        // [FITUR DEWA: PEMANGGILAN PENGECEKAN BERITA SECARA PARALEL]
        // =====================================================================
        checkForNews(context)

        withContext(Dispatchers.IO) {
            try {
                val dataStore = context.dataStore

                val preferences = dataStore.data.first()
                val automaticChecksEnabled = preferences[AutomaticUpdateCheckKey] ?: true
                if (!automaticChecksEnabled) {
                    cancelPeriodicUpdateCheck(context)
                    return@withContext
                }

                schedulePeriodicUpdateCheck(context)

                val notificationsEnabled = preferences[EnableUpdateNotificationKey] ?: false
                if (!notificationsEnabled) return@withContext

                val updateChannel =
                    UpdateChannel.fromStoredName(preferences[UpdateChannelKey], defaultUpdateChannel)

                val lastCheck = preferences[LastUpdateCheckKey] ?: 0L
                val now = System.currentTimeMillis()

                if (now - lastCheck < CHECK_INTERVAL_MS) return@withContext

                dataStore.edit { it[LastUpdateCheckKey] = now }

                val latestVersion =
                    when (updateChannel) {
                        UpdateChannel.ARTIFACT -> Updater.getLatestCanaryVersionName()
                        UpdateChannel.STABLE -> Updater.getLatestVersionName()
                    }.getOrElse { throw it }

                if (Updater.isUpdateAvailable(latestVersion, BuildConfig.VERSION_NAME)) {
                    notifyIfNewVersion(context, latestVersion, updateChannel)
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                reportException(exception)
            }
        }
    }

    // =====================================================================
    // [MODUL BARU: SISTEM PENGECEKAN DAN NOTIFIKASI BERITA PTERODACTYL]
    // =====================================================================
    private fun checkForNews(context: Context) {
        scope.launch {
            try {
                val prefs = context.getSharedPreferences("ArchiveTuneNewsPrefs", Context.MODE_PRIVATE)
                val lastNotifiedNewsId = prefs.getString("LastNotifiedNewsId", "")

                val url = URL("http://de3.bot-hosting.net:21013/api/archivetune/news")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 5000
                conn.readTimeout = 5000

                if (conn.responseCode == 200) {
                    val response = conn.inputStream.bufferedReader().readText()
                    val jsonArray = JSONArray(response)
                    
                    if (jsonArray.length() > 0) {
                        val latestNews = jsonArray.getJSONObject(0)
                        val newsId = latestNews.optString("id", "")
                        
                        // Menarik Judul dan Deskripsi secara Dinamis dari JSON Web Panel
                        val newsTitle = latestNews.optString("Title", "Berita Baru ArchiveTune")
                        val newsDesc = latestNews.optString("Description", "Ada informasi baru dari developer.")
                        val isImportant = latestNews.optBoolean("Important", false)
                        
                        // Menarik Link URL Gambar Jika Ada
                        val imageUrls = latestNews.optJSONArray("ImageURL")
                        val imageUrl = if (imageUrls != null && imageUrls.length() > 0) imageUrls.getString(0) else null

                        // Mencegah Spam: Hanya muncul jika ID berita berbeda dari yang terakhir kali dikirim
                        if (newsId.isNotEmpty() && newsId != lastNotifiedNewsId) {
                            showNewsNotification(context, newsTitle, newsDesc, isImportant, imageUrl)
                            prefs.edit().putString("LastNotifiedNewsId", newsId).apply()
                        }
                    }
                }
                conn.disconnect()
            } catch (e: Exception) {
                // Abaikan jika gagal terhubung
            }
        }
    }

    private fun getBitmapFromUrl(imageUrl: String?): Bitmap? {
        if (imageUrl.isNullOrBlank()) return null
        return try {
            val url = URL(imageUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.doInput = true
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.connect()
            BitmapFactory.decodeStream(connection.inputStream)
        } catch (e: Exception) {
            null
        }
    }

    private fun showNewsNotification(context: Context, title: String, desc: String, isImportant: Boolean, imageUrl: String?) {
        createNotificationChannel(context)

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            2, // Request Code 2 khusus untuk Berita
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val displayTitle = if (isImportant) "[PENTING] $title" else "[INFO] $title"
        val priority = if (isImportant) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.small_icon)
            .setContentTitle(displayTitle)
            .setContentText(desc)
            .setPriority(priority)
            .setContentIntent(openAppPendingIntent)
            .setAutoCancel(true)

        // Jika ada link gambar, muat dan tampilkan foto besar, jika tidak gunakan fallback teks biasa
        val bitmap = getBitmapFromUrl(imageUrl)
        if (bitmap != null) {
            builder.setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(bitmap)
                    .setSummaryText(desc)
            )
        } else {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(desc))
        }

        try {
            NotificationManagerCompat.from(context).notify(NEWS_NOTIFICATION_ID, builder.build())
        } catch (e: SecurityException) {
            // Missing POST_NOTIFICATIONS permission
        }
    }
    // =====================================================================

    suspend fun notifyIfNewVersion(
        context: Context,
        latestVersion: String,
        updateChannel: UpdateChannel = UpdateChannel.STABLE,
    ) {
        if (!BuildConfig.UPDATER_AVAILABLE) return

        try {
            val dataStore = context.dataStore
            val preferences = dataStore.data.first()
            val automaticChecksEnabled = preferences[AutomaticUpdateCheckKey] ?: true
            if (!automaticChecksEnabled || preferences[EnableUpdateNotificationKey] != true) return
            val lastNotified = preferences[LastNotifiedVersionKey].orEmpty()

            if (latestVersion != lastNotified && Updater.isUpdateAvailable(latestVersion, BuildConfig.VERSION_NAME)) {
                showUpdateNotification(context, latestVersion, updateChannel)
                dataStore.edit { it[LastNotifiedVersionKey] = latestVersion }
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            reportException(exception)
        }
    }

    private fun showUpdateNotification(
        context: Context,
        newVersion: String,
        updateChannel: UpdateChannel = UpdateChannel.STABLE,
    ) {
        createNotificationChannel(context)

        val openAppIntent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("navigate_to", "settings/update")
            }
        val openAppPendingIntent =
            PendingIntent.getActivity(
                context,
                0,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val downloadUrl =
            when (updateChannel) {
                UpdateChannel.ARTIFACT -> Updater.getLatestCanaryDownloadUrl()
                UpdateChannel.STABLE -> Updater.getLatestDownloadUrl()
            }
        val downloadIntent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl))
        val downloadPendingIntent =
            PendingIntent.getActivity(
                context,
                1,
                downloadIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val notification =
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.small_icon)
                .setContentTitle(context.getString(R.string.update_notification_title))
                .setContentText(context.getString(R.string.update_notification_text, newVersion))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(openAppPendingIntent)
                .setAutoCancel(true)
                .addAction(
                    R.drawable.download,
                    context.getString(R.string.download),
                    downloadPendingIntent,
                ).build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Missing POST_NOTIFICATIONS permission
        }
    }

    fun cancelUpdateNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }
}
