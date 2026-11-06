# Trip files and cross-account Google Drive sharing

Added 2026-09-09. Trip Sync now offers **Save trip to Drive or files** and **Open shared trip**, independently of Nearby permissions. Uses Android Storage Access Framework providers, including Drive; no OAuth client setup, Drive API, account credentials, or backend is needed in Wandercraft.

Save a dated `.wandercraft.json` snapshot to Drive, wait for Drive upload, and share the file with the recipient using Drive. Recipient selects the shared file or downloads it first and opens Downloads. Preview identifies the trip and counts; explicit Import and merge applies the existing transactional sync merge. This imports the trip identified by the file, regardless of the trip from which the screen was opened. Newer records win including deletion tombstones. Export later changes as a new snapshot; no live or automatic cloud sync. Both devices need the new file-sharing feature and matching sync protocol.

Version 1 envelope identifies `wandercraft-trip`, wraps protocol 6 bundles, includes cover/booking bytes, notes, packing, budget and itinerary, excludes app settings/API keys. Validation rejects incomplete attachments, bad hashes, deleted trips, unsupported versions and malformed/cross-trip records. Files limited to 50 MB; JSON/base64 processing uses memory. Failed save may leave an incomplete provider document; the UI explains cleanup. File operations stop Nearby sessions to prevent overlapping transfer flows. No persistent Drive access or remote overwrites.

Validation: 54 local unit tests passed, including six codec round-trip/rejection tests; debug APK built. Lint checked separately. Actual Drive account-to-account upload/download and visual phone verification remain manual; no personal data uploaded or device tests run. User reports recovered old trip data from another phone; preserve that restored database.

Android provider reference: https://developer.android.com/guide/topics/providers/document-provider
