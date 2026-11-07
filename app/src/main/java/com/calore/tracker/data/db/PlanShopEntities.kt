package com.calore.tracker.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.calore.tracker.domain.ShopSection

/** A week plan, or a saved template (M5). */
@Entity(tableName = "plans", indices = [Index(value = ["weekStart"])])
data class PlanEntity(
    @PrimaryKey val id: String,
    /** Monday, ISO date; null for a template. */
    val weekStart: String?,
    val templateName: String?,
    /** M6. */
    val generatorSeed: Long? = null,
    override val createdAt: Long,
    override val updatedAt: Long,
    override val deletedAt: Long? = null,
) : Stamped<PlanEntity> {
    override fun withStamps(createdAt: Long, updatedAt: Long, deletedAt: Long?) = copy(createdAt = createdAt, updatedAt = updatedAt, deletedAt = deletedAt)
}

/** One meal of one person on one day in a plan. */
@Entity(tableName = "plan_slots", indices = [Index(value = ["planId"]), Index(value = ["date"])])
data class PlanSlotEntity(
    @PrimaryKey val id: String,
    val planId: String,
    val date: String,
    val meal: String,
    val memberId: String,
    /** [com.calore.tracker.domain.SlotKind] name. */
    val kind: String,
    val recipeId: String?,
    val boxId: String?,
    val targetKcal: Int?,
    val targetProteinG: Int?,
    val targetCarbsG: Int?,
    val targetFatG: Int?,
    @ColumnInfo(defaultValue = "0") val locked: Boolean = false,
    @ColumnInfo(defaultValue = "0") val done: Boolean = false,
    override val createdAt: Long,
    override val updatedAt: Long,
    override val deletedAt: Long? = null,
) : Stamped<PlanSlotEntity> {
    override fun withStamps(createdAt: Long, updatedAt: Long, deletedAt: Long?) = copy(createdAt = createdAt, updatedAt = updatedAt, deletedAt = deletedAt)
}

/** What to cook, when (M5.4). */
@Entity(tableName = "plan_cooks", indices = [Index(value = ["planId"])])
data class PlanCookEntity(
    @PrimaryKey val id: String,
    val planId: String,
    val date: String,
    val recipeId: String,
    val portions: Int,
    val targetCookedGrams: Double?,
    val scale: Double,
    /** Set after cooking. */
    val batchId: String?,
    override val createdAt: Long,
    override val updatedAt: Long,
    override val deletedAt: Long? = null,
) : Stamped<PlanCookEntity> {
    override fun withStamps(createdAt: Long, updatedAt: Long, deletedAt: Long?) = copy(createdAt = createdAt, updatedAt = updatedAt, deletedAt = deletedAt)
}

/** Food at home (M5.5). */
@Entity(tableName = "pantry_items", indices = [Index(value = ["foodUuid"]), Index(value = ["state"])])
data class PantryItemEntity(
    @PrimaryKey val id: String,
    val foodUuid: String?,
    val storeProductId: String?,
    val name: String,
    val amount: Double,
    /** `G`, `ML` or `PIECE`. */
    val unit: String,
    val storage: String,
    val boughtDate: String,
    /** The date printed on the pack wins over the defaults (A17). */
    val bestBeforeDate: String?,
    val openedDate: String?,
    /** `AVAILABLE`, `USED_UP` or `WASTED`. */
    val state: String,
    val costHalers: Long?,
    val reservedForCookId: String?,
    val ownerMemberId: String? = null,
    @ColumnInfo(defaultValue = "SHARED") val visibility: String = "SHARED",
    override val createdAt: Long,
    override val updatedAt: Long,
    override val deletedAt: Long? = null,
) : Stamped<PantryItemEntity> {
    override fun withStamps(createdAt: Long, updatedAt: Long, deletedAt: Long?) = copy(createdAt = createdAt, updatedAt = updatedAt, deletedAt = deletedAt)
}

/** A shop (M5.6). */
@Entity(tableName = "stores", indices = [Index(value = ["key"], unique = true)])
data class StoreEntity(
    @PrimaryKey val id: String,
    /** Seed key, e.g. `cz:lidl`. */
    val key: String?,
    val name: String,
    val sectionOrder: List<ShopSection>,
    @ColumnInfo(defaultValue = "0") val favorite: Boolean = false,
    /** Sections this shop is the default for, e.g. meat at Lidl. */
    val defaultForSections: List<ShopSection>,
    override val createdAt: Long,
    override val updatedAt: Long,
    override val deletedAt: Long? = null,
) : Stamped<StoreEntity> {
    override fun withStamps(createdAt: Long, updatedAt: Long, deletedAt: Long?) = copy(createdAt = createdAt, updatedAt = updatedAt, deletedAt = deletedAt)
}

/** An exact product at a shop (M5.6). */
@Entity(tableName = "store_products", indices = [Index(value = ["key"], unique = true), Index(value = ["storeId"]), Index(value = ["foodUuid"])])
data class StoreProductEntity(
    @PrimaryKey val id: String,
    val key: String?,
    val storeId: String,
    val foodUuid: String?,
    val name: String,
    val brand: String?,
    val barcode: String?,
    val packAmount: Double,
    val packUnit: String,
    @ColumnInfo(defaultValue = "0") val preferred: Boolean = false,
    /** `OWNER_RESEARCH`, `USER` or `OFF`. */
    val source: String,
    val sourceNote: String?,
    override val createdAt: Long,
    override val updatedAt: Long,
    override val deletedAt: Long? = null,
) : Stamped<StoreProductEntity> {
    override fun withStamps(createdAt: Long, updatedAt: Long, deletedAt: Long?) = copy(createdAt = createdAt, updatedAt = updatedAt, deletedAt = deletedAt)
}

/** A price paid or seeded for a product (M5.8). */
@Entity(tableName = "prices", indices = [Index(value = ["storeProductId"])])
data class PriceEntity(
    @PrimaryKey val id: String,
    val storeProductId: String,
    val priceHalers: Long,
    val currency: String,
    val date: String,
    /** `USER` or `SEED`. */
    val source: String,
    override val createdAt: Long,
    override val updatedAt: Long,
    override val deletedAt: Long? = null,
) : Stamped<PriceEntity> {
    override fun withStamps(createdAt: Long, updatedAt: Long, deletedAt: Long?) = copy(createdAt = createdAt, updatedAt = updatedAt, deletedAt = deletedAt)
}

/** A line on the shopping list (M5.7). */
@Entity(tableName = "shopping_items", indices = [Index(value = ["checkedAt"])])
data class ShoppingItemEntity(
    @PrimaryKey val id: String,
    val foodUuid: String?,
    val storeProductId: String?,
    val storeId: String?,
    val name: String,
    val amountNeeded: Double?,
    val unit: String?,
    val packs: Int?,
    val section: String,
    val fromPlanId: String?,
    @ColumnInfo(defaultValue = "0") val manual: Boolean = false,
    val checkedAt: Long?,
    val checkedByMemberId: String?,
    /** M11 "buy for me". */
    val requestedByMemberId: String?,
    val priceHalers: Long?,
    override val createdAt: Long,
    override val updatedAt: Long,
    override val deletedAt: Long? = null,
) : Stamped<ShoppingItemEntity> {
    override fun withStamps(createdAt: Long, updatedAt: Long, deletedAt: Long?) = copy(createdAt = createdAt, updatedAt = updatedAt, deletedAt = deletedAt)
}
