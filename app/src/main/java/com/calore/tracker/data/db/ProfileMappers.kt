package com.calore.tracker.data.db

import com.calore.tracker.domain.ActivityLevel
import com.calore.tracker.domain.BodyFatMethod
import com.calore.tracker.domain.BodyMeasurement
import com.calore.tracker.domain.Checkin
import com.calore.tracker.domain.Diet
import com.calore.tracker.domain.GoalType
import com.calore.tracker.domain.MacroMode
import com.calore.tracker.domain.MacroSplit
import com.calore.tracker.domain.Member
import com.calore.tracker.domain.MemberKind
import com.calore.tracker.domain.Money
import com.calore.tracker.domain.NutritionGoals
import com.calore.tracker.domain.Sex

private inline fun <reified E : Enum<E>> enumOrNull(name: String?): E? =
    name?.let { JsonColumns.enumByName<E>(it) }

internal fun MemberEntity.toDomain(): Member {
    val storedSplit = if (splitProteinPct != null && splitCarbsPct != null && splitFatPct != null) {
        MacroSplit(splitProteinPct, splitCarbsPct, splitFatPct)
    } else {
        null
    }
    val storedTargets = if (targetKcal != null && targetProteinG != null && targetCarbsG != null && targetFatG != null) {
        NutritionGoals(targetKcal, targetProteinG, targetCarbsG, targetFatG)
    } else {
        null
    }
    return Member(
        id = id,
        kind = enumOrNull<MemberKind>(kind) ?: MemberKind.MANAGED,
        name = name,
        colour = colour,
        sex = enumOrNull<Sex>(sex),
        birthDate = birthDate?.let { parseStoredDate(it) },
        heightCm = heightCm,
        weightKg = weightKg,
        activity = enumOrNull<ActivityLevel>(activity),
        goalType = enumOrNull<GoalType>(goalType),
        goalRatePct = goalRatePct,
        macroMode = enumOrNull<MacroMode>(macroMode) ?: MacroMode.AUTO,
        proteinPerKg = proteinPerKg,
        fatPct = fatPct,
        split = storedSplit,
        targets = storedTargets,
        mealSplit = mealSplit.ifEmpty { Member.DEFAULT_MEAL_SPLIT },
        diet = enumOrNull<Diet>(diet) ?: Diet.OMNIVORE,
        allergens = allergens.toSet(),
        likedFoods = likedFoods,
        dislikedFoods = dislikedFoods,
        cuisines = cuisines,
        equipment = equipment.toSet(),
        variety = variety,
        weeklyBudget = weeklyBudgetHalers?.let { Money(it) },
        cookSessionsPerWeek = cookSessionsPerWeek,
        cookDays = cookDays,
        minutesPerSession = minutesPerSession,
        preppedMeals = preppedMeals.toSet(),
        sharesTargets = sharesTargets,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

internal fun Member.toEntity(deletedAt: Long? = null): MemberEntity = MemberEntity(
    id = id,
    kind = kind.name,
    name = name,
    colour = colour,
    sex = sex?.name,
    birthDate = birthDate?.toStored(),
    heightCm = heightCm,
    weightKg = weightKg,
    activity = activity?.name,
    goalType = goalType?.name,
    goalRatePct = goalRatePct,
    macroMode = macroMode.name,
    proteinPerKg = proteinPerKg,
    fatPct = fatPct,
    splitProteinPct = split?.proteinPercent,
    splitCarbsPct = split?.carbsPercent,
    splitFatPct = split?.fatPercent,
    targetKcal = targets?.kcal,
    targetProteinG = targets?.proteinG,
    targetCarbsG = targets?.carbsG,
    targetFatG = targets?.fatG,
    mealSplit = mealSplit,
    diet = diet.name,
    allergens = allergens.sortedBy { it.ordinal },
    likedFoods = likedFoods,
    dislikedFoods = dislikedFoods,
    cuisines = cuisines,
    equipment = equipment.sortedBy { it.ordinal },
    variety = variety,
    weeklyBudgetHalers = weeklyBudget?.halers,
    cookSessionsPerWeek = cookSessionsPerWeek,
    cookDays = cookDays,
    minutesPerSession = minutesPerSession,
    preppedMeals = preppedMeals.sortedBy { it.ordinal },
    sharesTargets = sharesTargets,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

internal fun BodyMeasurementEntity.toDomain(): BodyMeasurement? {
    val parsedDate = parseStoredDate(date) ?: return null
    return BodyMeasurement(
        id = id,
        memberId = memberId,
        date = parsedDate,
        neckCm = neckCm,
        waistCm = waistCm,
        hipCm = hipCm,
        bodyFatPct = bodyFatPct,
        method = enumOrNull<BodyFatMethod>(method) ?: BodyFatMethod.MANUAL,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

internal fun BodyMeasurement.toEntity(): BodyMeasurementEntity = BodyMeasurementEntity(
    id = id,
    memberId = memberId,
    date = date.toStored(),
    neckCm = neckCm,
    waistCm = waistCm,
    hipCm = hipCm,
    bodyFatPct = bodyFatPct,
    method = method.name,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun CheckinEntity.toDomain(): Checkin? {
    val parsedWeek = parseStoredDate(weekStart) ?: return null
    return Checkin(
        id = id,
        memberId = memberId,
        weekStart = parsedWeek,
        trendStartKg = trendStartKg,
        trendEndKg = trendEndKg,
        avgIntakeKcal = avgIntakeKcal,
        countedDays = countedDays,
        estimatedMaintenanceKcal = estimatedMaintenanceKcal,
        oldTargetKcal = oldTargetKcal,
        proposedTargetKcal = proposedTargetKcal,
        accepted = accepted,
        createdAt = createdAt,
    )
}

internal fun Checkin.toEntity(): CheckinEntity = CheckinEntity(
    id = id,
    memberId = memberId,
    weekStart = weekStart.toStored(),
    trendStartKg = trendStartKg,
    trendEndKg = trendEndKg,
    avgIntakeKcal = avgIntakeKcal,
    countedDays = countedDays,
    estimatedMaintenanceKcal = estimatedMaintenanceKcal,
    oldTargetKcal = oldTargetKcal,
    proposedTargetKcal = proposedTargetKcal,
    accepted = accepted,
    createdAt = createdAt,
)
