package com.socialmediascraper

import android.app.Application
import com.socialmediascraper.data.Repository
import com.socialmediascraper.notify.Notifier
import com.socialmediascraper.work.RefreshWorker

class SocialApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifier.createChannel(this)
        RefreshWorker.schedule(this, Repository.get(this).settings.value, replace = false)
    }
}
