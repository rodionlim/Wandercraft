package com.wanderlog.android.data.repository

import android.content.Context
import com.squareup.moshi.Moshi
import com.wanderlog.android.data.remote.openai.OpenAiService
import com.wanderlog.android.data.remote.openai.dto.ChatCompletionRequest
import com.wanderlog.android.data.remote.openai.dto.ContentPartDto
import com.wanderlog.android.data.remote.openai.dto.ImagePart
import com.wanderlog.android.data.remote.openai.dto.MessageDto
import com.wanderlog.android.data.remote.openai.dto.ResponseFormatDto
import com.wanderlog.android.data.remote.openai.dto.TextPart
import com.wanderlog.android.core.util.TripAssistantPromptSupport
import com.wanderlog.android.core.util.BudgetDisplayCurrencies
import com.wanderlog.android.domain.model.DocumentHint
import com.wanderlog.android.domain.model.Expense
import com.wanderlog.android.domain.model.ExpenseCategory
import com.wanderlog.android.domain.model.ItineraryItem
import com.wanderlog.android.domain.model.ItineraryItemType
import com.wanderlog.android.domain.model.PackingItem
import com.wanderlog.android.domain.model.ParsedBudgetExpense
import com.wanderlog.android.domain.model.ParsedBudgetExpenseImport
import com.wanderlog.android.domain.model.ParsedActivity
import com.wanderlog.android.domain.model.ParsedBooking
import com.wanderlog.android.domain.model.ParsedFlight
import com.wanderlog.android.domain.model.ParsedHotel
import com.wanderlog.android.domain.model.Place
import com.wanderlog.android.domain.model.TravellerProfile
import com.wanderlog.android.domain.model.Trip
import com.wanderlog.android.domain.model.TripAssistantMessage
import com.wanderlog.android.domain.model.TripAssistantRole
import com.wanderlog.android.domain.model.TripDay
import com.wanderlog.android.domain.repository.AiRepository
import com.wanderlog.android.presentation.settings.AiProvider
import com.wanderlog.android.presentation.settings.GeminiModels
import com.wanderlog.android.presentation.settings.OpenAiModels
import com.wanderlog.android.presentation.settings.SettingsViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject
import retrofit2.HttpException

class AiRepositoryImpl @Inject constructor(
    private val openAiService: OpenAiService,
    private val moshi: Moshi,
    @ApplicationContext private val context: Context
) : AiRepository {

    private val completionTokensModels = listOf(
        "gpt-5",
        "o1",
        "o3",
        "o4",
        "codex"
    )

    private val chatCompletionsVisionModels = setOf(
        "gpt-5.6-sol",
        "gpt-5.6-terra",
        "gpt-5.6-luna",
        "gpt-4o",
        "gpt-4o-mini",
        "gpt-4.1",
        "gpt-4.1-mini",
        "gpt-4.1-nano",
        "o1",
        "o1-mini",
        "o3",
        "o3-mini",
        "o4-mini"
    )

    override suspend fun generateItinerary(
        destination: String,
        startDate: String,
        endDate: String,
        preferences: String,
        travellerProfiles: List<TravellerProfile>,
        updatePrompt: String?,
        existingDays: List<TripDay>
    ): List<TripDay> {
        val travellerCount = travellerProfiles.size.coerceAtLeast(1)
        val travellerContext = travellerProfiles.joinToString(", ") { it.displayName }
            .ifBlank { "No named travellers saved" }
        val systemPrompt = """
            You are a travel planning assistant. Always respond with valid JSON only.
            Never include markdown code blocks or any text outside the JSON.
        """.trimIndent()

        val userPrompt = when {
            existingDays.isNotEmpty() && !updatePrompt.isNullOrBlank() -> {
                buildMultiDayUpdatePrompt(
                    destination = destination,
                    preferences = preferences,
                    travellerCount = travellerCount,
                    travellerContext = travellerContext,
                    availableDays = existingDays,
                    updatePrompt = updatePrompt
                )
            }

            else -> {
                """
                    Generate a day-by-day travel itinerary. Output ONLY valid JSON with this schema:
                    {
                      "days": [
                        {
                          "day_number": 1,
                          "date": "YYYY-MM-DD",
                          "items": [
                            {
                              "title": "...",
                              "type": "PLACE|HOTEL|ACTIVITY|TRANSPORT|FLIGHT|NOTE",
                              "location": "...",
                                                            "address": "...",
                              "start_time": "HH:mm",
                              "end_time": "HH:mm",
                              "notes": "..."
                            }
                          ]
                        }
                      ]
                    }

                    Trip details:
                    - Destination: $destination
                    - Start date: $startDate
                    - End date: $endDate
                    - Travel style: $preferences
                    - Traveller count: $travellerCount
                    - Saved travellers: $travellerContext

                    Important rules:
                    - Keep titles concise and action-focused.
                    - Use location for the most specific venue, landmark, or place name a user would search in Maps.
                    - Do not use a broad suburb or city in location when a more specific place name is available.
                    - Use address only when a real street or descriptive address is known; do not fill it with a broad area just to populate the field.
                """.trimIndent()
            }
        }

        val provider = SettingsViewModel.getAiProvider(context)
        val request = buildChatCompletionRequest(
            provider = provider,
            model = SettingsViewModel.getActiveAiModel(context),
            messages = listOf(
                MessageDto("system", systemPrompt),
                MessageDto("user", userPrompt)
            ),
            responseFormat = ResponseFormatDto("json_object")
        )

        val response = chatCompletionOrThrow(request, "generate itinerary", provider)
        val json = response.choices.first().message.content
        return parseItineraryJson(json, destination)
    }

    override suspend fun updatePackingList(
        trip: Trip,
        existingItems: List<PackingItem>,
        userPrompt: String
    ): List<PackingItem> {
        val travellerContext = trip.travellerProfiles.takeIf { it.isNotEmpty() }?.joinToString(", ") { profile ->
            profile.displayName
        } ?: "No named travellers saved"
        val systemPrompt = "You are a travel packing assistant. Always respond with valid JSON only. Never include markdown code blocks or explanatory text."
        val userPromptText = """
            Update this trip packing list. Output ONLY valid JSON with this schema:
            {
              "items": [
                {
                  "title": "...",
                                    "quantity": 1,
                  "traveller_name": "exact saved traveller name or null",
                  "is_checked": false
                }
              ]
            }

            Important rules:
            - Return the full updated packing list, not a diff.
            - Keep useful existing items unless the user clearly wants them removed or replaced.
            - If traveller names are provided, use one of those exact names when an item belongs to a specific traveller.
            - You may set traveller_name to null for shared family gear that should appear only once.
            - Set quantity to a realistic integer for the full trip duration, traveller ages, and the type of item.
            - Consumables and clothing should usually scale with trip length, weather, laundry assumptions, and traveller needs instead of defaulting to 1.
            - Preserve is_checked for unchanged existing items whenever possible.
            - Prefer concise, concrete item titles.
            - Do not include categories, notes, markdown, or commentary.

            Trip context:
            - Trip name: ${trip.name}
            - Destination: ${trip.destination}
            - Start date: ${trip.startDate}
            - End date: ${trip.endDate}
            - Duration days: ${trip.durationDays}
            - Traveller count: ${trip.travellerCount.coerceAtLeast(1)}
            - Saved travellers: $travellerContext

            Current packing list:
            ${formatPackingItems(existingItems)}

            User request:
            $userPrompt
        """.trimIndent()

        val provider = SettingsViewModel.getAiProvider(context)
        val request = buildChatCompletionRequest(
            provider = provider,
            model = SettingsViewModel.getActiveAiModel(context),
            messages = listOf(
                MessageDto("system", systemPrompt),
                MessageDto("user", userPromptText)
            ),
            responseFormat = ResponseFormatDto("json_object")
        )

        val response = chatCompletionOrThrow(request, "update packing list", provider)
        val json = response.choices.first().message.content
        return parsePackingListJson(json, trip)
    }

    override suspend fun askAboutTrip(
        trip: Trip,
        days: List<TripDay>,
        expenses: List<Expense>,
        packingItems: List<PackingItem>,
        conversation: List<TripAssistantMessage>,
        question: String,
        attachmentParts: List<ContentPartDto>,
        selectedAttachmentNames: List<String>
    ): String {
        val contextPrompt = TripAssistantPromptSupport.buildTripContextPrompt(
            trip = trip,
            days = days,
            expenses = expenses,
            packingItems = packingItems
        )

        val currentQuestionPrompt = TripAssistantPromptSupport.buildCurrentQuestionPrompt(
            question = question,
            selectedAttachmentNames = selectedAttachmentNames
        )
        val currentQuestionContent: Any = listOf(TextPart(text = currentQuestionPrompt)) + attachmentParts

        val messages = buildList {
            add(MessageDto("system", TripAssistantPromptSupport.systemPrompt))
            add(MessageDto("user", contextPrompt))
            conversation.forEach { message ->
                add(
                    MessageDto(
                        role = when (message.role) {
                            TripAssistantRole.USER -> "user"
                            TripAssistantRole.ASSISTANT -> "assistant"
                        },
                        content = message.text
                    )
                )
            }
            add(MessageDto("user", currentQuestionContent))
        }

        val provider = SettingsViewModel.getAiProvider(context)
        val request = buildChatCompletionRequest(
            provider = provider,
            model = SettingsViewModel.getActiveAiModel(context),
            messages = messages
        )

        val response = chatCompletionOrThrow(request, "answer a trip question", provider)
        return response.choices.first().message.content.trim()
    }

    private fun buildMultiDayUpdatePrompt(
        destination: String,
        preferences: String,
        travellerCount: Int,
        travellerContext: String,
        availableDays: List<TripDay>,
        updatePrompt: String
    ): String {
        val daysText = availableDays
            .sortedBy { it.dayNumber }
            .joinToString("\n\n") { day ->
                """
                Day ${day.dayNumber} - ${day.date}
                Existing items:
                ${formatExistingItems(day.items)}
                """.trimIndent()
            }

        return """
            Update an existing itinerary by choosing which existing days should receive new items.
            Output ONLY valid JSON using this schema:
            {
              "days": [
                {
                  "day_number": 1,
                  "date": "YYYY-MM-DD",
                  "items": [
                    {
                      "title": "...",
                      "type": "PLACE|HOTEL|ACTIVITY|TRANSPORT|FLIGHT|NOTE",
                      "location": "...",
                                            "address": "...",
                      "start_time": "HH:mm",
                      "end_time": "HH:mm",
                      "notes": "..."
                    }
                  ]
                }
              ]
            }

            Important rules:
            - Choose only from the existing trip days listed below.
            - Return only the subset of days that should receive new items.
            - Return only new itinerary items to add; do not repeat unchanged existing items.
            - Use the provided day date and day number exactly.
            - Spread additions across multiple days when that fits the request better than forcing everything onto one day.
            - Avoid obvious duplicates with existing items.
            - Choose realistic visit times and durations when the user does not specify them.
            - Keep titles concise and action-focused.
            - Use location for the most specific venue, landmark, or place name a user would search in Maps.
            - Do not use a broad suburb or city in location when a more specific place name is available.
            - Use address only when a real street or descriptive address is known; do not fill it with a broad area just to populate the field.

            Trip details:
            - Destination: $destination
            - Travel style: $preferences
            - Traveller count: $travellerCount
            - Saved travellers: $travellerContext

            Existing trip days:
            $daysText

            User request:
            $updatePrompt
        """.trimIndent()
    }

    private fun formatExistingItems(items: List<ItineraryItem>): String {
        return items
            .sortedBy { it.sortOrder }
            .joinToString("\n") { item ->
                buildString {
                    append("- ")
                    item.startTime?.takeIf { it.isNotBlank() }?.let {
                        append("[$it")
                        item.endTime?.takeIf { value -> value.isNotBlank() }?.let { end -> append("-$end") }
                        append("] ")
                    }
                    append(item.title)
                    item.place?.name?.takeIf { it.isNotBlank() }?.let { append(" @ $it") }
                    item.notes?.takeIf { it.isNotBlank() }?.let { append(" — $it") }
                }
            }
            .ifBlank { "- No items yet for this day." }
    }

    private fun formatPackingItems(items: List<PackingItem>): String {
        return items
            .sortedWith(compareBy<PackingItem> { it.sortOrder }.thenBy { it.title.lowercase() })
            .joinToString("\n") { item ->
                buildString {
                    append("- ")
                    append(item.title)
                    append(" [quantity=${item.quantity}]")
                    item.travellerName?.let { append(" [traveller=$it]") }
                    append(" [checked=${item.isChecked}]")
                }
            }
            .ifBlank { "- No packing items yet." }
    }

    override suspend fun parseFile(contentParts: List<ContentPartDto>, hint: DocumentHint?): ParsedBooking {
        val hintLine = hint?.promptAddendum()?.let { "\n\n$it" }.orEmpty()
        val systemPrompt = "You are a travel document parser. Always respond with valid JSON only.$hintLine"
        val parsePrompt = TextPart(text = """
            Extract all travel details from this document.
                        Prefer ISO 8601 date-time strings when the document includes both a date and a time.
            Include arrival and departure terminals, flight type or cabin/fare class, and total price whenever they are visible.
            For the flight `price`, return the total amount across all passengers for that booking or segment, not a per-person amount.
            If the document lists separate fares, taxes, or passenger totals, sum them into one final total price string.
                        For accommodation bookings, include the total accommodation price and any host phone number or host contact number when it is visible, especially for Airbnb-style stays.
                        For car rentals and activities, include endDateTime, total price, and booking reference whenever the document shows them.
                        For car rentals, keep pickupLocation and dropoffLocation separate instead of combining both into one location string.
            Output ONLY valid JSON with this schema:
            {
                            "flights": [{"flightNumber":null,"airline":null,"origin":"","destination":"","departureDateTime":null,"arrivalDateTime":null,"departureTerminal":null,"arrivalTerminal":null,"flightType":null,"price":null,"bookingRef":null}],
                            "hotels": [{"name":"","address":null,"checkIn":null,"checkOut":null,"bookingRef":null,"price":null,"hostPhone":null}],
                                        "activities": [{"title":"","location":null,"pickupLocation":null,"dropoffLocation":null,"dateTime":null,"endDateTime":null,"price":null,"bookingRef":null,"notes":null}]
            }
        """.trimIndent())

        val provider = SettingsViewModel.getAiProvider(context)
        val selectedModel = SettingsViewModel.getActiveAiModel(context)
        val selectedParsingModel = SettingsViewModel.getActiveAiParsingModel(context)
        val requestModel = pickParsingModel(provider, selectedModel, selectedParsingModel, contentParts)
        val allParts: List<Any> = listOf(parsePrompt) + contentParts
        val request = buildChatCompletionRequest(
            provider = provider,
            model = requestModel,
            messages = listOf(
                MessageDto("system", systemPrompt),
                MessageDto("user", allParts)
            ),
            responseFormat = ResponseFormatDto("json_object")
        )

        val response = chatCompletionOrThrow(
            request,
            "parse booking document with model $requestModel",
            provider
        )
        val json = response.choices.first().message.content
        return parseBookingJson(json)
    }

    override suspend fun parseBudgetExpenses(
        contentParts: List<ContentPartDto>,
        fallbackCurrencyCode: String
    ): ParsedBudgetExpenseImport {
        val provider = SettingsViewModel.getAiProvider(context)
        val requestModel = pickParsingModel(
            provider,
            SettingsViewModel.getActiveAiModel(context),
            SettingsViewModel.getActiveAiParsingModel(context),
            contentParts
        )
        val systemPrompt = "You are a budget expense photo parser. Always respond with valid JSON only."
        val parsePrompt = TextPart(text = """
            Extract every budget expense visible in this photo.
            If the image contains multiple charges or line items, return multiple items.
            Prefer the currency code $fallbackCurrencyCode when no currency is visible.
            Use these categories only: TRANSPORT, ACCOMMODATION, FOOD, GROCERIES, ACTIVITY, SHOPPING, OTHER.
            Output ONLY valid JSON with this schema:
            {
              "items": [
                {
                  "title": "",
                  "amount": "",
                  "currencyCode": "$fallbackCurrencyCode",
                  "category": "OTHER",
                  "date": null,
                  "notes": null
                }
              ]
            }
        """.trimIndent())

        val request = buildChatCompletionRequest(
            provider = provider,
            model = requestModel,
            messages = listOf(
                MessageDto("system", systemPrompt),
                MessageDto("user", listOf(parsePrompt) + contentParts)
            ),
            responseFormat = ResponseFormatDto("json_object")
        )

        val response = chatCompletionOrThrow(
            request,
            "parse budget expense photo with model $requestModel",
            provider
        )
        return parseBudgetExpenseJson(response.choices.first().message.content, fallbackCurrencyCode)
    }

    private fun pickParsingModel(
        provider: AiProvider,
        selectedModel: String,
        selectedParsingModel: String,
        contentParts: List<ContentPartDto>
    ): String {
        val hasImageInput = contentParts.any { it is ImagePart }
        if (!hasImageInput) {
            return selectedModel
        }
        return when (provider) {
            AiProvider.GEMINI -> GeminiModels.sanitizeParsingModel(selectedParsingModel)
            AiProvider.OPENAI -> selectedParsingModel.takeIf { it in chatCompletionsVisionModels }
                ?: OpenAiModels.DEFAULT_PARSING_MODEL
        }
    }

    private fun buildChatCompletionRequest(
        provider: AiProvider,
        model: String,
        messages: List<MessageDto>,
        responseFormat: ResponseFormatDto? = null
    ): ChatCompletionRequest {
        val normalizedModel = model.trim().lowercase()
        val isGemini = provider == AiProvider.GEMINI
        val usesCompletionTokens = provider == AiProvider.OPENAI && completionTokensModels.any { tokenModel ->
            normalizedModel.startsWith(tokenModel) || normalizedModel.contains(tokenModel)
        }

        return ChatCompletionRequest(
            model = model,
            messages = messages,
            responseFormat = responseFormat,
            // Leave max_tokens unset for Gemini so thinking tokens + JSON output aren't truncated at 4096.
            maxTokens = if (isGemini || usesCompletionTokens) null else 16384,
            maxCompletionTokens = if (!isGemini && usesCompletionTokens) 16384 else null,
            // Leave sampling at the model default for the new reasoning family.
            temperature = if (normalizedModel.startsWith("gpt-5.6")) null else 0.3
        )
    }

    private suspend fun chatCompletionOrThrow(
        request: ChatCompletionRequest,
        action: String,
        provider: AiProvider = SettingsViewModel.getAiProvider(context)
    ) = try {
        openAiService.chatCompletion(request)
    } catch (exception: HttpException) {
        val errorBody = exception.response()?.errorBody()?.string()?.trim().orEmpty()
        val detail = errorBody.ifBlank { exception.message() ?: "HTTP ${exception.code()}" }
        throw IllegalStateException("${provider.label} failed to $action: $detail", exception)
    }

    private fun stripJsonCodeFence(raw: String): String {
        val trimmed = raw.trim()
        val unfenced = if (trimmed.startsWith("```")) {
            trimmed
                .replaceFirst(Regex("^```(?:json)?\\s*", RegexOption.IGNORE_CASE), "")
                .replaceFirst(Regex("\\s*```$"), "")
                .trim()
        } else {
            trimmed
        }
        val firstBrace = unfenced.indexOf('{')
        val lastBrace = unfenced.lastIndexOf('}')
        return if (firstBrace >= 0 && lastBrace > firstBrace) {
            unfenced.substring(firstBrace, lastBrace + 1)
        } else {
            unfenced
        }
    }

    private fun parseItineraryJson(json: String, tripId: String): List<TripDay> {
        val root = JSONObject(stripJsonCodeFence(json))
        val daysArray = root.getJSONArray("days")
        val result = mutableListOf<TripDay>()

        for (i in 0 until daysArray.length()) {
            val dayObj = daysArray.getJSONObject(i)
            val dayId = UUID.randomUUID().toString()
            val itemsArray = dayObj.optJSONArray("items")
            val items = mutableListOf<ItineraryItem>()

            if (itemsArray != null) {
                for (j in 0 until itemsArray.length()) {
                    val itemObj = itemsArray.getJSONObject(j)
                    val title = itemObj.optString("title", "")
                    val location = itemObj.optString("location").trim().takeIf { it.isNotBlank() }
                    val address = itemObj.optString("address").trim().takeIf { it.isNotBlank() }
                    items.add(
                        ItineraryItem(
                            id = UUID.randomUUID().toString(),
                            tripDayId = dayId,
                            tripId = tripId,
                            title = title,
                            itemType = runCatching {
                                ItineraryItemType.valueOf(itemObj.optString("type", "PLACE"))
                            }.getOrDefault(ItineraryItemType.PLACE),
                            place = when {
                                location != null || address != null -> Place(
                                    name = location ?: title,
                                    address = address
                                )
                                else -> null
                            },
                            startTime = itemObj.optNullableString("start_time"),
                            endTime = itemObj.optNullableString("end_time"),
                            notes = itemObj.optNullableString("notes"),
                            sortOrder = j
                        )
                    )
                }
            }

            result.add(
                TripDay(
                    id = dayId,
                    tripId = tripId,
                    date = java.time.LocalDate.parse(dayObj.getString("date")),
                    dayNumber = dayObj.getInt("day_number"),
                    items = items
                )
            )
        }
        return result
    }

    private fun parsePackingListJson(json: String, trip: Trip): List<PackingItem> {
        val root = JSONObject(stripJsonCodeFence(json))
        val items = root.optJSONArray("items") ?: throw IllegalStateException("AI returned packing JSON without an items array.")

        return buildList {
            for (index in 0 until items.length()) {
                val itemObject = items.optJSONObject(index) ?: continue
                val title = itemObject.optString("title").trim()
                if (title.isBlank()) continue
                val travellerName = itemObject
                    .optNullableString("traveller_name")
                    ?: itemObject.optNullableString("traveler_name")

                add(
                    PackingItem(
                        id = UUID.randomUUID().toString(),
                        tripId = trip.id,
                        title = title,
                        quantity = itemObject.optInt("quantity").takeIf { it > 0 }
                            ?: itemObject.optInt("qty").takeIf { it > 0 }
                            ?: 1,
                        isChecked = itemObject.optBoolean("is_checked", false),
                        travellerName = travellerName?.takeIf { name ->
                            trip.travellerNames.isEmpty() || trip.travellerNames.contains(name)
                        },
                        sortOrder = size
                    )
                )
            }
        }
    }

    private fun JSONObject.optNullableString(key: String): String? =
        if (isNull(key)) null else optString(key).trim().ifBlank { null }

    private fun parseBookingJson(json: String): ParsedBooking {
        val root = JSONObject(stripJsonCodeFence(json))

        val flights = buildList {
            val arr = root.optJSONArray("flights") ?: return@buildList
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(ParsedFlight(
                    flightNumber = o.optString("flightNumber").takeIf { it.isNotBlank() },
                    airline = o.optString("airline").takeIf { it.isNotBlank() },
                    origin = o.optString("origin", ""),
                    destination = o.optString("destination", ""),
                    departureDateTime = o.optString("departureDateTime").takeIf { it.isNotBlank() },
                    arrivalDateTime = o.optString("arrivalDateTime").takeIf { it.isNotBlank() },
                    departureTerminal = o.optString("departureTerminal").takeIf { it.isNotBlank() },
                    arrivalTerminal = o.optString("arrivalTerminal").takeIf { it.isNotBlank() },
                    flightType = o.optString("flightType").takeIf { it.isNotBlank() },
                    price = o.optString("price").takeIf { it.isNotBlank() },
                    bookingRef = o.optString("bookingRef").takeIf { it.isNotBlank() }
                ))
            }
        }

        val hotels = buildList {
            val arr = root.optJSONArray("hotels") ?: return@buildList
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(ParsedHotel(
                    name = o.optString("name", ""),
                    address = o.optString("address").takeIf { it.isNotBlank() },
                    checkIn = o.optString("checkIn").takeIf { it.isNotBlank() },
                    checkOut = o.optString("checkOut").takeIf { it.isNotBlank() },
                    bookingRef = o.optString("bookingRef").takeIf { it.isNotBlank() },
                    price = firstNonBlank(
                        o.optString("price"),
                        o.optString("totalPrice"),
                        o.optString("total_price")
                    ),
                    hostPhone = firstNonBlank(
                        o.optString("hostPhone"),
                        o.optString("host_phone"),
                        o.optString("hostPhoneNumber"),
                        o.optString("host_phone_number"),
                        o.optString("hostContactPhone"),
                        o.optString("host_contact_phone")
                    )
                ))
            }
        }

        val activities = buildList {
            val arr = root.optJSONArray("activities") ?: return@buildList
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(ParsedActivity(
                    title = o.optString("title", ""),
                    location = o.optString("location").takeIf { it.isNotBlank() },
                    pickupLocation = firstNonBlank(
                        o.optString("pickupLocation"),
                        o.optString("pickup_location"),
                        o.optString("pickupAddress"),
                        o.optString("pickup_address")
                    ),
                    dropoffLocation = firstNonBlank(
                        o.optString("dropoffLocation"),
                        o.optString("dropoff_location"),
                        o.optString("dropOffLocation"),
                        o.optString("dropOffAddress"),
                        o.optString("dropoffAddress"),
                        o.optString("dropoff_address")
                    ),
                    dateTime = o.optString("dateTime").takeIf { it.isNotBlank() },
                    endDateTime = firstNonBlank(
                        o.optString("endDateTime"),
                        o.optString("end_time"),
                        o.optString("dropoffDateTime"),
                        o.optString("dropOffDateTime")
                    ),
                    price = firstNonBlank(
                        o.optString("price"),
                        o.optString("totalPrice"),
                        o.optString("total_price")
                    ),
                    bookingRef = firstNonBlank(
                        o.optString("bookingRef"),
                        o.optString("confirmationNumber"),
                        o.optString("confirmation_number")
                    ),
                    notes = o.optString("notes").takeIf { it.isNotBlank() }
                ))
            }
        }

        return ParsedBooking(flights, hotels, activities)
    }

    private fun parseBudgetExpenseJson(json: String, fallbackCurrencyCode: String): ParsedBudgetExpenseImport {
        val root = JSONObject(stripJsonCodeFence(json))
        val items = root.optJSONArray("items") ?: throw IllegalStateException("AI returned budget JSON without an items array.")

        return ParsedBudgetExpenseImport(
            items = buildList {
                for (index in 0 until items.length()) {
                    val itemObject = items.optJSONObject(index) ?: continue
                    val amountText = firstNonBlank(
                        itemObject.optString("amount"),
                        itemObject.optString("price"),
                        itemObject.optString("value")
                    )?.trim()?.takeIf { it.isNotBlank() } ?: continue
                    val title = itemObject.optString("title").trim().ifBlank { "Expense ${index + 1}" }
                    val category = runCatching {
                        ExpenseCategory.valueOf(itemObject.optString("category", ExpenseCategory.OTHER.name).uppercase())
                    }.getOrDefault(ExpenseCategory.OTHER)

                    add(
                        ParsedBudgetExpense(
                            title = title,
                            amountText = amountText,
                            currencyCode = BudgetDisplayCurrencies.sanitize(
                                firstNonBlank(
                                    itemObject.optString("currencyCode"),
                                    itemObject.optString("currency_code"),
                                    fallbackCurrencyCode
                                )
                            ),
                            category = category,
                            dateText = itemObject.optNullableString("date"),
                            notes = itemObject.optNullableString("notes")
                        )
                    )
                }
            }
        )
    }

    private fun firstNonBlank(vararg values: String?): String? =
        values.firstOrNull { !it.isNullOrBlank() }?.trim()
}
