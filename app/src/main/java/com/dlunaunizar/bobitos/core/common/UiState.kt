package com.dlunaunizar.bobitos.core.common

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>

    data class Content<T>(val value: T) : UiState<T>

    data class Error(val failure: FailureKind = FailureKind.UNKNOWN) : UiState<Nothing>
}
