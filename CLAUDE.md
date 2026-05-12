# Cibo — Project Context

> Notes Claude should load when working in this repo. Captures product
> intent, current state, decisions taken, and open questions so future
> sessions don't have to re-derive them.

## Product

**Cibo** (originally "AI Food Lens") — an Android-first (iOS planned) food
AI assistant. Two surfaces:

1. **Floating button overlay** that scans food-delivery cart screens
   (Swiggy, Zomato, Domino's, BigBasket, Blinkit, …) and shows health
   score, macros, and swap suggestions before the user orders.
2. **Plate tab** — snap a photo of a real-world meal, get a green / yellow
   / red verdict that's personalised against the user's daily kcal target
   and what they've already logged.

Pitch line: *"See what your meal will do to your body — before you order."*

Tagline rules: keep "Cibo" as the subject in user-facing copy. The word
"bubble" was removed from UI — use "Cibo button" or "Cibo" instead.

## Repo layout

```
apps/
├── backend/                # FastAPI + Postgres
├── android/                # Kotlin + Jetpack Compose
└── (mobile/)               # placeholder — see "Framework direction" below
render.yaml                 # Render Blueprint (web + Postgres)
```

## Backend (apps/backend)

- **Stack:** Python 3.13, FastAPI, SQLAlchemy 2.0 + psycopg 3, Pydantic v2.
- **DB:** Postgres only. No SQLite fallback. `DATABASE_URL` required.
  `_normalize_database_url()` rewrites `postgres://` and `postgresql://`
  to `postgresql+psycopg://` so Render's connection string works.
- **Auth:** bcrypt password hashing (with manual UTF-8 truncate to 72
  bytes — passlib + bcrypt 4.x was incompatible). JWT via python-jose,
  HS256, 30-day expiry. `JWT_SECRET` env var (Render auto-generates).
- **Vision:** Gemini 2.5 Flash via google-genai SDK. Structured Pydantic
  output, `thinking_budget=0`. Two routes:
  - `/analyze-vision` — cart screenshots (unauthenticated allowed).
  - `/analyze-plate` — single-plate photo, **requires auth**, response is
    personalised against `_today_summary` and the user's daily target.
- **Error handling:** `_gemini_error_to_http(e, route)` logs upstream
  Gemini code+message via `logger.error(..., exc_info=True)` and returns
  friendly 4xx/503 to the client. Tests tolerate transient 429/503.
- **TDEE:** Mifflin-St Jeor in `tdee.py`. Mirrored in Android
  (`UserProfile.computeSuggestedKcal`) so the slider hint updates live.

### Routes

- `GET  /healthz`
- `POST /analyze` — legacy text-based analyzer (kept for back-compat)
- `POST /analyze-vision` — cart screenshot → AnalyzeResponse + optional daily_summary
- `POST /analyze-plate` — plate photo, auth required → PlateAnalyzeResponse with CoachVerdict
- `POST /auth/register` / `/auth/login` — email+password, returns JWT
- `GET  /me`, `PATCH /me` — profile read/update (body stats)
- `GET  /me/today`, `GET /me/meal-logs/today` — daily summary + list
- `POST /meal-logs` — record a confirmed order

## Deployment

- **Render**, declared via `render.yaml`:
  - `cibo-api` (Docker web service, free tier, **region: oregon**)
  - `cibo-db` (Postgres 16, free tier, **region: oregon**)
- **Why oregon, not singapore:** Render's Singapore IPs trigger Gemini's
  *"User location is not supported"* geo check. Oregon works. Render
  free Postgres requires same-region web service, hence both in oregon.
- **Production URL:** `https://cibo-api-op1m.onrender.com`
- **Free-tier gotcha:** Render free Postgres expires after 90 days.
  Calendar a reminder for day 75 to migrate (Neon / paid Render / Supabase).
- **Cold starts:** free web service sleeps after 15 min idle. Pinging
  `/healthz` every 10 min from cron-job.org / UptimeRobot keeps it warm.

## Android app (apps/android)

- **Stack:** Kotlin 2.0, Jetpack Compose, Material3, minSdk 26, targetSdk 35.
- **Build:** user syncs/builds in Android Studio. **Don't run CLI gradle**
  from Claude — there's a memory rule blocking it.
- **Backend URL:** wired in `app/build.gradle.kts` via `buildConfigField`,
  currently `https://cibo-api-op1m.onrender.com`. ngrok is the fallback
  for local backend dev.

### Key components

- **OverlayBubbleManager** — single-window WindowManager overlay. Morphs
  between collapsed (circle) and expanded (result card) in one window.
- **FloatingButtonService** — foreground service (`specialUse` type)
  that owns the poller + bubble lifecycle. Has a `SCREEN_ON / USER_PRESENT`
  BroadcastReceiver that calls `poller.pulse()` to recover from Doze.
  Exposes a `refresh` ACTION the Home button uses.
- **ScreenCaptureService** — MediaProjection-based capture. Defers
  `showLoading()` until after the frame is captured so we don't capture
  our own bubble. `captureActive` flag prevents re-entry.
- **ForegroundAppPoller** — UsageStatsManager at 1 Hz, 24-hour lookback
  on `queryEvents` (short windows misfire when user idles on Swiggy).
- **MainActivity** — hosts onboarding (3 steps after recent cleanup),
  bottom-nav Main screen (Home / Plate), and Profile. Permission state
  refreshed in `onResume`, includes `batteryUnrestricted` via
  `PowerManager.isIgnoringBatteryOptimizations`.
- **AuthState / UserProfile** — local in-memory profile, persisted JWT.
  `UserPublic.toProfile()` extension (in `network/AnalyzeClient.kt`)
  maps server response → local profile with all body-stats fields.
- **PlateScreen** — camera (FileProvider) + library (PickVisualMedia)
  capture, sealed `PlateUiState` (Idle / Analyzing / Result / Logged / Error),
  verdict banner with spring scale-in animation.

### UX decisions taken

- Removed brand-specific (Xiaomi/MIUI) code paths. Manufacturer
  fingerprinting is brittle — use universal Android APIs instead.
- **Battery saver was the actual culprit** for "bubble doesn't show on
  Xiaomi". Fix: `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
  system prompt, surfaced in a conditional Home-screen tip card that
  auto-hides once granted.
- Home layout: Cibo wordmark + top-right avatar; time-of-day greeting
  ("Good evening, Subhash"); status pill ("Cibo is running"); battery
  tip (conditional); pinned Refresh / Stop buttons at the bottom inside
  a `weight(1f) + verticalScroll` Column so content scrolls without
  clipping the actions.
- Profile uses Material3 `Scaffold` + `TopAppBar` (status-bar inset
  handled, proper toolbar padding) instead of a hand-rolled Row.
- Profile fields (birth_year, sex, weight, height, activity_level,
  suggested_kcal_target) pre-fill correctly on second-device login —
  `handleAuth` and `saveProfile` both go through `UserPublic.toProfile()`.

## Open product / design decisions

### Screen content brief (agreed)

Trimmed onboarding from 5 → 3 (Welcome, How it works, Permissions-in-one).
Home shows greeting + daily ring + status pill + today's meals carousel.
Plate is idle → analyzing → result flow with a color-coded verdict
banner. Profile splits into signed-out (email/password) and signed-in
(goal slider + collapsible Personalise card). Floating overlay morphs
between collapsed bubble and expanded result card.

### Typography

Primary: **Plus Jakarta Sans** (Google Fonts) for body + headings.
Optional display accent: **Fraunces** (variable serif italic) for
hero numbers and editorial flourishes. Single-family alternative:
**Manrope** or **Geist**.

### Framework direction (open)

Current state: Compose Android only. User's preference is **Flutter** for
fluid animation defaults and a single codebase across Android + iOS,
with the floating button kept as a **Kotlin native module** behind a
`MethodChannel`. iOS Share Extension would be Swift behind a similar
channel. iOS won't have the floating overlay — the analogue is a Share
Sheet extension on screenshots.

Compose Multiplatform was considered (preserves the existing Compose
work, single language) but rejected because the user wants Flutter's
default fluidity over reusing existing Compose code.

Planned next step: **one-week Flutter spike** building just the Plate
result screen with full motion (verdict banner reveal, macros stagger-in,
ring animation). Decision to commit to the full Flutter rewrite happens
after the spike feels right in hand.

If the spike proceeds:

```
apps/
├── backend/                        (unchanged)
├── mobile/                         (Flutter)
│   ├── lib/                        (Dart — screens, state, AnalyzeClient)
│   ├── android/app/src/main/kotlin/overlay/   (Kotlin native module —
│   │                                            lifted from current app)
│   └── ios/ShareExtension/         (Swift — cart screenshot share flow)
└── android/                        (kept until Flutter parity, then removed)
```

### Design source

User is iterating UI in **Google Stitch**
(`stitch.withgoogle.com/projects/13289218025924918229`). Stitch is an
SPA — Claude's `WebFetch` can't render it. To get the design into a
session, the user should export PNGs or paste screenshots.

## Important user preferences (durable)

- **No remote pushes from `working-repos/jp-github/*`** (org policy)
  — exception: pushing Cibo to `git@github.com:Subhash703/cibo.git`
  is allowed, and must use `--no-verify`.
- **Don't run CLI gradle** for `apps/android/` — user builds in
  Android Studio.
- Brand "NutriSense" was rejected (trademark conflict). Final brand
  is **Cibo** (`cibo-icon.png` is the launcher).
- User prefers terse responses; avoid recapping completed work.

## Things to remember when changing code

- **Don't commit unless explicitly asked.** Even after a clean diff.
- **Profile pre-fill via `toProfile()`** — don't hand-build a
  `UserProfile` from a `UserPublic` response anywhere else. Use the
  extension.
- **Plate analyzer requires auth** (the verdict is personalised against
  the daily summary). Cart analyzer doesn't.
- **Battery-tip card visibility** is driven by
  `PowerManager.isIgnoringBatteryOptimizations(packageName)`. Don't add
  manufacturer checks back in.
- **Foreground service notifications** call the overlay "the Cibo button"
  (not "the bubble"). User-facing strings dropped the word "bubble".
- **Tests for vision tolerate Gemini upstream errors** (429 / 503 /
  400 on 1×1 inputs). Don't change those into hard assertions.
