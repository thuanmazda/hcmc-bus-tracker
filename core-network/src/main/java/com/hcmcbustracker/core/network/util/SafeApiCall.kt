package com.hcmcbustracker.core.network.util

import com.hcmcbustracker.core.model.NetworkResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import retrofit2.HttpException
import java.io.IOException

/**
 * Utility executing Retrofit suspend calls with explicit timeout, HTTP 429 Retry-After parsing,
 * and strict preservation of coroutine cancellation.
 */
object SafeApiCall {

    const val DEFAULT_TIMEOUT_MS = 10_000L

    suspend fun <T> execute(
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        block: suspend () -> T
    ): NetworkResult<T> {
        return try {
            val result = withTimeoutOrNull(timeoutMs) {
                block()
            } ?: return NetworkResult.Error(
                message = "Hết thời gian kết nối máy chủ (${timeoutMs / 1000}s)",
                code = 408
            )
            NetworkResult.Success(result)
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            val code = e.code()
            val retryAfterHeader = e.response()?.headers()?.get("Retry-After")?.trim()
            val retryAfterSeconds = retryAfterHeader?.toLongOrNull()?.coerceIn(5L, 300L)
            val msg = if (code == 429) {
                "Máy chủ đang giới hạn tần suất (HTTP 429)${retryAfterSeconds?.let { ", thử lại sau ${it}s" } ?: ""}"
            } else {
                "Lỗi máy chủ HTTP $code: ${e.message()}"
            }
            NetworkResult.Error(
                message = msg,
                code = code,
                throwable = e,
                retryAfterSeconds = if (code == 429) (retryAfterSeconds ?: 30L) else null
            )
        } catch (e: IOException) {
            NetworkResult.Error(
                message = e.localizedMessage ?: "Lỗi kết nối mạng",
                throwable = e
            )
        } catch (e: Exception) {
            NetworkResult.Error(
                message = e.localizedMessage ?: "Lỗi xử lý dữ liệu",
                throwable = e
            )
        }
    }
}
