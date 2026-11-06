# AI and Imports

## OpenAI & Gemini Integration

- `AiRepositoryImpl` uses `POST /v1/chat/completions` with JSON-object responses, routing through OpenAI (`https://api.openai.com/v1/chat/completions`) or Google Gemini's OpenAI-compatible endpoint (`https://generativelanguage.googleapis.com/v1beta/openai/chat/completions`) via `ApiKeyInterceptor` based on the active provider in Settings.
- Settings persist separate API keys and two model choices per provider:
  - General model for itinerary generation and text-heavy flows
  - Parsing model for image/PDF booking parsing
- Current defaults:
  - OpenAI General: `gpt-5.4-mini`
  - OpenAI Parsing: `gpt-4o-mini`
  - Gemini General: `gemini-3.8-flash`
  - Gemini Parsing: `gemini-3.5-flash-lite`

## AI Generate

- AI Generate has two modes: full-trip generation and update existing days.
- The update-days path sends existing trip-day context back to the model so the LLM can choose which existing days should receive new items.
- `Update days` shows an approximate prompt breakdown for context, input, and total tokens.
- Generated preview items can be accepted individually before commit, and likely overlaps with existing itinerary items start unchecked by default.
- A dedicated breakdown lives in [AI Generate](./ai-generate.md).

## Ask About Trip

- Ask About Trip is a conversational AI screen launched from the itinerary overflow menu.
- Each request can include trip details, itinerary items, expenses, packing items, and optionally selected attachments.
- Attachments are opt-in to reduce token usage.
- PDFs are inlined as extracted text; images are sent as image parts.
- The screen shows an approximate token breakdown for context, current-turn input, and total prompt size.
- Context estimate includes the trip snapshot plus prior conversation; input estimate includes the current question and any selected attachment parts for that turn.

## Booking Import Flow

- Inputs:
  - one or more uploaded files
  - pasted booking text from the import sheet
- PDFs default to text extraction and can optionally be rasterized into images.
- The parsed result becomes `ParsedBooking`, then `FileImportViewModel` maps it into reviewable `ItineraryItem` objects.
- The review step is editable before commit.

## Import Behavior Notes

- Imported items are assigned to matching `TripDay` records when dates can be resolved.
- File-based imports keep a stored local source document and link it back to imported items.
- Imports can create linked expenses automatically for flights, hotels, and activities.
- Flight imports store explicit `Departure:` and `Arrival:` note lines so later map flows can reuse the parsed endpoints.
- Flight place resolution prefers the destination side first at import time, then falls back to the origin if needed.
- Car rentals are treated as transport and can create a separate return entry when pickup and return land on different days.
- Pickup and drop-off locations are tracked separately for car-rental imports.

## Maps and Imported Places

- Imported places are not required to have coordinates at parse time.
- Map flows can later attempt a best-effort Google Places lookup and persist the resolved place back to the item.

## Related References

- [AI Generate](./ai-generate.md)
- [Settings and Onboarding](./settings-and-onboarding.md)
- [Map Integration](./map-integration.md)
