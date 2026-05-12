package `in`.foodlens.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.foodlens.app.auth.UserProfile
import java.time.LocalTime

@Composable
fun HomeScreen(
    user: UserProfile?,
    isRunning: Boolean,
    onOpenProfile: () -> Unit,
    onRefreshBubble: () -> Unit,
    onStopBubble: () -> Unit,
    onAllowUnrestrictedBattery: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 24.dp),
    ) {
        // Scrollable top section. The bottom action buttons stay pinned —
        // weight(1f) gives the scroll viewport whatever space the buttons
        // don't claim, so on short screens the content scrolls instead of
        // clipping the Refresh / Stop buttons.
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            HomeTopBar(user = user, onOpenProfile = onOpenProfile)
            GreetingBlock(user = user)
            StatusCard(isRunning = isRunning)
            if (onAllowUnrestrictedBattery != null) {
                BatteryTipCard(onAllow = onAllowUnrestrictedBattery)
            }
        }

        Spacer(Modifier.height(20.dp))

        // Primary: Refresh / Start. Always available.
        Button(
            onClick = onRefreshBubble,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text(
                if (isRunning) "Refresh Cibo" else "Start Cibo",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        // Secondary: Stop. Only shown when actually running.
        if (isRunning) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onStopBubble,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text("Stop Cibo")
            }
        }
    }
}

@Composable
private fun HomeTopBar(user: UserProfile?, onOpenProfile: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Cibo",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        ProfileAvatarButton(user = user, onClick = onOpenProfile)
    }
}

@Composable
private fun ProfileAvatarButton(user: UserProfile?, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        val ch = user?.name?.firstOrNull() ?: user?.email?.firstOrNull() ?: '+'
        Text(
            ch.uppercaseChar().toString(),
            color = MaterialTheme.colorScheme.onPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
        )
    }
}

@Composable
private fun GreetingBlock(user: UserProfile?) {
    val hour = LocalTime.now().hour
    val timeGreeting = when {
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        else -> "Good evening"
    }
    val displayName = user?.name?.takeIf { it.isNotBlank() }?.trim()?.split(" ")?.firstOrNull()
        ?: user?.email?.substringBefore("@")?.replaceFirstChar { it.uppercaseChar() }

    val headline = if (displayName != null) "$timeGreeting, $displayName" else timeGreeting
    val sub = if (user != null) {
        "Goal: ${user.dailyKcalTarget} kcal today · tap your initial to edit"
    } else {
        "Sign in to track your daily calorie goal — tap the avatar above."
    }
    Column {
        Text(
            headline,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            sub,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun StatusCard(isRunning: Boolean) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (isRunning) Color(0xFF34D399) else Color(0xFF8B95A2)),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (isRunning) "Cibo is running" else "Cibo is paused",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                if (isRunning) {
                    "Cibo appears automatically when you open Swiggy / Zomato / " +
                        "Domino's etc., and stays hidden everywhere else. If it " +
                        "doesn't appear after the phone has been idle, tap Refresh below."
                } else {
                    "Cibo isn't running. Tap Start to bring it back."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun BatteryTipCard(onAllow: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Cibo not showing on Swiggy / Zomato?",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Your phone's battery saver may be stopping Cibo in the background. Allow Cibo to run unrestricted, then reopen Swiggy.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            )
            TextButton(onClick = onAllow) {
                Text("Allow Cibo to run in background")
            }
        }
    }
}
