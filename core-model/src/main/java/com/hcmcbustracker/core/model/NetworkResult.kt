package com.hcmcbustracker.core.model

/**
 * Standard sealed hierarchy for handling operations and API states.
 */
sealed class NetworkResult<out T> {
    data class Success<out T>(val data: T) : NetworkResult<T>()
    data class Error(
        val message: String,
        val code: Int? = null,
        val throwable: Throwable? = null,
        val retryAfterSeconds: Long? = null
    ) : NetworkResult<Nothing>()
    data object Loading : NetworkResult<Nothing>()

    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error
    val isLoading: Boolean get() = this is Loading

    fun getOrNull(): T? = (this as? Success)?.data

    fun getOrDefault(defaultValue: @UnsafeVariance T): T = (this as? Success)?.data ?: defaultValue

    inline fun <R> map(transform: (T) -> R): NetworkResult<R> {
        return when (this) {
            is Success -> Success(transform(data))
            is Error -> Error(message, code, throwable, retryAfterSeconds)
            is Loading -> Loading
        }
    }
}
