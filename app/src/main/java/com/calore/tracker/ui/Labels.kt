package com.calore.tracker.ui

import androidx.annotation.StringRes
import com.calore.tracker.R
import com.calore.tracker.domain.ActivityLevel
import com.calore.tracker.domain.MealType
import com.calore.tracker.domain.Sex
import com.calore.tracker.domain.WeightGoal

@StringRes
fun MealType.labelRes(): Int = when (this) {
    MealType.BREAKFAST -> R.string.meal_breakfast
    MealType.LUNCH -> R.string.meal_lunch
    MealType.DINNER -> R.string.meal_dinner
    MealType.SNACK -> R.string.meal_snack
}

@StringRes
fun Sex.labelRes(): Int = when (this) {
    Sex.MALE -> R.string.sex_male
    Sex.FEMALE -> R.string.sex_female
}

@StringRes
fun ActivityLevel.labelRes(): Int = when (this) {
    ActivityLevel.SEDENTARY -> R.string.activity_sedentary
    ActivityLevel.LIGHT -> R.string.activity_light
    ActivityLevel.MODERATE -> R.string.activity_moderate
    ActivityLevel.ACTIVE -> R.string.activity_active
    ActivityLevel.VERY_ACTIVE -> R.string.activity_very_active
}

@StringRes
fun ActivityLevel.descriptionRes(): Int = when (this) {
    ActivityLevel.SEDENTARY -> R.string.activity_sedentary_desc
    ActivityLevel.LIGHT -> R.string.activity_light_desc
    ActivityLevel.MODERATE -> R.string.activity_moderate_desc
    ActivityLevel.ACTIVE -> R.string.activity_active_desc
    ActivityLevel.VERY_ACTIVE -> R.string.activity_very_active_desc
}

@StringRes
fun WeightGoal.labelRes(): Int = when (this) {
    WeightGoal.LOSE -> R.string.weight_goal_lose
    WeightGoal.MAINTAIN -> R.string.weight_goal_maintain
    WeightGoal.GAIN -> R.string.weight_goal_gain
}
