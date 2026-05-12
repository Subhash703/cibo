# Cibo

> See what your meal will do to your body — *before* you order.

Cibo is a floating Android assistant that lives on top of food-delivery apps
(Swiggy, Zomato, Domino's, BigBasket, Blinkit, …). One tap captures your
cart screen, sends it to a vision-LLM, and returns a health-score, calorie
breakdown, and a smarter swap — in under three seconds, without leaving the
food app.

Sign in with email + password to set a daily calorie goal; confirm an order
with one tap and Cibo subtracts those calories from your remaining target.
Every future cart you scan shows how much room you have left for the day.

This is a working prototype, not a published app. The focus is on a
demoable end-to-end loop and a credible privacy story.

---

## Architecture

```
food-ai-lens/                          ← monorepo root
├── apps/
│   ├── android/                       ← Kotlin + Jetpack Compose
│   │   ├── app/                       ← single-module app
│   │   ├── gradle/libs.versions.toml
│   │   └── settings.gradle.kts
│   └── backend/                       ← FastAPI on Python 3.12+
│       ├── src/foodlens_backend/      ← package
│       ├── tests/                     ← pytest
│       ├── .env.example               ← copy to .env, fill in
│       └── pyproject.toml
├── packages/
│   ├── api-contracts/openapi.yaml     ← contract source of truth
│   └── nutrition-data/                ← seed data (legacy OCR path)
├── PLAN.md                            ← original architecture plan
├── AI_Food_Lens_Pitch_Styled.pdf      ← original pitch deck
└── README.md                          ← this file
```

The runtime stack:

| Layer | Tech |
|---|---|
| Android UI | Kotlin 2.0 · Jetpack Compose · Material3 |
| Floating overlay | `WindowManager` overlay + `MediaProjection` for capture |
| Foreground-app scoping | `UsageStatsManager` allowlist (only food apps trigger the bubble) |
| Auth on device | Email + password → JWT stored in `SharedPreferences` |
| HTTP client | OkHttp + kotlinx.serialization |
| Vision analysis | **Gemini 2.5 Flash** with structured-output Pydantic schema |
| Backend | FastAPI · SQLAlchemy 2.0 · SQLite (dev) / Postgres (prod) |
| Auth on backend | bcrypt password hashing · JWT (HS256) via python-jose |

---

## Prerequisites

| Tool | Why | How to install |
|---|---|---|
| **Python 3.12+** | Backend | `brew install python@3.13` (or `@3.12`) |
| **PostgreSQL 14+** | Backend DB | `brew install postgresql@14 && brew services start postgresql@14` |
| **JDK 17** | Android build | `brew install openjdk@17` |
| **Android Studio** | App build + install | <https://developer.android.com/studio> |
| **Android SDK 35** | Compile target | Installed via Android Studio's SDK Manager |
| **ngrok** *(optional but recommended)* | Tunnel the dev backend to your phone | `brew install ngrok` + free account |
| **A Gemini API key** | Vision analysis | Free at <https://aistudio.google.com/apikey> |

---

## Quick start

### 1. Backend

```bash
cd apps/backend

# One-time: create venv + install deps
python3 -m venv .venv
.venv/bin/pip install -e .
.venv/bin/pip install pytest httpx          # dev/test deps

# One-time: create the dev + test Postgres databases
createdb cibo
createdb cibo_test

# One-time: configure secrets
cp .env.example .env
# → open .env and fill in:
#     GEMINI_API_KEY=...
#     JWT_SECRET=$(openssl rand -base64 48)
#     DATABASE_URL=postgresql+psycopg://$USER@localhost:5432/cibo
```

Run the server:

```bash
.venv/bin/python -m uvicorn foodlens_backend.main:app \
    --host 127.0.0.1 --port 8000 --app-dir src
```

Hit `http://127.0.0.1:8000/healthz` — should return `{"status":"ok"}`.

Tables are auto-created on first start via `Base.metadata.create_all`.
For schema changes between deploys we'll add Alembic migrations — for
now drop & recreate the DB (`dropdb cibo && createdb cibo`).

### 2. Tunnel the backend to your phone

A physical Android device on the same Wi-Fi *should* be able to reach
your Mac via LAN IP, but corporate / hotel / guest Wi-Fi often blocks
device-to-device traffic (AP isolation). The bullet-proof option:

```bash
ngrok http 8000
```

Copy the `https://xxxx.ngrok-free.app` URL.

### 3. Android

Open `apps/android/` in Android Studio. Update the backend URL inside
`apps/android/app/build.gradle.kts`:

```kotlin
buildConfigField(
    "String",
    "BACKEND_BASE_URL",
    "\"https://xxxx.ngrok-free.app\"",   // ← paste your ngrok URL
)
```

> The free ngrok tier rotates the subdomain on every restart. A reserved
> domain (paid plan) is the easiest fix for long demos. Alternatively, use
> `adb reverse tcp:8000 tcp:8000` and set the URL to `http://localhost:8000`
> — works over USB regardless of Wi-Fi state.

Sync Gradle, plug in / start your device, and press Run.

The first launch walks through onboarding:
1. **Welcome** — what Cibo does
2. **How it works** — three steps
3. **Permission #1: Display over apps** — for the floating button
4. **Permission #2: Usage access** — to scope the bubble to food apps only
5. **Ready** — start the floating service

After that, open Swiggy / Zomato / Domino's, tap the floating "C" bubble,
and you'll see the result card. Sign in via the Profile screen if you want
daily-intake tracking.

---

## Where do environment variables live?

| Variable | Used by | Where you put it |
|---|---|---|
| `GEMINI_API_KEY` | Backend `/analyze-vision` | `apps/backend/.env` |
| `JWT_SECRET` | Backend `/auth/*` and bearer-token validation | `apps/backend/.env` |
| `DATABASE_URL` | Backend (optional, defaults to local SQLite) | `apps/backend/.env` |
| `BACKEND_BASE_URL` | Android `AnalyzeClient` | `apps/android/app/build.gradle.kts` (`buildConfigField`) |

`apps/backend/.env` is gitignored. The backend auto-loads it via
`python-dotenv` on startup; you don't need to `export` anything in your
shell. See `apps/backend/.env.example` for the template.

For Android, `BACKEND_BASE_URL` is baked into `BuildConfig` at compile time,
so changing it requires a Gradle re-sync + rebuild. For prod it'll point at
your deployed HTTPS API; for dev it points at ngrok or `localhost`.

---

## Testing

Backend:

```bash
cd apps/backend
JWT_SECRET=test-secret .venv/bin/python -m pytest -q
```

Currently 36 tests covering: vision endpoint contract, OCR-text fallback,
nutrition matcher (legacy), auth (register/login/JWT), profile updates,
meal logs (per-user isolation, accumulation, over-target clamping). One
live-Gemini smoke test is skipped unless `GEMINI_API_KEY` is set.

Android: no instrumentation tests yet (manual QA on a device — see the
demo flow below).

---

## End-to-end demo flow

1. Install the app on a physical device. Walk through onboarding, granting
   both permissions.
2. Profile → **Create account** with any email + 8+ char password. Set a
   daily calorie goal (default 2000).
3. Open Swiggy or Zomato. The teal "C" bubble appears.
4. Tap it → grant the one-time MediaProjection consent → ~3 s later you'll
   see a result card with score, macros, item breakdown, and a "If you
   order this: X kcal left of Y" projection.
5. Tap **Yes, ordering it** → ✓ Order logged splash → bubble auto-collapses.
6. Open another cart → the projection now accounts for what you just
   logged. Repeat through the day.

---

## Folders worth knowing

- `apps/android/app/src/main/java/in/foodlens/app/`
  - `MainActivity.kt` — onboarding wizard + Home/Profile screen routing
  - `HomeScreen.kt`, `ProfileScreen.kt` — post-onboarding screens
  - `overlay/FloatingButtonService.kt` — the always-on FGS that hosts the bubble
  - `overlay/OverlayBubbleManager.kt` — the bubble itself; collapsed↔expanded morph
  - `capture/ScreenCaptureService.kt` — MediaProjection + JPEG upload
  - `auth/` — AuthState (persisted JWT) + UserProfile data class
  - `foreground/` — UsageStats poller + food-app allowlist
  - `network/AnalyzeClient.kt` — typed OkHttp client
- `apps/backend/src/foodlens_backend/`
  - `main.py` — FastAPI routes
  - `vision.py` — Gemini 2.5 Flash with structured Pydantic output
  - `analyze.py` — legacy OCR-text path (still mounted at `/analyze`)
  - `auth.py` — bcrypt + JWT
  - `models.py`, `db.py` — SQLAlchemy
  - `schemas.py` — Pydantic request/response types

---

## Notable design decisions

- **Vision LLM, not OCR-DB.** Earlier the backend ran on-device ML Kit OCR
  + a curated nutrition database. Real cart screens fragment items across
  multiple OCR lines (e.g., "Paneer & Capsicum" + "Pizza Mania" is one
  item, not two), and a curated DB will never cover the long tail. Sending
  the screenshot to Gemini 2.5 Flash bypasses both ceilings.
- **Bubble scoped to food apps only.** Android's `SYSTEM_ALERT_WINDOW`
  permission is global, but our `UsageStatsManager` poller (1 Hz) only
  *attaches* the bubble's window when the foreground app is in
  `FoodAppAllowlist`. On Instagram, banking, or your home screen the
  bubble is removed entirely — not hidden. Matches the deck's "Food Only,
  Always" promise.
- **Single-window morph.** The collapsed circle and the expanded card are
  the *same* `WindowManager` attachment. State transitions animate window
  size, position, and corner radius via `ValueAnimator`; content
  cross-fades inside. The card "grows out of" the circle.
- **No silent capture.** MediaProjection requires user consent on every
  invocation. We dismiss the bubble before capture and re-attach it
  *after* the frame is grabbed, so our own UI never bleeds into the OCR /
  vision payload.
- **Email + password auth instead of Google OAuth.** Started with Google
  OIDC, switched to email/password when Play Console credentials weren't
  available. Google path is queued (the `User.google_sub` column is
  reserved for it) — re-enabling is a small additive change.

---

## Deferred (queued for later)

- Permanent backend deployment (Cloud Run / Fly.io / Railway) so demos
  don't depend on a laptop + ngrok session.
- Google Sign-In via Credential Manager (re-add when Play Console
  credentials are available).
- Multi-day intake history + weekly trends.
- "Explain more" detail view + WhatsApp handoff for AI follow-up
  questions (slide 5 of the pitch deck).
- Telemetry to validate the deck's two riskiest assumptions: does the
  bubble actually change order behavior, and will users pay ₹149/mo for
  the Plus tier.
- Production-grade nutritionist sign-off on the health-score formula.

---

## Built from `AI_Food_Lens_Pitch_Styled.pdf`

The original 10-slide pitch deck in the repo root drove the architecture
in `PLAN.md`. The product name moved from "AI Food Lens" → "Cibo" mid-build
(see `apps/android/app/src/main/res/values/strings.xml`). User-visible copy
is fully Cibo; internal package `in.foodlens.app` is unchanged for now —
will rename when the brand is locked in legally.
