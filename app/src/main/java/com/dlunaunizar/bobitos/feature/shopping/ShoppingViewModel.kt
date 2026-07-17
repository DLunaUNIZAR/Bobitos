package com.dlunaunizar.bobitos.feature.shopping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.data.repository.ShoppingFailure
import com.dlunaunizar.bobitos.data.repository.ShoppingRepository
import com.dlunaunizar.bobitos.data.repository.ShoppingRepositoryException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ShoppingViewModel @Inject constructor(private val repository: ShoppingRepository) : ViewModel() {
    private val mutableUiState = MutableStateFlow(ShoppingUiState())
    val uiState: StateFlow<ShoppingUiState> = mutableUiState.asStateFlow()
    private var itemsJob: Job? = null
    private var observedSpaceId: String? = null

    fun observe(spaceId: String) {
        if (spaceId == observedSpaceId && itemsJob?.isActive == true) return
        itemsJob?.cancel()
        observedSpaceId = spaceId
        mutableUiState.update { it.copy(items = UiState.Loading) }
        itemsJob = viewModelScope.launch {
            repository.items(spaceId)
                .catch { error ->
                    mutableUiState.update { state ->
                        state.copy(items = UiState.Error(error.message))
                    }
                }
                .collect { items ->
                    mutableUiState.update { state ->
                        state.copy(items = UiState.Content(items))
                    }
                }
        }
    }
    fun stopObserving() {
        itemsJob?.cancel()
        itemsJob = null
        observedSpaceId = null
    }

    fun addItem(spaceId: String, name: String, quantity: String?, notes: String?) {
        if (!validate(name, quantity, notes)) return
        runAction(ShoppingUiMessage.ItemAdded) {
            repository.addItem(spaceId, name.trim(), quantity.normalized(), notes.normalized())
        }
    }

    fun updateItem(spaceId: String, itemId: String, name: String, quantity: String?, notes: String?) {
        if (!validate(name, quantity, notes)) return
        runAction(ShoppingUiMessage.ItemUpdated) {
            repository.updateItem(
                spaceId,
                itemId,
                name.trim(),
                quantity.normalized(),
                notes.normalized(),
            )
        }
    }

    fun setPurchased(spaceId: String, itemId: String, purchased: Boolean) {
        runAction(
            if (purchased) ShoppingUiMessage.ItemMarked else ShoppingUiMessage.ItemUnmarked,
        ) {
            repository.setPurchased(spaceId, itemId, purchased)
        }
    }

    fun deleteItem(spaceId: String, itemId: String) {
        runAction(ShoppingUiMessage.ItemDeleted) {
            repository.deleteItem(spaceId, itemId)
        }
    }

    fun clearPurchased(spaceId: String) {
        if (mutableUiState.value.isSaving) return
        mutableUiState.update {
            it.copy(
                isSaving = true,
                writeStatus = ShoppingWriteStatus.SAVING,
                error = null,
                notice = null,
            )
        }
        viewModelScope.launch {
            try {
                val count = repository.clearPurchased(spaceId)
                mutableUiState.update {
                    it.copy(
                        isSaving = false,
                        writeStatus = ShoppingWriteStatus.SAVED,
                        notice = ShoppingUiMessage.PurchasedCleared,
                        lastClearedCount = count,
                    )
                }
            } catch (error: Throwable) {
                showError(error.toUiMessage())
            }
        }
    }

    fun clearFeedback() {
        mutableUiState.update {
            it.copy(
                writeStatus = if (it.isSaving) {
                    ShoppingWriteStatus.SAVING
                } else {
                    ShoppingWriteStatus.IDLE
                },
                error = null,
                notice = null,
            )
        }
    }

    private fun validate(name: String, quantity: String?, notes: String?): Boolean {
        val error = ShoppingValidation.validate(name, quantity, notes) ?: return true
        showError(error)
        return false
    }

    private fun showError(message: ShoppingUiMessage) {
        mutableUiState.update {
            it.copy(
                isSaving = false,
                writeStatus = ShoppingWriteStatus.ERROR,
                error = message,
                notice = null,
            )
        }
    }

    private fun runAction(successNotice: ShoppingUiMessage, action: suspend () -> Unit) {
        if (mutableUiState.value.isSaving) return
        mutableUiState.update {
            it.copy(
                isSaving = true,
                writeStatus = ShoppingWriteStatus.SAVING,
                error = null,
                notice = null,
            )
        }
        viewModelScope.launch {
            try {
                action()
                mutableUiState.update {
                    it.copy(
                        isSaving = false,
                        writeStatus = ShoppingWriteStatus.SAVED,
                        notice = successNotice,
                    )
                }
            } catch (error: Throwable) {
                showError(error.toUiMessage())
            }
        }
    }
}

private fun String?.normalized(): String? = this?.trim()?.takeIf(String::isNotEmpty)

private fun Throwable.toUiMessage(): ShoppingUiMessage = when ((this as? ShoppingRepositoryException)?.failure) {
    ShoppingFailure.NameRequired -> ShoppingUiMessage.NameRequired
    ShoppingFailure.NameTooLong -> ShoppingUiMessage.NameTooLong
    ShoppingFailure.QuantityTooLong -> ShoppingUiMessage.QuantityTooLong
    ShoppingFailure.NotesTooLong -> ShoppingUiMessage.NotesTooLong
    ShoppingFailure.NotAuthenticated -> ShoppingUiMessage.NotAuthenticated
    ShoppingFailure.EmailNotVerified -> ShoppingUiMessage.EmailNotVerified
    ShoppingFailure.SpaceNotFound -> ShoppingUiMessage.SpaceNotFound
    ShoppingFailure.ItemNotFound -> ShoppingUiMessage.ItemNotFound
    ShoppingFailure.PermissionDenied -> ShoppingUiMessage.PermissionDenied
    ShoppingFailure.Network -> ShoppingUiMessage.NetworkError
    ShoppingFailure.Unknown,
    null,
    -> ShoppingUiMessage.UnexpectedError
}
