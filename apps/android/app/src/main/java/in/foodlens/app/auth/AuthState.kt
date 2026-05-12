package `in`.foodlens.app.auth

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * App-scoped holder for the signed-in user's profile + the Google ID token.
 *
 * Persisted to SharedPreferences so the user stays signed in across app
 * restarts. Tokens have a 1-hour Google-side expiry; we don't refresh
 * proactively in v1 — if the backend rejects the token with 401, the UI
 * prompts the user to sign in again.
 */
class AuthState(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(
        "cibo_auth",
        Context.MODE_PRIVATE,
    )

    private val _user = MutableStateFlow<UserProfile?>(loadUser())
    val user: StateFlow<UserProfile?> = _user.asStateFlow()

    @Volatile
    private var _idToken: String? = prefs.getString(KEY_ID_TOKEN, null)

    val idToken: String? get() = _idToken

    val isSignedIn: Boolean get() = _user.value != null && _idToken != null

    fun setSignedIn(profile: UserProfile, token: String) {
        _user.value = profile
        _idToken = token
        prefs.edit()
            .putString(KEY_USER_JSON, json.encodeToString(profile))
            .putString(KEY_ID_TOKEN, token)
            .apply()
    }

    fun setProfile(profile: UserProfile) {
        _user.value = profile
        prefs.edit().putString(KEY_USER_JSON, json.encodeToString(profile)).apply()
    }

    fun signOut() {
        _user.value = null
        _idToken = null
        prefs.edit().clear().apply()
    }

    private fun loadUser(): UserProfile? {
        val raw = prefs.getString(KEY_USER_JSON, null) ?: return null
        return runCatching { json.decodeFromString<UserProfile>(raw) }.getOrNull()
    }

    private companion object {
        const val KEY_USER_JSON = "user_profile_json"
        const val KEY_ID_TOKEN = "google_id_token"
        val json = Json { ignoreUnknownKeys = true }
    }
}
