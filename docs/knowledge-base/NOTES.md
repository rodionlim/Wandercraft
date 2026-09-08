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
