package com.nova.ai.agent.tools

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Helpers for reading tool arguments produced by the model.
 *
 * Models return arguments as a JSON string; [parseArgs] converts it into a flat-friendly
 * `Map<String, Any?>` where nested objects/arrays are kept as their raw JSON string form.
 */

/** Returns the string value for [key], or null when absent/not a string. */
fun Map<String, Any?>.str(key: String): String? = this[key] as? String

/** Returns the boolean value for [key], coercing numeric 0/1 and "true"/"false" strings. */
fun Map<String, Any?>.bool(key: String, default: Boolean): Boolean {
    return when (val v = this[key]) {
        is Boolean -> v
        is Number -> v.toInt() != 0
        is String -> v.equals("true", ignoreCase = true)
        else -> default
    }
}

/** Returns the int value for [key], coercing doubles and numeric strings. */
fun Map<String, Any?>.int(key: String, default: Int): Int {
    return when (val v = this[key]) {
        is Number -> v.toInt()
        is String -> v.toIntOrNull() ?: default
        else -> default
    }
}

/**
 * Parses a JSON object string into arguments. Returns an empty map for blank or invalid JSON
 * (callers treat missing required args as tool errors).
 */
fun parseArgs(argsJson: String): Map<String, Any?> {
    if (argsJson.isBlank()) return emptyMap()
    val element = try {
        Json.parseToJsonElement(argsJson)
    } catch (e: Exception) {
        return emptyMap()
    }
    val obj = element as? JsonObject ?: return emptyMap()
    return obj.mapValues { (_, value) -> jsonToAny(value) }
}

private fun jsonToAny(element: JsonElement): Any? {
    return when (element) {
        is JsonNull -> null
        is JsonPrimitive -> when {
            element.isString -> element.contentOrNull
            element.booleanOrNull != null -> element.booleanOrNull
            element.longOrNull != null -> element.longOrNull
            element.doubleOrNull != null -> element.doubleOrNull
            else -> element.contentOrNull
        }
        is JsonObject -> element.toString()
        is JsonArray -> element.toString()
    }
}
