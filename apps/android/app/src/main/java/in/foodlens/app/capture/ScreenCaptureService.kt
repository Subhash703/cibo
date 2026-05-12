package `in`.foodlens.app.capture

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import `in`.foodlens.app.foodLensApp
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.launch

/**
 * Foreground service that owns the MediaProjection session for a single
 * capture, encodes the captured frame as JPEG, and uploads it to the
 * vision-LLM analyzer (Gemini 2.5 Flash via the backend).
 *
 * If the user is signed in, the bearer token rides along on the upload —
 * the backend then includes today's daily-summary (consumed / remaining
 * kcal) on the response, and the bubble renders it.
 */
class ScreenCaptureService : LifecycleService() {

    private lateinit var projectionManager: MediaProjectionManager
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var consumed = false

    private val captureTimeout = Runnable {
        if (!consumed) {
            Log.w(TAG, "capture timed out without a frame")
            foodLensApp.captureActive = false
            foodLensApp.bubbleManager.showError("Couldn't capture this screen. Try again?")
            stopSelf()
        }
    }

    override fun onCreate() {
        super.onCreate()
        projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startInForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val data: Intent? = intent?.getParcelableExtra(EXTRA_DATA)
        if (data == null || resultCode == 0) {
            foodLensApp.captureActive = false
            stopSelf()
            return START_NOT_STICKY
        }

        foodLensApp.bubbleManager.dismiss()

        projection = projectionManager.getMediaProjection(resultCode, data).also { mp ->
            mp.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() { stopSelf() }
            }, mainHandler)
        }
        mainHandler.postDelayed(::captureSingleFrame, BUBBLE_REMOVE_DELAY_MS)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(captureTimeout)
        virtualDisplay?.release()
        imageReader?.close()
        runCatching { projection?.stop() }
        foodLensApp.captureActive = false
        super.onDestroy()
    }

    @SuppressLint("InlinedApi")
    private fun startInForeground() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Screen analysis", NotificationManager.IMPORTANCE_LOW),
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Analyzing this screen…")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
        )
    }

    private fun captureSingleFrame() {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)

        val reader = ImageReader.newInstance(
            metrics.widthPixels,
            metrics.heightPixels,
            PixelFormat.RGBA_8888,
            2,
        )
        imageReader = reader

        virtualDisplay = projection?.createVirtualDisplay(
            "FoodLensCapture",
            metrics.widthPixels,
            metrics.heightPixels,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null,
            null,
        )

        mainHandler.postDelayed(captureTimeout, CAPTURE_TIMEOUT_MS)

        reader.setOnImageAvailableListener({ r ->
            if (consumed) {
                runCatching { r.acquireLatestImage()?.close() }
                return@setOnImageAvailableListener
            }
            val image: Image? = r.acquireLatestImage()
            if (image != null) {
                consumed = true
                mainHandler.removeCallbacks(captureTimeout)
                val bitmap = image.toBitmap()
                image.close()
                foodLensApp.bubbleManager.showLoading()
                handleBitmap(bitmap)
            }
        }, mainHandler)
    }

    private fun handleBitmap(bitmap: Bitmap) {
        val app = foodLensApp
        lifecycleScope.launch {
            try {
                val jpeg = bitmapToJpeg(bitmap, MAX_DIM_PX, JPEG_QUALITY)
                bitmap.recycle()
                Log.d(TAG, "uploading ${jpeg.size} bytes; signed-in=${app.authState.idToken != null}")
                val response = app.analyzer.analyzeVision(
                    jpegBytes = jpeg,
                    regionHint = "IN",
                    idToken = app.authState.idToken,
                )
                if (response.macros.kcal == 0 && response.items.isEmpty()) {
                    app.bubbleManager.showError("No food detected on this screen.")
                } else {
                    app.bubbleManager.showResult(response)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "vision analyze failed", t)
                app.bubbleManager.showError(t.message ?: "Network error")
            } finally {
                app.captureActive = false
                stopSelf()
            }
        }
    }

    companion object {
        private const val TAG = "FoodLensCapture"
        private const val CHANNEL_ID = "foodlens.capture"
        private const val NOTIFICATION_ID = 1002
        private const val CAPTURE_TIMEOUT_MS = 5_000L
        private const val BUBBLE_REMOVE_DELAY_MS = 150L
        private const val MAX_DIM_PX = 1024
        private const val JPEG_QUALITY = 85
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_DATA = "data"

        fun start(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, ScreenCaptureService::class.java).apply {
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_DATA, data)
            }
            context.startForegroundService(intent)
        }
    }
}

private fun Image.toBitmap(): Bitmap {
    val plane = planes[0]
    val buffer = plane.buffer
    val pixelStride = plane.pixelStride
    val rowStride = plane.rowStride
    val rowPadding = rowStride - pixelStride * width
    val padded = Bitmap.createBitmap(
        width + rowPadding / pixelStride,
        height,
        Bitmap.Config.ARGB_8888,
    )
    padded.copyPixelsFromBuffer(buffer)
    return Bitmap.createBitmap(padded, 0, 0, width, height)
}

private fun bitmapToJpeg(bitmap: Bitmap, maxDim: Int, quality: Int): ByteArray {
    val w = bitmap.width
    val h = bitmap.height
    val scaled = if (maxOf(w, h) > maxDim) {
        val scale = maxDim.toFloat() / maxOf(w, h)
        Bitmap.createScaledBitmap(bitmap, (w * scale).toInt(), (h * scale).toInt(), true)
    } else {
        bitmap
    }
    val baos = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos)
    if (scaled !== bitmap) scaled.recycle()
    return baos.toByteArray()
}
