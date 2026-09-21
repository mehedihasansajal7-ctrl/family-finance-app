package com.familyfinance.app.ui.common

/**
 * The three states a data-backed screen can be in, plus the loaded case.
 *
 * Kept deliberately tiny: a screen renders exactly one of these, which is what makes "every screen
 * has a loading, empty, error and success state" a structural property rather than a checklist item.
 */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Failure(val message: String) : UiState<Nothing>
    data class Ready<T>(val data: T) : UiState<T>

    val isLoading: Boolean get() = this is Loading
    fun dataOrNull(): T? = (this as? Ready)?.data
}
