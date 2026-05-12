package `in`.foodlens.app.overlay

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import `in`.foodlens.app.MainActivity
import `in`.foodlens.app.network.AnalyzeResponse
import `in`.foodlens.app.network.DailySummary
import `in`.foodlens.app.network.MatchedItem

/**
 * Owns *the one* Food Lens bubble window. The bubble is a single
 * WindowManager attachment that morphs between two states:
 *
 *  - **Collapsed**: a 56dp draggable AI circle.
 *  - **Expanded**: a card showing loading / result / error / order-confirmed.
 *
 * State transitions animate the *same window's* size, position and
 * corner radius via [ValueAnimator]. Inner content crossfades.
 *
 * If the user is signed in, [showResult] adds a "Remaining today" section
 * and "Yes, ordering / No" action buttons. Tapping "Yes, ordering"
 * fires [onConfirmOrder] — wired by [`in`.foodlens.app.FoodLensApp] to
 * POST a meal log and call [showOrderConfirmed] on the way back.
 */
class OverlayBubbleManager(private val appContext: Context) {

    private val main = Handler(Looper.getMainLooper())
    private val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    /** Wired by FloatingButtonService at startup. Invoked when the user taps the AI circle. */
    var onCircleTap: () -> Unit = {}

    /** Wired by FoodLensApp. Fires when the user taps "Yes, ordering it". */
    var onConfirmOrder: (AnalyzeResponse) -> Unit = {}

    private var rootView: FrameLayout? = null
    private var rootParams: WindowManager.LayoutParams? = null
    private var sizeAnimator: ValueAnimator? = null
    private var currentState: State = State.NONE

    private val collapsedSizePx = dp(56f).toInt()
    private val expandedMaxWidthPx = dp(320f).toInt()
    private val collapsedCornerRadius = collapsedSizePx / 2f
    private val expandedCornerRadius = dp(16f)

    private var collapsedX = dp(16f).toInt()
    private var collapsedY = dp(200f).toInt()

    // Loading ticker state.
    private var loadingPhaseTv: TextView? = null
    private var loadingTipTv: TextView? = null
    private var loadingTickStart: Long = 0L
    private var loadingTipIdx: Int = 0
    private val loadingTicker = object : Runnable {
        override fun run() {
            if (currentState != State.LOADING) return
            loadingTipIdx = (loadingTipIdx + 1) % LOADING_TIPS.size
            loadingTipTv?.text = "💡 ${LOADING_TIPS[loadingTipIdx]}"
            val elapsed = System.currentTimeMillis() - loadingTickStart
            loadingPhaseTv?.text = when {
                elapsed < 1500 -> "Looking at your cart…"
                elapsed < 4500 -> "Doing the maths…"
                else -> "Almost there…"
            }
            main.postDelayed(this, LOADING_TIP_INTERVAL_MS)
        }
    }

    // Auto-collapse after order-confirmed splash.
    private val confirmedAutoCollapse: Runnable = Runnable {
        if (currentState == State.CONFIRMED) showCollapsed()
    }

    fun showCollapsed() {
        main.post {
            stopLoadingTicker()
            main.removeCallbacks(confirmedAutoCollapse)
            currentState = State.COLLAPSED
            morphTo(
                content = renderCollapsed(),
                targetX = collapsedX,
                targetY = collapsedY,
                targetW = collapsedSizePx,
                targetH = collapsedSizePx,
                targetRadius = collapsedCornerRadius,
            )
        }
    }

    fun showLoading() {
        main.post {
            loadingTickStart = System.currentTimeMillis()
            loadingTipIdx = 0
            currentState = State.LOADING
            morphToExpanded(::renderLoading)
            main.removeCallbacks(loadingTicker)
            main.postDelayed(loadingTicker, LOADING_TIP_INTERVAL_MS)
        }
    }

    fun showResult(response: AnalyzeResponse) {
        main.post {
            stopLoadingTicker()
            currentState = State.RESULT
            morphToExpanded { renderResult(response) }
        }
    }

    fun showError(message: String) {
        main.post {
            stopLoadingTicker()
            currentState = State.ERROR
            morphToExpanded { renderError(message) }
        }
    }

    fun showOrderConfirmed(summary: DailySummary) {
        main.post {
            stopLoadingTicker()
            currentState = State.CONFIRMED
            morphToExpanded { renderConfirmed(summary) }
            // Auto-collapse a few seconds after the confirmation splash so
            // the user is back to the small bubble when they return to
            // ordering.
            main.removeCallbacks(confirmedAutoCollapse)
            main.postDelayed(confirmedAutoCollapse, CONFIRMED_AUTO_COLLAPSE_MS)
        }
    }

    fun dismiss() {
        main.post {
            stopLoadingTicker()
            main.removeCallbacks(confirmedAutoCollapse)
            sizeAnimator?.cancel()
            rootView?.let { runCatching { wm.removeView(it) } }
            rootView = null
            rootParams = null
            currentState = State.NONE
        }
    }

    private fun stopLoadingTicker() {
        main.removeCallbacks(loadingTicker)
        loadingPhaseTv = null
        loadingTipTv = null
    }

    private fun morphToExpanded(contentBuilder: () -> View) {
        val content = contentBuilder()
        content.measure(
            View.MeasureSpec.makeMeasureSpec(expandedMaxWidthPx, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val measuredW = content.measuredWidth.coerceAtMost(expandedMaxWidthPx)
        val measuredH = content.measuredHeight

        val expandedX = expandedAnchorX(measuredW)
        val expandedY = dp(80f).toInt()

        morphTo(
            content = content,
            targetX = expandedX,
            targetY = expandedY,
            targetW = measuredW,
            targetH = measuredH,
            targetRadius = expandedCornerRadius,
        )
    }

    private fun expandedAnchorX(cardWidth: Int): Int {
        val screenW = appContext.resources.displayMetrics.widthPixels
        val margin = dp(16f).toInt()
        return (screenW - cardWidth - margin).coerceAtLeast(margin)
    }

    private fun morphTo(
        content: View,
        targetX: Int,
        targetY: Int,
        targetW: Int,
        targetH: Int,
        targetRadius: Float,
    ) {
        val root = ensureRoot()
        val params = rootParams!!

        val startX = params.x
        val startY = params.y
        val startW = if (params.width > 0) params.width else targetW
        val startH = if (params.height > 0) params.height else targetH
        val startRadius = (root.background as? GradientDrawable)?.cornerRadius ?: targetRadius

        val previous = root.getChildAt(0)
        if (previous != null) {
            previous.animate()
                .alpha(0f)
                .setDuration(CROSSFADE_OUT_MS)
                .withEndAction { runCatching { root.removeView(previous) } }
                .start()
        }
        content.alpha = 0f
        root.addView(content)
        content.animate()
            .alpha(1f)
            .setDuration(CROSSFADE_IN_MS)
            .setStartDelay(CROSSFADE_IN_DELAY_MS)
            .start()

        sizeAnimator?.cancel()
        sizeAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = MORPH_DURATION_MS
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { ani ->
                val t = ani.animatedValue as Float
                params.x = lerp(startX, targetX, t)
                params.y = lerp(startY, targetY, t)
                params.width = lerp(startW, targetW, t)
                params.height = lerp(startH, targetH, t)
                (root.background as? GradientDrawable)?.cornerRadius =
                    lerpF(startRadius, targetRadius, t)
                runCatching { wm.updateViewLayout(root, params) }
            }
            start()
        }
    }

    private fun ensureRoot(): FrameLayout {
        rootView?.let { return it }
        val root = FrameLayout(appContext).apply {
            background = GradientDrawable().apply {
                cornerRadius = collapsedCornerRadius
                setColor(0xFF15202B.toInt())
                setStroke(dp(1f).toInt(), 0xFF1ED4B6.toInt())
            }
            elevation = dp(8f)
        }
        val params = WindowManager.LayoutParams(
            collapsedSizePx,
            collapsedSizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = collapsedX
            y = collapsedY
        }
        runCatching { wm.addView(root, params) }
            .onFailure {
                Log.e(
                    "FoodLensBubble",
                    "WindowManager.addView failed — usually means SYSTEM_ALERT_WINDOW " +
                        "is revoked. Re-grant Display-over-other-apps in Settings.",
                    it,
                )
            }
        rootView = root
        rootParams = params
        return root
    }

    // --- Content builders ---------------------------------------------------

    @SuppressLint("ClickableViewAccessibility")
    private fun renderCollapsed(): View {
        val container = FrameLayout(appContext).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
        }
        val ai = TextView(appContext).apply {
            text = "C"
            setTypeface(Typeface.DEFAULT_BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#0F1722"))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#1ED4B6"))
            }
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
        }
        container.addView(ai)
        container.setOnTouchListener(
            DragAndTapListener(
                params = { rootParams!! },
                onPositionChanged = { x, y ->
                    collapsedX = x
                    collapsedY = y
                },
                wm = wm,
                rootViewProvider = { rootView },
                onTap = { onCircleTap() },
                isCollapsed = { currentState == State.COLLAPSED },
            ),
        )
        return container
    }

    private fun renderLoading(): View {
        val card = card()
        card.addView(header(title = "Cibo"))

        val phase = textView("Looking at your cart…", titleSize = 16f, bold = true)
        loadingPhaseTv = phase
        card.addView(phase)

        // Static evocative subtitle — mirrors the Plate tab's
        // "Reading colours, textures, portions" rhythm. Gives the user
        // something to read during the 2-3s Gemini round-trip.
        card.addView(
            textView(
                "Reading items, portions, totals.",
                titleSize = 13f,
                subdued = true,
            ),
        )

        card.addView(spinnerDots())

        card.addView(divider())
        val tip = textView("💡 ${LOADING_TIPS[0]}", titleSize = 13f, subdued = true)
        loadingTipTv = tip
        card.addView(tip)

        return card
    }

    private fun renderError(message: String): View {
        val card = card()
        card.addView(header(title = "Couldn't analyze"))
        card.addView(textView(message, subdued = true))
        return card
    }

    private fun renderResult(r: AnalyzeResponse): View {
        val card = card()

        card.addView(header(title = "Cibo"))
        card.addView(textView("Health Score", subdued = true))
        card.addView(
            textView(
                "${r.healthScore} · ${r.healthLabel}",
                titleSize = 22f,
                color = scoreColor(r.healthScore),
                bold = true,
            ),
        )

        val macros = LinearLayout(appContext).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10f).toInt(), 0, dp(10f).toInt())
        }
        macros.addView(macroChip("${r.macros.kcal}", "kcal"))
        macros.addView(macroChip("${r.macros.proteinG}g", "protein"))
        macros.addView(macroChip("${r.macros.fatG}g", "fat"))
        card.addView(macros)

        card.addView(textView("${r.percentDailyKcal}% of your daily intake", subdued = true))

        // Daily-tracking section: shown when the user is signed in (backend
        // populated daily_summary). The bar projects today's consumed +
        // *this cart's* kcal so the user sees the impact of confirming.
        r.dailySummary?.let { summary ->
            card.addView(divider())
            card.addView(dailySummarySection(summary, projectedAdd = r.macros.kcal))
        }

        if (r.items.isNotEmpty()) {
            card.addView(divider())
            for (item in r.items) {
                card.addView(itemRow(item))
            }
        }

        if (r.unmatched.isNotEmpty()) {
            card.addView(divider())
            card.addView(textView("AI couldn't identify:", titleSize = 12f, subdued = true))
            card.addView(textView(r.unmatched.joinToString(" · "), titleSize = 12f, subdued = true))
        }

        r.suggestions.firstOrNull()?.let {
            card.addView(divider())
            card.addView(textView("💡 ${it.text}", titleSize = 14f))
        }

        // Confirm-or-skip buttons (signed in) OR sign-in prompt (signed out).
        card.addView(divider())
        if (r.dailySummary != null) {
            card.addView(actionButtons(r))
        } else {
            card.addView(signInPrompt())
        }

        return card
    }

    private fun renderConfirmed(s: DailySummary): View {
        val card = card()
        card.addView(header(title = "Cibo"))

        val tick = textView(
            "✓ Order logged",
            titleSize = 22f,
            bold = true,
            color = 0xFF34D399.toInt(),
        )
        card.addView(tick)

        card.addView(
            textView(
                "${s.consumedKcal} kcal consumed · ${s.remainingKcal} kcal left today",
                titleSize = 13f,
                subdued = true,
            ),
        )

        val progress = (s.consumedKcal.toFloat() / s.dailyKcalTarget).coerceIn(0f, 1f)
        card.addView(progressBar(progress))

        card.addView(
            textView(
                "Goal: ${s.dailyKcalTarget} kcal · ${s.logCount} order${if (s.logCount == 1) "" else "s"} today",
                titleSize = 12f,
                subdued = true,
            ),
        )

        return card
    }

    private fun dailySummarySection(s: DailySummary, projectedAdd: Int): View {
        val container = LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
        }
        val projected = (s.consumedKcal + projectedAdd).coerceAtLeast(0)
        val newRemaining = (s.dailyKcalTarget - projected).coerceAtLeast(0)

        container.addView(
            textView(
                "If you order this:",
                titleSize = 12f,
                subdued = true,
            ),
        )
        container.addView(
            textView(
                "$newRemaining kcal left of ${s.dailyKcalTarget}",
                titleSize = 16f,
                bold = true,
                color = goalProjectionColor(projected, s.dailyKcalTarget),
            ),
        )
        val progress = (projected.toFloat() / s.dailyKcalTarget).coerceIn(0f, 1f)
        container.addView(progressBar(progress))
        container.addView(
            textView(
                "Already today: ${s.consumedKcal} of ${s.dailyKcalTarget} kcal",
                titleSize = 11f,
                subdued = true,
            ),
        )
        return container
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun actionButtons(response: AnalyzeResponse): View {
        val row = LinearLayout(appContext).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8f).toInt(), 0, 0)
        }
        val skip = TextView(appContext).apply {
            text = "No, just checking"
            setTextColor(0xFF92A0B0.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(dp(8f).toInt(), dp(12f).toInt(), dp(8f).toInt(), dp(12f).toInt())
            setOnClickListener { showCollapsed() }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val confirm = TextView(appContext).apply {
            text = "Yes, ordering it"
            setTextColor(0xFF0F1722.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(Typeface.DEFAULT_BOLD)
            background = GradientDrawable().apply {
                cornerRadius = dp(10f)
                setColor(0xFF1ED4B6.toInt())
            }
            setPadding(dp(16f).toInt(), dp(12f).toInt(), dp(16f).toInt(), dp(12f).toInt())
            gravity = Gravity.CENTER
            setOnClickListener { onConfirmOrder(response) }
        }
        row.addView(skip)
        row.addView(confirm)
        return row
    }

    private fun signInPrompt(): View {
        return TextView(appContext).apply {
            text = "Sign in to track today's intake →"
            setTextColor(0xFF1ED4B6.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(0, dp(8f).toInt(), 0, 0)
            setOnClickListener {
                val intent = Intent(appContext, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                runCatching { appContext.startActivity(intent) }
                showCollapsed()
            }
        }
    }

    private fun progressBar(progress: Float): View {
        val p = progress.coerceIn(0f, 1f)
        val container = LinearLayout(appContext).apply {
            orientation = LinearLayout.HORIZONTAL
            background = GradientDrawable().apply {
                cornerRadius = dp(4f)
                setColor(0x33FFFFFF)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(8f).toInt(),
            ).apply { topMargin = dp(6f).toInt(); bottomMargin = dp(4f).toInt() }
        }
        if (p > 0f) {
            val fill = View(appContext).apply {
                background = GradientDrawable().apply {
                    cornerRadius = dp(4f)
                    setColor(progressColor(p))
                }
            }
            container.addView(fill, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, p))
        }
        if (p < 1f) {
            container.addView(
                View(appContext),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f - p),
            )
        }
        return container
    }

    private fun progressColor(p: Float): Int = when {
        p >= 1f -> 0xFFEF4444.toInt()
        p >= 0.85f -> 0xFFFBBF24.toInt()
        else -> 0xFF34D399.toInt()
    }

    private fun goalProjectionColor(projected: Int, target: Int): Int {
        val ratio = projected.toFloat() / target.coerceAtLeast(1)
        return when {
            ratio > 1f -> 0xFFEF4444.toInt()
            ratio >= 0.85f -> 0xFFFBBF24.toInt()
            else -> 0xFFE7EEF6.toInt()
        }
    }

    private fun header(title: String): View {
        val row = LinearLayout(appContext).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dp(8f).toInt())
        }
        val titleTv = textView(title, titleSize = 12f, subdued = true).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f,
            ).apply { gravity = Gravity.CENTER_VERTICAL }
        }
        val close = TextView(appContext).apply {
            text = "✕"
            setTextColor(0xFF92A0B0.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTypeface(Typeface.DEFAULT_BOLD)
            setPadding(dp(8f).toInt(), 0, dp(4f).toInt(), 0)
            setOnClickListener { showCollapsed() }
        }
        row.addView(titleTv)
        row.addView(close)
        return row
    }

    private fun spinnerDots(): View {
        val row = LinearLayout(appContext).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8f).toInt(), 0, dp(8f).toInt())
        }
        row.addView(pulsingDot(0))
        row.addView(pulsingDot(150))
        row.addView(pulsingDot(300))
        return row
    }

    private fun pulsingDot(startDelayMs: Long): View {
        val dot = View(appContext).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#1ED4B6"))
            }
            layoutParams = LinearLayout.LayoutParams(dp(8f).toInt(), dp(8f).toInt()).apply {
                marginEnd = dp(6f).toInt()
            }
            alpha = 0.3f
        }
        val pulse = ValueAnimator.ofFloat(0.3f, 1f, 0.3f).apply {
            duration = 900
            startDelay = startDelayMs
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { dot.alpha = it.animatedValue as Float }
        }
        pulse.start()
        return dot
    }

    private fun itemRow(item: MatchedItem): View {
        val row = LinearLayout(appContext).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(2f).toInt(), 0, dp(2f).toInt())
        }
        val name = textView("${item.qty}× ${item.name}", titleSize = 13f).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f,
            )
        }
        val kcal = textView("${item.kcal} kcal", titleSize = 13f, subdued = true)
        row.addView(name)
        row.addView(kcal)
        return row
    }

    private fun card(): LinearLayout {
        return LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16f).toInt(), dp(14f).toInt(), dp(16f).toInt(), dp(14f).toInt())
            minimumWidth = dp(280f).toInt()
        }
    }

    private fun textView(
        text: String,
        titleSize: Float = 14f,
        subdued: Boolean = false,
        color: Int? = null,
        bold: Boolean = false,
    ): TextView {
        return TextView(appContext).apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, titleSize)
            setTextColor(color ?: if (subdued) 0xFF92A0B0.toInt() else 0xFFE7EEF6.toInt())
            if (bold) setTypeface(Typeface.DEFAULT_BOLD)
        }
    }

    private fun macroChip(value: String, unit: String): View {
        val v = LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(10f)
                setColor(0x33FFFFFF)
            }
            setPadding(dp(10f).toInt(), dp(6f).toInt(), dp(10f).toInt(), dp(6f).toInt())
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            lp.marginEnd = dp(6f).toInt()
            layoutParams = lp
        }
        v.addView(textView(value, titleSize = 16f, bold = true))
        v.addView(textView(unit, titleSize = 11f, subdued = true))
        return v
    }

    private fun divider(): View {
        return View(appContext).apply {
            setBackgroundColor(0x33FFFFFF)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(1f).toInt(),
            ).apply { topMargin = dp(8f).toInt(); bottomMargin = dp(8f).toInt() }
        }
    }

    private fun scoreColor(score: Int): Int = when {
        score >= 65 -> Color.parseColor("#34D399")
        score >= 45 -> Color.parseColor("#FBBF24")
        else -> Color.parseColor("#EF4444")
    }

    private fun dp(value: Float): Float =
        value * appContext.resources.displayMetrics.density

    private fun lerp(a: Int, b: Int, t: Float): Int = (a + (b - a) * t).toInt()
    private fun lerpF(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    private enum class State { NONE, COLLAPSED, LOADING, RESULT, ERROR, CONFIRMED }

    private companion object {
        const val MORPH_DURATION_MS = 280L
        const val CROSSFADE_OUT_MS = 120L
        const val CROSSFADE_IN_MS = 180L
        const val CROSSFADE_IN_DELAY_MS = 100L
        const val LOADING_TIP_INTERVAL_MS = 2200L
        const val CONFIRMED_AUTO_COLLAPSE_MS = 4500L

        val LOADING_TIPS = listOf(
            "Roti = ~100 kcal. Naan = ~260 kcal. The bread choice matters.",
            "One regular Coke ≈ 140 empty kcal — same as half a roti's worth.",
            "Most cart kcal hides in extras: dips, sides, and drinks.",
            "Average biryani: 500–600 kcal per restaurant serving.",
            "'Burst', 'Cheesy', 'Loaded' menu items are typically 30% more kcal.",
            "Butter chicken (490) vs chicken tikka masala (440) — same dish, lighter swap.",
            "Garlic naan ≈ 290 kcal. Replace with roti to save ~190 kcal per piece.",
            "A typical Pizza Mania (small) is ~640 kcal — about a third of your day.",
        )
    }
}

private class DragAndTapListener(
    private val params: () -> WindowManager.LayoutParams,
    private val onPositionChanged: (Int, Int) -> Unit,
    private val wm: WindowManager,
    private val rootViewProvider: () -> View?,
    private val onTap: () -> Unit,
    private val isCollapsed: () -> Boolean,
) : View.OnTouchListener {

    private var initialX = 0
    private var initialY = 0
    private var touchX = 0f
    private var touchY = 0f
    private var moved = false

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        if (!isCollapsed()) return false
        val p = params()
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = p.x
                initialY = p.y
                touchX = event.rawX
                touchY = event.rawY
                moved = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - touchX).toInt()
                val dy = (event.rawY - touchY).toInt()
                if (kotlin.math.abs(dx) > TOUCH_SLOP || kotlin.math.abs(dy) > TOUCH_SLOP) moved = true
                p.x = initialX + dx
                p.y = initialY + dy
                rootViewProvider()?.let { runCatching { wm.updateViewLayout(it, p) } }
            }
            MotionEvent.ACTION_UP -> {
                if (!moved) {
                    onTap()
                } else {
                    onPositionChanged(p.x, p.y)
                }
            }
        }
        return true
    }

    private companion object {
        const val TOUCH_SLOP = 12
    }
}
