package com.example.musicsm.domain.jam

import java.security.SecureRandom

/**
 * The short code a host reads out so a guest can join by typing instead of scanning.
 *
 * This exists because scanning turned out to be the weak link: OnePlus, Samsung and most other
 * OEM camera apps only act on `http(s)` QR codes and silently ignore a custom scheme like
 * `musicsm://`, so the "just point your camera at it" path never even produces a prompt.
 *
 * The code is a **secret**, not an address — it is resolved to a real host by
 * [JamDiscoveryProtocol] over the local network. Keeping the address out of the code is what lets
 * it stay six characters instead of the thirteen an encoded IPv4 and port would need, and it means
 * the code keeps working if the host's address changes.
 */
object JamJoinCode {

    const val LENGTH = 6

    /**
     * Crockford's Base32 alphabet: no `I`, `L`, `O` or `U`. The first three are dropped because
     * they are indistinguishable from `1` and `0` in most fonts, and `U` because excluding it makes
     * an accidental obscenity far less likely in a code people read aloud.
     */
    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

    /** 31^6 is roughly a billion, so guessing one before the host notices is not a concern. */
    fun random(random: SecureRandom = SecureRandom()): String =
        (1..LENGTH).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")

    /**
     * Cleans up whatever the user actually typed, or returns null if it cannot be a code.
     *
     * Accepts lower case, spaces and hyphens, and folds the look-alike characters the alphabet
     * deliberately avoids — someone reading `0` as `O` should not be told their code is wrong.
     */
    fun normalize(input: String): String? {
        val cleaned = buildString {
            for (c in input.uppercase()) {
                when {
                    c == ' ' || c == '-' || c == '_' -> Unit
                    c == 'I' || c == 'L' -> append('1')
                    c == 'O' -> append('0')
                    // Crockford treats U as an error rather than a digit; there is no safe fold.
                    c == 'U' -> return null
                    c in ALPHABET -> append(c)
                    else -> return null
                }
            }
        }
        return cleaned.takeIf { it.length == LENGTH }
    }

    /** Splits the code in half for display, which makes it far easier to read out loud. */
    fun format(code: String): String =
        if (code.length == LENGTH) "${code.take(3)}-${code.drop(3)}" else code
}
