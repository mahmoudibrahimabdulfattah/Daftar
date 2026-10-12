package com.mk.daftar.core.ui.mvi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Marker for an immutable screen state. */
interface UiState

/** Marker for a user or system action sent to a ViewModel. */
interface UiIntent

/** Marker for a one-shot side effect such as navigation or a message. */
interface UiEffect

/**
 * Unidirectional data flow: the UI sends an intent, the ViewModel reduces it into [state], and it emits one-shot
 * [effects] that are consumed once. Business rules stay in the domain layer; a ViewModel only sequences them.
 */
abstract class MviViewModel<S : UiState, I : UiIntent, E : UiEffect>(initialState: S) : ViewModel() {
    private val mutableState = MutableStateFlow(initialState)
    val state: StateFlow<S> = mutableState.asStateFlow()

    private val effectChannel = Channel<E>(Channel.BUFFERED)
    val effects: Flow<E> = effectChannel.receiveAsFlow()

    protected val currentState: S get() = mutableState.value

    /** The single entry point for everything the UI wants to do. */
    abstract fun onIntent(intent: I)

    protected fun reduce(reducer: S.() -> S) = mutableState.update(reducer)

    protected fun emitEffect(effect: E) {
        viewModelScope.launch { effectChannel.send(effect) }
    }
}
