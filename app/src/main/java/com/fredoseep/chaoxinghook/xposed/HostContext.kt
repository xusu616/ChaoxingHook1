package com.fredoseep.chaoxinghook.xposed

import android.app.Application
import android.content.Context

/** The host Application is the process-lifetime owner; Activity contexts are never retained. */
object HostContext {
    @Volatile
    private var application: Application? = null

    @JvmStatic
    fun attach(hostApplication: Application) {
        require(hostApplication.packageName == "com.chaoxing.mobile")
        application = hostApplication
    }

    @JvmStatic
    fun getContext(): Context? = application
}
