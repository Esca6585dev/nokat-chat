package com.nokat.chat.data.remote

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull

/** Decodes a JSON array, treating a JSON `null` as an empty list. */
fun <T> Json.decodeList(element: JsonElement, serializer: KSerializer<T>): List<T> =
    if (element is JsonNull) emptyList() else decodeFromJsonElement(ListSerializer(serializer), element)
