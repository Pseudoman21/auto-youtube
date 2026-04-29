package com.personal.autoytube

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*

class MainActivity : Activity() {

    private lateinit var searchInput: EditText
    private lateinit var playerView: PlayerView
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView

    private var player: ExoPlayer? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val videos = mutableListOf<VideoItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        YouTubeHelper.init()
        setContentView(buildLayout())
        loadTrending()
    }

    private fun buildLayout(): View {
        searchInput = EditText(this).apply {
            hint = "Search YouTube..."
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT)
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            inputType = InputType.TYPE_CLASS_TEXT
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    val q = text.toString().trim()
                    if (q.isNotEmpty()) search(q)
                    true
                } else false
            }
        }

        val searchBtn = Button(this).apply {
            text = "Search"
            setOnClickListener {
                val q = searchInput.text.toString().trim()
                if (q.isNotEmpty()) search(q)
            }
        }

        val searchBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#111111"))
            setPadding(16, 8, 8, 8)
            addView(searchInput, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            addView(searchBtn, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
        }

        progressBar = ProgressBar(this)

        statusText = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(32, 16, 32, 16)
        }

        val statusLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(
                progressBar,
                LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    bottomMargin = 16
                }
            )
            addView(statusText, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }

        recyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = VideoAdapter()
            setBackgroundColor(Color.parseColor("#0D0D0D"))
            addItemDecoration(DividerItemDecoration(this@MainActivity, DividerItemDecoration.VERTICAL))
        }

        playerView = PlayerView(this).apply {
            visibility = View.GONE
            setBackgroundColor(Color.BLACK)
        }

        val content = FrameLayout(this).apply {
            addView(recyclerView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            addView(playerView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            addView(statusLayout, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            addView(searchBar, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            addView(content, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        }
    }

    private fun loadTrending() {
        showLoading("Loading trending...")
        scope.launch {
            try {
                val items = withContext(Dispatchers.IO) { YouTubeHelper.getTrending() }
                videos.clear()
                videos.addAll(items)
                (recyclerView.adapter as VideoAdapter).notifyDataSetChanged()
                showList()
            } catch (e: Exception) {
                showError("Failed to load trending: ${e.message}")
            }
        }
    }

    private fun search(query: String) {
        showLoading("Searching...")
        playerView.visibility = View.GONE
        scope.launch {
            try {
                val items = withContext(Dispatchers.IO) { YouTubeHelper.search(query) }
                videos.clear()
                videos.addAll(items)
                (recyclerView.adapter as VideoAdapter).notifyDataSetChanged()
                showList()
            } catch (e: Exception) {
                showError("Search failed: ${e.message}")
            }
        }
    }

    private fun playVideo(video: VideoItem) {
        recyclerView.visibility = View.GONE
        playerView.visibility = View.VISIBLE
        showLoading("Loading: ${video.title}")
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) { YouTubeHelper.getStreamResult(video.url) }
                val mediaItem = when (result.type) {
                    StreamType.DASH -> MediaItem.Builder()
                        .setUri(result.url)
                        .setMimeType(MimeTypes.APPLICATION_MPD)
                        .build()
                    StreamType.HLS -> MediaItem.Builder()
                        .setUri(result.url)
                        .setMimeType(MimeTypes.APPLICATION_M3U8)
                        .build()
                    StreamType.PROGRESSIVE -> MediaItem.fromUri(result.url)
                }
                player?.release()
                player = ExoPlayer.Builder(this@MainActivity).build().also { p ->
                    playerView.player = p
                    p.setMediaItem(mediaItem)
                    p.prepare()
                    p.playWhenReady = true
                }
                progressBar.visibility = View.GONE
                statusText.visibility = View.GONE
            } catch (e: Exception) {
                showError("Failed to play: ${e.message}")
            }
        }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (playerView.visibility == View.VISIBLE) {
            player?.release()
            player = null
            playerView.player = null
            playerView.visibility = View.GONE
            progressBar.visibility = View.GONE
            statusText.visibility = View.GONE
            if (videos.isNotEmpty()) showList() else loadTrending()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        scope.cancel()
    }

    private fun showLoading(message: String) {
        progressBar.visibility = View.VISIBLE
        statusText.setTextColor(Color.WHITE)
        statusText.text = message
        statusText.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
    }

    private fun showList() {
        progressBar.visibility = View.GONE
        statusText.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE
        playerView.visibility = View.GONE
    }

    private fun showError(message: String) {
        progressBar.visibility = View.GONE
        statusText.setTextColor(Color.parseColor("#FF4444"))
        statusText.text = message
        statusText.visibility = View.VISIBLE
    }

    private fun formatDuration(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    private inner class VideoAdapter : RecyclerView.Adapter<VideoAdapter.VH>() {

        inner class VH(val titleView: TextView, val subtitleView: TextView, root: View) :
            RecyclerView.ViewHolder(root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val titleView = TextView(parent.context).apply {
                textSize = 15f
                setTextColor(Color.WHITE)
                maxLines = 2
            }
            val subtitleView = TextView(parent.context).apply {
                textSize = 12f
                setTextColor(Color.parseColor("#AAAAAA"))
                maxLines = 1
            }
            val root = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(32, 20, 32, 20)
                layoutParams = RecyclerView.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                addView(titleView, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
                addView(subtitleView, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            }
            return VH(titleView, subtitleView, root)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val video = videos[position]
            holder.titleView.text = video.title
            val dur = if (video.duration > 0) " · ${formatDuration(video.duration)}" else ""
            holder.subtitleView.text = "${video.uploader}$dur"
            holder.itemView.setOnClickListener { playVideo(video) }
        }

        override fun getItemCount() = videos.size
    }
}
