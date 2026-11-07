package com.calore.tracker.data.shop

import com.calore.tracker.data.repository.StoresFile
import com.calore.tracker.domain.ShopSection
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** M5.6: the bundled shop list is complete (names only, DATA_SOURCES section 6). */
class StoresSeedTest {

    private val file = Json { ignoreUnknownKeys = true }
        .decodeFromString(StoresFile.serializer(), File("src/main/assets/seed/cz/stores.json").readText())

    @Test
    fun theCommonCzechShopsAndMarketOrOther() {
        assertEquals("calore-stores", file.format)
        assertEquals(
            listOf("Albert", "Billa", "Globus", "Kaufland", "Lidl", "Penny", "Tesco", "Rohlik.cz", "Košík.cz", "Market / other"),
            file.stores.map { it.name },
        )
        assertEquals(file.stores.size, file.stores.map { it.key }.toSet().size)
        assertTrue(file.stores.all { it.key.startsWith("cz:") })
    }

    @Test
    fun everySectionIsInTheOrderOnce() {
        for (store in file.stores) {
            assertEquals(store.key, ShopSection.entries.map { it.name }.toSet(), store.sectionOrder.toSet())
            assertEquals(store.key, store.sectionOrder.size, store.sectionOrder.toSet().size)
        }
    }
}
