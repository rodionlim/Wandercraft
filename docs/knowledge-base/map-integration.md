# Map Integration

## Main Responsibilities

The map feature is responsible for:

- showing itinerary items with resolved coordinates on a Google Map
- plotting a polyline through the visible stops
- filtering flight markers so the map stays focused on the trip destination
- backfilling missing coordinates for imported or loosely matched places when possible
- providing a direct Google Maps launch path from itinerary items with an address

## Key Implementation Files

- `presentation/map/MapScreen.kt`
- `presentation/map/MapViewModel.kt`
- `data/remote/places/PlacesDataSource`
- `domain/repository/PlacesRepository`

## Runtime Map Behavior

- `MapScreen` renders a `GoogleMap` composable.
- The map uses a quiet basemap with light/dark styling, numbered markers, and stable per-day colors.
- Day chips filter markers and places; All days restores the whole trip. Search matches titles, place names, and addresses within the selected day.
- Stops and day connections follow itinerary `sortOrder`. Dotted connections describe stop order, not computed travel routes, and are suppressed during search.
- A collapsible places panel shows stop names, times, and addresses. Selecting a row or marker focuses the camera and synchronizes the list selection.
- Each place offers external Google Maps directions using coordinates and the saved place ID when available.
- Floating controls switch standard/satellite maps, toggle connections, and fit visible places.
- Camera bounds updates wait for map loading and account for the header and places panel.
- Resolving, empty, search-empty, and map-configuration states have dedicated messages.

## Coordinate Resolution Strategy

Imported items do not need to arrive with coordinates already populated.

`MapViewModel` attempts a best-effort resolution path for items whose `place` is present but coordinates are missing:

- try the saved address first
- try a combined `name + address` query when helpful
- fall back to the place name alone
- persist the resolved place back onto the itinerary item when a coordinate-bearing match is found

This keeps imported trips usable on the map even when the parser only produced names or addresses.

## Flight Map Placement

Flights are handled differently from regular itinerary items when choosing which endpoint should be shown on the trip map:

- imported flight notes store explicit `Departure:` and `Arrival:` lines
- map resolution can derive a preferred flight place from those notes even when an older saved place is stale
- flights on non-final trip days prefer the arrival side
- flights on the final trip day prefer the departure side
- if a saved flight place does not match the preferred endpoint, the map flow re-resolves it before plotting

This keeps arrival flights anchored to where the trip is headed while still showing the departure-side airport for the final trip day.

## Address-To-Maps Convenience Action

The itinerary item card exposes a direct Google Maps action for items with an address.

- the launch URL now prefers the item's address first
- if no address exists, it falls back to the place name
- coordinates are only used as a fallback after those text-based options

This keeps the Google Maps launch behavior aligned with what the user expects to see, rather than opening an opaque coordinate search when a human-readable address is available.

## Maps API Key Rules

There are two separate Maps/Places concerns in the app:

- the Android Maps SDK key in the manifest
- the runtime Settings key used for Places-based search/fetch behavior

The map screen specifically depends on the manifest `MAPS_API_KEY` coming from `local.properties` and an app rebuild. If that key is missing or unresolved, `MapViewModel` surfaces a clear error explaining that the runtime settings key is not enough for the map screen itself.

## Related References

- [Project Overview](./project-overview.md)
- [Architecture](./architecture.md)
- [Development and Operations](./development-and-operations.md)
- [Trip Sync](./trip-sync.md)
