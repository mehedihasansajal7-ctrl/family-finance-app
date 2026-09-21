package com.familyfinance.app.data.local

import androidx.room.TypeConverter
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Room type converters. Only non-primitive column types need one. */
class Converters {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @TypeConverter
    fun fromStringList(value: List<String>?): String = json.encodeToString<List<String>>(value ?: emptyList())

    @TypeConverter
    fun toStringList(value: String?): List<String> =
        if (value.isNullOrBlank()) emptyList()
        else runCatching { json.decodeFromString<List<String>>(value) }.getOrDefault(emptyList())

    @TypeConverter
    fun fromStringMap(value: Map<String, String>?): String =
        json.encodeToString<Map<String, String>>(value ?: emptyMap())

    @TypeConverter
    fun toStringMap(value: String?): Map<String, String> =
        if (value.isNullOrBlank()) emptyMap()
        else runCatching { json.decodeFromString<Map<String, String>>(value) }.getOrDefault(emptyMap())
}
