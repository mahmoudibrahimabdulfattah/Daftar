package com.mk.daftar.core.data.records

import kotlin.time.Instant

/**
 * Write rules shared by every record repository. They are pure so they can be tested without a database: a new
 * record starts at revision 1, an edit increments the revision by one, and an edit's update time never moves backwards.
 */
internal object RecordRules {
    const val FIRST_REVISION: Long = 1L

    /** A revision matches only when it is a real revision (at least 1) and equals the stored one. */
    fun matches(stored: Long, expected: Long): Boolean = expected >= FIRST_REVISION && stored == expected

    fun nextRevision(expected: Long): Long {
        require(expected >= FIRST_REVISION) { "revision starts at 1" }
        return expected + 1
    }

    /** The update time of an edit: the clock's reading, unless the record already carries a later one. */
    fun editedAt(now: Instant, previousUpdate: Instant): Instant = if (now >= previousUpdate) now else previousUpdate
}
