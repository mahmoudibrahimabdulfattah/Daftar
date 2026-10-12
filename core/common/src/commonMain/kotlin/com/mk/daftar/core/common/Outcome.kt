package com.mk.daftar.core.common

/** Typed success/failure result used across layer boundaries. */
sealed interface Outcome<out D, out E> {
    data class Success<out D>(val data: D) : Outcome<D, Nothing>
    data class Failure<out E>(val error: E) : Outcome<Nothing, E>
}

inline fun <D, E, R> Outcome<D, E>.map(transform: (D) -> R): Outcome<R, E> = when (this) {
    is Outcome.Success -> Outcome.Success(transform(data))
    is Outcome.Failure -> this
}

inline fun <D, E> Outcome<D, E>.onSuccess(action: (D) -> Unit): Outcome<D, E> {
    if (this is Outcome.Success) action(data)
    return this
}

inline fun <D, E> Outcome<D, E>.onFailure(action: (E) -> Unit): Outcome<D, E> {
    if (this is Outcome.Failure) action(error)
    return this
}

fun <D, E> Outcome<D, E>.asEmpty(): Outcome<Unit, E> = map { }
