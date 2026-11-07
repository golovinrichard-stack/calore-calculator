package com.calore.tracker.data.db

/**
 * Which tables may ever sync to the household (M11) and which stay on this phone (data-model:
 * "shared" and "private" tables). Household sync must only send [SHARED] tables; a test checks
 * that every table is in exactly one set and that cycle data is private (M7.6).
 */
object SyncScope {
    /** Never leave the phone (except in the user's own backup file). */
    val PRIVATE: Set<String> = setOf(
        "foods", // the user's own foods; basic foods ship with the app
        "diary_entries",
        "weight_entries",
        "members", // ME is private; MANAGED rows sync only if shared (M11 decides per row)
        "body_measurements",
        "checkins",
        "cycle_periods",
        "cycle_days",
    )

    /** May sync to the household's phones in M11. */
    val SHARED: Set<String> = setOf(
        "recipes", "recipe_ingredients", "recipe_steps", "recipe_ratings",
        "pots", "batches", "batch_ingredients", "boxes", "box_parts",
        "plans", "plan_slots", "plan_cooks",
        "pantry_items", "stores", "store_products", "prices", "shopping_items",
    )
}
