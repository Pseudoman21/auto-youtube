package com.personal.autoytube

import android.view.Surface
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.ItemList
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.MapTemplate
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VideoScreen(carContext: CarContext, private val video: VideoItem) : Screen(carContext) {

    private var player: ExoPlayer? = null
    private var surface: Surface? = null
    private var isLoading = true
    private var errorMsg: String? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                registerSurfaceCallback()
                loadAndPlay()
            }
            override fun onStop(owner: LifecycleOwner) {
                player?.pause()
            }
            override fun onDestroy(owner: LifecycleOwner) {
                player?.release()
                player = null
                scope.cancel()
            }
        })
    }

    private fun registerSurfaceCallback() {
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(
            object : SurfaceCallback {
                override fun onSurfaceAvailable(holder: SurfaceContainer) {
                    surface = holder.surface
                    player?.setVideoSurface(holder.surface)
                }
                override fun onSurfaceDestroyed(holder: SurfaceContainer) {
                    player?.setVideoSurface(null)
                    surface = null
                }
            }
        )
    }

    private fun loadAndPlay() {
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
                player = ExoPlayer.Builder(carContext).build().apply {
                    surface?.let { setVideoSurface(it) }
                    setMediaItem(mediaItem)
                    prepare()
                    playWhenReady = true
                }
                isLoading = false
                invalidate()
            } catch (e: Exception) {
                errorMsg = e.message ?: "Failed to load video"
                isLoading = false
                invalidate()
            }
        }
    }

    override fun onGetTemplate(): Template {
        val actionStrip = ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setTitle(if (player?.isPlaying == true) "Pause" else "Play")
                    .setOnClickListener {
                        if (player?.isPlaying == true) player?.pause() else player?.play()
                        invalidate()
                    }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle("Back")
                    .setOnClickListener { screenManager.pop() }
                    .build()
            )
            .build()

        val statusMessage = when {
            isLoading -> "Loading video..."
            errorMsg != null -> "Error: $errorMsg"
            else -> video.title
        }

        return MapTemplate.Builder()
            .setActionStrip(actionStrip)
            .setItemList(
                ItemList.Builder()
                    .setNoItemsMessage(statusMessage)
                    .build()
            )
            .build()
    }
}
