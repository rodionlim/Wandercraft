# Sync reliability review — 2026-09-08

## Scope

Reviewed the Nearby transport, session ViewModel and permissions UI, protocol parsing and comparison, manifest/bundle builders, bundle application, sync metadata, related DAO conflict strategies and foreign keys, and existing regression tests. This is a focused review of the sync path across the codebase, not a claim that every feature is bug-free.

## Fixed

- **Existing-trip join stalls:** the join-only entry previously replied to a host manifest only when its local trip was absent. A device already holding an older trip could wait forever because the host never saw what it needed. Every peer now sends its manifest once per connection, including existing-trip joins.
- **Child data loss during merges:** replacing a trip, day, or stop used SQLite replacement semantics, triggering cascading deletes. Parent writes now use Room Upsert, preserving local-only/newer children and attachment links. A real Room regression test verifies this.
- **Stale session work:** stop/disconnect now cancels session coroutines, clears the selected transport trip, and invalidates callbacks from earlier sessions. Manifest and bundle processing are serialized. Join-only retries do not inherit a prior trip ID.
- **Misleading progress and failures:** connection/transfer waits have an inactivity timeout, file-transfer failures surface an actionable error, and compatible peers acknowledge database application. Optional acknowledgement capability preserves support for older builds using the same data protocol; those show delivery without claiming the receiver saved it.
- **Unclear version mismatch:** explicit differing protocol versions stop before transfer with an instruction to update both devices. Existing tolerant parsing for missing/null control fields is retained. There is no unsafe automatic downgrade of newer trip data.
- **Unscoped incoming data:** bundle and child ownership, duplicate IDs, dates, cross-trip links, attachment paths, and file hashes are checked before changes are applied. Distinct day IDs colliding on the same date are rejected rather than silently replacing a day's records.
- **File/database rollback mismatch:** received attachments and covers use immutable content files, so rolling back a database transaction does not overwrite an older file. Missing attachment content produces a repairable error rather than adding a broken file reference.
- **Expensive comparisons:** manifests now read a consistent metadata snapshot and stream attachment hashes rather than base64-encoding every attachment during each comparison. Manifest coverage includes notes and attachment links. Full bundles also use a consistent database snapshot.
- **Device ID initialization race:** local sync identity initialization is synchronized.
- **Error dismissal:** dismissing an error now clears it in the transport, preventing the next state update from restoring it.

## Validation and limits

- Local Kotlin build, unit regression suite, and Android lint pass.
- A focused Room persistence test passed on a Pixel; a PDF pagination test also passed. Two unrelated Compose UI tests fail on Android 17 because the existing Espresso runtime invokes a removed InputManager API.
- No two-device radio transfer was verified: only one phone was available. Bluetooth/Wi-Fi conditions remain a possible source of failures.
- Data protocol remains version 6. Devices with a different protocol must update. Null/missing legacy fields remain normalized as before.
- Large file bundles still embed base64 content and can require substantial memory. Incremental binary attachment streaming remains future work.
- Immutable unreferenced files may remain after interrupted or superseded merges; cleanup must be coordinated with database references, not performed mid-transaction.
- Same-timestamp records with different content but identical metadata are not content-hashed except attachments; that pre-existing conflict-policy limit remains.

## Device-test incident

The default Android test runner uninstalled the app after instrumentation tests, removing the personal device's local trip database. This was an agent execution mistake: the test configuration should have been checked and personal data protected first. Reinstallation restored some attachments and covers, but the database is explicitly excluded from Android backup. The other device has no recovery copy. Recovery of the full itinerary has not been established.

The project now preserves installed APKs after test runs and blocks connected-test tasks unless explicitly marked for a disposable device. Personal phones must not be used for instrumentation tests. Exporting surviving attachment files for recovery was blocked by automatic approval review pending explicit user authorization; no such archive was completed.

## Platform references

- [Room DAO Upsert behavior](https://developer.android.com/training/data-storage/room/accessing-data.html)
- [Nearby payload completion semantics](https://developers.google.com/nearby/connections/android/exchange-data)
- [AndroidX test APK retention setting](https://android.googlesource.com/platform/frameworks/support/+/765562eaabc9b71e3bd67985cf3701907185e812/gradle.properties)
