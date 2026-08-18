package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.example.engine.VideoCompositorEngine
import com.example.model.AudioSource
import com.example.model.ExportResolution
import com.example.model.ExportState
import com.example.model.VideoClip
import com.example.util.MediaMetadataUtil
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class CompositorUiState(
    val clips: List<VideoClip> = emptyList(),
    val currentClipIndex: Int = 0,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val currentClipDurationMs: Long = 0L,
    val audioSource: AudioSource = AudioSource.OriginalAudio,
    val selectedResolution: ExportResolution = ExportResolution.P1080,
    val isExportDialogOpen: Boolean = false,
    val exportState: ExportState = ExportState.Idle,
    val isProcessingMedia: Boolean = false,
    val isMusicPlaying: Boolean = false,
    val infoMessage: String? = null
) {
    val totalDurationMs: Long
        get() = clips.sumOf { it.durationMs }

    val currentClip: VideoClip?
        get() = clips.getOrNull(currentClipIndex)
}

class VideoCompositorViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(CompositorUiState())
    val uiState: StateFlow<CompositorUiState> = _uiState.asStateFlow()

    private val context: Context get() = getApplication()
    private val engine = VideoCompositorEngine(context)

    var previewPlayer: ExoPlayer? = null
        private set

    var musicPlayer: ExoPlayer? = null
        private set

    private var progressTrackingJob: Job? = null
    private var exportJob: Job? = null

    init {
        setupPreviewPlayer()
    }

    private fun setupPreviewPlayer() {
        previewPlayer = ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _uiState.update { it.copy(isPlaying = isPlaying) }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        handleClipPlaybackEnded()
                    }
                    updatePlaybackPosition()
                }
            })
        }

        // Start periodic position ticker
        progressTrackingJob = viewModelScope.launch {
            while (isActive) {
                if (previewPlayer?.isPlaying == true) {
                    updatePlaybackPosition()
                }
                delay(200)
            }
        }
    }

    private fun updatePlaybackPosition() {
        val player = previewPlayer ?: return
        val pos = player.currentPosition.coerceAtLeast(0L)
        val dur = player.duration.coerceAtLeast(0L)
        _uiState.update {
            it.copy(
                currentPositionMs = pos,
                currentClipDurationMs = if (dur > 0) dur else (it.currentClip?.durationMs ?: 0L)
            )
        }
    }

    private fun handleClipPlaybackEnded() {
        val state = _uiState.value
        if (state.clips.isEmpty()) return
        val nextIndex = (state.currentClipIndex + 1) % state.clips.size
        selectClipForPreview(nextIndex, autoPlay = true)
    }

    fun addVideoUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessingMedia = true) }
            val newClips = uris.map { uri ->
                MediaMetadataUtil.extractVideoClip(context, uri)
            }
            val currentList = _uiState.value.clips.toMutableList()
            val wasEmpty = currentList.isEmpty()
            currentList.addAll(newClips)

            _uiState.update {
                it.copy(
                    clips = currentList,
                    isProcessingMedia = false,
                    infoMessage = "Added ${newClips.size} video clip(s)"
                )
            }

            if (wasEmpty && currentList.isNotEmpty()) {
                selectClipForPreview(0, autoPlay = false)
            }
        }
    }

    fun loadSampleClips() {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessingMedia = true) }
            val samples = com.example.util.SampleVideoGenerator.generateSampleClips(context)
            val currentList = _uiState.value.clips.toMutableList()
            val wasEmpty = currentList.isEmpty()
            currentList.addAll(samples)

            _uiState.update {
                it.copy(
                    clips = currentList,
                    isProcessingMedia = false,
                    infoMessage = "Loaded ${samples.size} sample 9:16 portrait clips"
                )
            }

            if (wasEmpty && currentList.isNotEmpty()) {
                selectClipForPreview(0, autoPlay = true)
            }
        }
    }

    fun removeClip(clipId: String) {
        val currentList = _uiState.value.clips.toMutableList()
        val indexToRemove = currentList.indexOfFirst { it.id == clipId }
        if (indexToRemove != -1) {
            currentList.removeAt(indexToRemove)
            val newIndex = when {
                currentList.isEmpty() -> 0
                _uiState.value.currentClipIndex >= currentList.size -> currentList.size - 1
                else -> _uiState.value.currentClipIndex
            }
            _uiState.update {
                it.copy(
                    clips = currentList,
                    currentClipIndex = newIndex
                )
            }
            if (currentList.isNotEmpty()) {
                selectClipForPreview(newIndex, autoPlay = false)
            } else {
                previewPlayer?.stop()
                previewPlayer?.clearMediaItems()
            }
        }
    }

    fun clearAllClips() {
        previewPlayer?.stop()
        previewPlayer?.clearMediaItems()
        _uiState.update {
            it.copy(
                clips = emptyList(),
                currentClipIndex = 0,
                isPlaying = false,
                currentPositionMs = 0L
            )
        }
    }

    fun moveClip(fromIndex: Int, toIndex: Int) {
        val list = _uiState.value.clips.toMutableList()
        if (fromIndex !in list.indices || toIndex !in list.indices || fromIndex == toIndex) return
        val item = list.removeAt(fromIndex)
        list.add(toIndex, item)

        val activeIndex = _uiState.value.currentClipIndex
        val newActiveIndex = when (activeIndex) {
            fromIndex -> toIndex
            in (minOf(fromIndex, toIndex)..maxOf(fromIndex, toIndex)) -> {
                if (fromIndex < toIndex) activeIndex - 1 else activeIndex + 1
            }
            else -> activeIndex
        }

        _uiState.update {
            it.copy(clips = list, currentClipIndex = newActiveIndex)
        }
    }

    fun moveClipUp(index: Int) {
        if (index > 0) {
            moveClip(index, index - 1)
        }
    }

    fun moveClipDown(index: Int) {
        if (index < _uiState.value.clips.size - 1) {
            moveClip(index, index + 1)
        }
    }

    fun selectClipForPreview(index: Int, autoPlay: Boolean = false) {
        val state = _uiState.value
        if (index !in state.clips.indices) return
        val clip = state.clips[index]
        _uiState.update { it.copy(currentClipIndex = index) }

        val player = previewPlayer ?: return
        player.stop()
        player.setMediaItem(MediaItem.fromUri(clip.uri))
        player.prepare()
        if (autoPlay) {
            player.play()
        }
    }

    fun togglePlayPause() {
        val player = previewPlayer ?: return
        if (_uiState.value.clips.isEmpty()) return
        if (player.playbackState == Player.STATE_IDLE) {
            val clip = _uiState.value.currentClip ?: return
            player.setMediaItem(MediaItem.fromUri(clip.uri))
            player.prepare()
        }
        if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
    }

    fun seekTo(positionMs: Long) {
        previewPlayer?.seekTo(positionMs)
        _uiState.update { it.copy(currentPositionMs = positionMs) }
    }

    fun playPreviousClip() {
        val state = _uiState.value
        if (state.clips.isEmpty()) return
        val newIndex = if (state.currentClipIndex > 0) state.currentClipIndex - 1 else state.clips.size - 1
        selectClipForPreview(newIndex, autoPlay = true)
    }

    fun playNextClip() {
        val state = _uiState.value
        if (state.clips.isEmpty()) return
        val newIndex = (state.currentClipIndex + 1) % state.clips.size
        selectClipForPreview(newIndex, autoPlay = true)
    }

    fun setAudioSource(source: AudioSource) {
        stopMusicPreview()
        _uiState.update { it.copy(audioSource = source) }
    }

    fun setCustomMusicUri(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessingMedia = true) }
            val musicSource = MediaMetadataUtil.extractAudioSource(context, uri)
            _uiState.update {
                it.copy(
                    audioSource = musicSource,
                    isProcessingMedia = false,
                    infoMessage = "Selected music: ${musicSource.displayName}"
                )
            }
        }
    }

    fun toggleMusicPreview() {
        val audioSource = _uiState.value.audioSource
        if (audioSource !is AudioSource.MusicFile) return

        if (musicPlayer == null) {
            musicPlayer = ExoPlayer.Builder(context).build().apply {
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _uiState.update { it.copy(isMusicPlaying = isPlaying) }
                    }
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED) {
                            _uiState.update { it.copy(isMusicPlaying = false) }
                        }
                    }
                })
            }
        }

        val player = musicPlayer ?: return
        if (player.isPlaying) {
            player.pause()
        } else {
            player.stop()
            player.setMediaItem(MediaItem.fromUri(audioSource.uri))
            player.prepare()
            player.play()
        }
    }

    private fun stopMusicPreview() {
        musicPlayer?.stop()
        _uiState.update { it.copy(isMusicPlaying = false) }
    }

    fun setResolution(resolution: ExportResolution) {
        _uiState.update { it.copy(selectedResolution = resolution) }
    }

    fun openExportDialog() {
        previewPlayer?.pause()
        stopMusicPreview()
        _uiState.update { it.copy(isExportDialogOpen = true, exportState = ExportState.Idle) }
    }

    fun closeExportDialog() {
        if (_uiState.value.exportState is ExportState.Progress) {
            cancelExport()
        }
        _uiState.update { it.copy(isExportDialogOpen = false, exportState = ExportState.Idle) }
    }

    @OptIn(UnstableApi::class)
    fun startExport() {
        val state = _uiState.value
        if (state.clips.isEmpty()) {
            _uiState.update { it.copy(exportState = ExportState.Error("No clips to export")) }
            return
        }

        exportJob?.cancel()
        exportJob = viewModelScope.launch {
            engine.exportComposition(
                clips = state.clips,
                audioSource = state.audioSource,
                resolution = state.selectedResolution
            ) { progressState ->
                _uiState.update { it.copy(exportState = progressState) }
            }
        }
    }

    fun cancelExport() {
        exportJob?.cancel()
        engine.cancelExport()
        _uiState.update { it.copy(exportState = ExportState.Idle) }
    }

    fun clearInfoMessage() {
        _uiState.update { it.copy(infoMessage = null) }
    }

    fun getFFmpegCommand(): String {
        val state = _uiState.value
        return engine.buildFFmpegCommandString(
            clips = state.clips,
            audioSource = state.audioSource,
            resolution = state.selectedResolution
        )
    }

    override fun onCleared() {
        super.onCleared()
        progressTrackingJob?.cancel()
        exportJob?.cancel()
        previewPlayer?.release()
        previewPlayer = null
        musicPlayer?.release()
        musicPlayer = null
    }
}
