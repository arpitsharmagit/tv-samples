package com.android.tv.classics.utils

import android.content.Context

object ApplicationContextProvider {
    var applicationContext: Context? = null
        private set
    
    fun init(context: Context) {
        applicationContext = context.applicationContext
    }
}