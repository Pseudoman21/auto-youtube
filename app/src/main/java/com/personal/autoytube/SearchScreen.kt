package com.personal.autoytube

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.ItemList
import androidx.car.app.model.Row
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SearchScreen(carContext: CarContext) : Screen(carContext) {

    private var results: List<VideoItem> = emptyList()
    private var isLoading = true
    private var isTrending = true
    private var errorMessage: String? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                scope.cancel()
            }
        })
        loadTrending()
    }

    override fun onGetTemplate(): Template {
        val listBuilder = ItemList.Builder()

        when {
            isLoading -> listBuilder.setNoItemsMessage(
                if (isTrending) "Loading trending videos..." else "Searching..."
            )
            errorMessage != null -> listBuilder.setNoItemsMessage("Error: $errorMessage")
            results.isEmpty() -> listBuilder.setNoItemsMessage(
                if (isTrending) "No trending videos available" else "No results found"
            )
            else -> results.take(20).forEach { video ->
                listBuilder.addItem(
                    Row.Builder()
                        .setTitle(video.title)
                        .addText(video.uploader + if (video.duration > 0) " · ${formatDuration(video.duration)}" else "")
                        .setOnClickListener { screenManager.push(VideoScreen(carContext, video)) }
                        .build()
                )
            }
        }

        return SearchTemplate.Builder(object : SearchTemplate.SearchCallback {
            override fun onSearchTextChanged(searchText: String) {}
            override fun onSearchSubmitted(searchText: String) {
                if (searchText.isNotBlank()) search(searchText)
            }
        })
            .setSearchHint("Search YouTube...")
            .setShowKeyboardByDefault(false)
            .setLoading(isLoading)
            .setItemList(listBuilder.build())
            .build()
    }

    private fun loadTrending() {
        isLoading = true
        isTrending = true
        errorMessage = null
        results = emptyList()

        scope.launch {
            try {
                results = withContext(Dispatchers.IO) { YouTubeHelper.getTrending() }
            } catch (e: Exception) {
                errorMessage = e.message ?: "Failed to load trending"
            } finally {
                isLoading = false
                invalidate()
            }
        }
    }

    private fun search(query: String) {
        isLoading = true
        isTrending = false
        errorMessage = null
        results = emptyList()
        invalidate()

        scope.launch {
            try {
                results = withContext(Dispatchers.IO) { YouTubeHelper.search(query) }
            } catch (e: Exception) {
                errorMessage = e.message ?: "Search failed"
            } finally {
                isLoading = false
                invalidate()
            }
        }
    }

    private fun formatDuration(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }
}
