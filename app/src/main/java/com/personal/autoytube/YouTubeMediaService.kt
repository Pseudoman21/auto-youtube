package com.personal.autoytube

import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.media.MediaBrowserServiceCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class YouTubeMediaService : MediaBrowserServiceCompat() {

    private lateinit var mediaSession: MediaSessionCompat
    private var player: ExoPlayer? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var searchResults: List<VideoItem> = emptyList()
    private var currentVideo: VideoItem? = null

    companion object {
        const val ROOT_ID = "root"
        const val RESULTS_ID = "results"
    }

    override fun onCreate() {
        super.onCreate()
        YouTubeHelper.init()

        mediaSession = MediaSessionCompat(this, "AutoYouTube").apply {
            setCallback(MediaSessionCallback())
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS or
                MediaSessionCompat.FLAG_HANDLES_QUEUE_COMMANDS
            )
            isActive = true
        }
        sessionToken = mediaSession.sessionToken

        player = ExoPlayer.Builder(this).build().apply {
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    updatePlaybackState()
                }
                override fun onPlaybackStateChanged(state: Int) {
                    updatePlaybackState()
                }
            })
        }

        updatePlaybackState()

        scope.launch {
            try {
                searchResults = withContext(Dispatchers.IO) { YouTubeHelper.getTrending() }
                notifyChildrenChanged(ROOT_ID)
            } catch (e: Exception) { /* trending unavailable */ }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
        mediaSession.release()
        scope.cancel()
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot {
        val extras = Bundle().apply {
            putBoolean(BrowserRoot.EXTRA_OFFLINE, false)
        }
        return BrowserRoot(ROOT_ID, extras)
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<List<MediaBrowserCompat.MediaItem>>
    ) {
        result.detach()
        val items = when (parentId) {
            ROOT_ID -> searchResults.map { it.toBrowserItem() }
            else -> emptyList()
        }
        result.sendResult(ArrayList(items))
    }

    override fun onSearch(
        query: String,
        extras: Bundle?,
        result: Result<List<MediaBrowserCompat.MediaItem>>
    ) {
        result.detach()
        scope.launch {
            try {
                searchResults = withContext(Dispatchers.IO) { YouTubeHelper.search(query) }
            } catch (e: Exception) {
                searchResults = emptyList()
            }
            result.sendResult(ArrayList(searchResults.map { it.toBrowserItem() }))
            notifyChildrenChanged(ROOT_ID)
        }
    }

    private fun VideoItem.toBrowserItem(): MediaBrowserCompat.MediaItem {
        val desc = MediaDescriptionCompat.Builder()
            .setMediaId(url)
            .setTitle(title)
            .setSubtitle(uploader)
            .build()
        return MediaBrowserCompat.MediaItem(desc, MediaBrowserCompat.MediaItem.FLAG_PLAYABLE)
    }

    private fun playVideo(video: VideoItem) {
        currentVideo = video
        updateMetadata(video)
        updatePlaybackState(PlaybackStateCompat.STATE_BUFFERING)

        scope.launch {
            try {
                val streamResult = withContext(Dispatchers.IO) {
                    YouTubeHelper.getStreamResult(video.url)
                }
                val mediaItem = when (streamResult.type) {
                    StreamType.DASH -> MediaItem.Builder()
                        .setUri(streamResult.url)
                        .setMimeType(MimeTypes.APPLICATION_MPD)
                        .build()
                    StreamType.HLS -> MediaItem.Builder()
                        .setUri(streamResult.url)
                        .setMimeType(MimeTypes.APPLICATION_M3U8)
                        .build()
                    StreamType.PROGRESSIVE -> MediaItem.fromUri(streamResult.url)
                }
                player?.setMediaItem(mediaItem)
                player?.prepare()
                player?.play()
            } catch (e: Exception) {
                updatePlaybackState(PlaybackStateCompat.STATE_ERROR)
            }
        }
    }

    private fun updateMetadata(video: VideoItem) {
        mediaSession.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, video.title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, video.uploader)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, video.duration * 1000)
                .build()
        )
    }

    private fun updatePlaybackState(forceState: Int? = null) {
        val state = forceState ?: when {
            player?.isPlaying == true -> PlaybackStateCompat.STATE_PLAYING
            player?.playbackState == Player.STATE_BUFFERING -> PlaybackStateCompat.STATE_BUFFERING
            else -> PlaybackStateCompat.STATE_PAUSED
        }
        val position = player?.currentPosition ?: 0L
        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(state, position, 1f)
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_STOP or
                    PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH
                )
                .build()
        )
    }

    inner class MediaSessionCallback : MediaSessionCompat.Callback() {
        override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
            val video = searchResults.find { it.url == mediaId } ?: return
            playVideo(video)
        }

        override fun onPlayFromSearch(query: String?, extras: Bundle?) {
            if (query.isNullOrBlank()) return
            scope.launch {
                try {
                    searchResults = withContext(Dispatchers.IO) { YouTubeHelper.search(query) }
                    searchResults.firstOrNull()?.let { playVideo(it) }
                    notifyChildrenChanged(ROOT_ID)
                } catch (e: Exception) { /* ignore */ }
            }
        }

        override fun onPlay() { player?.play(); updatePlaybackState() }
        override fun onPause() { player?.pause(); updatePlaybackState() }
        override fun onStop() { player?.stop(); updatePlaybackState() }
    }
}
