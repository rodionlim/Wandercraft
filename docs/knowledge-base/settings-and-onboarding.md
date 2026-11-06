# Settings and Onboarding

## Settings Responsibilities

The settings area manages runtime configuration that should remain on-device and editable by the user.

Current responsibilities include:

- selecting the active AI provider (`OpenAI` or `Gemini`)
- storing the OpenAI API key and Gemini API key independently
- storing the runtime Google Maps / Places key
- selecting the general AI model per provider
- selecting the booking image/PDF parsing model per provider
- selecting the budget display currency
- exposing sync-maintenance actions such as deleted-tombstone reset

## Storage Model

Settings are stored in `EncryptedSharedPreferences` protected by Android Keystore.

Important persisted values include:

- `ai_provider`
- `openai_api_key`
- `gemini_api_key`
- `maps_api_key`
- `openai_model`
- `openai_parsing_model`
- `gemini_model`
- `gemini_parsing_model`
- `budget_display_currency`

## AI Provider & Model Selection

The app uses separate model slots for different workloads per provider:

- general model for itinerary generation and text-oriented AI flows
- parsing model for booking images and PDFs

Current defaults:

- OpenAI general model: `gpt-5.4-mini`
- OpenAI parsing model: `gpt-4o-mini`
- Gemini general model: `gemini-3.8-flash`
- Gemini parsing model: `gemini-3.5-flash-lite`

The selectable model lists (`OpenAiModels` and `GeminiModels`) are defined in `SettingsViewModel`.

## OpenAI API Key Help

The Settings screen includes a help icon beside the OpenAI API key field.

That help dialog explains:

- how to create an OpenAI API key
- where to paste it in the app
- that enabling OpenAI data sharing under `Settings > Data controls > Sharing` can grant free daily tokens
- that those free tokens are usually enough for normal app usage unless the user uploads large images or heavily rasterizes PDFs

The dialog also includes a direct link to the OpenAI API keys page.

## First-Run AI Onboarding

The app also has a lightweight first-run onboarding prompt for AI setup.

- it is triggered from the navigation host at app startup
- it only appears when no OpenAI API key is currently stored
- it can send the user straight to Settings
- it can also open the OpenAI API keys page directly
- if dismissed with `Later`, it stays dismissed for that launch and will reappear on a future launch only if the key is still missing

This keeps AI features discoverable without blocking the rest of the app for users who want to skip setup temporarily.

## Budget Display Currency

Settings also control the budget display currency used by the budget screen.

- default display currency is `SGD`
- totals can be shown in a different display currency from the trip’s native currency
- conversion uses the app’s offline approximate FX table

## Sync Maintenance Entry Point

The Settings screen exposes a local maintenance operation to reset deleted sync tombstones.

This is intended as a deliberate cleanup tool rather than part of normal day-to-day user flow.

## Related References

- [AI and Imports](./ai-and-imports.md)
- [Development and Operations](./development-and-operations.md)
- [README](../../README.md)
