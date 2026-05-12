package `in`.foodlens.app.overlay

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import `in`.foodlens.app.MediaProjectionRequestActivity
import `in`.foodlens.app.foodLensApp
import `in`.foodlens.app.foreground.FoodAppAllowlist
import `in`.foodlens.app.foreground.ForegroundAppPoller

/**
 * Headless foreground service that drives the bubble's visibility.
 *
 * It does NOT own the bubble view itself — that lives in [OverlayBubbleManager]
 * (one Application-scoped instance, single window with collapsed/expanded
 * states). This service is the thin coordinator that:
 *
 *  - Wires the AI-circle tap to launch the MediaProjection consent flow.
 *  - Polls the foreground app at 1 Hz via [ForegroundAppPoller] and tells
 *    the bubble manager to show (collapsed) or dismiss based on whether the
 *    user is currently inside a food / grocery app.
 *
 * The service is what keeps the process alive (FGS notification); the bubble
 * is what the user sees.
 */
class FloatingButtonService : Service() {

    private lateinit var poller: ForegroundAppPoller

    /**
     * Re-pulses the foreground-app poller when the device wakes from Doze.
     * Without this, the bubble can fail to appear when the user wakes the
     * phone and goes straight to a food app — Android coalesces our
     * scheduled `postDelayed` callbacks during idle windows.
     *
     * ACTION_SCREEN_ON + ACTION_USER_PRESENT are both system broadcasts that
     * MUST be registered at runtime (manifest registration is rejected by
     * the OS) and that wake our process briefly to deliver them.
     */
    private val wakeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(TAG, "wake broadcast: ${intent.action}")
            if (::poller.isInitialized) poller.pulse()
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate — wiring poller + bubble callbacks")
        startInForeground()
        foodLensApp.bubbleManager.onCircleTap = ::onBubbleTapped
        poller = ForegroundAppPoller(applicationContext, ::onForegroundAppChanged)
        poller.start()
        registerWakeReceiver()
        foodLensApp.setFloatingButtonRunning(true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Manual "refresh" from HomeScreen → force-pulse the poller. Lets the
        // user kick the bubble back to life when Doze / battery saver has
        // paused it without having to fully restart the service.
        if (intent?.action == ACTION_REFRESH) {
            Log.d(TAG, "onStartCommand: refresh requested")
            if (::poller.isInitialized) poller.pulse()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        runCatching { unregisterReceiver(wakeReceiver) }
        poller.stop()
        foodLensApp.bubbleManager.dismiss()
        foodLensApp.setFloatingButtonRunning(false)
        super.onDestroy()
    }

    private fun registerWakeReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        // ContextCompat handles the API 33+ requirement to specify export
        // intent explicitly. SCREEN_ON / USER_PRESENT are protected system
        // broadcasts so NOT_EXPORTED is correct (only the system sends them).
        ContextCompat.registerReceiver(
            this,
            wakeReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    @SuppressLint("InlinedApi")
    private fun startInForeground() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Floating button", NotificationManager.IMPORTANCE_LOW),
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Cibo is ready")
            .setContentText("The bubble appears only when you open a food app.")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    private fun onForegroundAppChanged(packageName: String?) {
        // Ignore transient transitions through our own activities (e.g. the
        // MediaProjection consent dialog) — they're part of the capture flow,
        // not a real "user left the food app" event.
        if (packageName == applicationContext.packageName) {
            Log.v(TAG, "fg=$packageName (own pkg, ignored)")
            return
        }
        // While a capture is in flight, ScreenCaptureService is in charge of
        // bubble visibility. If we re-attached the bubble here, it would end
        // up in the captured screenshot.
        if (foodLensApp.captureActive) {
            Log.v(TAG, "fg=$packageName (capture in progress, ignored)")
            return
        }

        val shouldShow = FoodAppAllowlist.matches(packageName)
        Log.d(TAG, "fg=$packageName matchesAllowlist=$shouldShow")
        if (shouldShow) {
            foodLensApp.bubbleManager.showCollapsed()
        } else {
            foodLensApp.bubbleManager.dismiss()
        }
    }

    private fun onBubbleTapped() {
        startActivity(MediaProjectionRequestActivity.intent(this))
    }

    companion object {
        private const val TAG = "FoodLensOverlay"
        private const val CHANNEL_ID = "foodlens.overlay"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_REFRESH = "in.foodlens.app.action.REFRESH_BUBBLE"

        fun start(context: Context) {
            val intent = Intent(context, FloatingButtonService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingButtonService::class.java))
        }

        /**
         * Force the running service to immediately re-check the foreground app
         * and (re-)attach the bubble if appropriate. If the service isn't
         * already running, this also starts it. Used by the HomeScreen
         * "Refresh bubble" button to recover from Doze-induced poll stalls.
         */
        fun refresh(context: Context) {
            val intent = Intent(context, FloatingButtonService::class.java).apply {
                action = ACTION_REFRESH
            }
            context.startForegroundService(intent)
        }
    }
}
