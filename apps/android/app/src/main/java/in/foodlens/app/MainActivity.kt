package `in`.foodlens.app

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.foodlens.app.auth.UserProfile
import `in`.foodlens.app.foreground.ForegroundAppPoller
import `in`.foodlens.app.network.AnalyzeClient
import `in`.foodlens.app.network.ProfileUpdate
import `in`.foodlens.app.network.toProfile
import `in`.foodlens.app.overlay.FloatingButtonService
import `in`.foodlens.app.ui.CiboTheme
import kotlinx.coroutines.launch

private enum class OnboardingStep {
    Welcome,
    HowItWorks,
    PermissionOverlay,
    PermissionUsage,
    Ready,
}

private enum class AppScreen { ONBOARDING, MAIN, HISTORY }

private enum class MainTab { HOME, PLATE, PROFILE }

class MainActivity : ComponentActivity() {

    private var overlayGranted by mutableStateOf(false)
    private var usageGranted by mutableStateOf(false)
    private var batteryUnrestricted by mutableStateOf(true)
    private var step by mutableStateOf(OnboardingStep.Welcome)
    private var screen by mutableStateOf(AppScreen.ONBOARDING)
    private var tab by mutableStateOf(MainTab.HOME)

    // Profile screen UI state
    private var profileBusy by mutableStateOf(false)
    private var profileError by mutableStateOf<String?>(null)

    private val analyzeClient by lazy { AnalyzeClient(BuildConfig.BACKEND_BASE_URL) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refreshPermissions()
        screen = if (overlayGranted && usageGranted) AppScreen.MAIN else AppScreen.ONBOARDING
        if (overlayGranted && usageGranted) step = OnboardingStep.Ready

        setContent {
            CiboTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val user by foodLensApp.authState.user.collectAsState()
                    val dailySummary by foodLensApp.dailySummary.collectAsState()
                    val coroutineScope = rememberCoroutineScope()

                    // Whenever the user signs in (or relaunch with a saved
                    // token), pull today's numbers so HomeScreen has data
                    // before the user ever opens the Plate tab.
                    androidx.compose.runtime.LaunchedEffect(user?.email) {
                        if (user != null) foodLensApp.refreshToday()
                    }

                    when (screen) {
                        AppScreen.ONBOARDING -> OnboardingScreen(
                            step = step,
                            overlayGranted = overlayGranted,
                            usageGranted = usageGranted,
                            onAdvance = { step = nextStep(step) },
                            onBack = { step = previousStep(step) },
                            onRequestOverlay = ::requestOverlayPermission,
                            onRequestUsage = ::requestUsageAccess,
                            onRecheck = ::refreshPermissions,
                            onStart = {
                                if (overlayGranted && usageGranted) {
                                    if (foodLensApp.authState.isSignedIn) {
                                        FloatingButtonService.start(this@MainActivity)
                                        screen = AppScreen.MAIN
                                    } else {
                                        // Sign in first — Cibo's verdict is personalised
                                        // and the floating bubble counts toward the trial.
                                        screen = AppScreen.MAIN
                                        tab = MainTab.PROFILE
                                    }
                                } else {
                                    if (!overlayGranted) requestOverlayPermission()
                                    else if (!usageGranted) requestUsageAccess()
                                }
                            },
                        )

                        AppScreen.MAIN -> {
                            val running by foodLensApp.floatingButtonRunning.collectAsState()
                            Scaffold(
                                bottomBar = {
                                    NavigationBar(
                                        containerColor = MaterialTheme.colorScheme.surface,
                                    ) {
                                        NavigationBarItem(
                                            selected = tab == MainTab.HOME,
                                            onClick = { tab = MainTab.HOME },
                                            icon = {
                                                Icon(
                                                    if (tab == MainTab.HOME) {
                                                        Icons.Filled.Home
                                                    } else {
                                                        Icons.Outlined.Home
                                                    },
                                                    contentDescription = "Home",
                                                )
                                            },
                                            colors = NavigationBarItemDefaults.colors(
                                                indicatorColor = MaterialTheme.colorScheme.primary.copy(
                                                    alpha = 0.15f,
                                                ),
                                            ),
                                        )
                                        NavigationBarItem(
                                            selected = tab == MainTab.PLATE,
                                            onClick = { tab = MainTab.PLATE },
                                            icon = {
                                                Icon(
                                                    if (tab == MainTab.PLATE) {
                                                        Icons.Filled.Restaurant
                                                    } else {
                                                        Icons.Outlined.Restaurant
                                                    },
                                                    contentDescription = "Plate",
                                                )
                                            },
                                            colors = NavigationBarItemDefaults.colors(
                                                indicatorColor = MaterialTheme.colorScheme.primary.copy(
                                                    alpha = 0.15f,
                                                ),
                                            ),
                                        )
                                        NavigationBarItem(
                                            selected = tab == MainTab.PROFILE,
                                            onClick = { tab = MainTab.PROFILE },
                                            icon = {
                                                Icon(
                                                    if (tab == MainTab.PROFILE) {
                                                        Icons.Filled.Person
                                                    } else {
                                                        Icons.Outlined.Person
                                                    },
                                                    contentDescription = "Profile",
                                                )
                                            },
                                            colors = NavigationBarItemDefaults.colors(
                                                indicatorColor = MaterialTheme.colorScheme.primary.copy(
                                                    alpha = 0.15f,
                                                ),
                                            ),
                                        )
                                    }
                                },
                            ) { padding ->
                                // Back from Plate or Profile takes the user to Home
                                // first, instead of minimising the app.
                                androidx.activity.compose.BackHandler(enabled = tab != MainTab.HOME) {
                                    tab = MainTab.HOME
                                }
                                AnimatedContent(
                                    targetState = tab,
                                    transitionSpec = {
                                        (fadeIn(animationSpec = androidx.compose.animation.core.tween(220))
                                            togetherWith
                                            fadeOut(animationSpec = androidx.compose.animation.core.tween(180)))
                                    },
                                    label = "tab-switch",
                                    modifier = Modifier.padding(padding),
                                ) { selected ->
                                    when (selected) {
                                        MainTab.HOME -> HomeScreen(
                                            user = user,
                                            isRunning = running,
                                            summary = dailySummary,
                                            onOpenProfile = { tab = MainTab.PROFILE },
                                            onRefreshBubble = {
                                                if (running) {
                                                    FloatingButtonService.refresh(this@MainActivity)
                                                } else {
                                                    FloatingButtonService.start(this@MainActivity)
                                                }
                                            },
                                            onStopBubble = {
                                                FloatingButtonService.stop(this@MainActivity)
                                            },
                                            onAllowUnrestrictedBattery = if (batteryUnrestricted) {
                                                null
                                            } else {
                                                ::requestBatteryUnrestricted
                                            },
                                        )
                                        MainTab.PLATE -> PlateScreen(
                                            user = user,
                                            analyzer = foodLensApp.analyzer,
                                            idToken = foodLensApp.authState.idToken,
                                            onSignInRequested = { tab = MainTab.PROFILE },
                                        )
                                        MainTab.PROFILE -> ProfileScreen(
                                            state = ProfileUiState(
                                                user = user,
                                                busy = profileBusy,
                                                error = profileError,
                                            ),
                                            onAuthSubmit = { email, password, isRegister, name ->
                                                profileError = null
                                                profileBusy = true
                                                coroutineScope.launch {
                                                    runCatching { handleAuth(email, password, isRegister, name) }
                                                        .onFailure {
                                                            profileError = it.message ?: "Couldn't sign in"
                                                        }
                                                    profileBusy = false
                                                }
                                            },
                                            onSignOut = {
                                                foodLensApp.authState.signOut()
                                            },
                                            onSaveProfile = { updated ->
                                                profileError = null
                                                profileBusy = true
                                                coroutineScope.launch {
                                                    runCatching { saveProfile(updated) }
                                                        .onFailure {
                                                            profileError = it.message ?: "Couldn't save profile"
                                                        }
                                                    profileBusy = false
                                                }
                                            },
                                            onUploadAvatar = { uri ->
                                                profileBusy = true
                                                coroutineScope.launch {
                                                    runCatching { uploadAvatar(uri) }
                                                        .onFailure { showAvatarError(it) }
                                                    profileBusy = false
                                                }
                                            },
                                            onOpenHistory = { screen = AppScreen.HISTORY },
                                            onBack = { tab = MainTab.HOME },
                                        )
                                    }
                                }
                            }
                        }

                        AppScreen.HISTORY -> HistoryScreen(
                            analyzer = foodLensApp.analyzer,
                            idToken = foodLensApp.authState.idToken,
                            user = user,
                            onBack = { screen = AppScreen.MAIN },
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissions()
        if (screen == AppScreen.ONBOARDING) {
            if (step == OnboardingStep.PermissionOverlay && overlayGranted) {
                step = if (usageGranted) OnboardingStep.Ready else OnboardingStep.PermissionUsage
            }
            if (step == OnboardingStep.PermissionUsage && usageGranted) {
                step = OnboardingStep.Ready
            }
        }
    }

    private suspend fun handleAuth(
        email: String,
        password: String,
        isRegister: Boolean,
        name: String?,
    ) {
        val response = if (isRegister) {
            analyzeClient.register(email, password, name)
        } else {
            analyzeClient.login(email, password)
        }
        foodLensApp.authState.setSignedIn(response.user.toProfile(), response.token)
    }

    private suspend fun saveProfile(updated: UserProfile) {
        val token = foodLensApp.authState.idToken ?: error("Not signed in")
        val server = analyzeClient.updateProfile(
            idToken = token,
            update = ProfileUpdate(
                dailyKcalTarget = updated.dailyKcalTarget,
                birthYear = updated.birthYear,
                sex = updated.sex,
                weightKg = updated.weightKg,
                heightCm = updated.heightCm,
                activityLevel = updated.activityLevel,
                goal = updated.goal,
            ),
        )
        foodLensApp.authState.setProfile(server.toProfile())
    }

    private suspend fun uploadAvatar(uri: android.net.Uri) {
        val token = foodLensApp.authState.idToken ?: error("Not signed in")
        val jpeg = readUriAsJpeg(this, uri)
        val server = analyzeClient.uploadAvatar(idToken = token, jpegBytes = jpeg)
        foodLensApp.authState.setProfile(server.toProfile())
    }

    private fun showAvatarError(t: Throwable) {
        val raw = t.message.orEmpty()
        val friendly = when {
            raw.contains("Not Found", ignoreCase = true) ->
                "Photo upload isn't available yet — the server hasn't been updated."
            raw.contains("too large", ignoreCase = true) ->
                "Photo is too large. Pick one under 2 MB."
            raw.contains("Empty image", ignoreCase = true) ->
                "Couldn't read that photo. Pick a different one."
            raw.contains("Unauthorized", ignoreCase = true) || raw.contains("401") ->
                "Please sign in again."
            raw.contains("timed out", ignoreCase = true) ||
                raw.contains("connection", ignoreCase = true) ||
                raw.contains("Unable to resolve", ignoreCase = true) ->
                "Couldn't reach Cibo. Check your connection and try again."
            raw.isBlank() -> "Couldn't upload photo. Please try again."
            else -> raw
        }
        Toast.makeText(this, friendly, Toast.LENGTH_LONG).show()
    }

    private fun refreshPermissions() {
        overlayGranted = Settings.canDrawOverlays(this)
        usageGranted = ForegroundAppPoller.hasPermission(this)
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        batteryUnrestricted = pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun requestOverlayPermission() {
        if (!overlayGranted) {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName"),
                ),
            )
        }
    }

    private fun requestUsageAccess() {
        if (!usageGranted) startActivity(ForegroundAppPoller.settingsIntent())
    }

    /**
     * Fires Android's "Allow app to run in background?" system dialog. Tapping
     * Allow flips the app to battery-unrestricted, which is what most OEM
     * skins (MIUI, ColorOS, OxygenOS, …) actually check before they let our
     * foreground service survive a switch to Swiggy. Falls back to the App
     * info screen on the rare ROM that doesn't expose the action.
     */
    @SuppressLint("BatteryLife")
    private fun requestBatteryUnrestricted() {
        val prompt = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:$packageName"),
        )
        try {
            startActivity(prompt)
        } catch (_: ActivityNotFoundException) {
            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName"),
                ),
            )
        }
    }

    private fun nextStep(current: OnboardingStep): OnboardingStep = when (current) {
        OnboardingStep.Welcome -> OnboardingStep.HowItWorks
        OnboardingStep.HowItWorks -> when {
            overlayGranted && usageGranted -> OnboardingStep.Ready
            overlayGranted -> OnboardingStep.PermissionUsage
            else -> OnboardingStep.PermissionOverlay
        }
        OnboardingStep.PermissionOverlay -> OnboardingStep.PermissionUsage
        OnboardingStep.PermissionUsage -> OnboardingStep.Ready
        OnboardingStep.Ready -> OnboardingStep.Ready
    }

    private fun previousStep(current: OnboardingStep): OnboardingStep = when (current) {
        OnboardingStep.Welcome -> OnboardingStep.Welcome
        OnboardingStep.HowItWorks -> OnboardingStep.Welcome
        OnboardingStep.PermissionOverlay -> OnboardingStep.HowItWorks
        OnboardingStep.PermissionUsage ->
            if (overlayGranted) OnboardingStep.HowItWorks else OnboardingStep.PermissionOverlay
        OnboardingStep.Ready -> OnboardingStep.HowItWorks
    }
}

@Composable
private fun OnboardingScreen(
    step: OnboardingStep,
    overlayGranted: Boolean,
    usageGranted: Boolean,
    onAdvance: () -> Unit,
    onBack: () -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestUsage: () -> Unit,
    onRecheck: () -> Unit,
    onStart: () -> Unit,
) {
    // Hardware/system back button maps to going to previous step.
    androidx.activity.compose.BackHandler(enabled = step != OnboardingStep.Welcome) { onBack() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        // Compact back chevron sits inline with the page indicator —
        // mirrors the iOS toolbar feel without taking a full row.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (step != OnboardingStep.Welcome) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp),
                    )
                }
            } else {
                Spacer(Modifier.size(36.dp))
            }
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                StepIndicator(step)
            }
            Spacer(Modifier.size(36.dp))
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp, bottom = 24.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            when (step) {
                OnboardingStep.Welcome -> WelcomeBody()
                OnboardingStep.HowItWorks -> HowItWorksBody()
                OnboardingStep.PermissionOverlay -> PermissionBody(
                    title = "Display over other apps",
                    purpose = "We need this so the floating Cibo button can appear on top of your food delivery apps.",
                    safety = "Even with this granted, our app only shows the floating button inside food/grocery apps — never on Instagram, banking, your home screen, or anywhere else.",
                    granted = overlayGranted,
                    onRecheck = onRecheck,
                )
                OnboardingStep.PermissionUsage -> PermissionBody(
                    title = "Usage access",
                    purpose = "We use this to detect when you open a food app, so Cibo appears automatically — and stays hidden everywhere else.",
                    safety = "We never read what you do inside other apps. We only check whether the foreground app is in our food-app allowlist (Swiggy, Zomato, Domino's, …).",
                    granted = usageGranted,
                    onRecheck = onRecheck,
                )
                OnboardingStep.Ready -> ReadyBody()
            }
        }
        StepActions(
            step = step,
            overlayGranted = overlayGranted,
            usageGranted = usageGranted,
            onAdvance = onAdvance,
            onRequestOverlay = onRequestOverlay,
            onRequestUsage = onRequestUsage,
            onStart = onStart,
        )
    }
}

@Composable
private fun StepIndicator(step: OnboardingStep) {
    val total = 4
    val current = when (step) {
        OnboardingStep.Welcome -> 1
        OnboardingStep.HowItWorks -> 2
        OnboardingStep.PermissionOverlay,
        OnboardingStep.PermissionUsage -> 3
        OnboardingStep.Ready -> 4
    }
    Text(
        "Step $current of $total",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
    )
}

@Composable
private fun WelcomeBody() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "C",
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Cibo",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "See what your meal will do to your body — before you order.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
        )
        Spacer(Modifier.height(8.dp))
        FeatureBullet("🎯", "Tap one button on Swiggy, Zomato, Domino's…")
        FeatureBullet("🤖", "AI reads your cart and tells you the impact")
        FeatureBullet("🥗", "Smart swaps to cut calories without skipping the meal")
        FeatureBullet("🔒", "Only inside food apps. No silent scanning. Nothing stored.")
    }
}

@Composable
private fun HowItWorksBody() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            "How it works",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "Three steps, every time:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
        )
        Spacer(Modifier.height(4.dp))
        StepRow(
            1,
            "Open any food delivery app",
            "Swiggy, Zomato, Domino's, BigBasket, Blinkit, Dunzo, McDonald's, KFC…",
        )
        StepRow(
            2,
            "Tap the floating Cibo button",
            "It appears automatically on your cart screen — and only there.",
        )
        StepRow(
            3,
            "See instant insight",
            "Calories, macros, health score, and one healthier swap — under 3 seconds.",
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Setup takes ~30 seconds. Two quick permissions, both go to your phone's Settings.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun PermissionBody(
    title: String,
    purpose: String,
    safety: String,
    granted: Boolean,
    onRecheck: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        InfoCard("Why we need it", purpose, accent = false)
        InfoCard("Privacy promise", safety, accent = true)
        Spacer(Modifier.height(8.dp))
        StatusChip(granted)
        TextButton(onClick = onRecheck) {
            Text(
                "Already allowed it? Re-check",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun ReadyBody() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(44.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "All set 🎉",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "Open Swiggy, Zomato, or any food app. The Cibo button will appear on your cart — tap it for instant nutrition.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Want to stop it? Dismiss the persistent notification any time.",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
        )
    }
}

@Composable
private fun StepActions(
    step: OnboardingStep,
    overlayGranted: Boolean,
    usageGranted: Boolean,
    onAdvance: () -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestUsage: () -> Unit,
    onStart: () -> Unit,
) {
    when (step) {
        OnboardingStep.Welcome -> PrimaryButton("Let's go", onAdvance)
        OnboardingStep.HowItWorks -> PrimaryButton("Continue", onAdvance)
        OnboardingStep.PermissionOverlay -> {
            if (overlayGranted) PrimaryButton("Continue", onAdvance)
            else PrimaryButton("Open settings to allow", onRequestOverlay)
        }
        OnboardingStep.PermissionUsage -> {
            if (usageGranted) PrimaryButton("Continue", onAdvance)
            else PrimaryButton("Open settings to allow", onRequestUsage)
        }
        OnboardingStep.Ready -> PrimaryButton("Start floating button", onStart)
    }
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
        shape = RoundedCornerShape(14.dp),
    ) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun FeatureBullet(emoji: String, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(emoji, fontSize = 22.sp, modifier = Modifier.padding(end = 12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun StepRow(num: Int, title: String, body: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "$num",
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.padding(top = 2.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun InfoCard(label: String, body: String, accent: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = if (accent) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun StatusChip(granted: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (granted) Color(0xFF34D399) else Color(0xFF8B95A2)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (granted) Icons.Default.Check else Icons.Default.Close,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            if (granted) "Granted ✓" else "Not granted yet",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
