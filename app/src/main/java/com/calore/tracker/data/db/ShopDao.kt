package com.calore.tracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Shops, their exact products and prices (M5.6, M5.8). */
@Dao
abstract class ShopDao {

    @Query("SELECT * FROM stores WHERE ${SoftDelete.NOT_DELETED} ORDER BY favorite DESC, name")
    abstract fun observeStores(): Flow<List<StoreEntity>>

    @Query("SELECT * FROM stores WHERE ${SoftDelete.NOT_DELETED}")
    abstract suspend fun stores(): List<StoreEntity>

    @Query("SELECT * FROM stores WHERE `key` = :key")
    abstract suspend fun storeByKey(key: String): StoreEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertStore(store: StoreEntity)

    @Update
    abstract suspend fun updateStores(stores: List<StoreEntity>)

    @Query("SELECT * FROM store_products WHERE ${SoftDelete.NOT_DELETED}")
    abstract fun observeProducts(): Flow<List<StoreProductEntity>>

    @Query("SELECT * FROM store_products WHERE ${SoftDelete.NOT_DELETED}")
    abstract suspend fun products(): List<StoreProductEntity>

    @Query("SELECT * FROM store_products WHERE `key` = :key")
    abstract suspend fun productByKey(key: String): StoreProductEntity?

    @Query("SELECT * FROM store_products WHERE id = :id")
    abstract suspend fun product(id: String): StoreProductEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertProduct(product: StoreProductEntity)

    @Query("UPDATE store_products SET preferred = 0, updatedAt = :now WHERE storeId = :storeId AND foodUuid = :foodUuid AND id != :keep")
    protected abstract suspend fun clearPreferred(storeId: String, foodUuid: String, keep: String, now: Long)

    /** Saves [product] as the preferred one for its food at its shop. */
    @Transaction
    open suspend fun savePreferred(product: StoreProductEntity, now: Long) {
        upsertProduct(product.copy(preferred = true))
        product.foodUuid?.let { clearPreferred(product.storeId, it, product.id, now) }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertPrice(price: PriceEntity)

    @Query("SELECT * FROM prices WHERE ${SoftDelete.NOT_DELETED} ORDER BY date DESC, createdAt DESC")
    abstract fun observePrices(): Flow<List<PriceEntity>>

    @Query("SELECT * FROM prices WHERE ${SoftDelete.NOT_DELETED} ORDER BY date DESC, createdAt DESC")
    abstract suspend fun prices(): List<PriceEntity>
}
