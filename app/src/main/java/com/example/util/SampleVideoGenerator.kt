package com.example.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.view.Surface
import com.example.model.VideoClip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

object SampleVideoGenerator {

    private data class SampleSpec(
        val name: String,
        val topColor: Int,
        val bottomColor: Int,
        val label: String,
        val durationSeconds: Int
    )

    private val SAMPLES = listOf(
        SampleSpec(
            name = "urban_vlog_01.mp4",
            topColor = Color.parseColor("#4A0E4E"),
            bottomColor = Color.parseColor("#0D1B2A"),
            label = "City Walk (9:16)",
            durationSeconds = 4
        ),
        SampleSpec(
            name = "sunset_skyline_02.mp4",
            topColor = Color.parseColor("#C2410C"),
            bottomColor = Color.parseColor("#431407"),
            label = "Golden Sunset (9:16)",
            durationSeconds = 5
        ),
        SampleSpec(
            name = "ocean_waves_03.mp4",
            topColor = Color.parseColor("#0369A1"),
            bottomColor = Color.parseColor("#082F49"),
            label = "Ocean Breeze (9:16)",
            durationSeconds = 4
        ),
        SampleSpec(
            name = "coffee_pour_04.mp4",
            topColor = Color.parseColor("#78350F"),
            bottomColor = Color.parseColor("#1C1917"),
            label = "Morning Cafe (9:16)",
            durationSeconds = 3
        )
    )

    suspend fun generateSampleClips(context: Context): List<VideoClip> = withContext(Dispatchers.IO) {
        val outputClips = mutableListOf<VideoClip>()

        for (spec in SAMPLES) {
            val file = File(context.cacheDir, spec.name)
            if (!file.exists() || file.length() == 0L) {
                createSyntheticPortraitVideo(
                    outputFile = file,
                    spec = spec,
                    width = 720,
                    height = 1280
                )
            }

            if (file.exists() && file.length() > 0L) {
                val uri = Uri.fromFile(file)
                outputClips.add(
                    VideoClip(
                        id = UUID.randomUUID().toString(),
                        uri = uri,
                        displayName = spec.name,
                        durationMs = spec.durationSeconds * 1000L,
                        width = 720,
                        height = 1280,
                        sizeBytes = file.length()
                    )
                )
            }
        }
        outputClips
    }

    private fun createSyntheticPortraitVideo(
        outputFile: File,
        spec: SampleSpec,
        width: Int,
        height: Int
    ) {
        val frameRate = 30
        val totalFrames = frameRate * spec.durationSeconds
        val bitRate = 2_000_000
        val mime = MediaFormat.MIMETYPE_VIDEO_AVC

        var codec: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var inputSurface: Surface? = null

        try {
            val format = MediaFormat.createVideoFormat(mime, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

            codec = MediaCodec.createEncoderByType(mime)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            inputSurface = codec.createInputSurface()
            codec.start()

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val bufferInfo = MediaCodec.BufferInfo()
            var trackIndex = -1
            var muxerStarted = false

            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 36f
                textAlign = Paint.Align.CENTER
            }
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 48f
                textAlign = Paint.Align.CENTER
                isFakeBoldText = true
            }

            for (frame in 0 until totalFrames) {
                // Render frame to input surface
                val canvas: Canvas = inputSurface.lockCanvas(null)
                try {
                    val progress = frame.toFloat() / totalFrames.toFloat()

                    // Background vertical gradient
                    paint.shader = android.graphics.LinearGradient(
                        0f, 0f, 0f, height.toFloat(),
                        spec.topColor, spec.bottomColor,
                        android.graphics.Shader.TileMode.CLAMP
                    )
                    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
                    paint.shader = null

                    // Decorative animated pulse circle
                    paint.color = Color.WHITE
                    paint.alpha = (40 + (30 * Math.sin(frame * 0.15)).toInt()).coerceIn(20, 90)
                    val circleY = height * 0.4f + (50 * Math.sin(frame * 0.08)).toFloat()
                    canvas.drawCircle(width / 2f, circleY, 140f, paint)

                    // Card backdrop
                    paint.color = Color.BLACK
                    paint.alpha = 100
                    val cardRect = RectF(60f, height * 0.55f, width - 60f, height * 0.75f)
                    canvas.drawRoundRect(cardRect, 24f, 24f, paint)

                    // Text labels
                    canvas.drawText(spec.label, width / 2f, height * 0.63f, titlePaint)
                    canvas.drawText("Portrait 9:16 Clip", width / 2f, height * 0.68f, textPaint)
                    canvas.drawText("Frame ${frame + 1} / $totalFrames", width / 2f, height * 0.72f, textPaint)

                    // Progress bar at bottom
                    paint.color = Color.parseColor("#EADDFF")
                    paint.alpha = 200
                    canvas.drawRect(0f, height - 16f, width * progress, height.toFloat(), paint)

                } finally {
                    inputSurface.unlockCanvasAndPost(canvas)
                }

                // Drain encoder
                while (true) {
                    val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
                    if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        val newFormat = codec.outputFormat
                        trackIndex = muxer.addTrack(newFormat)
                        muxer.start()
                        muxerStarted = true
                    } else if (outputIndex >= 0) {
                        val encodedData = codec.getOutputBuffer(outputIndex)
                        if (encodedData != null && muxerStarted && bufferInfo.size > 0) {
                            encodedData.position(bufferInfo.offset)
                            encodedData.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(trackIndex, encodedData, bufferInfo)
                        }
                        codec.releaseOutputBuffer(outputIndex, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            break
                        }
                    } else {
                        break
                    }
                }
            }

            codec.signalEndOfInputStream()

            // Drain remaining EOS
            while (true) {
                val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                if (outputIndex >= 0) {
                    val encodedData = codec.getOutputBuffer(outputIndex)
                    if (encodedData != null && muxerStarted && bufferInfo.size > 0) {
                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(trackIndex, encodedData, bufferInfo)
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        break
                    }
                } else if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    break
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                inputSurface?.release()
                codec?.stop()
                codec?.release()
                muxer?.stop()
                muxer?.release()
            } catch (_: Exception) {}
        }
    }
}
