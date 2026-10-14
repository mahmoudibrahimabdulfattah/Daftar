package com.mk.daftar.core.ui.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** The privacy eye remembers the owner's last choice across launches and locks. */
@OptIn(ExperimentalCoroutinesApi::class)
class ValuesVisibilityTest {

    private class MemoryStore(var stored: Boolean?) : ValuesVisibilityStore {
        override suspend fun hidden(): Boolean? = stored

        override suspend fun setHidden(hidden: Boolean) {
            stored = hidden
        }
    }

    private fun TestScope.visibility(store: ValuesVisibilityStore) = ValuesVisibility(store, backgroundScope)

    @Test
    fun aRememberedHiddenChoiceStartsHidden() = runTest {
        val visibility = visibility(MemoryStore(stored = true))
        runCurrent()
        assertEquals(false, visibility.visible.value)
    }

    @Test
    fun hidingIsStoredAndSurvivesALock() = runTest {
        val store = MemoryStore(stored = false)
        val visibility = visibility(store)
        runCurrent()

        visibility.toggle()
        runCurrent()
        visibility.reset()

        assertEquals(true, store.stored)
        assertEquals(false, visibility.visible.value)
    }

    @Test
    fun anUnreadableStoreLeavesValuesShown() = runTest {
        val visibility = visibility(MemoryStore(stored = null))
        runCurrent()
        assertEquals(true, visibility.visible.value)
    }

    @Test
    fun withoutAStoreTheChoiceIsKeptForTheSessionOnly() {
        val visibility = ValuesVisibility()
        visibility.toggle()
        visibility.reset()
        assertEquals(false, visibility.visible.value)
    }
}
