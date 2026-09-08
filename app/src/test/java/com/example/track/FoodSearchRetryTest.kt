package com.example.track

import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class FoodSearchRetryTest {
    private val empty = """{"products":[]}"""
    private val valid = """{"code":"1234567890128","product_name":"Valid","nutriments":{
        "energy-kcal_100g":200,"proteins_100g":10,"carbohydrates_100g":20,"fat_100g":5}}"""
    private class Clock {
        var now = 0L
        val waits = mutableListOf<Long>()
        suspend fun pause(ms: Long) { waits += ms; now += ms }
    }
    private fun repository(clock: Clock, get: suspend (okhttp3.HttpUrl) -> String) =
        FoodLookupRepository(OpenFoodFactsClient(get), { clock.now }, clock::pause)

    @Test fun firstSuccessAndEmptySuccessNeverRetry() = runBlocking {
        for (response in listOf(empty, """{"products":[$valid]}""")) {
            var calls = 0
            val clock = Clock()
            val results = repository(clock) { calls++; response }.search("food")
            assertEquals(if (response == empty) 0 else 1, results.size)
            assertEquals(1, calls)
            assertTrue(clock.waits.isEmpty())
        }
    }

    @Test fun serverFailureThenSuccess() = runBlocking {
        var calls = 0
        val clock = Clock()
        val result = repository(clock) { if (++calls == 1) throw FoodHttpException(500); empty }.search("food")
        assertTrue(result.isEmpty())
        assertEquals(2, calls)
        assertEquals(listOf(6_100L), clock.waits)
    }

    @Test fun ioThenServerFailureThenSuccess() = runBlocking {
        var calls = 0
        val clock = Clock()
        repository(clock) {
            when (++calls) { 1 -> throw IOException("offline"); 2 -> throw FoodHttpException(503); else -> empty }
        }.search("food")
        assertEquals(3, calls)
        assertEquals(listOf(6_100L, 6_100L), clock.waits)
    }

    @Test fun transientFailuresStopAtThreeAttempts() = runBlocking {
        for (failure in listOf(IOException(), SocketTimeoutException(), FoodHttpException(408), FoodHttpException(503), FoodHttpException(429))) {
            var calls = 0
            try { repository(Clock()) { calls++; throw failure }.search("food"); fail() }
            catch (error: IOException) { assertSame(failure, error) }
            assertEquals(3, calls)
        }
    }

    @Test fun permanentHttpAndInvalidResponseDoNotRetry() = runBlocking {
        for (failure in listOf(FoodHttpException(400), FoodHttpException(401), FoodHttpException(403), FoodHttpException(404), FoodHttpException(422), InvalidFoodResponseException("oversized"))) {
            var calls = 0
            try { repository(Clock()) { calls++; throw failure }.search("food"); fail() }
            catch (error: IOException) { assertSame(failure, error) }
            assertEquals(1, calls)
        }
        for (response in listOf("{}", "not json", """{"products":{}}""")) {
            var calls = 0
            try { repository(Clock()) { calls++; response }.search("food"); fail() }
            catch (_: Exception) { assertEquals(1, calls) }
        }
    }

    @Test fun rateLimitHonorsRetryAfterAndIsBounded() = runBlocking {
        var calls = 0
        val clock = Clock()
        try { repository(clock) { calls++; throw FoodHttpException(429, 12_000) }.search("food"); fail() }
        catch (_: FoodHttpException) { }
        assertEquals(3, calls)
        assertEquals(listOf(12_000L, 12_000L), clock.waits)
    }

    @Test fun longCooldownEndsAutomaticRetriesAndSurvivesNewQuery() = runBlocking {
        var calls = 0
        val clock = Clock()
        val repo = repository(clock) { if (++calls == 1) throw FoodHttpException(429, 120_000); empty }
        try { repo.search("old"); fail() } catch (_: FoodHttpException) { }
        assertEquals(1, calls)
        repo.search("new")
        assertEquals(listOf(120_000L), clock.waits)
        assertEquals(2, calls)
    }

    @Test fun retryAfterAcceptsSecondsAndHttpDate() {
        assertEquals(12_000L, retryAfterMillis("12"))
        assertEquals(12_000L, retryAfterMillis("Tue, 08 Sep 2026 12:00:12 GMT", java.time.Instant.parse("2026-09-08T12:00:00Z").toEpochMilli()))
        assertEquals(0L, retryAfterMillis("Tue, 08 Sep 2026 12:00:12 GMT", java.time.Instant.parse("2026-09-08T12:01:00Z").toEpochMilli()))
        assertNull(retryAfterMillis("invalid"))
        assertNull(retryAfterMillis("-1"))
    }

    @Test fun cancellationDuringBackoffStopsRequest() = runBlocking {
        var calls = 0
        val repo = FoodLookupRepository(OpenFoodFactsClient { calls++; throw IOException() }, { 0L }, { throw CancellationException() })
        try { repo.search("old"); fail() } catch (_: CancellationException) { }
        assertEquals(1, calls)
    }

    @Test fun malformedItemsKeepValidProductsAndIncompleteNutritionStaysUnloggable() {
        val results = OpenFoodFactsMapper.search("""{"products":[null,42,"bad",{},
            {"code":"bad"},$valid,{"code":"87654321","nutriments":{"fat_100g":"NaN"}}]}""")
        assertEquals(2, results.size)
        assertTrue(results.first().isLoggable)
        assertFalse(results.last().isLoggable)
    }
}
