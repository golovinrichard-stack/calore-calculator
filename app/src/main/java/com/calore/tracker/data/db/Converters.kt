package com.calore.tracker.data.db

import androidx.room.TypeConverter
import com.calore.tracker.domain.Allergen
import com.calore.tracker.domain.AnimalFlag
import com.calore.tracker.domain.Equipment
import com.calore.tracker.domain.MealType
import com.calore.tracker.domain.ShopSection
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.time.DayOfWeek

/**
 * Small lists and maps stored as JSON text (data-model.md, Conventions), for example a list of
 * allergens or a meal split. Reading is forgiving: unreadable text gives an empty value, and
 * enum names this app version doesn't know are skipped, so one odd row never crashes a screen.
 */
object JsonColumns {
    val json: Json = Json { ignoreUnknownKeys = true }

    val stringList = ListSerializer(String.serializer())
    val stringIntMap = MapSerializer(String.serializer(), Int.serializer())

    fun encodeStrings(values: List<String>): String = json.encodeToString(stringList, values)

    fun decodeStrings(text: String?): List<String> = decodeOrNull { json.decodeFromString(stringList, it) }(text)
        ?: emptyList()

    fun encodeIntMap(values: Map<String, Int>): String = json.encodeToString(stringIntMap, values)

    fun decodeIntMap(text: String?): Map<String, Int> = decodeOrNull { json.decodeFromString(stringIntMap, it) }(text)
        ?: emptyMap()

    /** Stores enums by [Enum.name], as the rest of the database does. */
    inline fun <reified E : Enum<E>> encodeEnums(values: Collection<E>): String =
        encodeStrings(values.map { it.name })

    inline fun <reified E : Enum<E>> decodeEnums(text: String?): List<E> =
        decodeStrings(text).mapNotNull { enumByName<E>(it) }

    inline fun <reified K : Enum<K>> encodeEnumIntMap(values: Map<K, Int>): String =
        encodeIntMap(values.mapKeys { it.key.name })

    inline fun <reified K : Enum<K>> decodeEnumIntMap(text: String?): Map<K, Int> =
        decodeIntMap(text).mapNotNull { (name, value) -> enumByName<K>(name)?.let { it to value } }.toMap()

    inline fun <reified E : Enum<E>> enumByName(name: String): E? = enumValues<E>().firstOrNull { it.name == name }

    /** Wraps [decode] so blank or malformed text gives null instead of an exception. */
    fun <T> decodeOrNull(decode: (String) -> T): (String?) -> T? = { text ->
        if (text.isNullOrBlank()) {
            null
        } else {
            try {
                decode(text)
            } catch (e: IllegalArgumentException) {
                // SerializationException extends IllegalArgumentException.
                null
            }
        }
    }
}

/**
 * Room converters for JSON columns. Lists of a specific enum need their own pair of functions,
 * because Room picks converters by exact type; write them with [JsonColumns.encodeEnums] and
 * [JsonColumns.decodeEnums], for example:
 *
 * ```
 * @TypeConverter fun allergensToJson(value: List<Allergen>) = JsonColumns.encodeEnums(value)
 * @TypeConverter fun jsonToAllergens(value: String) = JsonColumns.decodeEnums<Allergen>(value)
 * ```
 */
class Converters {
    @TypeConverter
    fun stringListToJson(value: List<String>): String = JsonColumns.encodeStrings(value)

    @TypeConverter
    fun jsonToStringList(value: String?): List<String> = JsonColumns.decodeStrings(value)

    @TypeConverter
    fun stringIntMapToJson(value: Map<String, Int>): String = JsonColumns.encodeIntMap(value)

    @TypeConverter
    fun jsonToStringIntMap(value: String?): Map<String, Int> = JsonColumns.decodeIntMap(value)

    @TypeConverter
    fun mealIntMapToJson(value: Map<MealType, Int>): String = JsonColumns.encodeEnumIntMap(value)

    @TypeConverter
    fun jsonToMealIntMap(value: String?): Map<MealType, Int> = JsonColumns.decodeEnumIntMap(value)

    @TypeConverter
    fun mealListToJson(value: List<MealType>): String = JsonColumns.encodeEnums(value)

    @TypeConverter
    fun jsonToMealList(value: String?): List<MealType> = JsonColumns.decodeEnums(value)

    @TypeConverter
    fun allergenListToJson(value: List<Allergen>): String = JsonColumns.encodeEnums(value)

    @TypeConverter
    fun jsonToAllergenList(value: String?): List<Allergen> = JsonColumns.decodeEnums(value)

    @TypeConverter
    fun equipmentListToJson(value: List<Equipment>): String = JsonColumns.encodeEnums(value)

    @TypeConverter
    fun jsonToEquipmentList(value: String?): List<Equipment> = JsonColumns.decodeEnums(value)

    @TypeConverter
    fun dayListToJson(value: List<DayOfWeek>): String = JsonColumns.encodeEnums(value)

    @TypeConverter
    fun jsonToDayList(value: String?): List<DayOfWeek> = JsonColumns.decodeEnums(value)

    @TypeConverter
    fun shopSectionListToJson(value: List<ShopSection>): String = JsonColumns.encodeEnums(value)

    @TypeConverter
    fun jsonToShopSectionList(value: String?): List<ShopSection> = JsonColumns.decodeEnums(value)

    @TypeConverter
    fun animalFlagListToJson(value: List<AnimalFlag>): String = JsonColumns.encodeEnums(value)

    @TypeConverter
    fun jsonToAnimalFlagList(value: String?): List<AnimalFlag> = JsonColumns.decodeEnums(value)
}
