package com.mahi.assistant.ai

/**
 * IntentClassifier performs rule-based pattern matching on user input to
 * determine the user's intent. For ambiguous inputs that don't match any
 * pattern, it falls back to AI-based classification via Gemini.
 *
 * Supported intent types cover the most common voice assistant commands:
 * device control, weather, news, YouTube, calls, SMS, alarms, routines,
 * calendar, and notifications. Unrecognized input defaults to GENERAL_CHAT.
 */
class IntentClassifier(
    private val aiEngine: AiConversationEngine? = null
) {

    enum class IntentType {
        DEVICE_CONTROL,
        WEATHER,
        NEWS,
        YOUTUBE,
        CALL,
        SMS,
        ALARM,
        ROUTINE,
        CALENDAR,
        NOTIFICATION,
        GENERAL_CHAT
    }

    data class IntentResult(
        val type: IntentType,
        val action: String,
        val params: Map<String, String> = emptyMap(),
        val response: String? = null
    )

    // ──────────────────────────────────────────────────────────────────────────
    // Pattern Definitions
    // ──────────────────────────────────────────────────────────────────────────

    private data class IntentPattern(
        val type: IntentType,
        val action: String,
        val patterns: List<Regex>,
        val paramExtractor: ((MatchResult, String) -> Map<String, String>)? = null
    )

    private val intentPatterns: List<IntentPattern> = listOf(

        // ── DEVICE_CONTROL ────────────────────────────────────────────────────

        IntentPattern(
            type = IntentType.DEVICE_CONTROL,
            action = "flashlight_on",
            patterns = listOf(
                Regex("(?i)\\bturn on\\s+(?:the\\s+)?flashlight\\b"),
                Regex("(?i)\\bflashlight\\s+on\\b"),
                Regex("(?i)\\bswitch on\\s+(?:the\\s+)?flashlight\\b"),
                Regex("(?i)\\benable\\s+(?:the\\s+)?flashlight\\b"),
                Regex("(?i)\\btorch\\s+on\\b"),
                Regex("(?i)\\bturn on\\s+(?:the\\s+)?torch\\b")
            )
        ),

        IntentPattern(
            type = IntentType.DEVICE_CONTROL,
            action = "flashlight_off",
            patterns = listOf(
                Regex("(?i)\\bturn off\\s+(?:the\\s+)?flashlight\\b"),
                Regex("(?i)\\bflashlight\\s+off\\b"),
                Regex("(?i)\\bswitch off\\s+(?:the\\s+)?flashlight\\b"),
                Regex("(?i)\\bdisable\\s+(?:the\\s+)?flashlight\\b"),
                Regex("(?i)\\btorch\\s+off\\b"),
                Regex("(?i)\\bturn off\\s+(?:the\\s+)?torch\\b")
            )
        ),

        IntentPattern(
            type = IntentType.DEVICE_CONTROL,
            action = "wifi_on",
            patterns = listOf(
                Regex("(?i)\\bturn on\\s+(?:the\\s+)?wi\\s*fi\\b"),
                Regex("(?i)\\bwi\\s*fi\\s+on\\b"),
                Regex("(?i)\\benable\\s+(?:the\\s+)?wi\\s*fi\\b"),
                Regex("(?i)\\bswitch on\\s+(?:the\\s+)?wi\\s*fi\\b")
            )
        ),

        IntentPattern(
            type = IntentType.DEVICE_CONTROL,
            action = "wifi_off",
            patterns = listOf(
                Regex("(?i)\\bturn off\\s+(?:the\\s+)?wi\\s*fi\\b"),
                Regex("(?i)\\bwi\\s*fi\\s+off\\b"),
                Regex("(?i)\\bdisable\\s+(?:the\\s+)?wi\\s*fi\\b"),
                Regex("(?i)\\bswitch off\\s+(?:the\\s+)?wi\\s*fi\\b")
            )
        ),

        IntentPattern(
            type = IntentType.DEVICE_CONTROL,
            action = "bluetooth_on",
            patterns = listOf(
                Regex("(?i)\\bturn on\\s+(?:the\\s+)?bluetooth\\b"),
                Regex("(?i)\\bbluetooth\\s+on\\b"),
                Regex("(?i)\\benable\\s+(?:the\\s+)?bluetooth\\b"),
                Regex("(?i)\\bswitch on\\s+(?:the\\s+)?bluetooth\\b")
            )
        ),

        IntentPattern(
            type = IntentType.DEVICE_CONTROL,
            action = "bluetooth_off",
            patterns = listOf(
                Regex("(?i)\\bturn off\\s+(?:the\\s+)?bluetooth\\b"),
                Regex("(?i)\\bbluetooth\\s+off\\b"),
                Regex("(?i)\\bdisable\\s+(?:the\\s+)?bluetooth\\b"),
                Regex("(?i)\\bswitch off\\s+(?:the\\s+)?bluetooth\\b")
            )
        ),

        IntentPattern(
            type = IntentType.DEVICE_CONTROL,
            action = "brightness_up",
            patterns = listOf(
                Regex("(?i)\\bbrightness\\s+up\\b"),
                Regex("(?i)\\bincrease\\s+(?:the\\s+)?brightness\\b"),
                Regex("(?i)\\bmake\\s+(?:the\\s+)?screen\\s+brighter\\b")
            )
        ),

        IntentPattern(
            type = IntentType.DEVICE_CONTROL,
            action = "brightness_down",
            patterns = listOf(
                Regex("(?i)\\bbrightness\\s+down\\b"),
                Regex("(?i)\\bdecrease\\s+(?:the\\s+)?brightness\\b"),
                Regex("(?i)\\bmake\\s+(?:the\\s+)?screen\\s+dimmer\\b"),
                Regex("(?i)\\bdim\\s+(?:the\\s+)?screen\\b")
            )
        ),

        IntentPattern(
            type = IntentType.DEVICE_CONTROL,
            action = "volume_up",
            patterns = listOf(
                Regex("(?i)\\bvolume\\s+up\\b"),
                Regex("(?i)\\bincrease\\s+(?:the\\s+)?volume\\b"),
                Regex("(?i)\\bturn\\s+(?:the\\s+)?volume\\s+up\\b")
            )
        ),

        IntentPattern(
            type = IntentType.DEVICE_CONTROL,
            action = "volume_down",
            patterns = listOf(
                Regex("(?i)\\bvolume\\s+down\\b"),
                Regex("(?i)\\bdecrease\\s+(?:the\\s+)?volume\\b"),
                Regex("(?i)\\bturn\\s+(?:the\\s+)?volume\\s+down\\b")
            )
        ),

        // ── WEATHER ───────────────────────────────────────────────────────────

        IntentPattern(
            type = IntentType.WEATHER,
            action = "get_weather",
            patterns = listOf(
                Regex("(?i)\\bwhat'?s\\s+(?:the\\s+)?weather\\b"),
                Regex("(?i)\\bhow'?s\\s+(?:the\\s+)?weather\\b"),
                Regex("(?i)\\bweather\\s+(?:today|tomorrow|right now|outside)\\b"),
                Regex("(?i)\\b(?:is\\s+it|will\\s+it\\s+be)\\s+(?:hot|cold|rainy|sunny|warm|cool)\\b"),
                Regex("(?i)\\btemperature\\s+(?:today|tomorrow|outside|right now)\\b"),
                Regex("(?i)\\bweather\\s+in\\s+(.+)\\b")
            ),
            paramExtractor = { match, input ->
                val cityMatch = Regex("(?i)\\bweather\\s+in\\s+(.+)\\b").find(input)
                if (cityMatch != null) {
                    mapOf("city" to cityMatch.groupValues[1].trim())
                } else {
                    emptyMap()
                }
            }
        ),

        // ── NEWS ──────────────────────────────────────────────────────────────

        IntentPattern(
            type = IntentType.NEWS,
            action = "get_news",
            patterns = listOf(
                Regex("(?i)\\bshow\\s+(?:me\\s+)?(?:the\\s+)?news\\b"),
                Regex("(?i)\\bwhat'?s\\s+(?:the\\s+)?news\\b"),
                Regex("(?i)\\b(?:latest|recent|today'?s)\\s+news\\b"),
                Regex("(?i)\\bread\\s+(?:me\\s+)?(?:the\\s+)?news\\b"),
                Regex("(?i)\\bnews\\s+(?:about|on)\\s+(.+)\\b"),
                Regex("(?i)\\bheadlines\\b")
            ),
            paramExtractor = { _, input ->
                val topicMatch = Regex("(?i)\\bnews\\s+(?:about|on)\\s+(.+)\\b").find(input)
                if (topicMatch != null) {
                    mapOf("category" to topicMatch.groupValues[1].trim().lowercase())
                } else {
                    emptyMap()
                }
            }
        ),

        // ── YOUTUBE ───────────────────────────────────────────────────────────

        IntentPattern(
            type = IntentType.YOUTUBE,
            action = "play_youtube",
            patterns = listOf(
                Regex("(?i)\\bplay\\s+(?:on\\s+)?youtube\\s+(.+)\\b"),
                Regex("(?i)\\bsearch\\s+youtube\\s+(?:for\\s+)?(.+)\\b"),
                Regex("(?i)\\byoutube\\s+(?:search|play)\\s+(.+)\\b"),
                Regex("(?i)\\bplay\\s+(.+)\\s+on\\s+youtube\\b"),
                Regex("(?i)\\bwatch\\s+(.+)\\s+on\\s+youtube\\b"),
                Regex("(?i)\\bfind\\s+(.+)\\s+on\\s+youtube\\b")
            ),
            paramExtractor = { _, input ->
                val queryPatterns = listOf(
                    Regex("(?i)\\bplay\\s+(?:on\\s+)?youtube\\s+(.+)\\b"),
                    Regex("(?i)\\bsearch\\s+youtube\\s+(?:for\\s+)?(.+)\\b"),
                    Regex("(?i)\\byoutube\\s+(?:search|play)\\s+(.+)\\b"),
                    Regex("(?i)\\bplay\\s+(.+)\\s+on\\s+youtube\\b"),
                    Regex("(?i)\\bwatch\\s+(.+)\\s+on\\s+youtube\\b"),
                    Regex("(?i)\\bfind\\s+(.+)\\s+on\\s+youtube\\b")
                )
                for (pattern in queryPatterns) {
                    val match = pattern.find(input)
                    if (match != null) {
                        return@IntentPattern mapOf("query" to match.groupValues[1].trim())
                    }
                }
                emptyMap()
            }
        ),

        // ── CALL ──────────────────────────────────────────────────────────────

        IntentPattern(
            type = IntentType.CALL,
            action = "make_call",
            patterns = listOf(
                Regex("(?i)\\bcall\\s+(.+)\\b"),
                Regex("(?i)\\bphone\\s+(.+)\\b"),
                Regex("(?i)\\bmake\\s+(?:a\\s+)?call\\s+to\\s+(.+)\\b"),
                Regex("(?i)\\bring\\s+(.+)\\b"),
                Regex("(?i)\\bdial\\s+(.+)\\b")
            ),
            paramExtractor = { _, input ->
                val callPatterns = listOf(
                    Regex("(?i)\\bcall\\s+(.+)\\b"),
                    Regex("(?i)\\bphone\\s+(.+)\\b"),
                    Regex("(?i)\\bmake\\s+(?:a\\s+)?call\\s+to\\s+(.+)\\b"),
                    Regex("(?i)\\bring\\s+(.+)\\b"),
                    Regex("(?i)\\bdial\\s+(.+)\\b")
                )
                for (pattern in callPatterns) {
                    val match = pattern.find(input)
                    if (match != null) {
                        return@IntentPattern mapOf("contact" to match.groupValues[1].trim())
                    }
                }
                emptyMap()
            }
        ),

        // ── SMS ───────────────────────────────────────────────────────────────

        IntentPattern(
            type = IntentType.SMS,
            action = "send_sms",
            patterns = listOf(
                Regex("(?i)\\bsend\\s+(?:a\\s+)?(?:sms|text|message)\\s+to\\s+(.+)\\b"),
                Regex("(?i)\\btext\\s+(.+)\\b"),
                Regex("(?i)\\bsms\\s+(.+)\\b"),
                Regex("(?i)\\bsend\\s+(?:a\\s+)?message\\s+to\\s+(.+)\\b"),
                Regex("(?i)\\bmessage\\s+(.+)\\s+that\\s+(.+)\\b")
            ),
            paramExtractor = { _, input ->
                val contactPatterns = listOf(
                    Regex("(?i)\\bsend\\s+(?:a\\s+)?(?:sms|text|message)\\s+to\\s+(.+?)(?:\\s+that\\s+|\$)\\b"),
                    Regex("(?i)\\btext\\s+(.+?)(?:\\s+that\\s+|\$)\\b"),
                    Regex("(?i)\\bsms\\s+(.+?)(?:\\s+that\\s+|\$)\\b"),
                    Regex("(?i)\\bsend\\s+(?:a\\s+)?message\\s+to\\s+(.+?)(?:\\s+that\\s+|\$)\\b"),
                    Regex("(?i)\\bmessage\\s+(.+)\\s+that\\s+(.+)\\b")
                )
                for (pattern in contactPatterns) {
                    val match = pattern.find(input)
                    if (match != null) {
                        val params = mutableMapOf("contact" to match.groupValues[1].trim())
                        if (match.groupValues.size > 2 && match.groupValues[2].isNotBlank()) {
                            params["message"] = match.groupValues[2].trim()
                        }
                        return@IntentPattern params
                    }
                }
                emptyMap()
            }
        ),

        // ── ALARM ─────────────────────────────────────────────────────────────

        IntentPattern(
            type = IntentType.ALARM,
            action = "set_alarm",
            patterns = listOf(
                Regex("(?i)\\bset\\s+(?:an?\\s+)?alarm\\b"),
                Regex("(?i)\\balarm\\s+for\\s+(.+)\\b"),
                Regex("(?i)\\bwake\\s+me\\s+up\\b"),
                Regex("(?i)\\bset\\s+(?:an?\\s+)?timer\\b"),
                Regex("(?i)\\btimer\\s+for\\s+(.+)\\b"),
                Regex("(?i)\\bremind\\s+me\\s+in\\s+(.+)\\b")
            ),
            paramExtractor = { _, input ->
                val timePatterns = listOf(
                    Regex("(?i)\\balarm\\s+for\\s+(.+)\\b"),
                    Regex("(?i)\\btimer\\s+for\\s+(.+)\\b"),
                    Regex("(?i)\\bremind\\s+me\\s+in\\s+(.+)\\b"),
                    Regex("(?i)\\bwake\\s+me\\s+up\\s+at\\s+(.+)\\b"),
                    Regex("(?i)\\bset\\s+(?:an?\\s+)?alarm\\s+(?:for\\s+)?at\\s+(.+)\\b")
                )
                for (pattern in timePatterns) {
                    val match = pattern.find(input)
                    if (match != null) {
                        return@IntentPattern mapOf("time" to match.groupValues[1].trim())
                    }
                }
                emptyMap()
            }
        ),

        // ── ROUTINE ───────────────────────────────────────────────────────────

        IntentPattern(
            type = IntentType.ROUTINE,
            action = "morning_routine",
            patterns = listOf(
                Regex("(?i)\\bgood\\s+morning\\b"),
                Regex("(?i)\\bmorning\\s+routine\\b"),
                Regex("(?i)\\bstart\\s+(?:my\\s+)?morning\\b")
            )
        ),

        IntentPattern(
            type = IntentType.ROUTINE,
            action = "night_routine",
            patterns = listOf(
                Regex("(?i)\\bgood\\s+night\\b"),
                Regex("(?i)\\bnight\\s+routine\\b"),
                Regex("(?i)\\bbedtime\\s+routine\\b"),
                Regex("(?i)\\bstart\\s+(?:my\\s+)?night\\s+routine\\b")
            )
        ),

        // ── CALENDAR ──────────────────────────────────────────────────────────

        IntentPattern(
            type = IntentType.CALENDAR,
            action = "check_calendar",
            patterns = listOf(
                Regex("(?i)\\bwhat'?s\\s+(?:on\\s+)?(?:my\\s+)?(?:calendar|schedule)\\b"),
                Regex("(?i)\\b(?:do\\s+i\\s+have|any)\\s+(?:appointments?|meetings?|events?)\\b"),
                Regex("(?i)\\bcheck\\s+(?:my\\s+)?(?:calendar|schedule)\\b"),
                Regex("(?i)\\bwhat'?s\\s+(?:my\\s+)?schedule\\b"),
                Regex("(?i)\\bcalendar\\b")
            )
        ),

        IntentPattern(
            type = IntentType.CALENDAR,
            action = "add_event",
            patterns = listOf(
                Regex("(?i)\\badd\\s+(?:an?\\s+)?event\\b"),
                Regex("(?i)\\bschedule\\s+(.+)\\b"),
                Regex("(?i)\\bcreate\\s+(?:an?\\s+)?(?:event|meeting|appointment)\\b")
            ),
            paramExtractor = { _, input ->
                val eventMatch = Regex("(?i)\\bschedule\\s+(.+)\\b").find(input)
                if (eventMatch != null) {
                    mapOf("event" to eventMatch.groupValues[1].trim())
                } else {
                    emptyMap()
                }
            }
        ),

        // ── NOTIFICATION ──────────────────────────────────────────────────────

        IntentPattern(
            type = IntentType.NOTIFICATION,
            action = "read_notifications",
            patterns = listOf(
                Regex("(?i)\\bread\\s+(?:my\\s+)?notifications?\\b"),
                Regex("(?i)\\bcheck\\s+(?:my\\s+)?notifications?\\b"),
                Regex("(?i)\\bany\\s+notifications?\\b"),
                Regex("(?i)\\bwhat\\s+(?:are\\s+)?(?:my\\s+)?notifications?\\b"),
                Regex("(?i)\\bshow\\s+(?:me\\s+)?(?:my\\s+)?notifications?\\b"),
                Regex("(?i)\\bdo\\s+i\\s+have\\s+(?:any\\s+)?notifications?\\b")
            )
        )
    )

    // ──────────────────────────────────────────────────────────────────────────
    // Classification
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Classify the user's input into an IntentResult using rule-based
     * pattern matching. Falls back to AI classification if no pattern matches
     * and an AiConversationEngine is provided.
     *
     * @param input The raw text from speech recognition.
     * @return IntentResult with the classified type, action, and extracted parameters.
     */
    suspend fun classify(input: String): IntentResult {
        val trimmedInput = input.trim()
        if (trimmedInput.isBlank()) {
            return IntentResult(
                type = IntentType.GENERAL_CHAT,
                action = "empty_input",
                response = "I didn't catch that. Could you please repeat?"
            )
        }

        // Try rule-based classification first
        for (intentPattern in intentPatterns) {
            for (pattern in intentPattern.patterns) {
                val match = pattern.find(trimmedInput)
                if (match != null) {
                    val params = intentPattern.paramExtractor?.invoke(match, trimmedInput)
                        ?: emptyMap()
                    return IntentResult(
                        type = intentPattern.type,
                        action = intentPattern.action,
                        params = params
                    )
                }
            }
        }

        // No pattern matched — try AI classification fallback
        if (aiEngine != null) {
            return classifyWithAi(trimmedInput)
        }

        // Default to general chat
        return IntentResult(
            type = IntentType.GENERAL_CHAT,
            action = "general_conversation"
        )
    }

    /**
     * Non-suspend version of classify that only uses rule-based patterns.
     * Use this when you don't need AI fallback or are not in a coroutine context.
     */
    fun classifySync(input: String): IntentResult {
        val trimmedInput = input.trim()
        if (trimmedInput.isBlank()) {
            return IntentResult(
                type = IntentType.GENERAL_CHAT,
                action = "empty_input"
            )
        }

        for (intentPattern in intentPatterns) {
            for (pattern in intentPattern.patterns) {
                val match = pattern.find(trimmedInput)
                if (match != null) {
                    val params = intentPattern.paramExtractor?.invoke(match, trimmedInput)
                        ?: emptyMap()
                    return IntentResult(
                        type = intentPattern.type,
                        action = intentPattern.action,
                        params = params
                    )
                }
            }
        }

        return IntentResult(
            type = IntentType.GENERAL_CHAT,
            action = "general_conversation"
        )
    }

    /**
     * Use the Gemini AI to classify ambiguous input that didn't match
     * any rule-based patterns.
     */
    private suspend fun classifyWithAi(input: String): IntentResult {
        val classificationPrompt = """
            Classify the following user input into exactly one of these intent categories:
            ${IntentType.values().joinToString(", ") { it.name }}

            Also extract any relevant parameters (like contact names, search queries, etc.)

            Respond in this exact JSON format only, nothing else:
            {"type":"INTENT_TYPE","action":"action_name","params":{"key":"value"}}

            User input: $input
        """.trimIndent()

        return try {
            val aiResponse = aiEngine!!.queryOnce(classificationPrompt)

            // Parse the JSON response
            val jsonMatch = Regex("\\{[^}]+\\}").find(aiResponse)
            if (jsonMatch != null) {
                val json = jsonMatch.value
                val typeMatch = Regex(""""type"\s*:\s*"(\w+)"""").find(json)
                val actionMatch = Regex(""""action"\s*:\s*"([^"]+)"""").find(json)

                val typeName = typeMatch?.groupValues?.get(1)
                val intentType = try {
                    IntentType.valueOf(typeName ?: "GENERAL_CHAT")
                } catch (_: IllegalArgumentException) {
                    IntentType.GENERAL_CHAT
                }

                val params = mutableMapOf<String, String>()
                val paramPattern = Regex("""\"(\w+)\"\s*:\s*\"([^\"]+)\"""")
                paramPattern.findAll(json).forEach { match ->
                    val key = match.groupValues[1]
                    val value = match.groupValues[2]
                    if (key != "type" && key != "action") {
                        params[key] = value
                    }
                }

                IntentResult(
                    type = intentType,
                    action = actionMatch?.groupValues?.get(1) ?: "ai_classified",
                    params = params
                )
            } else {
                IntentResult(
                    type = IntentType.GENERAL_CHAT,
                    action = "general_conversation"
                )
            }
        } catch (_: Exception) {
            IntentResult(
                type = IntentType.GENERAL_CHAT,
                action = "general_conversation"
            )
        }
    }
}
