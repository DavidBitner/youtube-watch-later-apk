package com.davidbitner.watchlater.ui

import java.util.Locale

fun formatDuration(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%d:%02d", m, s)
}

fun formatSize(bytes: Long): String {
    val gb = bytes / 1_073_741_824.0
    if (gb >= 1.0) return String.format(Locale.US, "%.1f GB", gb)
    val mb = bytes / 1_048_576.0
    return String.format(Locale.US, "%.0f MB", mb)
}

fun formatSpeed(speed: Float): String =
    if (speed % 1f == 0f) "${speed.toInt()}×" else "${speed}×"

val SPEED_OPTIONS = listOf(0.5f, 1f, 1.5f, 1.75f, 2f, 2.5f)
