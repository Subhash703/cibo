package `in`.foodlens.app.overlay

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.res.ResourcesCompat
import `in`.foodlens.app.MainActivity
import `in`.foodlens.app.R
import `in`.foodlens.app.foreground.ForegroundAppPoller
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
 * and "Add to my day / No" action buttons. Tapping "Add to my day"
 * fires [onConfirmOrder] — wired by [`in`.foodlens.app.FoodLensApp] to
 * POST a meal log and call [showOrderConfirmed] on the way back.
 */
class OverlayBubbleManager(private val appContext: Context) {

    private val main = Handler(Looper.getMainLooper())
    private val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    /** Wired by FloatingButtonService at startup. Invoked when the user taps the AI circle. */
    var onCircleTap: () -> Unit = {}

    /** Wired by FoodLensApp. Fires when the user taps "Add to my day". */
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

    // Lazy-loaded brand typefaces (variable fonts in res/font/).
    private val jakarta: Typeface by lazy {
        ResourcesCompat.getFont(appContext, R.font.plus_jakarta_sans) ?: Typeface.DEFAULT
    }
    private val jakartaBold: Typeface by lazy {
        Typeface.create(jakarta, Typeface.BOLD)
    }
    private val fraunces: Typeface by lazy {
        ResourcesCompat.getFont(appContext, R.font.fraunces) ?: Typeface.SERIF
    }
    private val frauncesItalic: Typeface by lazy {
        Typeface.create(fraunces, Typeface.ITALIC)
    }

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

        // Whether this morph is expanding (going to a > collapsed surface).
        // We snap the *expanded* window to WRAP_CONTENT on animation-end so
        // the visible card never carries extra empty space when our measure
        // pass over-estimates (which it does for some layouts).
        val isExpanded = targetW > collapsedSizePx
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
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (!isExpanded) return
                    params.width = targetW
                    params.height = WindowManager.LayoutParams.WRAP_CONTENT
                    runCatching { wm.updateViewLayout(root, params) }
                }
            })
            start()
        }
    }

    private fun ensureRoot(): FrameLayout {
        rootView?.let { return it }
        // Background is transparent; each render method colors its own
        // content (glass bubble for collapsed, warm cream card for expanded)
        // so the same window can morph between very different surfaces.
        val root = FrameLayout(appContext).apply {
            background = GradientDrawable().apply {
                cornerRadius = collapsedCornerRadius
                setColor(Color.TRANSPARENT)
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
        // Glassy translucent navy disc with a faint teal hairline + the
        // Cibo logo at center. Sits over the host app without competing
        // for attention.
        val container = FrameLayout(appContext).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(GLASS_NAVY_TRANSLUCENT)
                setStroke(dp(1f).toInt(), GLASS_STROKE_TEAL)
            }
        }
        val logo = ImageView(appContext).apply {
            setImageResource(R.drawable.cibo_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
            val pad = dp(12f).toInt()
            setPadding(pad, pad, pad, pad)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
        }
        container.addView(logo)
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
        val card = creamCard()
        card.addView(eyebrowRow(label = "READING…"))

        val phase = textView(
            "Looking at your cart…",
            titleSize = 17f,
            color = INK,
        ).apply { setTypeface(fraunces, Typeface.ITALIC) }
        loadingPhaseTv = phase
        card.addView(phase)

        card.addView(
            textView(
                "Reading items, portions, totals.",
                titleSize = 13f,
                color = INK_SUBDUED,
            ).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(4f).toInt(); bottomMargin = dp(10f).toInt() }
            },
        )

        card.addView(spinnerDots())

        val tip = textView("💡 ${LOADING_TIPS[0]}", titleSize = 12f, color = INK_SUBDUED).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(10f).toInt() }
        }
        loadingTipTv = tip
        card.addView(tip)

        return card
    }

    private fun renderError(message: String): View {
        val card = creamCard()
        card.addView(eyebrowRow(label = "OOPS"))
        card.addView(textView("Couldn't read this cart.",
            titleSize = 18f, color = INK).apply {
            setTypeface(fraunces, Typeface.ITALIC)
        })
        card.addView(textView(message, titleSize = 13f, color = INK_SUBDUED).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(6f).toInt() }
        })
        return card
    }

    private fun renderResult(r: AnalyzeResponse): View {
        // Option C layout: eyebrow → observational quote → compact KCAL/macro
        // strip → SWAP-as-protagonist → action buttons.
        val card = creamCard()

        card.addView(eyebrowRow(label = labelFromScore(r.healthScore)))
        card.addView(quoteText(observationalCopy(r)))
        card.addView(compactStrip(r))

        r.suggestions.firstOrNull()?.let { suggestion ->
            card.addView(swapHero(suggestion, r.restaurantName))
        }

        if (r.dailySummary != null) {
            card.addView(actionRow(r))
        } else {
            card.addView(signInPrompt())
        }

        return card
    }

    private fun renderConfirmed(s: DailySummary): View {
        val card = creamCard()
        card.addView(eyebrowRow(label = "ADDED"))

        card.addView(textView(
            "On your day.",
            titleSize = 22f,
            color = INK,
        ).apply { setTypeface(frauncesItalic) })

        card.addView(
            textView(
                "${s.consumedKcal} kcal so far · ${s.remainingKcal} kcal left today",
                titleSize = 13f,
                color = INK_SUBDUED,
            ).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(4f).toInt(); bottomMargin = dp(8f).toInt() }
            },
        )

        val progress = (s.consumedKcal.toFloat() / s.dailyKcalTarget).coerceIn(0f, 1f)
        card.addView(progressBar(progress))

        card.addView(
            textView(
                "Goal: ${s.dailyKcalTarget} kcal · ${s.logCount} order${if (s.logCount == 1) "" else "s"} today",
                titleSize = 11f,
                color = INK_SUBDUED,
            ).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(6f).toInt() }
            },
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

    // ──────────────── Design-C builders ────────────────

    private fun creamCard(): LinearLayout {
        return LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(20f)
                setColor(CREAM_BG)
            }
            setPadding(dp(20f).toInt(), dp(16f).toInt(), dp(20f).toInt(), dp(16f).toInt())
            minimumWidth = dp(320f).toInt()
        }
    }

    private fun eyebrowRow(label: String): View {
        val row = LinearLayout(appContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(10f).toInt() }
        }
        val logo = ImageView(appContext).apply {
            setImageResource(R.drawable.cibo_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(dp(18f).toInt(), dp(18f).toInt()).apply {
                marginEnd = dp(6f).toInt()
            }
        }
        val word = TextView(appContext).apply {
            text = "cibo"
            setTypeface(jakartaBold)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(INK)
        }
        val pill = TextView(appContext).apply {
            text = label
            setTypeface(jakartaBold)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            letterSpacing = 0.12f
            setTextColor(INK)
            background = GradientDrawable().apply {
                cornerRadius = dp(999f)
                setColor(PILL_BG)
            }
            setPadding(dp(8f).toInt(), dp(3f).toInt(), dp(8f).toInt(), dp(3f).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { marginStart = dp(8f).toInt() }
        }
        val spacer = View(appContext).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        }
        val close = TextView(appContext).apply {
            text = "✕"
            setTextColor(INK_SUBDUED)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setPadding(dp(8f).toInt(), 0, dp(2f).toInt(), 0)
            setOnClickListener { showCollapsed() }
        }
        row.addView(logo); row.addView(word); row.addView(pill); row.addView(spacer); row.addView(close)
        return row
    }

    private fun quoteText(text: String): View {
        return TextView(appContext).apply {
            this.text = "“$text”"
            setTypeface(frauncesItalic)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            setTextColor(INK)
            setLineSpacing(0f, 1.2f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(12f).toInt() }
        }
    }

    private fun compactStrip(r: AnalyzeResponse): View {
        // Two stacked rows so weights can't squeeze either the kcal number
        // (was clipping the Fraunces descenders) or the macros (was forcing
        // "KCAL" to wrap as "KCA / L" on narrow screens).
        val container = LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(14f)
                setColor(STRIP_BG)
            }
            setPadding(dp(14f).toInt(), dp(10f).toInt(), dp(14f).toInt(), dp(10f).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(12f).toInt() }
        }

        // Row 1: big KCAL number (+ "KCAL" suffix) on the left, score pill on the right.
        val topRow = LinearLayout(appContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        }
        val numLabel = LinearLayout(appContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        numLabel.addView(TextView(appContext).apply {
            text = "${r.macros.kcal}"
            setTypeface(fraunces, Typeface.BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setTextColor(INK)
            includeFontPadding = false
        })
        numLabel.addView(TextView(appContext).apply {
            text = " KCAL"
            setTypeface(jakartaBold)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            letterSpacing = 0.15f
            setTextColor(INK_SUBDUED)
            setPadding(dp(4f).toInt(), 0, 0, dp(3f).toInt())
            maxLines = 1
        })
        topRow.addView(numLabel)

        topRow.addView(TextView(appContext).apply {
            text = "${r.healthScore}"
            setTypeface(jakartaBold)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(INK)
            background = GradientDrawable().apply {
                cornerRadius = dp(999f)
                setColor(PILL_BG)
            }
            setPadding(dp(10f).toInt(), dp(4f).toInt(), dp(10f).toInt(), dp(4f).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { marginStart = dp(8f).toInt() }
        })
        container.addView(topRow)

        // Row 2: macro dots, evenly spaced.
        val macroRow = LinearLayout(appContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6f).toInt(), 0, 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        }
        macroRow.addView(macroDot(MACRO_PROTEIN, "P${r.macros.proteinG}"))
        macroRow.addView(macroDot(MACRO_FAT, "F${r.macros.fatG}"))
        macroRow.addView(macroDot(MACRO_CARB, "C${r.macros.carbsG}"))
        container.addView(macroRow)

        return container
    }

    private fun macroDot(color: Int, label: String): View {
        val row = LinearLayout(appContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, dp(8f).toInt(), 0)
        }
        row.addView(View(appContext).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
            }
            layoutParams = LinearLayout.LayoutParams(dp(8f).toInt(), dp(8f).toInt()).apply {
                marginEnd = dp(4f).toInt()
            }
        })
        row.addView(TextView(appContext).apply {
            text = label
            setTypeface(jakartaBold)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(INK)
        })
        return row
    }

    @SuppressLint("SetTextI18n")
    private fun swapHero(
        suggestion: `in`.foodlens.app.network.Suggestion,
        restaurantName: String?,
    ): View {
        val card = LinearLayout(appContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(14f)
                setColor(Color.WHITE)
                setStroke(dp(1f).toInt(), 0x14000000)
            }
            setPadding(dp(10f).toInt(), dp(10f).toInt(), dp(14f).toInt(), dp(10f).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(14f).toInt() }
            isClickable = true
            isFocusable = true
            setOnClickListener { onSwapTap(suggestion.text, restaurantName) }
        }

        // Photo placeholder — Gemini doesn't hand us a URL today, so a warm
        // beige tile reads as "food image" without falsely promising one.
        card.addView(View(appContext).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(10f)
                setColor(PHOTO_PLACEHOLDER)
            }
            layoutParams = LinearLayout.LayoutParams(dp(48f).toInt(), dp(48f).toInt()).apply {
                marginEnd = dp(12f).toInt()
            }
        })

        val col = LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        col.addView(TextView(appContext).apply {
            text = "SWAP"
            setTypeface(jakartaBold)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
            letterSpacing = 0.15f
            setTextColor(INK_SUBDUED)
        })
        col.addView(TextView(appContext).apply {
            text = suggestion.text
            setTypeface(jakartaBold)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(INK)
            setLineSpacing(0f, 1.1f)
        })
        if (suggestion.kcalDelta != 0) {
            val deltaText = if (suggestion.kcalDelta < 0)
                "${suggestion.kcalDelta} kcal"
            else
                "+${suggestion.kcalDelta} kcal"
            col.addView(TextView(appContext).apply {
                text = deltaText
                setTypeface(jakartaBold)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTextColor(DELTA_GREEN_TEXT)
                background = GradientDrawable().apply {
                    cornerRadius = dp(999f)
                    setColor(DELTA_GREEN_BG)
                }
                setPadding(dp(8f).toInt(), dp(2f).toInt(), dp(8f).toInt(), dp(2f).toInt())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(4f).toInt() }
            })
        }
        card.addView(col)

        card.addView(TextView(appContext).apply {
            text = "Open →"
            setTypeface(jakartaBold)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(ACCENT_GREEN)
            setPadding(dp(8f).toInt(), 0, 0, 0)
        })

        return card
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun actionRow(r: AnalyzeResponse): View {
        val row = LinearLayout(appContext).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        }
        val log = TextView(appContext).apply {
            text = "Log to my day"
            setTypeface(jakartaBold)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(CREAM_BG)
            background = GradientDrawable().apply {
                cornerRadius = dp(999f)
                setColor(INK)
            }
            gravity = Gravity.CENTER
            setPadding(dp(16f).toInt(), dp(12f).toInt(), dp(16f).toInt(), dp(12f).toInt())
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f,
            ).apply { marginEnd = dp(8f).toInt() }
            setOnClickListener { onConfirmOrder(r) }
        }
        val skip = TextView(appContext).apply {
            text = "Just looking"
            setTypeface(jakartaBold)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(INK)
            background = GradientDrawable().apply {
                cornerRadius = dp(999f)
                setColor(Color.TRANSPARENT)
                setStroke(dp(1f).toInt(), 0x33000000)
            }
            gravity = Gravity.CENTER
            setPadding(dp(16f).toInt(), dp(12f).toInt(), dp(16f).toInt(), dp(12f).toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { showCollapsed() }
        }
        row.addView(log)
        row.addView(skip)
        return row
    }

    private fun signInPrompt(): View {
        return TextView(appContext).apply {
            text = "Sign in to track today's intake →"
            setTypeface(jakartaBold)
            setTextColor(ACCENT_GREEN)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(0, dp(4f).toInt(), 0, dp(4f).toInt())
            setOnClickListener {
                val intent = Intent(appContext, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                runCatching { appContext.startActivity(intent) }
                showCollapsed()
            }
        }
    }

    // ──────────────── Score → label ────────────────

    private fun labelFromScore(score: Int): String = when {
        score >= 75 -> "GOOD PICK"
        score >= 55 -> "FAIR PICK"
        score >= 35 -> "HEAVIER"
        else        -> "HEAVY MEAL"
    }

    private fun observationalCopy(r: AnalyzeResponse): String {
        // Observe, don't grade — the design-C insight. Use macros + score
        // to pick a warm, descriptive sentence. Avoids "you should…" tone.
        val pHigh = r.macros.proteinG >= 30
        val fHigh = r.macros.fatG >= 50
        return when {
            r.healthScore >= 75 && pHigh ->
                "Solid cart. Protein's there, calories sit well in your day."
            r.healthScore >= 55 && pHigh && fHigh ->
                "Hearty cart. Protein's solid — one swap brings the fats down."
            r.healthScore >= 55 ->
                "Hearty pick. One quick upgrade lightens it without losing the meal."
            else ->
                "Heavier today. One smart swap pulls it back into a comfortable range."
        }
    }

    // ──────────────── Swap deep-link / clipboard ────────────────

    private fun onSwapTap(suggestionText: String, restaurantName: String?) {
        val swap = extractSwapTarget(suggestionText)

        // Always copy the swap term so the user can paste it directly in
        // the restaurant's menu search box (food apps) or the host app's
        // search bar (grocery apps).
        val clip = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clip.setPrimaryClip(ClipData.newPlainText("Cibo swap", swap))

        val foreground = runCatching {
            ForegroundAppPoller.currentForegroundPackage(appContext)
        }.getOrNull()

        // Food-delivery apps lock the cart to ONE restaurant. So the swap
        // has to come from that same restaurant's menu — searching globally
        // would scatter the user across the wrong restaurants. Route them
        // back to the named restaurant; the clipboard has the swap term
        // for the menu's search box.
        //
        // Grocery / quick-commerce (Blinkit, BigBasket) have no restaurant
        // concept, so an item-level search is the right destination.
        val intent: Intent? = when {
            isFoodDelivery(foreground) && !restaurantName.isNullOrBlank() ->
                restaurantSearchIntent(foreground, restaurantName)
            isGrocery(foreground) ->
                itemSearchIntent(foreground, swap)
            else -> null
        }

        val toastMsg = when {
            intent != null && !restaurantName.isNullOrBlank() ->
                "Search “$swap” on $restaurantName's menu — copied for you."
            intent != null ->
                "Search “$swap” — copied for you."
            else ->
                "Copied “$swap” — paste in the menu search."
        }

        if (intent != null && runCatching {
                appContext.startActivity(intent.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
            }.isSuccess) {
            // Show the toast after the app switch lands so the user sees it.
            main.postDelayed({
                Toast.makeText(appContext, toastMsg, Toast.LENGTH_LONG).show()
            }, 500)
            return
        }

        Toast.makeText(appContext, toastMsg, Toast.LENGTH_LONG).show()
    }

    private fun isFoodDelivery(packageName: String?): Boolean = packageName in setOf(
        "in.swiggy.android",
        "com.application.zomato",
        "com.dominos", "com.dominos.app.android",
        "com.mcdonalds.mobileapp",
        "com.yum.kfc",
        "com.ubercab.eats",
        "com.dd.doordash",
    )

    private fun isGrocery(packageName: String?): Boolean = packageName in setOf(
        "com.grofers.customerapp",   // Blinkit
        "com.bigbasket.mobileapp",
        "in.dunzo.user",
    )

    private fun extractSwapTarget(text: String): String {
        val arrow = text.indexOfAny(charArrayOf('→', '➜', '➔'))
        if (arrow > -1) {
            return text.substring(arrow + 1)
                .substringBefore('—').substringBefore('-').trim()
        }
        val forIdx = text.lowercase().indexOf(" for ")
        if (forIdx > -1) {
            return text.substring(forIdx + 5)
                .substringBefore('—').substringBefore('-').trim()
        }
        return text.take(40).trim()
    }

    private fun restaurantSearchIntent(packageName: String?, restaurant: String): Intent? {
        val encoded = Uri.encode(restaurant)
        val url = when (packageName) {
            "in.swiggy.android"       -> "https://www.swiggy.com/search?query=$encoded"
            "com.application.zomato"  -> "https://www.zomato.com/search?q=$encoded"
            else -> null
        } ?: return null
        return Intent(Intent.ACTION_VIEW, Uri.parse(url))
    }

    private fun itemSearchIntent(packageName: String?, query: String): Intent? {
        val encoded = Uri.encode(query)
        val url = when (packageName) {
            "com.grofers.customerapp"  -> "https://blinkit.com/s/?q=$encoded"
            "com.bigbasket.mobileapp"  -> "https://www.bigbasket.com/ps/?q=$encoded"
            else -> null
        } ?: return null
        return Intent(Intent.ACTION_VIEW, Uri.parse(url))
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

        // Design-C overlay palette — warm cream card, dark navy ink,
        // muted macro dots, soft delta-green pill for savings.
        const val CREAM_BG          = 0xFFFAF6EE.toInt()
        const val INK               = 0xFF0B1226.toInt()
        const val INK_SUBDUED       = 0xFF6E7184.toInt()
        const val STRIP_BG          = 0x0F000000  // ~6% black overlay on cream
        const val PILL_BG           = 0xFFEDE3D0.toInt()
        const val PHOTO_PLACEHOLDER = 0xFFDDD0BD.toInt()
        const val ACCENT_GREEN      = 0xFF1C8F66.toInt()
        const val DELTA_GREEN_BG    = 0xFFD2EDDF.toInt()
        const val DELTA_GREEN_TEXT  = 0xFF0B5C3E.toInt()
        const val MACRO_PROTEIN     = 0xFF6FAEE0.toInt()
        const val MACRO_FAT         = 0xFFEAB360.toInt()
        const val MACRO_CARB        = 0xFFB97A4E.toInt()

        // Bubble (collapsed) glass tokens — translucent so the host app
        // shows through without the bubble feeling like a sticker.
        const val GLASS_NAVY_TRANSLUCENT = 0xCC0B1226.toInt()
        const val GLASS_STROKE_TEAL      = 0x551ED4B6

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
