package com.mk.daftar.update

import com.mk.daftar.core.domain.update.UpdateStatus

/**
 * What the update gate shows. [Force] replaces the vault and is never composed under it. [Soft] is a dialog over an
 * open, uncovered, resumed vault. Everything else is [Content], the vault gate as it was.
 */
internal sealed interface UpdatePresentation {
    data object Content : UpdatePresentation

    data class Force(val status: UpdateStatus.Force) : UpdatePresentation

    data class Soft(val status: UpdateStatus.Soft) : UpdatePresentation
}

internal fun updatePresentation(
    status: UpdateStatus,
    sessionOpen: Boolean,
    covered: Boolean,
    resumed: Boolean,
): UpdatePresentation = when (status) {
    UpdateStatus.UpToDate -> UpdatePresentation.Content

    is UpdateStatus.Force -> UpdatePresentation.Force(status)

    is UpdateStatus.Soft -> if (sessionOpen && !covered && resumed) {
        UpdatePresentation.Soft(status)
    } else {
        UpdatePresentation.Content
    }
}

/**
 * Keeps a soft prompt for a build that was already shown or dismissed this session from coming back. A prompt that is
 * still open stays open across a re-check; anything else settles to up to date.
 */
internal fun settleUpdateStatus(current: UpdateStatus, next: UpdateStatus, lastShownBuild: Long): UpdateStatus =
    if (next is UpdateStatus.Soft && next.targetBuild <= lastShownBuild) {
        if (current == next) current else UpdateStatus.UpToDate
    } else {
        next
    }
