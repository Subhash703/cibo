package `in`.foodlens.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import `in`.foodlens.app.auth.UserProfile
import `in`.foodlens.app.network.DailySummary
import `in`.foodlens.app.ui.CiboAvatar
import `in`.foodlens.app.ui.CiboColors
import `in`.foodlens.app.ui.CiboPrimaryButton
import `in`.foodlens.app.ui.CiboSecondaryButton
import `in`.foodlens.app.ui.CiboType
import `in`.foodlens.app.ui.GlassCard
import `in`.foodlens.app.ui.InsightCard
import `in`.foodlens.app.ui.KcalRing
import `in`.foodlens.app.ui.MacroBar
import `in`.foodlens.app.ui.StatusPill
import `in`.foodlens.app.ui.StatusTone
import java.time.LocalTime

@Composable
fun HomeScreen(
    user: UserProfile?,
    isRunning: Boolean,
    summary: DailySummary?,
    onOpenProfile: () -> Unit,
    onRefreshBubble: () -> Unit,
    onStopBubble: () -> Unit,
    onAllowUnrestrictedBattery: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        // Scrollable top section. Bottom action buttons stay pinned.
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            TopBar(user = user, onOpenProfile = onOpenProfile)
            StatusPill(
                text = if (isRunning) "Cibo is running" else "Cibo is paused",
                tone = if (isRunning) StatusTone.Positive else StatusTone.Neutral,
                pulses = isRunning,
            )
            GreetingBlock(user = user, summary = summary)
            DailyRingCard(user = user, summary = summary)
            if (onAllowUnrestrictedBattery != null) {
                BatteryTipCard(onAllow = onAllowUnrestrictedBattery)
            }
            HowItWorksHint(isRunning = isRunning)
            InsightCard(text = insightText(user))
        }

        Spacer(Modifier.height(16.dp))

        // Signed-out users get one obvious CTA — Sign in. Discoverability
        // of the avatar in the corner was the #1 user complaint.
        if (user == null) {
            CiboPrimaryButton(text = "Sign in to Cibo", onClick = onOpenProfile)
            Spacer(Modifier.height(8.dp))
            CiboSecondaryButton(
                text = if (isRunning) "Refresh Cibo" else "Start Cibo",
                onClick = onRefreshBubble,
            )
        } else {
            CiboPrimaryButton(
                text = if (isRunning) "Refresh Cibo" else "Start Cibo",
                onClick = onRefreshBubble,
            )
            if (isRunning) {
                Spacer(Modifier.height(8.dp))
                CiboSecondaryButton(text = "Stop Cibo", onClick = onStopBubble)
            }
        }
    }
}

// ──────────────── pieces

@Composable
private fun TopBar(user: UserProfile?, onOpenProfile: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f),
        ) {
            Image(
                painter = painterResource(id = R.drawable.cibo_logo),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
            )
            Text(
                "Cibo",
                style = CiboType.DisplaySm,
                color = CiboColors.Primary,
            )
        }
        Box(
            Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(CiboColors.SurfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Notifications,
                contentDescription = "Notifications",
                tint = CiboColors.OnSurface,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        ProfileAvatarButton(user = user, onClick = onOpenProfile)
    }
}

@Composable
private fun ProfileAvatarButton(user: UserProfile?, onClick: () -> Unit) {
    CiboAvatar(user = user, size = 38.dp, onClick = onClick)
}

@Composable
private fun GreetingBlock(user: UserProfile?, summary: DailySummary?) {
    val hour = LocalTime.now().hour
    val timeGreeting = when {
        hour < 12 -> "morning"
        hour < 17 -> "afternoon"
        else -> "evening"
    }
    val displayName = user?.name?.takeIf { it.isNotBlank() }
        ?.split(" ")?.firstOrNull()
        ?: user?.email?.substringBefore("@")?.replaceFirstChar { it.uppercaseChar() }
        ?: "there"

    val sub = when {
        user == null -> "Sign in to track your daily calorie goal."
        summary != null ->
            "You've used ${summary.consumedKcal} of ${summary.dailyKcalTarget} kcal today."
        else -> "Goal: ${user.dailyKcalTarget} kcal today."
    }

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("Good $timeGreeting,",
            style = CiboType.DisplayMd,
            color = CiboColors.OnSurface)
        Text(displayName.replaceFirstChar { it.uppercaseChar() },
            style = CiboType.DisplayMd,
            color = CiboColors.OnSurface)
        Spacer(Modifier.height(6.dp))
        Text(sub, style = CiboType.BodyMd, color = CiboColors.OnSurfaceVariant)
    }
}

@Composable
private fun DailyRingCard(user: UserProfile?, summary: DailySummary?) {
    val consumed = summary?.consumedKcal ?: 0
    val target = summary?.dailyKcalTarget ?: user?.dailyKcalTarget ?: 0
    GlassCard {
        Column(
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            KcalRing(consumed = consumed, target = target)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                MacroBar(label = "Protein",
                    grams = summary?.consumedProteinG ?: 0,
                    modifier = Modifier.weight(1f))
                MacroBar(label = "Fats",
                    grams = summary?.consumedFatG ?: 0,
                    target = 80,
                    modifier = Modifier.weight(1f))
                MacroBar(label = "Carbs",
                    grams = summary?.consumedCarbsG ?: 0,
                    target = 250,
                    modifier = Modifier.weight(1f))
            }
            Text(
                when {
                    user == null -> "Sign in to start tracking calories."
                    summary == null -> "Open the Plate tab or scan a cart to log today's meals."
                    summary.logCount == 0 -> "Nothing logged yet today — snap a meal or scan a cart."
                    else -> "${summary.remainingKcal} kcal left for the day."
                },
                style = CiboType.BodyMd,
                color = CiboColors.OnSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BatteryTipCard(onAllow: () -> Unit) {
    GlassCard(glow = true) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusPill(text = "Tip", tone = StatusTone.Warning)
            }
            Text(
                "Cibo not appearing on Swiggy / Zomato?",
                style = CiboType.H2,
                color = CiboColors.OnSurface,
            )
            Text(
                "Battery saver may be killing Cibo in the background. Allow Cibo to run unrestricted, then reopen the food app.",
                style = CiboType.BodyMd,
                color = CiboColors.OnSurfaceVariant,
            )
            CiboSecondaryButton(text = "Allow background", onClick = onAllow)
        }
    }
}

@Composable
private fun HowItWorksHint(isRunning: Boolean) {
    GlassCard(contentPadding = PaddingValues(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (isRunning) "How Cibo helps" else "What you'll see",
                style = CiboType.H2,
                color = CiboColors.OnSurface,
            )
            Text(
                if (isRunning) {
                    "Cibo appears automatically when you open Swiggy, Zomato, Domino's or Blinkit. Tap it to score the cart you're looking at."
                } else {
                    "Once you start, a small Cibo button rides on top of food-delivery apps. Tap it to see kcal, score, and a smarter swap."
                },
                style = CiboType.BodyMd,
                color = CiboColors.OnSurfaceVariant,
            )
        }
    }
}

private fun insightText(user: UserProfile?): String = if (user == null) {
    "Sign in and Cibo will tailor every cart verdict to your daily goal — not just generic kcal numbers."
} else {
    "Tip: take the swap when offered — most people save 200–400 kcal per order without losing the meal they wanted."
}
