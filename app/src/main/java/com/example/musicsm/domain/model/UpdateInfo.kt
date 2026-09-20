package com.example.musicsm.domain.model

/** A newer release available on GitHub than the installed build. */
data class UpdateInfo(
    /** Human-readable version, e.g. "v2.0.2". */
    val versionName: String,
    /** Release notes (GitHub release body), possibly empty. */
    val notes: String,
    /** Direct download URL of the APK asset. */
    val apkUrl: String,
    /** The release's web page, used as a browser fallback if the in-app install fails. */
    val releaseUrl: String,
)
