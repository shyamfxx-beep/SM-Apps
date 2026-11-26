// Package: com.sm.ownerconsole
// Application entry point for Owner Console
package com.sm.ownerconsole

import android.app.Application

class OwnerConsoleApp : Application() {
    companion object {
        lateinit var instance: OwnerConsoleApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}
