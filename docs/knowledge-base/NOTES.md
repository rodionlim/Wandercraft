# Knowledge Base Notes

This file is the overview for chronology and implementation-history notes.

## Why This Is Split

A single flat notes file is useful at first, but it becomes noisy and hard to query once many features have shipped. To keep the chronology useful for future gap-filling and implementation recall:

- `NOTES.md` stays short and explains the system
- detailed dated notes live under `chronology/`
- monthly files keep the history searchable without growing into one oversized log

## Chronology Entry Points

- [2026-09](./chronology/2026-09.md)

- [Chronology Index](./chronology/index.md)
- [2026-05](./chronology/2026-05.md)
- [2026-04](./chronology/2026-04.md)

## Update Rules

- Add chronology entries for notable feature work, workflow changes, documentation milestones, and architectural changes.
- Use dated entries in descending order within the relevant monthly file.
- Keep each bullet brief and factual.
- Link to the owning topic file when a change has a stable long-form explanation elsewhere in the knowledge base.

## When To Split Further

- Create a new monthly note when the calendar month changes.
- If one month becomes too dense, split it into sub-files only when needed, but keep the chronology index as the stable entry point.
- Do not turn chronology files into deep design docs; those belong in topic pages.

## 2026-09-08 follow-up

- [Sync reliability review and device-test incident](./sync-review-2026-09-08.md)
- [Itinerary PDF export](./itinerary-pdf-export.md)

- 2026-09-09: Added cross-account Drive trip snapshot sharing through Android file picker, validated import preview and transactional merge. See [Drive trip sharing](./drive-trip-sharing.md). Local validation only; recovered phone data untouched.
- 2026-11-06: Added AI provider toggle in Settings to support both OpenAI and Gemini API keys and model selections (`gemini-4-argon`, `gemini-3.8-flash`, `gemini-3.6-flash`, `gemini-3.5-flash-lite`, etc.). See [Settings and Onboarding](./settings-and-onboarding.md) and [AI and Imports](./ai-and-imports.md).
- 2026-11-07: Fixed transparent-background PDF rendering in `FileUtils` (white background fill + 240 DPI viewer rendering), added sync-safe attachment renaming in `ItemAttachmentsScreen` and `AttachmentViewerScreen`, enabled multi-file selection when uploading attachments, and fixed back-navigation when cancelling the initial "Upload attachment" picker. See [Sync and Storage](./sync-and-storage.md).
- 2026-11-08: Added per-expense cost breakdown support (`ExpenseBreakdownItem` with label, amount, and active/inactive toggle) across itinerary item cost entry (`ItineraryItemFormSheet`), itinerary cards (`ItineraryItemCard`), budget screen (`BudgetScreen` / `BudgetViewModel`), Room database migration (`10` -> `11` adding `cost_breakdown` on `expenses`), and sync payloads (`SyncExpensePayload` with backwards-compatible normalization). Inactive breakdown items are excluded from `totalSpent`. Also excluded Day 1 and final trip day flight items from the map view (`MapViewModel` / `MapScreen`) to avoid plotting outbound/return flight lines. See [Sync and Storage](./sync-and-storage.md) and [Map Integration](./map-integration.md).
