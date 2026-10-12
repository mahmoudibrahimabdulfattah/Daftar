package com.mk.daftar.core.backup

/** Why a backup password is not accepted. The rules are checked before any key derivation. */
enum class BackupPasswordProblem {
    /** Fewer than [BackupPasswordPolicy.MIN_CODE_POINTS] Unicode code points. */
    TOO_SHORT,

    /**
     * More than [BackupPasswordPolicy.MAX_CODE_POINTS] code points, or more than
     * [BackupPasswordPolicy.MAX_UTF8_BYTES] bytes.
     */
    TOO_LONG,

    /** An unpaired surrogate, which has no UTF-8 encoding. */
    INVALID_CHARACTERS,
}

/**
 * The backup password rules. The text is used as typed: nothing is trimmed, digit-normalized or case-folded, so the
 * same characters always derive the same key. The rules never look at the Daftar PIN.
 */
object BackupPasswordPolicy {
    const val MIN_CODE_POINTS: Int = 12
    const val MAX_CODE_POINTS: Int = 256
    const val MAX_UTF8_BYTES: Int = 1024

    /** The first failing rule, or null when [text] is an acceptable backup password. */
    fun problem(text: String): BackupPasswordProblem? {
        if (hasUnpairedSurrogate(text)) return BackupPasswordProblem.INVALID_CHARACTERS
        val points = codePointCount(text)
        return when {
            points < MIN_CODE_POINTS -> BackupPasswordProblem.TOO_SHORT
            points > MAX_CODE_POINTS || utf8Length(text) > MAX_UTF8_BYTES -> BackupPasswordProblem.TOO_LONG
            else -> null
        }
    }

    /** The bytes that feed the KDF: exact UTF-8 of the typed text. Only valid after [problem] returned null. */
    fun utf8(text: String): ByteArray {
        require(problem(text) == null) { "password does not meet the policy" }
        return text.encodeToByteArray()
    }

    /** Unicode code points. A surrogate pair counts once. */
    fun codePointCount(text: String): Int {
        var count = 0
        var index = 0
        while (index < text.length) {
            if (text[index].isHighSurrogate() && index + 1 < text.length && text[index + 1].isLowSurrogate()) {
                index += 2
            } else {
                index += 1
            }
            count += 1
        }
        return count
    }

    /** UTF-8 byte length of a string with no unpaired surrogates. */
    fun utf8Length(text: String): Int {
        var bytes = 0
        var index = 0
        while (index < text.length) {
            val char = text[index]
            bytes += when {
                char.code < 0x80 -> 1

                char.code < 0x800 -> 2

                char.isHighSurrogate() && index + 1 < text.length && text[index + 1].isLowSurrogate() -> {
                    index += 1
                    4
                }

                else -> 3
            }
            index += 1
        }
        return bytes
    }

    private fun hasUnpairedSurrogate(text: String): Boolean {
        var index = 0
        while (index < text.length) {
            val char = text[index]
            if (char.isHighSurrogate()) {
                if (index + 1 >= text.length || !text[index + 1].isLowSurrogate()) return true
                index += 2
                continue
            }
            if (char.isLowSurrogate()) return true
            index += 1
        }
        return false
    }
}
