package `in`.foodlens.app

import android.app.Application
import android.content.Context
import android.util.Log
import `in`.foodlens.app.auth.AuthState
import `in`.foodlens.app.network.AnalyzeClient
import `in`.foodlens.app.network.AnalyzeResponse
import `in`.foodlens.app.network.MealLogRequest
import `in`.foodlens.app.overlay.OverlayBubbleManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * App-scoped owner of singleton-style state.
 *
 * - [bubbleManager]: the floating bubble (one View, two visual states).
 * - [authState]: current sign-in / profile snapshot, persisted to prefs.
 * - [analyzer]: shared HTTP client for the backend; one instance keeps
 *   OkHttp's connection pool warm across the app lifetime.
 * - [appScope]: process-lifetime CoroutineScope used for fire-and-forget
 *   work (meal logging) that must survive after the originating Service
 *   has stopped.
 * - [captureActive]: inter-component flag set during the brief window
 *   between MediaProjection consent and the first captured frame.
 */
class FoodLensApp : Application() {
    val bubbleManager: OverlayBubbleManager by lazy { OverlayBubbleManager(this) }
    val authState: AuthState by lazy { AuthState(this) }
    val analyzer: AnalyzeClient by lazy { AnalyzeClient(BuildConfig.BACKEND_BASE_URL) }
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile
    var captureActive: Boolean = false

    /**
     * True iff [`in`.foodlens.app.overlay.FloatingButtonService] is currently
     * alive. Updated by the service in onCreate / onDestroy. The HomeScreen
     * collects this to render the status card and toggle the start/stop button.
     */
    private val _floatingButtonRunning = MutableStateFlow(false)
    val floatingButtonRunning: StateFlow<Boolean> = _floatingButtonRunning.asStateFlow()

    fun setFloatingButtonRunning(running: Boolean) {
        _floatingButtonRunning.value = running
    }

    override fun onCreate() {
        super.onCreate()
        bubbleManager.onConfirmOrder = ::confirmOrder
    }

    /** Wired from [OverlayBubbleManager] when the user taps "Yes, ordering" on the result card. */
    private fun confirmOrder(response: AnalyzeResponse) {
        val token = authState.idToken
        if (token == null) {
            Log.w(TAG, "confirmOrder called without auth token")
            return
        }
        appScope.launch {
            try {
                val summary = analyzer.logMeal(
                    idToken = token,
                    request = MealLogRequest(
                        macros = response.macros,
                        items = response.items,
                        healthScore = response.healthScore,
                    ),
                )
                bubbleManager.showOrderConfirmed(summary)
            } catch (t: Throwable) {
                Log.e(TAG, "log meal failed", t)
                bubbleManager.showError(t.message ?: "Couldn't save meal")
            }
        }
    }

    private companion object {
        const val TAG = "FoodLensApp"
    }
}

val Context.foodLensApp: FoodLensApp
    get() = applicationContext as FoodLensApp
