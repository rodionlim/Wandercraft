# Itinerary PDF export

Open a trip, choose **More actions → Export itinerary as PDF**, then choose the file location using Android's file picker.

The PDF includes the entire trip regardless of map/day/rating filters: trip title, destination, dates, day notes, ordered stops, start/end times, place names and addresses, booking references, item notes, and confirmation URLs. Empty days and unscheduled items are retained. Long notes wrap across A4 pages with page numbers.

This is a readable itinerary, not a restorable backup. It does not embed original attachment files, maps, photos, packing lists, or a full budget ledger. Booking notes may contain private details, so choose recipients accordingly when sharing the saved file.

Implementation: `presentation/itinerary/export/ItineraryPdf.kt`, triggered from the itinerary overflow menu. Generation runs off the main thread using Android's native PDF renderer.

The device test verified multi-page output and rendering, but a final human-style visual inspection of the rendered pages was not completed: the instrumented runner removed its temporary output when it uninstalled the application. Device tests are now restricted to disposable devices; no further tests were run on the user's phone.
