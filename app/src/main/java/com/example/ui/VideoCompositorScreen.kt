package com.example.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AudioSource
import com.example.model.ExportResolution
import com.example.model.formatDuration
import com.example.ui.components.AudioSelectorSection
import com.example.ui.components.ClipListItem
import com.example.ui.components.ExportDialog
import com.example.ui.components.VideoPreviewCard
import com.example.ui.theme.BorderOutline
import com.example.ui.theme.BorderOutlineSubtle
import com.example.ui.theme.CleanBackground
import com.example.ui.theme.CleanSurfaceVariant
import com.example.ui.theme.PrimaryContainer
import com.example.ui.theme.PrimaryPurple
import com.example.ui.theme.PrimaryPurpleDark
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.VideoCompositorViewModel

@Composable
fun VideoCompositorScreen(
    viewModel: VideoCompositorViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Multi-video picker launcher (Photo Picker)
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 15)
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.addVideoUris(uris)
        }
    }

    // Audio file picker launcher
    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.setCustomMusicUri(uri)
        }
    }

    var isResolutionDropdownOpen by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.infoMessage) {
        uiState.infoMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearInfoMessage()
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(CleanBackground),
        containerColor = CleanBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
        ) {
            // Header (Compositor + Fast Add button)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Compositor",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            letterSpacing = (-0.5).sp
                        )
                    )
                    Text(
                        text = "Portrait 9:16 to 16:9 Blur Background",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = TextMuted
                        )
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (uiState.clips.isNotEmpty()) {
                        IconButton(
                            onClick = { viewModel.clearAllClips() },
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("clear_all_clips_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Clear All Clips",
                                tint = TextSecondary
                            )
                        }
                    }

                    // Add Videos Circle Button
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(PrimaryContainer, CircleShape)
                            .clickable {
                                videoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                )
                            }
                            .testTag("add_videos_header_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Select Videos",
                            tint = Color(0xFF21005D),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // Top Section: Fixed 16:9 Video Preview
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                VideoPreviewCard(
                    player = viewModel.previewPlayer,
                    currentClip = uiState.currentClip,
                    clipIndex = uiState.currentClipIndex,
                    totalClips = uiState.clips.size,
                    isPlaying = uiState.isPlaying,
                    currentPositionMs = uiState.currentPositionMs,
                    durationMs = uiState.currentClipDurationMs,
                    onTogglePlayPause = { viewModel.togglePlayPause() },
                    onPreviousClip = { viewModel.playPreviousClip() },
                    onNextClip = { viewModel.playNextClip() },
                    onSeek = { pos -> viewModel.seekTo(pos) },
                    onAddVideosClick = {
                        videoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                        )
                    }
                )
            }

            if (uiState.isProcessingMedia) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    color = PrimaryPurple,
                    trackColor = Color(0xFFE7E0EC)
                )
            }

            // Middle Section: Selected Clips List
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SELECTED CLIPS (${uiState.clips.size})",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary,
                            letterSpacing = 0.5.sp
                        )
                    )
                    Text(
                        text = "Tap to preview • Reorder",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = TextMuted
                        )
                    )
                }

                if (uiState.clips.isEmpty()) {
                    // Empty Clips List Placeholder
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(20.dp))
                            .border(1.dp, BorderOutlineSubtle, RoundedCornerShape(20.dp))
                            .clickable {
                                videoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                )
                            },
                        color = Color.White
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .background(Color(0xFFF3EDF7), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VideoLibrary,
                                    contentDescription = null,
                                    tint = PrimaryPurple,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No Videos Selected",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Select 5–10 portrait 9:16 clips from your gallery, or load sample portrait clips to test immediately.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = TextSecondary
                                ),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = {
                                        videoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = PrimaryPurple,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.testTag("empty_select_videos_btn")
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Pick Videos", fontSize = 12.sp)
                                }

                                OutlinedButton(
                                    onClick = { viewModel.loadSampleClips() },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.testTag("empty_load_samples_btn")
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Load Samples", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                } else {
                    // List of Clips
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("clips_lazy_column"),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        itemsIndexed(
                            items = uiState.clips,
                            key = { _, clip -> clip.id }
                        ) { index, clip ->
                            ClipListItem(
                                clip = clip,
                                index = index,
                                isSelected = index == uiState.currentClipIndex,
                                canMoveUp = index > 0,
                                canMoveDown = index < uiState.clips.size - 1,
                                onSelect = { viewModel.selectClipForPreview(index, autoPlay = true) },
                                onMoveUp = { viewModel.moveClipUp(index) },
                                onMoveDown = { viewModel.moveClipDown(index) },
                                onRemove = { viewModel.removeClip(clip.id) }
                            )
                        }
                    }
                }
            }

            // Bottom Section: Clean Minimalism Action Panel
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                    .border(
                        1.dp,
                        BorderOutlineSubtle,
                        RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
                    )
                    .shadow(
                        elevation = 12.dp,
                        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
                    ),
                color = CleanSurfaceVariant,
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                        .navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Audio Selector
                    AudioSelectorSection(
                        audioSource = uiState.audioSource,
                        isMusicPlaying = uiState.isMusicPlaying,
                        onSelectOriginalAudio = { viewModel.setAudioSource(AudioSource.OriginalAudio) },
                        onPickMusicFile = { audioPickerLauncher.launch("audio/*") },
                        onToggleMusicPreview = { viewModel.toggleMusicPreview() }
                    )

                    // Resolution & Export Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        // Resolution Dropdown Box
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "RESOLUTION",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextSecondary,
                                    letterSpacing = 0.5.sp
                                ),
                                modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp)
                            )
                            Box {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(1.dp, BorderOutline, RoundedCornerShape(12.dp))
                                        .clickable { isResolutionDropdownOpen = true }
                                        .testTag("resolution_dropdown_button"),
                                    color = Color.White,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = uiState.selectedResolution.label.take(5),
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                color = TextPrimary
                                            )
                                        )
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = null,
                                            tint = TextSecondary
                                        )
                                    }
                                }

                                DropdownMenu(
                                    expanded = isResolutionDropdownOpen,
                                    onDismissRequest = { isResolutionDropdownOpen = false }
                                ) {
                                    ExportResolution.values().forEach { res ->
                                        DropdownMenuItem(
                                            text = {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column {
                                                        Text(res.label, fontWeight = FontWeight.Bold)
                                                        Text(res.subtitle, fontSize = 11.sp, color = TextSecondary)
                                                    }
                                                    if (res == uiState.selectedResolution) {
                                                        Icon(Icons.Default.Check, contentDescription = null, tint = PrimaryPurple)
                                                    }
                                                }
                                            },
                                            onClick = {
                                                viewModel.setResolution(res)
                                                isResolutionDropdownOpen = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Export Button
                        Button(
                            onClick = { viewModel.openExportDialog() },
                            enabled = uiState.clips.isNotEmpty(),
                            modifier = Modifier
                                .weight(1.5f)
                                .height(48.dp)
                                .testTag("export_video_main_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PrimaryPurple,
                                contentColor = Color.White,
                                disabledContainerColor = Color(0xFFE7E0EC),
                                disabledContentColor = Color(0xFF79747E)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FileUpload,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "EXPORT VIDEO",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Export Modal Dialog
        ExportDialog(
            isOpen = uiState.isExportDialogOpen,
            exportState = uiState.exportState,
            selectedResolution = uiState.selectedResolution,
            audioSource = uiState.audioSource,
            clipCount = uiState.clips.size,
            totalDurationMs = uiState.totalDurationMs,
            onResolutionSelected = { viewModel.setResolution(it) },
            onStartExport = { viewModel.startExport() },
            onCancelExport = { viewModel.cancelExport() },
            onDismiss = { viewModel.closeExportDialog() }
        )
    }
}
