# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Android travel planning app — a clone of [wanderlog.com](https://wanderlog.com/). Single-user, offline-first, with AI-powered itinerary generation and booking file import.

## Development Environment

This project can be worked on from macOS, Windows, or Linux as long as the local machine has Android Studio and the Android SDK configured.

- Use Android Studio on the host OS for emulator/device work.
- If you choose to work from WSL, keep Android Studio on Windows and open the project through the UNC path, for example:
  ```
  \\wsl$\Ubuntu\home\rodionlim\workspace\Github\wanderlog
  ```
- Set `sdk.dir` in `local.properties` to the SDK path on the machine running Gradle. On Windows, escape backslashes (for example `C\:\\Users\\YourName\\AppData\\Local\\Android\\Sdk`).

## Build & Run

Run these from any shell on the machine that has the Android SDK configured for this checkout:

```bash
# Build debug APK
./gradlew assembleDebug

# Run unit tests
./gradlew test

# Run instrumented tests (requires emulator or device)
./gradlew connectedAndroidTest

# Clean
./gradlew clean
```

### Prerequisites

1. Copy `local.properties.template` → `local.properties` and set `sdk.dir` to your Android SDK path.
2. Set `MAPS_API_KEY` in `local.properties` (injected into the manifest via `manifestPlaceholders`).
3. In the running app → Settings screen, enter your **OpenAI API key**, choose separate OpenAI models for general trip generation vs booking image/PDF parsing, and optionally set the budget display currency (stored in `EncryptedSharedPreferences`). The runtime Google Maps key helps Places-based search, but `MapScreen` still requires `MAPS_API_KEY` in `local.properties` because the Android Maps SDK reads the manifest key at app startup.

## Architecture

**Kotlin + Jetpack Compose + MVVM + Clean Architecture** with three layers:

| Layer        | Package                                               | Role                                                                                     |
| ------------ | ----------------------------------------------------- | ---------------------------------------------------------------------------------------- |
| Domain       | `domain/model`, `domain/repository`, `domain/usecase` | Pure Kotlin — no Android deps. Defines data models, repository interfaces, and use cases |
| Data         | `data/local`, `data/remote`, `data/repository`        | Room (local), Retrofit/OpenAI (remote), repository implementations                       |
| Presentation | `presentation/`                                       | ViewModels + Compose screens                                                             |

**DI**: Hilt (SingletonComponent). All modules in `core/di/`.

**Navigation**: Single-activity (`MainActivity`), Compose Navigation, `NavGraph.kt` + `Screen.kt` sealed class with typed routes.

## Key Packages

```
core/di/           — DatabaseModule, NetworkModule, RepositoryModule
core/util/         — FileUtils (PDF rasterise, image compress), DateTimeUtils, Result.kt, ApproximateCurrencyConverter
data/local/        — Room: 7 entities + DAOs + WanderlogDatabase
data/remote/openai — OpenAiService (Retrofit), DTOs, ApiKeyInterceptor
data/remote/places — PlacesDataSource (wraps Google Places SDK)
data/repository/   — *RepositoryImpl (bind interfaces to Room/network)
domain/usecase/    — trip/, itinerary/, ai/, expense/, packing/
presentation/      — one sub-package per screen; ViewModels use SavedStateHandle
```

## Room Database

7 tables: `trips`, `trip_days`, `itinerary_items`, `item_attachment_links`, `expenses`, `packing_items`, `attachments`.  
All FKs cascade-delete. Dates stored as ISO-8601 text via `RoomConverters`.  
`WanderlogDatabase` version = 7. Schema exported to `app/schemas/`.

## OpenAI Integration

`AiRepositoryImpl` uses `POST /v1/chat/completions` with `response_format = { "type": "json_object" }` and runtime-configurable model selection from Settings.

- **Itinerary generation**: system + user text prompt → JSON with day/item structure → `TripDay` + `ItineraryItem` lists, using the user-selected general OpenAI model. The flow supports full-trip generation and a multi-day additive update where AI chooses which existing days to extend.
- **File parsing**: message `content` is an array of `ContentPartDto` (text or image). `ParseFileUseCase` can combine one or more uploaded files into a single parsing request, and `ParseTextUseCase` supports pasted booking text from the import sheet. PDFs default to text extraction via `pdfbox-android`, with an import-time checkbox that can rasterise them page-by-page via `android.graphics.pdf.PdfRenderer` → JPEG → base64 for scanned or layout-heavy documents. Parsing uses a separate user-selectable parsing model, and image/PDF requests fall back to a vision-safe model when needed.

The API key is injected per-request by `ApiKeyInterceptor` reading from `EncryptedSharedPreferences`.

## Google Maps & Places

`MapScreen` uses `maps-compose` (`GoogleMap` composable). Markers for each `ItineraryItem` with lat/lng. Polyline connects all stops in order. The map view depends on the manifest `MAPS_API_KEY`; the runtime Settings key is only used for Places-based search/fetch flows. For imported places without saved coordinates, the map flow now tries a best-effort Places lookup and persists the resolved place back onto the itinerary item.

`PlacesDataSource` wraps `PlacesClient.findAutocompletePredictions` (autocomplete), `fetchPlace` (details), and place photo fetching for trip cover images. Destination cover photos are cached locally under app files storage. `PlaceSearchViewModel` debounces the query by 300 ms.

## File Import Flow

`ParseFileUseCase` / `ParseTextUseCase` → branch by MIME type or pasted booking text, with PDFs defaulting to text extraction and optional image rasterisation → combined `ContentPartDto` list → `AiRepositoryImpl.parseFile()` / `parseText()` → `ParsedBooking` → `FileImportViewModel` builds itinerary items and shows an editable review dialog → user confirms → `ItineraryRepository.insertItems()`.

- Imported items are assigned to matching `TripDay`s based on parsed dates when possible.
- For file-based imports, the original source documents are stored locally and imported items keep an `item_attachment_links` record back to the upload.
- Flight, hotel, and activity imports can create linked expenses automatically, and multi-day car rentals can create separate pickup and return itinerary entries.
- The import review step is editable before commit.

## Screens & Navigation

```
TripList → TripForm (create/edit)
TripList → Itinerary(tripId)
  ├─ BottomSheet: ItineraryItemFormSheet
  ├─ BottomSheet: PlaceSearchSheet
  ├─ BottomSheet: ImportSheet
  ├─ Map(tripId[, dayId])
  ├─ Budget(tripId)
  ├─ Packing(tripId)
  └─ AiGenerate(tripId)
Settings (from TripList top-bar)
```

`TripForm` also captures traveller aliases, `PackingScreen` can show an aggregated checklist plus individual traveller tabs backed by per-traveller packing items, and the itinerary screen surfaces the currently active accommodation separately from the rest of the day's items.

## Workflow Notes

- Before implementing a requested feature or change, check `docs/TODO.md` and any nearby project notes to see whether the work has already been completed.
- If the requested task appears to already be done, ask the user whether they consider the task completed and whether the related TODO should be removed or updated.
- Keep the local knowledge base under `docs/knowledge-base/` split by topic instead of growing one large file.
- Maintain `docs/knowledge-base/NOTES.md` as a dated chronological log of notable feature work, workflow changes, and documentation milestones.
- Nearby trip sync has a receiver-side compatibility gotcha: older or mismatched builds can send malformed control manifests with missing or null fields such as `tripId` or `records`. When touching `NearbyTripSyncTransport`, keep the incoming control payload parsing tolerant and normalize nested manifest defaults before deserializing with Moshi, otherwise sync can fail with `Unable to handle control payload` before merge logic runs.

## Device Test Safety

- Never run instrumentation tests on a personal device. Use a disposable emulator/device.
- Connected test tasks require `-PdisposableDevice=true`; this flag must never be used for the user's phone.
- Keep `android.injected.androidTest.leaveApksInstalledAfterRun=true`. The default runner can uninstall the app and erase local data after tests.
- Back up app data before device work that may reinstall or affect storage. Trip databases are excluded from Android backup.
