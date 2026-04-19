package com.personal.autoytube

import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

class AutoYouTubeService : CarAppService() {

    override fun onCreate() {
        super.onCreate()
        YouTubeHelper.init()
    }

    override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession(): Session = AutoYouTubeSession()
}
