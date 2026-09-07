# निरोग / Nirog

Offline-first Android app that diagnoses crop disease and pest damage from photos
for smallholder farmers in India, and recommends treatment as an escalation
ladder (cultural → biological → chemical). Hindi-first UI, voice output, works
without connectivity.

**Status: pre-data prototype.** The code paths are complete and tested, but the
app ships with **no agronomic data and no ML models** — by design. Wrong
pesticide advice can destroy a season, so nothing here invents label claims,
doses, PHIs, or thresholds. Until real data lands, release builds honestly
abstain on every scan and escalate to an expert.

## Modules

| Module | What |
|---|---|
| `:engine` | Pure JVM recommendation engine — safety invariants I1–I10 (label-claim match, banned actives, PHI vs harvest, FRAC/IRAC rotation, organic certification, ETL locks, dose math). Exhaustively unit-tested; no Android deps. |
| `:core-model` | Domain types shared by engine and app. |
| `:core-data` | Room DB (single source of truth), CSV seed parsers, weather, sync workers, DPDP delete-everything. |
| `:ml` | LiteRT (Play Services) two-stage inference: crop router → disease head. Stub engine in debug builds. |
| `:core-ui` | Design system: paper/blueprint theme, Hindi-first strings (`values/` is Hindi, `values-en/` English). |
| `:feature-scan/-diagnosis/-treatment/-diary` | Guided 3-shot capture with quality gate, result screens, treatment ladder + per-tank dose, spray diary with PHI countdown. |
| `:app` | Navigation, consent, onboarding, plot settings, outbreak radar, voice Q&A, offline status. |
| `backend/` | FastAPI + PostgreSQL/PostGIS: escalation intake, expert review queue, outbreak aggregation, label-claim delta sync. |

## Build

```
./gradlew assembleDebug     # stub diagnosis + synthetic TEST catalog: full demo flow works
./gradlew assembleRelease   # real inference path; abstains until .tflite models exist
./gradlew test              # engine invariants + all unit tests
```

Release signing reads `key.properties` / `release.keystore` from the repo root
(both gitignored — keep your own copies safe). Debug and release APKs have
different signatures; uninstall one before installing the other.

Backend: Python 3.10+, `pip install -r backend/requirements.txt`, run
`uvicorn app.main:app` from `backend/`. Tests: `pytest backend/test_api.py`.

## Before this can ship

- Label claims, banned actives, and ETL thresholds from CIB&RC gazette
  notifications → `core-data/src/main/assets/seed/*.csv` (parsers reject rows
  without a gazette ref).
- Trained `.tflite` models (`crop_router`, `disease_<crop>`) + calibrated
  thresholds in `ml/src/main/assets/config/inference.properties` — every value
  there is a placeholder.
- Agronomist sign-off on everything marked `VERIFY` in code (bigha-per-state
  table, crop durations, quality-gate thresholds).
- Deploy `backend/` and set `NIROG_API_BASE` in `core-data/build.gradle.kts`
  (empty string = all sync is a silent no-op, app stays fully offline).

## Safety rules baked in (not bypassable by flag or setting)

Exact label-claim match only · banned actives excluded with no override ·
PHI must fit days-to-harvest · resistance rotation enforced against the spray
diary · organic plots never see non-NPOP products · chemicals locked below
economic threshold · every exclusion carries a farmer-readable reason ·
uncertain diagnosis escalates to a human, never a guess · no approved product
means saying so, never a fallback.

Privacy: DPDP-compliant — itemised consent before capture, exact GPS never
leaves the device (geohash-5 only), phone numbers hashed, working
delete-everything, photos upload only on explicit per-scan consent.
