package com.calore.tracker.domain

import java.util.UUID

/**
 * A new random id for a row: a lowercase UUID, 36 characters. Every new row gets its id here,
 * never from SQL, so ids made on different phones can't clash once the household syncs.
 */
fun newId(): String = UUID.randomUUID().toString()

/**
 * The same id on every phone for a row that has a natural key (M11): a basic food, a product's
 * barcode, a starter recipe and its ingredients and steps, a seed shop or product, a week's plan.
 * Without it, two phones in one household would hold the same thing under two ids.
 */
object StableIds {
    fun of(kind: String, key: String): String = UUID.nameUUIDFromBytes("calore:$kind:$key".toByteArray(Charsets.UTF_8)).toString()

    fun food(key: String) = of("food", key)
    fun barcode(barcode: String) = of("barcode", barcode)
    fun recipe(key: String) = of("recipe", key)
    fun recipeIngredient(recipeKey: String, position: Int) = of("recipe", "$recipeKey:ingredient:$position")
    fun recipeStep(recipeKey: String, position: Int) = of("recipe", "$recipeKey:step:$position")
    fun store(key: String) = of("store", key)
    fun product(key: String) = of("product", key)
    fun seedPrice(productKey: String, date: String) = of("price", "$productKey:$date")
    fun plan(weekStart: String) = of("plan", weekStart)
}
