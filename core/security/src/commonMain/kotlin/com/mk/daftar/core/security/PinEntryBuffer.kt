package com.mk.daftar.core.security

/**
 * The PIN being typed, held as owned bytes rather than a String. Digits arrive as code points in any supported
 * numeral script and are stored as ASCII. The buffer exposes only its count, and it is wiped by [erase], [clear] and
 * [wipe] without ever producing text. Submission copies go out as exact-length [SecretBytes] owned by the caller.
 *
 * Not thread-safe: it is confined to the owner that created it (the auth view model's main-thread state).
 */
class PinEntryBuffer(private val capacity: Int = MAX_DIGITS) {
    private val bytes = ByteArray(capacity)
    private var count = 0
    private var wiped = false

    /** Number of digits entered. The only thing UI state may read. */
    val size: Int
        get() = count

    /** True once the entry is a valid PIN length, so a submit may run. Never auto-submitted. */
    val isSubmittable: Boolean
        get() = count in MIN_DIGITS..MAX_DIGITS

    /**
     * Appends the digit [codePoint] (ASCII, Arabic-Indic or Persian). Returns false, and changes nothing, for any other
     * code point or when the buffer is full. Leading zeros are kept.
     */
    fun append(codePoint: Int): Boolean {
        check(!wiped) { "PIN entry has been wiped" }
        val value = digitValue(codePoint)
        if (value < 0 || count >= capacity) return false
        bytes[count] = ('0'.code + value).toByte()
        count++
        return true
    }

    /** Removes the last digit and zeroes its byte. Returns false when empty. */
    fun erase(): Boolean {
        check(!wiped) { "PIN entry has been wiped" }
        if (count == 0) return false
        count--
        bytes[count] = 0
        return true
    }

    /** Zeroes every entered digit and resets the count. The buffer stays usable. */
    fun clear() {
        bytes.fill(0)
        count = 0
    }

    /** Clears the buffer for good. Later calls that touch digits fail. */
    fun wipe() {
        bytes.fill(0)
        count = 0
        wiped = true
    }

    /**
     * An exact-length, owned copy of the entered digits for one submission. The caller wipes it when the operation
     * that uses it ends, and must not keep it past that point. Null when the entry is not a valid PIN length.
     */
    fun snapshot(): SecretBytes? {
        check(!wiped) { "PIN entry has been wiped" }
        if (!isSubmittable) return null
        return SecretBytes.owned(bytes.copyOf(count))
    }

    /**
     * True when the entered digits equal [expected], compared over every byte without an early exit. Lengths are
     * compared first, which reveals only that the lengths differ.
     */
    fun matches(expected: SecretBytes): Boolean {
        check(!wiped) { "PIN entry has been wiped" }
        return expected.use { other ->
            if (other.size != count) return@use false
            var difference = 0
            for (index in 0 until count) {
                difference = difference or (other[index].toInt() xor bytes[index].toInt())
            }
            difference == 0
        }
    }

    companion object {
        const val MIN_DIGITS = PinNormalizer.MIN_DIGITS
        const val MAX_DIGITS = PinNormalizer.MAX_DIGITS

        /** The value of the decimal digit [codePoint] in ASCII, Arabic-Indic or Persian script, or -1. */
        fun digitValue(codePoint: Int): Int = when (codePoint) {
            in 0x30..0x39 -> codePoint - 0x30
            in 0x0660..0x0669 -> codePoint - 0x0660
            in 0x06F0..0x06F9 -> codePoint - 0x06F0
            else -> -1
        }
    }
}
