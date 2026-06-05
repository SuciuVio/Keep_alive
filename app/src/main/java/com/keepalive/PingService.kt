package com.keepalive

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PingService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var pingJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val failureCounts = mutableMapOf<String, Int>()
    private val alertedUrls = mutableSetOf<String>()

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val BROADCAST_LOG = "com.keepalive.LOG_UPDATE"
        const val EXTRA_LOG_MESSAGE = "log_message"
        private const val FAILURE_ALERT_THRESHOLD = 3
        @Volatile var isRunning = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startPinging()
            ACTION_STOP -> stopPinging()
        }
        return START_STICKY
    }

    private fun startPinging() {
        val urls = UrlRepository.getUrls(this)
        startForeground(
            NotificationHelper.NOTIFICATION_ID,
            NotificationHelper.buildNotification(this, urls.size)
        )

        if (pingJob?.isActive == true) {
            isRunning = true
            return
        }

        isRunning = true
        acquireWakeLock()
        pingJob = serviceScope.launch {
            while (isActive) {
                val currentUrls = UrlRepository.getUrls(this@PingService)
                syncTrackedUrls(currentUrls)
                startForeground(
                    NotificationHelper.NOTIFICATION_ID,
                    NotificationHelper.buildNotification(this@PingService, currentUrls.size)
                )
                currentUrls.forEach { url -> pingUrl(url) }
                renewWakeLock()
                delay(30_000L)
            }
        }
    }

    private fun pingUrl(url: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.instanceFollowRedirects = true
            val code = connection.responseCode
            connection.disconnect()

            failureCounts[url] = 0
            alertedUrls.remove(url)
            broadcastLog("OK [$timestamp] $url -> HTTP $code")
        } catch (e: Exception) {
            val failures = (failureCounts[url] ?: 0) + 1
            failureCounts[url] = failures
            broadcastLog("FAIL [$timestamp] $url -> ${e.message ?: e.javaClass.simpleName}")

            if (failures >= FAILURE_ALERT_THRESHOLD && !alertedUrls.contains(url)) {
                alertedUrls.add(url)
                NotificationHelper.showDownAlert(this, url, failures)
            }
        }
    }

    private fun broadcastLog(message: String) {
        val intent = Intent(BROADCAST_LOG).apply {
            setPackage(packageName)
            putExtra(EXTRA_LOG_MESSAGE, message)
        }
        sendBroadcast(intent)
    }

    private fun syncTrackedUrls(currentUrls: List<String>) {
        val active = currentUrls.toSet()
        failureCounts.keys.removeAll { it !in active }
        alertedUrls.removeAll { it !in active }
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "KeepAlive::PingWakeLock"
        ).apply {
            setReferenceCounted(false)
            acquire(60 * 60 * 1000L)
        }
    }

    private fun renewWakeLock() {
        wakeLock?.let {
            if (!it.isHeld) {
                it.acquire(60 * 60 * 1000L)
            }
        }
    }

    private fun stopPinging() {
        isRunning = false
        pingJob?.cancel()
        pingJob = null
        releaseWakeLock()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
    }

    override fun onDestroy() {
        isRunning = false
        pingJob?.cancel()
        serviceScope.cancel()
        releaseWakeLock()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
