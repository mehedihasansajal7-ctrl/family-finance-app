package com.familyfinance.app.core.logging

import android.util.Log

/**
 * Logging facade.
 *
 * Every message is passed through [Redactor.scrub] and nothing is emitted at all in release builds.
 * This is the only logging entry point in the app, which makes "no sensitive data in logs" a
 * property of the code rather than a habit.
 */
object AppLog {

    private var enabled: Boolean = false
    private const val TAG = "FamilyFinance"

    fun install(isDebug: Boolean) {
        enabled = isDebug
    }

    fun d(message: String, vararg args: Any?) = emit(Log.DEBUG, message, args)
    fun i(message: String, vararg args: Any?) = emit(Log.INFO, message, args)
    fun w(message: String, vararg args: Any?) = emit(Log.WARN, message, args)

    /** Errors are logged with the throwable type only — never its message, which may embed values. */
    fun e(message: String, throwable: Throwable? = null) {
        if (!enabled) return
        val safe = com.familyfinance.app.core.security.Redactor.scrub(message)
        if (throwable == null) {
            Log.e(TAG, safe)
        } else {
            Log.e(TAG, "$safe [${throwable.javaClass.simpleName}]")
        }
    }

    private fun emit(priority: Int, message: String, args: Array<out Any?>) {
        if (!enabled) return
        val formatted = if (args.isEmpty()) message else runCatching { message.format(*args) }.getOrElse { message }
        Log.println(priority, TAG, com.familyfinance.app.core.security.Redactor.scrub(formatted))
    }
}
