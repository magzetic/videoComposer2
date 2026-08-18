package com.example.util

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.example.model.AudioSource
import com.example.model.VideoClip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

object MediaMetadataUtil {

    suspend fun extractVideoClip(context: Context, uri: Uri): VideoClip = withContext(Dispatchers.IO) {
        var displayName = getFileNameFromUri(context, uri) ?: "Video_${System.currentTimeMillis()}"
        var durationMs = 0L
        var width = 1080
        var height = 1920
        var sizeBytes = 0L

        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        val name = cursor.getString(nameIndex)
                        if (!name.isNullOrBlank()) {
                            displayName = name
                        }
                    }
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex != -1) {
                        sizeBytes = cursor.getLong(sizeIndex)
                    }
                }
            }
        } catch (_: Exception) {}

        try {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(context, uri)
                val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                durationMs = durationStr?.toLongOrNull() ?: 0L

                val widthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                val heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                val rotationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)

                var rawWidth = widthStr?.toIntOrNull() ?: 1080
                var rawHeight = heightStr?.toIntOrNull() ?: 1920
                val rotation = rotationStr?.toIntOrNull() ?: 0

                // Account for 90 or 270 degree rotation
                if (rotation == 90 || rotation == 270) {
                    val temp = rawWidth
                    rawWidth = rawHeight
                    rawHeight = temp
                }
                width = rawWidth
                height = rawHeight
            }
        } catch (_: Exception) {}

        VideoClip(
            id = UUID.randomUUID().toString(),
            uri = uri,
            displayName = displayName,
            durationMs = durationMs,
            width = width,
            height = height,
            sizeBytes = sizeBytes
        )
    }

    suspend fun extractAudioSource(context: Context, uri: Uri): AudioSource.MusicFile = withContext(Dispatchers.IO) {
        var displayName = getFileNameFromUri(context, uri) ?: "Audio_${System.currentTimeMillis()}"
        var durationMs = 0L

        try {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(context, uri)
                val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                durationMs = durationStr?.toLongOrNull() ?: 0L

                val titleStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                val artistStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                if (!titleStr.isNullOrBlank()) {
                    displayName = if (!artistStr.isNullOrBlank()) "$artistStr - $titleStr" else titleStr
                }
            }
        } catch (_: Exception) {}

        AudioSource.MusicFile(
            uri = uri,
            displayName = displayName,
            durationMs = durationMs
        )
    }

    private fun getFileNameFromUri(context: Context, uri: Uri): String? {
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        return cursor.getString(index)
                    }
                }
            }
        } catch (_: Exception) {}
        return uri.lastPathSegment
    }
}
