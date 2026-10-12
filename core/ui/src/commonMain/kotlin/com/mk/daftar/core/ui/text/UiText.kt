package com.mk.daftar.core.ui.text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Text described by resources rather than resolved to a string, so wording can be built and unit-tested away from
 * composition, then resolved in the current language: inside a composable with [asString], or from background work
 * with [resolve]. Arguments may themselves be [UiText] and are resolved first.
 */
@Immutable
sealed interface UiText {
    data class Plain(val value: String) : UiText

    data class Resource(val res: StringResource, val args: List<Any> = emptyList()) : UiText

    data class Plural(val res: PluralStringResource, val quantity: Int, val args: List<Any> = emptyList()) : UiText

    data class Joined(val parts: List<UiText>, val separator: String) : UiText
}

fun text(res: StringResource, vararg args: Any): UiText = UiText.Resource(res, args.toList())

fun plural(res: PluralStringResource, quantity: Int, vararg args: Any): UiText =
    UiText.Plural(res, quantity, args.toList())

fun String.asUiText(): UiText = UiText.Plain(this)

fun List<UiText>.joined(separator: String): UiText = singleOrNull() ?: UiText.Joined(this, separator)

@Composable
fun UiText.asString(): String = when (this) {
    is UiText.Plain -> value
    is UiText.Resource -> stringResource(res, *args.map { it.asArg() }.toTypedArray())
    is UiText.Plural -> pluralStringResource(res, quantity, *args.map { it.asArg() }.toTypedArray())
    is UiText.Joined -> parts.map { it.asString() }.joinToString(separator)
}

suspend fun UiText.resolve(): String = when (this) {
    is UiText.Plain -> value
    is UiText.Resource -> getString(res, *args.map { it.resolveArg() }.toTypedArray())
    is UiText.Plural -> getPluralString(res, quantity, *args.map { it.resolveArg() }.toTypedArray())
    is UiText.Joined -> parts.map { it.resolve() }.joinToString(separator)
}

@Composable
private fun Any.asArg(): Any = if (this is UiText) asString() else this

private suspend fun Any.resolveArg(): Any = if (this is UiText) resolve() else this
