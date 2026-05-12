package `in`.foodlens.app

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import `in`.foodlens.app.capture.ScreenCaptureService

/**
 * Transparent activity that hosts the MediaProjection consent dialog.
 *
 * Critical timing: we [finish] this Activity AND dismiss the bubble BEFORE
 * starting [ScreenCaptureService], with a small delay before the service
 * triggers MediaProjection. Without that, the captured frame includes
 * either our transparent activity or our own AI bubble — both of which
 * pollute OCR ("Al Food Lens", "Reading your cart…", etc).
 *
 * [FoodLensApp.captureActive] is also flipped on here so the
 * foreground-app poller in [`in`.foodlens.app.overlay.FloatingButtonService]
 * doesn't re-attach the bubble during the gap before capture.
 */
class MediaProjectionRequestActivity : ComponentActivity() {

    private val launcher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val resultCode = result.resultCode
        val data = result.data
        val app = applicationContext as FoodLensApp
        finish()
        if (resultCode == RESULT_OK && data != null) {
            app.captureActive = true
            app.bubbleManager.dismiss()
            Handler(Looper.getMainLooper()).postDelayed({
                ScreenCaptureService.start(app, resultCode, data)
            }, START_DELAY_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        launcher.launch(mpm.createScreenCaptureIntent())
    }

    companion object {
        private const val START_DELAY_MS = 250L

        fun intent(context: Context): Intent =
            Intent(context, MediaProjectionRequestActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
    }
}
