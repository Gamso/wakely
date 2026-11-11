package com.gamso.wakely.domain.usecase

import com.gamso.wakely.domain.model.PatternHash
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * The pattern drawn to stop an alarm: dots of a 3x3 grid numbered 0 to 8 (row by row), each used once. It is a wake-up
 * proof, not a protection, but only a salted hash is kept anyway.
 */
object PatternLock {
    const val DOTS = 9
    const val MIN_DOTS = 4
    private const val SALT_BYTES = 16

    fun isValid(dots: List<Int>): Boolean =
        dots.size >= MIN_DOTS && dots.distinct().size == dots.size && dots.all { it in 0 until DOTS }

    fun create(dots: List<Int>, random: SecureRandom = SecureRandom()): PatternHash {
        require(isValid(dots)) { "Not a valid pattern" }
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes).toHex()
        return PatternHash(salt, hash(dots, salt))
    }

    fun matches(dots: List<Int>, stored: PatternHash): Boolean =
        MessageDigest.isEqual(hash(dots, stored.salt).toByteArray(), stored.hash.toByteArray())

    private fun hash(dots: List<Int>, salt: String): String =
        MessageDigest.getInstance("SHA-256").digest((salt + dots.joinToString(",")).toByteArray()).toHex()

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
}
