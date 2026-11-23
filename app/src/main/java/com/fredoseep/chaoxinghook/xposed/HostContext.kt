package com.fredoseep.chaoxinghook.xposed

import android.content.Context

/** The Application.attach base context belongs to this host process. */
object HostContext {
    @Volatile
    private var context: Context? = null

    @JvmStatic
    fun attach(hostContext: Context) {
        require(hostContext.packageName == "com.chaoxing.mobile")
        context = hostContext
    }

    @JvmStatic
    fun getContext(): Context? = context
}
