# AI Food Lens — Implementation Plan

Source: `AI_Food_Lens_Pitch_Styled.pdf` (10-slide pitch).
Goal of this doc: translate the pitch into a buildable plan, separating what's
feasible from what isn't, and proposing the simplest path to a working MVP.

---

## 1. What the product actually is (distilled)

A user-triggered, on-screen AI assistant that, while the user is inside another
app (Swiggy/Zomato/BigBasket/etc.), can:

1. Be summoned via a floating button.
2. Capture the current screen with explicit one-tap consent.
3. Analyse the visible food items (vision + OCR + nutrition lookup).
4. Show a compact bubble: kcal, macros, health score, one swap suggestion.
5. Discard the screenshot after analysis. No background monitoring.

Hard product constraints from the deck:
- **Latency budget: < 2 seconds** end-to-end.
- **Privacy-first**: no silent capture, no gallery access, food-only analysis,
  screenshot not persisted by default.
- **No app switching**: results render as an overlay on top of the host app.

---

## 2. Feasibility check (the honest part)

### Android — fully feasible
Every primitive the deck describes maps to a documented Android API:

| Pitch requirement | Android primitive |
|---|---|
| Floating button on top of any app | `SYSTEM_ALERT_WINDOW` + `WindowManager` overlay from a foreground `Service` |
| Capture the current screen on tap | `MediaProjection` API (per-session user consent) |
| Result bubble overlay | Second `WindowManager` view (Jetpack Compose in a `ComposeView`) |
| One-tap permission UX | `MediaProjectionManager.createScreenCaptureIntent()` |
| Don't capture protected content | `FLAG_SECURE` is honored automatically — banking apps will appear black, which is the right behavior |

### iOS — the core mechanic is **not possible**
- iOS does not permit system-wide floating UI over other apps.
- Apps cannot programmatically screenshot another app's content.
- Realistic iOS surfaces are: a **Share Extension** (user manually screenshots,
  shares to the app), a **Photos picker flow**, or a **Siri Shortcut**.
- Recommendation: **Android-first**. Treat iOS as a Phase-2 share-extension
  variant of the same backend. Do not promise feature parity in pitches.

### Risks worth naming up front
- **Play Store policy on `SYSTEM_ALERT_WINDOW`** is tightening. We must publish
  a clear data-safety form, request the permission contextually, and ideally
  use a foreground service with a visible notification while the bubble is on.
- **MediaProjection re-prompts** on every session unless the user opts into
  a persistent session — we should match the pitch's "user-triggered, explicit"
  framing and re-prompt every time, at least for v1.
- **Latency**: 2 s is tight for a round-trip vision LLM call (network ~150 ms,
  vision inference ~800–2500 ms depending on model). Mitigation in §4.
- **Nutrition accuracy**: estimates from a screenshot will be approximate.
  We should always show "≈" and a confidence band, and never give medical
  advice. (Health score is heuristic, not clinical.)

---

## 3. Architecture (MVP)

```
┌────────────────────────── Android app (Kotlin) ─────────────────────────┐
│                                                                         │
│  ┌──────────────┐   tap    ┌──────────────────┐   bitmap                │
│  │ Floating     │ ───────▶ │ Capture pipeline │ ──────┐                 │
│  │ button       │          │ (MediaProjection)│       │                 │
│  │ (overlay svc)│          └──────────────────┘       ▼                 │
│  └──────────────┘                              ┌─────────────┐          │
│                                                │ On-device   │          │
│                                                │ pre-process │          │
│                                                │ - crop UI   │          │
│                                                │ - ML Kit    │          │
│                                                │   OCR       │          │
│                                                └──────┬──────┘          │
│                                                       │ items + image   │
│                                                       ▼                 │
│                                              ┌────────────────┐         │
│                                              │ Result bubble  │ ◀─┐     │
│                                              │ (Compose       │   │     │
│                                              │  overlay)      │   │     │
│                                              └────────────────┘   │     │
└─────────────────────────────────────────────────┬──────────────────│────┘
                                                  │ HTTPS            │
                                                  ▼                  │
┌──────────────────── Backend (small, stateless) ─────────────────────────┐
│                                                                         │
│  POST /analyze  { items[], region_hint, image (optional) }              │
│                                                                         │
│  ┌────────────┐    ┌────────────────────┐    ┌──────────────────────┐   │
│  │ Item       │ ─▶ │ Nutrition lookup   │ ─▶ │ Vision LLM fallback  │   │
│  │ normalizer │    │ (IFCT + USDA cache)│    │ (only when needed)   │   │
│  └────────────┘    └────────────────────┘    └──────────┬───────────┘   │
│                                                         │               │
│                              ┌──────────────────────────▼───────┐       │
│                              │ Health-scoring + swap suggester  │       │
│                              └──────────────────────────────────┘       │
└─────────────────────────────────────────────────────────────────────────┘
```

### Why this shape (the key trade-off)
The pitch screenshots imply "send the whole image to a vision model." That's
the simplest path but the slowest and most expensive. The split above does:

1. **OCR on-device first** (Google ML Kit text recognition is free, ~100 ms).
   Most cart screens are 90% text. If we get clean item names + prices, we
   skip the vision call entirely and just look up nutrition.
2. **Vision LLM as fallback** for image-heavy menu screens or when OCR is
   ambiguous (e.g., "Chicken biryani" vs. "Chicken biryani family pack").
3. **Cached nutrition DB** (Indian Food Composition Tables + USDA FoodData
   Central) is the source of truth; the LLM is for matching, not for inventing
   numbers.

This is the difference between a 2 s product and a 5 s one, and between a
₹0.05/scan unit cost and ₹0.50/scan.

---

## 4. Latency budget (how we hit < 2 s)

Target end-to-end (tap → bubble visible):

| Stage | Budget | Notes |
|---|---|---|
| MediaProjection consent dialog (first time) | excluded | one-time per session |
| Screen capture + bitmap | 100 ms | `ImageReader` callback |
| On-device crop + ML Kit OCR | 250 ms | runs in parallel with upload prep |
| Network round-trip (4G typical) | 200 ms | small payload: text + thumbnail |
| Backend nutrition lookup | 150 ms | hot cache, Postgres / SQLite |
| Vision LLM (only if OCR insufficient) | 800–1500 ms | Gemini 2.x Flash or Claude Haiku 4.5 vision |
| Bubble render (Compose) | 50 ms | |
| **Typical total (OCR-only path)** | **~700 ms** | |
| **Worst case (vision path)** | **~2200 ms** | show skeleton bubble at 500 ms |

UX rule: render the bubble's frame and a shimmer at ~300 ms regardless, so
perceived latency is always sub-second.

---

## 5. MVP scope (what to build first)

Build only what the deck's Slide 4–6 demonstrates. Cut everything else.

**In scope for v0.1:**
- [ ] Android app shell + onboarding (1 screen explaining the permission)
- [ ] Foreground service hosting the floating button overlay
- [ ] MediaProjection capture pipeline (per-tap consent)
- [ ] On-device ML Kit OCR + simple item parser
- [ ] Backend `POST /analyze` with item normalizer + nutrition DB lookup
- [ ] Result bubble UI (kcal, 3 macros, health score, 1 swap suggestion)
- [ ] "Discard after use" — no on-device or server-side persistence of the image
- [ ] Crash + anonymous usage telemetry (no image, no item content)

**Explicitly deferred (per the deck's own phasing):**
- Phase 2: history, streaks, deeper explanation (the "Plus" tier)
- Phase 2: WhatsApp / chat handoff
- Phase 3: grocery basket analysis
- Phase 3: restaurant menu intelligence
- Phase 4: personal coach, B2B white-label
- iOS share-extension variant
- Custom on-device vision model (use cloud LLM until volume justifies it)

---

## 6. Stack proposal

| Layer | Choice | Rationale |
|---|---|---|
| Mobile | Kotlin + Jetpack Compose, min SDK 26 | Compose works inside `WindowManager` overlays via `ComposeView`; SDK 26 covers ~95% of Indian Android users |
| On-device ML | Google ML Kit (Text Recognition v2) | Free, offline, ~100 ms |
| Capture | `MediaProjection` + `ImageReader` | Standard, no third-party SDK |
| Backend | Python FastAPI on a single small VM, or Cloudflare Workers | Stateless, scales horizontally, deploys in minutes |
| Vision LLM | Gemini 2.x Flash *or* Claude Haiku 4.5 (vision) | Both <1.5 s typical, cheap; pick based on accuracy testing on Indian cart screens |
| Nutrition DB | IFCT (Indian foods) + USDA FoodData Central, materialised into Postgres | Free, authoritative, offline-cacheable |
| Telemetry | PostHog or self-hosted Plausible | No image data ever leaves device |

---

## 7. Privacy implementation (matches the trust pillars on Slide 6)

These are not just policy claims — they need to be enforceable in code:

1. **No background monitoring**: foreground service is started only by the
   user's tap on the floating button; service stops itself after the bubble
   closes. No `JobScheduler`, no `WorkManager` periodic work, no broadcast
   receivers tied to system events.
2. **No silent capture**: `MediaProjection` itself surfaces the system dialog
   on every new session; we do not request the persistent variant.
3. **No gallery access**: the app declares zero photo/media permissions in
   the manifest. Captured bitmap lives in a ByteBuffer, never written to disk.
4. **Food-only analysis**: backend prompt + post-processing reject anything
   that isn't a food/menu screen and return "Not a food screen" rather than
   leaking analysis of unrelated content.
5. **Transparent by design**: the bubble shows exactly which items we read.
6. **Discard after use**: image bytes are zeroed before the buffer is released;
   backend logs only item names + a hash, never the image.

---

## 8. Open questions for the team

1. **Distribution**: Play Store directly, or start as an internal-distribution
   APK while we validate retention? Play Store review of overlay + screen-capture
   apps is non-trivial.
2. **Health score formula**: who owns the rubric? Need a nutritionist sign-off
   before public launch — even a heuristic score becomes implicit medical advice.
3. **Geography**: India-first (IFCT-aligned) is implicit in the deck's pricing
   and food choices. Confirm before we localize the nutrition DB.
4. **Identity**: anonymous device-id only for v0.1, or login from day one?
   Login enables history (Plus tier) but adds onboarding friction.
5. **Cost ceiling per scan**: this drives the OCR-first vs. vision-first call.

---

## 9. Suggested 4-week build path

| Week | Deliverable |
|---|---|
| 1 | Android shell, overlay service, MediaProjection capture working end-to-end with a stub backend that returns canned data. Demoable on a real Swiggy screen. |
| 2 | Real backend: nutrition DB seeded, item normalizer, OCR-first analyze endpoint. No vision LLM yet. |
| 3 | Vision LLM fallback, health score + swap suggester, result bubble polish, latency tuning to hit < 2 s p50. |
| 4 | Privacy hardening, onboarding screen, Play Store internal-track release, instrument retention funnel. |

Goal at end of week 4: a working app on 20–50 internal testers, with metrics
to validate the deck's two riskiest assumptions — **does the bubble actually
change order behavior**, and **will users pay ₹149/mo for the Plus tier**.
