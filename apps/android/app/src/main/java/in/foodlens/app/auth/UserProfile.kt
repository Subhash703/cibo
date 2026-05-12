package `in`.foodlens.app.auth

import java.time.Year
import kotlinx.serialization.Serializable

/**
 * Local snapshot of a signed-in user. Mirrors the backend's `UserPublic`
 * shape, but defined in-Android-app to avoid a hard schema dependency.
 *
 * The body-stats fields are all optional. When all five are filled in, the
 * UI shows a Mifflin-St Jeor TDEE suggestion. The user can apply it to the
 * kcal goal slider, but never has it forced on them.
 */
@Serializable
data class UserProfile(
    val email: String,
    val name: String? = null,
    val picture: String? = null,
    val dailyKcalTarget: Int = 2000,
    val birthYear: Int? = null,
    val sex: String? = null,            // "male" / "female" / "other"
    val weightKg: Float? = null,
    val heightCm: Float? = null,
    val activityLevel: String? = null,  // see ACTIVITY_LEVELS
    val suggestedKcalTarget: Int? = null,
)

/** Display-friendly options for the activity selector. */
val ACTIVITY_LEVELS: List<Pair<String, String>> = listOf(
    "sedentary" to "Sedentary",
    "light" to "Light (1–3 days/wk)",
    "moderate" to "Moderate (3–5 days/wk)",
    "active" to "Active (6–7 days/wk)",
    "very_active" to "Athlete-level",
)

private val ACTIVITY_FACTORS: Map<String, Double> = mapOf(
    "sedentary" to 1.2,
    "light" to 1.375,
    "moderate" to 1.55,
    "active" to 1.725,
    "very_active" to 1.9,
)

/** Mifflin-St Jeor TDEE in kcal/day. Returns null if any input is missing
 *  or out of plausible range. Mirrors the backend's `tdee.compute_tdee`
 *  exactly so the UI can show a live hint as the user types. */
fun computeSuggestedKcal(
    sex: String?,
    weightKg: Float?,
    heightCm: Float?,
    birthYear: Int?,
    activityLevel: String?,
): Int? {
    if (weightKg == null || heightCm == null || birthYear == null || activityLevel == null) {
        return null
    }
    val age = Year.now().value - birthYear
    if (age < 10 || age > 120) return null
    if (weightKg < 20 || weightKg > 300) return null
    if (heightCm < 80 || heightCm > 250) return null
    val factor = ACTIVITY_FACTORS[activityLevel] ?: return null
    val sexOffset = when (sex) {
        "male" -> 5.0
        "female" -> -161.0
        else -> -78.0
    }
    val bmr = 10.0 * weightKg + 6.25 * heightCm - 5.0 * age + sexOffset
    val tdee = (bmr * factor).toInt()
    return tdee.coerceIn(1000, 5000)
}
