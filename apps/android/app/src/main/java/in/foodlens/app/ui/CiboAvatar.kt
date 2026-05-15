package `in`.foodlens.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import `in`.foodlens.app.BuildConfig
import `in`.foodlens.app.auth.UserProfile
import `in`.foodlens.app.network.AnalyzeClient

/** Avatar with three modes:
 *  - user has an uploaded photo → AsyncImage from server
 *  - user has no photo          → initials in a teal-tinted circle
 *  - user is null (signed out)  → "+" placeholder
 */
@Composable
fun CiboAvatar(
    user: UserProfile?,
    modifier: Modifier = Modifier,
    size: Dp = 38.dp,
    stroked: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val resolved = remember(user?.picture) {
        AnalyzeClient(BuildConfig.BACKEND_BASE_URL).resolvePictureUrl(user?.picture)
    }
    val tap = if (onClick != null) Modifier.clickable { onClick() } else Modifier
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(CiboColors.SurfaceContainerHigh)
            .then(if (stroked) Modifier.border(2.dp, CiboColors.Primary, CircleShape) else Modifier)
            .then(tap),
        contentAlignment = Alignment.Center,
    ) {
        if (resolved != null) {
            AsyncImage(
                model = resolved,
                contentDescription = "Profile photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
            )
        } else {
            Text(
                text = initials(user),
                color = if (user == null) CiboColors.OnSurfaceVariant else CiboColors.Primary,
                style = CiboType.BodyMd.copy(
                    fontSize = (size.value * 0.36f).sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
    }
}

private fun initials(user: UserProfile?): String {
    if (user == null) return "+"
    val name = user.name ?: user.email
    val parts = name.split(" ", limit = 2)
    return if (parts.size >= 2 && parts[0].isNotEmpty() && parts[1].isNotEmpty()) {
        "${parts[0].first()}${parts[1].first()}".uppercase()
    } else {
        name.take(2).uppercase()
    }
}
