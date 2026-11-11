package com.gamso.wakely.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PatternLockTest {

    @Test
    fun `a pattern needs four distinct dots of the grid`() {
        assertTrue(PatternLock.isValid(listOf(0, 1, 2, 5)))
        assertFalse(PatternLock.isValid(listOf(0, 1, 2)))
        assertFalse(PatternLock.isValid(listOf(0, 1, 1, 2)))
        assertFalse(PatternLock.isValid(listOf(0, 1, 2, 9)))
        assertFalse(PatternLock.isValid(emptyList()))
    }

    @Test
    fun `the right pattern matches and a wrong one does not`() {
        val stored = PatternLock.create(listOf(0, 4, 8, 5))
        assertTrue(PatternLock.matches(listOf(0, 4, 8, 5), stored))
        assertFalse(PatternLock.matches(listOf(0, 4, 8), stored))
        assertFalse(PatternLock.matches(listOf(5, 8, 4, 0), stored))
    }

    @Test
    fun `the hash does not contain the pattern and the salt makes two hashes differ`() {
        val a = PatternLock.create(listOf(0, 1, 2, 5))
        val b = PatternLock.create(listOf(0, 1, 2, 5))
        assertNotEquals(a.hash, b.hash)
        assertEquals(64, a.hash.length)
        assertFalse(a.hash.contains("0,1,2,5"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `an invalid pattern cannot be saved`() {
        PatternLock.create(listOf(1, 2))
    }
}
