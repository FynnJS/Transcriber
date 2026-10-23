package dev.fynnjs.transcriber.ui.components

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dev.fynnjs.transcriber.R
import dev.fynnjs.transcriber.ui.util.formatDuration
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.io.File

/**
 * Inkognito player: plays only a file from the app-private audio folder. Nothing is reported
 * back to WhatsApp because playback never touches its player or its message state.
 */
@Composable
fun AudioPlayer(
    file: File?,
    modifier: Modifier = Modifier,
) {
    val available = file != null && file.isFile
    if (!available) {
        AudioMissingHint(modifier)
        return
    }

    val context = LocalContext.current
    val player = remember(file?.absolutePath) {
        ExoPlayer.Builder(context).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true,
            )
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            prepare()
        }
    }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    var isPlaying by remember { mutableStateOf(false) }
    var duration by remember { mutableLongStateOf(0L) }
    var position by remember { mutableLongStateOf(0L) }
    var speed by remember { mutableFloatStateOf(1f) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    duration = player.duration.coerceAtLeast(0L)
                }
            }
        }
        player.addListener(listener)
        while (isActive) {
            if (!dragging) {
                position = player.currentPosition.coerceAtLeast(0L)
                val reported = player.duration
                if (reported > 0L && reported != C.TIME_UNSET) duration = reported
            }
            delay(200)
        }
        player.removeListener(listener)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledIconButton(
                    onClick = {
                        if (player.isPlaying) player.pause() else {
                            if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
                            player.play()
                        }
                    },
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = stringResource(
                            if (isPlaying) R.string.player_pause else R.string.player_play,
                        ),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Slider(
                        value = if (dragging) dragValue else position.toFloat(),
                        onValueChange = {
                            dragging = true
                            dragValue = it
                        },
                        onValueChangeFinished = {
                            player.seekTo(dragValue.toLong())
                            position = dragValue.toLong()
                            dragging = false
                        },
                        valueRange = 0f..(duration.coerceAtLeast(1L)).toFloat(),
                        enabled = duration > 0L,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(
                                R.string.player_position,
                                formatDuration(if (dragging) dragValue.toLong() else position),
                                formatDuration(duration),
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(
                            onClick = {
                                speed = when (speed) {
                                    1f -> 1.5f
                                    1.5f -> 2f
                                    else -> 1f
                                }
                                player.setPlaybackSpeed(speed)
                            },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                        ) {
                            Text(
                                text = when (speed) {
                                    1.5f -> stringResource(R.string.player_speed_15x)
                                    2f -> stringResource(R.string.player_speed_2x)
                                    else -> stringResource(R.string.player_speed_1x)
                                },
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AudioMissingHint(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.VolumeOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.player_audio_missing),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
