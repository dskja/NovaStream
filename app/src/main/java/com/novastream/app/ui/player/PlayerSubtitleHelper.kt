@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.novastream.app.ui.player

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import com.novastream.app.R
import com.novastream.app.util.AppContext

data class SubtitleTrackOption(
    val groupIndex: Int,
    val trackIndex: Int,
    val label: String,
    val isSelected: Boolean
)

internal fun collectSubtitleTracks(player: Player): List<SubtitleTrackOption> {
    val tracks = mutableListOf<SubtitleTrackOption>()
    player.currentTracks.groups.forEachIndexed { groupIndex, group ->
        if (group.type != C.TRACK_TYPE_TEXT) return@forEachIndexed
        for (trackIndex in 0 until group.length) {
            val format = group.getTrackFormat(trackIndex)
            val language = format.language?.takeIf { it.isNotBlank() && it != "und" }
            val fallbackLabel = AppContext.get().getString(R.string.player_subtitle_track_fmt, trackIndex + 1)
            val label = format.label?.takeIf { it.isNotBlank() }
                ?: language?.uppercase()
                ?: fallbackLabel
            tracks += SubtitleTrackOption(
                groupIndex = groupIndex,
                trackIndex = trackIndex,
                label = label,
                isSelected = group.isTrackSelected(trackIndex)
            )
        }
    }
    return tracks
}

internal fun selectSubtitleTrack(
    trackSelector: DefaultTrackSelector,
    player: Player,
    groupIndex: Int,
    trackIndex: Int
) {
    val group = player.currentTracks.groups.getOrNull(groupIndex) ?: return
    trackSelector.parameters = trackSelector.buildUponParameters()
        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
        .clearOverridesOfType(C.TRACK_TYPE_TEXT)
        .addOverride(TrackSelectionOverride(group.mediaTrackGroup, trackIndex))
        .build()
}

internal fun disableSubtitles(trackSelector: DefaultTrackSelector) {
    trackSelector.parameters = trackSelector.buildUponParameters()
        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        .clearOverridesOfType(C.TRACK_TYPE_TEXT)
        .build()
}

internal fun formatPlaybackTime(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSec = ms / 1000
    val hours = totalSec / 3600
    val minutes = (totalSec % 3600) / 60
    val seconds = totalSec % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}
