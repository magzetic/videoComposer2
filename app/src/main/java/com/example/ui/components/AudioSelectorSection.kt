package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AudioSource
import com.example.ui.theme.BorderOutline
import com.example.ui.theme.BorderOutlineSubtle
import com.example.ui.theme.PrimaryPurple
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun AudioSelectorSection(
    audioSource: AudioSource,
    isMusicPlaying: Boolean,
    onSelectOriginalAudio: () -> Unit,
    onPickMusicFile: () -> Unit,
    onToggleMusicPreview: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isOriginal = audioSource is AudioSource.OriginalAudio

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "AUDIO SOURCE",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary,
                    letterSpacing = 0.5.sp
                )
            )
            Text(
                text = if (isOriginal) "Original audio tracks" else "Custom background music",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = Color(0xFF79747E)
                )
            )
        }

        // Clean Minimalism Pill Container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFE7E0EC), CircleShape)
                .padding(4.dp)
                .testTag("audio_source_selector")
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                // Original Audio Pill
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(CircleShape)
                        .background(if (isOriginal) Color.White else Color.Transparent)
                        .then(
                            if (isOriginal) Modifier.shadow(2.dp, CircleShape) else Modifier
                        )
                        .clickable(onClick = onSelectOriginalAudio)
                        .padding(vertical = 8.dp)
                        .testTag("audio_opt_original"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = if (isOriginal) TextPrimary else TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Original Audio",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (isOriginal) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (isOriginal) TextPrimary else TextSecondary
                            )
                        )
                    }
                }

                // Music from File Pill
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(CircleShape)
                        .background(if (!isOriginal) Color.White else Color.Transparent)
                        .then(
                            if (!isOriginal) Modifier.shadow(2.dp, CircleShape) else Modifier
                        )
                        .clickable(onClick = onPickMusicFile)
                        .padding(vertical = 8.dp)
                        .testTag("audio_opt_music"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Audiotrack,
                            contentDescription = null,
                            tint = if (!isOriginal) PrimaryPurple else TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Music from File",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (!isOriginal) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (!isOriginal) PrimaryPurple else TextSecondary
                            )
                        )
                    }
                }
            }
        }

        // Expanded Custom Music Details Card
        AnimatedVisibility(
            visible = audioSource is AudioSource.MusicFile,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            val music = audioSource as? AudioSource.MusicFile
            if (music != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, BorderOutlineSubtle, RoundedCornerShape(16.dp)),
                    color = Color.White,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Play/Pause Music Preview
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color(0xFFEADDFF), CircleShape)
                                .clickable(onClick = onToggleMusicPreview)
                                .testTag("music_preview_toggle"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isMusicPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isMusicPlaying) "Pause music" else "Play music",
                                tint = Color(0xFF21005D),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = music.displayName,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Duration: ${music.formattedDuration} (Loops to fit video)",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color(0xFF79747E)
                                )
                            )
                        }

                        // Change file button
                        OutlinedButton(
                            onClick = onPickMusicFile,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("change_music_button")
                        ) {
                            Text(
                                text = "Change",
                                fontSize = 12.sp,
                                color = PrimaryPurple
                            )
                        }
                    }
                }
            }
        }
    }
}
