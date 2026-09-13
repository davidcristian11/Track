package com.example.track

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class FoodPrecisionDatabaseTest {
    @Test fun referenceSurvivesDatabaseReopenRepeatedEditsAndRecentLogging() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "food-precision-${UUID.randomUUID()}.db"
        val day = "2026-09-13"
        val food = ManualFoodDraft(name = "Precise food", unit = FoodUnit.Milliliters,
            calories = "884.1234", protein = "12.3456", carbs = "23.4567", fat = "45.6789")
            .toFoodDefinition("manual")
        fun open() = Room.databaseBuilder(context, TrackDatabase::class.java, name).build()
        var database = open()
        try {
            TrackRepository(database).addFood(day, MealContext.LUNCH, food, 1, loggedAt = 123)
            val id = database.trackDao().observeFoodLogs(day).first().single().id
            repeat(4) {
                database.close()
                database = open()
                val repository = TrackRepository(database)
                val small = requireNotNull(repository.food(day, id))
                assertEquals(9, small.nutrition.calories)
                assertEquals(food.basisNutrition, small.basisNutrition)
                assertEquals(100.0, small.basisAmount, 0.0)
                repository.updateFood(day, small, 100, MealContext.DINNER)
                val large = requireNotNull(repository.food(day, id))
                assertEquals(NutritionTotals(884, 12.3f, 23.5f, 45.7f), large.nutrition)
                assertEquals(123L, database.trackDao().food(day, id)!!.createdAt)
                repository.updateFood(day, large, 1, MealContext.LUNCH)
            }
            val repository = TrackRepository(database)
            val recent = repository.observeRecentFoods().first().single()
            repository.addFood(day, MealContext.DINNER, recent, 1_000)
            database.close()
            database = open()
            val rows = database.trackDao().observeFoodLogs(day).first()
            assertEquals(2, rows.size)
            val reused = rows.single { it.id != id }.toLoggedFood()
            assertEquals(NutritionTotals(8_841, 123.5f, 234.6f, 456.8f), reused.nutrition)
            assertEquals(food.basisNutrition, reused.basisNutrition)
            assertEquals(FoodUnit.Milliliters, reused.unit)
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }
}
