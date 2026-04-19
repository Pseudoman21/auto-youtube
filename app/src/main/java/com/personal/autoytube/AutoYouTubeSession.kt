package com.personal.autoytube

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session

class AutoYouTubeSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen = SearchScreen(carContext)
}
