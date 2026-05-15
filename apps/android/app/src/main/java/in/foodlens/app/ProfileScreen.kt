package `in`.foodlens.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.foodlens.app.auth.ACTIVITY_LEVELS
import `in`.foodlens.app.auth.GOALS
import `in`.foodlens.app.auth.UserProfile
import `in`.foodlens.app.auth.computeSuggestedKcal
import `in`.foodlens.app.ui.CiboAvatar
import `in`.foodlens.app.ui.CiboColors
import `in`.foodlens.app.ui.CiboPrimaryButton
import `in`.foodlens.app.ui.CiboSecondaryButton
import `in`.foodlens.app.ui.CiboType
import `in`.foodlens.app.ui.GlassCard
import `in`.foodlens.app.ui.InsightCard
import `in`.foodlens.app.ui.StatusPill
import `in`.foodlens.app.ui.StatusTone
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.filled.Edit

data class ProfileUiState(
    val user: UserProfile?,
    val busy: Boolean = false,
    val error: String? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    state: ProfileUiState,
    onAuthSubmit: (email: String, password: String, isRegister: Boolean, name: String?) -> Unit,
    onSignOut: () -> Unit,
    onSaveProfile: (UserProfile) -> Unit,
    onUploadAvatar: (Uri) -> Unit,
    onOpenHistory: () -> Unit,
    onBack: () -> Unit,
) {
    val scrollState = rememberScrollState()
    val wasBusy = remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Scroll back to the top after a successful save so the user sees
    // their updated header instead of the (now-irrelevant) save button.
    LaunchedEffect(state.busy, state.error) {
        if (wasBusy.value && !state.busy && state.error == null) {
            coroutineScope.launch { scrollState.animateScrollTo(0) }
        }
        wasBusy.value = state.busy
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Profile",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            if (state.user == null) {
                SignedOutSection(
                    busy = state.busy,
                    error = state.error,
                    onSubmit = onAuthSubmit,
                )
            } else {
                SignedInBody(
                    user = state.user,
                    busy = state.busy,
                    error = state.error,
                    onSaveProfile = onSaveProfile,
                    onUploadAvatar = onUploadAvatar,
                    onOpenHistory = onOpenHistory,
                    onSignOut = onSignOut,
                )
            }
        }
    }
}

@Composable
private fun SignedOutSection(
    busy: Boolean,
    error: String?,
    onSubmit: (email: String, password: String, isRegister: Boolean, name: String?) -> Unit,
) {
    var isRegister by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    val emailValid = email.contains("@") && email.contains(".") && email.length >= 4
    val passwordValid = password.length >= 8
    val canSubmit = emailValid && passwordValid && !busy

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        GlassCard {
            Column {
                Text(
                    if (isRegister) "Create your Cibo" else "Welcome back",
                    style = CiboType.DisplaySm,
                    color = CiboColors.OnSurface,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Save a daily calorie goal. When you confirm an order, " +
                        "Cibo subtracts those calories from your goal — every " +
                        "future cart shows how much room you have left.",
                    style = CiboType.BodyMd,
                    color = CiboColors.OnSurfaceVariant,
                )
            }
        }

        if (isRegister) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name (optional)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        OutlinedTextField(
            value = email,
            onValueChange = { email = it.trim() },
            label = { Text("Email") },
            singleLine = true,
            isError = email.isNotBlank() && !emailValid,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            isError = password.isNotBlank() && !passwordValid,
            visualTransformation = if (passwordVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailingIcon = {
                TextButton(onClick = { passwordVisible = !passwordVisible }) {
                    Text(if (passwordVisible) "Hide" else "Show")
                }
            },
            supportingText = {
                Text(
                    if (isRegister) "At least 8 characters" else " ",
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        CiboPrimaryButton(
            text = if (isRegister) "Create account" else "Sign in",
            onClick = {
                onSubmit(email, password, isRegister, name.takeIf { it.isNotBlank() })
            },
            enabled = canSubmit,
            loading = busy,
        )

        TextButton(
            onClick = { isRegister = !isRegister },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (isRegister) "Already have an account? Sign in"
                else "New to Cibo? Create an account",
            )
        }

        error?.let {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0x33EF4444)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    it,
                    modifier = Modifier.padding(14.dp),
                    color = Color(0xFFFCA5A5),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun SignedInBody(
    user: UserProfile,
    busy: Boolean,
    error: String?,
    onSaveProfile: (UserProfile) -> Unit,
    onUploadAvatar: (Uri) -> Unit,
    onOpenHistory: () -> Unit,
    onSignOut: () -> Unit,
) {
    var goal by remember(user.dailyKcalTarget) { mutableIntStateOf(user.dailyKcalTarget) }
    var sex by remember(user.sex) { mutableStateOf(user.sex) }
    var birthYear by remember(user.birthYear) { mutableStateOf(user.birthYear) }
    var weightKg by remember(user.weightKg) { mutableStateOf(user.weightKg) }
    var heightCm by remember(user.heightCm) { mutableStateOf(user.heightCm) }
    var activityLevel by remember(user.activityLevel) { mutableStateOf(user.activityLevel) }
    var userGoal by remember(user.goal) { mutableStateOf(user.goal) }

    val suggestion = computeSuggestedKcal(sex, weightKg, heightCm, birthYear, activityLevel)
    val updated = user.copy(
        dailyKcalTarget = goal,
        sex = sex,
        birthYear = birthYear,
        weightKg = weightKg,
        heightCm = heightCm,
        activityLevel = activityLevel,
        goal = userGoal,
    )
    val dirty = updated != user

    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) onUploadAvatar(uri) }

    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                CiboAvatar(user = user, size = 64.dp, stroked = true)
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .offset(x = 44.dp, y = 44.dp)
                        .clip(CircleShape)
                        .background(CiboColors.Primary)
                        .clickable {
                            avatarPicker.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly,
                                ),
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Change photo",
                        tint = CiboColors.OnPrimary,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    user.name ?: user.email,
                    style = CiboType.H1,
                    color = CiboColors.OnSurface,
                )
                Text(
                    user.email,
                    style = CiboType.BodyMd,
                    color = CiboColors.OnSurfaceVariant,
                )
            }
        }

        GoalSliderCard(
            goal = goal,
            onGoalChange = { goal = it },
            suggestion = suggestion,
            insight = user.goalInsight,
        )

        ScanBudgetCard(used = user.scansUsed, limit = user.scansLimit)

        PersonaliseGoalCard(
            sex = sex,
            onSexChange = { sex = it },
            birthYear = birthYear,
            onBirthYearChange = { birthYear = it },
            weightKg = weightKg,
            onWeightChange = { weightKg = it },
            heightCm = heightCm,
            onHeightChange = { heightCm = it },
            activityLevel = activityLevel,
            onActivityChange = { activityLevel = it },
            userGoal = userGoal,
            onGoalChange = { userGoal = it },
            suggestion = suggestion,
        )

        CiboPrimaryButton(
            text = if (dirty) "Save changes" else "Saved",
            onClick = { onSaveProfile(updated) },
            enabled = dirty && !busy,
            loading = busy,
        )

        CiboSecondaryButton(
            text = "Your history",
            onClick = onOpenHistory,
            icon = Icons.AutoMirrored.Filled.TrendingUp,
        )

        CiboSecondaryButton(text = "Sign out", onClick = onSignOut)

        error?.let {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0x33EF4444)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    it,
                    modifier = Modifier.padding(14.dp),
                    color = Color(0xFFFCA5A5),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun ScanBudgetCard(used: Int, limit: Int) {
    if (limit <= 0) return
    val remaining = (limit - used).coerceAtLeast(0)
    val fraction = (used.toFloat() / limit).coerceIn(0f, 1f)
    val accent = if (remaining == 0) CiboColors.Warning else CiboColors.Primary
    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Free trial", style = CiboType.H2, color = CiboColors.OnSurface)
                Text(
                    "$used / $limit",
                    style = CiboType.BodyMd.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                    ),
                    color = accent,
                )
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(CiboColors.SurfaceContainerHigh),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(accent),
                )
            }
            Text(
                if (remaining == 0) {
                    "You've used all your free scans. Premium is coming soon."
                } else {
                    "$remaining free scan${if (remaining == 1) "" else "s"} left. Premium is coming soon — unlimited scans included."
                },
                style = CiboType.BodyMd,
                color = CiboColors.OnSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GoalSliderCard(
    goal: Int,
    onGoalChange: (Int) -> Unit,
    suggestion: Int?,
    insight: String?,
) {
    GlassCard {
        Column {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Daily Goal",
                    style = CiboType.H1,
                    color = CiboColors.OnSurface,
                )
                StatusPill(text = "Active", tone = StatusTone.Positive)
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "$goal",
                    style = CiboType.DisplayLg.copy(fontSize = 44.sp),
                    color = CiboColors.Primary,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "kcal / day",
                    style = CiboType.BodyLg,
                    color = CiboColors.OnSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            Slider(
                value = goal.toFloat(),
                onValueChange = { onGoalChange((it / 50).toInt() * 50) },
                valueRange = 1200f..3500f,
                steps = 0,
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("1,200", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                Text("3,500", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
            suggestion?.let {
                Spacer(Modifier.height(8.dp))
                if (it == goal) {
                    Text(
                        "Matches your suggested target.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    TextButton(
                        onClick = { onGoalChange(it) },
                        modifier = Modifier.padding(0.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    ) {
                        Text(
                            "Suggested for you: $it kcal · tap to apply",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            } ?: Text(
                "We'll show this on every cart you scan.",
                style = CiboType.BodyMd,
                color = CiboColors.OnSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            InsightCard(
                label = "AI INSIGHT",
                text = insight
                    ?: "Pick a goal in Personalize — Cibo will tailor a daily insight to you. Until then, this slider is your TDEE estimate.",
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PersonaliseGoalCard(
    sex: String?,
    onSexChange: (String) -> Unit,
    birthYear: Int?,
    onBirthYearChange: (Int?) -> Unit,
    weightKg: Float?,
    onWeightChange: (Float?) -> Unit,
    heightCm: Float?,
    onHeightChange: (Float?) -> Unit,
    activityLevel: String?,
    onActivityChange: (String) -> Unit,
    userGoal: String?,
    onGoalChange: (String) -> Unit,
    suggestion: Int?,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val anyFilled = sex != null || birthYear != null || weightKg != null ||
        heightCm != null || activityLevel != null || userGoal != null

    GlassCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Personalise my goal",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        when {
                            suggestion != null -> "Suggested: $suggestion kcal/day"
                            anyFilled -> "Add the remaining stats to compute"
                            else -> "Optional · age, sex, weight, height, activity"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // Sex
                    Column {
                        Text(
                            "Sex",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                        Spacer(Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            listOf(
                                "male" to "Male",
                                "female" to "Female",
                                "other" to "Other",
                            ).forEach { (value, label) ->
                                FilterChip(
                                    selected = sex == value,
                                    onClick = { onSexChange(value) },
                                    label = { Text(label) },
                                )
                            }
                        }
                    }

                    // Numeric fields
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = birthYear?.toString() ?: "",
                            onValueChange = {
                                onBirthYearChange(it.filter(Char::isDigit).take(4).toIntOrNull())
                            },
                            label = { Text("Birth year") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Next,
                            ),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = weightKg?.let { if (it == it.toInt().toFloat()) it.toInt().toString() else it.toString() } ?: "",
                            onValueChange = { onWeightChange(it.replace(",", ".").toFloatOrNull()) },
                            label = { Text("Weight (kg)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                                imeAction = ImeAction.Next,
                            ),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = heightCm?.let { if (it == it.toInt().toFloat()) it.toInt().toString() else it.toString() } ?: "",
                            onValueChange = { onHeightChange(it.replace(",", ".").toFloatOrNull()) },
                            label = { Text("Height (cm)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                                imeAction = ImeAction.Done,
                            ),
                            modifier = Modifier.weight(1f),
                        )
                    }

                    // Activity level
                    Column {
                        Text(
                            "Activity level",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                        Spacer(Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ACTIVITY_LEVELS.forEach { (value, label) ->
                                FilterChip(
                                    selected = activityLevel == value,
                                    onClick = { onActivityChange(value) },
                                    label = { Text(label) },
                                )
                            }
                        }
                    }

                    // Goal
                    Column {
                        Text(
                            "Your goal",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                        Spacer(Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            GOALS.forEach { (value, label) ->
                                FilterChip(
                                    selected = userGoal == value,
                                    onClick = { onGoalChange(value) },
                                    label = { Text(label) },
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Used only to compute a suggested daily target. Your slider " +
                            "above always wins — we'll never override your choice.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    )
                }
            }
        }
    }
}

@Composable
private fun Avatar(name: String?, email: String, size: androidx.compose.ui.unit.Dp) {
    val initial = (name?.firstOrNull() ?: email.firstOrNull() ?: '?').uppercaseChar().toString()
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(CiboColors.SurfaceContainerHigh)
            .border(width = 2.dp, color = CiboColors.Primary, shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initial,
            color = CiboColors.Primary,
            fontSize = (size.value / 2.4).sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
