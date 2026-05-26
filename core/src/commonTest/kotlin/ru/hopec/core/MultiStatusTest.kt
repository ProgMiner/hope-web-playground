package ru.hopec.core

import ru.hopec.core.topography.Range
import kotlin.test.Test
import kotlin.test.assertEquals

class MultiStatusTest {
    @Test
    fun `empty status severity`() {
        assertEquals(StatusSeverity.INFO, MultiStatus().severity)
    }

    @Test
    fun `plain status severity`() {
        assertEquals(StatusSeverity.WARNING, plainStatus().severity)
    }

    @Test
    fun `nested status severity`() {
        assertEquals(StatusSeverity.ERROR, nestedStatus().severity)
    }

    @Test
    fun `empty status message`() {
        assertEquals("nothing: everything is OK", MultiStatus(label = "nothing").message)
    }

    @Test
    fun `plain status message`() {
        assertEquals("something: 1 warning", plainStatus().message)
    }

    @Test
    fun `nested status message`() {
        assertEquals("something: 2 errors, 1 warning", nestedStatus().message)
    }

    private fun nestedStatus(): MultiStatus {
        val status = plainStatus()
        val inner = MultiStatus(label = "inner")
        inner.add(errorStatus("error!", Range()))
        inner.add(errorStatus("another error!", Range()))
        status.add(inner)
        return status
    }

    private fun plainStatus(): MultiStatus {
        val status = MultiStatus(label = "something")
        status.add(warningStatus("warning!", Range()))
        return status
    }
}
