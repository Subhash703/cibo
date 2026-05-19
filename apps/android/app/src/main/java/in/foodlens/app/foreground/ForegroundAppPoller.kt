package `in`.foodlens.app.foreground

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.provider.Settings
import android.util.Log

/**
 * Polls [UsageStatsManager] to detect the current foreground app, and emits a
 * callback when it changes. We use this to scope the floating bubble to food
 * apps only — even though Android's `SYSTEM_ALERT_WINDOW` permission is
 * global, our bubble only ever attaches when the user is inside a known
 * food / grocery / QSR app.
 *
 * This requires the user to grant "Usage access" once via system Settings;
 * there is no runtime permission dialog for it. Until granted,
 * [hasPermission] returns false and [start] is a no-op (the bubble simply
 * never shows — failing closed is the privacy-correct default).
 */
class ForegroundAppPoller(
    private val context: Context,
    private val onChanged: (String?) -> Unit,
) {

    private val usageStats =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private val handler = Handler(Looper.getMainLooper())
    private var lastForeground: String? = null
    private var running = false

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            val current = currentForegroundPackage()
            if (current != lastForeground) {
                lastForeground = current
                onChanged(current)
            }
            handler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    fun start() {
        val hasPerm = hasPermission(context)
        Log.d(TAG, "start() hasUsageAccess=$hasPerm running=$running")
        if (!hasPerm) {
            Log.w(TAG, "start aborted: Usage Access permission not granted; bubble will never show")
            return
        }
        if (running) return
        running = true
        handler.post(tick)
    }

    /**
     * Force an immediate poll, then resume the regular cadence. Use this on
     * screen-on / user-present broadcasts: Android Doze defers our scheduled
     * `postDelayed` callbacks to maintenance windows (potentially 15+ min
     * apart), so without a kick after wake-up we miss the user's first
     * food-app launch.
     */
    fun pulse() {
        if (!running) start()
        if (!running) return
        Log.d(TAG, "pulse() — forcing immediate poll")
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    fun stop() {
        Log.d(TAG, "stop()")
        running = false
        handler.removeCallbacks(tick)
        lastForeground = null
    }

    private fun currentForegroundPackage(): String? {
        val now = System.currentTimeMillis()
        // We use a wide lookback because UsageEvents only contains transition
        // events (MOVE_TO_FOREGROUND/BACKGROUND). A user idling on Swiggy's
        // cart for several minutes generates no transitions, so a short
        // window like 10 s would return empty and we'd wrongly treat "no
        // recent event" as "user left the app" — and dismiss the bubble.
        val events = usageStats.queryEvents(now - LOOKBACK_MS, now)
        val event = UsageEvents.Event()
        var bestPackage: String? = null
        var bestTime = 0L
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND &&
                event.timeStamp > bestTime
            ) {
                bestTime = event.timeStamp
                bestPackage = event.packageName
            }
        }
        return bestPackage
    }

    companion object {
        private const val TAG = "FoodLensPoller"
        private const val POLL_INTERVAL_MS = 1_000L
        // 24 hours: the most recent MOVE_TO_FOREGROUND event for the active
        // app might be from hours ago if the user has been parked on it.
        // Iterating events is cheap; correctness wins here.
        private const val LOOKBACK_MS = 24L * 60 * 60 * 1000L

        fun hasPermission(context: Context): Boolean {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName,
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName,
                )
            }
            return mode == AppOpsManager.MODE_ALLOWED
        }

        fun settingsIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

        /** One-shot query for the most recently foregrounded app.
         *  Used by [`in`.foodlens.app.overlay.OverlayBubbleManager] to route
         *  swap deep-links into the food app the user is actually in right
         *  now. Returns null when Usage Access isn't granted. */
        fun currentForegroundPackage(context: Context): String? {
            if (!hasPermission(context)) return null
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val events = usm.queryEvents(now - LOOKBACK_MS, now)
            val event = UsageEvents.Event()
            var lastPkg: String? = null
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    lastPkg = event.packageName
                }
            }
            return lastPkg
        }
    }
}
