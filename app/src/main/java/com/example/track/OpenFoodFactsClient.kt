package com.example.track

import java.io.IOException
import java.util.concurrent.TimeUnit
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** Two read-only GETs. Inject a transport for fixture tests; no catalog cache. */
class OpenFoodFactsClient(
    private val get: suspend (HttpUrl) -> String = ::getOpenFoodFacts,
) {
    suspend fun search(query: String): String = get(
        "$BaseUrl/cgi/search.pl".toHttpUrl().newBuilder()
            .addQueryParameter("search_terms", query)
            .addQueryParameter("search_simple", "1")
            .addQueryParameter("action", "process")
            .addQueryParameter("json", "1")
            .addQueryParameter("page_size", "15")
            .addQueryParameter("fields", Fields).build(),
    )

    suspend fun lookupBarcode(code: String): String {
        require(isRetailBarcode(code))
        return get("$BaseUrl/api/v2/product/$code.json".toHttpUrl().newBuilder()
            .addQueryParameter("fields", Fields).build())
    }

    companion object {
        const val BaseUrl = "https://world.openfoodfacts.org"
        const val UserAgent = "TrackAndroid/1.0 (https://github.com/davidcristian11/Track)"
        const val Fields = "code,product_name,product_name_en,brands,quantity,serving_size," +
            "serving_quantity,serving_quantity_unit,nutrition_data_per,nutriments"
    }
}

internal fun isRetailBarcode(code: String) = code.length in listOf(8, 12, 13) && code.all { it in '0'..'9' }

private val foodHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .followSslRedirects(false) // Never follow an HTTPS redirect to cleartext.
        .retryOnConnectionFailure(false)
        .build()
}

private suspend fun getOpenFoodFacts(url: HttpUrl): String = suspendCancellableCoroutine { continuation ->
    val call = foodHttpClient.newCall(Request.Builder().url(url)
        .header("User-Agent", OpenFoodFactsClient.UserAgent).header("Accept", "application/json").build())
    continuation.invokeOnCancellation { call.cancel() }
    call.enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (continuation.isActive) continuation.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            try {
                val body = response.use {
                    // OFF may return HTTP 404 for a barcode that does not exist.
                    if (it.code == 404 && url.encodedPath.startsWith("/api/v2/product/")) {
                        """{"status":0}"""
                    } else {
                        if (!it.isSuccessful) throw FoodHttpException(it.code, retryAfterMillis(it.header("Retry-After")))
                        val source = it.body?.source() ?: throw InvalidFoodResponseException("Empty food response")
                        if (source.request(2_000_001)) throw InvalidFoodResponseException("Food response too large")
                        source.readUtf8()
                    }
                }
                if (continuation.isActive) continuation.resume(body)
            } catch (error: Exception) {
                if (continuation.isActive) continuation.resumeWithException(error)
            }
        }
    })
}

internal class FoodHttpException(val status: Int, val retryAfterMillis: Long? = null) :
    IOException("Food service HTTP $status")

internal class InvalidFoodResponseException(message: String) : IOException(message)

internal fun retryAfterMillis(header: String?, nowMillis: Long = System.currentTimeMillis()): Long? {
    val value = header?.trim() ?: return null
    value.toLongOrNull()?.let {
        return if (it >= 0) it.coerceAtMost(Long.MAX_VALUE / 4_000) * 1_000 else null
    }
    return try {
        (ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() - nowMillis)
            .coerceAtLeast(0)
    } catch (_: java.time.DateTimeException) { null }
}
