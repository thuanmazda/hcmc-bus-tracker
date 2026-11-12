package com.hcmcbustracker.core.network

import com.hcmcbustracker.core.model.NetworkResult
import com.hcmcbustracker.core.network.util.SafeApiCall
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class SafeApiCallTest {

    @Test
    fun execute_returnsSuccess_onNormalCompletion() = runBlocking {
        val result = SafeApiCall.execute { "ok" }
        assertTrue(result is NetworkResult.Success)
        assertEquals("ok", result.getOrNull())
    }

    @Test
    fun execute_returnsHttp408Error_whenTimedOut() = runBlocking {
        val result = SafeApiCall.execute(timeoutMs = 50L) {
            delay(400L)
            "late"
        }
        assertTrue(result is NetworkResult.Error)
        val err = result as NetworkResult.Error
        assertEquals(408, err.code)
        assertTrue(err.message.contains("Hết thời gian"))
    }

    @Test
    fun execute_returnsHttp429WithParsedRetryAfterHeader() = runBlocking {
        val rawResponse = okhttp3.Response.Builder()
            .code(429)
            .message("Too Many Requests")
            .protocol(Protocol.HTTP_1_1)
            .request(Request.Builder().url("https://apicms.ebms.vn/businfo/getallroute").build())
            .header("Retry-After", "45")
            .build()
        val body = "rate limited".toResponseBody("text/plain".toMediaType())
        val httpException = HttpException(Response.error<String>(body, rawResponse))

        val result = SafeApiCall.execute<String> {
            throw httpException
        }
        assertTrue(result is NetworkResult.Error)
        val err = result as NetworkResult.Error
        assertEquals(429, err.code)
        assertEquals(45L, err.retryAfterSeconds)
        assertTrue(err.message.contains("429"))
        assertTrue(err.message.contains("45s"))
    }

    @Test
    fun execute_returnsHttp429WithDefaultRetryAfterWhenHeaderMissing() = runBlocking {
        val body = "rate limited".toResponseBody("text/plain".toMediaType())
        val httpException = HttpException(Response.error<String>(429, body))

        val result = SafeApiCall.execute<String> {
            throw httpException
        }
        assertTrue(result is NetworkResult.Error)
        val err = result as NetworkResult.Error
        assertEquals(429, err.code)
        assertEquals(30L, err.retryAfterSeconds)
    }

    @Test
    fun execute_handlesIOExceptionCleanly() = runBlocking {
        val result = SafeApiCall.execute<String> {
            throw IOException("Network unreachable")
        }
        assertTrue(result is NetworkResult.Error)
        val err = result as NetworkResult.Error
        assertEquals("Network unreachable", err.message)
    }

    @Test
    fun execute_rethrowsCancellationException_withoutSwallowing() = runBlocking {
        var rethrown = false
        try {
            SafeApiCall.execute<String> {
                throw CancellationException("Parent job cancelled")
            }
        } catch (_: CancellationException) {
            rethrown = true
        }
        assertTrue("CancellationException must propagate to respect structured concurrency", rethrown)
    }
}
