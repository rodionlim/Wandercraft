# Trip Sync

## Overview

Trip Sync is a manual, nearby, device-to-device sync flow for sharing one trip between two installs of the app without using a cloud database.

The current implementation is:

- trip-scoped
- foreground and user-initiated
- peer-to-peer over Nearby Connections
- offline-first
- last-writer-wins for sync conflicts

The app compares manifests first, then transfers the required bundle for the selected trip.

## How It Works

1. The host device opens a trip and starts a sync session from that trip.
2. The joining device opens Sync from the trip list and discovers nearby sessions.
3. After connection, the devices exchange trip manifests.
4. The sender transfers the required trip bundle.
5. The receiver applies the bundle locally into Room and app-managed files.

## UI Entry Points

### Host A Trip

1. Open the trip itinerary screen.
2. Tap the Sync action.
3. Grant required permissions.
4. Tap `Host`.

### Join From Another Device

1. Open the trip list.
2. Tap the Sync icon in the top bar.
3. Grant required permissions.
4. Tap `Join`.
5. Select the nearby host and connect.

The trip-list entry is intentionally join-only. Hosting is available only when sync is opened from a specific trip.

## Permissions

Nearby Connections requires different combinations of permissions depending on Android version.

- Android 12+: `ACCESS_COARSE_LOCATION`, `ACCESS_FINE_LOCATION`, `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, `BLUETOOTH_ADVERTISE`
- Android 13+: also `NEARBY_WIFI_DEVICES`
- Older Android versions: location permissions are still required for discovery

The manifest also declares `ACCESS_WIFI_STATE` and `CHANGE_WIFI_STATE` because Nearby may reject advertise/discovery without them.

## Synced Data

The sync bundle includes:

- trip metadata
- trip days
- itinerary items
- expenses
- packing items
- attachments
- item-attachment links
- trip cover image file content when the cover is cached locally under `files/trip-covers/`

Trip cover images are not synced as bare local file paths. The bundle carries the cover bytes and rewrites the cover URI on the receiving device to a local cached file.

## Delete Semantics

Delete behavior is intentionally split:

- deleting a single itinerary item, expense, packing item, or attachment creates a tombstone so the deletion can sync
- deleting an entire trip is now a hard delete and does **not** create a trip tombstone

That means whole-trip deletion no longer propagates as a deleted-trip sync record.

## Resetting Old Tombstones

Older builds could still have soft-deleted rows recorded locally. To clear those historical tombstones:

1. Open Settings.
2. Go to `Sync Maintenance`.
3. Tap `Reset Deleted Tombstones`.
4. Confirm the reset.

This permanently removes soft-deleted rows on the current device only. If multiple devices have old tombstones, you need to reset them on each device.

## Conflict Model

Conflicts use last-writer-wins with deterministic tie-breaking.

Priority is based on:

1. effective timestamp
2. device ID
3. content hash
4. payload size

This keeps merge outcomes deterministic even when timestamps are equal.

## Current Limitations

- sync is manual, not background or automatic
- sync is trip-scoped, not whole-account
- there is no trash or restore flow for whole trips
- old tombstones from earlier builds must be reset locally if they still exist
- two-device behavior still depends on real hardware conditions and Nearby availability

## Owning Code Areas

- `app/src/main/java/com/wanderlog/android/data/sync/`
- `app/src/main/java/com/wanderlog/android/presentation/sync/`
- `app/src/main/java/com/wanderlog/android/presentation/settings/`

## Related References

- [Sync and Storage](./sync-and-storage.md)
- [Project Overview](./project-overview.md)
- [Development and Operations](./development-and-operations.md)

## Reliability changes (2026-09-08)

See the [sync reliability review](./sync-review-2026-09-08.md) for confirmed defects, fixes, validation, and remaining limitations. Both phones should use the same latest app version. A delivered bundle is not necessarily a saved trip: newer peers explicitly confirm database application; older compatible peers require checking the receiving phone.

Instrumented tests must use disposable devices. The test runner can uninstall apps and erase data; connected-test tasks are now guarded, and APK retention is enabled.
