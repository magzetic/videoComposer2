package com.example.model

import android.net.Uri

data class VideoClip(
    val id: String,
    val uri: Uri,
    val displayName: String,
    val durationMs: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val sizeBytes: Long = 0L,
    val formattedDuration: String = formatDuration(durationMs)
) {
    val isPortrait: Boolean
        get() = height > width || (width > 0 && height.toFloat() / width.toFloat() >= 1.5f)
}

sealed class AudioSource {
    object OriginalAudio : AudioSource()
    data class MusicFile(
        val uri: Uri,
        val displayName: String,
        val durationMs: Long = 0L,
        val formattedDuration: String = formatDuration(durationMs)
    ) : AudioSource()
}

enum class ExportResolution(
    val label: String,
    val subtitle: String,
    val width: Int,
    val height: Int,
    val bitrateMbps: Float
) {
    P480("480p SD", "854 × 480 (Fast export)", 854, 480, 2.5f),
    P720("720p HD", "1280 × 720 (Balanced)", 1280, 720, 5.0f),
    P1080("1080p FHD", "1920 × 1080 (High Quality)", 1920, 1080, 10.0f);

    val aspectRatio: String
        get() = "16:9"
}

sealed class ExportState {
    object Idle : ExportState()
    data class Progress(val progressPercentage: Int, val statusMessage: String) : ExportState()
    data class Success(
        val outputUri: Uri,
        val displayName: String,
        val durationMs: Long,
        val resolution: String,
        val sizeBytes: Long
    ) : ExportState()
    data class Error(val message: String) : ExportState()
}

fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0) return "00:00"
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val hours = minutes / 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes % 60, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format("%.2f GB", gb)
        mb >= 1.0 -> String.format("%.1f MB", mb)
        kb >= 1.0 -> String.format("%.0f KB", kb)
        else -> "$bytes B"
    }
}
