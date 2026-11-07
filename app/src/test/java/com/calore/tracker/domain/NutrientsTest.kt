package com.calore.tracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NutrientsTest {

    @Test
    fun plusAddsKnownValues() {
        val a = Nutrients(kcal = 100.0, protein = 10.0, carbs = 5.0, fat = 2.0, fiber = 1.0, sugar = 3.0)
        val b = Nutrients(kcal = 50.0, protein = 1.0, carbs = 2.0, fat = 3.0, fiber = 4.0, sugar = 5.0)

        val sum = a + b

        assertEquals(Nutrients(150.0, 11.0, 7.0, 5.0, 5.0, 8.0), sum)
    }

    @Test
    fun plusKeepsKnownValueWhenOtherSideIsMissing() {
        val known = Nutrients(kcal = 100.0, protein = 10.0)
        val partial = Nutrients(kcal = 20.0, protein = null, fiber = 2.0)

        val sum = known + partial

        assertEquals(120.0, sum.kcal, 0.0)
        assertEquals(10.0, sum.protein!!, 0.0)
        assertEquals(2.0, sum.fiber!!, 0.0)
        assertNull(sum.carbs)
    }

    @Test
    fun zeroIsIdentityForKnownValues() {
        val food = Nutrients(kcal = 42.0, protein = 1.0, carbs = 2.0, fat = 3.0, fiber = 0.5, sugar = 0.0)

        assertEquals(food, Nutrients.ZERO + food)
    }

    @Test
    fun zeroPlusMissingValuesStaysZero() {
        val sum = Nutrients.ZERO + Nutrients(kcal = 10.0)

        assertEquals(0.0, sum.protein!!, 0.0)
        assertEquals(10.0, sum.kcal, 0.0)
    }

    @Test
    fun scalesPer100gToPortion() {
        val oatsPer100g = Nutrients(kcal = 370.0, protein = 12.0, carbs = 56.0, fat = 8.0, fiber = 10.0, sugar = null)

        val portion = oatsPer100g.forGramsOfPer100g(40.0)

        assertEquals(148.0, portion.kcal, 1e-9)
        assertEquals(4.8, portion.protein!!, 1e-9)
        assertEquals(22.4, portion.carbs!!, 1e-9)
        assertEquals(3.2, portion.fat!!, 1e-9)
        assertEquals(4.0, portion.fiber!!, 1e-9)
        assertNull(portion.sugar)
    }

    @Test
    fun zeroGramsIsZeroEnergy() {
        val portion = Nutrients(kcal = 500.0, protein = 20.0).forGramsOfPer100g(0.0)

        assertEquals(0.0, portion.kcal, 0.0)
        assertEquals(0.0, portion.protein!!, 0.0)
    }

    @Test
    fun micronutrientsAddAndScaleLikeTheOthers() {
        val spinach = Nutrients(kcal = 23.0, ironMg = 2.7, magnesiumMg = 79.0, vitaminCMg = 28.1)
        val rice = Nutrients(kcal = 130.0)

        val portion = spinach.forGramsOfPer100g(200.0)
        assertEquals(5.4, portion.ironMg!!, 1e-9)
        assertEquals(158.0, portion.magnesiumMg!!, 1e-9)

        val meal = portion + rice
        assertEquals(56.2, meal.vitaminCMg!!, 1e-9)
        assertNull("unknown stays unknown", rice.ironMg)
        assertNull((rice + rice).magnesiumMg)
    }

    @Test
    fun dayTotalsLeaveMicronutrientsUnknownUntilAFoodHasThem() {
        assertNull(Nutrients.ZERO.ironMg)
        assertEquals(2.7, (Nutrients.ZERO + Nutrients(kcal = 23.0, ironMg = 2.7)).ironMg!!, 1e-9)
    }
}
