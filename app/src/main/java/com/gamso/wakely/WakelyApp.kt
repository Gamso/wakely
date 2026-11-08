package com.gamso.wakely

import android.app.Application
import android.content.Context
import com.gamso.wakely.di.AppContainer
import com.gamso.wakely.framework.CalendarWatchJob
import com.gamso.wakely.framework.Notifications

class WakelyApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannels(this)
        CalendarWatchJob.schedule(this)
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as WakelyApp).container
