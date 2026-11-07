package com.calore.tracker.ui

import androidx.annotation.StringRes
import com.calore.tracker.R
import com.calore.tracker.domain.Allergen
import com.calore.tracker.domain.Diet
import com.calore.tracker.domain.Equipment
import com.calore.tracker.domain.ShopSection

@StringRes
fun Diet.labelRes(): Int = when (this) {
    Diet.OMNIVORE -> R.string.diet_omnivore
    Diet.VEGETARIAN -> R.string.diet_vegetarian
    Diet.VEGAN -> R.string.diet_vegan
    Diet.PESCATARIAN -> R.string.diet_pescatarian
    Diet.NO_PORK -> R.string.diet_no_pork
}

@StringRes
fun Allergen.labelRes(): Int = when (this) {
    Allergen.GLUTEN -> R.string.allergen_gluten
    Allergen.CRUSTACEANS -> R.string.allergen_crustaceans
    Allergen.EGGS -> R.string.allergen_eggs
    Allergen.FISH -> R.string.allergen_fish
    Allergen.PEANUTS -> R.string.allergen_peanuts
    Allergen.SOY -> R.string.allergen_soy
    Allergen.MILK -> R.string.allergen_milk
    Allergen.NUTS -> R.string.allergen_nuts
    Allergen.CELERY -> R.string.allergen_celery
    Allergen.MUSTARD -> R.string.allergen_mustard
    Allergen.SESAME -> R.string.allergen_sesame
    Allergen.SULPHITES -> R.string.allergen_sulphites
    Allergen.LUPIN -> R.string.allergen_lupin
    Allergen.MOLLUSCS -> R.string.allergen_molluscs
}

@StringRes
fun Equipment.labelRes(): Int = when (this) {
    Equipment.OVEN -> R.string.equipment_oven
    Equipment.AIR_FRYER -> R.string.equipment_air_fryer
    Equipment.SLOW_COOKER -> R.string.equipment_slow_cooker
    Equipment.RICE_COOKER -> R.string.equipment_rice_cooker
    Equipment.MICROWAVE -> R.string.equipment_microwave
    Equipment.BLENDER -> R.string.equipment_blender
}

@StringRes
fun ShopSection.labelRes(): Int = when (this) {
    ShopSection.VEGETABLES -> R.string.section_vegetables
    ShopSection.FRUIT -> R.string.section_fruit
    ShopSection.MEAT -> R.string.section_meat
    ShopSection.FISH -> R.string.section_fish
    ShopSection.DAIRY -> R.string.section_dairy
    ShopSection.EGGS -> R.string.section_eggs
    ShopSection.BAKERY -> R.string.section_bakery
    ShopSection.GRAINS -> R.string.section_grains
    ShopSection.LEGUMES -> R.string.section_legumes
    ShopSection.CANNED -> R.string.section_canned
    ShopSection.FROZEN -> R.string.section_frozen
    ShopSection.SPICES_SAUCES -> R.string.section_spices_sauces
    ShopSection.OILS -> R.string.section_oils
    ShopSection.SNACKS -> R.string.section_snacks
    ShopSection.DRINKS -> R.string.section_drinks
    ShopSection.SUPPLEMENTS -> R.string.section_supplements
    ShopSection.OTHER -> R.string.section_other
}

@StringRes
fun cuisineLabelRes(key: String): Int? = when (key) {
    "czech" -> R.string.cuisine_czech
    "italian" -> R.string.cuisine_italian
    "mediterranean" -> R.string.cuisine_mediterranean
    "asian" -> R.string.cuisine_asian
    "indian" -> R.string.cuisine_indian
    "mexican" -> R.string.cuisine_mexican
    "middle_eastern" -> R.string.cuisine_middle_eastern
    "american" -> R.string.cuisine_american
    else -> null
}

@StringRes
fun varietyLabelRes(level: Int): Int = when (level) {
    1 -> R.string.variety_low
    3 -> R.string.variety_high
    else -> R.string.variety_some
}
