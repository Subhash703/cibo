package `in`.foodlens.app.network

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Serializable
data class AnalyzeRequest(
    val items: List<String>,
    val regionHint: String? = null,
)

@Serializable
data class Macro(val kcal: Int, val proteinG: Int, val fatG: Int, val carbsG: Int)

@Serializable
data class Suggestion(val text: String, val kcalDelta: Int)

@Serializable
data class MatchedItem(val name: String, val qty: Int, val kcal: Int)

@Serializable
data class DailySummary(
    val dailyKcalTarget: Int,
    val consumedKcal: Int,
    val remainingKcal: Int,
    val consumedProteinG: Int = 0,
    val consumedFatG: Int = 0,
    val consumedCarbsG: Int = 0,
    val logCount: Int = 0,
)

@Serializable
data class AnalyzeResponse(
    val healthScore: Int,
    val healthLabel: String,
    val macros: Macro,
    val percentDailyKcal: Int,
    val suggestions: List<Suggestion>,
    val items: List<MatchedItem> = emptyList(),
    val unmatched: List<String> = emptyList(),
    val dailySummary: DailySummary? = null,
)

@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    val name: String? = null,
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
)

@Serializable
data class UserPublic(
    val email: String,
    val name: String? = null,
    val picture: String? = null,
    val dailyKcalTarget: Int = 2000,
    val birthYear: Int? = null,
    val sex: String? = null,
    val weightKg: Float? = null,
    val heightCm: Float? = null,
    val activityLevel: String? = null,
    val suggestedKcalTarget: Int? = null,
)

fun UserPublic.toProfile(): `in`.foodlens.app.auth.UserProfile = `in`.foodlens.app.auth.UserProfile(
    email = email,
    name = name,
    picture = picture,
    dailyKcalTarget = dailyKcalTarget,
    birthYear = birthYear,
    sex = sex,
    weightKg = weightKg,
    heightCm = heightCm,
    activityLevel = activityLevel,
    suggestedKcalTarget = suggestedKcalTarget,
)

@Serializable
data class AuthResponse(
    val token: String,
    val user: UserPublic,
)

@Serializable
data class ProfileUpdate(
    val dailyKcalTarget: Int? = null,
    val birthYear: Int? = null,
    val sex: String? = null,
    val weightKg: Float? = null,
    val heightCm: Float? = null,
    val activityLevel: String? = null,
)

@Serializable
data class MealLogRequest(
    val macros: Macro,
    val items: List<MatchedItem>,
    val healthScore: Int,
)

@Serializable
data class CoachVerdict(
    val signal: String,        // "green" | "yellow" | "red"
    val oneLiner: String,
    val reason: String = "",
)

@Serializable
data class PlateAnalyzeResponse(
    val dishName: String,
    val dishDescription: String,
    val macros: Macro,
    val healthScore: Int,
    val healthLabel: String,
    val verdict: CoachVerdict,
    val dailySummary: DailySummary? = null,
)

@Serializable
data class MealLogPublic(
    val id: Int,
    val loggedAt: String,   // ISO 8601 (UTC, naive — parse as LocalDateTime, treat as UTC)
    val kcal: Int,
    val proteinG: Int,
    val fatG: Int,
    val carbsG: Int,
    val healthScore: Int,
    val items: List<MatchedItem> = emptyList(),
)

class AnalyzeClient(private val baseUrl: String) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        namingStrategy = JsonNamingStrategy.SnakeCase
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    /** Legacy OCR-text path. Kept for testing / fallback. */
    suspend fun analyze(request: AnalyzeRequest): AnalyzeResponse = withContext(Dispatchers.IO) {
        val body = json.encodeToString(request).toRequestBody(JSON_MEDIA)
        val httpRequest = Request.Builder()
            .url("$baseUrl/analyze")
            .post(body)
            .build()
        execute(httpRequest, AnalyzeResponse.serializer())
    }

    /**
     * Primary path: upload the JPEG screenshot to the vision-LLM endpoint.
     *
     * If [idToken] is non-null, the backend includes the user's daily
     * summary (consumed / remaining kcal vs. target) in the response.
     */
    suspend fun analyzeVision(
        jpegBytes: ByteArray,
        regionHint: String? = null,
        idToken: String? = null,
    ): AnalyzeResponse = withContext(Dispatchers.IO) {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("image", "screen.jpg", jpegBytes.toRequestBody(JPEG_MEDIA))
            .also { builder ->
                if (regionHint != null) builder.addFormDataPart("region_hint", regionHint)
            }
            .build()
        val builder = Request.Builder()
            .url("$baseUrl/analyze-vision")
            .post(body)
        if (idToken != null) builder.header("Authorization", "Bearer $idToken")
        execute(builder.build(), AnalyzeResponse.serializer())
    }

    suspend fun register(email: String, password: String, name: String?): AuthResponse =
        withContext(Dispatchers.IO) {
            val body = json.encodeToString(RegisterRequest(email, password, name))
                .toRequestBody(JSON_MEDIA)
            val request = Request.Builder()
                .url("$baseUrl/auth/register")
                .post(body)
                .build()
            execute(request, AuthResponse.serializer())
        }

    suspend fun login(email: String, password: String): AuthResponse = withContext(Dispatchers.IO) {
        val body = json.encodeToString(LoginRequest(email, password)).toRequestBody(JSON_MEDIA)
        val request = Request.Builder()
            .url("$baseUrl/auth/login")
            .post(body)
            .build()
        execute(request, AuthResponse.serializer())
    }

    suspend fun getMe(idToken: String): UserPublic = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/me")
            .header("Authorization", "Bearer $idToken")
            .get()
            .build()
        execute(request, UserPublic.serializer())
    }

    suspend fun updateProfile(idToken: String, update: ProfileUpdate): UserPublic =
        withContext(Dispatchers.IO) {
            val body = json.encodeToString(update).toRequestBody(JSON_MEDIA)
            val request = Request.Builder()
                .url("$baseUrl/me")
                .header("Authorization", "Bearer $idToken")
                .patch(body)
                .build()
            execute(request, UserPublic.serializer())
        }

    suspend fun getTodaySummary(idToken: String): DailySummary = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/me/today")
            .header("Authorization", "Bearer $idToken")
            .get()
            .build()
        execute(request, DailySummary.serializer())
    }

    suspend fun getTodayMealLogs(idToken: String): List<MealLogPublic> =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$baseUrl/me/meal-logs/today")
                .header("Authorization", "Bearer $idToken")
                .get()
                .build()
            execute(request, kotlinx.serialization.builtins.ListSerializer(MealLogPublic.serializer()))
        }

    suspend fun logMeal(idToken: String, request: MealLogRequest): DailySummary =
        withContext(Dispatchers.IO) {
            val body = json.encodeToString(request).toRequestBody(JSON_MEDIA)
            val httpRequest = Request.Builder()
                .url("$baseUrl/meal-logs")
                .header("Authorization", "Bearer $idToken")
                .post(body)
                .build()
            execute(httpRequest, DailySummary.serializer())
        }

    /**
     * Plate-tab analyzer. Auth is required server-side because the verdict
     * is personalised against the user's daily target.
     */
    suspend fun analyzePlate(
        idToken: String,
        jpegBytes: ByteArray,
    ): PlateAnalyzeResponse = withContext(Dispatchers.IO) {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("image", "plate.jpg", jpegBytes.toRequestBody(JPEG_MEDIA))
            .build()
        val request = Request.Builder()
            .url("$baseUrl/analyze-plate")
            .header("Authorization", "Bearer $idToken")
            .post(body)
            .build()
        execute(request, PlateAnalyzeResponse.serializer())
    }

    private fun <T> execute(
        request: Request,
        serializer: kotlinx.serialization.KSerializer<T>,
    ): T {
        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            check(response.isSuccessful) { friendlyError(response.code, raw) }
            return json.decodeFromString(serializer, raw)
        }
    }

    private fun friendlyError(code: Int, raw: String): String {
        // Server replies with {"detail":"..."} on errors. Surface that text.
        val detail = runCatching {
            val map = Json.parseToJsonElement(raw)
            map.toString().let { _ ->
                Regex("\"detail\"\\s*:\\s*\"([^\"]+)\"").find(raw)?.groupValues?.get(1)
            }
        }.getOrNull()
        return detail ?: "Request failed ($code)"
    }

    private companion object {
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        val JPEG_MEDIA = "image/jpeg".toMediaType()
    }
}
