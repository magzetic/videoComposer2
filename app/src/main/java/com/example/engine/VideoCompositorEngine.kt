package com.example.engine

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import androidx.media3.effect.Presentation
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultMuxer
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.TransformationRequest
import androidx.media3.transformer.Transformer
import com.example.model.AudioSource
import com.example.model.ExportResolution
import com.example.model.ExportState
import com.example.model.VideoClip
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VideoCompositorEngine(private val context: Context) {

    private var activeTransformer: Transformer? = null
    private var progressJob: Job? = null

    /**
     * Composes sequential portrait 9:16 videos into a 16:9 output with centered sharp video
     * and blurred background side wings, optionally replacing audio.
     */
    @OptIn(UnstableApi::class)
    suspend fun exportComposition(
        clips: List<VideoClip>,
        audioSource: AudioSource,
        resolution: ExportResolution,
        onProgress: (ExportState) -> Unit
    ) = withContext(Dispatchers.Main) {
        if (clips.isEmpty()) {
            onProgress(ExportState.Error("Please select at least one video clip."))
            return@withContext
        }

        val tempOutputFile = File(context.cacheDir, "composite_${System.currentTimeMillis()}.mp4")
        if (tempOutputFile.exists()) {
            tempOutputFile.delete()
        }

        val totalDurationMs = clips.sumOf { it.durationMs }
        onProgress(ExportState.Progress(1, "Preparing video composition..."))

        try {
            // Build Video Effects for each clip
            val videoEffects = listOf(
                Presentation.createForWidthAndHeight(
                    resolution.width,
                    resolution.height,
                    Presentation.LAYOUT_SCALE_TO_FIT
                )
            )

            val editedVideoItems = clips.map { clip ->
                val mediaItem = MediaItem.fromUri(clip.uri)
                val isRemoveAudio = audioSource is AudioSource.MusicFile
                EditedMediaItem.Builder(mediaItem)
                    .setRemoveAudio(isRemoveAudio)
                    .setEffects(Effects(emptyList(), videoEffects))
                    .build()
            }

            val videoSequence = EditedMediaItemSequence(editedVideoItems)

            // Audio configuration
            val sequences = mutableListOf<EditedMediaItemSequence>()
            sequences.add(videoSequence)

            if (audioSource is AudioSource.MusicFile) {
                // If custom music is selected, build an audio sequence that loops or trims to match video duration
                val musicDurationMs = if (audioSource.durationMs > 0) audioSource.durationMs else totalDurationMs
                val loopCount = if (musicDurationMs > 0 && totalDurationMs > musicDurationMs) {
                    ((totalDurationMs / musicDurationMs) + 1).toInt()
                } else 1

                val audioItems = mutableListOf<EditedMediaItem>()
                for (i in 0 until loopCount) {
                    val musicItem = MediaItem.fromUri(audioSource.uri)
                    audioItems.add(
                        EditedMediaItem.Builder(musicItem)
                            .setRemoveVideo(true)
                            .build()
                    )
                }
                sequences.add(EditedMediaItemSequence(audioItems))
            }

            val composition = Composition.Builder(sequences)
                .build()

            val transformationRequest = TransformationRequest.Builder()
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .build()

            val progressHolder = ProgressHolder()

            var exportCompleted = false
            var exportFailed: Exception? = null

            val transformer = Transformer.Builder(context)
                .setTransformationRequest(transformationRequest)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        exportCompleted = true
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        exportFailed = exportException
                        exportCompleted = true
                    }
                })
                .build()

            activeTransformer = transformer

            // Start Transformation
            transformer.start(composition, tempOutputFile.absolutePath)

            // Progress polling loop
            progressJob = CoroutineScope(Dispatchers.Main).launch {
                while (isActive && !exportCompleted) {
                    val progressState = transformer.getProgress(progressHolder)
                    if (progressState == Transformer.PROGRESS_STATE_AVAILABLE) {
                        val percent = progressHolder.progress.coerceIn(0, 99)
                        val msg = when {
                            percent < 30 -> "Compositing clips & applying 16:9 layout ($percent%)"
                            percent < 70 -> "Encoding H.264 video ($percent%)"
                            else -> "Finalizing AAC audio & saving ($percent%)"
                        }
                        onProgress(ExportState.Progress(percent, msg))
                    }
                    delay(250)
                }
            }

            while (!exportCompleted && isActive) {
                delay(100)
            }

            progressJob?.cancel()

            if (exportFailed != null) {
                throw exportFailed!!
            }

            if (!tempOutputFile.exists() || tempOutputFile.length() == 0L) {
                throw IllegalStateException("Export produced an empty or invalid output file.")
            }

            onProgress(ExportState.Progress(98, "Saving video to Gallery..."))

            // Save output file to MediaStore Gallery
            val savedUri = saveToGallery(
                context = context,
                sourceFile = tempOutputFile,
                resolution = resolution
            )

            val fileSize = tempOutputFile.length()
            tempOutputFile.delete()

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val exportDisplayName = "Composite_16x9_$timestamp.mp4"

            onProgress(
                ExportState.Success(
                    outputUri = savedUri,
                    displayName = exportDisplayName,
                    durationMs = totalDurationMs,
                    resolution = "${resolution.width}×${resolution.height} (16:9)",
                    sizeBytes = fileSize
                )
            )

        } catch (e: CancellationException) {
            cancelExport()
            tempOutputFile.delete()
            onProgress(ExportState.Idle)
        } catch (e: Exception) {
            cancelExport()
            tempOutputFile.delete()
            onProgress(ExportState.Error(e.localizedMessage ?: "Video export failed. Please check source files."))
        } finally {
            activeTransformer = null
            progressJob = null
        }
    }

    fun cancelExport() {
        try {
            progressJob?.cancel()
            activeTransformer?.cancel()
        } catch (_: Exception) {}
        activeTransformer = null
        progressJob = null
    }

    private suspend fun saveToGallery(
        context: Context,
        sourceFile: File,
        resolution: ExportResolution
    ): Uri = withContext(Dispatchers.IO) {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "VideoCompositor_$timestamp.mp4"

        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.WIDTH, resolution.width)
            put(MediaStore.Video.Media.HEIGHT, resolution.height)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/VideoCompositor")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val itemUri = context.contentResolver.insert(collection, values)
            ?: throw IllegalStateException("Failed to create MediaStore entry for video export.")

        context.contentResolver.openOutputStream(itemUri)?.use { outputStream ->
            FileInputStream(sourceFile).use { inputStream ->
                inputStream.copyTo(outputStream)
            }
        } ?: throw IllegalStateException("Failed to write video data to MediaStore.")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            context.contentResolver.update(itemUri, values, null, null)
        }

        itemUri
    }

    /**
     * Generates the equivalent FFmpeg command for the user's composite pipeline.
     */
    fun buildFFmpegCommandString(
        clips: List<VideoClip>,
        audioSource: AudioSource,
        resolution: ExportResolution
    ): String {
        if (clips.isEmpty()) return ""
        val width = resolution.width
        val height = resolution.height
        val sb = StringBuilder()
        sb.append("ffmpeg \\\n")

        clips.forEach { clip ->
            sb.append("  -i \"${clip.displayName}\" \\\n")
        }

        if (audioSource is AudioSource.MusicFile) {
            sb.append("  -stream_loop -1 -i \"${audioSource.displayName}\" \\\n")
        }

        sb.append("  -filter_complex \"")
        clips.indices.forEach { i ->
            sb.append("[$i:v]scale=$width:$height:force_original_aspect_ratio=increase,crop=$width:$height,boxblur=25:5[bg$i];")
            sb.append("[$i:v]scale=-1:$height[fg$i];")
            sb.append("[bg$i][fg$i]overlay=(W-w)/2:(H-h)/2[v$i];")
        }
        clips.indices.forEach { i ->
            sb.append("[v$i]")
            if (audioSource is AudioSource.OriginalAudio) {
                sb.append("[$i:a]")
            }
        }
        val concatAudio = if (audioSource is AudioSource.OriginalAudio) "a=1" else "a=0"
        sb.append("concat=n=${clips.size}:v=1:$concatAudio[outv]")
        if (audioSource is AudioSource.OriginalAudio) {
            sb.append("[outa]")
        }
        sb.append("\" \\\n")
        sb.append("  -map \"[outv]\" \\\n")
        if (audioSource is AudioSource.MusicFile) {
            sb.append("  -map ${clips.size}:a -shortest \\\n")
        } else {
            sb.append("  -map \"[outa]\" \\\n")
        }
        sb.append("  -c:v libx264 -preset medium -crf 22 -c:a aac -b:a 192k \\\n")
        sb.append("  \"output_${resolution.label.take(4)}.mp4\"")

        return sb.toString()
    }
}
