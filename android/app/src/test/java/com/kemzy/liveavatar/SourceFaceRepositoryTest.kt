package com.kemzy.liveavatar

import kotlin.test.Test
import kotlin.test.assertEquals

class SourceFaceRepositoryTest {
    @Test
    fun selectionIsPersistedAsCurrentSource() {
        val repository = SourceFaceRepository()

        repository.select("content://media/external/images/1")

        assertEquals("content://media/external/images/1", repository.currentSource)
    }

    @Test
    fun clearingSelectionRemovesCurrentSource() {
        val repository = SourceFaceRepository()
        repository.select("content://media/external/images/1")

        repository.clear()

        assertEquals(null, repository.currentSource)
    }
}
