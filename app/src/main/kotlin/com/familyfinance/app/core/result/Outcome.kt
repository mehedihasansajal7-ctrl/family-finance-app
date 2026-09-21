package com.familyfinance.app.core.result

/**
 * Explicit result type for operations that are expected to fail for ordinary reasons
 * (validation, network, permission). Exceptions are reserved for programming errors.
 */
sealed interface Outcome<out T> {
    data class Success<T>(val value: T) : Outcome<T>
    data class Failure(val error: AppError) : Outcome<Nothing>

    val isSuccess: Boolean get() = this is Success
    val isFailure: Boolean get() = this is Failure

    fun getOrNull(): T? = (this as? Success)?.value
    fun errorOrNull(): AppError? = (this as? Failure)?.error
}

inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> = when (this) {
    is Outcome.Success -> Outcome.Success(transform(value))
    is Outcome.Failure -> this
}

inline fun <T> Outcome<T>.onSuccess(action: (T) -> Unit): Outcome<T> {
    if (this is Outcome.Success) action(value)
    return this
}

inline fun <T> Outcome<T>.onFailure(action: (AppError) -> Unit): Outcome<T> {
    if (this is Outcome.Failure) action(error)
    return this
}

/** A user-presentable error. [message] is safe to show; it never contains sensitive values. */
data class AppError(
    val code: ErrorCode,
    val message: String,
    val cause: Throwable? = null,
) {
    companion object {
        fun validation(message: String) = AppError(ErrorCode.VALIDATION, message)
        fun permission(message: String = "You do not have access to this data.") =
            AppError(ErrorCode.PERMISSION_DENIED, message)
        fun notFound(message: String = "Not found.") = AppError(ErrorCode.NOT_FOUND, message)
        fun conflict(message: String = "That action conflicts with the current state.") =
            AppError(ErrorCode.CONFLICT, message)
        fun network(message: String = "Network unavailable. Your change is saved and will sync.") =
            AppError(ErrorCode.NETWORK, message)
        fun unknown(message: String = "Something went wrong. Please try again.", cause: Throwable? = null) =
            AppError(ErrorCode.UNKNOWN, message, cause)
    }
}

enum class ErrorCode {
    VALIDATION,
    PERMISSION_DENIED,
    NOT_FOUND,
    CONFLICT,
    NETWORK,
    UNKNOWN,
}
