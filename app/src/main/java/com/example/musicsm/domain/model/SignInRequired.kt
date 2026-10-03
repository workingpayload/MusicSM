package com.example.musicsm.domain.model

/**
 * YouTube refused to play without a signed-in account (its "confirm you're not a bot" wall, which
 * goes up for a whole network address), and there is no usable signed-in session to fall back on.
 *
 * Unlike an ordinary stream failure this affects every track alike, so the player must not skip
 * ahead looking for one that works; the listener has to sign in (or wait the block out).
 */
class SignInRequiredException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** True when [this] or anything in its cause chain is a [SignInRequiredException]. */
fun Throwable.isSignInRequired(): Boolean =
    generateSequence(this) { it.cause }.take(MAX_CAUSE_DEPTH).any { it is SignInRequiredException }

private const val MAX_CAUSE_DEPTH = 16
