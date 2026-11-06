package com.calore.tracker.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.calore.tracker.domain.Allergen
import com.calore.tracker.domain.Equipment
import com.calore.tracker.domain.MealType
import java.time.DayOfWeek

/**
 * A person with targets (data-model M1). `ME` is private. Enums are stored by name; lists and
 * maps as JSON through [Converters].
 */
@Entity(tableName = "members")
data class MemberEntity(
    @PrimaryKey val id: String,
    /** [com.calore.tracker.domain.MemberKind] name. */
    val kind: String,
    val name: String,
    val colour: Int,
    val sex: String?,
    /** ISO date. */
    val birthDate: String?,
    val heightCm: Double?,
    val weightKg: Double?,
    val activity: String?,
    val goalType: String?,
    val goalRatePct: Double?,
    @ColumnInfo(defaultValue = "AUTO") val macroMode: String,
    val proteinPerKg: Double?,
    val fatPct: Int?,
    val splitProteinPct: Int?,
    val splitCarbsPct: Int?,
    val splitFatPct: Int?,
    val targetKcal: Int?,
    val targetProteinG: Int?,
    val targetCarbsG: Int?,
    val targetFatG: Int?,
    val mealSplit: Map<MealType, Int>,
    @ColumnInfo(defaultValue = "OMNIVORE") val diet: String,
    val allergens: List<Allergen>,
    val likedFoods: List<String>,
    val dislikedFoods: List<String>,
    val cuisines: List<String>,
    val equipment: List<Equipment>,
    @ColumnInfo(defaultValue = "2") val variety: Int,
    val weeklyBudgetHalers: Long?,
    @ColumnInfo(defaultValue = "2") val cookSessionsPerWeek: Int,
    val cookDays: List<DayOfWeek>,
    @ColumnInfo(defaultValue = "120") val minutesPerSession: Int,
    val preppedMeals: List<MealType>,
    @ColumnInfo(defaultValue = "0") val sharesTargets: Boolean,
    override val createdAt: Long,
    override val updatedAt: Long,
    override val deletedAt: Long? = null,
) : Stamped<MemberEntity> {
    override fun withStamps(createdAt: Long, updatedAt: Long, deletedAt: Long?) =
        copy(createdAt = createdAt, updatedAt = updatedAt, deletedAt = deletedAt)
}

/** Tape measurements and body fat (A5). Private. */
@Entity(
    tableName = "body_measurements",
    indices = [Index(value = ["memberId", "date"])],
)
data class BodyMeasurementEntity(
    @PrimaryKey val id: String,
    val memberId: String,
    val date: String,
    val neckCm: Double?,
    val waistCm: Double?,
    val hipCm: Double?,
    val bodyFatPct: Double?,
    /** [com.calore.tracker.domain.BodyFatMethod] name. */
    val method: String,
    override val createdAt: Long,
    override val updatedAt: Long,
    override val deletedAt: Long? = null,
) : Stamped<BodyMeasurementEntity> {
    override fun withStamps(createdAt: Long, updatedAt: Long, deletedAt: Long?) =
        copy(createdAt = createdAt, updatedAt = updatedAt, deletedAt = deletedAt)
}

/** One weekly check-in (A7); at most one per member and week. Private. */
@Entity(
    tableName = "checkins",
    indices = [Index(value = ["memberId", "weekStart"], unique = true)],
)
data class CheckinEntity(
    @PrimaryKey val id: String,
    val memberId: String,
    /** The Monday, ISO date. */
    val weekStart: String,
    val trendStartKg: Double?,
    val trendEndKg: Double?,
    val avgIntakeKcal: Double?,
    val countedDays: Int,
    val estimatedMaintenanceKcal: Int?,
    val oldTargetKcal: Int,
    val proposedTargetKcal: Int,
    val accepted: Boolean,
    val createdAt: Long,
)
