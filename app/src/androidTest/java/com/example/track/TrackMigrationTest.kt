package com.example.track

import androidx.room.Room
import android.database.sqlite.SQLiteDatabase
import org.json.JSONObject
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class TrackMigrationTest {
    @Test fun migrationFromExportedV1PreservesEveryExistingTableAndMakesWeightUsable() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "weight-migration-${UUID.randomUUID()}.db"
        val day = "2026-09-07"
        try {
            val v1 = exportedSchema(1)
            val path = context.getDatabasePath(name)
            path.parentFile!!.mkdirs()
            // Native fixture creation avoids the Room testing serializer's runtime ABI mismatch.
            // Every table/index and the Room identity come from the committed v1 export.
            SQLiteDatabase.openOrCreateDatabase(path, null).apply {
                val entities = v1.getJSONArray("entities")
                for (index in 0 until entities.length()) {
                    val entity = entities.getJSONObject(index)
                    val table = entity.getString("tableName")
                    execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                    val indices = entity.optJSONArray("indices")
                    if (indices != null) for (i in 0 until indices.length()) {
                        execSQL(indices.getJSONObject(i).getString("createSql").replace("\${TABLE_NAME}", table))
                    }
                }
                val setup = v1.getJSONArray("setupQueries")
                for (i in 0 until setup.length()) execSQL(setup.getString(i))
                version = 1
                execSQL("""INSERT INTO food_logs VALUES
                    (41, '$day', 'LUNCH', NULL, 'Migration yogurt', 'Saved brand', 150, 'g', 123, 12.5, 14.5, 3.5, 123456)""")
                execSQL("""INSERT INTO workouts VALUES
                    (17, '$day', 'Walking', 37, 'Migration walk', 280, '18:10', 123457)""")
                execSQL("INSERT INTO daily_tracking_state VALUES ('$day', 1250, 1)")
                execSQL("INSERT INTO daily_tracking_state VALUES ('2026-09-06', 500, 0)")
                close()
            }
            val database = Room.databaseBuilder(context, TrackDatabase::class.java, name)
                .addMigrations(MIGRATION_1_2).build()
            try {
                // Opening the v1 file invokes MIGRATION_1_2 AND Room's generated schema validator.
                val migrated = database.openHelper.writableDatabase
                assertEquals(2, migrated.version)
                assertExportedSchema(migrated, exportedSchema(2))
                val dao = database.trackDao()
                assertEquals(LoggedFoodEntity(41, day, "LUNCH", null, "Migration yogurt", "Saved brand",
                    150, "g", 123, 12.5f, 14.5f, 3.5f, 123456), dao.observeFoodLogs(day).first().single())
                assertEquals(WorkoutEntity(17, day, "Walking", 37, "Migration walk", 280, "18:10", 123457),
                    dao.observeWorkouts(day).first().single())
                assertEquals(DailyTrackingStateEntity(day, 1250, true), dao.dailyState(day))
                assertEquals(DailyTrackingStateEntity("2026-09-06", 500, false), dao.dailyState("2026-09-06"))
                assertTrue(dao.observeWeightEntries().first().isEmpty())
                val weight = WeightEntryEntity(day, 73.85, 123458)
                dao.upsertWeight(weight)
                assertEquals(weight, dao.weightForDay(day))
                dao.upsertWeight(weight.copy(weightKg = 74.8, updatedAt = 123459))
                assertEquals(74.8, dao.observeWeightEntries().first().single().weightKg, 0.0)
                assertEquals(1250, dao.dailyState(day)?.waterMl)
                // Existing autoincrement sequences are also preserved.
                val nextId = dao.insertFood(dao.observeFoodLogs(day).first().single().copy(id = 0))
                assertTrue(nextId > 41)
            } finally { database.close() }
        } finally { context.deleteDatabase(name) } // Only this test's unique fixture.
    }
    private fun exportedSchema(version: Int): JSONObject {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        return assets.open("com.example.track.TrackDatabase/$version.json").bufferedReader().use {
            JSONObject(it.readText()).getJSONObject("database")
        }
    }

    private fun assertExportedSchema(db: SupportSQLiteDatabase, schema: JSONObject) {
        val entities = schema.getJSONArray("entities")
        val expectedTables = mutableSetOf<String>()
        for (index in 0 until entities.length()) {
            val entity = entities.getJSONObject(index)
            val table = entity.getString("tableName")
            expectedTables += table
            val fields = entity.getJSONArray("fields")
            val expectedColumns = (0 until fields.length()).map { fields.getJSONObject(it) }
                .associate { it.getString("columnName") to (it.getString("affinity") to it.optBoolean("notNull")) }
            val actualColumns = mutableMapOf<String, Pair<String, Boolean>>()
            db.query("PRAGMA table_info(`$table`)").use { cursor ->
                while (cursor.moveToNext()) actualColumns[cursor.getString(1)] = cursor.getString(2) to (cursor.getInt(3) == 1)
            }
            assertEquals(expectedColumns, actualColumns)
        }
        val actualTables = mutableSetOf<String>()
        db.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' AND name NOT IN ('room_master_table', 'android_metadata')").use {
            while (it.moveToNext()) actualTables += it.getString(0)
        }
        assertEquals(expectedTables, actualTables)
    }

}
