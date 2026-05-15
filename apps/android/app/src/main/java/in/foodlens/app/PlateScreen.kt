package `in`.foodlens.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import `in`.foodlens.app.auth.UserProfile
import `in`.foodlens.app.network.AnalyzeClient
import `in`.foodlens.app.network.CiboHttpException
import `in`.foodlens.app.network.CoachVerdict
import `in`.foodlens.app.network.DailySummary
import `in`.foodlens.app.network.MatchedItem
import `in`.foodlens.app.network.MealLogPublic
import `in`.foodlens.app.network.MealLogRequest
import `in`.foodlens.app.network.PlateAnalyzeResponse
import `in`.foodlens.app.ui.CiboColors
import `in`.foodlens.app.ui.CiboType
import `in`.foodlens.app.ui.InsightCard
import `in`.foodlens.app.ui.PaywallDialog
import `in`.foodlens.app.ui.VerdictSignal
import `in`.foodlens.app.ui.VerdictBanner as CiboVerdictBanner
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * State machine for the Plate tab's snap-and-analyse flow.
 *
 *  Idle      → user is browsing / two-button surface + today's log
 *  Analyzing → JPEG uploaded, waiting for Gemini, preview visible
 *  Result    → response received, render verdict + actions + image preview
 *  Logged    → user tapped "Add to my day"; brief success splash, auto-reset
 *  Error     → recoverable failure (network / Gemini 503 / no food in image)
 */
private sealed interface PlateUiState {
    data object Idle : PlateUiState
    data class Analyzing(val capturedUri: Uri?) : PlateUiState
    data class Result(val response: PlateAnalyzeResponse, val capturedUri: Uri?) : PlateUiState
    data class Logged(val summary: DailySummary) : PlateUiState
    data class Error(val message: String) : PlateUiState
}

@Composable
fun PlateScreen(
    user: UserProfile?,
    analyzer: AnalyzeClient,
    idToken: String?,
    onSignInRequested: () -> Unit,
) {
    if (user == null || idToken == null) {
        SignInGate(onSignInRequested = onSignInRequested)
        return
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val app = context.foodLensApp

    // Single source of truth for today — shared with HomeScreen and the
    // overlay's order-confirmed callback.
    val summary by app.dailySummary.collectAsState()
    val mealLogs by app.todayMealLogs.collectAsState()
    var summaryLoading by remember { mutableStateOf(true) }
    var state by remember { mutableStateOf<PlateUiState>(PlateUiState.Idle) }
    var pendingCaptureUri by remember { mutableStateOf<Uri?>(null) }
    var paywallMessage by remember { mutableStateOf<String?>(null) }

    suspend fun refreshDaily() {
        runCatching { analyzer.getTodaySummary(idToken) }.onSuccess { app.setSummary(it) }
        runCatching { analyzer.getTodayMealLogs(idToken) }.onSuccess { app.setMealLogs(it) }
    }

    LaunchedEffect(idToken) {
        summaryLoading = true
        refreshDaily()
        summaryLoading = false
    }

    fun analyzeUri(uri: Uri) {
        scope.launch {
            state = PlateUiState.Analyzing(uri)
            try {
                val jpeg = readUriAsJpeg(context, uri)
                val response = analyzer.analyzePlate(idToken, jpeg)
                state = PlateUiState.Result(response, uri)
                response.dailySummary?.let { app.setSummary(it) }
            } catch (e: CiboHttpException) {
                if (e.code == 402) {
                    state = PlateUiState.Idle
                    paywallMessage = e.message
                } else {
                    state = PlateUiState.Error(e.message)
                }
            } catch (t: Throwable) {
                state = PlateUiState.Error(t.message ?: "Couldn't analyse this photo")
            }
        }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { saved ->
        val uri = pendingCaptureUri
        pendingCaptureUri = null
        if (saved && uri != null) analyzeUri(uri)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            launchCamera(context, takePictureLauncher) { pendingCaptureUri = it }
        } else {
            state = PlateUiState.Error(
                "Camera permission denied. Allow it in Settings → Apps → Cibo.",
            )
        }
    }

    val pickLibraryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) analyzeUri(uri)
    }

    paywallMessage?.let { msg ->
        PaywallDialog(message = msg, onDismiss = { paywallMessage = null })
    }

    AnimatedContent(
        targetState = state,
        transitionSpec = {
            fadeIn(animationSpec = tween(200)) togetherWith fadeOut(animationSpec = tween(150))
        },
        label = "plate-state",
        modifier = Modifier.fillMaxSize(),
    ) { current ->
        when (current) {
            PlateUiState.Idle -> IdleSurface(
                user = user,
                summary = summary,
                summaryLoading = summaryLoading,
                mealLogs = mealLogs,
                onTakePhoto = {
                    val grant = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.CAMERA,
                    )
                    if (grant == PackageManager.PERMISSION_GRANTED) {
                        launchCamera(context, takePictureLauncher) { pendingCaptureUri = it }
                    } else {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                },
                onPickFromLibrary = {
                    pickLibraryLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
            )

            is PlateUiState.Analyzing -> AnalyzingSurface(capturedUri = current.capturedUri)

            is PlateUiState.Result -> ResultSurface(
                response = current.response,
                capturedUri = current.capturedUri,
                onTryAnother = { state = PlateUiState.Idle },
                onAddToLog = {
                    scope.launch {
                        runCatching {
                            analyzer.logMeal(
                                idToken = idToken,
                                request = MealLogRequest(
                                    macros = current.response.macros,
                                    items = listOf(
                                        MatchedItem(
                                            name = current.response.dishName,
                                            qty = 1,
                                            kcal = current.response.macros.kcal,
                                        ),
                                    ),
                                    healthScore = current.response.healthScore,
                                ),
                            )
                        }.onSuccess { updated ->
                            app.setSummary(updated)
                            // Refresh the today list too so it shows up in Idle.
                            runCatching { analyzer.getTodayMealLogs(idToken) }
                                .onSuccess { app.setMealLogs(it) }
                            state = PlateUiState.Logged(updated)
                        }.onFailure {
                            state = PlateUiState.Error(it.message ?: "Couldn't log this meal")
                        }
                    }
                },
            )

            is PlateUiState.Logged -> LoggedSurface(
                summary = current.summary,
                onDone = { state = PlateUiState.Idle },
            )

            is PlateUiState.Error -> ErrorSurface(
                message = current.message,
                onDismiss = { state = PlateUiState.Idle },
            )
        }
    }
}

// --- States ---------------------------------------------------------------

@Composable
private fun IdleSurface(
    user: UserProfile,
    summary: DailySummary?,
    summaryLoading: Boolean,
    mealLogs: List<MealLogPublic>,
    onTakePhoto: () -> Unit,
    onPickFromLibrary: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        GreetingHeader(name = user.name ?: user.email.substringBefore("@"), summary = summary)
        DailyFuelCard(summary = summary, loading = summaryLoading)

        Text(
            "Snap a photo to check your meal",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "For real-world meals — home-cooked, restaurant, anything outside " +
                "delivery apps. We'll tell you if it fits your day.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PlateActionButton(
                icon = Icons.Outlined.PhotoCamera,
                label = "Take a photo",
                onClick = onTakePhoto,
                modifier = Modifier.weight(1f),
                primary = true,
            )
            PlateActionButton(
                icon = Icons.Outlined.PhotoLibrary,
                label = "From library",
                onClick = onPickFromLibrary,
                modifier = Modifier.weight(1f),
                primary = false,
            )
        }

        if (mealLogs.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                "TODAY'S LOG",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    mealLogs.forEachIndexed { idx, log ->
                        MealLogRow(log)
                        if (idx < mealLogs.lastIndex) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun MealLogRow(log: MealLogPublic) {
    val firstItem = log.items.firstOrNull()
    val displayName = firstItem?.name ?: "Meal"
    val tail = if (log.items.size > 1) " + ${log.items.size - 1} more" else ""
    val timeLabel = remember(log.loggedAt) { formatLoggedAt(log.loggedAt) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            timeLabel,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            modifier = Modifier.width(72.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                displayName + tail,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            "${log.kcal} kcal",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
        )
    }
}

private fun formatLoggedAt(iso: String): String {
    // Backend returns naive ISO datetime in UTC; convert to local time-of-day.
    return runCatching {
        val parsed = LocalDateTime.parse(iso)
        val zoned = parsed.atZone(ZoneOffset.UTC).withZoneSameInstant(ZoneId.systemDefault())
        zoned.format(DateTimeFormatter.ofPattern("h:mm a"))
    }.getOrDefault(iso.take(5))
}

@Composable
private fun AnalyzingSurface(capturedUri: Uri?) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        capturedUri?.let { uri ->
            UriImage(
                uri = uri,
                modifier = Modifier
                    .size(200.dp)
                    .clip(RoundedCornerShape(20.dp)),
            )
            Spacer(Modifier.height(24.dp))
        }
        CircularProgressIndicator(strokeWidth = 3.dp, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(20.dp))
        Text(
            "Looking at your plate…",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Reading colours, textures, portions.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ResultSurface(
    response: PlateAnalyzeResponse,
    capturedUri: Uri?,
    onTryAnother: () -> Unit,
    onAddToLog: () -> Unit,
) {
    var logging by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        VerdictBanner(response.verdict)

        capturedUri?.let { uri ->
            UriImage(
                uri = uri,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.4f)
                    .clip(RoundedCornerShape(20.dp)),
            )
        }

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    "DISH IDENTIFIED",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    response.dishName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                if (response.dishDescription.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        response.dishDescription,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    NutrientPill("${response.macros.kcal}", "kcal", emphasis = true)
                    NutrientPill("${response.macros.proteinG}g", "protein")
                    NutrientPill("${response.macros.fatG}g", "fat")
                    NutrientPill("${response.macros.carbsG}g", "carbs")
                }
                if (response.verdict.reason.isNotBlank()) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        response.verdict.reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onTryAnother,
                enabled = !logging,
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text("Try another")
            }
            Button(
                onClick = {
                    logging = true
                    onAddToLog()
                },
                enabled = !logging,
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                if (logging) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("Add to my day", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun LoggedSurface(summary: DailySummary, onDone: () -> Unit) {
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(2200)
        onDone()
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(Color(0xFF34D399)),
            contentAlignment = Alignment.Center,
        ) {
            Text("✓", fontSize = 56.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        Spacer(Modifier.height(24.dp))
        Text("Added to your day", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            "${summary.remainingKcal} kcal left for today.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
        )
    }
}

@Composable
private fun ErrorSurface(message: String, onDismiss: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Couldn't analyse this photo",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onDismiss, shape = RoundedCornerShape(14.dp)) {
            Text("Back")
        }
    }
}

// --- Sub-components --------------------------------------------------------

@Composable
private fun VerdictBanner(verdict: CoachVerdict) {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(verdict) { entered = true }

    val scale by animateFloatAsState(
        targetValue = if (entered) 1f else 0.92f,
        animationSpec = spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioMediumBouncy,
        ),
        label = "verdict-scale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "verdict-alpha",
    )

    val signal = when (verdict.signal.lowercase()) {
        "green"  -> VerdictSignal.Green
        "red"    -> VerdictSignal.Red
        else     -> VerdictSignal.Yellow
    }
    val headline = when (signal) {
        VerdictSignal.Green  -> "Go for it"
        VerdictSignal.Yellow -> "Fair choice"
        VerdictSignal.Red    -> "Heads up"
    }
    Box(
        Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
            this.alpha = alpha
        },
    ) {
        CiboVerdictBanner(
            signal = signal,
            headline = headline,
            detail = verdict.oneLiner,
        )
    }
}

@Composable
private fun UriImage(uri: Uri, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap: ImageBitmap? = remember(uri) {
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.Options().also { it.inSampleSize = 2 }
                    .let { opts -> BitmapFactory.decodeStream(stream, null, opts) }
                    ?.asImageBitmap()
            }
        }.getOrNull()
    }
    bitmap?.let {
        Image(
            bitmap = it,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    }
}

@Composable
private fun NutrientPill(value: String, label: String, emphasis: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = if (emphasis) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun GreetingHeader(name: String, summary: DailySummary?) {
    val greeting = when (LocalTime.now().hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..20 -> "Good evening"
        else -> "Late night"
    }
    Column {
        Text(
            "$greeting, $name.",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = summary?.let {
                "You have ${it.remainingKcal} kcal of headroom for today."
            } ?: "Listening to your day.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
        )
    }
}

@Composable
private fun DailyFuelCard(summary: DailySummary?, loading: Boolean) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when {
                loading && summary == null -> {
                    Box(
                        modifier = Modifier.size(180.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(strokeWidth = 3.dp)
                    }
                }
                summary != null -> {
                    FuelRing(summary)
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        MacroStat("${summary.consumedProteinG}g", "PROTEIN")
                        MacroStat("${summary.consumedCarbsG}g", "CARBS")
                        MacroStat("${summary.consumedFatG}g", "FATS")
                    }
                }
                else -> Text("No data yet.", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun FuelRing(summary: DailySummary) {
    val target = summary.dailyKcalTarget.coerceAtLeast(1)
    val progressTarget = (summary.consumedKcal.toFloat() / target).coerceIn(0f, 1f)
    val progress by animateFloatAsState(
        targetValue = progressTarget,
        animationSpec = tween(durationMillis = 700),
        label = "fuel-ring-progress",
    )
    val ringColor = ringColorFor(progress)

    Box(
        modifier = Modifier
            .size(200.dp)
            .aspectRatio(1f),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 16.dp.toPx()
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val arcOffset = Offset(stroke / 2, stroke / 2)
            drawArc(
                color = Color(0xFF1ED4B6).copy(alpha = 0.12f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = arcOffset,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = arcOffset,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "FUEL LEFT",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
            Spacer(Modifier.height(2.dp))
            Text("${summary.remainingKcal}", fontSize = 44.sp, fontWeight = FontWeight.Bold)
            Text(
                "${summary.consumedKcal} / ${summary.dailyKcalTarget}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
        }
    }
}

private fun ringColorFor(progress: Float): Color = when {
    progress >= 1f -> Color(0xFFEF4444)
    progress >= 0.85f -> Color(0xFFFBBF24)
    else -> Color(0xFF34D399)
}

@Composable
private fun MacroStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun PlateActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean,
) {
    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier.padding(vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(30.dp))
            Spacer(Modifier.height(8.dp))
            Text(label, fontWeight = FontWeight.SemiBold)
        }
    }
    if (primary) {
        Button(
            onClick = onClick,
            modifier = modifier,
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(),
        ) { content() }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier,
            shape = RoundedCornerShape(18.dp),
        ) { content() }
    }
}

@Composable
private fun SignInGate(onSignInRequested: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.PhotoCamera,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(48.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "Plate coach is personal",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Sign in so we can compare each meal against your daily goal — " +
                "and tell you if it fits, in one tap.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onSignInRequested,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
        ) {
            Text("Sign in", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// --- Camera + image helpers ------------------------------------------------

private fun launchCamera(
    context: Context,
    launcher: androidx.activity.result.ActivityResultLauncher<Uri>,
    onUriReady: (Uri) -> Unit,
) {
    val capturesDir = File(context.cacheDir, "captures").apply { mkdirs() }
    val file = File(capturesDir, "${UUID.randomUUID()}.jpg")
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )
    onUriReady(uri)
    launcher.launch(uri)
}

internal suspend fun readUriAsJpeg(
    context: Context,
    uri: Uri,
    maxDim: Int = 1024,
    quality: Int = 85,
): ByteArray = withContext(Dispatchers.IO) {
    val bitmap = context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it)
    } ?: error("Couldn't read image at $uri")
    bitmapToJpeg(bitmap, maxDim, quality)
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
